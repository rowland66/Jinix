package org.rowland.jinix.nio;

import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.naming.LookupResult;
import org.rowland.jinix.naming.RemainingPath;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Set;

/**
 * A FileChannel for the Jinix OS.
 */
public class JinixFileChannel extends FileChannel {

    private org.rowland.jinix.naming.FileChannel raf;

    /**
     * Get a JinixFileChannel from an existing filedescriptor. The channel will be open with the same
     * options that created the filedescriptor.
     *
     * @param fd
     * @return
     * @throws IOException
     */
    public static JinixFileChannel open(JinixFileDescriptor fd)
            throws IOException {
        return new JinixFileChannel(fd.getHandle());
    }

    public static JinixFileChannel open(JinixFileDescriptor fd, Set<? extends OpenOption> options, FileAttribute<?>[] attrs)
        throws IOException {
        // Throws FileAlreadyExistsException with CREATE_NEW option
        org.rowland.jinix.naming.FileChannel raf = fd.getHandle();
        return new JinixFileChannel(raf);

    }

    JinixFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>[] attrs)
            throws IOException {
        super();
        try {
            // Throws FileAlreadyExistsException with CREATE_NEW option
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(
                    path.toAbsolutePath().toString());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            raf = fns.getFileChannel(
                    lookup.remainingPath,
                    options.toArray(new OpenOption[options.size()]));
        } catch (RemoteException e) {
            throw new IOException("IOException creating file channel", e.getCause());
        }
    }

    private JinixFileChannel(org.rowland.jinix.naming.FileChannel fc) {
        raf = fc;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int len = dst.remaining();
        byte[] b = raf.read(len);
        if (b == null) {
            return -1;
        }
        dst.put(b);
        return b.length;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        byte[] b = new byte[src.remaining()];
        src.get(b);
        raf.write(b);
        return b.length;
    }

    @Override
    public long position() throws IOException {
        return raf.getFilePointer();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        raf.seek(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return raf.length();
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        if (size >= size()) {
            if (position() > size) {
                position(size);
            }
            return this;
        }
        raf.setLength(size);
        return this;
    }


    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        int i = 0;
        long totalBytes = 0;
        int len = dsts[i].limit() - offset;
        byte[] b = raf.read(len);
        if (b == null) {
            return -1;
        }
        while (b != null) {
            dsts[i++].put(b, offset, len);
            len = dsts[i].limit();
            offset = 0;
            totalBytes += b.length;
            b = raf.read(len);
        }
        return totalBytes;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return 0;
    }

    @Override
    public void force(boolean metaData) throws IOException {

    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        return 0;
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        return 0;
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        position(position);
        return read(dst);
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        position(position);
        return write(src);
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        return null;
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        return null;
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return null;
    }

    @Override
    protected void implCloseChannel() throws IOException {
        raf.close();
    }
}
