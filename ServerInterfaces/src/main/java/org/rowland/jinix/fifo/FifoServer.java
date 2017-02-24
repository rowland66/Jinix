package org.rowland.jinix.fifo;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Server that provide first in first out pipes.
 */
public interface FifoServer extends Remote {

    public static final String SERVER_NAME = "/fifo";

    FileChannelPair openPipe() throws RemoteException;
}
