package org.rowland.jinix;

import org.rowland.jinix.exec.ExecServer;
import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.logger.LogServer;
import org.rowland.jinix.naming.*;
import org.rowland.jinix.proc.ProcessManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.EnumSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * The three core server that constitute the Jinix kernel. The servers are NameSpace, ProcessManager
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

    private static Logger logger;

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

            pm = new ProcessManagerServer(fs);
            fs.bind(ProcessManager.SERVER_NAME, pm);

            logger.info("ProcessManager: Started and bound to root namespace at " + ProcessManager.SERVER_NAME);

            String javaHome = System.getenv("JINIX_JAVA_HOME");
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
                        shutdownFromShutdownHook();
                    }
                }
            }));

            mainThread = Thread.currentThread();

            RemoteFileAccessor initLogRaf = null;
            try {
                EnumSet<StandardOpenOption> openOptions = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                NameSpace remoteRootNameSpace = (NameSpace) getRegistry().lookup("root");
                Object lookup = remoteRootNameSpace.lookup("/var/log");
                if (!(lookup instanceof RemoteFileHandle)) {
                    throw new RuntimeException("Kernel: Found unexpected object at: /var/log");
                }
                initLogRaf = ((RemoteFileHandle) lookup).getParent().
                        getRemoteFileAccessor(-1, "/var/log/init.log", openOptions);
            } catch (FileAlreadyExistsException e) {
                // Should never happen as we are not using CREATE_NEW
            } catch (NoSuchFileException e) {
                // Should never happen as we are using CREATE
            } catch (NotBoundException e) {
                System.err.println("Kernel: Failed to find root namespace in RMI registry.");
                System.exit(0);
            }

            try {
                int initPid = es.exec(null, "/sbin/init.jar", new String[] {/**"-Xdebug","-Xrunjdwp:transport=dt_socket,address=5557,server=y,suspend=y"*/}, 0, -1, -1, initLogRaf, initLogRaf, initLogRaf);
            } catch (FileNotFoundException e) {
                System.err.println("Init executable not found at /sbin/init.jar");
                if (initLogRaf != null) {
                    initLogRaf.close();
                }
                System.exit(0);
            } catch (InvalidExecutableException e) {
                System.err.println("Invalid executable at /sbin/init.jar");
                if (initLogRaf != null) {
                    initLogRaf.close();
                }
                System.exit(0);
            }

            // No need to close raf since we pass it to init.

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

    /**
     * Get the root of the Jinix namespace. The root namespace is provided by the NameSpaceServer.
     *
     * @return the root NameSpace
     */
    static NameSpaceServer getNameSpaceRoot() {
        return fs;
    }

    /**
     * Shutdown the JinixKernel by interrupting the main thread. When the main thread is interrupted, it will exit the JVM
     * and trigger the JVM shutdown hook to clean up.
     */
    static void shutdown() {
        mainThread.interrupt();
    }

    static private void shutdownFromShutdownHook() {
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

            KernelRMIClassLoader.close();

            shutdown = true;
            JinixKernelUnicastRemoteObject.dumpExportedObjects(System.out);
        } catch (NoSuchObjectException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private static Registry getRegistry() throws RemoteException {
        if (RMISocketFactory.getSocketFactory() != null &&
                RMISocketFactory.getSocketFactory().getClass().getName().
                        equals("org.newsclub.net.unix.rmi.AFUNIXRMISocketFactory")) {
            return LocateRegistry.getRegistry("JinixKernel", 100001, RMISocketFactory.getSocketFactory());
        } else {
            return LocateRegistry.getRegistry(Registry.REGISTRY_PORT);
        }
    }

    private static void setupLogging(String[] args) {

        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.INFO);

        for (int i =0; i<args.length; i++) {
            if (args[i].equals("-i") || args[i].equals("--interactive")) {
                rootLogger.getHandlers()[0].setFormatter(new BriefKernelLogFormatter());
                rootLogger.getHandlers()[0].setLevel(Level.ALL);
                consoleLogging = true;
                break;
            }
        }

        if (!consoleLogging) {
            //Clear all existing handlers
            Handler[] oldHandler = rootLogger.getHandlers();
            for (Handler h : oldHandler) {
                rootLogger.removeHandler(h);
            }

            Handler memHandler = LogServerServer.kernelLoggingHandler;
            rootLogger.addHandler(memHandler);
        }

        logger = Logger.getLogger("jinix");
        for (int i =0; i<args.length; i++) {
            if (args[i].equals("-l") || args[i].equals("--logLevel")) {
                logger.setLevel(Level.parse(args[i+1]));
                break;
            }
        }
    }

    protected static void setupRMI(String serverName, String[] args) {

        // Setting useCodebaseOnly to false tells the object unmarshalling code to pass the codebase annotations that
        // are included in the input stream to the RMI
        System.setProperty("java.rmi.server.useCodebaseOnly", "false");
        System.setProperty("java.rmi.server.RMIClassLoaderSpi", KernelRMIClassLoader.class.getCanonicalName());

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
            Class<?> rootFileSystemClass = Class.forName("org.rowland.jinix.nativefilesystem.FileSystemServer");
            Method initMethod = rootFileSystemClass.getMethod("runAsRootFileSystem", args.getClass());
            Object[] invokeArgs = new Object[1];
            invokeArgs[0] = args;
            Object rtrn =initMethod.invoke(null, invokeArgs);

            if (!(rtrn instanceof FileNameSpace)) {
                throw new RuntimeException("The root file system class is invalid: runAsRootFileSystem does not return a FileNameSpace");
            }

            return (FileNameSpace) rtrn;
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
