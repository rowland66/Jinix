package org.rowland.jinix.lang;

import org.rowland.jinix.IllegalOperationException;
import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.fifo.FileChannelPair;
import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.io.JinixPipe;
import org.rowland.jinix.naming.NameSpace;
import org.rowland.jinix.proc.ProcessManager;
import org.rowland.jinix.terminal.TerminalAttributes;

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

    public enum StandardFileDescriptor {IN, OUT, ERROR};

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
     *
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
     * @return the process ID of the new process that is executing the command
     * @throws FileNotFoundException if the Jinix executable image cannot be located
     */
    public abstract int exec(String cmd, String[] args)
            throws FileNotFoundException, InvalidExecutableException;


    public abstract int exec(Properties env, String cmd, String[] args, int processGroupId,
                             JinixFileDescriptor stdin, JinixFileDescriptor stdout, JinixFileDescriptor stderr)
            throws FileNotFoundException, InvalidExecutableException;

    /**
     * Execute a Jinix executable image in a new process providing a customized environment,
     * input, output and error file descriptors.
     *
     * @param env
     * @param cmd the name of the Jinix executable to execute. The JINIX_PATH will be searched
     *            and the JINIX_PATH_EXT executable suffix's will be applied locate executable
     *            images.
     * @param args the arguments to provide to the executable images as a parameter to the
     *             main() method.
     * @param processGroupId -1 to set the processGroupId to the pid of the new process (ie. create a new process group),
     *                       0 to join the processGroup of the parent, any other value will be assigned directly as the processGroupId
     * @param sessionId -1 to set the sessionId to the pid of the new process (ie. create a new session), 0 to join
     *                  the session of the parent process.
     * @param stdin
     * @param stdout
     * @param stderr
     * @return the process ID of the new process that is executing the command
     * @throws FileNotFoundException
     * @throws InvalidExecutableException
     */
    public abstract int exec(Properties env, String cmd, String[] args, int processGroupId, int sessionId,
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
     * Execute the current Jinix executable image in a new process. This is not a true fork as
     * the memory address space will not be replicated. The JinixRuntime.isForkChild() method
     * will return true in the child process. The JinixFileDescriptor parameters are generally
     * obtained from a call to pipe(). This allows the parent to communication with its child.
     *
     * @param in
     * @param out
     * @param error
     * @return
     * @throws FileNotFoundException
     * @throws InvalidExecutableException
     */
    public abstract int fork(JinixFileDescriptor in, JinixFileDescriptor out, JinixFileDescriptor error) throws FileNotFoundException, InvalidExecutableException;

    /**
     * Was the current process started as a fork of a parent process. This method will only
     * return true once for any process. Once it is called, any subsequent calls return false.
     *
     * @return true if the current process is a fork of a parent process
     */
    public abstract boolean isForkChild();

    /**
     * Get the process identifier of the current process.
     *
     * @return the process ID
     */
    public abstract int getPid();

    /**
     * Get the process group identifier of the current process. If the process group id equals the pid, the process is
     * a process group leader.
     *
     * @return the Process Group ID
     */
    public abstract int getProcessGroupId();

    /**
     * Get the session identifier of the current process. If the session id equals the pid, the process is a session
     * leader.
     *
     * @return the Session ID
     */
    public abstract int getProcessSessionId();

    /**
     * Block the thread that calls this method until a child process is stopped or terminates.
     * If no child process exists, returns -1.
     *
     * @param nowait true if call should check for and return a pending ChildEvents, but not wait
     * @return the pid of the child process that triggered the return, or -1 if no child processes exist
     */
    public abstract ProcessManager.ChildEvent waitForChild(boolean nowait);

    /**
     * Get a Jinix file descriptor for one of the standard process files (IN, OUT and ERROR).
     *
     * @param sfd
     * @return
     */
    public abstract JinixFileDescriptor getStandardFileDescriptor(StandardFileDescriptor sfd);

    /**
     * Create a JinixPipe that can be used to transfer data between two processes.
     *
     * @return a new JinixPipe
     */
    public abstract JinixPipe pipe();

    /**
     * Send a ProcessManager.Signal to another process.
     *
     * @param pid
     * @param signal
     */
    public abstract void sendSignal(int pid, ProcessManager.Signal signal);

    /**
     * Send a ProcessManager.Signal to all of the processes in a process group.
     *
     * @param processGroupId
     * @param signal
     */
    public abstract void sendSignalProcessGroup(int processGroupId, ProcessManager.Signal signal);

    /**
     * Register a signal handler to receive Jinix signals.
     *
     * @param handler
     */
    public abstract void registerSignalHandler(ProcessSignalHandler handler);

    public abstract JinixFileDescriptor getTranslatorFile();

    public abstract String getTranslatorNodePath();

    public abstract void bindTranslator(Remote translator);

    /**
     * Register a Java native thread with the Jinix runtime. Only called internally, and calling with any thread
     * created in a Jinix program will be a NOOP since the thread is already registered.
     */
    public abstract void registerJinixThread(Thread t);

    /**
     * Set the process group ID for a Jinix process. The process group ID can only be set to ID of a process group in
     * the same session as the current process. -1 can be used to create a new process group with the current process as
     * the only member. A process group leader cannot join a new process group.
     *
     * @param processGroupId -1 to create a new process group with the ID of the current process's PID or any other valid
     *                       process group ID.
     */
    public abstract void setProcessGroupId(int processGroupId);

    /**
     * Create a new session if the calling process is not a process group leader.  The calling process is the leader
     * of the new session (i.e., its session ID is made the same as its process ID).The calling process also becomes
     * the process group leader of a new process group in the session (i.e., its process group ID is made the
     * same as its process ID).
     *
     * The calling process will be the only process in the new process group and in the new session.
     *
     * @throws IllegalOperationException
     */
    public abstract void setProcessSessionId();

    /**
     * Set the terminal ID for a Jinix process. Once the id is set to a value other than -1, it cannot be changed to any
     * value other than -1. When a deamon disassociated itself from a terminal, it can set its terminal ID to -1.
     *
     * @param terminalId -1 to disassociate the process from a terminal, any other value to associate the process with a terminal
     */
    public abstract void setProcessTerminalId(short terminalId);

    /**
     * Get the terminal ID for the Jinix process.
     *
     * @return the terminal ID
     */
    public abstract short getProcessTerminalId();

    public abstract void setForegroundProcessGroupId(int processGroupId);

    /**
     * Get the terminal attributes for a terminal. The terminal attributes control how the terminal line discipline
     * processes characters sent and received by the terminal device.
     *
     * @param terminalId id of the terminal
     * @return a TerminalAttributes object. The TerminalAtributes class is modeled after the unix termios strcture.
     */
    public abstract TerminalAttributes getTerminalAttributes(short terminalId);

    /**
     * Set the terminal attributes for a terminal.
     *
     * @param terminalId
     * @param terminalAttributes
     */
    public abstract void setTerminalAttributes(short terminalId, TerminalAttributes terminalAttributes);

    public abstract void exit(int status);
}
