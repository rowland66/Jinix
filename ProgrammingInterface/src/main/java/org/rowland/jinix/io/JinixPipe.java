package org.rowland.jinix.io;

import org.rowland.jinix.fifo.FileChannelPair;

/**
 * A pipe that provides FileDescriptors for input and output such that data written
 * to the output can be read from the input.
 */
public class JinixPipe {
    private FileChannelPair pair;


    public JinixPipe(FileChannelPair pair) {
        this.pair = pair;
    }

    public JinixFileDescriptor getInputFileDescriptor() {
        return new JinixFileDescriptor(pair.getInput());
    }

    public JinixFileDescriptor getOutputFileDescriptor() {
        return new JinixFileDescriptor(pair.getOutput());
    }
}
