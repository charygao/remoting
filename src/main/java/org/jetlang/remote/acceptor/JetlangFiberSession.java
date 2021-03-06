package org.jetlang.remote.acceptor;

import org.jetlang.channels.Subscriber;
import org.jetlang.core.Callback;
import org.jetlang.fibers.Fiber;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.ReadTimeoutEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * User: mrettig
 * Date: 11/29/11
 * Time: 11:14 AM
 */
public class JetlangFiberSession<R, W> implements JetlangSession<R, W> {

    private final JetlangSession<R, W> session;
    private final Fiber targetFiber;
    private final Map<String, SessionTopic<W>> subscribed = new HashMap<String, SessionTopic<W>>();

    public JetlangFiberSession(JetlangSession<R, W> session, Fiber targetFiber) {
        this.session = session;
        this.targetFiber = targetFiber;
        session.getSubscriptionRequestChannel().subscribe(targetFiber, new Callback<SessionTopic<W>>() {
            public void onMessage(SessionTopic<W> message) {
                subscribed.put(message.getTopic(), message);
            }
        });
        session.getUnsubscribeChannel().subscribe(targetFiber, new Callback<String>() {
            public void onMessage(String message) {
                subscribed.remove(message);
            }
        });
    }

    public Map<String, SessionTopic<W>> getSubscriptions() {
        return subscribed;
    }

    public boolean isSubscribed(String topic) {
        return subscribed.containsKey(topic);
    }

    public Fiber getFiber() {
        return targetFiber;
    }

    public Object getSessionId() {
        return session.getSessionId();
    }

    public Subscriber<SessionTopic<W>> getSubscriptionRequestChannel() {
        return session.getSubscriptionRequestChannel();
    }

    public Subscriber<String> getUnsubscribeChannel() {
        return session.getUnsubscribeChannel();
    }

    public Subscriber<LogoutEvent> getLogoutChannel() {
        return session.getLogoutChannel();
    }

    public Subscriber<HeartbeatEvent> getHeartbeatChannel() {
        return session.getHeartbeatChannel();
    }

    public Subscriber<SessionMessage<R>> getSessionMessageChannel() {
        return session.getSessionMessageChannel();
    }

    public Subscriber<SessionRequest<R,W>> getSessionRequestChannel() {
        return session.getSessionRequestChannel();
    }

    public Subscriber<ReadTimeoutEvent> getReadTimeoutChannel() {
        return session.getReadTimeoutChannel();
    }

    public Subscriber<SessionCloseEvent> getSessionCloseChannel() {
        return session.getSessionCloseChannel();
    }

    public void disconnect() {
        session.disconnect();
    }

    public void publish(byte[] data) {
        session.publish(data);
    }

    public void publish(String topic, W msg) {
        session.publish(topic, msg);
    }
}
