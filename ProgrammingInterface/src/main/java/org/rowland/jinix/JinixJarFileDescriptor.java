package org.rowland.jinix;

import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.naming.RemoteFileAccessor;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;

/**
 * This is special FileDescriptor used when resources are being read from Jinix Jar files. Because the ExecClassLoader
 * needs to read multiple entries from the same Jar file, calling RemoteFileAccessor.close() on a JarFile does not unexport
 * the remote server. To unexport the remote server, close() must be called a second time. The ExecClassLoader does this
 * properly, but JinixFileDescriptors supporting JinixInputStreams providing access to Jar resources do not.
 */
public class JinixJarFileDescriptor extends JinixFileDescriptor {
    private boolean entryClosed = false;

    public JinixJarFileDescriptor(RemoteFileAccessor streamHandle) {
        super(streamHandle);
    }

    @Override
    public void close() {
        if (!entryClosed) {
            try {
                if (getHandle() != null) {
                    try {
                        getHandle().close();
                    } catch (NoSuchObjectException e) {
                        // Ignore for now. This should never happen, but still does.
                    }
                }
            } catch (RemoteException e) {
                throw new RuntimeException("Error closing file descriptor", e);
            }
            entryClosed = true;
            super.close();
        }
    }
}
