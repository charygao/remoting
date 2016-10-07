package org.jetlang.remote.example.ws;

public interface WebSocketHandler<T> {
    T onOpen(WebSocketConnection connection);

    void onMessage(WebSocketConnection connection, T state, String msg);

    void onClose(WebSocketConnection connection, T state);
}
