package org.rowland.jinix;

import org.rowland.jinix.exec.ExecServer;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.proc.ProcessManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;

/**
 * The three core server that constitute the Jinix kernel. The server are NameSpace, ProcessManager
 * and Exec. These servers are started together since nothing can happen without them. Other
 * Jinix servers are implemented as translators.
 */
public class JinixKernel {

    protected enum RMI_MODE {Default, AFUNIX};

    protected static RMI_MODE rmiMode = RMI_MODE.Default;

    private static Thread mainThread;
    private static Registry registry;
    private static NameSpaceServer fs;
    private static ProcessManagerServer pm;
    private static ExecServerServer es;
    private static boolean shutdown = false;

    public static void main(String[] args) {
        try {
            setupRMI("JinixKernel", args);

            if (RMISocketFactory.getSocketFactory() != null &&
                    RMISocketFactory.getSocketFactory().getClass().getName().
                            equals("org.newsclub.net.unix.rmi.AFUNIXRMISocketFactory")) {
                registry = LocateRegistry.createRegistry(100001,
                        RMISocketFactory.getSocketFactory(), RMISocketFactory.getSocketFactory());
            } else {
                registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
            }

            FileNameSpace rootFileSystem = getRootFileSystem(new String[0]);

            fs = new NameSpaceServer(rootFileSystem);

            registry.rebind("root", fs);

            System.out.println("NameSpace: Started");

            pm = new ProcessManagerServer();
            fs.bind(ProcessManager.SERVER_NAME, pm);

            System.out.println("ProcessManager: Started and bound to root namespace at " + ProcessManager.SERVER_NAME);

            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome != null) {
                System.out.println("ExecServer: Executing java from: "+javaHome);
            }

            es = new ExecServerServer(fs, javaHome);
            fs.bind(ExecServer.SERVER_NAME, es);

            System.out.println("ExecServer: Started and bound to root namespace at " + ExecServer.SERVER_NAME);

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    if (!shutdown) {
                        shutdown();
                    }
                }
            }));

            mainThread = Thread.currentThread();

            try {
                Thread.sleep(Integer.MAX_VALUE);
            } catch (InterruptedException e) {
                System.out.println("Jinix Kernel Halted");
                System.exit(0);
            }

        } catch (ConnectException e) {
            System.err.println("NameSpace: Failed to bind to RMI Registry.");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    static void shutdown() {
        pm.shutdown();
        try {

            fs.unbind(ExecServer.SERVER_NAME);
            fs.unbind(ProcessManager.SERVER_NAME);
            pm.unexport();
            es.unexport();
            registry.unbind("root");
            fs.unexport();
            UnicastRemoteObject.unexportObject(registry, true);
            shutdown = true;
            JinixKernelUnicastRemoteObject.dumpExportedObjects(System.out);
            mainThread.interrupt();

        } catch (NoSuchObjectException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    protected static void setupRMI(String serverName, String[] args) {

        String rmiModeStr = "Default";
        for (int i =0; i<args.length; i++) {
            if (args[i].startsWith("rmi=")) {
                rmiModeStr = args[i].substring("rmi=".length());
            }
        }

        if (rmiModeStr != null) {
            if (rmiModeStr.equals("AFUNIX")) {
                rmiMode = RMI_MODE.AFUNIX;
                System.setProperty("AFUNIXRMISocketFactory.server.name", serverName);
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
                System.out.println("Initialized "+serverName+" for RMI over AFUNIX sockets");
            }
        }
    }

    private static FileNameSpace getRootFileSystem(String[] args) {

        try {
            Class rootFileSystemClass = Class.forName("org.rowland.jinix.nativefilesystem.FileSystemServer");
            Method initMethod = rootFileSystemClass.getMethod("runAsRootFileSystem", args.getClass());
            Object[] invokeArgs = new Object[1];
            invokeArgs[0] = args;
            return (FileNameSpace) initMethod.invoke(null, invokeArgs);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load the root file system class");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("The root file system class is invalid: No such method: runAsRootFileSystem");
        } catch (IllegalAccessException e) {
            throw new RuntimeException("The root file system class is invalid: Illegal Access: runAsRootFileSystem", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Failure initializing the root file system", e.getCause());
        } catch (ClassCastException e) {
            throw new RuntimeException("The root file system class is invalid: runAsRootFileSystem does not return a FileNameSpace");
        }
    }
}
