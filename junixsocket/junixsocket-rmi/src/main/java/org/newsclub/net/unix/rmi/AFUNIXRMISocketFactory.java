/**
 * junixsocket
 * <p>
 * Copyright (c) 2009,2014 Christian Kohlschütter
 * <p>
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix.rmi;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.util.Properties;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * An {@link RMISocketFactory} that supports {@link AFUNIXSocket}s.
 *
 * @author Christian Kohlschütter
 */
public class AFUNIXRMISocketFactory extends RMISocketFactory implements Externalizable {

    private static final String SYSTEM_PROPERTY_CONFIG_FILE = "AFUNIXRMISocketFactory.config.file";
    private static final String SYSTEM_PROPERTY_SERVER_NAME = "AFUNIXRMISocketFactory.server.name";

    private static final String CONFIG_FILE_PROPERTY_SOCKET_DIRECTORY = "socket.directory";

    private static final File DEFAULT_SOCKET_DIRECTORY = new File("/tmp");
    private static final String DEFAULT_SOCKET_FILE_SUFFIX = ".rmi";

    private static final long serialVersionUID = 1L;

    private RMIClientSocketFactory defaultClientFactory;
    private RMIServerSocketFactory defaultServerFactory;

    private static File socketDir;

    private String socketPrefix;
    private String socketSuffix;

    private PortAssigner generator = null;

    private static Properties configProperties = null;

    static {
        String configFile = System.getProperty(SYSTEM_PROPERTY_CONFIG_FILE);
        if (configFile != null) {
            configProperties = new Properties();
            try {
                configProperties.load(new FileInputStream(new File(configFile, "AFUNIXRMISocketFactory.config")));
            } catch (IOException e) {
                throw new RuntimeException("AFUNIXRMISocketFactory: Failed to load config file: "+configFile, e);
            }
        }

        if (socketDir == null) {
            if (configProperties != null && configProperties.getProperty(CONFIG_FILE_PROPERTY_SOCKET_DIRECTORY) != null) {
                socketDir = new File(configProperties.getProperty(CONFIG_FILE_PROPERTY_SOCKET_DIRECTORY));
            } else {
                socketDir = DEFAULT_SOCKET_DIRECTORY;
            }
        }
    }

    /**
     * Constructor required per definition.
     *
     * @see RMISocketFactory
     */
    public AFUNIXRMISocketFactory() {
        this(null, null);
    }

    private AFUNIXRMISocketFactory(final RMIClientSocketFactory defaultClientFactory,
                                  final RMIServerSocketFactory defaultServerFactory) {
        this(defaultClientFactory, defaultServerFactory, null, null);
    }

    private AFUNIXRMISocketFactory(final RMIClientSocketFactory defaultClientFactory,
                                  final RMIServerSocketFactory defaultServerFactory, final String socketPrefix,
                                  final String socketSuffix) {

        this.defaultClientFactory = defaultClientFactory;
        this.defaultServerFactory = defaultServerFactory;

        this.socketPrefix = System.getProperty(SYSTEM_PROPERTY_SERVER_NAME); // This will return null for the client
        this.socketSuffix = socketSuffix == null ? DEFAULT_SOCKET_FILE_SUFFIX : socketSuffix;

        this.generator = new PortAssignerImpl();
    }

    @Override
    public int hashCode() {
        return socketPrefix.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AFUNIXRMISocketFactory)) {
            return false;
        }
        AFUNIXRMISocketFactory sf = (AFUNIXRMISocketFactory) other;
        return sf.socketPrefix.equals(socketPrefix);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        final RMIClientSocketFactory cf = defaultClientFactory;
        if (cf != null && port < AFUNIXRMIPorts.AF_PORT_BASE) {
            return cf.createSocket(host, port);
        }

        final AFUNIXSocketAddress addr = new AFUNIXSocketAddress(getFile(host, port), port);
        return AFUNIXSocket.connectTo(addr);
    }

    private File getFile(String host, int port) {
        if (host == null || host.equals("127.0.0.1") || host.equals("127.0.1.1") || host.equals("0.0.0.0")) {
            host = socketPrefix;
            if (host == null) {
                throw new RuntimeException("Illegal attempt to create RMI socket with no server name");
            }
        }
        return new File(socketDir, host + "-" + port + socketSuffix);
    }

    public void close() {
    }

    private int newPort() throws IOException {
        return generator.newPort();
    }

    private void returnPort(int port) throws IOException {
        generator.returnPort(port);
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
        if (port == 0) {
            port = newPort();
            final AFUNIXSocketAddress addr = new AFUNIXSocketAddress(getFile(null, port), port);
            final AnonymousServerSocket ass = new AnonymousServerSocket(port);
            ass.bind(addr);
            return ass;
        }

        final RMIServerSocketFactory sf = defaultServerFactory;
        if (sf != null && port < AFUNIXRMIPorts.AF_PORT_BASE) {
            return sf.createServerSocket(port);
        }

        final AFUNIXSocketAddress addr = new AFUNIXSocketAddress(getFile(null, port), port);
        return AFUNIXServerSocket.bindOn(addr);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        defaultClientFactory = (RMIClientSocketFactory) in.readObject();
        defaultServerFactory = (RMIServerSocketFactory) in.readObject();

        socketPrefix = in.readUTF();
        socketSuffix = in.readUTF();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(defaultClientFactory);
        out.writeObject(defaultServerFactory);

        out.writeUTF(socketPrefix);
        out.writeUTF(socketSuffix);
    }

    private final class AnonymousServerSocket extends AFUNIXServerSocket {
        private final int returnPort;

        AnonymousServerSocket(int returnPort) throws IOException {
            super();
            this.returnPort = returnPort;
        }

        @Override
        public void close() throws IOException {
            super.close();
            returnPort(returnPort);
        }
    }

}
