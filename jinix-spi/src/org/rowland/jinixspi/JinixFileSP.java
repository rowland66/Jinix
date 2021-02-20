package org.rowland.jinixspi;

import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

public interface JinixFileSP {

    String getName();

    String getParent();

    String getPath();

    JinixFileSP getParentFile();

    boolean isAbsolute();

    String getAbsolutePath();

    JinixFileSP getAbsoluteFile();

    String getCanonicalPath();

    JinixFileSP getCanonicalFile();

    boolean exists();

    boolean isDirectory();

    boolean isFile();

    long lastModified();

    long length();

    boolean createNewFile() throws IOException;

    boolean delete();

    String[] list() throws NotDirectoryException;

    boolean mkdir();

    boolean mkdirs();

    boolean renameTo(JinixFileSP file);

    boolean setLastModified(long time);

    boolean setReadOnly();

    boolean setWritable(boolean writable, boolean ownerOnly);

    boolean setReadable(boolean readable, boolean ownerOnly);

    boolean setExecutable(boolean executable, boolean ownerOnly);

    boolean canExecute();

    Path toPath();

}
