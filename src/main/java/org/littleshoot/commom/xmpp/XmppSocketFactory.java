package org.littleshoot.commom.xmpp;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;

public interface XmppSocketFactory {

    Socket newSocket(URI uri) throws IOException;

}
