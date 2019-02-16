package org.rowland.jinix.io;

import org.rowland.jinix.naming.DirectoryFileData;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.naming.RemoteFileAccessor;
import org.rowland.jinix.naming.RemoteFileHandle;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.rmi.RemoteException;
import java.util.Set;

/**
 * A simple implementation of a RemoteFileHandle for a directory. Useful for supporting translators that need to provide
 * a hierarchical directory.
 */
public class SimpleDirectoryRemoteFileHandle extends BaseRemoteFileHandleImpl implements RemoteFileHandle {

    public SimpleDirectoryRemoteFileHandle(FileNameSpace parent, String pathWithinParent) {
        super(parent, pathWithinParent);
    }

    @Override
    public RemoteFileAccessor open(int pid, Set<? extends OpenOption> options) throws FileAlreadyExistsException, NoSuchFileException {
        throw new NoSuchFileException(getPath());
    }

    @Override
    public DirectoryFileData getAttributes() throws NoSuchFileException {
        DirectoryFileData dfd = new DirectoryFileData();
        dfd.type = DirectoryFileData.FileType.DIRECTORY;
        dfd.lastModified = 0;
        dfd.length = 0;
        dfd.name = getPath().substring(getPath().lastIndexOf('/')+1);
        return dfd;
    }
}
