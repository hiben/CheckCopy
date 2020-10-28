package de.zvxeb.checkcopy;

import java.io.File;
import java.security.MessageDigest;
import java.util.concurrent.Callable;

class ChecksumCallable implements Callable<byte []> {

    private CancellationCheck cc;
    private MessageDigest md;
    private File f;

    public ChecksumCallable(CancellationCheck cc, MessageDigest md, File f) {
        this.cc = cc;
        this.md = md;
        this.f = f;
    }

    @Override
    public byte[] call() throws Exception {
        return CheckCopy.createChecksum(cc, md, f);
    }
}
