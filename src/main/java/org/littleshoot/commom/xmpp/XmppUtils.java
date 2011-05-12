package org.littleshoot.commom.xmpp;

import javax.xml.xpath.XPathExpressionException;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.XMPPError;
import org.lastbamboo.common.p2p.P2PConstants;
import org.littleshoot.util.xml.XPathUtils;
import org.littleshoot.util.xml.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class XmppUtils {
    
    private static final Logger LOG = LoggerFactory.getLogger(XmppUtils.class);
    
    private XmppUtils() {}

    public static String extractSdp(final Document doc) 
        throws XPathExpressionException {
        return extractXmppProperty(doc, P2PConstants.SDP);
    }

    public static String extractKey(final Document doc)
            throws XPathExpressionException {
        return extractXmppProperty(doc, P2PConstants.SECRET_KEY);
    }

    private static String extractXmppProperty(final Document doc, 
        final String name) throws XPathExpressionException {
        final String invite = XmlUtils.toString(doc);
        LOG.info("Got an invite: {}", invite);
        final XPathUtils xpath = XPathUtils.newXPath(doc);
        return xpath.getString("/message/properties/property[name='"+name+"']/value");
    }

    public static void printMessage(final Packet msg) {
        LOG.info(toString(msg));
    }

    public static String toString(final Packet msg) {
        final XMPPError error = msg.getError();
        final StringBuilder sb = new StringBuilder();
        sb.append("\nMESSAGE: ");
        sb.append("\nBODY: ");
        if (msg instanceof Message) {
            sb.append(((Message)msg).getBody());
        }
        sb.append("\nFROM: ");
        sb.append(msg.getFrom());
        sb.append("\nTO: ");
        sb.append(msg.getTo());
        sb.append("\nSUBJECT: ");
        if (msg instanceof Message) {
            sb.append(((Message)msg).getSubject());
        }
        sb.append("\nPACKET ID: ");
        sb.append(msg.getPacketID());
        
        sb.append("\nERROR: ");
        if (error != null) {
            sb.append(error);
            sb.append("\nCODE: ");
            sb.append(error.getCode());
            sb.append("\nMESSAGE: ");
            sb.append(error.getMessage());
            sb.append("\nCONDITION: ");
            sb.append(error.getCondition());
            sb.append("\nEXTENSIONS: ");
            sb.append(error.getExtensions());
            sb.append("\nTYPE: ");
            sb.append(error.getType());
        }
        sb.append("\nEXTENSIONS: ");
        sb.append(msg.getExtensions());
        sb.append("\nTYPE: ");
        if (msg instanceof Message) {
            sb.append(((Message)msg).getType());
        }
        sb.append("\nPROPERTY NAMES: ");
        sb.append(msg.getPropertyNames());
        return sb.toString();
    }
}
