package org.littleshoot.commom.xmpp;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;

import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.Offerer;
import org.lastbamboo.common.p2p.DefaultTcpUdpSocket;
import org.lastbamboo.common.p2p.TcpUdpSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultXmppSocketFactory implements XmppSocketFactory {

    private final Logger m_log = LoggerFactory.getLogger(getClass());
    private final OfferAnswerFactory offerAnswerFactory;
    private final int relayWaitTime;
    private final Offerer offerer;

    public DefaultXmppSocketFactory(final Offerer offerer,
        final OfferAnswerFactory offerAnswerFactory, final int relayWaitTime) {
        this.offerer = offerer;
        this.offerAnswerFactory = offerAnswerFactory;
        this.relayWaitTime = relayWaitTime;
    }

    public Socket newSocket(final URI uri) throws IOException {
        m_log.trace ("Creating XMPP socket for URI: {}", uri);
        final TcpUdpSocket tcpUdpSocket = 
            new DefaultTcpUdpSocket(this.offerer, this.offerAnswerFactory,
                this.relayWaitTime);
        
        return tcpUdpSocket.newSocket(uri);
    }

}
