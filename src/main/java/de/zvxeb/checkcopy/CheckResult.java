package de.zvxeb.checkcopy;

import de.zvxeb.checkcopy.conflict.Conflict;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

class CheckResult {
    private Path sourcePath;
    private Path destinationPath;
    private List<File> notInDestination = new LinkedList<>();
    private List<File> notInSource = new LinkedList<>();
    private List<File> conflicts = new LinkedList<>();

    private Map<File, Conflict> conflictDetails = new HashMap<>();

    public CheckResult(Path sourcePath, Path destinationPath) {
        this.sourcePath = sourcePath;
        this.destinationPath = destinationPath;
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public Path destinationPath() {
        return destinationPath;
    }

    public List<File> notInDestination() {
        return Collections.unmodifiableList(notInDestination);
    }

    public List<File> notInSource() {
        return Collections.unmodifiableList(notInSource);
    }

    public List<File> conflicts() {
        return Collections.unmodifiableList(conflicts);
    }

    public boolean ok() {
        return notInSource.isEmpty() && notInDestination.isEmpty() && conflicts.isEmpty();
    }

    public Map<File, Conflict> conflictDetails() {
        return Collections.unmodifiableMap(conflictDetails);
    }
    public void addNotInDestination(File f) {
        notInDestination.add(f);
    }

    public void addNotInSource(File f) {
        notInSource.add(f);
    }

    public void addConflict(File f, Conflict c) {
        conflicts.add(f);
        conflictDetails.put(f, c);
    }
}
