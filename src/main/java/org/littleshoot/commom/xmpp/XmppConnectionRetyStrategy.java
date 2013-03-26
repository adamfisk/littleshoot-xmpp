package org.littleshoot.commom.xmpp;

public interface XmppConnectionRetyStrategy {

    boolean retry();

    void sleep();

}
