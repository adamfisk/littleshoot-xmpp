package org.littleshoot.commom.xmpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.security.auth.login.CredentialException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.lastbamboo.common.offer.answer.AnswererOfferAnswerListener;
import org.lastbamboo.common.offer.answer.IceMediaStreamDesc;
import org.lastbamboo.common.offer.answer.NoAnswerException;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerConnectException;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.offer.answer.OfferAnswerMessage;
import org.lastbamboo.common.offer.answer.OfferAnswerTransactionListener;
import org.lastbamboo.common.offer.answer.Offerer;
import org.lastbamboo.common.p2p.DefaultTcpUdpSocket;
import org.lastbamboo.common.p2p.P2PConnectionEvent;
import org.lastbamboo.common.p2p.P2PConnectionListener;
import org.lastbamboo.common.p2p.P2PConstants;
import org.littleshoot.mina.common.ByteBuffer;
import org.littleshoot.util.CommonUtils;
import org.littleshoot.util.KeyStorage;
import org.littleshoot.util.PublicIp;
import org.littleshoot.util.SessionSocketListener;
import org.littleshoot.util.mina.MinaUtils;
import org.littleshoot.util.xml.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Default implementation of an XMPP P2P client connection.
 */
public class ControlXmppP2PClient implements XmppP2PClient {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final Map<Long, TransactionData> transactionIdsToProcessors =
        new ConcurrentHashMap<Long, TransactionData>();
    
    private static final Map<String, Socket> incomingControlSockets = 
        new ConcurrentHashMap<String, Socket>();

    private static final int TIMEOUT = 60 * 60 * 1000;

    private final OfferAnswerFactory offerAnswerFactory;

    private XMPPConnection xmppConnection;
    
    private final Collection<MessageListener> messageListeners =
        new ArrayList<MessageListener>();

    private final int relayWaitTime;

    private String xmppServiceName;

    private final SessionSocketListener callSocketListener;

    private final InetSocketAddress plainTextRelayAddress;

    private static final ExecutorService exec = 
        Executors.newCachedThreadPool(new ThreadFactory() {
        
        private AtomicInteger counter = new AtomicInteger(0);
        
        @Override
        public Thread newThread(final Runnable r) {
            final Thread thread = 
                new Thread(r, "ControlXmppP2PClient-Thread-Pool-"+
                    counter.incrementAndGet());
            return thread;
        }
    });
    
    private final Map<URI, SSLSocket> outgoingControlSockets = 
        new ConcurrentHashMap<URI, SSLSocket>();

    private final boolean useRelay;
    
    private final Set<String> sentMessageIds = new HashSet<String>();
    
    private final Map<URI, InetSocketAddress> urisToMappedServers =
        new ConcurrentHashMap<URI, InetSocketAddress>();

    private final PublicIp publicIp;

    private String xmppServerHost;

    private int xmppServerPort;

    private final SocketFactory socketFactory;

    private AtomicBoolean loggedOut = new AtomicBoolean(true);

    
    public static ControlXmppP2PClient newGoogleTalkDirectClient(
        final OfferAnswerFactory factory,
        final InetSocketAddress plainTextRelayAddress, 
        final SessionSocketListener callSocketListener, final int relayWait,
        final PublicIp publicIp, final SocketFactory socketFactory) {
        return new ControlXmppP2PClient(factory, plainTextRelayAddress, 
            //callSocketListener, relayWait, "talk.google.com", 5222, "talk.google.com",
            callSocketListener, relayWait, "talk.google.com", 5222, "gmail.com", 
            false, publicIp, socketFactory);
    }
    
    public static ControlXmppP2PClient newClient(
        final OfferAnswerFactory factory,
        final InetSocketAddress plainTextRelayAddress, 
        final SessionSocketListener callSocketListener, final int relayWait,
        final PublicIp publicIp, final SocketFactory socketFactory,
        final String host, final int port, final String serviceName) {
        return new ControlXmppP2PClient(factory, plainTextRelayAddress, 
            callSocketListener, relayWait, host, port, serviceName, 
            false, publicIp, socketFactory);
    }

    /*
    public static ControlXmppP2PClient newGoogleTalkClient(
        final OfferAnswerFactory factory,
        final InetSocketAddress plainTextRelayAddress, 
        final SessionSocketListener callSocketListener, final int relayWait,
        final PublicIp publicIp) {
        return new ControlXmppP2PClient(factory, plainTextRelayAddress, 
            callSocketListener, relayWait, "talk.google.com", 5222, "gmail.com", 
            true, publicIp);
    }
    */

    private ControlXmppP2PClient(final OfferAnswerFactory offerAnswerFactory,
        final InetSocketAddress plainTextRelayAddress,
        final SessionSocketListener callSocketListener,
        final int relayWaitTime, final String host, final int port, 
        final String serviceName, final boolean useRelay,
        final PublicIp publicIp, final SocketFactory socketFactory) {
        this.offerAnswerFactory = offerAnswerFactory;
        this.plainTextRelayAddress = plainTextRelayAddress;
        this.callSocketListener = callSocketListener;
        this.relayWaitTime = relayWaitTime;
        
        this.xmppServerHost = host;
        this.xmppServerPort = port;
        this.xmppServiceName = serviceName;
        this.useRelay = useRelay;
        this.publicIp = publicIp;
        this.socketFactory = socketFactory;
    }
    
    @Override
    public Socket newSocket(final URI uri) 
        throws IOException, NoAnswerException {
        log.trace ("Creating XMPP socket for URI: {}", uri);
        if (useRelay) {
            return newSocket(uri, IceMediaStreamDesc.newReliable(), false);
        }
        return newSocket(uri, IceMediaStreamDesc.newReliableNoRelay(), false);
    }
    
    @Override
    public Socket newUnreliableSocket(final URI uri) 
        throws IOException, NoAnswerException {
        log.trace ("Creating XMPP socket for URI: {}", uri);
        if (useRelay) {
            return newSocket(uri, IceMediaStreamDesc.newUnreliableUdpStream(), 
                false);
        }
        return newSocket(uri, 
            IceMediaStreamDesc.newUnreliableUdpStreamNoRelay(), false);
    }
    
    @Override
    public Socket newRawSocket(final URI uri) 
        throws IOException, NoAnswerException {
        log.trace ("Creating XMPP socket for URI: {}", uri);
        if (useRelay) {
            return newSocket(uri, IceMediaStreamDesc.newReliable(), true);
        }
        return newSocket(uri, IceMediaStreamDesc.newReliableNoRelay(), true);
    }
    
    @Override
    public Socket newRawUnreliableSocket(final URI uri) 
        throws IOException, NoAnswerException {
        log.trace ("Creating XMPP socket for URI: {}", uri);
        if (useRelay) {
            return newSocket(uri, IceMediaStreamDesc.newUnreliableUdpStream(), 
                true);
        }
        return newSocket(uri, 
            IceMediaStreamDesc.newUnreliableUdpStreamNoRelay(), true);
    }
    
    private Socket newSocket(final URI uri, 
        final IceMediaStreamDesc streamDesc, final boolean raw) 
        throws IOException, NoAnswerException {
        log.trace ("Creating XMPP socket for URI: {}", uri);
        final String us = this.xmppConnection.getUser().trim();
        log.trace("Our JID is: "+us);
        if (us.equals(uri.toASCIIString())) {
            log.info("Not connecting to ourselves.");
            throw new IOException("Not connecting to ourselves: "+us);
        }
        
        // If the remote host has their ports mapped, we just use those.
        if (streamDesc.isTcp() && urisToMappedServers.containsKey(uri)) {
            log.info("USING MAPPED PORT SERVER!");
            return newMappedServerSocket(uri, raw);
        }
        
        final SSLSocket control = controlSocket(uri, streamDesc);
        
        
        if (streamDesc.isTcp() && urisToMappedServers.containsKey(uri) &&
            (control instanceof SSLSocket)) {
            log.info("USING MAPPED PORT SERVER AFTER CONTROL!");
            // No reason to keep the control socket around if we have the
            // mapped port. Note we do go through with creating the control in
            // any case to avoid getting into weird states with socket 
            // negotiation on both the local and the remote sides.
            IOUtils.closeQuietly(control);
            return newMappedServerSocket(uri, raw);
        }

        // Note we use a short timeout for waiting for answers. This is 
        // because we've seen XMPP messages get lost in the ether, and we 
        // just want to send a few of them quickly when this does happen.
        final DefaultTcpUdpSocket tcpUdpSocket = 
            new DefaultTcpUdpSocket(
                new OffererOverControlSocket(control, streamDesc), 
                this.offerAnswerFactory,
                this.relayWaitTime, 20 * 1000, streamDesc);
        
        log.info("Trying to create new socket...raw="+raw);
        final Socket sock = tcpUdpSocket.newSocket(uri);
        return sock;
        /*
        if (raw || sock instanceof SSLSocket) {
            log.info("Returning raw socket");
            return sock;
        }
        final byte[] writeKey = tcpUdpSocket.getWriteKey();
        final byte[] readKey = tcpUdpSocket.getReadKey();
        log.info("Creating new CipherSocket with write key {} and read key {}", 
            writeKey, readKey);
        return new CipherSocket(sock, writeKey, readKey);
        */
    }
    
    private Socket newMappedServerSocket(final URI uri, final boolean raw) 
        throws IOException {
        final InetSocketAddress serverIp = urisToMappedServers.get(uri);
        final Socket sock;
        if (raw) {
            log.info("Creating raw socket and skipping socket factory");
            sock = new Socket();
        } else {
            log.info("Using socket factory: {}", this.socketFactory);
            sock = this.socketFactory.createSocket();
        }
        try {
            sock.connect(serverIp, 40 * 1000);
            return sock;
        } catch (final IOException e) {
            log.info("Could not connect -- peer offline?", e);
            urisToMappedServers.remove(uri);
            throw e;
        }
    }

    private SSLSocket controlSocket(final URI uri, 
        final IceMediaStreamDesc streamDesc) throws IOException, 
        NoAnswerException {
        // We want to synchronized on the control sockets and block new 
        // incoming sockets because it's pointless for them to do much before
        // the control socket is established, since that's how they'll connect
        // themselves.
        synchronized (this.outgoingControlSockets) {
            if (!this.outgoingControlSockets.containsKey(uri)) {
                log.info("Creating new control socket");
                final SSLSocket control = establishControlSocket(uri, streamDesc);
                return control;
            } else {
                log.info("Using existing control socket");
                final SSLSocket control = this.outgoingControlSockets.get(uri);
                if (!control.isClosed()) {
                    return control;
                }
                log.info("Establishing new control socket");
                final SSLSocket newControl = 
                    establishControlSocket(uri, streamDesc);
                return newControl;
            }
        }
    }

    private String username;

    private String password;

    private String connectionId;

    private final AtomicInteger connectionAttempts = new AtomicInteger(0);

    private final AtomicBoolean connecting = new AtomicBoolean(false);
    
    private void notifyConnectionListeners(final URI jid, final Socket sock, 
        final boolean incoming, final boolean connected) {
        notifyConnectionListeners(jid.toASCIIString(), sock, incoming, connected);
    }
    
    private void notifyConnectionListeners(final String jid, final Socket sock, 
        final boolean incoming, final boolean connected) {
        final Runnable runner = new Runnable() {
            @Override
            public void run() {
                final P2PConnectionEvent event = 
                    new P2PConnectionEvent(jid, sock, incoming, connected);
                synchronized (listeners) {
                    for (final P2PConnectionListener listener : listeners) {
                        listener.onConnectivityEvent(event);
                    }
                }
            }
        };
        exec.execute(runner);
    }

    private SSLSocket establishControlSocket(final URI uri, 
        final IceMediaStreamDesc streamDesc) throws IOException, 
        NoAnswerException {
        final DefaultTcpUdpSocket tcpUdpSocket = 
            new DefaultTcpUdpSocket(this, this.offerAnswerFactory,
                this.relayWaitTime, 30 * 1000, streamDesc);
        
        final Socket rawSock = tcpUdpSocket.newSocket(uri);
        log.info("Raw sock class: {}", rawSock.getClass());
        final SSLSocket sock = (SSLSocket) rawSock;
        sock.setSoTimeout(TIMEOUT);
        log.info("Created control socket: {}", sock);
        
        /*
        final byte[] writeKey = tcpUdpSocket.getWriteKey();
        final byte[] readKey = tcpUdpSocket.getReadKey();
        
        final Socket cs;
        if (sock instanceof SSLSocket) {
            log.info("Control socket is an SSL socket -- not using cipher socket");
            cs = sock;
        } else {
            log.info("Control socket is a UDP cipher socket");
            log.info("Creating new CipherSocket with write key {} and read key {}", 
                    writeKey, readKey);
            cs =  new CipherSocket(sock, writeKey, readKey);
            
            // It's rare that UDP sockets will resolve faster than TCP
            // sockets -- more likely there was some error creating the
            // TCP socket, so we should remove the mapped server URI --
            // there was likely in fact a problem with the mapping.
            this.urisToMappedServers.remove(uri);
        }
        */
        
        notifyConnectionListeners(uri, sock, false, true);
        this.outgoingControlSockets.put(uri, sock);
        return (SSLSocket) sock;
    }

    @Override
    public String login(final String user, final String pass) 
        throws IOException, CredentialException {
        return login(user, pass, "SHOOT-");
    }
    
    @Override
    public String login(final String user, final String pass, 
        final String serverHost, final int serverPort, final String serviceName) 
        throws IOException, CredentialException {
        return login(user, pass, serverHost, serverPort, serviceName, "SHOOT-");
    }
    
    @Override
    public String login(final String user, final String pass,
        final String id) throws IOException, 
        CredentialException {
        return login(user, pass, this.xmppServerHost, this.xmppServerPort, 
            this.xmppServiceName, id);
    }
    
    
    @Override
    public String login(final String user, final String pass, 
        final String serverHost, final int serverPort, 
        final String serviceName, final String id) 
        throws CredentialException, IOException {
        if (this.connecting.get()) {
            throw new IOException("Already attempting connection");
        }
        this.loggedOut.set(false);
        this.username = user;
        this.password = pass;
        this.xmppServerHost = serverHost;
        
        if ("talk.google.com".equals(this.xmppServerHost)) {
            this.xmppServerPort = 5222;
            this.xmppServiceName = "gmail.com";
            if (!user.contains("@")) {
                this.username = user + "@gmail.com";
            }
        } else {
            this.xmppServerPort = serverPort;
            this.xmppServiceName = serviceName;
        }
        this.connectionId = id;
        final int att = this.connectionAttempts.incrementAndGet();
        final int retries = 100 - att;
        if (retries < 1) {
            throw new IOException("Already reached maximum number of attempts");
        }
        this.connecting.set(true);
        try {
            this.xmppConnection = XmppUtils.persistentXmppConnection(username, 
                password, id, retries, this.xmppServerHost, this.xmppServerPort, 
                this.xmppServiceName, this);
        } catch (final CredentialException e) {
            this.connecting.set(false);
            throw e;
        } catch (final IOException e) {
            this.connecting.set(false);
            throw e;
        }
        this.connecting.set(false);
        processMessages();
        return this.xmppConnection.getUser();
    }
    
    @Override
    public void offer(final URI uri, final byte[] offer,
        final OfferAnswerTransactionListener transactionListener, 
        final KeyStorage keyStorage) throws IOException {
        // We need to convert the URI to a XMPP/Jabber JID.
        final String jid = uri.toASCIIString();
        final Message offerMessage = 
            newInviteToEstablishControlSocket(jid, offer, transactionListener, 
                keyStorage);
        XmppUtils.goOffTheRecord(jid, xmppConnection);
        xmppConnection.sendPacket(offerMessage);
    }
    
    private Message newInviteToEstablishControlSocket(final String jid, 
        final byte[] offer, 
        final OfferAnswerTransactionListener transactionListener,
        final KeyStorage keyStorage) {
        final long id = RandomUtils.nextLong();
        transactionIdsToProcessors.put(id, 
            new TransactionData(transactionListener, keyStorage));
        //transactionIdsToProcessors.put(id, td);
        final Message msg = new Message();
        msg.setTo(jid);
        log.info("Sending offer: {}", new String(offer));
        final String base64Sdp = 
            Base64.encodeBase64URLSafeString(offer);
        msg.setProperty(P2PConstants.TRANSACTION_ID, id);
        msg.setProperty(P2PConstants.MESSAGE_TYPE, P2PConstants.INVITE);
        msg.setProperty(P2PConstants.SDP, base64Sdp);
        msg.setProperty(P2PConstants.CONTROL, "true");
        //msg.setProperty(P2PConstants.SECRET_KEY, 
        //    Base64.encodeBase64String(keyStorage.getWriteKey()));
        return msg;
    }
    
    private void processMessages() {
        final PacketFilter filter = new PacketTypeFilter(Message.class);
        final PacketListener myListener = new PacketListener() {
            @Override
            public void processPacket(final Packet packet) {
                final Message msg = (Message) packet;
                final String id = msg.getPacketID();
                log.info("Checking message ID: {}", id);
                if (loggedOut.get()) {
                    log.warn("Got a message while logged out?");
                    return;
                }
                if (sentMessageIds.contains(id)) {
                    log.warn("Message is from us!!");
                    
                    // This is a little silly in that we're sending a 
                    // message back to ourselves, but this signals to the 
                    // client thread right away that the invite has failed.
                    final Message error = newError(msg);
                    xmppConnection.sendPacket(error);
                } else {
                    exec.execute(new PacketProcessor(msg));
                }
            }
        };
        // Register the listener.
        this.xmppConnection.addPacketListener(myListener, filter);
    }
    
    protected Message newError(final Message msg) {
        return newError(msg.getFrom(), 
            (Long)msg.getProperty(P2PConstants.TRANSACTION_ID));
    }
    
    protected Message newError(final String from, final Long tid) {
        final Message error = new Message();
        error.setProperty(P2PConstants.MESSAGE_TYPE, 
            P2PConstants.INVITE_ERROR);
        if (tid != null) {
            error.setProperty(P2PConstants.TRANSACTION_ID, tid);
        }
        error.setTo(from);
        return error;
    }

    /**
     * This processes an INVITE to establish a control socket.
     * 
     * @param msg The INVITE message received from the XMPP server to establish
     * the control socket.
     */
    private void processInviteToEstablishControlSocket(final Message msg) {
        //final String readString = 
        //    (String) msg.getProperty(P2PConstants.SECRET_KEY);
        //final byte[] readKey = Base64.decodeBase64(readString);
        //final byte[] writeKey = CommonUtils.generateKey();
        final String sdp = (String) msg.getProperty(P2PConstants.SDP);
        final ByteBuffer offer = ByteBuffer.wrap(Base64.decodeBase64(sdp));
        final String offerString = MinaUtils.toAsciiString(offer);
        log.info("Processing offer: {}", offerString);
        
        final OfferAnswer offerAnswer;
        try {
            offerAnswer = this.offerAnswerFactory.createAnswerer(
                new ControlSocketOfferAnswerListener(msg.getFrom()), false);
        }
        catch (final OfferAnswerConnectException e) {
            // This indicates we could not establish the necessary connections 
            // for generating our candidates.
            log.warn("We could not create candidates for offer: " + sdp, e);
            
            final Message error = newError(msg);
            xmppConnection.sendPacket(error);
            return;
        }
        final byte[] answer = offerAnswer.generateAnswer();
        final long tid = (Long) msg.getProperty(P2PConstants.TRANSACTION_ID);
        
        final Message inviteOk = newInviteOk(tid, answer);
        final String to = msg.getFrom();
        inviteOk.setTo(to);
        log.info("Sending CONTROL INVITE OK to {}", inviteOk.getTo());
        XmppUtils.goOffTheRecord(to, xmppConnection);
        xmppConnection.sendPacket(inviteOk);

        offerAnswer.processOffer(offer);
        log.debug("Done processing CONTROL XMPP INVITE!!!");
    }
    
    private Message newInviteOk(final Long tid, final byte[] answer) {
        final Message inviteOk = new Message();
        if (tid != null) {
            inviteOk.setProperty(P2PConstants.TRANSACTION_ID, tid.longValue());
        }
        inviteOk.setProperty(P2PConstants.MESSAGE_TYPE, P2PConstants.INVITE_OK);
        inviteOk.setProperty(P2PConstants.SDP, 
            Base64.encodeBase64String(answer));
        
        if (this.offerAnswerFactory.isAnswererPortMapped()) {
            inviteOk.setProperty(P2PConstants.MAPPED_PORT, 
                this.offerAnswerFactory.getMappedPort());
            inviteOk.setProperty(P2PConstants.PUBLIC_IP, 
                this.publicIp.getPublicIpAddress().getHostAddress());
        }
        return inviteOk;
    }
    

    private final class TransactionData {

        private final OfferAnswerTransactionListener transactionListener;
        private final KeyStorage keyStorage;

        private TransactionData(
            final OfferAnswerTransactionListener transactionListener,
            final KeyStorage keyStorage) {
            this.transactionListener = transactionListener;
            this.keyStorage = keyStorage;
        }
        
    }
    
    /**
     * Runnable for processing incoming packets. These will can be Presence 
     * packets, info packets from the controller, INVITEs, INVITE OKs, etc.
     */
    private final class PacketProcessor implements Runnable {

        private final Message msg;

        private PacketProcessor(final Message msg) {
            this.msg = msg;
        }
        
        @Override
        public void run() {
            log.info("Got message from {}", msg.getFrom());
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
                    log.info("Processing CONTROL INVITE");
                    processInviteToEstablishControlSocket(msg);
                    break;
                case P2PConstants.INVITE_OK:
                    // We just pass these along to the other listener -- 
                    // sometimes this listener can get notified first for
                    // whatever reason.
                    log.info("Got INVITE_OK");
                    final TransactionData okTd = toTransactionData();
                    if (okTd == null) {
                        log.error("No matching transaction ID?");
                    } else {
                        log.info("Got transaction data!!");
                        // This also sets the read key.
                        final OfferAnswerMessage oam = toOfferAnswerMessage(okTd);
                        addMappedServer();
                        okTd.transactionListener.onTransactionSucceeded(oam);
                    }
                    break;
                case P2PConstants.INVITE_ERROR:
                    // This can happen when a message is in fact from us, and
                    // we send an error message to ourselves, for example. 
                    // We'll see messages from us when trying to send them to
                    // non-existent peers, for example.
                    log.info("Got INVITE_ERROR - transaction failed");
                    final TransactionData eTd = toTransactionData();
                    if (eTd == null) {
                        log.error("No matching transaction ID?");
                    } else {
                        final OfferAnswerMessage oam = toOfferAnswerMessage(eTd);
                        eTd.transactionListener.onTransactionFailed(oam);
                    }
                    break;
                default:
                    log.info("Non-standard message on aswerer..." +
                        "sending to additional listeners, if any: "+ mt);
                    notifyListeners();
                    break;
            }
        }

        private TransactionData toTransactionData() {
            final Long id = 
                (Long) msg.getProperty(P2PConstants.TRANSACTION_ID);
            return transactionIdsToProcessors.remove(id);
        }

        private OfferAnswerMessage toOfferAnswerMessage(
            final TransactionData td) {
            final byte[] body = CommonUtils.decodeBase64(
                (String) msg.getProperty(P2PConstants.SDP));
            //final byte[] key = CommonUtils.decodeBase64(
            //    (String) msg.getProperty(P2PConstants.SECRET_KEY));
            //td.keyStorage.setReadKey(key);
            return new OfferAnswerMessage() {
                @Override
                public String getTransactionKey() {
                    return String.valueOf(hashCode());
                }
                @Override
                public ByteBuffer getBody() {
                    return ByteBuffer.wrap(body);
                }
            };
        }

        private boolean addMappedServer() {
            final String remoteIp = 
                (String) msg.getProperty(P2PConstants.PUBLIC_IP);
            log.info("Got public IP address: {}", remoteIp);
            if (StringUtils.isNotBlank(remoteIp)) {
                final Integer port = 
                    (Integer) msg.getProperty(P2PConstants.MAPPED_PORT);
                if (port != null) {
                    final InetSocketAddress mapped =
                        new InetSocketAddress(remoteIp, port);
                    log.info("ADDING MAPPED SERVER PORT!!");
                    try {
                        urisToMappedServers.put(new URI(msg.getFrom()), mapped);
                    } catch (final URISyntaxException e) {
                        log.error("Bad URI?", msg.getFrom());
                    }
                    return true;
                } 
            }
            return false;
        }
        
        private void notifyListeners() {
            log.info("Notifying global listeners");
            synchronized (messageListeners) {
                if (messageListeners.isEmpty()) {
                    log.info("No message listeners to forward to");
                }
                for (final MessageListener ml : messageListeners) {
                    ml.processMessage(null, msg);
                }
            }
        }
        
        @Override
        public String toString() {
            return "INVITE Runner for Chat with: "+msg.getFrom();
        }
    }
    
    /**
     * This class sends offers over an established control socket.
     */
    private class OffererOverControlSocket implements Offerer {

        private SSLSocket control;
        private final IceMediaStreamDesc streamDesc;

        private OffererOverControlSocket(final SSLSocket control, 
            final IceMediaStreamDesc streamDesc) {
            this.control = control;
            this.streamDesc = streamDesc;
        }

        @Override
        public void offer(final URI uri, final byte[] offer,
            final OfferAnswerTransactionListener transactionListener,
            final KeyStorage keyStore) {
            log.info("Sending message from local address: {}", 
                this.control.getLocalSocketAddress());
            synchronized (this.control) {
                log.info("Got lock on control socket...");
                final Message msg = 
                    newInviteOverControlSocket(uri.toASCIIString(), offer, keyStore);
                final String xml = toXml(msg);
                log.info("Writing XML offer on control socket: {}", xml);
                
                // We just block on a single offer and answer.
                
                // We also need to catch IOExceptions here for when the control
                // socket is broken for some reason.
                try {
                    writeToControlSocket(xml);
                } catch (final IOException e) {
                    closeOutgoing(uri, control);
                    log.info("Control socket timed out? We'll try to " +
                        "establish a new one", e);
                    try {
                        this.control = establishControlSocket(uri, streamDesc);
                        writeToControlSocket(xml);
                    } catch (final IOException ioe) {
                        log.warn("Still could not establish or write to " +
                            "new control socket -- try " +
                            "-Djavax.net.debug=ssl:record or " +
                            "System.setProperty(\"javax.net.debug\", \"ssl:record\");", ioe);
                        closeOutgoing(uri, control);
                        return;
                    } catch (final NoAnswerException nae) {
                        log.warn("Still could not establish or write to " +
                            "new control socket -- try " +
                            "-Djavax.net.debug=ssl:record or " +
                            "System.setProperty(\"javax.net.debug\", \"ssl:record\");", nae);
                        closeOutgoing(uri, control);
                        return;
                    }
                }
                
                
                try {
                    final InputStream is = this.control.getInputStream();
                    log.info("Reading incoming answer on control socket");
                    final Document doc = XmlUtils.toDoc(is, "</message>");
                    final String received = XmlUtils.toString(doc);
                    log.info("Got INVITE OK on CONTROL socket: {}", received);
                    
                    // We need to extract the SDP to establish the new socket.
                    final String sdp = XmppUtils.extractSdp(doc);
                    final byte[] sdpBytes = Base64.decodeBase64(sdp); 
                    
                    final OfferAnswerMessage message = new OfferAnswerMessage(){
                        @Override
                        public String getTransactionKey() {
                            return String.valueOf(hashCode());
                        }
                        @Override
                        public ByteBuffer getBody() {
                            return ByteBuffer.wrap(sdpBytes);
                        }
                    };
                    //final String from = XmppUtils.extractFrom(doc);
                    //final String encodedKey = XmppUtils.extractKey(doc);
                    //final byte[] key = CommonUtils.decodeBase64(encodedKey);
                    //keyStore.setReadKey(key);
                    //final Long tid = XmppUtils.extractTransactionId(doc);
                    //log.info("Got INVITE OK establishing new socket over " +
                    //    "control socket...from: "+from+" read key: "+key);
                    
                    log.info("Calling transaction succeeded on listener: {}", 
                        transactionListener);
                    transactionListener.onTransactionSucceeded(message);
                } catch (final SAXException e) {
                    log.warn("Could not parse INVITE OK", e);
                    // Close the socket?
                    closeOutgoing(uri, control);
                } catch (final IOException e) {
                    log.warn("Exception handling control socket", e);
                    closeOutgoing(uri, control);
                }
            }
        }
        
        private Message newInviteOverControlSocket(final String jid, 
            final byte[] offer, final KeyStorage keyStorage) {
            final Message msg = new Message();
            msg.setTo(jid);
            log.info("Sending offer: {}", new String(offer));
            final String base64Sdp = 
                Base64.encodeBase64URLSafeString(offer);
            msg.setProperty(P2PConstants.MESSAGE_TYPE, P2PConstants.INVITE);
            msg.setProperty(P2PConstants.SDP, base64Sdp);
            msg.setProperty(P2PConstants.CONTROL, "true");
            //final byte[] writeKey = keyStorage.getWriteKey();
            //log.info("Setting client write key to: {}", writeKey);
            //msg.setProperty(P2PConstants.SECRET_KEY, 
            //    Base64.encodeBase64String(writeKey));
            return msg;
        }
        
        private void writeToControlSocket(final String xml) throws IOException {
            final OutputStream os = this.control.getOutputStream();
            os.write(xml.getBytes("UTF-8"));
            os.flush();
            log.info("Wrote message on control socket stream: {}", os);
         }
    }

    private final class ControlSocketOfferAnswerListener 
        implements OfferAnswerListener {
    
        private final String fullJid;
        //private final byte[] readKey;
        //private final byte[] writeKey;
    
        public ControlSocketOfferAnswerListener(final String fullJid) {
            //this.readKey = readKey;
            //this.writeKey = writeKey;
            log.info("Creating listener on answerwer with full JID: {}", 
                fullJid);
            this.fullJid = fullJid;
        }
    
        @Override
        public void onOfferAnswerFailed(final OfferAnswer offerAnswer) {
            // The following will often happen for one of TCP or UDP.
            log.info("TCP or UDP offer answer failed: {}", offerAnswer);
        }
    
        @Override
        public void onTcpSocket(final Socket sock) {
            log.info("Got a TCP socket: {}", sock);
            onControlSocket(sock);
        }
    
        @Override
        public void onUdpSocket(final Socket sock) {
            log.info("Got a UDP socket: {}", sock);
            //log.info("Creating new CipherSocket with write key {} and read key {}", 
            //        writeKey, readKey);
            //onSocket(new CipherSocket(sock, writeKey, readKey));

            onControlSocket(sock);
        }
    
        private void onControlSocket(final Socket sock) {
            log.info("Got control socket on 'server' side: {}", sock);
            // We use one control socket for sending offers and another one
            // for receiving offers. This is an incoming socket for 
            // receiving offers.
            notifyConnectionListeners(this.fullJid, sock, true, true);
            incomingControlSockets.put(this.fullJid, sock);
            try {
                readInvites(sock);
            } catch (final IOException e) {
                log.info("Exception reading invites - this will happen " +
                    "whenever the other side closes the connection, which " +
                    "will happen all the time.", e);
                IOUtils.closeQuietly(sock);
                notifyConnectionListeners(this.fullJid, sock, true, false);
                incomingControlSockets.remove(this.fullJid);
            } catch (final SAXException e) {
                log.info("Exception reading invites", e);
                IOUtils.closeQuietly(sock);
                notifyConnectionListeners(this.fullJid, sock, true, false);
                incomingControlSockets.remove(this.fullJid);
            }
        }
    
        private void readInvites(final Socket sock) throws IOException, 
            SAXException {
            final InputStream is = sock.getInputStream();
            log.info("Reading streams from remote address: {}", 
                 sock.getRemoteSocketAddress());
            log.info("Reading answerer invites on input stream: {}", is);
            while (true) {
                // This will parse the full XML/XMPP message and extract the 
                // SDP from it.
                log.info("Trying to read next offer on control socket...");
                final Document doc = XmlUtils.toDoc(is, "</message>");
                log.info("Got XML INVITE: {}", XmlUtils.toString(doc));
                
                final String sdp = XmppUtils.extractSdp(doc);
                final String from = XmppUtils.extractFrom(doc);
                //final String key = XmppUtils.extractKey(doc);
                
                final ByteBuffer offer = 
                    ByteBuffer.wrap(Base64.decodeBase64(sdp));
                processInviteOverControlSocket(offer, sock, from);
            }
        }
    }
    

    /**
     * This processes an incoming offer received on the control socket after
     * the control socket has already been established.
     * 
     * @param tid The ID of the transaction.
     * @param offer The offer itself.
     * @param controlSocket The control socket.
     * @param readKey The key for decrypting incoming data.
     * @param from The user this is from.
     * @throws IOException If any IO error occurs, including normal socket
     * closings.
     */
    private void processInviteOverControlSocket(
        final ByteBuffer offer, final Socket controlSocket, 
        final String from) throws IOException {
        log.info("Processing offer...");
        final String offerString = MinaUtils.toAsciiString(offer);
        
        //final byte[] answerKey = CommonUtils.generateKey();
        final OfferAnswer offerAnswer;
        //final byte[] key = CommonUtils.decodeBase64(readKey);
        //log.info("Read key from client INVITE -- our read key: {}", key);
        
        try {
            offerAnswer = this.offerAnswerFactory.createAnswerer(
                new AnswererOfferAnswerListener("", 
                    this.plainTextRelayAddress, callSocketListener, 
                    offerString), this.useRelay);
        }
        catch (final OfferAnswerConnectException e) {
            // This indicates we could not establish the necessary connections 
            // for generating our candidates.
            log.warn("We could not create candidates for offer", e);
            error(from, null, controlSocket);
            return;
        }
        log.info("Creating answer");
        final byte[] answer = offerAnswer.generateAnswer();
        log.info("Creating INVITE OK");
        final Message inviteOk = newInviteOk(null, answer);
        log.info("Writing INVITE OK");
        writeMessage(inviteOk, controlSocket);
        log.info("Wrote INVITE OK");
        
        exec.submit(new Runnable() {
            @Override
            public void run() {
                log.info("Passing offer processing to listener...");
                offerAnswer.processOffer(offer);
            }
        });
        log.info("Done processing offer...");
    }

    private void closeOutgoing(final URI uri, final Socket control) {
        notifyConnectionListeners(uri, control, false, false);
        IOUtils.closeQuietly(control);
        this.outgoingControlSockets.remove(uri);
    }

    @Override
    public XMPPConnection getXmppConnection() {
        return xmppConnection;
    }

    @Override
    public void addMessageListener(final MessageListener ml) {
        messageListeners.add(ml);
    }
    
    private void error(final String from, final Long tid, final Socket sock) {
        final Message error = newError(from, tid);
        try {
            writeMessage(error, sock);
        } catch (final IOException e) {
            log.warn("Could not write message", e);
        }
    }

    private void writeMessage(final Message msg, final Socket sock) 
        throws IOException {
        log.info("Sending message through socket: {}", sock);
        final String msgString = toXml(msg);
        log.info("Writing XMPP message: {}", msgString);
        final OutputStream os = sock.getOutputStream();
        log.info("Writing message to output stream: {}", os);
        os.write(msgString.getBytes("UTF-8"));
        os.flush();
    }

    private String toXml(final Message msg) {
        return msg.toXML() + "\n";
    }

    @Override
    public void logout() {
        this.loggedOut.set(true);
        if (this.xmppConnection != null) {
            this.xmppConnection.disconnect();
        }
    }

    private final Collection<P2PConnectionListener> listeners =
        new ArrayList<P2PConnectionListener>();
    
    @Override
    public void addConnectionListener(final P2PConnectionListener listener) {
        synchronized (listeners) {
            this.listeners.add(listener);
        }
    }

    @Override
    public boolean isLoggedOut() {
        return this.loggedOut.get();
    }

    @Override
    public void handleClose() {
        if (isLoggedOut()) {
            log.info("Not maintaining connection when the user has " +
                "explictly logged out.");
            return;
        }
        try {
            login (this.username, this.password, this.connectionId);
            if (this.callSocketListener != null) {
                final Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        callSocketListener.reconnected();
                    }
                }, "Reconnected-Listener-Thread");
                t.setDaemon(true);
                t.start();
            }
        } catch (final IOException e) {
            log.info("Could not connect!!");
        } catch (final CredentialException e) {
            log.info("Credentials are wrong!", e);
        }
    }
    
    @Override 
    public void stop() {
        logout();
        this.xmppConnection.disconnect();
    }
}
