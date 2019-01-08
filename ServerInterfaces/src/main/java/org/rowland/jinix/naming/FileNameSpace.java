package org.rowland.jinix.naming;

import java.net.URI;
import java.nio.file.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

/**
 * Primary interface for a Jinix file system.
 */
public interface FileNameSpace extends Remote {

    /**
     * Return the URI that uniquely identifies the FileNameSpace in the system. The URI
     * scheme should be file, and the path should be unique within the system. Usually,
     * the path to the block device or file providing data for the FileNameSpace should
     * be adequate.
     *
     * @return a URI with scheme 'file' that uniquely identifies the FileNameSpace in the system
     */
    URI getURI() throws RemoteException;

    DirectoryFileData getFileAttributes(String name) throws NoSuchFileException, RemoteException;

    void setFileAttributes(String name, DirectoryFileData attributes) throws NoSuchFileException, RemoteException;

    boolean exists(String name) throws InvalidPathException, RemoteException;

    String[] list(String name) throws NotDirectoryException, RemoteException;

    DirectoryFileData[] listDirectoryFileData(String name) throws NotDirectoryException, RemoteException;

    boolean createFileAtomically(String name) throws FileAlreadyExistsException, RemoteException;

    boolean createDirectory(String name) throws FileAlreadyExistsException, RemoteException;

    boolean createDirectories(String name) throws FileAlreadyExistsException, RemoteException;

    void delete(String name) throws NoSuchFileException, DirectoryNotEmptyException, RemoteException;

    void copy(String nameFrom, String pathNameTo, CopyOption... options)
            throws NoSuchFileException, FileAlreadyExistsException, UnsupportedOperationException, RemoteException;

    void move(String nameFrom, String pathNameTo, CopyOption... options)
            throws NoSuchFileException, FileAlreadyExistsException, UnsupportedOperationException, RemoteException;

    LookupResult lookup(int pid, String name);

    RemoteFileAccessor getRemoteFileAccessor(int pid, String name, Set<? extends OpenOption> options) throws FileAlreadyExistsException, NoSuchFileException, RemoteException;

    Object getKey(String name) throws RemoteException;

    List<FileAccessorStatistics> getOpenFiles(int pid) throws RemoteException;
}
