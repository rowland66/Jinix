package org.rowland.jinix.fifo;

import org.rowland.jinix.JinixKernelUnicastRemoteObject;
import org.rowland.jinix.naming.*;

import java.io.IOException;
import java.io.PipedInputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by rsmith on 12/15/2016.
 */
public class PipedInputStreamFileChannelServer extends JinixKernelUnicastRemoteObject implements FileChannel {

    private PipedInputStream is;
    private int openCount;

    PipedInputStreamFileChannelServer(PipedInputStream pipedInputStream) throws IOException {
        super();
        openCount = 1;
        is = pipedInputStream;
    }

    @Override
    public byte[] read(int len) throws RemoteException {
        try {
            byte[] b = new byte[len];
            int r = is.read(b);
            if (r == -1) {
                return null;
            }

            byte[] rb = new byte[r];
            System.arraycopy(b, 0, rb, 0, r);
            return rb;
        } catch (IOException e) {
            throw new RemoteException("Internal error", e);
        }
    }

    @Override
    public int write(byte[] bytes) throws RemoteException {
        return 0;
    }

    @Override
    public long skip(long l) throws RemoteException {
        try {
            return is.skip(l);
        } catch (IOException e) {
            throw new RemoteException("Internal error", e);
        }
    }

    @Override
    public int available() throws RemoteException {
        try {
            return is.available();
        } catch (IOException e) {
            throw new RemoteException("Internal error", e);
        }
    }

    @Override
    public long getFilePointer() throws RemoteException {
        return 0;
    }

    @Override
    public void seek(long l) throws RemoteException {

    }

    @Override
    public long length() throws RemoteException {
        return 0;
    }

    @Override
    public void setLength(long l) throws RemoteException {

    }

    @Override
    public void close() throws RemoteException {
        if (openCount > 0) {
            openCount--;
            if (openCount == 0) {
                try {
                    is.close();
                    unexport();
                } catch (IOException e) {
                    throw new RemoteException("Internal error", e);
                }
            }
        }
    }

    @Override
    public void duplicate() throws RemoteException {
        openCount++;
    }
}
