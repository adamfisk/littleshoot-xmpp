package org.littleshoot.commom.xmpp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerConnectException;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.offer.answer.OfferAnswerTransactionListener;
import org.lastbamboo.common.p2p.P2PConstants;
import org.lastbamboo.common.p2p.P2PSignalingClient;
import org.lastbamboo.common.util.CommonUtils;
import org.littleshoot.mina.common.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppP2PSignalingClient implements P2PSignalingClient {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final OfferAnswerFactory offerAnswerFactory;
    private final OfferAnswerListener offerAnswerListener;

    private XMPPConnection xmppConnection;
    

    public XmppP2PSignalingClient(final OfferAnswerFactory offerAnswerFactory,
        final OfferAnswerListener offerAnswerListener) {
        this.offerAnswerFactory = offerAnswerFactory;
        this.offerAnswerListener = offerAnswerListener;
    }

    public String login(final String username, final String password) 
        throws IOException {
        return persistentXmppConnection(username, password);
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
        final OfferAnswerTransactionListener transactionListener) 
        throws IOException {
        // We need to convert the URI to a XMPP/Jabber JID.
        final String jid = uri.toASCIIString();
        
        final ChatManager chatManager = xmppConnection.getChatManager();
        final Object lock = new Object();
        final Message offerMessage = new Message();
        final String base64 = 
            Base64.encodeBase64URLSafeString(offer);
        offerMessage.setProperty(P2PConstants.MESSAGE_TYPE, P2PConstants.INVITE);
        offerMessage.setProperty(P2PConstants.SDP, base64);
        final Chat chat = chatManager.createChat(jid,
            new MessageListener() {
                public void processMessage(final Chat ch, final Message msg) {
                    log.info("Got message on offerer: {}", msg);
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            });
        
        log.info("Sending message to: {}", jid);
        try {
            chat.sendMessage(offerMessage);
        } catch (final XMPPException e1) {
            log.error("Could not send offer!!", e1);
            throw new IOException("Could not send offer", e1);
        }
        
        synchronized (lock) {
            try {
                lock.wait(30 * 1000);
            } catch (final InterruptedException e) {
                log.error("Interrupted while waiting?", e);
            }
        }
    }
    
    private String persistentXmppConnection(final String username, 
        final String password) throws IOException {
        XMPPException exc = null;
        for (int i = 0; i < 10; i++) {
            try {
                log.info("Attempting XMPP connection...");
                final XMPPConnection conn = 
                    singleXmppConnection(username, password);
                addChatManagerLister(conn);
                return conn.getUser();
            } catch (final XMPPException e) {
                final String msg = "Error creating XMPP connection";
                log.error(msg, e);
                exc = e;
            }
        }
        if (exc != null) {
            throw new IOException("Could not log in!!", exc);
        }
        else {
            throw new IOException("Could not log in?");
        }
    }
    
    private void addChatManagerLister(final XMPPConnection conn) {

        final ChatManager cm = conn.getChatManager();
        cm.addChatListener(new ChatManagerListener() {
            
            public void chatCreated(final Chat chat, 
                final boolean createdLocally) {
                log.info("Created a chat!!");
               
                
                final String participant = chat.getParticipant();
                // We need to listen for the unavailability of clients we're 
                // chatting with so we can disconnect from their associated 
                // remote servers.
                final PacketListener pl = new PacketListener() {
                    public void processPacket(final Packet pack) {
                        log.info("Got packet: {}", pack);
                    }
                };
                // Register the listener.
                conn.addPacketListener(pl, null);
                
                final MessageListener ml = new MessageListener() {
                    
                    public void processMessage(final Chat ch, final Message m) {
                        log.info("Got message on answerer: {}", m);
                        final int mt = 
                            (Integer) m.getProperty(P2PConstants.MESSAGE_TYPE);
                        switch (mt) {
                            case P2PConstants.INVITE:
                                final String sdp = 
                                    (String) m.getProperty(P2PConstants.SDP);
                                if (StringUtils.isNotBlank(sdp)) {
                                    processSdp(ch, Base64.decodeBase64(sdp));
                                }
                                break;
                        }
                    }

                };
                chat.addMessageListener(ml);
            }
        });
    }
    
    private void processSdp(final Chat chat, final byte[] body) {
        final ByteBuffer offer = ByteBuffer.wrap(body);
        final OfferAnswer offerAnswer;
        try {
            offerAnswer = this.offerAnswerFactory.createAnswerer(
               this.offerAnswerListener);
        }
        catch (final OfferAnswerConnectException e) {
            // This indicates we could not establish the necessary connections 
            // for generating our candidates.
            log.warn("We could not create candidates for offer: " +
                CommonUtils.toString(body), e);
            final Message msg = new Message();
            msg.setProperty(P2PConstants.MESSAGE_TYPE, P2PConstants.INVITE_ERROR);
            try {
                chat.sendMessage(msg);
            } catch (final XMPPException e1) {
                log.error("Could not send error message", e1);
            }
            //this.m_sipClient.writeInviteRejected(invite, 488, 
            //"Not Acceptable Here");
            return;
        }
        final byte[] answer = offerAnswer.generateAnswer();
        final Message msg = new Message();
        msg.setProperty(P2PConstants.MESSAGE_TYPE, P2PConstants.INVITE_OK);
        msg.setProperty(P2PConstants.SDP, Base64.encodeBase64String(answer));
        log.info("Sending INVITE OK!!");
        try {
            chat.sendMessage(msg);
        } catch (final XMPPException e) {
            log.error("Could not send error message", e);
        }
        //this.sipClient.writeInviteOk(invite, ByteBuffer.wrap(answer));
        offerAnswer.processOffer(offer);

        log.debug("Done processing XMPP INVITE!!!");
    }

    private XMPPConnection singleXmppConnection(final String username, 
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
        
        xmppConnection.login(username, password, "SHOOT-");
        
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
                try {
                    persistentXmppConnection(username, password);
                } catch (final IOException e1) {
                    log.error("Could not re-establish connection?", e1);
                }
            }
            
            public void connectionClosed() {
                log.info("XMPP connection closed. Creating new connection.");
                try {
                    persistentXmppConnection(username, password);
                } catch (final IOException e1) {
                    log.error("Could not re-establish connection?", e1);
                }
            }
        });
        
        return xmppConnection;
    }
}
