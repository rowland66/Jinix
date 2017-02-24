package org.rowland.jinix.fifo;

import org.rowland.jinix.naming.FileChannel;
import org.rowland.jinix.naming.FileNameSpace;

import java.io.Serializable;

/**
 * A pair of FileChannels that provide the ends of a pipe.
 */
public class FileChannelPair implements Serializable {

    private FileChannel input;
    private FileChannel output;

    public FileChannelPair(FileChannel inputFd, FileChannel outputFd) {
        input = inputFd;
        output = outputFd;
    }

    public FileChannel getInput() {
        return input;
    }

    public FileChannel getOutput() {
        return output;
    }
}
