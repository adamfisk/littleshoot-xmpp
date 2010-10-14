package org.littleshoot.commom.xmpp;

import java.net.URI;

/**
 * Factory for creating XMPP <code>URI</code>s from user IDs.
 */
public interface XmppUriFactory
    {

    /**
     * Creates a XMPP URI for the user with the specified ID.
     * 
     * @param id The ID of the user to create a XMPP URI for.
     * @return The URI for the user.
     */
    URI createXmppUri(String id);

    /**
     * Creates a XMPP URI for the user with the specified ID.
     * 
     * @param id The ID of the user to create a XMPP URI for.
     * @return The URI for the user.
     */
    URI createXmppUri(long id);

    }
