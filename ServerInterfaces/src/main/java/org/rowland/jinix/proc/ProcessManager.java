package org.rowland.jinix.proc;

import org.rowland.jinix.IllegalOperationException;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by rsmith on 11/25/2016.
 */
public interface ProcessManager extends Remote {

    public final String SERVER_NAME = "/proc";
    public final String SERVER_LOGGER = "jinix.proc";

    public enum EventName {CHILD, DEREGISTER, RESUME};

    public enum Signal {SHUTDOWN, // Shutdown the Jinix Kernel
        ABORT, // Sent by the ExecLauncher to itself to signal that the launched process has exited
        HANGUP, // Tell the process to Hangup, or to reload its configuration
        KILL, // Kill the process with extreme prejudice. The process signal handler is not be called
        TERMINATE, // Tell the process nicely to shutdown
        STOP, // Suspend the process. All Jinix threads will be suspended. The process signal handler is not be called
        TSTOP, // Suspend the process from terminal. This signal will only be sent to processes link to the terminal.
        CONTINUE, // Resume a process that has been suspended with STOP
        CHILD, // Sent to indicate that a child process has terminated
        TERMINAL_INPUT,
        TERMINAL_OUTPUT
    };

    public enum ProcessState implements Serializable {
        STARTING,
        RUNNING,
        STOPPING,
        SHUTDOWN,
        SUSPENDED
    }

    public interface ChildEvent {
        public int getPid();
        public int getProcessGroupId();
        public ProcessState getState();
        public int getExitStatus();
    }

    /**
     * Called by ExecServer to register a newly created process with the ProcessManager. This call will assign a
     * new processId.
     *
     * @param parentId processId of the new process's parent
     * @param processGroupId -1 to set the processGroupId to the pid (ie. create a new process group), 0 join the
     *                       processGroup of the parent, any other number assign directly as the processGroupId
     * @param cmd
     * @param args
     * @return
     * @throws RemoteException
     */
    RegisterResult registerProcess(int parentId, int processGroupId, String cmd, String[] args) throws RemoteException;

    void deRegisterProcess(int id, int exitStatus) throws RemoteException;

    void updateProcessState(int id, ProcessState state) throws RemoteException;

    ProcessData[] getProcessData() throws RemoteException;

    ChildEvent waitForChild(int pid, boolean nowait) throws RemoteException;

    void registerEventNotificationHandler(int pid, EventName eventName, EventNotificationHandler handler) throws RemoteException;

    void registerGlobalEventNotificationHandler(EventName eventName, EventNotificationHandler handler) throws RemoteException;

    void sendSignal(int pid, Signal signal) throws RemoteException;

    void sendSignalProcessGroup(int processGroupId, Signal signal) throws RemoteException;

    Signal listenForSignal(int pid) throws RemoteException;

    int setProcessGroupId(int pid, int processGroupId) throws IllegalOperationException, RemoteException;

    int setProcessSessionId(int pid) throws IllegalOperationException, RemoteException;

    int getProcessSessionId(int pid) throws RemoteException;

    void setProcessTerminalId(int pid, short terminalId) throws RemoteException;

    short getProcessTerminalId(int pid) throws RemoteException;
}
