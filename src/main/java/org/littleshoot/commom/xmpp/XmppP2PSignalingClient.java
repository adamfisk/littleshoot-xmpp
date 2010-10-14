package org.littleshoot.commom.xmpp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerTransactionListener;
import org.lastbamboo.common.p2p.P2PSignalingClient;
import org.lastbamboo.common.util.SocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppP2PSignalingClient implements P2PSignalingClient {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final OfferAnswerFactory offerAnswerFactory;
    private final SocketListener socketListener;

    private XMPPConnection xmppConnection;
    

    public XmppP2PSignalingClient(final OfferAnswerFactory offerAnswerFactory,
        final SocketListener socketListener) {
        this.offerAnswerFactory = offerAnswerFactory;
        this.socketListener = socketListener;
    }

    public void login(final String username, final String password) {
        persistentXmppConnection(username, password);
    }
    
    public void register(final long userId) {
        log.error("User name and pwd required");
        throw new UnsupportedOperationException("User name and pwd required");
    }

    public void register(final URI sipUri) {
        log.error("User name and pwd required");
        throw new UnsupportedOperationException("User name and pwd required");
    }

    public void register(final String id) {
        log.error("User name and pwd required");
        throw new UnsupportedOperationException("User name and pwd required");
    }
    
    public void offer(final URI uri, final byte[] offer,
        final OfferAnswerTransactionListener transactionListener) {
        // We need to convert the URI to a XMPP/Jabber JID.
        final String jid = uri.toASCIIString();
        
        final ChatManager chatManager = xmppConnection.getChatManager();
        final Chat chat = chatManager.createChat(jid,
            new MessageListener() {
                public void processMessage(final Chat ch, final Message msg) {
                }
            });
    }
    
    private void persistentXmppConnection(final String username, 
        final String password) {
        for (int i = 0; i < 10; i++) {
            try {
                log.info("Attempting XMPP MONITORING connection...");
                singleXmppConnection(username, password);
                log.info("Successfully connected...");
                return;
            } catch (final XMPPException e) {
                final String msg = "Error creating XMPP connection";
                log.error(msg, e);
            }
        }
    }
    
    private void singleXmppConnection(final String username, 
        final String password) throws XMPPException {
        final ConnectionConfiguration config = 
            new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
        config.setCompressionEnabled(true);
        config.setRosterLoadedAtLogin(true);
        config.setReconnectionAllowed(false);
        config.setSocketFactory(new SocketFactory() {
            
            @Override
            public Socket createSocket(final InetAddress host, 
                final int port, final InetAddress localHost,
                final int localPort) throws IOException {
                // We ignore the local port binding.
                return createSocket(host, port);
            }
            
            @Override
            public Socket createSocket(final String host, 
                final int port, final InetAddress localHost,
                final int localPort)
                throws IOException, UnknownHostException {
                // We ignore the local port binding.
                return createSocket(host, port);
            }
            
            @Override
            public Socket createSocket(final InetAddress host, int port) 
                throws IOException {
                log.info("Creating socket");
                final Socket sock = new Socket();
                sock.connect(new InetSocketAddress(host, port), 40000);
                log.info("Socket connected");
                return sock;
            }
            
            @Override
            public Socket createSocket(final String host, final int port) 
                throws IOException, UnknownHostException {
                log.info("Creating socket");
                return createSocket(InetAddress.getByName(host), port);
            }
        });
        
        this.xmppConnection = new XMPPConnection(config);
        xmppConnection.connect();
        
        // We have a limited number of bytes to work with here, so we just
        // append the MAC straight after the "MG".
        //final String id = "MG"+macAddress;
        
        xmppConnection.login(username, password, "LittleShoot");
        
        while (!xmppConnection.isAuthenticated()) {
            log.info("Waiting for authentication");
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e1) {
                log.error("Exception during sleep?", e1);
            }
        }
        
        /*
        final Roster roster = xmpp.getRoster();
        
        roster.addRosterListener(new RosterListener() {
            public void entriesDeleted(Collection<String> addresses) {}
            public void entriesUpdated(Collection<String> addresses) {}
            public void presenceChanged(final Presence presence) {
                final String from = presence.getFrom();

            }
            public void entriesAdded(final Collection<String> addresses) {
                log.info("Entries added: "+addresses);
            }
        });
        */
        
        xmppConnection.addConnectionListener(new ConnectionListener() {
            
            public void reconnectionSuccessful() {
                log.info("Reconnection successful...");
            }
            
            public void reconnectionFailed(final Exception e) {
                log.info("Reconnection failed", e);
            }
            
            public void reconnectingIn(final int time) {
                log.info("Reconnecting to XMPP server in "+time);
            }
            
            public void connectionClosedOnError(final Exception e) {
                log.info("XMPP connection closed on error", e);
                persistentXmppConnection(username, password);
            }
            
            public void connectionClosed() {
                log.info("XMPP connection closed. Creating new connection.");
                persistentXmppConnection(username, password);
            }
        });
    }
}
