package org.littleshoot.commom.xmpp;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
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

    public static String extractSdp(final Document doc) {
        return extractXmppProperty(doc, P2PConstants.SDP);
    }

    public static String extractKey(final Document doc) {
        return extractXmppProperty(doc, P2PConstants.SECRET_KEY);
    }
    

    public static long extractTransactionId(final Document doc) {
        final String id = extractXmppProperty(doc, P2PConstants.TRANSACTION_ID);
        return Long.parseLong(id);
    }
    

    public static String extractFrom(final Document doc) {
        final String xml = XmlUtils.toString(doc);
        LOG.info("Got an XMPP message: {}", xml);
        final XPathUtils xpath = XPathUtils.newXPath(doc);
        final String str = "/message/From";
        try {
            return xpath.getString(str);
        } catch (final XPathExpressionException e) {
            throw new Error("Tested XPath no longer working: "+str, e);
        }
    }

    private static String extractXmppProperty(final Document doc, 
        final String name) {
        final String xml = XmlUtils.toString(doc);
        LOG.info("Got an XMPP message: {}", xml);
        final XPathUtils xpath = XPathUtils.newXPath(doc);
        final String str = 
            "/message/properties/property[name='"+name+"']/value";
        try {
            return xpath.getString(str);
        } catch (final XPathExpressionException e) {
            throw new Error("Tested XPath no longer working: "+str, e);
        }
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
    
    public static String jidToUser(final String jid) {
        return StringUtils.substringBefore(jid, "/");
    }
    
    //// The following includes a whole bunch of custom Google Talk XMPP 
    //// messages.
    
    public static Packet activateOtr(final String jidToOtr, 
        final XMPPConnection conn) {
        LOG.info("Activating OTR for {}...", jidToOtr);
        final String query =
            "<query xmlns='google:nosave'>"+
                "<item xmlns='google:nosave' jid='"+jidToOtr+"' value='enabled'/>"+
             "</query>";
        return setGTalkProperty(conn, query);
    }
    
    public static Packet deactivateOtr(final String jidToOtr, 
        final XMPPConnection conn) {
        LOG.info("Activating OTR for {}...", jidToOtr);
        final String query =
            "<query xmlns='google:nosave'>"+
                "<item xmlns='google:nosave' jid='"+jidToOtr+"' value='disabled'/>"+
             "</query>";
        return setGTalkProperty(conn, query);
    }

    public static Packet getOtr(final XMPPConnection conn) {
        LOG.info("Getting OTR status...");
        return getGTalkProperty(conn, "<query xmlns='google:nosave'/>");
    }
    
    public static Packet getSharedStatus(final XMPPConnection conn) {
        LOG.info("Getting shared status...");
        return getGTalkProperty(conn, 
            "<query xmlns='google:shared-status' version='2'/>");
    }
    
    public static Packet discoveryRequest(final XMPPConnection conn) {
        LOG.info("Sending discovery request...");
        return getGTalkProperty(conn, 
            "<query xmlns='http://jabber.org/protocol/disco#info'/>");
    }
    
    private static Packet setGTalkProperty(final XMPPConnection conn, 
        final String query) {
        return sendXmppMessage(conn, query, Type.SET);
    }
    
    private static Packet getGTalkProperty(final XMPPConnection conn, 
        final String query) {
        return sendXmppMessage(conn, query, Type.GET);
    }
    
    private static Packet sendXmppMessage(final XMPPConnection conn, 
        final String query, final Type iqType) {
        
        LOG.info("Sending XMPP stanza message...");
        final IQ iq = new IQ() {
            @Override
            public String getChildElementXML() {
                return query;
            }
        };
        final String jid = conn.getUser();
        iq.setTo(jidToUser(jid));
        iq.setFrom(jid);
        iq.setType(iqType);
        final PacketCollector collector = conn.createPacketCollector(
            new PacketIDFilter(iq.getPacketID()));
        
        LOG.info("Sending XMPP stanza packet:\n"+iq.toXML());
        conn.sendPacket(iq);
        final Packet response = collector.nextResult(40000);
        return response;
    }

    /**
     * Note we don't even need to set this property to maintain compatibility
     * with Google Talk presence -- just sending them the shared status 
     * message signifies we generally understand the protocol and allows us
     * to not clobber other clients' presences.
     * @param conn
     * @param to
     */
    public static void setGoogleTalkInvisible(final XMPPConnection conn, 
        final String to) {
        final IQ iq = new IQ() {
            @Override
            public String getChildElementXML() {
                return "<query xmlns='google:shared-status' version='2'><invisible value='true'/></query>";
            }
        };
        iq.setType(Type.SET);
        iq.setTo(to);
        LOG.info("Setting invisible with XML packet:\n"+iq.toXML());
        conn.sendPacket(iq);
    }
}
