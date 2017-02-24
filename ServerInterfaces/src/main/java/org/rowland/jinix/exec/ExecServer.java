package org.rowland.jinix.exec;

import org.rowland.jinix.naming.FileChannel;
import org.rowland.jinix.naming.FileNameSpace;

import java.io.FileNotFoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Properties;

/**
 * Jinix server that executes Jinix executable files (executable jars).
 */
public interface ExecServer extends Remote {

    static final String SERVER_NAME = "/exec";

    int execTranslator(String cmd, String[] args, FileChannel translatorNode, String translatorNodePath)
            throws FileNotFoundException, RemoteException;

    /**
     * Execute
     * ExecLauncher in a new operating system process and
     * @param env
     * @param cmd
     * @param args
     * @param parentId the process ID of the current process, or 0 if the process has no parent.
     * @param stdIn
     * @param stdOut
     * @param stdErr
     * @throws RemoteException
     * @return the process ID of the new process
     * @TODO change cmd parameter from String to FileServer. This may mean reworking the class loading.
     */
    int exec(Properties env,
             String cmd,
             String[] args,
             int parentId,
             FileChannel stdIn,
             FileChannel stdOut,
             FileChannel stdErr)
            throws FileNotFoundException, RemoteException;

    ExecLauncherData execLauncherCallback(int pid) throws RemoteException;
}
