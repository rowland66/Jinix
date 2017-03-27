package org.rowland.jinix;

import org.rowland.jinix.naming.*;
import org.rowland.jinix.proc.DeregisterEventData;
import org.rowland.jinix.proc.EventNotificationHandler;
import org.rowland.jinix.proc.ProcessManager;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.rmi.Remote;
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

    static Logger logger = Logger.getLogger(SERVER_LOGGER);

    private State state;
    private NameSpace ns;
    private ProcessManager p;
    private AtomicInteger nextId = new AtomicInteger(1); // Ever increasing counter for the next process id

    Map<Integer, Proc> processMap; // Map from process id to Proc objects maintaned for each running process
    long startUpTime;

    ProcessManagerServer() throws RemoteException {
        super();
        processMap = new HashMap<Integer, Proc>();
        startUpTime = System.currentTimeMillis();
        state = State.RUNNING;
    }

    /**
     * Create and register a new ProcessManager process. This method is called by an Executor.
     *
     * @param parentId process id of the processes parent. Translators have parentId -1, session
     *                 group leaders and daemons have parent id 0
     * @param cmd
     * @param args
     * @return
     * @throws RemoteException
     */
    @Override
    public int registerProcess(int parentId, String cmd, String[] args)
            throws RemoteException {

        if (state == State.STOPPING || state == State.SHUTDOWN) {

        }
        Proc p = new Proc();
        p.id = nextId.incrementAndGet();
        p.parentId = parentId;
        p.cmd = cmd;
        p.args = args;
        p.startTime = System.currentTimeMillis();
        p.isZombie = false;

        // Processes with parentId == 0 are group leaders and have no parent.
        if (parentId > 0) {
            Proc parent = processMap.get(parentId);

            synchronized (parent.children) {
                parent.children.add(p);
            }
        }

        p.children = new LinkedList<Proc>();

        p.eventWaiters = new HashMap<EventName, Object>(16);
        p.eventHandlers = new HashMap<EventName, List<EventNotificationHandler>>(16);

        p.pendingSignals = new LinkedList<Signal>();

        synchronized (processMap) {
            processMap.put(Integer.valueOf(p.id), p);
        }

        return p.id;
    }

    /**
     * Deregister a process that has previously been registered with the ProcessManager. When a process is deregistered,
     * any process waiting on the EventName.CHILD event will be notified. If there is no process waiting on the
     * EventName.CHILD event, then the Proc object of the process remains registered as a zombie until some process shows
     * an interest in the EventName.CHILD event. Generate EventName.DEREGISTER events for any processes that have registered
     * to receive them.
     *
     * @param id
     */
    public synchronized void deRegisterProcess(int id) {
        Proc p = processMap.get(id);

        if (p == null) {
            throw new IllegalArgumentException("Call to deRegisterProcess with unknown pid: "+id);
        }

        // A process has called waitForChild() and one of its children is a zombie. Cleanup the processMap and return.
        if (p.isZombie) {
            synchronized (processMap) {
                processMap.remove(id);
                Proc parent = processMap.get(p.parentId);
                parent.children.remove(p);
                return;
            }
        }

        if (p.eventHandlers.containsKey(EventName.DEREGISTER)) {
            List<EventNotificationHandler> handlerList = p.eventHandlers.get(EventName.DEREGISTER);
            for (EventNotificationHandler handler : handlerList) {
                DeregisterEventData data = new DeregisterEventData();
                data.pid = p.id;
                try {
                    handler.handleEventNotification(EventName.DEREGISTER, data);
                } catch (RemoteException e) {
                    throw new RuntimeException("Process Manager: Failure delivering DEREGISTER event notification for " + p.id, e);
                }
            }
        }

        if (p.parentId <= 0) {
            synchronized (processMap) {
                processMap.remove(id);
                return;
            }
        }

        Proc parent = processMap.get(p.parentId);
        ChildWaitObject childWaitObject;
        synchronized (parent.eventWaiters) {
            childWaitObject = (ChildWaitObject) parent.eventWaiters.get(EventName.CHILD);
            if (childWaitObject == null) {
                childWaitObject = new ChildWaitObject();
                parent.eventWaiters.put(EventName.CHILD, childWaitObject);
            }
        }

        synchronized (childWaitObject) {
            if (childWaitObject.waiterCount == 0) {
                p.isZombie = true;
                sendSignal(p.parentId, Signal.CHILD);
                return;
            }

            childWaitObject.notifyAll();
        }

        // Any children of the process being deregistered get their parentId set to 0. They are daemons.
        if (!p.children.isEmpty()) {
            for (Proc child : p.children) {
                child.parentId = 0;
            }
        }

        synchronized (processMap) {
            processMap.remove(id);
            parent.children.remove(p);
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
    public int[] listProcessIDs() throws RemoteException {
        int[] rtrn = new int[processMap.size()];
        int cnt = 0;
        Iterator<Map.Entry<Integer,Proc>> i = processMap.entrySet().iterator();
        while (i.hasNext()) {
            rtrn[cnt++] = i.next().getValue().id;
        }
        return rtrn;
    }

    /**
     * Wait for a child process of the pid to terminate.
     *
     * @param pid
     * @return
     * @throws RemoteException
     */
    @Override
    public int waitForChild(int pid) throws RemoteException {
        Proc p = processMap.get(pid);

        if (p == null) throw new IllegalArgumentException("ProcessManager: Unknown pid: "+pid);

        synchronized (p) {
            if (p.children.isEmpty()) {
                throw new IllegalStateException("Process Manager: Call to waitForChild() with no child processes: "+pid);
            }

            for (Proc child : p.children) {
                if (child.isZombie) {
                    deRegisterProcess(child.id);
                    return child.id;
                }
                break;
            }

            ChildWaitObject childWaitObject = (ChildWaitObject) p.eventWaiters.get(EventName.CHILD);
            if (childWaitObject == null) {
                childWaitObject = new ChildWaitObject();
                p.eventWaiters.put(EventName.CHILD, childWaitObject);
            }

            synchronized (childWaitObject) {
                try {
                    childWaitObject.waiterCount++;
                    childWaitObject.wait();
                } catch (InterruptedException e) {
                    throw new RemoteException("Aborted wait for child process(es) to complete.");
                }

                int rtrnVal = childWaitObject.pid;
                childWaitObject.pid = 0;
                childWaitObject.waiterCount--;

                return rtrnVal;
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
    public void registerEventNotificationHandler(int pid, EventName eventName, EventNotificationHandler handler) throws RemoteException {

        Proc p = processMap.get(pid);

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
    public Signal listenForSignal(int pid) throws RemoteException {
        Proc p = processMap.get(pid);

        if (p == null) {
            return null;
        }

        synchronized (p.pendingSignals) {
            while (p.pendingSignals.isEmpty()) {
                try {
                    p.pendingSignals.wait();
                } catch (InterruptedException e) {
                    return null;
                }
            }
            return p.pendingSignals.removeFirst();
        }
    }

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

        if (name.indexOf('/') > -1) {
            throw new NoSuchFileException(name);
        }

        int pid;
        try {
            pid = Integer.parseInt(name);
        } catch (NumberFormatException e) {
            throw new NoSuchFileException(name);
        }

        ProcessManagerServer.Proc proc = processMap.get(pid);
        if (proc == null) {
            throw new NoSuchFileException(name);
        }

        DirectoryFileData rtrn = new DirectoryFileData();
        rtrn.lastModified = proc.startTime;
        rtrn.length = 0;
        rtrn.name=Integer.toString(pid);
        rtrn.type= DirectoryFileData.FileType.FILE;
        return rtrn;
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
        if (!name.equals("/")) {
            throw new NotDirectoryException(name);
        }
        String[] rtrn = new String[processMap.size()];
        int i=0;
        for(Integer pid : processMap.keySet()) {
            rtrn[i++] = pid.toString();
        }
        return rtrn;
    }

    @Override
    public DirectoryFileData[] listDirectoryFileData(String name) throws NotDirectoryException, RemoteException {
        if (!name.equals("/")) {
            throw new NotDirectoryException(name);
        }
        DirectoryFileData[] rtrn = new DirectoryFileData[processMap.size()];
        int i=0;
        for(Integer pid : processMap.keySet()) {
            DirectoryFileData dfd = new DirectoryFileData();
            dfd.name = pid.toString();
            dfd.length = 0;
            dfd.lastModified = processMap.get(pid).startTime;
            dfd.type = DirectoryFileData.FileType.DIRECTORY;
            rtrn[i++] = dfd;
        }
        return rtrn;
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
    public FileChannel getFileChannel(String name, OpenOption... options) throws FileAlreadyExistsException, NoSuchFileException, RemoteException {
        if (!name.startsWith("/")) {
            throw new RemoteException("Invalid file name: "+name);
        }

        name = name.trim().substring(1);

        if (name.isEmpty()) {
            throw new NoSuchFileException(name);
        }

        if (name.indexOf('/') > -1) {
            throw new NoSuchFileException(name);
        }

        int pid;
        try {
            pid = Integer.parseInt(name);
        } catch (NumberFormatException e) {
            throw new NoSuchFileException(name);
        }

        ProcessManagerServer.Proc proc = processMap.get(pid);
        if (proc == null) {
            throw new NoSuchFileException(name);
        }
        StringBuilder pdb = new StringBuilder();
        pdb.append("PID="+Integer.toString(pid)+"\n");
        pdb.append("PPID="+proc.parentId+"\n");
        pdb.append("StartTime="+String.format("%1$tb %1$td %1$tY  %1$tH:%1$tM:%1$tS\n",proc.startTime));
        pdb.append("Command="+proc.cmd+"\n");
        return new StringFileChannel(pdb.toString());
    }

    @Override
    public Object getKey(String name) throws RemoteException {
        return null;
    }

    long getStartUpTime() {
        return startUpTime;
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
        try {
            registerEventNotificationHandler(id, EventName.DEREGISTER, new EventNotificationHandler() {
                @Override
                public void handleEventNotification(EventName event, Object eventData) throws RemoteException {
                    if (event == EventName.DEREGISTER) {
                        waitObj.release();
                    }
                }
            });
        } catch (RemoteException e) {
            // will not happen as not remote call.
        }

        sendSignal(id, Signal.TERMINATE); // TODO possible that the process is no longer running.

        try {
            waitObj.acquire();
        } catch (InterruptedException e) {
            // Should not happen
        }
    }

    static class Proc {
        int id;
        int parentId;
        String cmd;
        String[] args;
        long startTime;
        Process osProcess;
        boolean isZombie;
        List<Proc> children;
        Map<EventName, Object> eventWaiters;
        Map<EventName, List<EventNotificationHandler>> eventHandlers;
        Deque<Signal> pendingSignals;
    }

    private static class ChildWaitObject {
        int waiterCount; // the number of waiters for this child event
        int pid; // set to the pid of the child that triggered this event. The first waiter to lock this object must
                 // set the value back to 0
    }

    private static enum State {
        RUNNING,
        STOPPING,
        SHUTDOWN
    }
}
