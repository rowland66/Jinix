package org.rowland.jinix.lang;

import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.fifo.FileChannelPair;
import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.io.JinixPipe;
import org.rowland.jinix.naming.NameSpace;
import org.rowland.jinix.proc.ProcessManager;

import javax.naming.Context;
import java.io.FileNotFoundException;
import java.rmi.Remote;
import java.util.Properties;

/**
 * The JinixRuntime class provides and interface to the Jinix kernel and provides services to
 * Jinix programs. Programs do not need to access the JinixRuntime as Jinix kernel services are
 * also available though other Java API's (eg java.io and java.nio). Programs that use the
 * JinixRuntime class will not be portable to traditional Java runtime environments.
 */
public abstract class JinixRuntime {

    public static final String JINIX_ENV_PWD = "jinix.pwd";
    public static final String JINIX_PATH = "jinix.path";
    public static final String JINIX_LIBRARY_PATH = "jinix.library.path";
    public static final String JINIX_PATH_EXT = "jinix.path.ext";
    public static final String JINIX_FORK = "jinix.fork";

    private static JinixRuntime jinixRuntimeSingleton = null;

    /**
     * Get the JinixRuntime for a process. The JinixRuntime provides access to Jinix kernel services.
     * Programs that use the JinixRuntime will not be portable.
     *
     * @return
     */
    public static synchronized JinixRuntime getRuntime() {
        return jinixRuntimeSingleton;
    }

    /**
     * The Jinix ExecLauncher sets the Jinix Runtime for every Jinix process. Once it has been set,
     * it cannot be changed. This method should never be called by Jinix programs, and is noop
     * method.
     *
     * @param runtime
     */
    public static void setJinixRuntime(JinixRuntime runtime) {

        if (jinixRuntimeSingleton == null) {
            jinixRuntimeSingleton = runtime;
        }
    }

    /**
     * Get the Jinix system's root NameSpace. All resources in a Jinix system are located through
     * the root NameSpace.
     *
     * @return the root NameSpace
     */
    public abstract NameSpace getRootNamespace();

    /**
     * Get the Jinix systems root NameSpace wrapped in javax.naming.Context interface.
     * @return
     */
    public abstract Context getNamingContext();

    /**
     * Execute a Jinix executable image in a new process.
     *
     * @param cmd the name of the Jinix executable to execute. The JINIX_PATH will be searched
     *            and the JINIX_PATH_EXT executable suffix's will be applied locate executable
     *            images.
     * @param args the arguments to provide to the executable images as a parameter to the
     *             main() method.
     * @return the Jinix process ID of the new process.
     * @throws FileNotFoundException if the Jinix executable image cannot be located
     */
    public abstract int exec(String cmd, String[] args)
            throws FileNotFoundException, InvalidExecutableException;

    /**
     * Execute a Jinix executable image in a new process providing a customized environment,
     * input, output and error file descriptors.
     *
     * @param env
     * @param cmd
     * @param args
     * @param stdin
     * @param stdout
     * @param stderr
     * @return
     * @throws FileNotFoundException
     * @throws InvalidExecutableException
     */
    public abstract int exec(Properties env, String cmd, String[] args,
                             JinixFileDescriptor stdin, JinixFileDescriptor stdout, JinixFileDescriptor stderr)
            throws FileNotFoundException, InvalidExecutableException;

    /**
     * Execute the current Jinix executable image in a new process. This is not a true fork as
     * the memory address space will not be replicated. The JinixRuntime.isForkChild() method
     * will return true in the child process.
     *
     * @return the Jinix process ID of the new process.
     */
    public abstract int fork() throws FileNotFoundException, InvalidExecutableException;

    /**
     * Was the current process started as a fork of a parent process.
     *
     * @return true if the current process is a fork of a parent process
     */
    public abstract boolean isForkChild();

    public abstract int getPid();

    public abstract int waitForChild();

    public abstract JinixPipe pipe();

    public abstract void sendSignal(int pid, ProcessManager.Signal signal);

    public abstract void registerSignalHandler(ProcessSignalHandler handler);

    public abstract JinixFileDescriptor getTranslatorFile();

    public abstract void bindTranslator(Remote translator);


}
