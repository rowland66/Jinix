package org.rowland.jinix.exec;

import org.rowland.jinix.io.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.JinixSystem;
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
            this.pid = JinixRuntime.getRuntime().exec(environment, cmd, args, 0, child_fds[0], child_fds[1], child_fds[2]);
            runningChildrenMap.put(this.pid, this);
            initStreams(parent_fds);
        } catch (InvalidExecutableException e) {
            exitCode = 1;
            hasExited = true;
            return;
        }
    }

    private void initStreams(JinixFileDescriptor[] fds) throws IOException {
        stdin = (fds[0] == null) ?
                JinixProcess.NullOutputStream.INSTANCE :
                new ProcessPipeOutputStream(fds[0]);

        stdout = (fds[1] == null) ?
                JinixProcess.NullInputStream.INSTANCE :
                new ProcessPipeInputStream(fds[1]);

        stderr = (fds[2] == null) ?
                JinixProcess.NullInputStream.INSTANCE :
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
                                JinixProcess.NullInputStream.INSTANCE :
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
                this.out = JinixProcess.NullOutputStream.INSTANCE;
            }
        }
    }

    static class NullOutputStream extends OutputStream {
        static final JinixProcess.NullOutputStream INSTANCE = new JinixProcess.NullOutputStream();

        private NullOutputStream() {
        }

        public void write(int b) throws IOException {
            throw new IOException("Stream closed");
        }
    }

    static class NullInputStream extends InputStream {
        static final JinixProcess.NullInputStream INSTANCE = new JinixProcess.NullInputStream();

        private NullInputStream() {
        }

        public int read() {
            return -1;
        }

        public int available() {
            return 0;
        }
    }

    // Only for use by ProcessBuilder.start()
    static Process start(String[] cmdarray,
                         java.util.Map<String, String> environment,
                         String dir,
                         ProcessBuilder.Redirect[] redirects,
                         boolean redirectErrorStream)
            throws IOException {
        assert cmdarray != null && cmdarray.length > 0;

        // Convert arguments to a contiguous block; it's easier to do
        // memory management in Java than in C.

        JinixFileDescriptor[] child_fds, parent_fds;

        JinixFileInputStream f0 = null;
        JinixFileOutputStream f1 = null;
        JinixFileOutputStream f2 = null;

        try {
            if (redirects == null) {
                redirects = new ProcessBuilder.Redirect[]{ProcessBuilder.Redirect.PIPE, ProcessBuilder.Redirect.PIPE, ProcessBuilder.Redirect.PIPE};
            }

            child_fds = new JinixFileDescriptor[3];
            parent_fds = new JinixFileDescriptor[3];

            if (redirects[0] == ProcessBuilder.Redirect.PIPE) {
                JinixPipe pipe = JinixRuntime.getRuntime().pipe();
                child_fds[0] = pipe.getInputFileDescriptor();
                parent_fds[0] = pipe.getOutputFileDescriptor();
            } else if (redirects[0] == ProcessBuilder.Redirect.INHERIT) {
                child_fds[0] = JinixRuntime.getRuntime().getStandardFileDescriptor(JinixRuntime.StandardFileDescriptor.IN);
                parent_fds[0] = null;
            } else {
                f0 = new JinixFileInputStream(new JinixFile(redirects[0].file().getPath()));
                child_fds[0] = f0.getFD();
                parent_fds[0] = null;
            }

            if (redirects[1] == ProcessBuilder.Redirect.PIPE) {
                JinixPipe pipe = JinixRuntime.getRuntime().pipe();
                child_fds[1] = pipe.getOutputFileDescriptor();
                parent_fds[1] = pipe.getInputFileDescriptor();
            } else if (redirects[1] == ProcessBuilder.Redirect.INHERIT) {
                child_fds[1] = JinixRuntime.getRuntime().getStandardFileDescriptor(JinixRuntime.StandardFileDescriptor.OUT);
                parent_fds[1] = null;
            } else {
                f1 = new JinixFileOutputStream(new JinixFile(redirects[1].file().getPath()),
                        redirects[1].append());
                child_fds[1] = f1.getFD();
                parent_fds[1] = null;
            }

            if (redirects[2] == ProcessBuilder.Redirect.PIPE) {
                JinixPipe pipe = JinixRuntime.getRuntime().pipe();
                child_fds[2] = pipe.getOutputFileDescriptor();
                parent_fds[2] = pipe.getInputFileDescriptor();
            } else if (redirects[2] == ProcessBuilder.Redirect.INHERIT) {
                child_fds[2] = JinixRuntime.getRuntime().getStandardFileDescriptor(JinixRuntime.StandardFileDescriptor.ERROR);
                parent_fds[2] = null;
            } else {
                f2 = new JinixFileOutputStream(new JinixFile(redirects[2].file().getPath()),
                        redirects[2].append());
                child_fds[2] = f2.getFD();
                parent_fds[2] = null;
            }

            String cmd = cmdarray[0];
            String[] args = new String[cmdarray.length - 1];
            for (int i = 0; i < args.length; i++) {
                args[i] = cmdarray[i + 1];
            }

            Properties env = JinixSystem.getJinixProperties();
            if (environment != null) {
                for (Map.Entry<String, String> envEntry : environment.entrySet()) {
                    env.setProperty(envEntry.getKey(), envEntry.getValue());
                }
            }

            Process rtrnProc = new JinixProcess(cmd, args, env, dir, child_fds, parent_fds, redirectErrorStream);

            // Close the child side of any pipes since JinixRuntime.exec() duplicates the fds that it is passed
            for (int i=0; i<3; i++) {
                if (redirects[i] == ProcessBuilder.Redirect.PIPE) {
                    child_fds[i].close();
                }
            }

            return rtrnProc;
        } finally {
            // In theory, close() can throw IOException
            // (although it is rather unlikely to happen here)
            try {
                if (f0 != null) f0.close();
            } finally {
                try {
                    if (f1 != null) f1.close();
                } finally {
                    if (f2 != null) f2.close();
                }
            }
        }
    }
}
