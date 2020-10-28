package de.zvxeb.checkcopy.exception;

import java.io.IOException;

public class NotADirectoryException extends IOException {
    private boolean source;
    private String path;

    public NotADirectoryException(boolean source, String path) {
        super("Not a directory: " + path);
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
