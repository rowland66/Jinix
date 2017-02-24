package org.rowland.jinix;

import org.rowland.jinix.naming.FileChannel;

import java.io.*;
import java.rmi.RemoteException;

/**
 * A simple FileIS that serves up a String from memory.
 */
public class StringFileChannel extends JinixKernelUnicastRemoteObject implements FileChannel {
    private ByteArrayInputStream data;
    private int openCount;


    public StringFileChannel(String fileData) throws RemoteException {
        super();
        openCount = 1;
        data = new ByteArrayInputStream(fileData.getBytes());
    }

    @Override
    public byte[] read(int len) throws RemoteException {
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
    public int write(byte[] b) throws RemoteException {
        return b.length;
    }

    @Override
    public long skip(long n) throws RemoteException {
        return 0;
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
        return 0;
    }

    @Override
    public void seek(long pos) throws RemoteException {

    }

    @Override
    public long length() throws RemoteException {
        return 0;
    }

    @Override
    public void setLength(long length) throws RemoteException {

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
}
