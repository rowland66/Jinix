package org.rowland.jinix.naming;

import org.rowland.jinix.exec.InvalidExecutableException;

import java.io.FileNotFoundException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.EnumSet;

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

    LookupResult lookup(String path) throws RemoteException;
}
