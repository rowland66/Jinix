package org.rowland.jinix.exec;

import org.rowland.jinix.naming.RemoteFileAccessor;

import java.io.FileNotFoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Properties;

/**
 * Jinix server that executes Jinix executable files (executable jars).
 */
public interface ExecServer extends Remote {

    static final String SERVER_NAME = "/exec";
    static final String SERVER_LOGGER = "jinix.exec";

    int execTranslator(String cmd,
                       String[] args,
                       RemoteFileAccessor translatorNode,
                       String translatorNodePath)
            throws FileNotFoundException, InvalidExecutableException, RemoteException;

    /**
     * Execute
     * ExecLauncher in a new operating system process and
     * @param env
     * @param cmd the name of the command to execute
     * @param args
     * @param parentId the process ID of the current process, or 0 if the process has no parent.
     * @param processGroupId -1 to set the processGroupId to the pid (ie. create a new process group), 0 join the
     *                       processGroup of the parent, any other number assign directly as the processGroupId
     * @param sessionId -1 to set the sessionId to the pid (ie. create a new session). 0 to join the session of the
     *                  parent.
     * @param stdIn
     * @param stdOut
     * @param stdErr
     * @throws RemoteException
     * @return the process ID of the new process
     * @TODO change cmd parameter from String to RemoteFileAccessor. This may mean reworking the class loading.
     */
    int exec(Properties env,
             String cmd,
             String[] args,
             int parentId,
             int processGroupId,
             int sessionId,
             RemoteFileAccessor stdIn,
             RemoteFileAccessor stdOut,
             RemoteFileAccessor stdErr)
            throws FileNotFoundException, InvalidExecutableException, RemoteException;

    ExecLauncherData execLauncherCallback(int pid) throws RemoteException;
}
