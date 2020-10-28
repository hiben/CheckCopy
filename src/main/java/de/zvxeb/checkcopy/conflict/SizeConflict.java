package de.zvxeb.checkcopy.conflict;

public class SizeConflict extends Conflict {
    private long sourceSize;
    private long destinationSize;

    public SizeConflict(long source, long destination) {
        sourceSize = source;
        destinationSize = destination;
        setMessage(String.format(messages.getString("conflict_size"), source, destination));
    }

    public static SizeConflict causedBy(long source, long destination) {
        return new SizeConflict(source, destination);
    }

    public long sourceSize() {
        return sourceSize;
    }

    public long destinationSize() {
        return destinationSize;
    }
}
