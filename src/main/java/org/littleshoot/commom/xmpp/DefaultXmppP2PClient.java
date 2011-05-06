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
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.lastbamboo.common.offer.answer.AnswererOfferAnswerListener;
import org.lastbamboo.common.offer.answer.IceMediaStreamDesc;
import org.lastbamboo.common.offer.answer.NoAnswerException;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerConnectException;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerMessage;
import org.lastbamboo.common.offer.answer.OfferAnswerTransactionListener;
import org.lastbamboo.common.p2p.DefaultTcpUdpSocket;
import org.lastbamboo.common.p2p.P2PConstants;
import org.littleshoot.mina.common.ByteBuffer;
import org.littleshoot.util.CipherSocket;
import org.littleshoot.util.CommonUtils;
import org.littleshoot.util.KeyStorage;
import org.littleshoot.util.SessionSocketListener;
import org.littleshoot.util.mina.MinaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of an XMPP P2P client connection.
 */
public class DefaultXmppP2PClient implements XmppP2PClient {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final OfferAnswerFactory offerAnswerFactory;

    private XMPPConnection xmppConnection;
    
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

    private final String host;

    private final int port;

    private final String serviceName;

    private final SessionSocketListener callSocketListener;

    private final InetSocketAddress plainTextRelayAddress;
    
    private final ExecutorService messageProcessingExecutor = 
        Executors.newCachedThreadPool();

    //private final SessionSocketListener sessionListener;
    
    public static DefaultXmppP2PClient newGoogleTalkClient(
        final OfferAnswerFactory factory,
        final InetSocketAddress plainTextRelayAddress, 
        final SessionSocketListener callSocketListener,final int relayWait) {
        return new DefaultXmppP2PClient(factory, plainTextRelayAddress, 
            callSocketListener, relayWait, "talk.google.com", 5222, "gmail.com");
    }

    /*
    public static DefaultXmppP2PClient newFacebookChatClient(
        final OfferAnswerFactory factory,
        final SessionSocketListener socketListener, 
        final SessionSocketListener callSocketListener, final int relayWait) {
        return new DefaultXmppP2PClient(factory, socketListener, 
            callSocketListener, relayWait, "chat.facebook.com", 5222, 
            "chat.facebook.com");
    }
    */
    
    private DefaultXmppP2PClient(final OfferAnswerFactory offerAnswerFactory,
        final InetSocketAddress plainTextRelayAddress,
        final SessionSocketListener callSocketListener,
        final int relayWaitTime, final String host, final int port, 
        final String serviceName) {
        this.offerAnswerFactory = offerAnswerFactory;
        this.plainTextRelayAddress = plainTextRelayAddress;
        this.callSocketListener = callSocketListener;
        this.relayWaitTime = relayWaitTime;
        this.host = host;
        this.port = port;
        this.serviceName = serviceName;
    }
    
    public Socket newSocket(final URI uri) 
        throws IOException, NoAnswerException {
        log.trace ("Creating XMPP socket for URI: {}", uri);
        return newSocket(uri, IceMediaStreamDesc.newReliable());
    }
    
    public Socket newUnreliableSocket(final URI uri) 
        throws IOException, NoAnswerException {
        log.trace ("Creating XMPP socket for URI: {}", uri);
        return newSocket(uri, IceMediaStreamDesc.newUnreliableUdpStream());
    }
    
    private Socket newSocket(final URI uri, 
        final IceMediaStreamDesc streamDesc) 
        throws IOException, NoAnswerException {
        log.trace ("Creating XMPP socket for URI: {}", uri);
        
        // Note we use a short timeout for waiting for answers. This is 
        // because we've seen XMPP messages get lost in the ether, and we 
        // just want to send a few of them quickly when this does happen.
        final DefaultTcpUdpSocket tcpUdpSocket = 
            new DefaultTcpUdpSocket(this, this.offerAnswerFactory,
                this.relayWaitTime, 10 * 1000, streamDesc);
        
        final Socket sock = tcpUdpSocket.newSocket(uri);
        log.info("Creating new CipherSocket");
        return new CipherSocket(sock, tcpUdpSocket.getWriteKey(), 
            tcpUdpSocket.getReadKey());
    }

    public String login(final String username, final String password) 
        throws IOException {
        return persistentXmppConnection(username, password, "SHOOT-");
    }
    
    public String login(final String username, final String password,
        final String id) throws IOException {
        return persistentXmppConnection(username, password, id);
    }
    
    public void offer(final URI uri, final byte[] offer,
        final OfferAnswerTransactionListener transactionListener, 
        final KeyStorage keyStorage) 
        throws IOException {
        
        final Runnable runner = new Runnable() {
            public void run() {
                try {
                    xmppOffer(uri, offer, transactionListener, keyStorage);
                }
                catch (final Throwable t) {
                    log.error("Unexpected throwable", t);
                }
            }
        };
        
        this.messageExecutor.execute(runner);
    }
    
    private void xmppOffer(final URI uri, final byte[] offer,
        final OfferAnswerTransactionListener transactionListener, 
        final KeyStorage keyStorage) throws IOException {
        // We need to convert the URI to a XMPP/Jabber JID.
        final String jid = uri.toASCIIString();
        
        final ChatManager chatManager = xmppConnection.getChatManager();
        final Message offerMessage = new Message();
        log.info("Creating offer to: {}", jid);
        log.info("Sending offer: {}", new String(offer));
        final String base64Sdp = 
            Base64.encodeBase64URLSafeString(offer);
        offerMessage.setProperty(P2PConstants.MESSAGE_TYPE, P2PConstants.INVITE);
        offerMessage.setProperty(P2PConstants.SDP, base64Sdp);
        offerMessage.setProperty(P2PConstants.SECRET_KEY, 
            Base64.encodeBase64String(keyStorage.getWriteKey()));
        log.info("Creating chat from: {}", xmppConnection.getUser());
        final Chat chat = chatManager.createChat(jid, 
            new MessageListener() {
                public void processMessage(final Chat ch, final Message msg) {
                    log.info("Got answer on offerer: {}", msg);
                    messageProcessingExecutor.execute(new XmppInviteOkRunner(msg, 
                        transactionListener, keyStorage));
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
                this.xmppConnection = 
                    singleXmppConnection(username, password, id);
                log.info("Created offerer");
                addChatManagerListener(this.xmppConnection);
                return this.xmppConnection.getUser();
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
                        messageProcessingExecutor.execute(
                            new XmppInviteRunner(ch, msg));
                    }
                });
            }
        });
    }
    
    private void processInvite(final Chat chat, final Message msg) {
        final String readString = 
            (String) msg.getProperty(P2PConstants.SECRET_KEY);
        final byte[] readKey = Base64.decodeBase64(readString);
        final String sdp = (String) msg.getProperty(P2PConstants.SDP);
        final ByteBuffer offer = ByteBuffer.wrap(Base64.decodeBase64(sdp));
        final String offerString = MinaUtils.toAsciiString(offer);
        
        final byte[] answerKey = CommonUtils.generateKey();
        final OfferAnswer offerAnswer;
        try {
            offerAnswer = this.offerAnswerFactory.createAnswerer(
                new AnswererOfferAnswerListener(chat.getParticipant(), 
                    this.plainTextRelayAddress, callSocketListener, 
                    offerString, answerKey, readKey));
        }
        catch (final OfferAnswerConnectException e) {
            // This indicates we could not establish the necessary connections 
            // for generating our candidates.
            log.warn("We could not create candidates for offer: " + sdp, e);
            final Message error = new Message();
            error.setProperty(P2PConstants.MESSAGE_TYPE, 
                P2PConstants.INVITE_ERROR);
            error.setTo(chat.getParticipant());
            try {
                chat.sendMessage(error);
            } catch (final XMPPException e1) {
                log.error("Could not send error message", e1);
            }
            //this.m_sipClient.writeInviteRejected(invite, 488, 
            //"Not Acceptable Here");
            return;
        }
        final byte[] answer = offerAnswer.generateAnswer();
        final Message inviteOk = new Message();
        inviteOk.setProperty(P2PConstants.MESSAGE_TYPE, P2PConstants.INVITE_OK);
        inviteOk.setProperty(P2PConstants.SDP, 
            Base64.encodeBase64String(answer));
        inviteOk.setProperty(P2PConstants.SECRET_KEY, 
            Base64.encodeBase64String(answerKey));
        inviteOk.setTo(chat.getParticipant());
        log.info("Sending INVITE OK to {}", inviteOk.getTo());
        try {
            chat.sendMessage(inviteOk);
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
            //new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
            new ConnectionConfiguration(this.host, this.port, this.serviceName);
        config.setCompressionEnabled(true);
        config.setRosterLoadedAtLogin(true);
        config.setReconnectionAllowed(false);
        
        // TODO: This should probably be an SSLSocketFactory no??
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
        
        log.info("Connection is Secure: {}", conn.isSecureConnection());
        log.info("Connection is TLS: {}", conn.isUsingTLS());
        conn.login(username, password, id);
        
        while (!conn.isAuthenticated()) {
            log.info("Waiting for authentication");
            try {
                Thread.sleep(400);
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
        return xmppConnection;
    }

    public void addMessageListener(final MessageListener ml) {
        messageListeners.add(ml);
    }
    

    /**
     * Runnable for off-loading INVITE OK processing to a thread pool.
     */
    private final class XmppInviteOkRunner implements Runnable {

        private final Message msg;
        private final OfferAnswerTransactionListener transactionListener;
        private final KeyStorage keyStorage;

        private XmppInviteOkRunner(final Message msg, 
            final OfferAnswerTransactionListener transactionListener, 
            final KeyStorage keyStorage) {
            this.msg = msg;
            this.transactionListener = transactionListener;
            this.keyStorage = keyStorage;
            
        }
        
        public void run() {
            final byte[] body = CommonUtils.decodeBase64(
                (String) msg.getProperty(P2PConstants.SDP));
            final byte[] key = CommonUtils.decodeBase64(
                (String) msg.getProperty(P2PConstants.SECRET_KEY));
            keyStorage.setReadKey(key);
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
    }
    
    /**
     * Runnable for off-loading INVITE processing to a thread pool.
     */
    private final class XmppInviteRunner implements Runnable {

        private final Chat chat;
        private final Message msg;

        private XmppInviteRunner(final Chat chat, final Message msg) {
            this.chat = chat;
            this.msg = msg;
            
        }
        
        public void run() {
            log.info("Got message: {} from "+chat.getParticipant(), msg);
            final Object obj = 
                msg.getProperty(P2PConstants.MESSAGE_TYPE);
            if (obj == null) {
                log.info("No message type!! Notifying listeners");
                notifyListeners();
                return;
            }

            final int mt = (Integer) obj;
            switch (mt) {
                case P2PConstants.INVITE:
                    log.info("Processing INVITE");
                    processInvite(chat, msg);
                    break;
                default:
                    log.info("Non-standard message on aswerer..." +
                        "sending to additional listeners, if any: "+ mt);
                    
                    notifyListeners();
                    break;
            }
        }

        private void notifyListeners() {
            log.info("Notifying global listeners");
            synchronized (messageListeners) {
                if (messageListeners.isEmpty()) {
                    log.info("No message listeners to forward to");
                }
                for (final MessageListener ml : messageListeners) {
                    ml.processMessage(chat, msg);
                }
            }
        }
    }

}
