package org.rowland.jinix.naming;

import java.io.Serializable;

/**
 * Created by rsmith on 12/7/2016.
 */
public class DirectoryFileData implements Serializable {

    public enum FileType {FILE, DIRECTORY};

    public String name;
    public long length;
    public FileType type;
    public long lastModified;
}
