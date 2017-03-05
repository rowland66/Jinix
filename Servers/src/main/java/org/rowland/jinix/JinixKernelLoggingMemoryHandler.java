package org.rowland.jinix;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;

/**
 * The Jinix kernel in memory logging Handler. This java.util.logging Handler
 * accumulates logRecords in a circular buffer. The LogServer makes the
 * logs available to the KernelLogDaemon that retrieves them through the LogServer
 * and writes them to a file in Jinix file system. If the daemon cannot keep up,
 * log records can be lost, but the kernel will not block or fail.
 */
public class JinixKernelLoggingMemoryHandler extends Handler {
    private final static int DEFAULT_SIZE = 1000;
    private int size;
    private LogRecord buffer[];
    int start, count;

    JinixKernelLoggingMemoryHandler() {
        this(DEFAULT_SIZE);
    }

    JinixKernelLoggingMemoryHandler(int logBufferSize) {
        if (logBufferSize > 0) {
            size = logBufferSize;
        } else {
            size = DEFAULT_SIZE;
        }
        LogManager manager = LogManager.getLogManager();
        setLevel(Level.ALL);

        buffer = new LogRecord[size];
        start = 0;
        count = 0;
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        int ix = (start+count)%buffer.length;
        buffer[ix] = record;
        if (count < buffer.length) {
            count++;
        } else {
            start++;
            start %= buffer.length;
        }
    }

    @Override
    public void flush() {
        // Noop as flushing of the kernel log is not possible
    }

    @Override
    public void close() throws SecurityException {
        // Noop as closing the kernel log is not possible
    }

    synchronized LogRecord[] getAvailableLogRecords() {
        List<LogRecord> ll = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int ix = (start+i)%buffer.length;
            LogRecord record = buffer[ix];
            ll.add(record);
        }
        // Empty the buffer.
        start = 0;
        count = 0;

        return ll.toArray(new LogRecord[ll.size()]);
    }
}
