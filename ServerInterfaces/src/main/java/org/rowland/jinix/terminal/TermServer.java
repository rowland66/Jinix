package org.rowland.jinix.terminal;

import org.rowland.jinix.naming.RemoteFileAccessor;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

/**
 * A server that provides terminals. A terminal has a master and slave that are used move data
 * between a process and a
 */
public interface TermServer extends Remote {

    public static final String SERVER_NAME = "/term";

    /**
     * Create a new terminal with no encoded terminal options.
     *
     * @return
     * @throws RemoteException
     */
    short createTerminal() throws RemoteException;

    /**
     * Create a new terminal with the given encoded terminal options.
     * @param ttyOptions
     * @return
     * @throws RemoteException
     */
    short createTerminal(Map<PtyMode, Integer> ttyOptions) throws RemoteException;

    RemoteFileAccessor getTerminalMaster(short termId) throws RemoteException;

    RemoteFileAccessor getTerminalSlave(short termId) throws RemoteException;

    void linkProcessToTerminal(short termId, int pid) throws RemoteException;

    void setTerminalForegroundProcessGroup(short termId, int processGroupId) throws RemoteException;
}
