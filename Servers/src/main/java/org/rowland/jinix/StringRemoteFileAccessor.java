package org.rowland.jinix;

import org.rowland.jinix.naming.RemoteFileAccessor;

import java.io.*;
import java.rmi.RemoteException;

/**
 * A simple FileIS that serves up a String from memory.
 */
public class StringRemoteFileAccessor extends JinixKernelUnicastRemoteObject implements RemoteFileAccessor {
    private ByteArrayInputStream data;
    private int openCount;
    private int size;


    public StringRemoteFileAccessor(String fileData) throws RemoteException {
        super();
        openCount = 1;
        byte[] stringBytes = fileData.getBytes();
        size = stringBytes.length;
        data = new ByteArrayInputStream(stringBytes);
    }

    @Override
    public byte[] read(int processGroupId, int len) throws RemoteException {
        if (data==null) {
            throw new RemoteException("File closed");
        }
        int bsize = Math.min(len,data.available());
        if (bsize == 0) {
            return null;
        }
        byte[] b = new byte[bsize];
        data.read(b, 0, b.length);
        return b;
    }

    @Override
    public int write(int processGroupId, byte[] b) throws RemoteException {
        return b.length;
    }

    @Override
    public long skip(long n) throws RemoteException {
        if (data==null) {
            throw new RemoteException("File closed");
        }
        return data.skip(n);
    }

    @Override
    public int available() throws RemoteException {
        if (data==null) {
            throw new RemoteException("File closed");
        }
        return data.available();
    }

    @Override
    public long getFilePointer() throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void seek(long pos) throws RemoteException {
        throw new UnsupportedOperationException();

    }

    @Override
    public long length() throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLength(long length) throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws RemoteException {
        if (openCount > 0) {
            openCount--;
            if (openCount == 0) {
                data = null;
                unexport();
            }
        }
    }

    @Override
    public void duplicate() throws RemoteException {
        openCount++;
    }

    @Override
    public void force(boolean metadata) throws RemoteException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void flush() throws RemoteException {

    }
}
