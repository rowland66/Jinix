package org.rowland.jinix.naming;

import java.rmi.RemoteException;

/**
 * Inteface provided to support Jinix jar files. This interface is primarily used by the ExecClassLoader to more
 * efficiently load class byte code from Jinix jar files. Jinix jar files support the inclusion of additional jars
 * in a /lib directory in a jar file. This feature allows a jar all of its dependencies to packaged in a single
 * artifact.
 */
public interface RemoteJarFileAccessor extends RemoteFileAccessor {

    /**
     * Get the manifest file for a jar file. The manifest is returned in a JarManifest object.
     * @return the manifest or null if not available
     * @throws RemoteException
     */
    public JarManifest getManifest() throws RemoteException;

    public String[] getPackages() throws RemoteException;

    public long findEntry(String name) throws RemoteException;
}
