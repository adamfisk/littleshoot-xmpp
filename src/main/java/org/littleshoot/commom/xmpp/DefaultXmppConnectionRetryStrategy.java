package org.littleshoot.commom.xmpp;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultXmppConnectionRetryStrategy implements XmppConnectionRetyStrategy {

    private static final Logger LOG = 
        LoggerFactory.getLogger(DefaultXmppConnectionRetryStrategy.class);
    private final AtomicInteger retries = new AtomicInteger(0);
    
    @Override
    public boolean retry() {
        if (retries.get() < 1000) {
            retries.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public void sleep() {
        try {
            Thread.sleep(this.retries.get() * 100);
        } catch (final InterruptedException e) {
            LOG.info("Interrupted?", e);
        }
    }

}
