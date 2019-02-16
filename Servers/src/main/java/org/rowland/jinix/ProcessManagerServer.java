package org.rowland.jinix;

import org.rowland.jinix.io.BaseRemoteFileHandleImpl;
import org.rowland.jinix.io.SimpleDirectoryRemoteFileHandle;
import org.rowland.jinix.naming.*;
import org.rowland.jinix.proc.*;

import javax.management.remote.rmi.RMIServer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A server that manages all processes running in the system. Running processes are created
 * by an Executor and registered with the ProcessManager server. When processes terminate they
 * are deregistered. The ProcessManager supports termination of running processes and any other
 * singnaling. The ProcessManager binds itself into the root namespace at '/proc'.
 */
class ProcessManagerServer extends JinixKernelUnicastRemoteObject implements ProcessManager, FileNameSpace {

    static final Logger logger = Logger.getLogger(SERVER_LOGGER);

    private State state;
    private NameSpace ns;
    private AtomicInteger nextId = new AtomicInteger(1); // Ever increasing counter for the next process id

    private Map<Integer, Proc> processMap; // Map from process id to Proc objects maintained for each running process
    private Map<Integer, List<Proc>> processGroupMap; // Map from process group id to a list of Proc objects belonging to the process group

    private Map<EventName, List<EventNotificationHandler>> globalEventHandlers; // Handlers for global events (DEREGISTER and RESUME)

    private long startUpTime;

    ProcessManagerServer(NameSpace rootNameSpace) throws RemoteException {
        super();
        ns = rootNameSpace;
        processMap = new TreeMap<>();
        processGroupMap = new TreeMap<>();
        globalEventHandlers = new HashMap<>();
        startUpTime = System.currentTimeMillis();
        state = State.RUNNING;

    }

    /**
     * Create and register a new ProcessManager process. This method is called by an Executor.
     *
     * @param parentId process id of the processes parent. Translators have parentId -1, session
     *                 group leaders and daemons have parent id 1
     * @param processGroupId
     * @param cmd
     * @param args
     * @return
     * @throws RemoteException
     */
    @Override
    public synchronized RegisterResult registerProcess(int parentId, int processGroupId, int sessionId, String cmd, String[] args)
            throws RemoteException {

        synchronized (processMap) {
            if (state == State.STOPPING || state == State.SHUTDOWN) {
                RegisterResult rtrn = new RegisterResult();
                rtrn.pid = -1;
                rtrn.pgid = -1;
                return rtrn;
            }

            Proc parentProc = null;
            if (parentId > 0) {
                parentProc = processMap.get(parentId);
                if (parentProc == null) {
                    throw new RemoteException("Invalid parent pid: " + parentId);
                }

                if (processGroupId == 0) {
                    processGroupId = parentProc.processGroup;
                }

                if (sessionId == 0) {
                    sessionId = parentProc.sessionId;
                }
            }

            if (processGroupId == 0) {
                throw new RemoteException("Invalid call processGroupId = 0 and parentId = 0");
            }

            if (sessionId == 0) {
                throw new RemoteException("Invalid call sessionId = 0 and parentId = 0");
            }

            Proc p = new Proc();
            p.id = nextId.getAndIncrement();
            p.parentId = parentId;
            p.processGroup = (processGroupId == -1 ? p.id : processGroupId);
            p.terminal = (parentProc != null ? parentProc.terminal : -1);
            p.sessionId = (sessionId == -1 ? p.id : sessionId);
            p.state = ProcessState.STARTING;
            p.cmd = cmd;
            p.args = args;
            p.startTime = System.currentTimeMillis();
            p.isZombie = false;

            // Processes with parentId == 0 are group leaders and have no parent.
            if (parentProc != null) {
                parentProc.children.add(p);
            }

            p.children = new LinkedList<Proc>();

            p.eventWaiters = new HashMap<>(16);
            p.eventHandlers = new HashMap<>(16);

            p.pendingSignals = new LinkedList<>();

            processMap.put(Integer.valueOf(p.id), p);
            if (p.id == p.processGroup) {
                List<Proc> processGroupList = new LinkedList<>();
                processGroupList.add(p);
                processGroupMap.put(p.processGroup, processGroupList);
            } else {
                List<Proc> processGroupList = processGroupMap.get(p.processGroup);
                synchronized (processGroupList) {
                    processGroupList.add(p);
                }
            }

            RegisterResult rtrn = new RegisterResult();
            rtrn.pid = p.id;
            rtrn.pgid = p.processGroup;

            return rtrn;
        }
    }

    @Override
    public void registerProcessMBeanServer(int id, RMIServer remoteMBeanServer) {
        Proc process = processMap.get(id);
        if (process == null) {
            throw new RuntimeException("Illegal attempt to register remoteMBeanServer for invalid pid: "+id);
        }
        process.platformMBeanServer = remoteMBeanServer;
    }

    /**
     * Deregister a process that has previously been registered with the ProcessManager. When a process is deregistered,
     * any process waiting on the EventName.CHILD event will be notified. If there is no process waiting on the
     * EventName.CHILD event, then the Proc object of the process remains registered as a zombie until some process shows
     * an interest in the EventName.CHILD event. Generate EventName.DEREGISTER events for any processes that have registered
     * to receive them.
     *
     * @param id
     * @return boolean indicating if the process existed and was deregistered. A possible condition could exist where
     * two thread try to de
     */
    @Override
    public synchronized void deRegisterProcess(int id, int exitStatus) {

        synchronized (processMap) {
            Proc p = processMap.get(id);

            if (p == null) {
                throw new IllegalArgumentException("Call to deRegisterProcess with unknown pid: " + id);
            }

            EventData data = new EventData();
            data.pid = p.id;
            data.sessionId = p.sessionId;
            data.terminalId = p.terminal;
            triggerProcessEvent(p, EventName.DEREGISTER, data);
            triggerGlobalEvent(EventName.DEREGISTER, data);

            // If a thread in the terminating process is listening for signals, notify it so that it won't hang the kernel on shutdown
            synchronized (p.pendingSignals) {
                p.pendingSignals.notifyAll();
            }

            // If the process is a daemon (child of init at pid 1) clean it up and return as init does not listen for children.
            /*
            if (p.parentId == 1) {
                synchronized (processMap) {
                    List<Proc> processGroupList = processGroupMap.get(p.processGroup);
                    synchronized (processGroupList) {
                        processGroupList.remove(p);
                        if (processGroupList.isEmpty()) {
                            processGroupMap.remove(p.processGroup);
                        }
                    }
                    processMap.remove(id);
                    return;
                }
            }
            */

            // The only process with parent id 0 is init. If init is shutting down, then shutdown the kernel.
            if (p.parentId == 0) {
                processGroupMap.remove(p.processGroup);
                processMap.remove(p.id);
                state = State.SHUTDOWN;
            } else {
                p.state = ProcessState.SHUTDOWN;
                p.isZombie = true;
                p.exitStatus = exitStatus;

                enqueueParentEventWaiters(p);

                // Any children of the process being deregistered become children of init. They are daemons.
                if (!p.children.isEmpty()) {
                    Proc initProc = processMap.get(1);
                    for (Proc child : p.children) {
                        child.parentId = 1;
                        synchronized (initProc.children) {
                            initProc.children.add(child);
                        }
                    }
                }

                Proc parent = processMap.get(p.parentId);

                synchronized (processGroupMap) {
                    List<Proc> processGroupList = processGroupMap.get(p.processGroup);
                    processGroupList.remove(p);
                    if (processGroupList.isEmpty()) {
                        processGroupMap.remove(p.processGroup);
                    }
                }

                processMap.remove(id);

                if (parent != null) {
                    parent.children.remove(p);
                }
            }
        }

        if (processMap.isEmpty()) {
            System.exit(0);
        }
    }

    @Override
    public void updateProcessState(int id, ProcessState state) throws RemoteException {

        if (state == null) {
            throw new IllegalArgumentException("Call updateProcessState() with null state");
        }

        Proc p = processMap.get(id);

        if (p == null) {
            throw new IllegalArgumentException("Call to updateProcessState() with unknown pid: "+id);
        }

        synchronized (p) {
            ProcessState oldState = p.state;
            p.state = state;

            if (oldState == ProcessState.RUNNING && p.state == ProcessState.SUSPENDED) {
                enqueueParentEventWaiters(p);
            }

            if (oldState == ProcessState.SUSPENDED && p.state == ProcessState.RUNNING) {
                EventData eventData = new EventData();
                eventData.pid = p.id;
                eventData.pgid = p.processGroup;
                eventData.sessionId = p.sessionId;
                eventData.terminalId = p.terminal;
                triggerGlobalEvent(EventName.RESUME, eventData);
            }
        }

        // The SHUTDOWN state is handled by deRegisterProcess.
    }

    private void enqueueParentEventWaiters(Proc p) {

        Proc parent = processMap.get(p.parentId);

        // If we have no parent, then our parent cannot be waiting for an event.
        if (parent == null) {
            return;
        }

        ChildWaitObject childWaitObject = null;
        synchronized (parent.eventWaiters) {
            childWaitObject = parent.eventWaiters.get(EventName.CHILD);
            if (childWaitObject != null){
                for (Proc listObject : childWaitObject.processList) {
                    if (listObject.id == p.id) {
                        return;
                    }
                }
            } else {
                childWaitObject = new ChildWaitObject();
                parent.eventWaiters.put(EventName.CHILD, childWaitObject);
            }
            childWaitObject.processList.add(p);
        }

        synchronized (childWaitObject) {
            if (childWaitObject.waiterCount == 0) {
                sendSignal(p.parentId, Signal.CHILD);
                return;
            }

            childWaitObject.notifyAll();
        }

    }
    /**
     * Return an array of the process IDs for all processes registered with the ProcessManager. IDs of Active and zombie
     * processes will be included in the array.
     *
     * @return an array of process IDs
     * @throws RemoteException
     */
    @Override
    public ProcessData[] getProcessData() throws RemoteException {
        ProcessData[] rtrn = new ProcessData[processMap.size()];
        int cnt = 0;
        for (Proc proc : processMap.values()) {
            ProcessData pd = new ProcessData();
            pd.id =  proc.id;
            pd.parentId = (proc.parentId);
            pd.processGroupId = proc.processGroup;
            pd.sessionId = proc.sessionId;
            pd.terminalId = proc.terminal;
            pd.startTime = proc.startTime;
            pd.state = proc.state;
            pd.cmd = proc.cmd;
            pd.args = proc.args;
            rtrn[cnt++] = pd;
        }
        return rtrn;
    }

    @Override
    public ProcessData[] getProcessDataByProcessGroup() throws RemoteException {
        ProcessData[] rtrn = new ProcessData[processMap.size()];
        int cnt = 0;
        for (List<Proc> procList : processGroupMap.values()) {
            for (Proc proc : procList) {
                ProcessData pd = new ProcessData();
                pd.id = proc.id;
                pd.parentId = (proc.parentId);
                pd.processGroupId = proc.processGroup;
                pd.sessionId = proc.sessionId;
                pd.terminalId = proc.terminal;
                pd.startTime = proc.startTime;
                pd.state = proc.state;
                pd.cmd = proc.cmd;
                pd.args = proc.args;
                rtrn[cnt++] = pd;
            }
        }
        return rtrn;
    }

    /**
     * Wait for a child process of the pid to terminate.
     *
     * @param pid process Id of the parent process.
     * @return
     * @throws RemoteException
     */
    @Override
    public ChildEvent waitForChild(int pid, boolean nowait) throws RemoteException {
        Proc p = processMap.get(pid);

        if (p == null) throw new IllegalArgumentException("ProcessManager: Unknown pid: "+pid);

        synchronized (p.eventWaiters) {
            if (p.eventWaiters.get(EventName.CHILD) == null) {
                p.eventWaiters.put(EventName.CHILD, new ChildWaitObject());
            }
        }

        ChildWaitObject childWaitObject = p.eventWaiters.get(EventName.CHILD);
        while(true) {
            synchronized (childWaitObject) {
                if (childWaitObject.processList.isEmpty()) {
                    if (nowait) return null;
                    childWaitObject.waiterCount++;
                    try {
                        childWaitObject.wait();
                    } catch (InterruptedException e) {
                        // Not sure that this can happen, but just in case it does.
                        return null;
                    } finally {
                        childWaitObject.waiterCount--;
                    }
                }
                if (!childWaitObject.processList.isEmpty()) {
                    Proc childProcess = childWaitObject.processList.removeFirst();
                    return new ChildEventImpl(childProcess.id, childProcess.processGroup, childProcess.state, childProcess.exitStatus);
                }
                // We may get notified when the process has been terminated.
                if (p.state == ProcessState.SHUTDOWN) {
                    return null;
                }
            }
        }
    }

    /**
     * Wait for a particular child process of the pid to terminate.
     *
     * @param pid process Id of the parent process.
     * @param childPid process Id of the child process to wait for.
     * @return a ChildEvent with information about the status and disposition of the child process
     * @throws RemoteException
     */
    @Override
    public ChildEvent waitForChild(int pid, int childPid, boolean nowait) throws RemoteException {
        throw new UnsupportedOperationException();
    }

    /**
     * Register an interest in event notifications with a given name for a process.
     *
     * @param pid
     * @param eventName
     * @param handler
     * @throws RemoteException
     */
    @Override
    public void registerEventNotificationHandler(int pid, EventName eventName, EventNotificationHandler handler) {

        Proc p = processMap.get(pid);

        if (p == null) {
            return;
        }

        if (p.isZombie) {
            return;
        }

        if (p.eventHandlers.containsKey(eventName)) {
            p.eventHandlers.get(eventName).add(handler);
        } else {
            LinkedList<EventNotificationHandler> l = new LinkedList<EventNotificationHandler>();
            l.add(handler);
            p.eventHandlers.put(eventName, l);
        }
    }

    @Override
    public void registerGlobalEventNotificationHandler(EventName eventName, EventNotificationHandler handler) {
        if (globalEventHandlers.containsKey(eventName)) {
            globalEventHandlers.get(eventName).add(handler);
        } else {
            LinkedList<EventNotificationHandler> l = new LinkedList<EventNotificationHandler>();
            l.add(handler);
            globalEventHandlers.put(eventName, l);
        }
    }

    private void triggerProcessEvent(Proc p, EventName eventName, Object data) {
        if (p.eventHandlers.containsKey(eventName)) {
            List<EventNotificationHandler> handlerList = p.eventHandlers.get(eventName);
            for (EventNotificationHandler handler : handlerList) {
                try {
                    handler.handleEventNotification(eventName, data);
                } catch (RemoteException e) {
                    throw new RuntimeException("ProcessData Manager: Failure delivering " + eventName + " event notification for " + p.id, e);
                }
            }
        }
    }

    private void triggerGlobalEvent(EventName eventName, Object data) {
        if (globalEventHandlers.containsKey(eventName)) {
            List<EventNotificationHandler> handlerList = globalEventHandlers.get(eventName);
            for (EventNotificationHandler handler : handlerList) {
                try {
                    handler.handleEventNotification(eventName, data);
                } catch (RemoteException e) {
                    throw new RuntimeException("ProcessData Manager: Failure delivering " + eventName + " global event notification", e);
                }
            }
        }
    }

    @Override
    public void sendSignal(int pid, Signal signal) {

        Proc p = processMap.get(pid);

        if (p == null) {
            return;
        }

        synchronized (p.pendingSignals) {
            logger.fine("Send signal "+signal+" to pid "+pid);
            p.pendingSignals.addLast(signal);
            p.pendingSignals.notify();
        }
    }

    @Override
    public void sendSignalProcessGroup(int processGroupId, Signal signal) {

        List<Proc> processGroupList = processGroupMap.get(processGroupId);

        if (processGroupList == null) {
            return;
        }

        synchronized (processGroupList) {

            for (Proc p : processGroupList) {
                if (p.state != ProcessState.SHUTDOWN || p.state != ProcessState.STOPPING) {
                    synchronized (p.pendingSignals) {
                        p.pendingSignals.addLast(signal);
                        p.pendingSignals.notify();
                    }
                }
            }
        }
    }

    @Override
    public Signal listenForSignal(int pid) throws RemoteException {
        Proc p = processMap.get(pid);

        if (p == null) throw new IllegalArgumentException("ProcessManager: Unknown pid: "+pid);

        logger.fine("listen for signal: " + pid);
        synchronized (p.pendingSignals) {
            while (p.pendingSignals.isEmpty()) {
                try {
                    p.pendingSignals.wait();
                } catch (InterruptedException e) {
                    return null;
                }
            }

            logger.fine("listen for signal exiting: " + pid);

            if (!p.pendingSignals.isEmpty()) {
                Signal rtrnSignal = p.pendingSignals.removeFirst();
                logger.fine("PID "+pid+" received signal "+rtrnSignal);
                return rtrnSignal;
            } else {
                return null;
            }
        }
    }

    @Override
    public int setProcessGroupId(int pid, int processGroupId) throws IllegalOperationException, RemoteException {

        if (processGroupId < -1) {
            throw new IllegalArgumentException("processGroupId < -1");
        }

        Proc p = processMap.get(pid);

        synchronized (p) {
            if (p.processGroup == p.id) {
                throw new IllegalOperationException("Process group leader cannot move to another process group");
            }

            if (processGroupId > 1) {
                Proc processGroupLeader = processMap.get(processGroupId);
                if (processGroupLeader == null || p.sessionId != processGroupLeader.sessionId) {
                    throw new IllegalOperationException("Process cannot be moved to a process group in a different session");
                }
            }

            if (processGroupId == -1) {
                p.processGroup = p.id;
                return p.processGroup;
            }

            synchronized (processGroupMap) {
                List<Proc> processGroupList = processGroupMap.get(p.processGroup);
                processGroupList.remove(p);
                if (processGroupList.isEmpty()) {
                    processGroupMap.remove(p.processGroup);
                }

                p.processGroup = processGroupId;

                if (p.id == p.processGroup) {
                    processGroupList = new LinkedList<>();
                    processGroupList.add(p);
                    processGroupMap.put(p.processGroup, processGroupList);
                } else {
                    processGroupList = processGroupMap.get(p.processGroup);
                    synchronized (processGroupList) {
                        processGroupList.add(p);
                    }
                }
            }

            return p.processGroup;
        }
    }

    @Override
    public int getProcessSessionId(int pid) {

        Proc p = processMap.get(pid);

        if (p == null) throw new IllegalArgumentException("ProcessManager: Unknown pid: "+pid);

        return p.sessionId;
    }

    @Override
    public int setProcessSessionId(int pid) {

        Proc p = processMap.get(pid);

        if (p == null) throw new IllegalArgumentException("ProcessManager: Unknown pid: " + pid);

        synchronized (p) {

            if (p.id == p.processGroup) {
                throw new IllegalOperationException("Session cannot be changed for process group leader");
            }

            p.sessionId = p.id;

            synchronized (processGroupMap) {
                List<Proc> processGroupList = processGroupMap.get(p.processGroup);
                synchronized (processGroupList) {
                    processGroupList.remove(p);
                    if (processGroupList.isEmpty()) {
                        processGroupMap.remove(p.processGroup);
                    }
                }
            }

            p.processGroup = p.id;

            synchronized (processMap) {
                List<Proc> processGroupList = new LinkedList<>();
                processGroupList.add(p);
                processGroupMap.put(p.processGroup, processGroupList);
            }

            // POSIX says we should do this, but until a process can acquire a terminal it creates a race condition.
            //p.terminal = -1;

            return p.processGroup;
        }
    }

    @Override
    public void setProcessTerminalId(int pid, short terminalId) {
        Proc p = processMap.get(pid);

        if (p == null) throw new IllegalArgumentException("ProcessManager: Unknown pid: "+pid);

        synchronized (p) {
            // Once the terminal ID is set, it cannot be changed to any value other than -1
            if (p.terminal != -1 && terminalId != -1) {
                return;
            }

            p.terminal = terminalId;
        }
    }

    @Override
    public short getProcessTerminalId(int pid) throws RemoteException {
        Proc p = processMap.get(pid);

        if (p == null) throw new IllegalArgumentException("ProcessManager: Unknown pid: "+pid);

        return p.terminal;
    }

    // Start of FileNameSpace interface implementation

    @Override
    public URI getURI() throws RemoteException {
        try {
            return new URI("file", null, ProcessManager.SERVER_NAME, null);
        } catch (URISyntaxException e) {
            throw new RemoteException("Unexpected failure creating FileNameSpace URI", e);
        }
    }

    @Override
    public DirectoryFileData getFileAttributes(String filePathName) throws NoSuchFileException, RemoteException {
        if (!filePathName.startsWith("/")) {
            throw new RemoteException("Invalid file name: "+filePathName);
        }

        ProcessManageerVirtualFileSystem pmvfs = new ProcessManageerVirtualFileSystem();
        PMVFSNode node = pmvfs.lookup(filePathName);
        if (node != null) {
            return node.dfd;
        }

        throw new NoSuchFileException(filePathName);
    }

    @Override
    public void setFileAttributes(String filePathName, DirectoryFileData attributes) throws NoSuchFileException, RemoteException {

    }

    @Override
    public String[] list(String directoryPathName) throws NotDirectoryException, RemoteException {

        if (!directoryPathName.startsWith("/")) {
            throw new RemoteException("Invalid file name: " + directoryPathName);
        }

        ProcessManageerVirtualFileSystem pmvfs = new ProcessManageerVirtualFileSystem();
        PMVFSNode node = pmvfs.lookup(directoryPathName);

        if (node instanceof PMVFSDirectoryNode) {
            return ((PMVFSDirectoryNode) node).getContents();
        }

        throw new NotDirectoryException(directoryPathName);
    }

    @Override
    public boolean createFileAtomically(String parentDirectoryPathName, String fileName) throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean createDirectory(String parentDirectoryPathName, String fileName) throws FileAlreadyExistsException, RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String filePathName) throws NoSuchFileException, DirectoryNotEmptyException, RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copy(RemoteFileHandle sourceFile, RemoteFileHandle targetFile, String fileName, CopyOption... options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void move(RemoteFileHandle sourceFile, RemoteFileHandle targetFile, String fileName, CopyOption... options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RemoteFileAccessor getRemoteFileAccessor(int pid, String name, Set<? extends OpenOption> options) throws FileAlreadyExistsException, NoSuchFileException, RemoteException {

        ProcessManageerVirtualFileSystem pmvfs = new ProcessManageerVirtualFileSystem();
        PMVFSNode node = pmvfs.lookup(name);

        if (node instanceof PMVFSFileNode) {
            return new StringRemoteFileAccessor(this, name, ((PMVFSFileNode) node).getContents());
        }

        if (node instanceof PMVFSDataNode) {
            return new StringRemoteFileAccessor(this, name, ((PMVFSDataNode) node).getContents());
        }

        throw new NoSuchFileException(name);
    }

    @Override
    public RemoteFileAccessor getRemoteFileAccessor(int pid, RemoteFileHandle fileHandle, Set<? extends OpenOption> options) throws FileAlreadyExistsException, NoSuchFileException, RemoteException {
        return getRemoteFileAccessor(pid, fileHandle.getPath(), options);
    }

    @Override
    public Object lookup(int pid, String name) throws RemoteException {
        ProcessManageerVirtualFileSystem pmvfs = new ProcessManageerVirtualFileSystem();
        PMVFSNode node = pmvfs.lookup(name);
        if (node instanceof PMVFSDirectoryNode) {
            return new SimpleDirectoryRemoteFileHandle(this, name);
        }
        if (node instanceof PMVFSFileNode) {
            return new BaseRemoteFileHandleImpl(this, name);
        }

        if (node instanceof PMVFSDataNode) {
            return new BaseRemoteFileHandleImpl(this, name);
        }

        if (node instanceof PMVFSObjectNode) {
            return ((PMVFSObjectNode) node).getObject();
        }

        return null;
    }

    @Override
    public List<FileAccessorStatistics> getOpenFiles(int pid) throws RemoteException {
        return null;
    }

    @Override
    public FileNameSpace getParent() throws RemoteException {
        return JinixKernel.getNameSpaceRoot().getRootFileSystem();
    }

    @Override
    public String getPathWithinParent() throws RemoteException {
        return "/proc";
    }

    // End of FileNameSpace interface implementation

    private static class ProcFNSParts {
        int pid = -1;
        ComponentEnum component;
        String operand;
    }

    private enum ComponentEnum {
        cmdline, CWD, EXE, files, FILEINFO, mbeans
    }

    private enum PMVFSDirectoryNodeType {
        ROOT, PID, FILES
    }

    /**
     * A virtual files system for the /proc FileNameSpace. This class provides a filesystem abstraction that several
     * methods implementing the FileNameSpace interface can share. The process manager data is presented through this
     * virtual file system.
     */
    private class ProcessManageerVirtualFileSystem {

        /**
         * Retrieve a node that represents a node in the virtual file system.
         *
         * @param pathName
         * @return
         */
        PMVFSNode lookup(String pathName) {

            try {
                ProcFNSParts parts = null;
                try {
                    parts = parseProcFNSParts(pathName);
                } catch (NoSuchFileException e) {
                    return null;
                }

                if (parts.pid == -1) {
                    DirectoryFileData rtrnDfd = new DirectoryFileData();
                    rtrnDfd.name = "";
                    rtrnDfd.length = 0;
                    rtrnDfd.lastModified = 0;
                    rtrnDfd.type = DirectoryFileData.FileType.DIRECTORY;
                    PMVFSDirectoryNode rtrnNode = new PMVFSDirectoryNode(PMVFSDirectoryNodeType.ROOT);
                    rtrnNode.pathName = pathName;
                    rtrnNode.dfd = rtrnDfd;
                    return rtrnNode;
                }

                Proc proc = processMap.get(parts.pid);
                if (proc == null) {
                    return null;
                }

                if (parts.component == null) {
                    DirectoryFileData rtrnDfd = new DirectoryFileData();
                    rtrnDfd.name = Integer.toString(parts.pid);
                    rtrnDfd.length = 0;
                    rtrnDfd.lastModified = proc.startTime;
                    rtrnDfd.type = DirectoryFileData.FileType.DIRECTORY;
                    PMVFSDirectoryNode rtrnNode = new PMVFSDirectoryNode(PMVFSDirectoryNodeType.PID);
                    rtrnNode.pathName = pathName;
                    rtrnNode.dfd = rtrnDfd;
                    return rtrnNode;
                }

                if (parts.component == ComponentEnum.files) {

                    if (parts.operand == null) {
                        DirectoryFileData rtrnDfd = new DirectoryFileData();
                        rtrnDfd.name = ComponentEnum.files.name();
                        rtrnDfd.length = 0;
                        rtrnDfd.lastModified = 0;
                        rtrnDfd.type = DirectoryFileData.FileType.DIRECTORY;
                        PMVFSDirectoryNode rtrnNode = new PMVFSDirectoryNode(PMVFSDirectoryNodeType.FILES);
                        rtrnNode.pathName = pathName;
                        rtrnNode.dfd = rtrnDfd;
                        rtrnNode.key = new Integer(parts.pid);
                        return rtrnNode;
                    } else {
                        List<FileAccessorStatistics> l = ns.getOpenFiles(parts.pid);
                        FileAccessorStatistics fas = l.get(Integer.valueOf(parts.operand));
                        if (fas != null) {
                            DirectoryFileData rtrnDfd = new DirectoryFileData();
                            rtrnDfd.name = parts.operand;
                            rtrnDfd.length = 0;
                            rtrnDfd.lastModified = 0;
                            rtrnDfd.type = DirectoryFileData.FileType.FILE;
                            PMVFSFileNode rtrnNode = new PMVFSFileNode();
                            rtrnNode.pathName = pathName;
                            rtrnNode.dfd = rtrnDfd;
                            rtrnNode.key = fas;
                            return rtrnNode;
                        }
                        return null;
                    }
                } else if (parts.component == ComponentEnum.cmdline) {
                    if (parts.operand == null) {
                        StringBuilder pdb = new StringBuilder();
                        pdb.append(proc.cmd);
                        for (String arg : proc.args) {
                            pdb.append(" " + arg);
                        }
                        pdb.append("\n");
                        DirectoryFileData rtrnDfd = new DirectoryFileData();
                        rtrnDfd.name = parts.operand;
                        rtrnDfd.length = 0;
                        rtrnDfd.lastModified = 0;
                        rtrnDfd.type = DirectoryFileData.FileType.FILE;
                        PMVFSDataNode rtrnNode = new PMVFSDataNode();
                        rtrnNode.pathName = pathName;
                        rtrnNode.dfd = rtrnDfd;
                        rtrnNode.data = pdb.toString();
                        return rtrnNode;
                    }
                } else if (parts.component == ComponentEnum.mbeans) {
                    if (parts.operand == null) {
                        DirectoryFileData rtrnDfd = new DirectoryFileData();
                        rtrnDfd.name = parts.operand;
                        rtrnDfd.length = 0;
                        rtrnDfd.lastModified = 0;
                        rtrnDfd.type = DirectoryFileData.FileType.FILE;
                        PMVFSObjectNode rtrnNode = new PMVFSObjectNode();
                        rtrnNode.pathName = pathName;
                        rtrnNode.dfd = rtrnDfd;
                        rtrnNode.object = proc.platformMBeanServer;
                        return rtrnNode;
                    }
                } else {
                    return null;
                }
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        /**
         * Break a FileNameSpace name into parts and parse into the structure of the proc FileNameSpace
         *
         * @param name
         * @return
         * @throws NoSuchFileException
         */
        ProcFNSParts parseProcFNSParts(String name) throws NoSuchFileException {
            name = name.substring(1); // remove leading '/'
            String[] names = name.split("/");

            ProcFNSParts rtrn = new ProcFNSParts();

            if (names.length > 0 && !names[0].isEmpty()) {
                try {
                    rtrn.pid = Integer.parseInt(names[0]);
                } catch (NumberFormatException e) {
                    throw new NoSuchFileException(name);
                }
            }
            if (names.length > 1) {
                if (names[1].equals(ComponentEnum.files.name())) {
                    rtrn.component = ComponentEnum.files;
                } else if (names[1].equals(ComponentEnum.cmdline.name())) {
                    rtrn.component = ComponentEnum.cmdline;
                } else if (names[1].equals(ComponentEnum.mbeans.name())) {
                    rtrn.component = ComponentEnum.mbeans;
                } else {
                    throw new NoSuchFileException(name);
                }
            }
            if (names.length > 2) {
                if (rtrn.component == ComponentEnum.files) {
                    rtrn.operand = names[2];
                } else {
                    throw new NoSuchFileException(name);
                }
            }

            if (names.length > 3) {
                throw new NoSuchFileException(name);
            }

            return rtrn;
        }

    }

    static class PMVFSNode {
        String pathName;
        DirectoryFileData dfd;
    }

    class PMVFSDirectoryNode extends PMVFSNode {
        PMVFSDirectoryNodeType nodeType;
        Object key;

        PMVFSDirectoryNode(PMVFSDirectoryNodeType type) {
            nodeType = type;
        }

        String[] getContents() {

            try {
                if (nodeType == PMVFSDirectoryNodeType.ROOT) {
                    String[] rtrn = new String[processMap.size()];
                    int i = 0;
                    for (Integer pid : processMap.keySet()) {
                        rtrn[i++] = pid.toString();
                    }
                    return rtrn;
                }

                if (nodeType == PMVFSDirectoryNodeType.PID) {
                    String[] rtrn = new String[]{ComponentEnum.cmdline.name(),ComponentEnum.files.name(), ComponentEnum.mbeans.name()};
                    return rtrn;
                }

                if (nodeType == PMVFSDirectoryNodeType.FILES) {

                    Proc proc = processMap.get(key);
                    if (proc == null) {
                        return new String[0];
                    }

                    ArrayList<String> fileList = new ArrayList<String>(64);
                    List<FileAccessorStatistics> l = ns.getOpenFiles(((Integer) key).intValue());
                    int fileCounter = 0;
                    for (FileAccessorStatistics fas : l) {
                        fileList.add(Integer.toString(fileCounter++));
                    }
                    return fileList.toArray(new String[fileList.size()]);
                }
                return null;
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class PMVFSFileNode extends PMVFSNode {
        FileAccessorStatistics key;

        String getContents() {
            try {
                return key.getAbsolutePathName();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class PMVFSDataNode extends PMVFSNode {
        String data;

        String getContents() {
            return data;
        }
    }

    static class PMVFSObjectNode extends PMVFSNode {
        Remote object;

        Remote getObject() {
            return object;
        }
    }

    static class Proc {
        int id;
        int parentId;
        int processGroup;
        short terminal;
        ProcessManager.ProcessState state;
        String cmd;
        String[] args;
        long startTime;
        Process osProcess;
        int exitStatus;
        boolean isZombie;
        int sessionId;
        List<Proc> children;
        Map<EventName, ChildWaitObject> eventWaiters;
        Map<EventName, List<EventNotificationHandler>> eventHandlers;
        Deque<Signal> pendingSignals;
        RMIServer platformMBeanServer;
    }

    private static class ChildWaitObject {
        int waiterCount; // the number of waiters for this child event
        int childPid = -1; // the
        Deque<Proc> processList = new LinkedList<>(); // a list of processes that have triggered a child Event
    }

    private static enum State {
        RUNNING,
        STOPPING,
        SHUTDOWN
    }
}
