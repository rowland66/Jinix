package org.rowland.jinix.proc;

import org.rowland.jinix.IllegalOperationException;

import javax.management.remote.rmi.RMIServer;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The Jinix ProcessManager is responsible for all active Jinix processes. Processes are registered with the
 * ProcessManager by the ExecServer when a process is started, and deregistered by the process when it shuts down.
 * The ProcessManager support signaling processes and listening for process events.
 */
public interface ProcessManager extends Remote {

    public final String SERVER_NAME = "/proc";
    public final String SERVER_LOGGER = "jinix.proc";

    public enum EventName {
        CHILD, //
        DEREGISTER,
        RESUME
    };

    public enum Signal {
        SHUTDOWN, // Shutdown the Jinix Kernel
        HANGUP, // Tell the process to Hangup, or to reload its configuration
        KILL, // Kill the process with extreme prejudice. The process signal handler is not be called
        TERMINATE, // Tell the process nicely to shutdown
        STOP, // Suspend the process. All Jinix threads will be suspended. The process signal handler is not be called
        TSTOP, // Suspend the process from terminal. This signal will only be sent to processes link to the terminal.
        CONTINUE, // Resume a process that has been suspended with STOP
        CHILD, // Sent to indicate that a child process has terminated
        TERMINAL_INPUT,
        TERMINAL_OUTPUT,
        WINCH // Sent to indicate that the size of the processes terminal has changed.
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
    RegisterResult registerProcess(int parentId, int processGroupId, int sessionId, String cmd, String[] args) throws RemoteException;

    /**
     * Register a remote MBean server connection for the platform MBean server in a Jinix process. The platform MBean
     * server is provided by the JVM and provide instrumentation for the Jinix process.
     *
     * @param id
     * @param remoteMBeanServer
     */
    void registerProcessMBeanServer(int id, RMIServer remoteMBeanServer) throws RemoteException;

    void deRegisterProcess(int id, int exitStatus) throws RemoteException;

    void updateProcessState(int id, ProcessState state) throws RemoteException;

    ProcessData[] getProcessData() throws RemoteException;

    /**
     * Get an array of ProcessData for all processes ordered by the process group ID.
     * @return an array of ProcessData
     * @throws RemoteException
     */
    ProcessData[] getProcessDataByProcessGroup() throws RemoteException;

    ChildEvent waitForChild(int pid, boolean nowait) throws RemoteException;

    ChildEvent waitForChild(int pid, int childPid, boolean nowait) throws RemoteException;

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
