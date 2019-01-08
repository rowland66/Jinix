package org.rowland.jinix.io;

import org.rowland.jinix.naming.RemoteFileAccessor;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.SyncFailedException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by rsmith on 12/20/2016.
 */
public class JinixFileDescriptor extends FileDescriptor {

    public static List<JinixFileDescriptor> openFileDescriptors = new LinkedList<JinixFileDescriptor>();

    RemoteFileAccessor handle;
    private Closeable parent;
    private List<Closeable> otherParents;
    private boolean closed;

    public JinixFileDescriptor(RemoteFileAccessor streamHandle) {
        handle = streamHandle;
        if (handle != null) { // Translators on directories create file descriptors with null handles. These are never used.
            synchronized (openFileDescriptors) {
                openFileDescriptors.add(this);
            }
        }
    }

    public RemoteFileAccessor getHandle() {
        return handle;
    }

    public void sync() throws SyncFailedException {
        try {
            if (handle != null) {
                handle.force(false);
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Internal error", e.getCause());
        }
    }

    synchronized void attach(Closeable c) {
        if (parent == null) {
            // first caller gets to do this
            parent = c;
        } else if (otherParents == null) {
            otherParents = new ArrayList<>();
            otherParents.add(parent);
            otherParents.add(c);
        } else {
            otherParents.add(c);
        }
    }

    /**
     * Cycle through all Closeables sharing this FD and call
     * close() on each one.
     *
     * The caller closeable gets to call close0().
     */
    @SuppressWarnings("try")
    synchronized void closeAll(Closeable releaser) throws IOException {
        if (!closed) {
            closed = true;
            IOException ioe = null;
            try (Closeable c = releaser) {
                if (otherParents != null) {
                    for (Closeable referent : otherParents) {
                        try {
                            referent.close();
                        } catch(IOException x) {
                            if (ioe == null) {
                                ioe = x;
                            } else {
                                ioe.addSuppressed(x);
                            }
                        }
                    }
                }
            } catch(IOException ex) {
                /*
                 * If releaser close() throws IOException
                 * add other exceptions as suppressed.
                 */
                if (ioe != null)
                    ex.addSuppressed(ioe);
                ioe = ex;
            } finally {
                if (ioe != null)
                    throw ioe;
            }
        }
    }

    public void close() {
        if (!closed) {
            try {
                if (handle != null) {
                    try {
                        handle.close();
                    } catch (NoSuchObjectException e) {
                        // Ignore for now. This should never happen, but still does.
                    }
                    synchronized (openFileDescriptors) {
                        openFileDescriptors.remove(this);
                    }
                }
            } catch (RemoteException e) {
                throw new RuntimeException("Error closing file descriptor", e);
            }
            closed = true;
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        if (closed) {
            throw new RuntimeException("Illegal attempt to clone a closed file descriptor");
        }
        JinixFileDescriptor cloned = (JinixFileDescriptor) super.clone();
        try {
            cloned.handle.duplicate();
            cloned.parent = null;
            cloned.otherParents = null;
            synchronized (openFileDescriptors) {
                openFileDescriptors.add(cloned);
            }
            return cloned;
        } catch (RemoteException e) {
            throw new RuntimeException("Internal error", e);
        }
    }
}
