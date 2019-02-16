package org.rowland.jinix.io;

import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.RemoteFileHandle;
import org.rowland.jinix.nio.JinixFileChannel;
import org.rowland.jinix.terminal.TerminalBlockedOperationException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Created by rsmith on 11/27/2016.
 */
public class JinixFileOutputStream extends OutputStream {

    JinixFileDescriptor fd;
    JinixFileChannel channel;
    Set<StandardOpenOption> options;

    public JinixFileOutputStream(JinixFile file, boolean append) {

        try {
            options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            if (append) {
                options.add(StandardOpenOption.APPEND);
            } else {
                options.add(StandardOpenOption.TRUNCATE_EXISTING);
            }

            int pid = JinixRuntime.getRuntime().getPid();
            Object lookup = JinixRuntime.getRuntime().lookup(file.getCanonicalPath());
            if (lookup == null) {
                String filePath = file.getCanonicalPath();
                String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
                String directoryPath = filePath.substring(0, filePath.lastIndexOf('/'));
                lookup = JinixRuntime.getRuntime().lookup(directoryPath);
                if (lookup instanceof RemoteFileHandle) {
                    fd = new JinixFileDescriptor(((RemoteFileHandle) lookup).getParent().
                            getRemoteFileAccessor(pid, ((RemoteFileHandle) lookup).getPath() + "/" + fileName, options));
                    return;
                }
            } else {
                if (lookup instanceof RemoteFileHandle) {
                    fd = new JinixFileDescriptor(((RemoteFileHandle) lookup).getParent().
                            getRemoteFileAccessor(pid, ((RemoteFileHandle) lookup).getPath(), options));
                    return;
                }
            }
            throw new FileNotFoundException(file.getAbsolutePath());
        } catch (NoSuchFileException | FileAlreadyExistsException e) {
            throw new RuntimeException("Unexpected internal error", e); // This should never happen with options
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JinixFileOutputStream(JinixFileDescriptor fileDescriptor) {

        if (fileDescriptor == null) {
            throw new NullPointerException();
        }
        this.fd = fileDescriptor;
        this.fd.attach(this);
    }

    public final JinixFileDescriptor getFD() throws IOException {
        if (fd != null) {
            return fd;
        }
        throw new IOException();
    }

    public synchronized JinixFileChannel getChannel() {
        try {
            if (channel == null) {
                channel = JinixFileChannel.open(fd, options, this);
            }
            return channel;
        } catch (IOException e) {
            throw new RuntimeException("IOException opening JinixFileChanel", e);
        }
    }

    @Override
    public void write(int b) throws IOException {

        byte[] wb = new byte[] {(byte) b};

        try {
            fd.getHandle().write(JinixRuntime.getRuntime().getProcessGroupId(), wb);
        } catch (NonWritableChannelException e) {
            throw new IOException("Illegal attempt to write to a non-writable file descriptor");
        } catch (NoSuchObjectException e) {
            throw new IOException("JinixFileOutputStream: RemoteFileAccessor has been deleted");
        } catch (TerminalBlockedOperationException e) {
            write(b);
        } catch (RemoteException e) {
            throw new IOException("JinixFileOutputStream: Jinix server failure", e.getCause());
        }
    }


    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        byte[] wb = new byte[len];
        System.arraycopy(b, off, wb, 0, len);
        try {
            fd.getHandle().write(JinixRuntime.getRuntime().getProcessGroupId(), wb);
        } catch (NonWritableChannelException e) {
            throw new IOException("Illegal attempt to write to a non-writable file descriptor");
        } catch (NoSuchObjectException e) {
            throw new IOException("JinixFileOutputStream: RemoteFileAccessor has been deleted");
        } catch (TerminalBlockedOperationException e) {
            write(b, off, len);
        } catch (RemoteException e) {
            throw new IOException("JinixFileOutputStream: Jinix server failure", e.getCause());
        }
    }

    @Override
    public void flush() throws IOException {
        fd.getHandle().force(true);
    }

    @Override
    public void close() throws IOException {
        fd.close();
    }
}
