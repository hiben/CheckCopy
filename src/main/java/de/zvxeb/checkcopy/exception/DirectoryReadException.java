package de.zvxeb.checkcopy.exception;

import java.io.IOException;

public class DirectoryReadException extends IOException {
    private boolean source;
    private String path;

    public DirectoryReadException(boolean source, String path) {
        super("Unable to read files from directory: " + path);
        this.source = source;
        this.path = path;
    }

    public boolean isSource() {
        return source;
    }

    public String getPath() {
        return path;
    }
}
