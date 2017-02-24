package org.rowland.jinix.proc;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by rsmith on 11/25/2016.
 */
public interface ProcessManager extends Remote {

    public final String SERVER_NAME = "/proc";

    public enum EventName {CHILD, DEREGISTER};

    public enum Signal {SHUTDOWN, ABORT, HANGUP, KILL, TERMINATE, CHILD};

    int registerProcess(int parentId, String cmd, String[] args) throws RemoteException;

    void deRegisterProcess(int id) throws RemoteException;

    int[] listProcessIDs() throws RemoteException;

    int waitForChild(int pid) throws RemoteException;

    void registerEventNotificationHandler(int pid, EventName eventName, EventNotificationHandler handler) throws RemoteException;

    void sendSignal(int pid, Signal signal) throws RemoteException;

    Signal listenForSignal(int pid) throws RemoteException;
}
