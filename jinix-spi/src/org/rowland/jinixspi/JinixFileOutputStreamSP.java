package org.rowland.jinixspi;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

public abstract class JinixFileOutputStreamSP extends OutputStream {

    public abstract FileDescriptor getFD() throws IOException;

    public abstract FileChannel getChannel();
}
