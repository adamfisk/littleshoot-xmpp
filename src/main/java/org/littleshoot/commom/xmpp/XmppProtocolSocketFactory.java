package org.littleshoot.commom.xmpp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.prefs.Preferences;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.io.IOExceptionWithCause;
import org.lastbamboo.common.offer.answer.NoAnswerException;
import org.lastbamboo.common.p2p.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppProtocolSocketFactory implements ProtocolSocketFactory {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SocketFactory socketFactory;
    private final DefaultXmppUriFactory xmppUriFactory;
    
    public XmppProtocolSocketFactory(final SocketFactory socketFactory,
        final DefaultXmppUriFactory defaultXmppUriFactory) {
        this.socketFactory = socketFactory;
        this.xmppUriFactory = defaultXmppUriFactory;
    }

    public Socket createSocket(final String host, final int port, 
        final InetAddress localAddress, final int localPort) 
        throws IOException, UnknownHostException {
        log.warn("Attempted unsupported socket call");
        throw new UnsupportedOperationException("not allowed");
    }

    public Socket createSocket(final String host, final int port, 
        final InetAddress localAddress, final int localPort, 
        final HttpConnectionParams params) throws IOException,
        UnknownHostException, ConnectTimeoutException {
        return createSocket(host, port);
    }

    public Socket createSocket(final String host, final int port) 
        throws IOException, UnknownHostException {
        log.trace("Creating a socket for user: {}", host);
        final Preferences prefs = Preferences.userRoot();
        final long id = prefs.getLong("LITTLESHOOT_ID", -1);
        if (id == Long.parseLong(host)) {
            // This is an error because we should just stream it locally
            // if we have the file (we're trying to connect to ourselves!).
            log.error("Ignoring request to download from ourselves...");
            throw new IOException("Not downloading from ourselves...");
        }
        
        final URI uri = this.xmppUriFactory.createXmppUri(host);
        
        // We try a few times because sometimes XMPP servers seem to randomly
        // not deliver messages.
        NoAnswerException nae = null;
        for (int i = 0; i < 3; i++) {
            try {
                log.trace("About to create socket...");
                final Socket sock = this.socketFactory.newSocket(uri);
                log.debug("Got socket!! Returning to HttpClient");
                
                // Note there can appear to be an odd delay after this point if
                // you're just looking at the raw logs, but it's due to HttpClient
                // actually making the HTTP request and getting a response.
                return sock;
            } catch (final NoAnswerException e) {
                log.warn("Did not get answer! Trying again", e);
                nae = e;
            }
            catch (final IOException e) {
                log.warn("Exception creating P2P socket", e);
                throw e;
            }
        }
        throw nae;
    }
}
