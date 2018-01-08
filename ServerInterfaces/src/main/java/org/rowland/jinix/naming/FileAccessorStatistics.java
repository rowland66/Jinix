package org.rowland.jinix.naming;

import java.rmi.RemoteException;

public interface FileAccessorStatistics {

    /**
     * Get the absolute path name of the file from the root name space. This is the Jinix name.
     *
     * @return
     */
    String getAbsolutePathName() throws RemoteException;


}
