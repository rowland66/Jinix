package org.rowland.jinix.io;

import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.naming.LookupResult;
import org.rowland.jinix.nio.JinixFileChannel;
import org.rowland.jinix.terminal.TerminalBlockedOperationException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.NonReadableChannelException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Created by rsmith on 11/26/2016.
 */
public class JinixFileInputStream extends InputStream {

    private static final Set<? extends OpenOption> inputStreamOpenOptionSet = Collections.unmodifiableSet(EnumSet.of(StandardOpenOption.READ));

    private final JinixFileDescriptor fd;
    private JinixFileChannel channel;

    public JinixFileInputStream(JinixFile file) throws FileNotFoundException {
        try {
            int pid = JinixRuntime.getRuntime().getPid();
            LookupResult lookup = JinixRuntime.getRuntime().lookup(file.getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fd = new JinixFileDescriptor(fns.getRemoteFileAccessor(pid, lookup.remainingPath, inputStreamOpenOptionSet));
            fd.attach(this);
        } catch (FileAlreadyExistsException e) {
            throw new RuntimeException("Internal error", e); // Should never happen when opening channel in READ mode.
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException("Unable to find file: " + file.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JinixFileInputStream(JinixFileDescriptor fileDescriptor) {
        fd = fileDescriptor;
        fd.attach(this);
    }

    public final JinixFileDescriptor getFD() {
        return fd;
    }

    public synchronized JinixFileChannel getChannel() {
        try {
            if (channel == null) {
                channel = JinixFileChannel.open(fd, inputStreamOpenOptionSet, this);
            }
            return channel;
        } catch (IOException e) {
            throw new RuntimeException("IOException opening JinixFileChannel", e);
        }
    }

    @Override
    public int read() throws IOException {
        try {
            byte[] b = fd.getHandle().read(JinixRuntime.getRuntime().getProcessGroupId(), 1);
            if (b != null && b.length == 1) {
                return b[0] & 0xff;  // This is the only way to convert a byte to an int with negative numbers for values over 128
            } else {
                return -1;
            }
        } catch (NonReadableChannelException e) {
            throw new IOException("Illegal attempt to read from a non-readable file descriptor");
        } catch (NoSuchObjectException e) {
            throw new IOException("JinixFileInputStream: RemoteFileAccessor has been deleted");
        } catch (TerminalBlockedOperationException e) {
            /**
            synchronized (JinixFileDescriptor.blockedTerminalOperationSynchronizationObject) {
                try {
                    JinixFileDescriptor.blockedTerminalOperationSynchronizationObject.wait();
                } catch (InterruptedException e1) {
                    return -1; // Should not happen, but not sure what to do here.
                }
            } */
            return read();
        } catch (RemoteException e) {
            throw new IOException("JinixFileInputStream: Jinix server failure", e.getCause());
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {

        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        try {
            byte[] rb = fd.getHandle().read(JinixRuntime.getRuntime().getProcessGroupId(), len);

            if (rb == null) {
                return -1;
            }

            System.arraycopy(rb, 0, b, off, rb.length);
            return rb.length;
        } catch (NonReadableChannelException e) {
            throw new IOException("Illegal attempt to read from a non-readable file descriptor");
        } catch (NoSuchObjectException e) {
            throw new IOException("JinixFileInputStream: RemoteFileAccessor has been deleted");
        } catch (TerminalBlockedOperationException e) {
            /**
            synchronized (JinixFileDescriptor.blockedTerminalOperationSynchronizationObject) {
                try {
                    JinixFileDescriptor.blockedTerminalOperationSynchronizationObject.wait();
                } catch (InterruptedException e1) {
                    return -1;
                }
            } */
            return read(b, off, len);
        } catch (RemoteException e) {
            throw new IOException("JinixFileInputStream: Jinix server failure", e.getCause());
        }
    }

    @Override
    public long skip(long n) throws IOException {
        return fd.getHandle().skip(n);
    }

    @Override
    public int available() throws IOException {
        return fd.getHandle().available();
    }

    @Override
    public void close() throws IOException {
        fd.close();
    }
}
