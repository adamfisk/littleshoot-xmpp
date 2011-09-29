package org.littleshoot.xmpp;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.StringUtils;
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
    
    @Test 
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
}
