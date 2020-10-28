package de.zvxeb.checkcopy.conflict;

import java.util.ResourceBundle;

public abstract class Conflict {
    protected static ResourceBundle messages;

    static {
        messages = ResourceBundle.getBundle("messages");
    }

    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
