package de.zvxeb.checkcopy.exception;

import java.io.IOException;

public class ChecksumException extends IOException {
    private String path;

    public ChecksumException(String path) {
        super("Checksum generation failed for: " + path);
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
