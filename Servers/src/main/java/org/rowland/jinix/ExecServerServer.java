package org.rowland.jinix;

import org.rowland.jinix.exec.ExecLauncherData;
import org.rowland.jinix.exec.ExecServer;
import org.rowland.jinix.naming.*;
import org.rowland.jinix.proc.ProcessManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.util.*;

import static org.rowland.jinix.JinixKernel.rmiMode;

/**
 * The ExecServer is responsible for executing Jinix executable files. Conceptually, the ExecServer could handle
 * multiple executable file types. For not is only executable jar files are executable. The execute an executable
 * file, the ExecServer creates a new process and runs the ExecLauncher. Every new process is registered with the
 * ProcessManager. At the underlying OS level, all Jinix process created from Jinix executable files are children
 * of the ExecServer process.
 */
class ExecServerServer extends JinixKernelUnicastRemoteObject implements ExecServer {

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
    public int execTranslator(String cmd, String[] args, FileChannel translatorNode, String translatorNodePath)
            throws FileNotFoundException, RemoteException {
        return exec0(null, cmd, args, -1,
                null, null, null,
                translatorNode, translatorNodePath, "file://"+cmd);
    }

    @Override
    public int exec(Properties env,
                    String cmd,
                    String[] args,
                    int parentId,
                    FileChannel stdIn,
                    FileChannel stdOut,
                    FileChannel stdErr)
            throws FileNotFoundException, RemoteException {
        return exec0(env, cmd, args, parentId, stdIn, stdOut, stdErr, null, null, null);
    }

    private int exec0(Properties env,
                      String cmd,
                      String[] args,
                      int parentId,
                      FileChannel stdIn,
                      FileChannel stdOut,
                      FileChannel stdErr,
                      FileChannel translatorNode,
                      String translatorNodePath,
                      String codebaseURL)
            throws FileNotFoundException, RemoteException {
        Runtime runtime = Runtime.getRuntime();

        LookupResult lookup = this.ns.lookup(cmd);
        FileNameSpace fs = (FileNameSpace) lookup.remote;
        FileChannel cmdFd = null;
        try {
            try {
                cmdFd = fs.getFileChannel(lookup.remainingPath, StandardOpenOption.READ);
            } catch (FileAlreadyExistsException e) {
                throw new RuntimeException("Internal Error",e); // should never happen
            } catch (NoSuchFileException e) {
                throw new FileNotFoundException("File not found: '"+cmd+"'");
            }

            if (!isValidExecutable(cmdFd)) {
                throw new RemoteException("Invalid Jinix executable: " + cmd);
            }
        } finally {
            if (cmdFd != null) {
                cmdFd.close();
            }
        }

        final int pid = pm.registerProcess(parentId, cmd, args);

        String javaCmd = "java";
        if (javaHome != null) {
            javaCmd = javaHome +
                    File.separator + "bin" +
                    File.separator + "java";
        }
        List<String> cmdList = new ArrayList<>(16);
        cmdList.add(javaCmd);
        cmdList.add("-Xbootclasspath/p:" +
                "./lib/Runtime.jar" + File.pathSeparator +
                "./lib/ProgrammingInterface.jar" + File.pathSeparator +
                "./lib/ServerInterfaces.jar");
        cmdList.add("-Djava.nio.file.spi.DefaultFileSystemProvider=org.rowland.jinix.nio.JinixFileSystemProvider");
        //cmdList.add("-Dsun.misc.URLClassPath.debug=true");
        //cmdList.add("-Djava.security.debug=\"access,failure\"");
        if (codebaseURL != null) {
            //cmdList.add("-Djava.rmi.server.codebase="+codebaseURL);
            //cmdList.add("-Djava.rmi.server.logCalls=true");
            //cmdList.add("-Dsun.rmi.server.logLevel=VERBOSE");
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

        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-Xdebug") || args[i].startsWith("-Xrunjdwp")) {
                cmdList.add(args[i]);
                args[i] = null;
            }
        }

        args = compressCmdArgs(args);

        cmdList.add("org.rowland.jinix.exec.ExecLauncher");
        cmdList.add(Integer.toString(pid));

        if (rmiMode == JinixKernel.RMI_MODE.AFUNIX) {
            cmdList.add("rmi=AFUNIX");
        } else {
            cmdList.add("rmi=Default");
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
            //registerOSProcess(pid, osProcess); //TODO: figure out how to handle the OS process as Process is not serializable

            (new Thread(new Runnable() {
                public void run() {
                    try {
                        System.out.println("Waiting for osProcess: "+pid);
                        osProcess.waitFor();
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        System.out.println("Deregistering process: " +pid );
                        pm.deRegisterProcess(pid);
                    } catch (RemoteException e) {
                        System.err.println("ExecServer: RemoteExeception deregistering process: "+pid);
                    }
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
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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
            e.printStackTrace();
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

    private boolean isValidExecutable(FileChannel cmd) {
        return true;
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
        FileChannel stdIn;
        FileChannel stdOut;
        FileChannel stdErr;
        Properties environment;
        FileChannel translatorNode;
        String translatorNodePath;
    }
}
