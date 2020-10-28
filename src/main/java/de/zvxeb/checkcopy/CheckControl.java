package de.zvxeb.checkcopy;

import de.zvxeb.checkcopy.exception.CheckCancelledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;

public class CheckControl implements CancellationCheck, AutoCloseable {
    private static Logger log = LoggerFactory.getLogger(CheckControl.class);

    private boolean checkSize = false;
    private boolean failFast = false;
    private boolean failOnDestination = false;
    private boolean checksum = false;
    private MessageDigest messageDigestS;
    private MessageDigest messageDigestD;

    private ExecutorService executor;

    private boolean cancelled;

    private CheckEventListener eventListener;

    public CheckControl() {
    }

    public boolean checkSize() {
        return checkSize;
    }

    public CheckControl checkSize(boolean checkSize) {
        this.checkSize = checkSize;
        return this;
    }

    public boolean failFast() {
        return failFast;
    }

    public CheckControl failFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    public boolean failOnDestination() {
        return failOnDestination;
    }

    public CheckControl failOnDestination(boolean failOnDestination) {
        this.failOnDestination = failOnDestination;
        return this;
    }

    public boolean checksum() {
        return checksum;
    }

    public CheckControl checksum(boolean checksum) {
        this.checksum = checksum;
        try {
            this.messageDigestS = MessageDigest.getInstance("MD5");
            this.messageDigestD = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            log.error("No MD5 Digest available!", e);
            this.checksum = false;
            this.messageDigestS = null;
            this.messageDigestD = null;
        }
        return this;
    }

    public CheckControl checksum(MessageDigest md) {
        this.checksum = md != null;
        this.messageDigestS = md;
        this.messageDigestD = null;
        // need to clone for parallel digestion
        if(md != null) {
            try {
                this.messageDigestD = MessageDigest.getInstance(md.getAlgorithm());
            } catch (NoSuchAlgorithmException e) {
                log.error("Unable to create new instance of " + md.getAlgorithm(), e);
                this.checksum = false;
                this.messageDigestS = null;
            }
        }
        return this;
    }

    public MessageDigest messageDigestS() {
        return messageDigestS;
    }

    public MessageDigest messageDigestD() {
        return messageDigestD;
    }

    public CheckControl messageDigest(MessageDigest messageDigest) {
        return checksum(messageDigest);
    }

    public ExecutorService executor() {
        return executor;
    }

    public CheckControl executor(ExecutorService executor) {
        this.executor = executor;
        return this;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    public void cancel() {
        boolean ev = !cancelled;
        this.cancelled = true;
        if(ev) {
            if(eventListener!=null) {
                eventListener.onCancelled(this);
            }
        }
    }

    public CheckControl init() {
        this.cancelled = false;
        if(this.messageDigestS==null) {
            this.checksum = false;
        }
        if(this.messageDigestD == null) {
            this.checksum = false;
        }
        if(executor!=null && executor.isShutdown()) {
            log.warn("Executor is shut-down - multi-threading disabled!");
            executor = null;
        }
        return this;
    }

    public void checkCancelled() {
        if(cancelled) {
            throw new CheckCancelledException();
        }
    }

    public CheckEventListener eventListener() {
        return eventListener;
    }

    public CheckControl eventListener(CheckEventListener eventListener) {
        this.eventListener = eventListener;
        return this;
    }

    public void release() {
        if(executor!=null) {
            executor.shutdown();
        }
    }

    @Override
    public void close() throws Exception {
        release();
    }
}
