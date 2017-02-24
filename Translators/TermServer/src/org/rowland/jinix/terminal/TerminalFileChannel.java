package org.rowland.jinix.terminal;

import org.rowland.jinix.JinixKernelUnicastRemoteObject;
import org.rowland.jinix.naming.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by rsmith on 11/30/2016.
 */
public class TerminalFileChannel extends JinixKernelUnicastRemoteObject implements FileChannel {

    private short terminalId;
    private String type;
    LineDiscipline lineDiscipline;
    private volatile int openCount;

    TerminalFileChannel(short terminalId, String type, TermBuffer inputStream, TermBuffer outputStream, Map<PtyMode, Integer> modes) throws RemoteException {
        super();
        this.terminalId = terminalId;
        this.type = type;
        openCount = 1;
        if (modes != null) {
            this.lineDiscipline = new LineDiscipline(outputStream, inputStream, modes);
        } else {
            this.lineDiscipline = new LineDiscipline(outputStream, inputStream, new HashMap<PtyMode,Integer>());
        }
    }

    @Override
    public byte[] read(int len) throws RemoteException {

        if (openCount == 0) throw new RemoteException("Illegal attempt to read from a closed TermBuferIS");

        try {
            byte[] b = new byte[len];
            int i =0;
            do {
                int s = lineDiscipline.read();
                if (s == -1) {
                    return null;
                }
                b[i++] = (byte) s;
            } while (lineDiscipline.available() > 0);

            if (i < len) {
                byte[] rb = new byte[i];
                System.arraycopy(b, 0, rb, 0, i);
                b = rb;
            }
            return b;
        } catch (IOException e) {
            throw new RemoteException("Internal error", e);
        }
    }

    @Override
    public int write(byte[] bs) throws RemoteException {
        if (openCount == 0) throw new RemoteException("Illegal attempt to write to a closed TermBuferOS");

        try {
            for (byte b : bs) {
                lineDiscipline.write(b);
            }
            lineDiscipline.flush();
            return bs.length;
        } catch (IOException e) {
            throw new RemoteException("Internal error", e);
        }
    }

    @Override
    public long skip(long n) throws RemoteException {
        return 0;
    }

    @Override
    public int available() throws RemoteException {
        return lineDiscipline.available();
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
    public synchronized void close() throws RemoteException {
        if (openCount > 0) {
            System.out.println("Closing TFCS("+terminalId+"): open count="+openCount+": "+type);
            openCount--;
            if (openCount == 0) {
                System.out.println("Closing TFCS("+terminalId+"): "+type);
                unexport();
            }
        }
    }

    @Override
    public synchronized void duplicate() throws RemoteException {
        openCount++;
    }
}
