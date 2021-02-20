package org.rowland.jinixspi;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

public abstract class JinixFileInputStreamSP extends InputStream {

    public abstract FileDescriptor getFD() throws IOException;

    public abstract FileChannel getChannel();
}
