package org.rowland.jinix.naming;

import java.io.Serializable;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.util.Set;

/**
 * A serializable handle that provides access to a RemoteFileAccessor for an object in a Jinix file system.
 */
public interface RemoteFileHandle extends Serializable {

    FileNameSpace getParent();

    /**
     * Get the path of the file relative to the root of the parent FileNameSpace. The path will always begin with a '/'
     *
     * @return the path
     */
    String getPath();

    RemoteFileAccessor open(int pid, Set<? extends OpenOption> options) throws FileAlreadyExistsException, NoSuchFileException;

    DirectoryFileData getAttributes() throws NoSuchFileException;

    void setAttributes(DirectoryFileData directoryFileData) throws NoSuchFileException;

    Object getKey();
}
