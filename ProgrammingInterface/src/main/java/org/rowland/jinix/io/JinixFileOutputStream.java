package org.rowland.jinix.io;

import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.FileChannel;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.naming.LookupResult;
import org.rowland.jinix.naming.RemainingPath;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;

/**
 * Created by rsmith on 11/27/2016.
 */
public class JinixFileOutputStream extends OutputStream {

    JinixFileDescriptor fd;

    public JinixFileOutputStream(JinixFile file, boolean append) throws FileNotFoundException {

        try {
            OpenOption[] options = new OpenOption[append ? 3 : 2];
            options[0] = StandardOpenOption.WRITE;
            options[1] = StandardOpenOption.CREATE;
            if (append) {
                options[2] = StandardOpenOption.APPEND;
            }

            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(file.getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fd = new JinixFileDescriptor(fns.getFileChannel(lookup.remainingPath, options));
        } catch (NoSuchFileException | FileAlreadyExistsException e) {
            throw new RuntimeException("Unexpected internal error", e); // This should never happen with options
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JinixFileOutputStream(JinixFileDescriptor fileDescriptor) {
        this.fd = fileDescriptor;
        this.fd.attach(this);
    }

    @Override
    public void write(int b) throws IOException {

        byte[] wb = new byte[] {(byte) b};

        try {
            fd.getHandle().write(wb);
        } catch (NoSuchObjectException e) {
            System.err.println("JinixFileOutputStream: remote FileChannel has been deleted");
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        byte[] wb = new byte[len];
        System.arraycopy(b, off, wb, 0, len);
        try {
            fd.getHandle().write(wb);
        } catch (NoSuchObjectException e) {
            System.err.println("JinixFileOutputStream: remote FileChannel has been deleted");
        } catch (RemoteException e) {
            throw new IOException(e.getCause());
        }
    }

    @Override
    public void close() throws IOException {
        fd.close();
    }

    public final JinixFileDescriptor getFD() throws IOException {
        return fd;
    }

}
