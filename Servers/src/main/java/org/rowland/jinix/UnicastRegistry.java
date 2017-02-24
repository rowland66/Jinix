package org.rowland.jinix;

import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;

class UnicastRegistry {
    private static Registry registry;
    private static UnicastRegistry singleton;
    // private to force the singleton

    private UnicastRegistry(int registryPort) throws RemoteException {
        registry = LocateRegistry.createRegistry(registryPort);
    }

    private UnicastRegistry(int registryPort, RMISocketFactory csf, RMISocketFactory ssf) throws RemoteException {
        registry = LocateRegistry.createRegistry(registryPort, csf, ssf);
    }

    static UnicastRegistry createSingleton(int registryPort) throws RemoteException {
        if (singleton == null) {
            singleton = new UnicastRegistry(registryPort);
        }
        return singleton;
    }

    static UnicastRegistry createSingleton(int registryPort, RMISocketFactory csf, RMISocketFactory ssf) throws RemoteException {
        if (singleton == null) {
            singleton = new UnicastRegistry(registryPort, csf, ssf);
        }
        return singleton;
    }

    void register(String label, Remote obj) throws RemoteException, AlreadyBoundException {
        registry.bind(label, obj);
    }

    void unregister(String label) throws RemoteException, NotBoundException {
        Remote remote = registry.lookup(label);
        registry.unbind(label);
        if (remote instanceof UnicastRemoteObject) {
            UnicastRemoteObject.unexportObject(remote, true);
        }
    }

    void unregisterAll() throws RemoteException {
        for (String label : registry.list()) {
            try {
                unregister(label);
            } catch (NotBoundException e) {
                // Ignore if not bound. Should not happen
            }
        }
    }

    void printStillBound() throws RemoteException {
        String[] stillBound = registry.list();
        if (stillBound.length > 0) {
            System.out.println("Still bound = " + Arrays.toString(stillBound));
        }
    }
}