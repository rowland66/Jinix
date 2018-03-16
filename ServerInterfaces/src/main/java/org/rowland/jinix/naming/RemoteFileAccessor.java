package org.rowland.jinix.naming;

import java.io.IOException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface for a file
 */
public interface RemoteFileAccessor extends Remote {

    /**
     * Read up to len bytes from a file. At least a single byte will be read or an end of file will be reached before
     * this method returns. The size of the byte[] returned indicates the number of bytes read. A null return value
     * indicates an end of file.
     *
     * @param pgid the process group id of the Jinix process calling read
     * @param len the maximum number of bytes to read
     * @return byte[] containing the bytes read from the file. The byte[] size may be less than len
     * @throws IOException
     * @throws RemoteException
     */
    byte[] read(int pgid, int len) throws NonReadableChannelException, RemoteException;

    /**
     * Write an array of bytes to a file at the current position.
     *
     * @param pgid the process group id of the Jinix process calling write
     * @param b the bytes to write
     * @return the number of bytes written
     * @throws NonWritableChannelException
     * @throws RemoteException
     */
    int write(int pgid, byte[] b) throws NonWritableChannelException, RemoteException;

    long skip(long n) throws RemoteException;

    int available() throws RemoteException;

    long getFilePointer() throws RemoteException;

    void seek(long pos) throws RemoteException;

    long length() throws RemoteException;

    void setLength(long length) throws RemoteException;

    void close() throws RemoteException;

    void duplicate() throws RemoteException;

    void force(boolean metadata) throws RemoteException;

    void flush() throws RemoteException;
}
