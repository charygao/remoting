package org.jetlang.remote.example.ws;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

public class WebSocketReader<T> {

    private final Charset charset;
    private final WebSocketHandler<T> handler;
    private final WebSocketConnection connection;
    private final HttpRequest headers;
    private final BodyReader bodyRead = new BodyReader();
    private final BodyReader fragment = new BodyReader();
    private final Frame textFrame = new Frame();
    private final T state;

    public WebSocketReader(WebSocketConnection connection, HttpRequest headers, Charset charset, WebSocketHandler<T> handler) {
        this.connection = connection;
        this.headers = headers;
        this.charset = charset;
        this.handler = handler;
        this.state = handler.onOpen(connection);
    }

    public NioReader.State start() {
        return contentReader;
    }

    private final NioReader.State contentReader = new NioReader.State() {
        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte b = bb.get();
            boolean fin = ((b & 0x80) != 0);
//                boolean rsv1 = ((b & 0x40) != 0);
//                boolean rsv2 = ((b & 0x20) != 0);
//                boolean rsv3 = ((b & 0x10) != 0);
            byte opcode = (byte) (b & 0x0F);
            switch (opcode) {
                case WebSocketConnection.OPCODE_TEXT:
                    return textFrame.init(ContentType.Text, fin, false);
                case WebSocketConnection.OPCODE_BINARY:
                    return textFrame.init(ContentType.Binary, fin, false);
                case WebSocketConnection.OPCODE_PING:
                    return textFrame.init(ContentType.PING, fin, false);
                case WebSocketConnection.OPCODE_PONG:
                    return textFrame.init(ContentType.PONG, fin, false);
                case WebSocketConnection.OPCODE_CONT:
                    return textFrame.init(null, fin, true);
                case WebSocketConnection.OPCODE_CLOSE:
                    connection.sendClose();
                    return createClose();
            }
            handler.onError(connection, state, opcode + " op code isn't supported.");
            return createClose();
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    };

    private NioReader.State createClose() {
        connection.close();
        return new NioReader.Close() {
            @Override
            public void onClosed() {
                handler.onClose(connection, state);
            }
        };
    }

    enum ContentType {
        Text {
            @Override
            public <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset) {
                handler.onMessage(connection, state, new String(result, 0, size, charset));
            }
        }, Binary {
            @Override
            public <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset) {
                handler.onBinaryMessage(connection, state, result, size);
            }
        }, PING {
            @Override
            public <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset) {
                handler.onPing(connection, state, result, size, charset);
            }
        }, PONG {
            @Override
            public <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset) {
                handler.onPong(connection, state, result, size);
            }
        };

        public abstract <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset);
    }

    private class Frame implements NioReader.State {

        private ContentType t;
        private boolean fin;
        private boolean isFragment;

        private NioReader.State init(ContentType t, boolean fin, boolean isFragment) {
            this.t = t;
            this.fin = fin;
            this.isFragment = isFragment;
            return this;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte b = bb.get();
            final int size = (byte) (0x7F & b);
            if (size >= 0 && size <= 125) {
                return bodyReadinit(size, t, fin, isFragment);
            }
            if (size == 126) {
                return new NioReader.State() {
                    @Override
                    public int minRequiredBytes() {
                        return 2;
                    }

                    @Override
                    public NioReader.State processBytes(ByteBuffer bb) {
                        int size = ((bb.get() & 0xFF) << 8) + (bb.get() & 0xFF);
                        return bodyReadinit(size, t, fin, isFragment);
                    }
                };
            }
            if (size == 127) {
                return new NioReader.State() {
                    @Override
                    public int minRequiredBytes() {
                        return 8;
                    }

                    @Override
                    public NioReader.State processBytes(ByteBuffer bb) {
                        return bodyReadinit((int) bb.getLong(), t, fin, isFragment);
                    }
                };
            }
            handler.onError(connection, state, "Unsupported size: " + size);
            return createClose();
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    }

    private NioReader.State bodyReadinit(int size, ContentType t, boolean fin, boolean isFragment) {
        if (isFragment || !fin) {
            return fragment.init(size, t, fin, isFragment);
        }
        return bodyRead.init(size, t, fin, isFragment);
    }


    private class BodyReader implements NioReader.State {
        private int totalSize;
        private int size;
        private ContentType t;
        private byte[] result = new byte[0];
        private boolean fin;

        NioReader.State init(int size, ContentType t, boolean fin, boolean isFragment) {
            this.fin = fin;
            if (!isFragment) {
                this.t = t;
                this.totalSize = 0;
            } else if (t == null && totalSize == 0) {
                return closeOnError("ContentType not specified for fragment.");
            }
            if (size == 0 && !fin) {
                return closeOnError("Size: " + size + " but message is not finished.");
            }
            totalSize += size;
            this.size = size;

            if (result.length < totalSize) {
                result = Arrays.copyOf(result, totalSize);
            }
            return this;
        }

        @Override
        public int minRequiredBytes() {
            return size > 0 ? size + 4 : 0;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            if (size > 0) {
                final int maskPos = bb.position();
                bb.position(bb.position() + 4);
                int startPos = totalSize - size;
                for (int i = 0; i < size; i++) {
                    result[i + startPos] = (byte) (bb.get() ^ bb.get((i % 4) + maskPos));
                }
            }
            if (fin) {
                t.onComplete(handler, connection, state, result, totalSize, charset);
                totalSize = 0;
                t = null;
            }
            return contentReader;
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    }

    private NioReader.State closeOnError(String msg) {
        handler.onError(connection, state, msg);
        return createClose();
    }
}
