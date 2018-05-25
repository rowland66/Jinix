package org.rowland.jinix;

import org.rowland.jinix.naming.*;
import org.rowland.jinix.proc.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Semaphore;
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

    Map<Integer, Proc> processMap; // Map from process id to Proc objects maintained for each running process
    Map<Integer, List<Proc>> processGroupMap; // Map from process group id to a list of Proc objects belonging to the process group

    Map<EventName, List<EventNotificationHandler>> globalEventHandlers; // Handlers for global events (DEREGISTER and RESUME)

    long startUpTime;

    ProcessManagerServer(NameSpace rootNameSpace) throws RemoteException {
        super();
        ns = rootNameSpace;
        processMap = new HashMap<>();
        processGroupMap = new HashMap<>();
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

        if (state == State.STOPPING || state == State.SHUTDOWN) {

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

            synchronized (parentProc.children) {
                parentProc.children.add(p);
            }
        }

        p.children = new LinkedList<Proc>();

        p.eventWaiters = new HashMap<>(16);
        p.eventHandlers = new HashMap<>(16);

        p.pendingSignals = new LinkedList<>();

        synchronized (processMap) {
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
        }

        RegisterResult rtrn = new RegisterResult();
        rtrn.pid = p.id;
        rtrn.pgid = p.processGroup;

        return rtrn;
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
        Proc p = processMap.get(id);

        if (p == null) {
            throw new IllegalArgumentException("Call to deRegisterProcess with unknown pid: "+id);
        }

        EventData data = new EventData();
        data.pid = p.id;
        data.sessionId = p.sessionId;
        data.terminalId = p.terminal;
        triggerProcessEvent(p, EventName.DEREGISTER, data);
        triggerGlobalEvent(EventName.DEREGISTER, data);

        // If a thread in the terminating process is listening for signals, notify it so that it won't hang the kernel on shutdown
        synchronized(p.pendingSignals) {
            p.pendingSignals.notifyAll();
        }

        // The only process with parent id 0 is init. If init is shutting down, then shutdown the kernel.
        if (p.parentId == 0) {

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

        synchronized (processMap) {
            List<Proc> processGroupList = processGroupMap.get(p.processGroup);
            synchronized (processGroupList) {
                processGroupList.remove(p);
                if (processGroupList.isEmpty()) {
                    processGroupMap.remove(p.processGroup);
                }
            }
            processMap.remove(id);
            parent.children.remove(p);
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

        if (p.isZombie) return;

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

        if (signal == Signal.SHUTDOWN) {
            JinixKernel.shutdown();
            return;
        }

        Proc p = processMap.get(pid);

        if (p == null) {
            return;
        }

        synchronized (p.pendingSignals) {
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
                return p.pendingSignals.removeFirst();
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

        synchronized (processMap) {
            List<Proc> processGroupList = processGroupMap.get(p.processGroup);
            synchronized (processGroupList) {
                processGroupList.remove(p);
                if (processGroupList.isEmpty()) {
                    processGroupMap.remove(p.processGroup);
                }
            }
        }

        p.processGroup = processGroupId;

        synchronized (processMap) {
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
        }

        return p.processGroup;
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

        if (p == null) throw new IllegalArgumentException("ProcessManager: Unknown pid: "+pid);

        if (p.id == p.processGroup) {
            throw new IllegalOperationException("Session cannot be changed for process group leader");
        }

        p.sessionId = p.id;

        synchronized (processMap) {
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

    @Override
    public void setProcessTerminalId(int pid, short terminalId) {
        Proc p = processMap.get(pid);

        if (p == null) throw new IllegalArgumentException("ProcessManager: Unknown pid: "+pid);

        // Once the terminal ID is set, it cannot be changed to any value other than -1
        if (p.terminal != -1 && terminalId != -1) {
            return;
        }

        p.terminal = terminalId;
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
    public DirectoryFileData getFileAttributes(String name) throws NoSuchFileException, RemoteException {
        if (!name.startsWith("/")) {
            throw new RemoteException("Invalid file name: "+name);
        }

        name = name.trim().substring(1);

        if (name.isEmpty()) {
            DirectoryFileData rtrn = new DirectoryFileData();
            rtrn.lastModified = getStartUpTime();
            rtrn.length = 0;
            rtrn.name = "proc";
            rtrn.type = DirectoryFileData.FileType.DIRECTORY;
            return rtrn;
        }

        ProcFNSParts parts = parseProcFNSParts(name);

        ProcessManagerServer.Proc proc = processMap.get(parts.pid);
        if (proc == null) {
            throw new NoSuchFileException(name);
        }

        if (parts.component == null) {
            DirectoryFileData rtrn = new DirectoryFileData();
            rtrn.lastModified = proc.startTime;
            rtrn.name = Integer.toString(parts.pid);
            rtrn.type = DirectoryFileData.FileType.DIRECTORY;
            return rtrn;
        }

        if (parts.component == ComponentEnum.FILES) {

            if (parts.operand == null) {
                DirectoryFileData rtrn = new DirectoryFileData();
                rtrn.lastModified = proc.startTime;
                rtrn.name = "files";
                rtrn.type = DirectoryFileData.FileType.DIRECTORY;
                return rtrn;
            } else {
                List<FileAccessorStatistics> l = ns.getOpenFiles(parts.pid);
                for (FileAccessorStatistics fas : l) {
                    String fileName = fas.getAbsolutePathName();
                    if (parts.operand.equals(fileName)) {
                        DirectoryFileData rtrn = new DirectoryFileData();
                        rtrn.lastModified = proc.startTime;
                        rtrn.name = fileName;
                        rtrn.type = DirectoryFileData.FileType.FILE;
                        return rtrn;
                    }
                }
                throw new NoSuchFileException(name);
            }
        } else if (parts.component == ComponentEnum.CMDLINE) {
            if (parts.operand == null) {
                DirectoryFileData rtrn = new DirectoryFileData();
                rtrn.lastModified = proc.startTime;
                rtrn.name = "cmdline";
                rtrn.type = DirectoryFileData.FileType.FILE;
                return rtrn;
            }
            throw new NoSuchFileException(name);
        } else {
            throw new NoSuchFileException(name);
        }
    }

    @Override
    public void setFileAttributes(String name, DirectoryFileData attributes) throws NoSuchFileException, RemoteException {

    }

    @Override
    public boolean exists(String name) throws RemoteException {
        return true;
    }

    @Override
    public String[] list(String name) throws NotDirectoryException, RemoteException {
        if (!name.startsWith("/")) {
            throw new RemoteException("Invalid file name: " + name);
        }

        name = name.trim().substring(1);

        if (name.isEmpty()) {
            String[] rtrn = new String[processMap.size()];
            int i = 0;
            for (Integer pid : processMap.keySet()) {
                rtrn[i++] = pid.toString();
            }
            return rtrn;
        }

        ProcFNSParts parts = null;
        try {
            parts = parseProcFNSParts(name);
        } catch (NoSuchFileException e) {
            throw new NotDirectoryException(name);
        }

        ProcessManagerServer.Proc proc = processMap.get(parts.pid);
        if (proc == null) {
            throw new NotDirectoryException(name);
        }

        if (parts.component == null) {
            String[] rtrn = new String[]{"cmdline","files"};
            return rtrn;
        }

        if (parts.component == ComponentEnum.FILES) {

            if (parts.operand == null) {
                ArrayList<String> fileList = new ArrayList<String>(64);
                List<FileAccessorStatistics> l = ns.getOpenFiles(parts.pid);
                for (FileAccessorStatistics fas : l) {
                    fileList.add(fas.getAbsolutePathName());
                }
                return fileList.toArray(new String[fileList.size()]);
            } else {
                throw new NotDirectoryException(name);
            }
        }

        throw new NotDirectoryException(name);
    }

    @Override
    public DirectoryFileData[] listDirectoryFileData(String name) throws NotDirectoryException, RemoteException {
        if (!name.startsWith("/")) {
            throw new RemoteException("Invalid file name: " + name);
        }

        name = name.trim().substring(1);

        if (name.isEmpty()) {
            DirectoryFileData[] rtrn = new DirectoryFileData[processMap.size()];
            int i = 0;
            for (Integer pid : processMap.keySet()) {
                DirectoryFileData dfd = new DirectoryFileData();
                dfd.name = pid.toString();
                dfd.length = 0;
                dfd.lastModified = processMap.get(pid).startTime;
                dfd.type = DirectoryFileData.FileType.DIRECTORY;
                rtrn[i++] = dfd;
            }
            return rtrn;
        }

        ProcFNSParts parts = null;
        try {
            parts = parseProcFNSParts(name);
        } catch (NoSuchFileException e) {
            throw new NotDirectoryException(name);
        }

        ProcessManagerServer.Proc proc = processMap.get(parts.pid);
        if (proc == null) {
            throw new NotDirectoryException(name);
        }

        if (parts.component == null) {
            DirectoryFileData cmdline = new DirectoryFileData();
            cmdline.name = "cmdline";
            cmdline.length = 0;
            cmdline.lastModified = 0;
            cmdline.type = DirectoryFileData.FileType.FILE;

            DirectoryFileData files = new DirectoryFileData();
            files.name = "files";
            files.length = 0;
            files.lastModified = 0;
            files.type = DirectoryFileData.FileType.DIRECTORY;

            return new DirectoryFileData[]{files};
        }

        if (parts.component == ComponentEnum.FILES) {

            if (parts.operand == null) {
                ArrayList<DirectoryFileData> fileList = new ArrayList<DirectoryFileData>(64);
                List<FileAccessorStatistics> l = ns.getOpenFiles(parts.pid);
                for (FileAccessorStatistics fas : l) {
                    DirectoryFileData dfd = new DirectoryFileData();
                    dfd.name = fas.getAbsolutePathName();
                    dfd.length = 0;
                    dfd.lastModified = 0;
                    dfd.type = DirectoryFileData.FileType.FILE;
                    fileList.add(dfd);
                }
                return fileList.toArray(new DirectoryFileData[fileList.size()]);
            } else {
                throw new NotDirectoryException(name);
            }
        }
        throw new NotDirectoryException(name);
    }

    @Override
    public boolean createFileAtomically(String name) throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean createDirectory(String name) throws FileAlreadyExistsException, RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean createDirectories(String name) throws FileAlreadyExistsException, RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String name) throws NoSuchFileException, DirectoryNotEmptyException, RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copy(String name, String pathNameTo, CopyOption... options) throws FileAlreadyExistsException, RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void move(String name, String pathNameTo, CopyOption... options) throws FileAlreadyExistsException, RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public RemoteFileAccessor getRemoteFileAccessor(int pid, String name, Set<? extends OpenOption> options) throws FileAlreadyExistsException, NoSuchFileException, RemoteException {
        if (!name.startsWith("/")) {
            throw new RemoteException("Invalid file name: "+name);
        }

        name = name.trim().substring(1);

        if (name.isEmpty()) {
            throw new NoSuchFileException(name);
        }

        ProcFNSParts parts = parseProcFNSParts(name);

        ProcessManagerServer.Proc proc = processMap.get(parts.pid);
        if (proc == null) {
            throw new NoSuchFileException(name);
        }

        if (parts.component == null) {
            throw new NoSuchFileException(name);
        }

        if (parts.component == ComponentEnum.FILES) {

            if (parts.operand == null) {
                throw new NoSuchFileException(name);
            } else {
                throw new NoSuchFileException(name);
            }
        } else if (parts.component == ComponentEnum.CMDLINE) {
            if (parts.operand == null) {
                StringBuilder pdb = new StringBuilder();
                pdb.append(proc.cmd);
                for (String arg : proc.args) {
                    pdb.append(" " + arg);
                }
                pdb.append("\n");
                return new StringRemoteFileAccessor(pdb.toString());
            }
        } else {
            throw new NoSuchFileException(name);
        }

        throw new NoSuchFileException(name);
        /*
        StringBuilder pdb = new StringBuilder();
        pdb.append("PID="+Integer.toString(pidFile)+"\n");
        pdb.append("PPID="+proc.parentId+"\n");
        pdb.append("StartTime="+String.format("%1$tb %1$td %1$tY  %1$tH:%1$tM:%1$tS\n",proc.startTime));
        pdb.append("Command="+proc.cmd+"\n");
        return new StringRemoteFileAccessor(pdb.toString());
        */
    }

    @Override
    public Object getKey(String name) throws RemoteException {
        return null;
    }

    @Override
    public List<FileAccessorStatistics> getOpenFiles(int pid) throws RemoteException {
        return null;
    }

    // End of FileNameSpace interface implementation

    long getStartUpTime() {
        return startUpTime;
    }

    /**
     * Break a FileNameSpace name into parts and parse into the structure of the proc FileNameSpace
     *
     * @param name
     * @return
     * @throws NoSuchFileException
     */
    ProcFNSParts parseProcFNSParts(String name) throws NoSuchFileException {
        String[] names = name.split("/");

        ProcFNSParts rtrn = new ProcFNSParts();

        if (names.length > 0) {
            try {
                rtrn.pid = Integer.parseInt(names[0]);
            } catch (NumberFormatException e) {
                throw new NoSuchFileException(name);
            }
        }
        if (names.length > 1) {
            if (names[1].equals("files")) {
                rtrn.component = ComponentEnum.FILES;
            } else if (names[1].equals("cmdline")) {
                rtrn.component = ComponentEnum.CMDLINE;
            } else {
                throw new NoSuchFileException(name);
            }
        }
        if (names.length > 2) {
            throw new NoSuchFileException(name);
        }

        return rtrn;
    }

    private static class ProcFNSParts {
        int pid;
        ComponentEnum component;
        String operand;
    }

    private enum ComponentEnum {
        CMDLINE, CWD, EXE, FILES, FILEINFO
    }
    /**
     * Shutdown the ProcessManager. To cleanly shutdown, first shutdown all process groups by identifying
     * processes with parentId 0. Shutdown the groups from process tree leaf back to root (recursive).
     * Next showdown all translators by identifying processes with parentId -1.
     */
    void shutdown() {
        state = State.STOPPING;
        List<Integer> processGroupList = new LinkedList<Integer>();
        List<Integer> translatorList = new LinkedList<Integer>();
        for (Map.Entry<Integer, Proc> procMapEntry : processMap.entrySet()) {
            if (procMapEntry.getValue().parentId == 0) {
                processGroupList.add(procMapEntry.getValue().id);
            }
            if (procMapEntry.getValue().parentId == -1) {
                translatorList.add(procMapEntry.getValue().id);
            }
        }

        for (Integer id : processGroupList) {
            killProcessGroup(id);
        }

        for (Integer id : translatorList) {
            killProcessGroup(id);
        }


        state = State.SHUTDOWN;
    }

    private void killProcessGroup(int id) {
        List<Integer> childList = new LinkedList<Integer>();
        for (Map.Entry<Integer, Proc> procMapEntry : processMap.entrySet()) {
            if (procMapEntry.getValue().parentId == id) {
                childList.add(procMapEntry.getValue().id);
            }
        }

        for (Integer child : childList) {
            killProcessGroup(child);
        }

        final Semaphore waitObj = new Semaphore(0);

        registerEventNotificationHandler(id, EventName.DEREGISTER, new EventNotificationHandler() {
            @Override
            public void handleEventNotification(EventName event, Object eventData) throws RemoteException {
                if (event == EventName.DEREGISTER) {
                    waitObj.release();
                }
            }
        });

        logger.info("Shuting down process: " + id);
        sendSignal(id, Signal.TERMINATE); // TODO possible that the process is no longer running.

        try {
            waitObj.acquire();
        } catch (InterruptedException e) {
            // Should not happen
        }

        logger.info("Process shut down: " + id);
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
    }

    private static class ChildWaitObject {
        int waiterCount; // the number of waiters for this child event
        Deque<Proc> processList = new LinkedList<>(); // a list of processes that have triggered a child Event
    }

    private static enum State {
        RUNNING,
        STOPPING,
        SHUTDOWN
    }
}
