package org.littleshoot.commom.xmpp;

import java.io.IOException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;


public class GoogleOAuth2Credentials implements XmppCredentials, CallbackHandler {

    private String username;
    private String resource;
    private String clientID;
    private String clientSecret;
    private String accessToken;
    private String refreshToken;

    public GoogleOAuth2Credentials(String username,
                                   String clientID,
                                   String clientSecret,
                                   String accessToken,
                                   String refreshToken) {
        this(username, clientID, clientSecret, accessToken,
             refreshToken, "SHOOT-");
    }

    public GoogleOAuth2Credentials(String username,
                                   String clientID,
                                   String clientSecret,
                                   String accessToken,
                                   String refreshToken,
                                   String resource) {
        if (!username.contains("@")) {
            username += "@gmail.com";
        }
        this.username = username;
        this.clientID = clientID;
        this.clientSecret = clientSecret;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.resource = resource;
    }

    public String getUsername() {
        return username;
    }

    public String getKey() {
        return username + refreshToken;
    }

    public XMPPConnection createConnection(ConnectionConfiguration config) {
        config.setCallbackHandler(this);
        XMPPConnection conn = new XMPPConnection(config);
        conn.getSASLAuthentication().supportSASLMechanism("X-OAUTH2");
        return conn;
    }

    public void login(XMPPConnection conn) throws XMPPException {
        conn.login(username, null, resource);
    }

    public void handle(Callback[] callbacks)
                              throws IOException,
                                     UnsupportedCallbackException {
        for (Callback cb : callbacks) {
            if (cb instanceof TextInputCallback) {
                TextInputCallback ticb = (TextInputCallback)cb;
                String prompt = ticb.getPrompt();
                if (prompt == "clientID") {
                    ticb.setText(clientID);
                } else if (prompt == "clientSecret") {
                    ticb.setText(clientSecret);
                } else if (prompt == "accessToken") {
                    ticb.setText(accessToken);
                } else if (prompt == "refreshToken") {
                    ticb.setText(refreshToken);
                } else {
                    throw new UnsupportedCallbackException(ticb, "Unrecognized prompt: " + ticb.getPrompt());
                }
            } else {
                throw new UnsupportedCallbackException(cb, "Unsupported callback type.");
            }
        }
    }
}

