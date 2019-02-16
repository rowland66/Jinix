package org.rowland.jinix.naming;

import org.rowland.jinix.exec.InvalidExecutableException;

import java.io.FileNotFoundException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.EnumSet;
import java.util.List;

/**
 * The Jinix NameSpace
 */
public interface NameSpace extends java.rmi.Remote {

    static final String SERVER_LOGGER = "jinix.ns";

    enum BindTranslatorOption {
        ACTIVATE,
        PASSIVE,
        FORCE;
    }

    void bind(String path, Remote remoteObj) throws RemoteException;

    void unbind(String path) throws RemoteException;

    void bindTranslator(String path, String cmd, String[] args, EnumSet<BindTranslatorOption> options)
            throws FileNotFoundException, InvalidExecutableException, RemoteException;

    void unbindTranslator(String path, EnumSet<BindTranslatorOption> options) throws RemoteException;

    /**
     * Lookup from within the Jinix kernel where a pid is not available. This method will simply call the other
     * lookup with pid value of 0. This method is not to be used outside of the kernel.
     *
     * @param path the hierarchical name to lookup
     * @return
     * @throws RemoteException
     */

    Object lookup(String path) throws RemoteException;

    Object lookup(int pid, String path) throws RemoteException;

    List<FileAccessorStatistics> getOpenFiles(int pid) throws RemoteException;

    void translatorFailure(String path) throws RemoteException;
}
