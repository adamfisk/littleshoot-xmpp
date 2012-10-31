package org.littleshoot.commom.xmpp;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;


public class PasswordCredentials implements XmppCredentials {

    private String username;
    private String password;
    private String resource;

    public PasswordCredentials(String username, String password) {
        this(username, password, "SHOOT-");
    }

    public PasswordCredentials(String username, String password, String resource) {
        this.username = username;
        this.password = password;
        this.resource = resource;
    }

    public String getKey() {
        return username + password;
    }

    public XMPPConnection createConnection(ConnectionConfiguration config) {
        return new XMPPConnection(config);
    }

    public void login(XMPPConnection conn) throws XMPPException {
        conn.login(username, password, resource);
    }
}
