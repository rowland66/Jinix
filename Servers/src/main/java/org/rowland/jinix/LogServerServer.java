package org.rowland.jinix;

import org.rowland.jinix.logger.LogServer;

import java.rmi.RemoteException;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A Jinix kernel server that provides access to the in memory JinixKernelLoggger.
 * This server is generally only used by the kernel logger daemon (klogd) to
 * retrieve kernel logs and write them to /var/log/kernel.log.
 */
public class LogServerServer extends JinixKernelUnicastRemoteObject implements LogServer {

    static JinixKernelLoggingMemoryHandler kernelLoggingHandler =
            new JinixKernelLoggingMemoryHandler();
    static Formatter logFormatter = new BriefKernelLogFormatter();

    LogServerServer() throws RemoteException {

    }

    @Override
    public String[] getLogs(int count) throws RemoteException {
        LogRecord[] availableLogs = kernelLoggingHandler.getAvailableLogRecords();
        if (availableLogs.length > 0) {
            String[] rtrn = new String[Math.min(availableLogs.length, count)];
            int i=0;
            for (LogRecord rec : availableLogs) {
                rtrn[i++] = logFormatter.format(rec);
                if (i == rtrn.length) {
                    return rtrn;
                }
            }
            return rtrn;
        }
        return null;
    }

    @Override
    public void setLevel(String server, String level) throws RemoteException {
        Logger l = Logger.getLogger(server);
        if (l != null) {
            try {
                l.setLevel(Level.parse(level));
            } catch (IllegalArgumentException e) {
                // Ignore this error, and don't set the level
            }
        }
    }

    @Override
    public int getLevel(String server) throws RemoteException {
        Logger l = Logger.getLogger(server);
        if (l != null) {
            return l.getLevel().intValue();
        }
        return -1;
    }
}
