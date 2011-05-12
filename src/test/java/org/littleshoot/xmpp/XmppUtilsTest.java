package org.littleshoot.xmpp;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.junit.Test;
import org.littleshoot.commom.xmpp.XmppUtils;
import org.littleshoot.util.xml.XmlUtils;
import org.w3c.dom.Document;


public class XmppUtilsTest {

    @Test public void testXmpp() throws Exception {
        final File testFile = 
            new File("src/test/resources/testXmppMessage.txt");
        final InputStream is = new FileInputStream(testFile);
        final Document doc = XmlUtils.toDoc(is);
        final String sdp = XmppUtils.extractSdp(doc);
        assertEquals("dj0wDQpvPS0gMCAwIElOIElQNCAxOTIuMTY4LjAuMTM5DQpzPS0NCnQ9MCAwDQptPW1lc3NhZ2UgNTE0MzggdWRwIGh0dHANCmM9SU4gSVA0IDE5Mi4xNjguMC4xMzkNCmE9Y2FuZGlkYXRlOnVkcC1zcmZseC0xOTIuMTY4LjAuMTM5LTIwOC45Ny4yNS4yMCAxIHVkcCAxNjg0NjY4NDE1IDk5LjEwNS41NC4xMiA1MTQzOCB0eXAgc3JmbHggcmFkZHIgMTkyLjE2OC4wLjEzOSBycG9ydCA1MTQzOA0KYT1jYW5kaWRhdGU6dWRwLWhvc3QtMTkyLjE2OC4wLjEzOSAxIHVkcCAyMTIwODc2MDMxIDE5Mi4xNjguMC4xMzkgNTE0MzggdHlwIGhvc3QNCmE9Y2FuZGlkYXRlOnRjcC1wYXNzLWhvc3QtMTkyLjE2OC4wLjEzOSAxIHRjcC1wYXNzIDIxMzAwNTEwNzEgMTkyLjE2OC4wLjEzOSAyNDUwOSB0eXAgaG9zdA0K", sdp);
    }
}
