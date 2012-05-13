package org.littleshoot.xmpp;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;
import javax.security.auth.login.CredentialException;

import org.apache.commons.lang.StringUtils;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Packet;
import org.junit.Test;
import org.littleshoot.commom.xmpp.XmppUtils;
import org.littleshoot.util.xml.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;


public class XmppUtilsTest {

    private static final Logger LOG = 
        LoggerFactory.getLogger(XmppUtilsTest.class);
    private static final int SERVER_PORT = 4822;

    @Test
    public void testExtendedRoster() {
        //System.setProperty("javax.net.debug", "ssl:record");
        final String[] cipherSuites = new String[] {
            //"TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            //"TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            //"TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",
            //"TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
            //"TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            //"TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            //"TLS_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            //"TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            //"TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            //"TLS_ECDHE_RSA_WITH_RC4_128_SHA",
            //"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            //"TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",
            //"TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA",
            //"TLS_DHE_DSS_WITH_RC4_128_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
            //"SSL_RSA_WITH_RC4_128_MD5",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
            //"TLS_ECDH_RSA_WITH_RC4_128_SHA",
            //"TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            //"TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
            //"TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            //"TLS_RSA_WITH_SEED_CBC_SHA",
            //"TLS_RSA_WITH_CAMELLIA_128_CBC_SHA",
            //"TLS_RSA_WITH_RC4_128_MD5",
            //"TLS_RSA_WITH_RC4_128_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            //"TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            //"TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            //"TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            //"TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            //"TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            //"TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            //"SSL_RSA_FIPS_WITH_3DES_EDE_CBC_SHA",
            //"TLS_RSA_WITH_3DES_EDE_CBC_SHA",
        };
        final Collection<String> working = new ArrayList<String>();
        //for (final String cs : cipherSuites) {
            //XmppUtils.setGlobalConfig(xmppConfig(cipherSuites));
            try {
                final XMPPConnection conn = XmppUtils.persistentXmppConnection("adamfisk@gmail.com", "#@$77rq7rR", "test", 1);
                
                Packet msg = XmppUtils.extendedRoster(conn);
                System.out.println(msg.toXML());
                //msg = XmppUtils.getSharedStatus(conn);
                //System.out.println(msg.toXML());
                //System.out.println("Adding "+cs);
                //working.add(cs);
            } catch (CredentialException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        //}
        //System.out.println(working);
    }
    
    //@Test 
    public void testXmpp() throws Exception {
        final File testFile = 
            new File("src/test/resources/testXmppMessage.txt");
        final InputStream is = new FileInputStream(testFile);
        final Document doc = XmlUtils.toDoc(is);
        final String sdp = XmppUtils.extractSdp(doc);
        assertEquals("dj0wDQpvPS0gMCAwIElOIElQNCAxOTIuMTY4LjAuMTM5DQpzPS0NCnQ9MCAwDQptPW1lc3NhZ2UgNTE0MzggdWRwIGh0dHANCmM9SU4gSVA0IDE5Mi4xNjguMC4xMzkNCmE9Y2FuZGlkYXRlOnVkcC1zcmZseC0xOTIuMTY4LjAuMTM5LTIwOC45Ny4yNS4yMCAxIHVkcCAxNjg0NjY4NDE1IDk5LjEwNS41NC4xMiA1MTQzOCB0eXAgc3JmbHggcmFkZHIgMTkyLjE2OC4wLjEzOSBycG9ydCA1MTQzOA0KYT1jYW5kaWRhdGU6dWRwLWhvc3QtMTkyLjE2OC4wLjEzOSAxIHVkcCAyMTIwODc2MDMxIDE5Mi4xNjguMC4xMzkgNTE0MzggdHlwIGhvc3QNCmE9Y2FuZGlkYXRlOnRjcC1wYXNzLWhvc3QtMTkyLjE2OC4wLjEzOSAxIHRjcC1wYXNzIDIxMzAwNTEwNzEgMTkyLjE2OC4wLjEzOSAyNDUwOSB0eXAgaG9zdA0K", sdp);
        final String key = XmppUtils.extractKey(doc);
        assertEquals("", key);
    }
    
    //@Test 
    public void testXmlSocketReads() throws Exception {
        final AtomicReference<String> ref = new AtomicReference<String>("");
        startServer(ref);
        Thread.yield();
        Thread.yield();
        final File testFile = 
            new File("src/test/resources/testXmppMessage.txt");
        final InputStream is = new FileInputStream(testFile);
        final Document doc = XmlUtils.toDoc(is);
        final String xml = XmlUtils.toString(doc);
        final Socket sock = new Socket();
        sock.connect(new InetSocketAddress(SERVER_PORT));
        final OutputStream os = sock.getOutputStream();
        os.write(xml.getBytes("UTF-8"));
        //os.close();
        //sock.close();
        synchronized (ref) {
            if (StringUtils.isBlank(ref.get())) {
                ref.wait(2000);
            }
        }
        assertEquals(xml, ref.get());
    }

    private void startServer(final AtomicReference<String> ref) 
        throws IOException {
        final ServerSocket ss = new ServerSocket(SERVER_PORT);
        final Runnable runner = new Runnable() {
            public void run() {
                try {
                    
                    final Socket sock = ss.accept();
                    final InputStream is = sock.getInputStream();
                    final Document doc = XmlUtils.toDoc(is, "</message>");
                    final String xmlString = XmlUtils.toString(doc);
                    System.out.println(xmlString);
                    ref.set(xmlString);
                    synchronized (ref) {
                        ref.notifyAll();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (SAXException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };
        final Thread t = new Thread(runner);
        t.setDaemon(true);
        t.start();
    }
    
    private static ConnectionConfiguration xmppConfig(final String... cs) {
        final ConnectionConfiguration config = 
            new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
        config.setExpiredCertificatesCheckEnabled(true);
        config.setNotMatchingDomainCheckEnabled(true);
        config.setSendPresence(false);
        
        config.setCompressionEnabled(true);
        
        config.setRosterLoadedAtLogin(true);
        config.setReconnectionAllowed(false);
        config.setVerifyChainEnabled(true);
        //config.setVerifyRootCAEnabled(true);
        config.setSelfSignedCertificateEnabled(false);
        
        final String[] cipherSuites = new String[] {
                //cs,
            //"TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            //"TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            //"TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",
            //"TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA",
            //"TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
            //"TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
            //"TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            //"TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            //"TLS_RSA_WITH_CAMELLIA_256_CBC_SHA",
            //"TLS_RSA_WITH_AES_256_CBC_SHA",
            //"TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            //"TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            //"TLS_ECDHE_RSA_WITH_RC4_128_SHA",
            //"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            //"TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",
            //"TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA",
            //"TLS_DHE_DSS_WITH_RC4_128_SHA",
            //"TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
            //"SSL_RSA_WITH_RC4_128_MD5",
            //"TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
            //"TLS_ECDH_RSA_WITH_RC4_128_SHA",
            //"TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            //"TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
            //"TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            //"TLS_RSA_WITH_SEED_CBC_SHA",
            //"TLS_RSA_WITH_CAMELLIA_128_CBC_SHA",
            //"TLS_RSA_WITH_RC4_128_MD5",
            //"TLS_RSA_WITH_RC4_128_SHA",
            //"TLS_RSA_WITH_AES_128_CBC_SHA",
            //"TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            //"TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            //"TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            //"TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            //"TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            //"TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            //"SSL_RSA_FIPS_WITH_3DES_EDE_CBC_SHA",
            //"TLS_RSA_WITH_3DES_EDE_CBC_SHA",
        };
        config.setCipherSuites(cs);
        
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
                LOG.info("Creating socket");
                final Socket sock = new Socket();
                sock.connect(new InetSocketAddress(host, port), 40000);
                LOG.info("Socket connected");
                return sock;
            }
            
            @Override
            public Socket createSocket(final String host, final int port) 
                throws IOException, UnknownHostException {
                LOG.info("Creating socket");
                return createSocket(InetAddress.getByName(host), port);
            }
        });
        return config;
    }
}
