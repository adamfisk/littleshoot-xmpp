package org.littleshoot.commom.xmpp;

import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.lastbamboo.common.p2p.P2PClient;

/**
 * P2P client interface for XMPP clients.
 */
public interface XmppP2PClient extends P2PClient {

    XMPPConnection getXmppConnection();
    
    void addMessageListener(MessageListener ml);
}
