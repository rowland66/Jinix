package org.rowland.jinix.io;

import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.naming.LookupResult;
import org.rowland.jinix.nio.JinixFileChannel;

import java.io.*;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Created by rsmith on 12/23/2016.
 */
public class JinixRandomAccessFile {

    private JinixFileDescriptor fd;
    private JinixFileChannel channel = null;

    Set<StandardOpenOption> options;

    public JinixRandomAccessFile(String name, String mode)
            throws FileNotFoundException {
        this(name != null ? new JinixFile(name) : null, mode);
    }

    public JinixRandomAccessFile(JinixFile file, String mode)
            throws FileNotFoundException {
        String name = (file != null ? file.getPath() : null);

        boolean modeStringError = false;
        options = EnumSet.noneOf(StandardOpenOption.class);
        if (mode.equals("r"))
            options.add(StandardOpenOption.READ);
        else if (mode.startsWith("rw")) {
            options.add(StandardOpenOption.READ);
            options.add(StandardOpenOption.WRITE);
            if (mode.length() > 2) {
                if (mode.equals("rws"))
                    options.add(StandardOpenOption.SYNC);
                else if (mode.equals("rwd"))
                    options.add(StandardOpenOption.DSYNC);
                else
                    modeStringError = true;
            }
        } else {
            modeStringError = true;
        }
        if (modeStringError)
            throw new IllegalArgumentException("Illegal mode \"" + mode
                    + "\" must be one of "
                    + "\"r\", \"rw\", \"rws\","
                    + " or \"rwd\"");

        if (name == null) {
            throw new NullPointerException();
        }

        try {
            int pid = JinixRuntime.getRuntime().getPid();
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(file.getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fd = new JinixFileDescriptor(fns.getRemoteFileAccessor(pid, lookup.remainingPath, options));
        } catch (NoSuchFileException | FileAlreadyExistsException e) {
            throw new FileNotFoundException("File: "+file.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the opaque file descriptor object associated with this
     * stream.
     *
     * @return     the file descriptor object associated with this stream.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FileDescriptor
     */
    public final JinixFileDescriptor getFD() throws IOException {
        if (fd != null) {
            return fd;
        }
        throw new IOException();
    }

    /**
     * Returns the unique {@link java.nio.channels.FileChannel RemoteFileAccessor}
     * object associated with this file.
     *
     * <p> The {@link java.nio.channels.FileChannel#position()
     * position} of the returned channel will always be equal to
     * this object's file-pointer offset as returned by the {@link
     * #getFilePointer getFilePointer} method.  Changing this object's
     * file-pointer offset, whether explicitly or by reading or writing bytes,
     * will change the position of the channel, and vice versa.  Changing the
     * file's length via this object will change the length seen via the file
     * channel, and vice versa.
     *
     * @return  the file channel associated with this file
     *
     * @since 1.4
     * @spec JSR-51
     */
    public synchronized final JinixFileChannel getChannel() {
        try {
            if (channel == null) {
                channel = JinixFileChannel.open(fd, options, this);
            }
            return channel;
        } catch (IOException e) {
            throw new RuntimeException("IOException opening JinixFileChanel", e);
        }
    }

    public int read() throws IOException {
        try {
            byte[] b = fd.getHandle().read(JinixRuntime.getRuntime().getProcessGroupId(), 1);
            return (int) b[0];
        } catch (NonReadableChannelException e) {
            throw new IOException("Illegal attempt to read from a non-readable file descriptor");
        } catch (NoSuchObjectException e) {
            throw new IOException("JinixRandomAccessFile: RemoteFileAccessor has been deleted");
        } catch (RemoteException e) {
            throw new IOException("JinixRandomAccessFile: Jinix server failure", e.getCause());
        }
    }

    public int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        try {
            byte[] rb = fd.getHandle().read(JinixRuntime.getRuntime().getProcessGroupId(), len);

            if (rb == null) {
                return -1;
            }

            System.arraycopy(rb, 0, b, off, rb.length);
            return rb.length;
        } catch (NonReadableChannelException e) {
            throw new IOException("Illegal attempt to read from a non-readable file descriptor");
        } catch (NoSuchObjectException e) {
            throw new IOException("JinixRandomAccessFile: RemoteFileAccessor has been deleted");
        } catch (RemoteException e) {
            throw new IOException("JinixRandomAccessFile: Jinix server failure", e.getCause());
        }
    }

    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    public final void readFully(byte b[]) throws IOException {
        readFully(b, 0, b.length);
    }

    public final void readFully(byte b[], int off, int len) throws IOException {
        int n = 0;
        do {
            int count = this.read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        } while (n < len);
    }

    public int skipBytes(int n) throws IOException {
        long pos;
        long len;
        long newpos;

        if (n <= 0) {
            return 0;
        }
        pos = getFilePointer();
        len = length();
        newpos = pos + n;
        if (newpos > len) {
            newpos = len;
        }
        seek(newpos);

        /* return the actual number of bytes skipped */
        return (int) (newpos - pos);
    }

    public void write(int b) throws IOException {

        try {
            byte[] wb = new byte[1];
            wb[0] = (byte) b;
            fd.getHandle().write(JinixRuntime.getRuntime().getProcessGroupId(), wb);
        } catch (NonWritableChannelException e) {
            throw new IOException("Illegal attempt to write to a non-writable file descriptor");
        } catch (NoSuchObjectException e) {
            throw new IOException("JinixRandomAccessFile: RemoteFileAccessor has been deleted");
        } catch (RemoteException e) {
            throw new IOException("JinixRandomAccessFile: Jinix server failure", e.getCause());
        }
    }

    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte b[], int off, int len) throws IOException {
        byte[] wb;
        if (off == 0 && b.length == len) {
            wb = b;
        } else {
            wb = new byte[len];
            System.arraycopy(b, off, wb, 0, len);
        }
        try {
            fd.getHandle().write(JinixRuntime.getRuntime().getProcessGroupId(), wb);
        } catch (NonWritableChannelException e) {
            throw new IOException("Illegal attempt to write to a non-writable file descriptor");
        } catch (NoSuchObjectException e) {
            throw new IOException("JinixRandomAccessFile: RemoteFileAccessor has been deleted");
        } catch (RemoteException e) {
            throw new IOException("JinixRandomAccessFile: Jinix server failure", e.getCause());
        }
    }

    public long getFilePointer() throws IOException {
        return fd.getHandle().getFilePointer();
    }

    public void seek(long pos) throws IOException {
        if (pos < 0) {
            throw new IOException("Negative seek offset");
        } else {
            fd.getHandle().seek(pos);
        }
    }

    public long length() throws IOException {
        return fd.getHandle().length();
    }

    public void setLength(long newLength) throws IOException {
        fd.getHandle().setLength(newLength);
    }

    public void close() throws IOException {
        fd.close();
    }

    public final boolean readBoolean() throws IOException {
        int ch = this.read();
        if (ch < 0)
            throw new EOFException();
        return (ch != 0);
    }

    public final byte readByte() throws IOException {
        int ch = this.read();
        if (ch < 0)
            throw new EOFException();
        return (byte) (ch);
    }

    public final int readUnsignedByte() throws IOException {
        int ch = this.read();
        if (ch < 0)
            throw new EOFException();
        return ch;
    }

    public final short readShort() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (short) ((ch1 << 8) + (ch2 << 0));
    }

    public final int readUnsignedShort() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (ch1 << 8) + (ch2 << 0);
    }

    public final char readChar() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (char) ((ch1 << 8) + (ch2 << 0));
    }

    public final int readInt() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        int ch3 = this.read();
        int ch4 = this.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public final long readLong() throws IOException {
        return ((long) (readInt()) << 32) + (readInt() & 0xFFFFFFFFL);
    }

    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public final String readLine() throws IOException {
        StringBuffer input = new StringBuffer();
        int c = -1;
        boolean eol = false;

        while (!eol) {
            switch (c = read()) {
                case -1:
                case '\n':
                    eol = true;
                    break;
                case '\r':
                    eol = true;
                    long cur = getFilePointer();
                    if ((read()) != '\n') {
                        seek(cur);
                    }
                    break;
                default:
                    input.append((char)c);
                    break;
            }
        }

        if ((c == -1) && (input.length() == 0)) {
            return null;
        }
        return input.toString();
    }

    public final void writeBoolean(boolean v) throws IOException {
        write(v ? 1 : 0);
        //written++;
    }

    public final void writeByte(int v) throws IOException {
        write(v);
        //written++;
    }

    public final void writeShort(int v) throws IOException {
        write((v >>> 8) & 0xFF);
        write((v >>> 0) & 0xFF);
        //written += 2;
    }

    public final void writeChar(int v) throws IOException {
        write((v >>> 8) & 0xFF);
        write((v >>> 0) & 0xFF);
        //written += 2;
    }

    public final void writeInt(int v) throws IOException {
        write((v >>> 24) & 0xFF);
        write((v >>> 16) & 0xFF);
        write((v >>>  8) & 0xFF);
        write((v >>>  0) & 0xFF);
        //written += 4;
    }

    public final void writeLong(long v) throws IOException {
        write((int)(v >>> 56) & 0xFF);
        write((int)(v >>> 48) & 0xFF);
        write((int)(v >>> 40) & 0xFF);
        write((int)(v >>> 32) & 0xFF);
        write((int)(v >>> 24) & 0xFF);
        write((int)(v >>> 16) & 0xFF);
        write((int)(v >>>  8) & 0xFF);
        write((int)(v >>>  0) & 0xFF);
        //written += 8;
    }

    public final void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    public final void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    /**
     * Writes the string to the file as a sequence of bytes. Each
     * character in the string is written out, in sequence, by discarding
     * its high eight bits. The write starts at the current position of
     * the file pointer.
     *
     * @param      s   a string of bytes to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    @SuppressWarnings("deprecation")
    public final void writeBytes(String s) throws IOException {
        int len = s.length();
        byte[] b = new byte[len];
        s.getBytes(0, len, b, 0);
        try {
            fd.getHandle().write(JinixRuntime.getRuntime().getProcessGroupId(), b);
        } catch (NonWritableChannelException e) {
            throw new IOException("Illegal attempt to write to a non-writable file descriptor");
        } catch (NoSuchObjectException e) {
            throw new IOException("JinixRandomAccessFile: RemoteFileAccessor has been deleted");
        } catch (RemoteException e) {
            throw new IOException("JinixRandomAccessFile: Jinix server failure", e.getCause());
        }
    }

    public final void writeChars(String s) throws IOException {
        int clen = s.length();
        int blen = 2*clen;
        byte[] b = new byte[blen];
        char[] c = new char[clen];
        s.getChars(0, clen, c, 0);
        for (int i = 0, j = 0; i < clen; i++) {
            b[j++] = (byte)(c[i] >>> 8);
            b[j++] = (byte)(c[i] >>> 0);
        }
        write(b, 0, blen);
    }
}