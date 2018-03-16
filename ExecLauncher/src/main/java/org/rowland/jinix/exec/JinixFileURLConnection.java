package org.rowland.jinix.exec;

import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.io.JinixFileInputStream;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.RemoteJarFileAccessor;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.rmi.Remote;
import java.security.Permission;
import java.util.List;

/**
 * A java.net.URLConnection that provides connections to resources in Jinix Jar files. The URL filename provides the
 * absolute path to the Jar file in the file system, and the path of the resource entry in the Jar file separated by
 * "!/".
 */
public class JinixFileURLConnection extends URLConnection {

    JinixJarFileDescriptor remoteJarFd;

    public JinixFileURLConnection(URL u) {
        super(u);
    }

    @Override
    public void connect() throws IOException {
        if (!connected) {
            String fileName = getURL().getPath();
            if (!fileName.contains("!/") || fileName.endsWith("!/")) {
                throw new FileNotFoundException(fileName);
            }
            fileName = fileName.substring(0, fileName.indexOf("!/"));
            String resourcePath = getURL().getPath().substring(getURL().getPath().indexOf("!/") + 2);

            try {
                Context ctx = JinixRuntime.getRuntime().getNamingContext();
                Remote r = (Remote) ctx.lookup(fileName);
                if (r instanceof RemoteJarFileAccessor) {
                    RemoteJarFileAccessor remoteJar = (RemoteJarFileAccessor) r;
                    if (remoteJar.findEntry(resourcePath) == -1) {
                        remoteJar.close();
                        throw new FileNotFoundException(getURL().getPath());
                    }
                    remoteJarFd = new JinixJarFileDescriptor(remoteJar);
                    connected = true;
                } else {
                    throw new FileNotFoundException(fileName);
                }
            } catch (NameNotFoundException e) {
                throw new FileNotFoundException(fileName);
            } catch (NamingException e) {
                throw new RuntimeException("Internal error", e);
            }
        }
    }

    public synchronized InputStream getInputStream() throws IOException {
        this.connect();
        return new JinixFileInputStream(remoteJarFd);
    }


    /**
     * Control what permissions are required to access this connection. For now no permissions
     * are required, but we may need to reconsider this.
     *
     * @return
     * @throws IOException
     */
    @Override
    public Permission getPermission() throws IOException {
        return null;
    }
}