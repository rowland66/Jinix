package org.rowland.jinix;

import org.rowland.jinix.exec.ExecServer;
import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.io.JinixFileOutputStream;
import org.rowland.jinix.naming.FileChannel;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.io.JinixFileInputStream;
import org.rowland.jinix.naming.NameSpace;
import org.rowland.jinix.terminal.TermServer;

import java.io.*;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.util.HashMap;

/**
 * A simple Console for Jinix. The Console access the TerminalServer to create a terminal, and then accesses
 * the ExecServer to start the Jinix shell (Jsh) on the terminal. The Console creates two threads, one to read
 * terminal output and display it on the Console's System.out, and another to read data from the Console's
 * System.in and send it to the terminal input.
 */
public class Console {

    public static void main(String[] args) {

        try {
            setupRMI(args);
            Registry registry = getRegistry();
            NameSpace fs = (NameSpace) registry.lookup("root");

            TermServer t = (TermServer) fs.lookup(TermServer.SERVER_NAME).remote;

            short terminalId = t.createTerminal();
            JinixFileDescriptor masterFileDescriptor = new JinixFileDescriptor(t.getTerminalMaster(terminalId));
            JinixFileDescriptor slaveFileDescriptor = new JinixFileDescriptor(t.getTerminalSlave(terminalId));

            ExecServer es = (ExecServer) fs.lookup(ExecServer.SERVER_NAME).remote;

            //"-Xdebug","-Xrunjdwp:transport=dt_socket,address=5555,server=y,suspend=y",
            try {
                slaveFileDescriptor.getHandle().duplicate(); // We need to dup the slave FileChannel twice because we are
                slaveFileDescriptor.getHandle().duplicate(); // passing 3 slave FileChannels, and ExecLauncher will close all three
                int pid = es.exec(null, "/bin/jsh.jar", new String[]{"/home"}, 0,
                        slaveFileDescriptor.getHandle(), slaveFileDescriptor.getHandle(), slaveFileDescriptor.getHandle());
                t.linkProcessToTerminal(terminalId, pid);
            } catch (FileNotFoundException | InvalidExecutableException e) {
                throw new RuntimeException(e);
            }

            // This is confusing. The inputstream is the output from the exec'd process, and the output stream
            // is the input.
            OutputThread outputThread = null;
            try {
                InputStream is = new BufferedInputStream(new JinixFileInputStream(masterFileDescriptor));
                OutputStream os = new JinixFileOutputStream(masterFileDescriptor);

                outputThread = new OutputThread(is);
                InputThread inputThread = new InputThread(os);

                outputThread.start();
                inputThread.start();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            try {
                outputThread.join();
            } catch (InterruptedException e) {
                System.out.println("Interrupted");
            }

            // Java no longer supports interuptable blocking I/O. (It was only supported on Solaris anyway)
            // Interrupting the inputThread does not work, so we just need to exit().
            //inputThread.interrupt();

            System.exit(0);

        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
    }

    protected static void setupRMI(String[] args) {

        String rmiMode = null;
        for (int i =0; i<args.length; i++) {
            if (args[i].startsWith("rmi=")) {
                rmiMode = args[i].substring("rmi=".length());
            }
        }

        if (rmiMode != null) {
            if (rmiMode.equals("AFUNIX")) {
                try {
                    Class socketFactoryClass = Class.forName("org.newsclub.net.unix.rmi.AFUNIXRMISocketFactory");
                    RMISocketFactory factory = (RMISocketFactory) socketFactoryClass.newInstance();
                    RMISocketFactory.setSocketFactory(factory);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Failed to setup AFUNIX socket factory: Class not found org.newsclub.net.unix.rmi.AFUNIXRMISocketFactory");
                } catch (ClassCastException e) {
                    throw new RuntimeException("Failed to setup AFUNIX socket factory: Class org.newsclub.net.unix.rmi.AFUNIXRMISocketFactory is not an RMISocketFactory");
                } catch (InstantiationException e) {
                    throw new RuntimeException("Failed to setup AFUNIX socket factory: Failed to intantiate instance of org.newsclub.net.unix.rmi.AFUNIXRMISocketFactory");
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to setup AFUNIX socket factory: Public constructor not available for org.newsclub.net.unix.rmi.AFUNIXRMISocketFactory");
                } catch (IOException e) {
                    throw new RuntimeException("IOException setting RMI socket factory", e);
                }
            }
        }
    }

    protected static Registry getRegistry() throws RemoteException {
        if (RMISocketFactory.getSocketFactory() != null &&
                RMISocketFactory.getSocketFactory().getClass().getName().
                equals("org.newsclub.net.unix.rmi.AFUNIXRMISocketFactory")) {
            return LocateRegistry.getRegistry("JinixKernel", 100001, RMISocketFactory.getSocketFactory());
        } else {
            return LocateRegistry.getRegistry(Registry.REGISTRY_PORT);
        }
    }

    private static class OutputThread extends Thread {

        private InputStream is;

        private OutputThread(InputStream input) {
            super("Output Thread");
            this.is = input;
        }

        @Override
        public void run() {
            try {
                byte[] b = new byte[128];
                int n;
                while ((n = is.read(b)) > 0) {
                    if (n == -1)
                        return;
                    for (int i=0; i<n; i++) System.out.print((char) b[i]);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class InputThread extends Thread {

        private OutputStream os;

        private InputThread(OutputStream output) {
            super("Input Thread");
            this.os = output;
        }

        @Override
        public void run() {
            try {
                int b;
                while ((b = System.in.read()) > 0) {
                    if (b != 0x0d) {
                        os.write(b);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}