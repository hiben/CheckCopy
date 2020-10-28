package de.zvxeb.checkcopy;

public class CheckMeta {
    private int numberOfFiles;
    private int numberOfDirectories;

    public void incFiles() {
        numberOfFiles++;
    }

    public void incDirectories() {
        numberOfDirectories++;
    }

    public int numberOfFiles() {
        return numberOfFiles;
    }

    public int numberOfDirectories() {
        return numberOfDirectories;
    }
}
