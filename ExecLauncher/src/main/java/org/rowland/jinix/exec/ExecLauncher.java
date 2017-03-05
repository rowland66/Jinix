package org.rowland.jinix.exec;

import org.rowland.jinix.fifo.FifoServer;
import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.io.JinixFileInputStream;
import org.rowland.jinix.io.JinixFileOutputStream;
import org.rowland.jinix.io.JinixPipe;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.JinixSystem;
import org.rowland.jinix.lang.ProcessSignalHandler;
import org.rowland.jinix.naming.*;
import org.rowland.jinix.nio.JinixPath;
import org.rowland.jinix.proc.ProcessManager;

import javax.naming.Context;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.security.Policy;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.logging.*;

/**
 * ExecLauncher bootstraps every Jinix executable started by the ExecServer. The ExecLauncher calls back to the
 * ExecServer to get critical data required to execute the Jinix executable. The callback data include FileChannels
 * for stdin, stdout and stderr. The ExecLauncher creates an ExecClassLoader to load the Jinix executable jar. The
 * ExecLauncher installs a SecurityManager that enforces a Jinix security policy to restrict Jinix executables
 * access to the underlying OS.
 */
public class ExecLauncher {

    private static int pid;
    private static NameSpace rootNameSpace;
    private static ExecServer es;
    private static ProcessManager pm;
    private static JinixFileDescriptor stdIn;
    private static JinixFileDescriptor stdOut;
    private static JinixFileDescriptor stdErr;
    private static JinixFileDescriptor translatorNode;
    private static String translatorNodePath;
    private static ThreadGroup execThreadGroup;
    private static boolean nativeAccess = false;
    private static boolean consoleLogging = false;

    private static InputStream debugStdin;
    private static PrintStream debugStdOut;
    private static PrintStream debugStdErr;

    private static String execCmd;
    private static String[] execArgs;
    private static ProcessSignalHandler signalHandler;

    public static void launch(int pid) {

        try {
            Runtime.getRuntime().addShutdownHook(new ExecLauncherShutdownThread());

            JinixRuntime.setJinixRuntime(new JinixRuntimeImpl());

            ExecLauncher.pid = pid;

            ExecLauncherData execLaunchData = es.execLauncherCallback(pid);

            execCmd = execLaunchData.cmd;
            execArgs = execLaunchData.args;

            debugStdin = System.in;
            debugStdOut = System.out;
            debugStdErr = System.err;

            if (execLaunchData.translatorNodePath == null) {
                ExecLauncher.stdIn = new JinixFileDescriptor(execLaunchData.stdIn);
                ExecLauncher.stdOut = new JinixFileDescriptor(execLaunchData.stdOut);
                ExecLauncher.stdErr = new JinixFileDescriptor(execLaunchData.stdErr);

                System.setIn(new BufferedInputStream(new JinixFileInputStream(ExecLauncher.stdIn)));
                System.setOut(new PrintStream(new BufferedOutputStream(
                        new JinixFileOutputStream(ExecLauncher.stdOut)), true));
                System.setErr(new PrintStream(new BufferedOutputStream(
                        new JinixFileOutputStream(ExecLauncher.stdErr)), true));

                setupJinixEnvironment(execLaunchData.environment);
            } else {
                translatorNode = new JinixFileDescriptor(execLaunchData.translatorNode);
                translatorNodePath = execLaunchData.translatorNodePath;
                setupJinixEnvironment(new Properties());
                for (int i=0; i<execArgs.length; i++) {
                    String arg = execArgs[i];
                    if (arg.equals("-jinix:native")) {
                        nativeAccess = true;
                        execArgs[i] = null;
                        execArgs = Arrays.stream(execArgs)
                                .filter(s -> (s != null))
                                .toArray(String[]::new);
                        break;
                    }
                }
                setupTranslatorLogging(translatorNodePath);
                System.setIn(null);
                System.setOut(new PrintStream(new LoggingOutputStream(Logger.getLogger(""))));
                System.setErr(new PrintStream(new LoggingOutputStream(Logger.getLogger(""))));
            }

            URL.setURLStreamHandlerFactory(new ExecStreamHandlerFactory());

            URL execCmdURL;
            try {
                execCmdURL = new URL("jns", null, 0, execCmd);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }

            ExecClassLoader execCL = new ExecClassLoader(execCmdURL, nativeAccess,
                    ExecLauncher.class.getClassLoader());

            String execClassName;
            String[] execClassPath;
            try {
                execClassName = execCL.getMainClassName();
                execClassPath = execCL.getLibraryNames();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (execClassName == null) {
                throw new RuntimeException("Invalid executable JAR. Missing Main-Class attribute in manifest.");
            }

            if (execClassPath != null && execClassPath.length > 0) {
                URL[] classpathURLs = resolveExecClassPath(execClassPath,
                        JinixSystem.getJinixProperties().getProperty(JinixRuntime.JINIX_LIBRARY_PATH));
                for (URL url : classpathURLs) {
                    execCL.addURL(url);
                }
            }

            execThreadGroup = new ThreadGroup("Jinix-Exec");

            try {
                Class c = execCL.loadClass(execClassName);
                Method m = c.getMethod("main", new Class[] { execArgs.getClass() });
                m.setAccessible(true);
                int mods = m.getModifiers();
                if (m.getReturnType() != void.class || !Modifier.isStatic(mods) ||
                        !Modifier.isPublic(mods)) {
                    throw new NoSuchMethodException("main");
                }

                Thread execThread = new Thread(execThreadGroup, new ExecThreadRunnable(m, execArgs));
                execThread.setContextClassLoader(execCL);

                Policy.setPolicy(new JinixPolicy());
                System.setSecurityManager(new SecurityManager());

                execThread.start();


            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Invalid jinix executable", e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Invalid jinix executable", e);
            }

        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void setupRMI(String rmiMode) {

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

    private static void setupJinixEnvironment(Properties environment) {

        for (Object propertyName: environment.keySet()) {
            JinixSystem.setJinixProperty((String)propertyName, (String)environment.get(propertyName));
        }

        // Add default variables to the environment if they are not already there
        if (JinixSystem.getJinixProperties().getProperty(JinixRuntime.JINIX_PATH) == null) {
            JinixSystem.setJinixProperty(JinixRuntime.JINIX_PATH,"/bin");
        }
        if (JinixSystem.getJinixProperties().getProperty(JinixRuntime.JINIX_LIBRARY_PATH) == null) {
            JinixSystem.setJinixProperty(JinixRuntime.JINIX_LIBRARY_PATH, "/lib");
        }
        if (JinixSystem.getJinixProperties().getProperty(JinixRuntime.JINIX_PATH_EXT) == null) {
            JinixSystem.setJinixProperty(JinixRuntime.JINIX_PATH_EXT, "jar");
        }

        if (environment.containsKey(JinixRuntime.JINIX_ENV_PWD)) {
            JinixSystem.setJinixProperty("user.dir", environment.getProperty(JinixRuntime.JINIX_ENV_PWD));
        }
        JinixSystem.setJinixProperty("java.library.path", null);
        JinixSystem.setJinixProperty("user.home", "/home");
        JinixSystem.setJinixProperty("java.io.tmpdir", "/home/tmp");
        JinixSystem.setJinixProperty("os.name", "Jinix");
        JinixSystem.setJinixProperty("os.version", "0.1");
        JinixSystem.setJinixProperty("path.separator", ":");
        JinixSystem.setJinixProperty("line.separator", "\n");
        JinixSystem.setJinixProperty("file.separator", "/");

        JinixSystem.setJinixProperty("sun.java.command", execCmd);

        JinixSystem.setJinixProperty("sun.boot.class.path", null);

    }

    /**
     * Resolve all of the jar files in the manifest classpath in the library path.
     *
     * @param execClassPath
     * @return an array of URL to fully qualified jar files
     */
    private static URL[] resolveExecClassPath(String[] execClassPath, String libraryPathStr) {
        try {
            int i=0;
            URL[] rtrn = new URL[execClassPath.length];
            if (libraryPathStr == null) {
                libraryPathStr = "";
            }
            String[] libraryPath = libraryPathStr.split(":");
            for (String jarFile : execClassPath) {
                for(String libDir : libraryPath) {
                    String libPathName = libDir + "/" + jarFile;
                    LookupResult lookup = rootNameSpace.lookup(libPathName);
                    FileNameSpace fns = (FileNameSpace) lookup.remote;
                    if (fns.exists(lookup.remainingPath)) {
                        try {
                            rtrn[i] = new URL("jns", null, 0, libPathName);
                        } catch (MalformedURLException e) {
                            throw new RuntimeException("Invalid library path name: "+libPathName);
                        }
                        break;
                    }
                }
                if (rtrn[i] == null) {
                    throw new RuntimeException("Failed to locate library in library path: "+jarFile);
                }
                i++;
            }
            return rtrn;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Translators have no standard input, output or error. Therefore, we need
     * to setup their default logging so that the console logger is removed. Instead,
     * each translator logs to its synthetic server name in the /var/log directory.
     */
    private static void setupTranslatorLogging(String translatorNodePath) {
        Logger rootLogger = LogManager.getLogManager().getLogger("");

        if (consoleLogging) {
            // The first and only handler should be the ConsoleHandler.
            rootLogger.getHandlers()[0].setFormatter(
                    new BriefExecLauncherLogFormatter(getTranslatorServerName(translatorNodePath)));
            return;
        }
        //Clear all existing handlers
        Handler[] oldHandler = rootLogger.getHandlers();
        for (Handler h : oldHandler) {
            rootLogger.removeHandler(h);
        }

        String logFileName = "/var/log/" + getTranslatorServerName(translatorNodePath) + ".log";
        try {
            Path logFilePath = new JinixPath(logFileName);
            rootLogger.addHandler(new ExecLauncherLoggingHandler(logFilePath));
        } catch (IOException e) {
            // Ignore any IO Errors. We will have not logging, but there is no way to
            // tell anyone.
        }
    }

    private static String getTranslatorServerName(String translatorNodePath) {
        if (translatorNodePath.startsWith("/")) {
            translatorNodePath = translatorNodePath.substring(1);
        }
        translatorNodePath = translatorNodePath.replace('/','-');
        if (translatorNodePath.lastIndexOf('.') > -1) {
            translatorNodePath = translatorNodePath.substring(0,
                    translatorNodePath.lastIndexOf('.')-1);
        }
        return translatorNodePath;
    }

    public static void main(String[] args) {

        if (args.length < 2) {
            throw new IllegalArgumentException("Insufficient arguments provided.");
        }

        int pid;
        try {
            pid = Integer.valueOf(args[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("First parameter must be a valid integer: " + args[0]);
        }

        String rmiModeStr = args[1];
        if (rmiModeStr == null || !rmiModeStr.startsWith("rmi=")) {
            throw new IllegalArgumentException("Second parameter must indicate rmi mode: " + args[1]);
        }

        rmiModeStr = rmiModeStr.substring("rmi=".length());

        setupRMI(rmiModeStr);

        try {
            Registry registry = getRegistry();

            try {
                rootNameSpace = (NameSpace) registry.lookup("root");
                es = (ExecServer) (rootNameSpace.lookup(ExecServer.SERVER_NAME)).remote;
                pm = (ProcessManager) rootNameSpace.lookup(ProcessManager.SERVER_NAME).remote;
            } catch (NotBoundException e) {
                System.err.println("ProcessManager: Failed to locate root NameSpace in RMI Registry");
                return;
            }
        } catch (RemoteException e) {
            System.err.println("Exec failure to locate root name space");
            e.printStackTrace(System.err);
            return;
        }

        launch(pid);

        try {
            while (true) {
                ProcessManager.Signal signal = pm.listenForSignal(pid);

                if (signal == null) {
                    // Should never happen, means the pid is not registered with the process manager. We just exit.
                    System.exit(0);
                }

                if (signal == ProcessManager.Signal.ABORT || signal == ProcessManager.Signal.KILL) {
                    System.exit(0);
                } else if (signal == ProcessManager.Signal.HANGUP) {
                    // TODO: give the process an opportunity to handle this signal. If not handler, just exit
                    System.exit(0);
                } else if (signal == ProcessManager.Signal.TERMINATE) {
                    if (signalHandler != null) {
                        signalHandler.handleSignal(signal);
                    } else {
                        System.exit(0);
                    }
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    static private class ExecLauncherShutdownThread extends Thread {

        @Override
        public void run() {
            if (ExecLauncher.translatorNode != null) {
                ExecLauncher.translatorNode.close();
            } else {
                ExecLauncher.stdIn.close();
                ExecLauncher.stdOut.close();
                ExecLauncher.stdErr.close();
            }
            FileChannel lastFC = null;
            int retryCount = 0;
            while (!JinixFileDescriptor.openFileDescriptors.isEmpty()) {
                JinixFileDescriptor fd = JinixFileDescriptor.openFileDescriptors.get(0);
                if (lastFC != null && lastFC == fd.getHandle()) {
                    retryCount++;
                    if (retryCount > 9) {
                        System.out.println("Stopped FD close loop");
                        return;
                    }
                } else {
                    lastFC = fd.getHandle();
                }
                fd.close();
            }
        }
    }

    private static class ExecThreadRunnable implements Runnable {

        Method m;
        String[] execArgs;

        private ExecThreadRunnable(Method method, String[] args) {
            m = method;
            execArgs = args;
        }

        @Override
        public void run() {
            try {
                m.invoke(null, new Object[] { execArgs });
            } catch (IllegalAccessException e) {
                // This should not happen, as we have disabled access checks
            } catch (InvocationTargetException e) {
                // If the executing program throws a RuntimeException display on stderr.
                Throwable cause = e.getCause();
                if (cause != null) {
                    cause.printStackTrace(System.err);
                } else {
                    e.printStackTrace(System.err);
                }
            } finally {
                // Since the main ExecLauncher thread is waiting on a signal, send an abort signal.
                JinixRuntime.getRuntime().sendSignal(pid, ProcessManager.Signal.ABORT);
            }
        }
    }

    private static class BriefExecLauncherLogFormatter extends Formatter {
        private static final String DEFAULT_FORMAT =
                "%1$tH:%1$tM:%1$tS %2$s: %3$s: %4$s%n";
        String name;

        private BriefExecLauncherLogFormatter(String translatorName) {
            name = translatorName;
        }
        @Override
        public synchronized String format(LogRecord record) {
            return String.format(DEFAULT_FORMAT,
                    new Date(record.getMillis()),
                    name + (!record.getLoggerName().isEmpty() ? "-"+record.getLoggerName() : ""),
                    record.getLevel().getLocalizedName(),
                    formatMessage(record));
        }
    }

    public static class JinixRuntimeImpl extends JinixRuntime {

        @Override
        public NameSpace getRootNamespace() {
            return rootNameSpace;
        }

        @Override
        public Context getNamingContext() {
            return new JinixContext(rootNameSpace);
        }

        @Override
        public int exec(String cmd, String[] args) throws FileNotFoundException, InvalidExecutableException {

            return exec(JinixSystem.getJinixProperties(), cmd, args, null, null, null);
        }

        @Override
        public int exec(Properties environment, String cmd, String[] args,
                        JinixFileDescriptor stdin, JinixFileDescriptor stdout, JinixFileDescriptor stderr)
            throws FileNotFoundException, InvalidExecutableException {
            try {

                if (stdin == null) stdin = ExecLauncher.stdIn;
                if (stdout == null) stdout = ExecLauncher.stdOut;
                if (stderr == null) stderr = ExecLauncher.stdErr;

                // Duplicate the file descriptors. This is necessary to prevent the
                // file descriptors from being closed when the child process terminates as our
                // parent has the same file descriptors and may continue to use them.
                stdin.getHandle().duplicate();
                stdout.getHandle().duplicate();
                stderr.getHandle().duplicate();

                return es.exec(environment, cmd, args, ExecLauncher.pid,
                        stdin.getHandle(), stdout.getHandle(), stderr.getHandle());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int fork() throws FileNotFoundException, InvalidExecutableException {
            JinixSystem.setJinixProperty(JINIX_FORK, Integer.toString(pid));
            Properties environ = JinixSystem.getJinixProperties();
            return exec(environ, execCmd, execArgs, null, null, null);
        }

        @Override
        public boolean isForkChild() {
            if (System.getProperty(JINIX_FORK) != null &&
                    Integer.parseInt(System.getProperty(JINIX_FORK)) != pid) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int getPid() {
            return pid;
        }

        @Override
        public int waitForChild() {
            try {
                return pm.waitForChild(ExecLauncher.pid);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JinixPipe pipe() {
            try {
                FifoServer fs = (FifoServer) rootNameSpace.lookup(FifoServer.SERVER_NAME).remote;
                return new JinixPipe(fs.openPipe());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void sendSignal(int pid, ProcessManager.Signal signal) {
            try {
                pm.sendSignal(pid, signal);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void registerSignalHandler(ProcessSignalHandler handler) {
            signalHandler = handler;
        }

        @Override
        public JinixFileDescriptor getTranslatorFile() {
            if (translatorNode == null && translatorNodePath == null) {
                return null;
            }
            return translatorNode;
        }

        @Override
        public void bindTranslator(Remote translator) {
            try {
                rootNameSpace.bind(translatorNodePath, translator);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
