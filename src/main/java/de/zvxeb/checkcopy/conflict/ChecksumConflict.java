package de.zvxeb.checkcopy.conflict;

public class ChecksumConflict extends Conflict {
    String sourceChecksum;
    String destinationChecksum;

    public ChecksumConflict(String source, String destination) {
        sourceChecksum = source;
        destinationChecksum = destination;
        setMessage(String.format(messages.getString("conflict_checksum"), source, destination));
    }

    public static ChecksumConflict causedBy(String source, String destination) {
        return new ChecksumConflict(source, destination);
    }

    public String sourceChecksum() {
        return sourceChecksum;
    }

    public String destinationChecksum() {
        return destinationChecksum;
    }
}
