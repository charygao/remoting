package org.jetlang.remote.acceptor;

import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.core.MsgTypes;

import java.nio.channels.SocketChannel;

public class JetlangNioSession extends JetlangBaseSession implements JetlangMessageHandler {

    private final NioJetlangSendFiber.ChannelState channel;
    private final NioJetlangSendFiber sendFiber;
    private final ErrorHandler errorHandler;

    public interface ErrorHandler {

        void onUnhandledReplyMsg(int reqId, String dataTopicVal, Object readObject);

        void onUnknownMessage(int read);

        void onDataHandlingFailure(String dataTopicVal, Object readObject, Exception failed);
    }

    public JetlangNioSession(NioFiber fiber, SocketChannel channel, NioJetlangSendFiber sendFiber, NioJetlangRemotingClientFactory.Id id, ErrorHandler errorHandler) {
        super(id);
        this.errorHandler = errorHandler;
        this.channel = new NioJetlangSendFiber.ChannelState(channel, id, fiber);
        this.sendFiber = sendFiber;
        this.sendFiber.onNewSession(this.channel);
    }

    @Override
    public void onDataHandlingFailure(String dataTopicVal, Object readObject, Exception failed) {
        errorHandler.onDataHandlingFailure(dataTopicVal, readObject, failed);
    }

    public void sendHb() {
        sendFiber.sendIntAsByte(channel, MsgTypes.Heartbeat);
    }

    @Override
    public void onLogout() {
        sendFiber.handleLogout(channel);
        Logout.publish(new LogoutEvent());
    }

    @Override
    public void onSubscriptionRequest(String topic) {
        sendFiber.onSubscriptionRequest(topic, channel);
        SubscriptionRequest.publish(new SessionTopic(topic, this));
    }

    @Override
    public void onUnsubscribeRequest(String topic) {
        UnsubscribeRequest.publish(topic);
        sendFiber.onUnsubscribeRequest(topic, channel);
    }

    @Override
    public <T> void publish(String topic, T msg) {
        sendFiber.publish(channel, topic, msg);
    }

    @Override
    public void disconnect() {
        channel.closeOnNioFiber();
    }

    @Override
    public void publish(byte[] data) {
        sendFiber.publishBytes(channel, data);
    }

    @Override
    public void reply(int reqId, String replyTopic, Object replyMsg) {
        sendFiber.reply(channel, reqId, replyTopic, replyMsg);
    }

    @Override
    public void onRequestReply(int reqId, String dataTopicVal, Object readObject) {
        errorHandler.onUnhandledReplyMsg(reqId, dataTopicVal, readObject);
    }

    @Override
    public void publishIfSubscribed(String topic, byte[] data) {
        sendFiber.publishIfSubscribed(channel, topic, data);
    }

    @Override
    public void onClose(SessionCloseEvent sessionCloseEvent) {
        sendFiber.handleClose(channel);
        super.onClose(sessionCloseEvent);
    }

    @Override
    public void onUnknownMessage(int read) {
        errorHandler.onUnknownMessage(read);
    }
}
