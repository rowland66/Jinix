package org.rowland.jinix.exec;

import org.rowland.jinix.IllegalOperationException;
import org.rowland.jinix.JinixKernelUnicastRemoteObject;
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
import org.rowland.jinix.terminal.TermServer;
import org.rowland.jinix.terminal.TerminalAttributes;

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
import java.util.*;
import java.util.logging.*;
import java.util.logging.Formatter;

/**
 * ExecLauncher bootstraps every Jinix executable started by the ExecServer. The ExecLauncher calls back to the
 * ExecServer to get critical data required to execute the Jinix executable. The callback data include FileChannels
 * for stdin, stdout and stderr. The ExecLauncher creates an ExecClassLoader to load the Jinix executable jar. The
 * ExecLauncher installs a SecurityManager that enforces a Jinix security policy to restrict Jinix executables
 * access to the underlying OS.
 */
public class ExecLauncher {

    private static int pid, pgid;
    private static NameSpace rootNameSpace;
    private static ExecServer es;
    private static ProcessManager pm;
    private static TermServer ts;
    private static JinixFileDescriptor stdIn;
    private static JinixFileDescriptor stdOut;
    private static JinixFileDescriptor stdErr;
    private static JinixFileDescriptor translatorNode;
    private static String translatorNodePath;
    private static ThreadGroup execThreadGroup;
    private static boolean nativeAccess = false;
    private static boolean consoleLogging = false;
    private static ExecClassLoader execCL;

    private static InputStream debugStdin;
    private static PrintStream debugStdOut;
    private static PrintStream debugStdErr;

    private static String execCmd;
    private static String[] execArgs;
    private static ProcessSignalHandler signalHandler;
    private static List<Thread> runtimeThreads;

    private static ProcessManager.ProcessState state;
    private static int exitStatus = 0;

    public static void launch(int pid, int pgid) {

        try {
            Runtime.getRuntime().addShutdownHook(new ExecLauncherShutdownThread());

            JinixRuntime.setJinixRuntime(new JinixRuntimeImpl());

            ExecLauncher.pid = pid;
            ExecLauncher.pgid = pgid;

            runtimeThreads = new LinkedList<>();

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

            execCL = new ExecClassLoader(execCmd, nativeAccess,
                    ExecLauncher.class.getClassLoader());

            String execClassName;
            try {
                execClassName = execCL.getMainClassName();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (execClassName == null) {
                System.err.println("'"+execCmd+"' is an invalid executable file. Missing manifest or Main-Class attribute in manifest.");
                System.exit(1);
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

                // The Jinix security realm is not in effect. All access to resources beyond this point is through Jinix
                Policy.setPolicy(new JinixPolicy());
                System.setSecurityManager(new SecurityManager());

                Thread.setJinixThread();

                Thread execThread = new Thread(execThreadGroup, new ExecThreadRunnable(m, execArgs), "Jinix Main");
                execThread.setContextClassLoader(execCL);

                /*
                try {
                    Thread.class.getDeclaredMethod("setJinixThread").invoke(null);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
                */

                execThread.start();

            } catch (ClassNotFoundException e) {
                System.err.println("'"+execCmd+"' is an invalid executable file. Manifest Main-Class not found: "+execClassName);
                System.exit(1);
            } catch (NoSuchMethodException e) {
                System.err.println("'"+execCmd+"' is an invalid executable file. Manifest Main-Class has no main(): "+execClassName);
                System.exit(1);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void setupRMI(String rmiMode) {

        System.setProperty("java.rmi.server.useCodebaseOnly", "false");
        System.setProperty("java.rmi.server.RMIClassLoaderSpi", "org.rowland.jinix.exec.ExecRMIClassLoader");

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
            rootLogger.getHandlers()[0].setLevel(Level.ALL);
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

    private static void suspend() throws RemoteException{

        if (state != ProcessManager.ProcessState.RUNNING) {
            return;
        }

        for (Thread t : runtimeThreads) {
            t.suspend();
        }
        state = ProcessManager.ProcessState.SUSPENDED;
        pm.updateProcessState(pid, state);
    }

    private static void resume() throws RemoteException {

        if (state != ProcessManager.ProcessState.SUSPENDED) {
            return;
        }
        for (Thread t : runtimeThreads) {
            t.resume();
        }

        // Release any terminal operations that were blocked when the process was suspended
        //synchronized (JinixFileDescriptor.blockedTerminalOperationSynchronizationObject) {
        //    JinixFileDescriptor.blockedTerminalOperationSynchronizationObject.notifyAll();
        //}

        state = ProcessManager.ProcessState.RUNNING;
        pm.updateProcessState(pid, state);
    }

    public static void main(String[] args) {

        state = ProcessManager.ProcessState.STARTING;

        if (args.length < 2) {
            throw new IllegalArgumentException("Insufficient arguments provided.");
        }

        int pid;
        try {
            pid = Integer.valueOf(args[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("First parameter (PID) must be a valid integer: " + args[0]);
        }

        int pgid;
        try {
            pgid = Integer.valueOf(args[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Second parameter (PGID) must be a valid integer: " + args[1]);
        }

        String rmiModeStr = args[2];
        if (rmiModeStr == null || !rmiModeStr.startsWith("rmi=")) {
            throw new IllegalArgumentException("Third parameter must indicate rmi mode: " + args[2]);
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
                System.err.println("ExecLauncher: Failed to locate root NameSpace in RMI Registry");
                return;
            }
        } catch (RemoteException e) {
            System.err.println("Exec failure to locate root name space");
            e.printStackTrace(System.err);
            return;
        }

        launch(pid, pgid);

        try {
            state = ProcessManager.ProcessState.RUNNING;
            pm.updateProcessState(pid, state);
        } catch (RemoteException e) {
            // ignore as the state updated can be skipped
        }

        try {
            while (true) {
                ProcessManager.Signal signal = pm.listenForSignal(pid);
                if (signal == null) {
                    // Should only happen when the process is terminating. ProcessManager.deRegisterProcess() will wake
                    // up waiting thread, and it will return null;
                    return;
                }

                switch (signal) {
                    case ABORT:
                    case KILL:
                        System.exit(0);
                        break;
                    case HANGUP:
                        if (signalHandler == null || !signalHandler.handleSignal(signal)) {
                            System.exit(0);
                        }
                        break;
                    case TERMINATE:
                        if (signalHandler == null || !signalHandler.handleSignal(signal)) {
                            System.exit(0);
                        }
                        break;
                    case STOP:
                        suspend();
                        break;
                    case TSTOP:
                        if (signalHandler == null || !signalHandler.handleSignal(signal)) {
                            suspend();
                        }
                        break;
                    case CONTINUE:
                        resume();
                        break;
                    case CHILD:
                        if (signalHandler != null) {
                            signalHandler.handleSignal(signal);
                        }
                        break;
                    case TERMINAL_INPUT:
                        if (signalHandler == null || !signalHandler.handleSignal(signal)) {
                            suspend();
                        }
                        break;
                    case TERMINAL_OUTPUT:
                        if (signalHandler == null || !signalHandler.handleSignal(signal)) {
                            suspend();
                        }
                        break;
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    static private class ExecLauncherShutdownThread extends Thread {

        @Override
        public void run() {

            try {
                state = ProcessManager.ProcessState.STOPPING;
                pm.updateProcessState(pid, state);
            } catch (RemoteException e) {
                // Ignore as the state update can be skipped
            }

            if (ExecLauncher.translatorNode != null) {
                ExecLauncher.translatorNode.close();
            } else {
                ExecLauncher.stdIn.close();
                ExecLauncher.stdOut.close();
                ExecLauncher.stdErr.close();
            }
            RemoteFileAccessor lastFC = null;
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

            if (execCL != null) {
                execCL.close();
            }

            if (ExecRMIClassLoader.getJinixProcessInstance() != null) {
                ExecRMIClassLoader.getJinixProcessInstance().close();
            }

            try {
                state = ProcessManager.ProcessState.SHUTDOWN;
                pm.deRegisterProcess(pid, ExecLauncher.exitStatus);
            } catch (RemoteException e) {
                // Ignore as the state update can be skipped
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

                JinixKernelUnicastRemoteObject.dumpExportedObjects(System.out);

                boolean livingThread = false;
                do {
                    livingThread = false;
                    for (Thread t : runtimeThreads) {
                        if (t != Thread.currentThread() && !t.isDaemon() && t.isAlive()) {
                            livingThread = true;
                            System.out.println("Living thread: "+t.getName());
                            try {
                                t.join();
                            } catch (InterruptedException e) {
                                // this should never happen
                            }
                        }
                    }
                } while (livingThread);

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

    static synchronized void getTerminalServer() {
        if (ts == null) {
            try {
                ts = (TermServer) rootNameSpace.lookup(TermServer.SERVER_NAME).remote;
            } catch (RemoteException e) {
                System.err.println("Jinix critical error: failed to find terminal server at "+TermServer.SERVER_NAME);
            }
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

            return exec(JinixSystem.getJinixProperties(), cmd, args, 0, null, null, null);
        }

        @Override
        public int exec(Properties environment, String cmd, String[] args, int processGroupId,
                        JinixFileDescriptor stdin, JinixFileDescriptor stdout, JinixFileDescriptor stderr)
                throws FileNotFoundException, InvalidExecutableException {
            return exec(JinixSystem.getJinixProperties(), cmd, args, processGroupId, 0, stdin, stdout, stderr);
        }

        @Override
        public int exec(Properties environment, String cmd, String[] args, int processGroupId, int sessionId,
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

                return es.exec(environment, cmd, args, ExecLauncher.pid, processGroupId, sessionId,
                        stdin.getHandle(), stdout.getHandle(), stderr.getHandle());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int fork() throws FileNotFoundException, InvalidExecutableException {
            return fork(null, null, null);
        }

        @Override
        public int fork(JinixFileDescriptor in, JinixFileDescriptor out, JinixFileDescriptor error) throws FileNotFoundException, InvalidExecutableException {
            JinixSystem.setJinixProperty(JINIX_FORK, Integer.toString(pid));
            Properties environ = JinixSystem.getJinixProperties();
            return exec(environ, execCmd, execArgs, 0, in, out, error);
        }

        @Override
        public boolean isForkChild() {
            if (System.getProperty(JINIX_FORK) != null &&
                    Integer.parseInt(System.getProperty(JINIX_FORK)) != pid) {
                JinixSystem.setJinixProperty(JINIX_FORK, Integer.toString(pid));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public JinixFileDescriptor getStandardFileDescriptor(StandardFileDescriptor sfd) {
            switch (sfd) {
                case IN:
                    return stdIn;
                case OUT:
                    return stdOut;
                case ERROR:
                    return stdErr;
            }
            return null; // this cannot happen. Not sure why it is required.
        }

        @Override
        public int getPid() {
            return pid;
        }

        @Override
        public int getProcessGroupId() {
            return pgid;
        }

        @Override
        public int getProcessSessionId() {
            try {
                return pm.getProcessSessionId(pid);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ProcessManager.ChildEvent waitForChild(boolean nowait) {
            try {
                return pm.waitForChild(ExecLauncher.pid, nowait);
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
        public void sendSignalProcessGroup(int processGroupId, ProcessManager.Signal signal) {
            try {
                pm.sendSignalProcessGroup(processGroupId, signal);
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
        public String getTranslatorNodePath() {
            return translatorNodePath;
        }

        @Override
        public void bindTranslator(Remote translator) {
            try {
                rootNameSpace.bind(translatorNodePath, translator);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void registerJinixThread(Thread t) {
            if (!runtimeThreads.contains(t)) {
                runtimeThreads.add(t);
            }
        }

        @Override
        public void setProcessGroupId(int processGroupId) {
            try {
                pgid = pm.setProcessGroupId(pid, processGroupId);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setProcessSessionId() {
            try {
                pgid = pm.setProcessSessionId(pid);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public short getProcessTerminalId() {
            try {
                return pm.getProcessTerminalId(pid);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setProcessTerminalId(short terminalId) {
            try {
                pm.setProcessTerminalId(pid, terminalId);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setForegroundProcessGroupId(int processGroupId) {
            try {
                getTerminalServer();
                short terminalId = pm.getProcessTerminalId(pid);
                if (terminalId == -1) {
                    //Deamon processes and translators don't have terminals so they cannot call this method.
                    //TODO throw an exception.
                    return;
                }
                ts.setTerminalForegroundProcessGroup(terminalId, processGroupId);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public TerminalAttributes getTerminalAttributes(short terminalId) {
            try {
                getTerminalServer();
                return ts.getTerminalAttributes(terminalId);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setTerminalAttributes(short terminalId, TerminalAttributes terminalAttributes) {
            try {
                getTerminalServer();
                ts.setTerminalAttributes(terminalId, terminalAttributes);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void exit(int status) {
            exitStatus = status;
            sendSignal(pid, ProcessManager.Signal.ABORT);
            while (true) {
                try {
                    // wait until the abort signal is received and JVM terminates.
                    wait();
                } catch (InterruptedException e) {
                    // We should never be interrupted.
                }
            }
        }
    }


}
