package org.jetlang.remote.acceptor;

import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.ReadTimeoutEvent;

public interface JetlangMessageHandler<T> extends JetlangRemotingProtocol.Handler<T> {
    Object getSessionId();

    void onReadTimeout(ReadTimeoutEvent readTimeoutEvent);
}
