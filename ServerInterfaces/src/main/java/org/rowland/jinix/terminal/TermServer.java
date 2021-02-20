package org.rowland.jinix.terminal;

import org.rowland.jinix.naming.RemoteFileAccessor;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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
     *
     * @param inputModes
     * @param outputModes
     * @param localModes
     * @param specialCharacterMap
     * @return
     * @throws RemoteException
     */
    short createTerminal(Set<InputMode> inputModes, Set<OutputMode> outputModes, Set<LocalMode> localModes,
                         Map<SpecialCharacter, Byte> specialCharacterMap) throws RemoteException;

    TerminalAttributes getTerminalAttributes(short termId) throws RemoteException;

    void setTerminalAttributes(short termId, TerminalAttributes attributes) throws RemoteException;

    RemoteFileAccessor getTerminalMaster(short termId) throws RemoteException;

    RemoteFileAccessor getTerminalSlave(short termId) throws RemoteException;

    void linkProcessToTerminal(short termId, int pid) throws RemoteException;

    void setTerminalForegroundProcessGroup(short termId, int processGroupId) throws RemoteException;

    int getTerminalForegroundProcessGroup(short termId) throws RemoteException;

    void setTerminalSize(short termId, int columns, int lines) throws RemoteException;

    int getTerminalColumns(short termId) throws RemoteException;

    int getTerminalLines(short termId) throws RemoteException;
}
