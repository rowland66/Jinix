package org.rowland.jinix.naming;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FileAccessorStatistics extends Remote {

    /**
     * Get the absolute path name of the file from the root name space. This is the Jinix name.
     *
     * @return
     */
    String getAbsolutePathName() throws RemoteException;


}
