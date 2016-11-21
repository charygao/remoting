package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.web.HttpRequest;
import org.jetlang.web.RoundRobinClientFactory;
import org.jetlang.web.SendResult;
import org.jetlang.web.SessionFactory;
import org.jetlang.web.StaticResource;
import org.jetlang.web.WebAcceptor;
import org.jetlang.web.WebServerConfigBuilder;
import org.jetlang.web.WebSocketConnection;
import org.jetlang.web.WebSocketHandler;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebSocketServerEchoMain {


    public static void main(String[] args) throws InterruptedException {

        NioFiberImpl acceptorFiber = new NioFiberImpl();
        acceptorFiber.start();
        WebSocketHandler<Void, Map<String, String>> handler = new WebSocketHandler<Void, Map<String, String>>() {
            @Override
            public Map<String, String> onOpen(WebSocketConnection connection, HttpRequest headers, Void httpSessionState) {
                System.out.println("Open!");
                return new HashMap<>();
            }

            @Override
            public void onMessage(WebSocketConnection connection, Map<String, String> wsState, String msg) {
                SendResult send = connection.send(msg);
                if (send instanceof SendResult.Buffered) {
                    System.out.println("Buffered: " + ((SendResult.Buffered) send).getTotalBufferedInBytes());
                }
            }

            @Override
            public void onBinaryMessage(WebSocketConnection connection, Map<String, String> state, byte[] result, int size) {
                connection.sendBinary(result, 0, size);
            }

            @Override
            public void onClose(WebSocketConnection connection, Map<String, String> nothing) {
                System.out.println("WS Close");
            }

            @Override
            public void onError(WebSocketConnection connection, Map<String, String> state, String msg) {
                System.err.println(msg);
            }

            @Override
            public boolean onException(WebSocketConnection connection, Map<String, String> state, Exception failed) {
                System.err.print(failed.getMessage());
                failed.printStackTrace(System.err);
                return true;
            }
        };

        WebServerConfigBuilder<Void> config = new WebServerConfigBuilder<>(SessionFactory.none());
        config.add("/websockets/echo", handler);
        final URL resource = Thread.currentThread().getContextClassLoader().getResource("websocket.html");
        config.add("/", new StaticResource(new File(resource.getFile()).toPath()));

        final int cores = Runtime.getRuntime().availableProcessors();
        RoundRobinClientFactory readers = new RoundRobinClientFactory();
        List<NioFiber> allReadFibers = new ArrayList<>();
        for (int i = 0; i < cores; i++) {
            NioFiber readFiber = new NioFiberImpl();
            readFiber.start();
            readers.add(config.create(readFiber));
            allReadFibers.add(readFiber);
        }

        WebAcceptor.Config acceptorConfig = new WebAcceptor.Config();

        WebAcceptor acceptor = new WebAcceptor(8025, acceptorFiber, readers, acceptorConfig, () -> {
            System.out.println("AcceptorEnd");
        });

        acceptor.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                allReadFibers.forEach(NioFiber::dispose);
                acceptorFiber.dispose();
            }
        });
        Thread.sleep(Long.MAX_VALUE);
    }
}
