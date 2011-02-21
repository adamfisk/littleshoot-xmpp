package org.littleshoot.commom.xmpp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.SocketFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.lastbamboo.common.offer.answer.NoAnswerException;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerConnectException;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.offer.answer.OfferAnswerMessage;
import org.lastbamboo.common.offer.answer.OfferAnswerTransactionListener;
import org.lastbamboo.common.p2p.DefaultTcpUdpSocket;
import org.lastbamboo.common.p2p.P2PConstants;
import org.lastbamboo.common.p2p.TcpUdpSocket;
import org.littleshoot.util.CommonUtils;
import org.littleshoot.mina.common.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of an XMPP P2P client connection.
 */
public class DefaultXmppP2PClient implements XmppP2PClient {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final OfferAnswerFactory offerAnswerFactory;
    private final OfferAnswerListener offerAnswerListener;

    private XMPPConnection xmppOffererConnection;
    
    /**
     * The executor is used to queue up messages in order. This allows 
     * different threads to send messages without worrying about them getting
     * mangled or out of order.
     */
    private final ExecutorService messageExecutor = 
        Executors.newSingleThreadExecutor();
    
    private final Collection<MessageListener> messageListeners =
        new ArrayList<MessageListener>();

    private final int relayWaitTime;
    

    public DefaultXmppP2PClient(final OfferAnswerFactory offerAnswerFactory,
        final OfferAnswerListener offerAnswerListener, 
        final int relayWaitTime) {
        this.offerAnswerFactory = offerAnswerFactory;
        this.offerAnswerListener = offerAnswerListener;
        this.relayWaitTime = relayWaitTime;
    }
    
    public Socket newSocket(final URI uri) throws IOException, 
        NoAnswerException {
        log.trace ("Creating XMPP socket for URI: {}", uri);
        
        // Note we use a short timeout for waiting for answers. This is 
        // because we've seen XMPP messages get lost in the ether, and we 
        // just want to send a few of them quickly when this does happen.
        final TcpUdpSocket tcpUdpSocket = 
            new DefaultTcpUdpSocket(this, this.offerAnswerFactory,
                this.relayWaitTime, 10 * 1000);
        
        return tcpUdpSocket.newSocket(uri);
    }

    public String login(final String username, final String password) 
        throws IOException {
        return persistentXmppConnection(username, password, "SHOOT-");
    }
    
    public String login(final String username, final String password,
        final String id) throws IOException {
        return persistentXmppConnection(username, password, id);
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
        
        final Runnable runner = new Runnable() {
            public void run() {
                try {
                    xmppOffer(uri, offer, transactionListener);
                }
                catch (final Throwable t) {
                    log.error("Unexpected throwable", t);
                }
            }
        };
        
        this.messageExecutor.execute(runner);
    }
    
    private void xmppOffer(final URI uri, final byte[] offer,
        final OfferAnswerTransactionListener transactionListener) 
        throws IOException {
        // We need to convert the URI to a XMPP/Jabber JID.
        final String jid = uri.toASCIIString();
        
        final ChatManager chatManager = xmppOffererConnection.getChatManager();
        final Message offerMessage = new Message();
        log.info("Creating offer to: {}", jid);
        log.info("Sending offer: {}", new String(offer));
        final String base64 = 
            Base64.encodeBase64URLSafeString(offer);
        offerMessage.setProperty(P2PConstants.MESSAGE_TYPE, P2PConstants.INVITE);
        offerMessage.setProperty(P2PConstants.SDP, base64);
        log.info("Creating chat from: {}", xmppOffererConnection.getUser());
        final Chat chat = chatManager.createChat(jid, 
            new MessageListener() {
                public void processMessage(final Chat ch, final Message msg) {
                    log.info("Got answer on offerer: {}", msg);

                    final String base64Body = 
                        (String) msg.getProperty(P2PConstants.SDP);
                    final byte[] body;
                    if (StringUtils.isBlank(base64Body)) {
                        log.error("No SDP!!");
                        body = ArrayUtils.EMPTY_BYTE_ARRAY;
                    }
                    else {
                        body = Base64.decodeBase64(base64Body);
                    }
                    final OfferAnswerMessage oam = 
                        new OfferAnswerMessage() {
                            
                            public String getTransactionKey() {
                                return String.valueOf(hashCode());
                            }
                            
                            public ByteBuffer getBody() {
                                return ByteBuffer.wrap(body);
                            }
                        };
                        
                    final Object obj = 
                        msg.getProperty(P2PConstants.MESSAGE_TYPE);
                    if (obj == null) {
                        log.error("No message type!!");
                        return;
                    }
                    final int type = (Integer) obj;
                    switch (type) {
                        case P2PConstants.INVITE_OK:
                            transactionListener.onTransactionSucceeded(oam);
                            break;
                        case P2PConstants.INVITE_ERROR:
                            transactionListener.onTransactionFailed(oam);
                            break;
                        default:
                            log.error("Did not recognize type: " + type);
                            log.error(XmppUtils.toString(msg));
                    }
                }
            });
        
        log.info("Message listeners just after OFFER: {}", chat.getListeners());
        log.info("Sending INVITE to: {}", jid);
        try {
            chat.sendMessage(offerMessage);
        } catch (final XMPPException e1) {
            log.error("Could not send offer!!", e1);
            throw new IOException("Could not send offer", e1);
        }
    }
    
    private String persistentXmppConnection(final String username, 
        final String password, final String id) throws IOException {
        XMPPException exc = null;
        for (int i = 0; i < 20000; i++) {
            try {
                log.info("Attempting XMPP connection...");
                this.xmppOffererConnection = 
                    singleXmppConnection(username, password, id);
                log.info("Created offerer");
                addChatManagerListener(this.xmppOffererConnection);
                return this.xmppOffererConnection.getUser();
            } catch (final XMPPException e) {
                final String msg = "Error creating XMPP connection";
                log.error(msg, e);
                exc = e;
            }
            
            // Gradual backoff.
            try {
                Thread.sleep(i * 100);
            } catch (InterruptedException e) {
                log.info("Interrupted?", e);
            }
        }
        if (exc != null) {
            throw new IOException("Could not log in!!", exc);
        }
        else {
            throw new IOException("Could not log in?");
        }
    }
    
    private void addChatManagerListener(final XMPPConnection conn) {
        // TODO: Add a roster listener and make sure we're only processing 
        // messages from users on our roster.
        final ChatManager cm = conn.getChatManager();
        cm.addChatListener(new ChatManagerListener() {
            public void chatCreated(final Chat chat, 
                final boolean createdLocally) {
                log.info("Created a chat with: {}", chat.getParticipant());
                log.info("I am: {}", conn.getUser());
                //log.info("Message listeners on chat: {}", chat.getListeners());
                log.info("Created locally: " + createdLocally);
                chat.addMessageListener(new MessageListener() {
                    
                    public void processMessage(final Chat ch, final Message msg) {
                        log.info("Got message: {} from "+ch.getParticipant(),
                            msg);
                        final Object obj = 
                            msg.getProperty(P2PConstants.MESSAGE_TYPE);
                        if (obj == null) {
                            log.info("No message type!! Notifying listeners");
                            notifyListeners(ch, msg);
                            return;
                        }

                        final int mt = (Integer) obj;
                        switch (mt) {
                            case P2PConstants.INVITE:
                                log.info("Processing INVITE");
                                final String sdp = 
                                    (String) msg.getProperty(P2PConstants.SDP);
                                if (StringUtils.isNotBlank(sdp)) {
                                    processSdp(ch, Base64.decodeBase64(sdp));
                                }
                                break;
                            default:
                                log.info("Non-standard message on aswerer..." +
                                    "sending to additional listeners, if any: "+
                                    mt);
                                
                                notifyListeners(ch, msg);
                                break;
                        }
                    }

                    private void notifyListeners(final Chat ch, 
                        final Message msg) {
                        log.info("Notifying global listeners");
                        synchronized (messageListeners) {
                            if (messageListeners.isEmpty()) {
                                log.info("No message listeners to forward to");
                            }
                            for (final MessageListener ml : messageListeners) {
                                ml.processMessage(ch, msg);
                            }
                        }
                    }
                });
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
            msg.setTo(chat.getParticipant());
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
        msg.setTo(chat.getParticipant());
        log.info("Sending INVITE OK to {}", msg.getTo());
        try {
            chat.sendMessage(msg);
            log.info("Sent INVITE OK");
        } catch (final XMPPException e) {
            log.error("Could not send error message", e);
        }
        offerAnswer.processOffer(offer);

        log.debug("Done processing XMPP INVITE!!!");
    }

    private XMPPConnection singleXmppConnection(final String username, 
        final String password, final String id) throws XMPPException {
        final ConnectionConfiguration config = 
            new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
        config.setCompressionEnabled(true);
        config.setRosterLoadedAtLogin(true);
        config.setReconnectionAllowed(false);
        config.setSocketFactory(new SocketFactory() {
            
            @Override
            public Socket createSocket(final InetAddress host, final int port, 
                final InetAddress localHost, final int localPort) 
                throws IOException {
                // We ignore the local port binding.
                return createSocket(host, port);
            }
            
            @Override
            public Socket createSocket(final String host, final int port, 
                final InetAddress localHost, final int localPort)
                throws IOException, UnknownHostException {
                // We ignore the local port binding.
                return createSocket(host, port);
            }
            
            @Override
            public Socket createSocket(final InetAddress host, final int port) 
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
        
        return newConnection(username, password, config, id);
    }

    private XMPPConnection newConnection(final String username, 
        final String password, final ConnectionConfiguration config,
        final String id) throws XMPPException {
        final XMPPConnection conn = new XMPPConnection(config);
        conn.connect();
        
        conn.login(username, password, id);
        
        while (!conn.isAuthenticated()) {
            log.info("Waiting for authentication");
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e1) {
                log.error("Exception during sleep?", e1);
            }
        }
        
        conn.addConnectionListener(new ConnectionListener() {
            
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
                    persistentXmppConnection(username, password, id);
                } catch (final IOException e1) {
                    log.error("Could not re-establish connection?", e1);
                }
            }
            
            public void connectionClosed() {
                log.info("XMPP connection closed. Creating new connection.");
                try {
                    persistentXmppConnection(username, password, id);
                } catch (final IOException e1) {
                    log.error("Could not re-establish connection?", e1);
                }
            }
        });
        
        return conn;
    }

    public XMPPConnection getXmppConnection() {
        return xmppOffererConnection;
    }

    public void addMessageListener(final MessageListener ml) {
        messageListeners.add(ml);
    }
}
