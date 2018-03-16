package org.rowland.jinix.exec;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * A java.net.URLStreamHandler that support the "jns" protocol. This protocol is used for locating resources in a Jar file.
 */
public class JinixFileStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        if ("jns".equals(u.getProtocol())) {
            return new JinixFileURLConnection(u);
        }

        return null;
    }
}
