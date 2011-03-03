package org.littleshoot.xmpp;

import java.io.IOException;
import java.net.Socket;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.provider.ProviderManager;
import org.junit.Ignore;
import org.junit.Test;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerConnectException;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.offer.answer.OfferAnswerListenerImpl;
import org.littleshoot.commom.xmpp.DefaultXmppP2PClient;
import org.littleshoot.commom.xmpp.SASLFacebookMechanism;
import org.littleshoot.util.SocketListener;

@Ignore
public class DefaultXmppP2PClientTest {

    @Test public void testGoogleLogin() throws Exception  {
        ConnectionConfiguration config = 
            new ConnectionConfiguration("talk.google.com", 5222);
        final XMPPConnection conn = new XMPPConnection(config);
        
        conn.connect();
    }
    
    public void testLogin() throws Exception {
        
        
        ProviderManager.getInstance().addIQProvider("vCard", "vcard-temp",
                new org.jivesoftware.smackx.provider.VCardProvider());
            // register Facebook SASL mechanism
        
            SASLAuthentication.registerSASLMechanism(
                SASLFacebookMechanism.NAME,
                SASLFacebookMechanism.class);
            SASLAuthentication.supportSASLMechanism(
                SASLFacebookMechanism.NAME, 0);

            // create a connection
            ConnectionConfiguration config = 
                new ConnectionConfiguration("chat.facebook.com", 5222);
            final XMPPConnection conn = new XMPPConnection(config);
            
            conn.connect();
            //SASLAuthentication.registerSASLMechanism("DIGEST-MD5", MySASLDigestMD5Mechanism.class);
            
            //conn.login("a@bravenewsoftware.org", "1745t77q", "test");
            conn.login("a@bravenewsoftware.org@chat.facebook.com", "1745t77q");
            
            Thread.sleep(30000);
            
            /*
        final ConnectionConfiguration config = 
            //new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
            new ConnectionConfiguration("chat.facebook.com", 5222, "chat.facebook.com");
        config.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        config.setDebuggerEnabled(true);
        //config.setCompressionEnabled(true);
        //config.setRosterLoadedAtLogin(true);
        //config.setReconnectionAllowed(false);
        
        final XMPPConnection conn = new XMPPConnection(config);
        conn.connect();
        SASLAuthentication.registerSASLMechanism("DIGEST-MD5", MySASLDigestMD5Mechanism.class);
        
        //conn.login("a@bravenewsoftware.org", "1745t77q", "test");
        conn.login("a@bravenewsoftware.org@chat.facebook.com", "1745t77q");
        
        Thread.sleep(30000);
        
        */
            
        /*
        final SocketListener socketListener = new SocketListener() {
            
            public void onSocket(Socket arg0) throws IOException {
                // TODO Auto-generated method stub
                
            }
        };
        final OfferAnswerListener listener = 
            new OfferAnswerListenerImpl(socketListener);
        final OfferAnswerFactory factory = new OfferAnswerFactory() {
            
            public OfferAnswer createOfferer(OfferAnswerListener arg0)
                    throws OfferAnswerConnectException {
                // TODO Auto-generated method stub
                return null;
            }
            
            public OfferAnswer createAnswerer(OfferAnswerListener arg0)
                    throws OfferAnswerConnectException {
                // TODO Auto-generated method stub
                return null;
            }
        };
        final DefaultXmppP2PClient client = 
            DefaultXmppP2PClient.newFacebookChatClient(factory, listener, 20000);
        client.login("a@littleshoot.org", "1745t77q");
        */
    }
    

}
