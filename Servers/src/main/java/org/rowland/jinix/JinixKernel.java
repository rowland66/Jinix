package org.rowland.jinix;

import org.rowland.jinix.exec.ExecServer;
import org.rowland.jinix.logger.LogServer;
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
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * The three core server that constitute the Jinix kernel. The server are NameSpace, ProcessManager
 * and Exec. These servers are started together since nothing can happen without them. Other
 * Jinix servers are implemented as translators.
 */
public class JinixKernel {

    protected enum RMI_MODE {Default, AFUNIX};

    protected static RMI_MODE rmiMode = RMI_MODE.Default;
    static boolean consoleLogging = false;

    private static Thread mainThread;
    private static Registry registry;
    private static NameSpaceServer fs;
    private static LogServerServer ls;
    private static ProcessManagerServer pm;
    private static ExecServerServer es;
    private static boolean shutdown = false;

    private static Logger logger = Logger.getLogger("jinix");

    public static void main(String[] args) {
        try {
            setupLogging(args);

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

            logger.info("NameSpace: Started");

            ls = new LogServerServer();
            fs.bind(LogServer.SERVER_NAME, ls);

            logger.info("LogServer: Started and bound to root namespace at " + LogServer.SERVER_NAME);

            pm = new ProcessManagerServer();
            fs.bind(ProcessManager.SERVER_NAME, pm);

            logger.info("ProcessManager: Started and bound to root namespace at " + ProcessManager.SERVER_NAME);

            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome != null) {
                logger.info("ExecServer: Executing java from: "+javaHome);
            }

            es = new ExecServerServer(fs, javaHome);
            fs.bind(ExecServer.SERVER_NAME, es);

            logger.info("ExecServer: Started and bound to root namespace at " + ExecServer.SERVER_NAME);

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
                logger.info("Jinix Kernel Halted");
                System.exit(0);
            }

        } catch (ConnectException e) {
            logger.severe("NameSpace: Failed to bind to RMI Registry.");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    static void shutdown() {
        pm.shutdown();
        try {
            fs.unbind(ExecServer.SERVER_NAME);
            fs.unbind(ProcessManager.SERVER_NAME);
            fs.unbind(LogServer.SERVER_NAME);
            es.unexport();
            pm.unexport();
            ls.unexport();
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

    private static void setupLogging(String[] args) {

        Logger rootLogger = LogManager.getLogManager().getLogger("");

        for (int i =0; i<args.length; i++) {
            if (args[i].equals("-i") || args[i].equals("--interactive")) {
                rootLogger.getHandlers()[0].setFormatter(new BriefKernelLogFormatter());
                consoleLogging = true;
                return; // Leave the defaule ConsoleHandler in place and log to the console
            }
        }

        //Clear all existing handlers
        Handler[] oldHandler = rootLogger.getHandlers();
        for (Handler h : oldHandler) {
            rootLogger.removeHandler(h);
        }

        Handler memHandler = LogServerServer.kernelLoggingHandler;
        rootLogger.addHandler(memHandler);
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
                logger.info("Initialized "+serverName+" for RMI over AFUNIX sockets");
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
