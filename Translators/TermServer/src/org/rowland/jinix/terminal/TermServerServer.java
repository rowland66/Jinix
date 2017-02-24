package org.rowland.jinix.terminal;

import org.rowland.jinix.JinixKernelUnicastRemoteObject;
import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.ProcessSignalHandler;
import org.rowland.jinix.naming.FileChannel;
import org.rowland.jinix.naming.NameSpace;
import org.rowland.jinix.proc.EventNotificationHandler;
import org.rowland.jinix.proc.ProcessManager;
import org.rowland.jinix.proc.DeregisterEventData;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.Map;

/**
 * Created by rsmith on 11/28/2016.
 */
class TermServerServer extends JinixKernelUnicastRemoteObject implements TermServer {

    private static TermServerServer server;
    private static Thread mainThread;

    Terminal[] term;
    NameSpace rootNamespace;
    ProcessManager processManager;

    TermServerServer() throws RemoteException {

        super();
        rootNamespace = JinixRuntime.getRuntime().getRootNamespace();

        while (true) {
            try {
                processManager = (ProcessManager) rootNamespace.lookup(ProcessManager.SERVER_NAME).remote;
            } catch (RemoteException e) {
                System.err.println("Term: Failed to locate process manager at " + ProcessManager.SERVER_NAME);
            }
            if (processManager == null) {
                try {
                    Thread.sleep(500);
                    continue;
                } catch (InterruptedException e) {
                    System.exit(0);
                }
            }
            break;
        }

        term = new Terminal[256];
    }

    @Override
    public short createTerminal() throws RemoteException {
        return createTerminal(Collections.emptyMap());
    }

    @Override
    public short createTerminal(Map<PtyMode, Integer> ttyOptions) throws RemoteException {
        for (short i = 0; i < 255; i++) {
            if(term[i]==null) {
                term[i] = new Terminal(i, ttyOptions);
                return i;
            }
        }

        throw new RuntimeException("TermServer: Unable to allocate a new Terminal");
    }

    @Override
    public void linkProcessToTerminal(short termId, int pid) throws RemoteException {

        if (term[termId] == null) throw new RuntimeException("TermServer: Attempt to link a process to an unallocated terminal");
        term[termId].setLinkedProcess(pid);

        processManager.registerEventNotificationHandler(pid, ProcessManager.EventName.DEREGISTER,
                new DeRegisterEventNotificationHandler());
    }

    @Override
    public FileChannel getTerminalMaster(short termId) throws RemoteException {
        return term[termId].getMasterTerminalFileDescriptor();
    }

    @Override
    public FileChannel getTerminalSlave(short termId) throws RemoteException {
        return term[termId].getSlaveTerminalFileDescriptor();
    }

    private void shutdown() {
        for (short i = 0; i < 255; i++) {
            if(term[i] != null) {
                term[i].close();
            }
        }
        unexport();
    }

    public class DeRegisterEventNotificationHandler extends UnicastRemoteObject implements EventNotificationHandler {

        private DeRegisterEventNotificationHandler() throws RemoteException {
            super(0, RMISocketFactory.getSocketFactory(), RMISocketFactory.getSocketFactory());
        }

        @Override
        public void handleEventNotification(ProcessManager.EventName event, Object eventData) throws RemoteException {

            if (!event.equals(ProcessManager.EventName.DEREGISTER)) {
                return; // This should never happen as we have only registered for DEREGISTER events
            }
            for(int i=0; i<term.length; i++) {
                Terminal t = term[i];
                if(t != null && t.getLinkedProcess() == ((DeregisterEventData) eventData).pid) {
                    term[i].close();
                    term[i] = null;
                }
            }
        }
    }

    public static void main(String[] args) {

            JinixFileDescriptor fd = JinixRuntime.getRuntime().getTranslatorFile();

            if (fd == null) {
                System.err.println("Translator must be started with settrans");
                return;
            }
            try {
                server = new TermServerServer();
            } catch (RemoteException e) {
                throw new RuntimeException("Translator failed initialization",e);
            }

            JinixRuntime.getRuntime().bindTranslator(server);

            mainThread = Thread.currentThread();

            JinixRuntime.getRuntime().registerSignalHandler(new ProcessSignalHandler() {
                @Override
                public void handleSignal(ProcessManager.Signal signal) {
                    if (signal == ProcessManager.Signal.TERMINATE) {
                        mainThread.interrupt();
                    }
                }
            });

            try {
                Thread.sleep(Integer.MAX_VALUE);
            } catch (InterruptedException e) {
                // Interrupted shutting down
            }

            server.shutdown();

            System.out.println("TermServer shutdown complete");
    }
}
