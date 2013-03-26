package org.littleshoot.commom.xmpp;

public class DefaultXmppConnectionRetryStrategyFactory implements
        XmppConnectionRetyStrategyFactory {

    @Override
    public XmppConnectionRetyStrategy newStrategy() {
        return new DefaultXmppConnectionRetryStrategy();
    }

}
