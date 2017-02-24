package org.rowland.jinix.naming;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by rsmith on 2/11/2017.
 */
public interface FileChannel extends Remote {

    /**
     * Read up to len bytes from a file. At least a single byte will be read or an end of file will be reached before
     * this method returns. The size of the byte[] returned indicates the number of bytes read. A null return value
     * indicates an end of file.
     *
     * @param len the maximum number of bytes to read
     * @return byte[] containing the bytes read from the file. The byte[] size may be less than len
     * @throws IOException
     * @throws RemoteException
     */
    byte[] read(int len) throws RemoteException;

    int write(byte[] b) throws RemoteException;

    long skip(long n) throws RemoteException;

    int available() throws RemoteException;

    long getFilePointer() throws RemoteException;

    void seek(long pos) throws RemoteException;

    long length() throws RemoteException;

    void setLength(long length) throws RemoteException;

    void close() throws RemoteException;

    void duplicate() throws RemoteException;
}
