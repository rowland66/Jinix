package org.rowland.jinix.nio;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class JinixFileLockImpl extends FileLock {

    protected JinixFileLockImpl(FileChannel channel, long position, long size, boolean shared) {
        super(channel, position, size, shared);
    }

    protected JinixFileLockImpl(AsynchronousFileChannel channel, long position, long size, boolean shared) {
        super(channel, position, size, shared);
    }

    @Override
    public Channel acquiredBy() {
        return super.acquiredBy();
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void release() throws IOException {

    }
}
