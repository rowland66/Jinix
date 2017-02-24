package org.rowland.jinix.io;

import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.FileChannel;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.naming.LookupResult;
import org.rowland.jinix.naming.RemainingPath;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by rsmith on 11/26/2016.
 */
public class JinixFileInputStream extends InputStream {

    private final JinixFileDescriptor fd;

    public JinixFileInputStream(JinixFile file) throws FileNotFoundException {
        try {
            OpenOption[] options = new OpenOption[1];
            options[0] = StandardOpenOption.READ;

            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(file.getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fd = new JinixFileDescriptor(fns.getFileChannel(lookup.remainingPath, options));
            fd.attach(this);
        } catch (FileAlreadyExistsException e) {
            throw new RuntimeException("Internal error", e); // Should never happen when opening channel in READ mode.
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException("Unable to find file: " + file.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JinixFileInputStream(JinixFileDescriptor fileDescriptor) throws FileNotFoundException {
        fd = fileDescriptor;
        fd.attach(this);
    }

    @Override
    public int read() throws IOException {
        byte[] b = fd.getHandle().read(1);
        if (b != null && b.length == 1) {
            return (int) b[0];
        } else {
            return -1;
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

        byte[] rb = fd.getHandle().read(len);

        if (rb == null) {
            return -1;
        }

        System.arraycopy(rb, 0, b, off, rb.length);
        return rb.length;
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

    public final JinixFileDescriptor getFD() {
        return fd;
    }

}
