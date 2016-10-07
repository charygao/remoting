package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class WebServerConfigBuilder {

    private Map<String, Handler> handlerMap = new HashMap<>();
    private final Charset ascii = Charset.forName("ASCII");

    public WebServerConfigBuilder add(String path, WebSocketHandler handler) {
        handlerMap.put(path, new Handler() {
            private final MessageDigest msgDigest = getDigest("SHA-1");

            @Override
            public NioReader.State start(HttpRequest headers, NioControls controls, SocketChannel channel, NioFiber fiber, HeaderReader headerReader) {
                StringBuilder handshake = new StringBuilder();
                handshake.append("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
                String key = headers.get("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                String reply = DatatypeConverter.printBase64Binary(msgDigest.digest(key.getBytes(ascii)));
                handshake.append(reply).append("\r\n\r\n");
                controls.write(channel, ByteBuffer.wrap(handshake.toString().getBytes(ascii)));
                System.out.println("handshake = " + handshake);
                WebSocketConnection connection = new WebSocketConnection(headers, channel, controls, ascii);
                WebSocketReader reader = new WebSocketReader(channel, fiber, controls, connection, headers, ascii, handler);
                return reader.start();
            }
        });
        return this;
    }

    public WebServerConfigBuilder add(String path, StaticResource rs) {
        handlerMap.put(path, rs);
        return this;
    }

    private MessageDigest getDigest(String s) {
        try {
            return MessageDigest.getInstance(s);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public WebDispatcher create() {
        return new WebDispatcher(new HashMap<>(handlerMap));
    }
}
