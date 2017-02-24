package org.rowland.jinix.proc;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by rsmith on 12/4/2016.
 */
public interface EventNotificationHandler extends Remote {

    void handleEventNotification(ProcessManager.EventName event, Object eventData) throws RemoteException;
}
