package org.rowland.jinix.exec;

import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.io.JinixFileInputStream;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;
import java.util.List;

/**
 * Created by rsmith on 11/26/2016.
 */
public class JinixFileURLConnection extends URLConnection {

    static String CONTENT_LENGTH = "content-length";
    static String CONTENT_TYPE = "content-type";
    static String TEXT_PLAIN = "text/plain";
    static String LAST_MODIFIED = "last-modified";

    String contentType;
    InputStream is;

    JinixFile file;
    String filename;
    boolean isDirectory = false;
    boolean exists = false;
    List<String> files;

    long length = -1;
    long lastModified = 0;


    public JinixFileURLConnection(URL u) {
        super(u);
    }

    @Override
    public void connect() throws IOException {
        if (!connected) {
            this.is = new BufferedInputStream(new JinixFileInputStream(new JinixFile(getURL().getPath())));
            connected = true;
        }
    }

    public synchronized InputStream getInputStream() throws IOException {
        this.connect();
        if (this.is == null) {
            throw new FileNotFoundException(getURL().getPath());
        }
        return this.is;
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