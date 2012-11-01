package org.littleshoot.commom.xmpp;

import java.io.IOException;

import javax.security.auth.login.CredentialException;

import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.lastbamboo.common.p2p.P2PClient;

/**
 * P2P client interface for XMPP clients.
 */
public interface XmppP2PClient extends P2PClient {

    XMPPConnection getXmppConnection();
    
    void addMessageListener(MessageListener ml);
    
    boolean isLoggedOut();

    void handleClose();
    
    void stop();

    String login(String user, String pass, String serverHost, int serverPort,
            String serviceName) throws IOException, CredentialException;

    String login(String user, String pass, String serverHost, int serverPort,
            String serviceName, String id) throws IOException,
            CredentialException;

    String login(XmppCredentials credentials) throws IOException,
            CredentialException;

    String login(XmppCredentials credentials, String serverHost, int serverPort,
            String serviceName) throws IOException,
            CredentialException;
}
