package org.rowland.jinix.exec;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * A very simple URLStreamHandlerFactory for the jns (Jinix Name Space) protocol. This
 * URLStreamHandlerFactory is used by the ExecClassLoader to load files from the Jinix
 * name space (aka the Jinix file system)
 */
public class ExecStreamHandlerFactory implements URLStreamHandlerFactory {

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {

        if ("jns".equals(protocol)) {
            return new JinixFileStreamHandler();
        }

        return null;
    }
}
