package de.zvxeb.checkcopy.conflict;

public class TypeConflict extends Conflict {
    boolean isFile;

    public TypeConflict(boolean isFile) {
        this.isFile = isFile;
        setMessage(messages.getString(isFile ? "conflict_type_file" : "conflict_type_directory"));
    }

    public static TypeConflict causedBy(boolean isFile) {
        return new TypeConflict(isFile);
    }

    public boolean isFile() {
        return isFile;
    }
}
