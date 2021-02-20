package org.rowland.jinix.nio;

import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.RemoteFileAccessor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;

/**
 * A RemoteFileAccessor for the Jinix OS.
 */
public class JinixFileChannel extends FileChannel {

    private RemoteFileAccessor raf;

    // File access mode options(immutable)
    private Set<? extends OpenOption> options;
    private Object parent;

    // Lock for operations involving position and size
    private final Object positionLock = new Object();

    public static JinixFileChannel open(JinixFileDescriptor fd, Set<? extends OpenOption> options, Object parent)
        throws IOException {
        // Throws FileAlreadyExistsException with CREATE_NEW option
        return new JinixFileChannel(fd.getHandle(), options, parent);
    }

    /**
     * Get a JinixFileChannel from an existing filedescriptor. The channel will be open with the same
     * options that created the filedescriptor.
     *
     * @param fc the RemoteFileAccessor underlying channel
     * @param options the options that describe the mode in which the underlying RemoteFileAccessor was opened.
     * @param parent the object that called this method and provided the RemoteFileAccessor
     * @return
     * @throws IOException
     */
    private JinixFileChannel(RemoteFileAccessor fc, Set<? extends OpenOption> options, Object parent)
            throws IOException {
        super();
        raf = fc;
        this.options = options;
        this.parent = parent;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (!options.contains(StandardOpenOption.READ)) {
            throw new NonReadableChannelException();
        }
        synchronized (positionLock) {

            int len = dst.remaining();
            byte[] b = raf.read(JinixRuntime.getRuntime().getProcessGroupId(), len);
            if (b == null) {
                return -1;
            }
            dst.put(b);
            return b.length;
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ensureOpen();
        if (!options.contains(StandardOpenOption.WRITE)) {
            throw new NonWritableChannelException();
        }
        byte[] b = new byte[src.remaining()];
        src.get(b);
        raf.write(JinixRuntime.getRuntime().getProcessGroupId(), b);
        return b.length;
    }

    @Override
    public long position() throws IOException {
        return raf.getFilePointer();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        ensureOpen();
        if (newPosition < 0)
            throw new IllegalArgumentException();
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
        if ((offset < 0) || (length < 0) || (offset > dsts.length - length))
            throw new IndexOutOfBoundsException();
        ensureOpen();
        if (!options.contains(StandardOpenOption.READ)) {
            throw new NonReadableChannelException();
        }
        int count = offset + length;
        int i = offset;
        long totalBytes = 0;
        int len = dsts[i].limit();
        byte[] b = raf.read(JinixRuntime.getRuntime().getProcessGroupId(), len);
        if (b == null) {
            return -1;
        }
        while (i < count && b != null) {
            dsts[i++].put(b);
            len = dsts[i].limit();
            totalBytes += b.length;
            b = raf.read(JinixRuntime.getRuntime().getProcessGroupId(), len);
        }
        return totalBytes;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if ((offset < 0) || (length < 0) || (offset > srcs.length - length))
            throw new IndexOutOfBoundsException();
        ensureOpen();
        if (!options.contains(StandardOpenOption.WRITE))
            throw new NonWritableChannelException();
        int count = offset + length;
        int i = offset;
        long bytesWritten = 0;
        while (i < count) {
            byte[] b = new byte[srcs[i].remaining()];
            srcs[i++].get(b);
            bytesWritten += raf.write(JinixRuntime.getRuntime().getProcessGroupId(), b);
        }
        return bytesWritten;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        ensureOpen();
        raf.force(metaData);
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
        return new JinixFileLockImpl(this, position, size, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return new JinixFileLockImpl(this, position, size, shared);
    }

    @Override
    protected void implCloseChannel() throws IOException {
        raf.close();
    }

    private void ensureOpen() throws IOException {
        if (!isOpen())
            throw new ClosedChannelException();
    }

    public Object getParent() {
        return this.parent;
    }
}
