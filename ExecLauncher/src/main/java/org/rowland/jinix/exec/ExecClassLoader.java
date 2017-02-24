package org.rowland.jinix.exec;

import sun.security.util.SecurityConstants;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.*;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.Enumeration;
import java.util.jar.Attributes;

/**
 * ClassLoader used by the ExecLauncher to load executable jars their dependencies.
 * The ExecClassLoader defines a ProtectionDomain that the JinixPolicy security
 * policy uses to restrict Jinix executables access to native linux resources.
 */
public class ExecClassLoader extends URLClassLoader {

    private URL jarURL;
    private boolean isPrivileged;

    /**
     * Create an ExecClassloader for a set of URL's. The URL's must use the 'jns'
     * protocol.
     * @param url a 'jns' protocol URL for the executable jar
     * @param parent the parent classloader
     */
    ExecClassLoader(URL url, boolean isPrivileged, ClassLoader parent) {
        super(new URL[] {url}, parent);
        this.jarURL = url;
        this.isPrivileged = isPrivileged;
    }

    /**
     * Overridden to provide access to other classes in package
     *
     * @param url
     */
    @Override
    protected void addURL(URL url) {
        super.addURL(url);
    }

    /**
     * Return the name of the jar file main class, or null if
     * no "Main-Class" manifest attributes was defined.

     * @return
     * @throws IOException
     */
    String getMainClassName() throws IOException {
        URL u = new URL("jar", "", jarURL + "!/");
        JarURLConnection uc = (JarURLConnection)u.openConnection();
        uc.setUseCaches(true);
        Attributes attr = uc.getMainAttributes();
        return attr != null ? attr.getValue(Attributes.Name.MAIN_CLASS) : null;
    }

    /**
     * Return an array of dependent libraries or a zero length
     * array if no "Class-Path" manifest attribute was defined.
     *
     * @return
     * @throws IOException
     */
    String[] getLibraryNames() throws IOException {
        URL u = new URL("jar", "", jarURL + "!/");
        JarURLConnection uc = (JarURLConnection)u.openConnection();
        uc.setUseCaches(true);
        Attributes attr = uc.getMainAttributes();
        String classPath = attr.getValue(Attributes.Name.CLASS_PATH);
        if (classPath == null) {
            return new String[0];
        }
        String[] rtrn = classPath.split("\\s");
        return rtrn;
    }

    boolean isPrivileged() {
        return this.isPrivileged;
    }
}
