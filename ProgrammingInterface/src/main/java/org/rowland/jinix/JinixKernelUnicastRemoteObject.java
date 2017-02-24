package org.rowland.jinix;

import java.io.PrintStream;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * UnicastRemoteObject subclass to be used by all RemoteObjects exported in the Jinix Kernel including
 * translators. Keeps track of export and unexports to facilitate kernel shutdown.
 */
public class JinixKernelUnicastRemoteObject extends UnicastRemoteObject {

    private static ExportedObjectCounter exportedObjectCounter = new ExportedObjectCounter();

    public JinixKernelUnicastRemoteObject() throws RemoteException {
        this(0, RMISocketFactory.getSocketFactory(), RMISocketFactory.getSocketFactory());
    }

    public JinixKernelUnicastRemoteObject(int port, RMISocketFactory csf, RMISocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        exportedObjectCounter.add(this.getClass());
    }

    public boolean unexport() {
        try {
            return unexportObject(this, true);
        } catch (NoSuchObjectException e) {
            return true; // this should never happen
        }
    }

    public static boolean unexportObject(Remote obj, boolean force) throws NoSuchObjectException {
        exportedObjectCounter.remove(obj, obj.getClass());
        return UnicastRemoteObject.unexportObject(obj, force);
    }

    public static void dumpExportedObjects(PrintStream ps) {
        exportedObjectCounter.dumpExportedObjects(ps);
    }

    private static class ExportedObjectCounter {
        private Map<String, Counter> exportCount = new HashMap<String, Counter>();

        private ExportedObjectCounter() {
        }

        private void add(Class c) {
            String className = c.getName();
            if (!exportCount.containsKey(className)) {
                exportCount.put(className, new Counter());
            }
            exportCount.get(className).increment();
        }

        private void remove(Object o, Class c) {
            String className = c.getName();
            if (!exportCount.containsKey(className)) {
                return;
            }
            exportCount.get(className).decrement();
        }

        private void dumpExportedObjects(PrintStream ps) {
            for (Map.Entry<String,Counter> entry : exportCount.entrySet()) {
                String className = entry.getKey();
                if (entry.getValue().value > 0) {
                    ps.println(className + " : " + entry.getValue().value);
                }
            }
        }
    }

    private static class Counter {
        private int value;

        private Counter() {
            value = 0;
        }

        private void increment() {
            value++;
        }

        private void decrement() {
            value--;
        }
    }
}
