package de.zvxeb.checkcopy.gui;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public class DirectoryFilter extends FileFilter  {
    @Override
    public boolean accept(File f) {
        return f.isDirectory();
    }

    @Override
    public String getDescription() {
        return "Directories";
    }
}
