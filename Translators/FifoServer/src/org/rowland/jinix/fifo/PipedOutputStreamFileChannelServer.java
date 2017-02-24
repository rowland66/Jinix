package org.rowland.jinix.fifo;

import org.rowland.jinix.JinixKernelUnicastRemoteObject;
import org.rowland.jinix.naming.*;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by rsmith on 12/15/2016.
 */
public class PipedOutputStreamFileChannelServer extends JinixKernelUnicastRemoteObject implements FileChannel {

    private PipedOutputStream os;
    private int openCount;

    PipedOutputStreamFileChannelServer(PipedOutputStream pipedOutputStream) throws RemoteException {
        super();
        openCount = 1;
        os = pipedOutputStream;
        System.out.println("Opening POSFCS: " + this.toString());
    }

    @Override
    public String toString() {
        return this.os.toString();
    }

    @Override
    public byte[] read(int i) throws RemoteException {
        return new byte[0];
    }

    @Override
    public int write(byte[] b) throws RemoteException {
        try {
            os.write(b);
            return b.length;
        } catch (IOException e) {
            throw new RemoteException("Internal error", e);
        }
    }

    @Override
    public long skip(long l) throws RemoteException {
        return 0;
    }

    @Override
    public int available() throws RemoteException {
        return 0;
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
            System.out.println("Closing POSFCS count = "+openCount+": " + this.toString());
            openCount--;
            if (openCount == 0) {
                System.out.println("Closing POSFCS: " + this.toString());
                try {
                    os.close();
                    unexport();
                } catch (IOException e) {
                    throw new RemoteException("Internal error", e);
                }
            }
        }
    }

    @Override
    public void duplicate() throws RemoteException {
        System.out.println("Duplicate PSOFCS: "+toString());
        openCount++;
    }
}
