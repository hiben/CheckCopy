package de.zvxeb.checkcopy;

import de.zvxeb.checkcopy.conflict.Conflict;

import java.io.File;
import java.nio.file.Path;

public interface CheckEventListener {
    void onNotInDestination(CheckControl cc, Path source, Path destination, File f);
    void onNotInSource(CheckControl cc, Path source, Path destination, File f);
    void onConflict(CheckControl cc, Path source, Path destination, File fs, File fd, Conflict c);
    void onCancelled(CheckControl cc);
}
