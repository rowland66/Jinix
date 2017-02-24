package org.rowland.jinix.naming;

import java.nio.file.*;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Primary interface for a Jinix file system.
 */
public interface FileNameSpace extends Remote {

    DirectoryFileData getFileAttributes(String name) throws NoSuchFileException, RemoteException;

    void setFileAttributes(String name, DirectoryFileData attributes) throws NoSuchFileException, RemoteException;

    boolean exists(String name) throws InvalidPathException, RemoteException;

    String[] list(String name) throws NotDirectoryException, RemoteException;

    DirectoryFileData[] listDirectoryFileData(String name) throws NotDirectoryException, RemoteException;

    boolean createFileAtomically(String name) throws FileAlreadyExistsException, RemoteException;

    boolean createDirectory(String name) throws FileAlreadyExistsException, RemoteException;

    boolean createDirectories(String name) throws FileAlreadyExistsException, RemoteException;

    void delete(String name) throws NoSuchFileException, DirectoryNotEmptyException, RemoteException;

    void move(String nameFrom, String pathNameTo, CopyOption... options) throws NoSuchFileException, FileAlreadyExistsException, RemoteException;

    FileChannel getFileChannel(String name, OpenOption... options) throws FileAlreadyExistsException, NoSuchFileException, RemoteException;

    Object getKey(String name) throws RemoteException;
}
