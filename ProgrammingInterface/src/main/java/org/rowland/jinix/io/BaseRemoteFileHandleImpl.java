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

public class BaseRemoteFileHandleImpl implements RemoteFileHandle {

    private FileNameSpace parent;
    private String path;
    private String key;

    public BaseRemoteFileHandleImpl(FileNameSpace parent, String path) {
        try {
            this.parent = parent;
            this.path = path;
            this.key = parent.getURI()+"/"+this.path;
        } catch (RemoteException e) {
            throw new RuntimeException(e); // Should not happen as construction is not performed remotely
        }
    }

    @Override
    public FileNameSpace getParent() {
        return this.parent;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Override
    public RemoteFileAccessor open(int pid, Set<? extends OpenOption> set) throws FileAlreadyExistsException, NoSuchFileException {
        try {
            return this.parent.getRemoteFileAccessor(pid, this.getPath(), set);
        } catch (RemoteException e) {
            throw new RuntimeException("Internal error", e);
        }
    }

    @Override
    public DirectoryFileData getAttributes() throws NoSuchFileException {
        try {
            return this.parent.getFileAttributes(this.getPath());
        } catch (RemoteException e) {
            throw new RuntimeException("Internal error", e);
        }
    }

    @Override
    public void setAttributes(DirectoryFileData attributes) throws NoSuchFileException {
        try {
            this.parent.setFileAttributes(this.getPath(), attributes);
        } catch (RemoteException e) {
            throw new RuntimeException("Internal error", e);
        }
    }

    @Override
    public Object getKey() {
        return this.key;
    }
}
