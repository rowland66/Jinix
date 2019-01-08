package org.rowland.jinix.init;

import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.io.JinixFileInputStream;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.JinixSystem;
import org.rowland.jinix.lang.ProcessSignalHandler;
import org.rowland.jinix.proc.ProcessData;
import org.rowland.jinix.proc.ProcessManager;

import javax.naming.Context;
import javax.naming.NamingException;
import java.io.*;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Properties;

/**
 * The first(and only) Jinix executable launched by the Jinix kernel after starting the core kernel servers. Init reads
 * a simple command file (/config/init.config) and creates a process to run each command.
 */
public class Init {
    private static ProcessManager pm;
    private static Thread mainThread;

    public static void main(String[] args) {

        System.out.println("Init: Starting");

        mainThread = Thread.currentThread();

        try {
            Context ns = JinixRuntime.getRuntime().getNamingContext();
            pm = (ProcessManager) ns.lookup(ProcessManager.SERVER_NAME);

            JinixSystem.setJinixProperty(JinixRuntime.JINIX_PATH, "/bin"); // /usr/local/sbin:/sbin:/bin:/usr/sbin:/usr/bin
            JinixSystem.setJinixProperty(JinixRuntime.JINIX_LIBRARY_PATH, "/lib");
            JinixSystem.setJinixProperty(JinixRuntime.JINIX_PATH_EXT, "jar");

            JinixFile f = new JinixFile("/config/init.config");
            try (BufferedReader initTabFileReader = new BufferedReader(new InputStreamReader(new JinixFileInputStream(f)))) {
                String initLine = initTabFileReader.readLine();
                while (initLine != null) {
                    initLine = initLine.trim();
                    if (!initLine.isEmpty() && !initLine.startsWith("#")) {
                        executeInitLine(initLine);
                    }
                    initLine = initTabFileReader.readLine();
                }
            }
        } catch (NamingException e) {
            System.err.println("Init: Unable to find Process Manager at "+ProcessManager.SERVER_NAME);
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println("Init: Unable to find inittab file at /config/init.config");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Init: IO Exception reading /config/inittab");
            System.exit(1);
        }

        JinixRuntime.getRuntime().registerSignalHandler(new ProcessSignalHandler() {
            @Override
            public boolean handleSignal(ProcessManager.Signal signal) {
                if (signal.equals(ProcessManager.Signal.TERMINATE)) {
                    mainThread.interrupt();
                }
                return true;
            }
        });

        System.out.println("Init: Complete");

        try {
            Thread.currentThread().sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("Init: Main thread interuppted. Shutting down.");
        }

        // Begin Shutdown

        try {
            JinixRuntime runtime = JinixRuntime.getRuntime();
            ProcessData[] processData = pm.getProcessDataByProcessGroup();

            while (true) {
                boolean processFound = false;

                int lastProcessGroupId = -1;
                for (ProcessData p : processData) {
                    if (p.terminalId != -1 && p.processGroupId != lastProcessGroupId) {
                        System.out.println("Init: Shutting down process group: " + p.id + ":" +p.cmd);
                        runtime.sendSignalProcessGroup(p.processGroupId, ProcessManager.Signal.KILL);
                        processFound = true;
                        lastProcessGroupId = p.processGroupId;
                    }
                }

                // If not process belonging to a terminal
                if (!processFound) {
                    break;
                }

                // Give the signaled processes time to die.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Retrieve the process data again
                processData = pm.getProcessDataByProcessGroup();
            }

            while (true) {
                boolean processFound = false;

                int lastProcessGroupId = -1;
                for (ProcessData p : processData) {
                    if (p.parentId == 1 && p.sessionId != 1 && p.processGroupId != lastProcessGroupId) { // Terminate all process groups that are not translators
                        System.out.println("Init: Shutting down deamon process group: " + p.id + ":" +p.cmd);
                        runtime.sendSignalProcessGroup(p.processGroupId, ProcessManager.Signal.KILL);
                        processFound = true;
                        lastProcessGroupId = p.processGroupId;
                    }
                }

                // If not process belonging to a terminal
                if (!processFound) {
                    break;
                }

                // Give the signaled processes time to die.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Retrieve the process data again
                processData = pm.getProcessDataByProcessGroup();
            }

            while (true) {
                boolean processFound = false;

                int lastProcessGroupId = -1;
                for (ProcessData p : processData) {
                    if (p.parentId == 1 && p.sessionId == 1 && p.processGroupId != lastProcessGroupId) { // Terminate all process groups that are translators
                        System.out.println("Init: Shutting down translator process: "+p.id + ":" + p.cmd);
                        runtime.sendSignalProcessGroup(p.processGroupId, ProcessManager.Signal.KILL);
                        processFound = true;
                        lastProcessGroupId = p.processGroupId;
                    }
                }

                // If not process belonging to a terminal
                if (!processFound) {
                    break;
                }

                // Give the signaled processes time to die.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Retrieve the process data again
                processData = pm.getProcessDataByProcessGroup();
            }

        } catch (RemoteException e) {
            System.err.println("Init: Error getting process data from the process manager");
            if (e.getCause() != null) {
                System.err.println(e.getCause().getMessage());
                e.getCause().printStackTrace(System.err);
            }
        } finally {
            // Return false so that ExecLauncher will terminate our process
            System.out.println("Init: Shutdown complete.");
        }
    }

    private static void executeInitLine(String initLine) {

        String[] cmd = initLine.split(" ");
        String[] args = new String[cmd.length-1];
        for (int i=1; i<cmd.length; i++) {
            args[i-1] = cmd[i];
        }
        try {
            JinixRuntime.getRuntime().exec(cmd[0], args);
        } catch (FileNotFoundException e) {
            System.err.println("Init: Unable to find file: " + cmd[0]);
        } catch (org.rowland.jinix.exec.InvalidExecutableException e) {
            System.err.println("Init: Invalid executable file: " + cmd[0]);
        }
    }
}
