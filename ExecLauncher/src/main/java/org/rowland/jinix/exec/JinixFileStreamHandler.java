package org.rowland.jinix.exec;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Created by rsmith on 11/26/2016.
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
