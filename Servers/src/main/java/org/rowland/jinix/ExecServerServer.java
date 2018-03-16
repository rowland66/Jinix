package org.rowland.jinix;

import org.rowland.jinix.exec.ExecLauncherData;
import org.rowland.jinix.exec.ExecServer;
import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.naming.*;
import org.rowland.jinix.proc.ProcessManager;
import org.rowland.jinix.proc.RegisterResult;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.rowland.jinix.JinixKernel.consoleLogging;
import static org.rowland.jinix.JinixKernel.rmiMode;

/**
 * The ExecServer is responsible for executing Jinix executable files. Conceptually, the ExecServer could handle
 * multiple executable file types. For not is only executable jar files are executable. The execute an executable
 * file, the ExecServer creates a new process and runs the ExecLauncher. Every new process is registered with the
 * ProcessManager. At the underlying OS level, all Jinix process created from Jinix executable files are children
 * of the ExecServer process.
 */
class ExecServerServer extends JinixKernelUnicastRemoteObject implements ExecServer {

    static Logger logger = Logger.getLogger(SERVER_LOGGER);
    private String javaHome;
    private NameSpace ns;
    private ProcessManager pm;
    private final Map<Integer, ExecLauncherCallbackData> callbackDataMap = new HashMap<>();

    ExecServerServer(NameSpace nameSpace, String javaHome) throws RemoteException {
        super();
        this.ns = nameSpace;
        this.javaHome = javaHome;

        this.pm = (ProcessManager) ns.lookup(ProcessManager.SERVER_NAME).remote;
    }

    @Override
    public int execTranslator(String cmd, String[] args, RemoteFileAccessor translatorNode, String translatorNodePath)
            throws FileNotFoundException, InvalidExecutableException, RemoteException {
        return exec0(null, cmd, args, 1, -1,
                null, null, null,
                translatorNode, translatorNodePath, "file://"+cmd);
    }

    @Override
    public int exec(Properties env,
                    String cmd,
                    String[] args,
                    int parentId,
                    int processGroupId,
                    RemoteFileAccessor stdIn,
                    RemoteFileAccessor stdOut,
                    RemoteFileAccessor stdErr)
            throws FileNotFoundException, InvalidExecutableException, RemoteException {
        return exec0(env, cmd, args, parentId, processGroupId, stdIn, stdOut, stdErr, null, null, null);
    }

    private int exec0(Properties env,
                      String cmd,
                      String[] args,
                      int parentId,
                      int processGroupId,
                      RemoteFileAccessor stdIn,
                      RemoteFileAccessor stdOut,
                      RemoteFileAccessor stdErr,
                      RemoteFileAccessor translatorNode,
                      String translatorNodePath,
                      String codebaseURL)
            throws FileNotFoundException, InvalidExecutableException, RemoteException {
        Runtime runtime = Runtime.getRuntime();

        LookupResult lookup = this.ns.lookup(cmd);
        FileNameSpace fs = (FileNameSpace) lookup.remote;
        StringBuilder redirectExecutable = new StringBuilder(128);
        RemoteFileAccessor cmdFd = null;
        try {
            try {
                cmdFd = fs.getRemoteFileAccessor(-1, lookup.remainingPath, EnumSet.of(StandardOpenOption.READ));
            } catch (FileAlreadyExistsException e) {
                throw new RuntimeException("Internal Error",e); // should never happen
            } catch (NoSuchFileException e) {
                throw new FileNotFoundException("File not found: '"+cmd+"'");
            }

            if (!isValidExecutable(cmdFd, redirectExecutable)) {
                throw new InvalidExecutableException(cmd);
            }
        } finally {
            if (cmdFd != null) {
                cmdFd.close();
            }
        }

        if (redirectExecutable.length() > 0) {

            // Move the cmd (which is a script) into the args array as the first element
            String[] newArgs = new String[args.length+1];
            newArgs[0] = cmd;
            System.arraycopy(args, 0, newArgs, 1, args.length);
            args = newArgs;

            cmd = redirectExecutable.toString();
            lookup = this.ns.lookup(cmd);
            fs = (FileNameSpace) lookup.remote;
            try {
                try {
                    cmdFd = fs.getRemoteFileAccessor(-1, lookup.remainingPath, EnumSet.of(StandardOpenOption.READ));
                } catch (FileAlreadyExistsException e) {
                    throw new RuntimeException("Internal Error",e); // should never happen
                } catch (NoSuchFileException e) {
                    throw new FileNotFoundException("File not found: '"+cmd+"'");
                }

                if (!isValidExecutable(cmdFd, null)) {
                    throw new InvalidExecutableException(cmd);
                }

            } finally {
                if (cmdFd != null) {
                    cmdFd.close();
                }
            }
        }

        RegisterResult result = pm.registerProcess(parentId, processGroupId, cmd, args);
        final int pid = result.pid;
        final int pgid = result.pgid;

        String javaCmd = "java";
        if (javaHome != null) {
            javaCmd = javaHome +
                    File.separator + "bin" +
                    File.separator + "java";
        }
        List<String> cmdList = new ArrayList<>(16);
        cmdList.add(javaCmd);

        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-Xdebug") || args[i].startsWith("-Xrunjdwp")) {
                cmdList.add(args[i]);
                args[i] = null;
            }
        }

        cmdList.add("-Xbootclasspath/p:" +
                "./lib/Runtime.jar" + File.pathSeparator +
                "./lib/PlatformRuntime.jar" + File.pathSeparator +
                "./lib/ProgrammingInterface.jar" + File.pathSeparator +
                "./lib/ServerInterfaces.jar");
        cmdList.add("-javaagent:./lib/RuntimeAgent.jar");
        cmdList.add("-Djava.nio.file.spi.DefaultFileSystemProvider=org.rowland.jinix.nio.JinixFileSystemProvider");
        //cmdList.add("-Dsun.rmi.dgc.logLevel=VERBOSE");
        //cmdList.add("-Dsun.misc.URLClassPath.debug=true");
        //cmdList.add("-Djava.security.debug=\"access,failure\"");
        if (codebaseURL != null) {
            cmdList.add("-Djava.rmi.server.codebase="+codebaseURL);
            //cmdList.add("-Djava.rmi.server.logCalls=true");
            //cmdList.add("-Dsun.rmi.dgc.logLevel=VERBOSE");
            //cmdList.add("-Dsun.rmi.client.logCalls=true");
            //cmdList.add("-Dsun.rmi.client.logLevel=VERBOSE");
            //cmdList.add("-Dsun.rmi.loader.logLevel=VERBOSE");
        }

        if (rmiMode == JinixKernel.RMI_MODE.AFUNIX) {
            cmdList.add("-DAFUNIXRMISocketFactory.config.file=./config");
            cmdList.add("-Djava.library.path=./lib");
            if (translatorNodePath != null) {
                cmdList.add("-DAFUNIXRMISocketFactory.server.name=" +
                        getTranslatorServerName(translatorNodePath));
            }
        }
        cmdList.add("-classpath");
        String classPathStr = "./lib/ExecLauncher.jar";
        if (rmiMode == JinixKernel.RMI_MODE.AFUNIX) {
            classPathStr = classPathStr + File.pathSeparator + "./lib/AFUNIXRMI.jar";
        }
        cmdList.add(classPathStr);

        args = compressCmdArgs(args);

        cmdList.add("org.rowland.jinix.exec.ExecLauncher");
        cmdList.add(Integer.toString(pid));
        cmdList.add(Integer.toString(pgid));

        if (rmiMode == JinixKernel.RMI_MODE.AFUNIX) {
            cmdList.add("rmi=AFUNIX");
        } else {
            cmdList.add("rmi=Default");
        }

        if (consoleLogging) {
            cmdList.add("consoleLogging");
        }

        String[] cmdArray = cmdList.toArray(new String[cmdList.size()]);

        if (env == null) {
            env = new Properties();
        }

        ExecLauncherCallbackData callbackData = new ExecLauncherCallbackData();
        callbackData.cmd = cmd;
        callbackData.args = args;
        callbackData.stdIn = stdIn;
        callbackData.stdOut = stdOut;
        callbackData.stdErr = stdErr;
        callbackData.environment = env;
        callbackData.translatorNode = translatorNode;
        callbackData.translatorNodePath = translatorNodePath;

        synchronized (callbackDataMap) {
            callbackDataMap.put(pid, callbackData);
        }

        try {
            final Process osProcess = runtime.exec(cmdArray);
            //registerOSProcess(pid, osProcess); //TODO: figure out how to handle the OS process as ProcessData is not serializable

            (new Thread(new Runnable() {
                public void run() {
                    try {
                        logger.fine("Waiting for osProcess: "+pid);
                        osProcess.waitFor();
                    } catch (InterruptedException e) {
                        return;
                    }

                    if (osProcess.exitValue() > 0) {
                        logger.severe("Internal Failure. Process returned exit value: "+osProcess.exitValue());
                        try {
                            if (stdErr != null) {
                                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                OutputStreamWriter byteWriter = new OutputStreamWriter(bos);
                                byteWriter.write("Internal failure launching OS process. Check debugging parameters.\n");
                                stdErr.write(-1, bos.toByteArray());
                                stdErr.flush();
                            }
                        } catch (IOException e) {
                            logger.log(Level.SEVERE, "", e);
                        }
                        try {
                            pm.deRegisterProcess(pid, osProcess.exitValue());
                        } catch (RemoteException e) {
                            logger.log(Level.SEVERE, "Failure deregistering process", e);
                        }
                    }
                    /*
                    try {
                        logger.fine("Deregistering process: " +pid );
                        pm.deRegisterProcess(pid);
                    } catch (RemoteException e) {
                        logger.fine("ExecServer: RemoteExeception deregistering process: "+pid);
                    }
                    */
                }
            })).start();

            // These 2 thread are not really needed, but are left in for easier debugging.
            Thread stdOutThread = new Thread(new Runnable() {public void run() {
                try {
                    InputStream is = osProcess.getInputStream();
                    int c = is.read();
                    while (c > -1) {
                        System.out.print((char)c);
                        c = is.read();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }});
            stdOutThread.start();

            Thread stdErrThread = new Thread(new Runnable() {public void run() {
                try {
                    InputStream is = osProcess.getErrorStream();
                    int c = is.read();
                    while (c > -1) {
                        System.out.print((char)c);
                        c = is.read();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }});
            stdErrThread.start();

        } catch (IOException e) {
            pm.deRegisterProcess(pid, 1);
            throw new RemoteException("Failure starting underlying OS process.", e);
        }

        return pid;
    }

    @Override
    public ExecLauncherData execLauncherCallback(int pid) throws RemoteException {
        ExecLauncherCallbackData p = callbackDataMap.get(pid);

        if (p == null) {
            throw new RemoteException("ExecServer: execLauncherCallback() called with invalid pid: " + pid);
        }

        ExecLauncherData rtrn = new ExecLauncherData();
        rtrn.environment = p.environment;
        rtrn.cmd = p.cmd;
        rtrn.args = p.args;
        rtrn.stdIn = p.stdIn;
        rtrn.stdOut = p.stdOut;
        rtrn.stdErr = p.stdErr;
        rtrn.translatorNode = p.translatorNode;
        rtrn.translatorNodePath = p.translatorNodePath;

        synchronized (callbackDataMap) {
            callbackDataMap.remove(pid);
        }

        return rtrn;
    }

    private static boolean isValidExecutable(RemoteFileAccessor cmd, StringBuilder redirectExecutable) {
        try {
            byte[] magicBytes = cmd.read(0,2);
            if (magicBytes == null || magicBytes.length < 2) {
                return false;
            }
            if (magicBytes[0] == 0x50 && magicBytes[1] == 0x4B) { // 'PK'
                return true;
            }
            if (magicBytes[0] == 0x23 && magicBytes[1] == 0x21) { // '#!'
                if (redirectExecutable == null) {
                    return false; // Only 1 redirect executable supported.
                }
                int i = 0;
                byte[] execBytes = new byte[128];
                byte[] b = cmd.read(0,1);
                while (b != null && b[0] != 0x0A) {
                    redirectExecutable.append((char) b[0]);
                    b = cmd.read(0,1);
                }
                if (b == null) {
                    return false; // reaching the end of the file before a newline is invalid
                }

                return true;
            }
            return false;
        } catch (RemoteException e) {
            throw new RuntimeException("Internal error", e);
        }
    }

    private static String[] compressCmdArgs(String[] args) {
        int nullSlots = 0;
        for(int i=0;i<args.length-nullSlots;i++) {
            if (args[i]==null) {
                nullSlots++;
                if (i < (args.length-1)) {
                    System.arraycopy(args, i + 1, args, i, args.length - i - 1);
                    i--;
                }
            }
        }
        String[] rtrn = new String[args.length-nullSlots];
        System.arraycopy(args,0,rtrn,0,args.length-nullSlots);
        return rtrn;
    }

    /**
     * For AFUNIX every server needs a name, and translators are servers. Derive
     * a name from the translatorNodePath which should be unique for the system.
     *
     * @param translatorNodePath the translators fully qualified name in the global
     *                           namespace
     * @return the server name
     */
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


    private static class ExecLauncherCallbackData {
        String cmd;
        String[] args;
        RemoteFileAccessor stdIn;
        RemoteFileAccessor stdOut;
        RemoteFileAccessor stdErr;
        Properties environment;
        RemoteFileAccessor translatorNode;
        String translatorNodePath;
    }
}
