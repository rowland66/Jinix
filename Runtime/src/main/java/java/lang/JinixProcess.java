package java.lang;

import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.io.JinixFileInputStream;
import org.rowland.jinix.io.JinixFileOutputStream;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.proc.ProcessManager;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class JinixProcess extends Process {

    private static Map<Integer, JinixProcess> runningChildrenMap = new HashMap<>();

    private int pid;
    private volatile int exitCode;
    private volatile boolean hasExited;
    private OutputStream stdin;
    private InputStream  stdout;
    private InputStream  stderr;

    JinixProcess(String cmd,
                 String[] args,
                 Properties environment,
                 String dir,
                 JinixFileDescriptor[] child_fds,
                 JinixFileDescriptor[] parent_fds,
                 final boolean redirectErrorStream)
            throws IOException {

        try {
            if (dir != null) {
                environment.setProperty(JinixRuntime.JINIX_ENV_PWD, dir);
            }
            ChildListenerDeamon.startDeamon(); // This will only start the deamon if it is not already started
            int progGrpId = JinixRuntime.getRuntime().getProcessGroupId();
            this.pid = JinixRuntime.getRuntime().exec(environment, cmd, args, progGrpId, child_fds[0], child_fds[1], child_fds[2]);
            runningChildrenMap.put(this.pid, this);
            initStreams(parent_fds);
        } catch (InvalidExecutableException e) {
            exitCode = 1;
            hasExited = true;
            return;
        }
    }

    void initStreams(JinixFileDescriptor[] fds) throws IOException {
        stdin = (fds[0] == null) ?
                ProcessBuilder.NullOutputStream.INSTANCE :
                new ProcessPipeOutputStream(fds[0]);

        stdout = (fds[1] == null) ?
                ProcessBuilder.NullInputStream.INSTANCE :
                new ProcessPipeInputStream(fds[1]);

        stderr = (fds[2] == null) ?
                ProcessBuilder.NullInputStream.INSTANCE :
                new ProcessPipeInputStream(fds[2]);
    }

    @Override
    public synchronized int waitFor() throws InterruptedException {
        if (hasExited) return exitCode;
        do {
            // Round up to next millisecond
            wait();
            if (hasExited) {
                return exitCode;
            }
        } while (true);

    }

    @Override
    public synchronized boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {

        if (hasExited) return true;
        if (timeout <= 0) return false;

        long remainingNanos = unit.toNanos(timeout);
        long deadline = System.nanoTime() + remainingNanos;

        do {
            // Round up to next millisecond
            wait(TimeUnit.NANOSECONDS.toMillis(remainingNanos + 999_999L));
            if (hasExited) {
                return true;
            }
            remainingNanos = deadline - System.nanoTime();
        } while (remainingNanos > 0);
        return hasExited;
    }

    @Override
    public OutputStream getOutputStream() {
        return stdin;
    }

    @Override
    public InputStream getInputStream() {
        return stdout;
    }

    @Override
    public InputStream getErrorStream() {
        return stderr;
    }

    @Override
    public int exitValue() {
        if (hasExited) {
            return exitCode;
        }
        throw new IllegalThreadStateException();
    }

    @Override
    public void destroy() {
        JinixRuntime.getRuntime().sendSignal(pid, ProcessManager.Signal.TERMINATE);
    }

    @Override
    public Process destroyForcibly() {
        JinixRuntime.getRuntime().sendSignal(pid, ProcessManager.Signal.KILL);
        return this;
    }

    private static class ChildListenerDeamon extends Thread {

        private static ChildListenerDeamon childListenerDeamon;

        private static synchronized void startDeamon() {
            if (childListenerDeamon == null) {
                ThreadGroup tg = Thread.currentThread().getThreadGroup();
                while (tg.getParent() != null) tg = tg.getParent();
                ThreadGroup systemThreadGroup = tg;

                childListenerDeamon = new ChildListenerDeamon(systemThreadGroup);
                childListenerDeamon.start();
            }
        }

        private ChildListenerDeamon(ThreadGroup threadGroup) {
            super(threadGroup, "ChildListenerDeamon");
            setDaemon(true);
        }

        @Override
        public void run() {
            while(true) {
                ProcessManager.ChildEvent event = JinixRuntime.getRuntime().waitForChild(false);
                if (event.getState() == ProcessManager.ProcessState.SHUTDOWN) {
                    JinixProcess jp = null;
                    synchronized (runningChildrenMap) {
                        jp = runningChildrenMap.remove(event.getPid());
                    }
                    if (jp != null) {
                        synchronized (jp) {
                            jp.hasExited = true;
                            jp.exitCode = event.getExitStatus();
                            jp.notifyAll();
                        }

                        if (jp.stdout instanceof ProcessPipeInputStream)
                            ((ProcessPipeInputStream) jp.stdout).processExited();

                        if (jp.stderr instanceof ProcessPipeInputStream)
                            ((ProcessPipeInputStream) jp.stderr).processExited();

                        if (jp.stdin instanceof ProcessPipeOutputStream)
                            ((ProcessPipeOutputStream) jp.stdin).processExited();
                    }
                }
            }
        }
    }

    /**
     * A buffered input stream for a subprocess pipe file descriptor
     * that allows the underlying file descriptor to be reclaimed when
     * the process exits, via the processExited hook.
     *
     * This is tricky because we do not want the user-level InputStream to be
     * closed until the user invokes close(), and we need to continue to be
     * able to read any buffered data lingering in the OS pipe buffer.
     */
    private static class ProcessPipeInputStream extends BufferedInputStream {
        private final Object closeLock = new Object();

        ProcessPipeInputStream(JinixFileDescriptor fd) {
            super(new JinixFileInputStream(fd));
        }
        private static byte[] drainInputStream(InputStream in)
                throws IOException {
            int n = 0;
            int j;
            byte[] a = null;
            while ((j = in.available()) > 0) {
                a = (a == null) ? new byte[j] : Arrays.copyOf(a, n + j);
                n += in.read(a, n, j);
            }
            return (a == null || n == a.length) ? a : Arrays.copyOf(a, n);
        }

        /** Called by the process reaper thread when the process exits. */
        synchronized void processExited() {
            synchronized (closeLock) {
                try {
                    InputStream in = this.in;
                    // this stream is closed if and only if: in == null
                    if (in != null) {
                        byte[] stragglers = drainInputStream(in);
                        in.close();
                        this.in = (stragglers == null) ?
                                ProcessBuilder.NullInputStream.INSTANCE :
                                new ByteArrayInputStream(stragglers);
                    }
                } catch (IOException ignored) {}
            }
        }

        @Override
        public void close() throws IOException {
            // BufferedInputStream#close() is not synchronized unlike most other
            // methods. Synchronizing helps avoid race with processExited().
            synchronized (closeLock) {
                super.close();
            }
        }
    }

    /**
     * A buffered output stream for a subprocess pipe file descriptor
     * that allows the underlying file descriptor to be reclaimed when
     * the process exits, via the processExited hook.
     */
    private static class ProcessPipeOutputStream extends BufferedOutputStream {
        ProcessPipeOutputStream(JinixFileDescriptor fd) {
            super(new JinixFileOutputStream(fd));
        }

        /** Called by the process reaper thread when the process exits. */
        synchronized void processExited() {
            OutputStream out = this.out;
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // We know of no reason to get an IOException, but if
                    // we do, there's nothing else to do but carry on.
                }
                this.out = ProcessBuilder.NullOutputStream.INSTANCE;
            }
        }
    }
}
