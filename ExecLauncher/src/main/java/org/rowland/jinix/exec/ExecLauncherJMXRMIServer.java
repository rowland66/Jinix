package org.rowland.jinix.exec;

//import sun.rmi.server.UnicastServerRef;

import javax.management.MBeanServer;
import javax.management.remote.rmi.RMIConnection;
import javax.management.remote.rmi.RMIConnectionImpl;
import javax.management.remote.rmi.RMIServerImpl;
import javax.security.auth.Subject;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

public class ExecLauncherJMXRMIServer extends RMIServerImpl {

    private static int connectionId = 0;

    private Map<String, RMIConnection> connectionMap = new HashMap();
    private ClassLoader mBeanClassLoader;

    ExecLauncherJMXRMIServer(MBeanServer mBeanServer, ClassLoader mBeanClassLoader) throws IOException {
        super(null);
        this.mBeanClassLoader = mBeanClassLoader;
        setMBeanServer(mBeanServer);
        export();
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    protected void export() throws IOException {
        UnicastRemoteObject.exportObject(this, 0);
    }

    @Override
    public Remote toStub() throws IOException {
        return UnicastRemoteObject.toStub(this);
    }

    @Override
    public RMIConnection newClient(Object credentials) throws IOException {
        RMIConnection newClient = makeClient(Integer.toString(connectionId++), null);
        connectionMap.put(newClient.getConnectionId(), newClient);
        return newClient;
    }

    @Override
    protected RMIConnection makeClient(String connectionId, Subject subject) throws IOException {
        if (connectionId == null)
            throw new NullPointerException("Null connectionId");
        RMIConnection client =
                new RMIConnectionImpl(this, connectionId, mBeanClassLoader,
                        null, null);
        UnicastRemoteObject.exportObject(client, 0);
        return client;
    }

    @Override
    protected void closeClient(RMIConnection client) throws IOException {
        UnicastRemoteObject.unexportObject(client, true);
    }

    @Override
    protected void closeServer() throws IOException {
        UnicastRemoteObject.unexportObject(this, true);
    }

    @Override
    public synchronized void close() throws IOException {

        closeServer();

        for (RMIConnection conn : connectionMap.values()) {
            conn.close();
        }

        connectionMap.clear();
    }

    @Override
    protected void clientClosed(RMIConnection client) throws IOException {

        if (client == null)
            throw new NullPointerException("Null client");

        RMIConnection clientConnection = null;
        synchronized (connectionMap) {
            clientConnection = connectionMap.remove(client.getConnectionId());
        }
        if (clientConnection != null) {
            closeClient(clientConnection);
        }
    }

    @Override
    protected String getProtocol() {
        return null;
    }
}
