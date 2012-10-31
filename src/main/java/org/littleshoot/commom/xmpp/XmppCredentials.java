package org.littleshoot.commom.xmpp;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

public interface XmppCredentials {
    String getKey();
    XMPPConnection createConnection(ConnectionConfiguration config);
    void login(XMPPConnection conn) throws XMPPException;
}
