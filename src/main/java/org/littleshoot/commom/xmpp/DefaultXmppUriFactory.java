package org.littleshoot.commom.xmpp;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Factory for creating SIP URIs.
 */
public final class DefaultXmppUriFactory implements XmppUriFactory
    {

    public URI createXmppUri(final String id)
        {
        final String sipUriString = "sip:"+id+"@lastbamboo.org";
        try
            {
            return new URI(sipUriString);
            }
        catch (final URISyntaxException e)
            {
            throw new IllegalArgumentException("Invalid request string: "+
                id);
            }
        }

    public URI createXmppUri(final long id)
        {
        return createXmppUri(Long.toString(id));
        }

    }
