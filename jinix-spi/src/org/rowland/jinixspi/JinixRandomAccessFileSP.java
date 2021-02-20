package org.rowland.jinixspi;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;

public interface JinixRandomAccessFileSP {

    FileDescriptor getFD() throws IOException;

    FileChannel getChannel();

    int read() throws IOException;

    int read(byte b[], int off, int len) throws IOException;

    void readFully(byte b[], int off, int len) throws IOException;

    int skipBytes(int n) throws IOException;

    void write(int b) throws IOException;

    void write(byte b[], int off, int len) throws IOException;

    long getFilePointer() throws IOException;

    void seek(long pos) throws IOException;

    long length() throws IOException;

    void setLength(long length) throws IOException;

    void close() throws IOException;
}
