package org.rowland.jinix.naming;

import java.io.IOException;
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
     * scheme should be 'file', and the path should be unique within the system. Usually,
     * the path to the block device or file providing data for the FileNameSpace should
     * be adequate.
     *
     * @return a URI with scheme 'file' that uniquely identifies the FileNameSpace in the system
     */
    URI getURI() throws RemoteException;

    DirectoryFileData getFileAttributes(String filePathName) throws NoSuchFileException, RemoteException;

    void setFileAttributes(String filePathName, DirectoryFileData attributes) throws NoSuchFileException, RemoteException;

    String[] list(String directoryPathName) throws NotDirectoryException, RemoteException;

    boolean createFileAtomically(String parentDirectoryPathName, String newFileName) throws FileAlreadyExistsException, RemoteException;

    boolean createDirectory(String parentDirectoryPathName, String newDirectoryName) throws FileAlreadyExistsException, RemoteException;

    void delete(String filePathName) throws NoSuchFileException, DirectoryNotEmptyException, RemoteException;

    /**
     * Copy the contents of the sourceFile to a new file named targetFileName in the targetDirectory. If sourceFile and
     * targetDirectory exist in different FileNameSpaces, this method must copy data between them. If the sourceFile
     * is a directory, then its entire contents should be copied to a new directory with targetFileName in the targetDirectory.
     *
     * @param sourceFile
     * @param targetDirectory
     * @param targetFileName
     * @param options
     * @throws IOException
     */
    void copy(RemoteFileHandle sourceFile, RemoteFileHandle targetDirectory, String targetFileName, CopyOption... options)
            throws IOException;

    /**
     * Move a file within the FileNameSpace. If the sourceFile and targetDirectory exist in different FileNameSpaces,
     * this method should throw an UnsupportedOperationException.
     *
     * @param sourceFile
     * @param targetDirectory
     * @param targetFileName
     * @param options
     * @throws IOException
     */
    void move(RemoteFileHandle sourceFile, RemoteFileHandle targetDirectory, String targetFileName, CopyOption... options)
            throws IOException;

    /**
     * Lookup and object in this FileNameSpace with the given name. For typical filesystem objects like files and directories,
     * this method will return a RemoteFileHandle. However, since a Jinix filesystem can store an object of any type,
     * the caller must be prepared to receive any Java type as a return value.
     *
     * @param pid the process id of the process initiating the lookup. This value is provided to allow translators to
     *            lookup object that they are translating without receiving a reference to themselves.
     * @param name the name of the object to lookup. Name parts are delimited with '/' characters, and every valid name
     *             must begin with a '/' character.
     * @return
     * @throws RemoteException
     */
    Object lookup(int pid, String name) throws RemoteException;

    RemoteFileAccessor getRemoteFileAccessor(int pid, String filePathName, Set<? extends OpenOption> options) throws FileAlreadyExistsException, NoSuchFileException, RemoteException;

    RemoteFileAccessor getRemoteFileAccessor(int pid, RemoteFileHandle fileHandle, Set<? extends OpenOption> options) throws FileAlreadyExistsException, NoSuchFileException, RemoteException;

    /**
     * Get the FileNameSpace within which this FileNameSpace is contained. Every FileNameSpace except the root has a parent.
     *
     * @return the FileNameSpace within which this FileNameSpace is contained. null for the system root FileNameSpace
     * @throws RemoteException
     */
    FileNameSpace getParent() throws RemoteException;

    /**
     * Get the attachment path where this FileNameSpace is attached to its parent FileNameSpace. The path is relative to
     * the root of the parent FileNameSpace, but contains a leading '/' because it is absolute within the parent
     * FileNameSpace. null for the system root FileNameSpace.
     *
     * @return the attachment path
     * @throws RemoteException
     */
    String getPathWithinParent() throws RemoteException;

    List<FileAccessorStatistics> getOpenFiles(int pid) throws RemoteException;
}
