package org.rowland.jinix.terminal;

import org.rowland.jinix.naming.FileChannel;
import org.rowland.jinix.naming.FileNameSpace;

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

    FileChannel getTerminalMaster(short termId) throws RemoteException;

    FileChannel getTerminalSlave(short termId) throws RemoteException;

    void linkProcessToTerminal(short termId, int pid) throws RemoteException;
}
