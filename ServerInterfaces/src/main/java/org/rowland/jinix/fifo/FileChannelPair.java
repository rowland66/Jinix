package org.rowland.jinix.fifo;

import org.rowland.jinix.naming.RemoteFileAccessor;

import java.io.Serializable;

/**
 * A pair of FileChannels that provide the ends of a pipe.
 */
public class FileChannelPair implements Serializable {

    private RemoteFileAccessor input;
    private RemoteFileAccessor output;

    public FileChannelPair(RemoteFileAccessor inputFd, RemoteFileAccessor outputFd) {
        input = inputFd;
        output = outputFd;
    }

    public RemoteFileAccessor getInput() {
        return input;
    }

    public RemoteFileAccessor getOutput() {
        return output;
    }
}
