package org.rowland.jinix.terminal;

import java.rmi.RemoteException;

/**
 * Exception signals that an operation on TerminalFileChannel has been blocked. The process group of the process that
 * performed the blocking operation will be sent a signal causing them to suspend.
 */
public class TerminalBlockedOperationException extends RuntimeException {

    public TerminalBlockedOperationException() {
        super();
    }
}
