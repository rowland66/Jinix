package org.rowland.jinix.logger;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Access to Jinix kernel logs. All of the logging from kernel servers are retrieved
 * through this API and stored in user space.
 */
public interface LogServer extends Remote {

    static final String SERVER_NAME = "/log";

    /**
     * Retrieve an array of log String up to the number specified by count.
     *
     * @param count the maximum number of log records to return
     * @return an array of formatted log Strings
     * @throws RemoteException
     */
    String[] getLogs(int count) throws RemoteException;

    void setLevel(String server, String levelName) throws UnknownLoggerException, RemoteException;

    int getLevel(String server) throws UnknownLoggerException, RemoteException;
}
