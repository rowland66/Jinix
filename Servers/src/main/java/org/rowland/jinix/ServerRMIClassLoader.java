package org.rowland.jinix;

import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.JinixSystem;
import org.rowland.jinix.naming.*;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.security.*;
import java.util.*;
import java.util.jar.Attributes;

/**
 * ClassLoader used by the KernelRMIClassLoader to load executable jars and their dependencies.
 * The ExecClassLoader defines a ProtectionDomain that the JinixPolicy security
 * policy uses to restrict Jinix executables from accessing native linux resources.
 *
 * The ExecClassLoader talks native RMI to a special RemoteJarFileAccessor. These objects are looked up like
 * files in the Jinix NameSpace. This ClassLoader does not use URL's.
 */
public class ServerRMIClassLoader extends SecureClassLoader {

    private final String jarFileName;
    private final boolean isPrivileged;
    private List<RemoteJarHolder> remoteJarList;
    private boolean closed;
    private JarManifest jarManifest;
    private boolean missingManifest;

    /* The context to be used when loading classes and resources */
    private final AccessControlContext acc;

    /**
     * Create an ExecClassloader for a Jinix jar file. This constructor is only called from the Jinix kernel.
     *
     * @param jarFileName the absolute pathname of a Jinix jar file
     * @param privileged indicates whether classes loaded by this ClassLoader are privileged to native OS resources
     * @param remoteJarAccessor a RemoteJarFileAccessor corresponding to the jarFileName
     * @param parent the parent classloader
     */
    public ServerRMIClassLoader(String jarFileName, boolean privileged, RemoteJarFileAccessor remoteJarAccessor, ClassLoader parent) {
        super(parent);
        this.jarFileName = jarFileName;
        this.isPrivileged = privileged;
        this.acc = AccessController.getContext();

        this.remoteJarList = new LinkedList<>();
        remoteJarList.add(new RemoteJarHolder(jarFileName, remoteJarAccessor));

        try {
            resolveExecClassPath();
        } catch (IOException e) {
            throw new RuntimeException("Internal error", e);
        }

        closed = false;
    }

    void addLibraryToClasspath(String jarFileName) {
        String libraryPathStr = "/lib";
        String[] libraryPath = libraryPathStr.split(":");
        for(String libDir : libraryPath) {
            String libPathName = libDir + "/" + jarFileName;
            try {
                RemoteFileHandle jarFile = (RemoteFileHandle) JinixKernel.getNameSpaceRoot().lookup(libPathName);
                RemoteJarFileAccessor jarFileAccessor = (RemoteJarFileAccessor) jarFile.getParent().
                        getRemoteFileAccessor(-1, jarFile.getPath(), EnumSet.of(StandardOpenOption.READ));
                remoteJarList.add(new RemoteJarHolder(libPathName, jarFileAccessor));
                break;
            } catch (FileAlreadyExistsException | NoSuchFileException e) {
                throw new RuntimeException("Internal error: ", e); // This should never happen
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (closed || remoteJarList == null) {
            return null;
        }
        final Class<?> result;
        try {
            result = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<Class<?>>) () -> {
                        String path = name.replace('.', '/').concat(".class");
                        String pkg;
                        if (path.indexOf('/') == -1) {
                            pkg = "/"; // package for classes in the default package
                        } else {
                            pkg = path.substring(0, path.lastIndexOf("/"));
                        }
                        long entrySize;
                        for (RemoteJarHolder remoteJarHolder : remoteJarList) {
                            if (remoteJarHolder.pkg == null) {
                                remoteJarHolder.pkg = remoteJarHolder.remoteJar.getPackages();
                            }
                            if (Arrays.binarySearch(remoteJarHolder.pkg, pkg) > -1) {
                                if ((entrySize = remoteJarHolder.remoteJar.findEntry(path)) > -1) {
                                    byte[] classBytes;
                                    try {
                                        classBytes = remoteJarHolder.remoteJar.read(-1, (int) entrySize);
                                    } finally {
                                        remoteJarHolder.remoteJar.close();
                                    }
                                    return defineClass(name, classBytes, 0, classBytes.length);
                                }
                            }
                        }
                        return null;
                    }, acc);
        } catch (java.security.PrivilegedActionException pae) {
            if (pae.getException() instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) pae.getException();
            }
            throw new RuntimeException("Critical failure finding class: "+name, pae.getException());
        }
        if (result == null) {
            throw new ClassNotFoundException(name);
        }
        return result;
    }

    @Override
    protected URL findResource(String name) {
        if (closed || remoteJarList == null) {
            return null;
        }
        final URL result;
        try {
            result = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<URL>) () -> {
                        String pkg;
                        if (name.indexOf('/') == -1) {
                            pkg = "/";
                        } else {
                            pkg = name.substring(0, name.lastIndexOf("/"));
                        }
                        for (RemoteJarHolder remoteJarHolder : remoteJarList) {
                            if (remoteJarHolder.pkg == null) {
                                remoteJarHolder.pkg = remoteJarHolder.remoteJar.getPackages();
                            }
                            if (Arrays.binarySearch(remoteJarHolder.pkg, pkg) > -1) {
                                if (remoteJarHolder.remoteJar.findEntry(name) > -1) {
                                    return new URL("jns", null, -1, remoteJarHolder.name+"!/"+name, new JinixFileStreamHandler());
                                }
                            }
                        }
                        return null;
                    }, acc);
        } catch (java.security.PrivilegedActionException pae) {
            if (pae.getException() instanceof MalformedURLException) {
                throw new RuntimeException("Internal Error", pae.getException());
            }
            throw new RuntimeException(pae.getException());
        }
        return result;
    }

    @Override
    protected Enumeration<URL> findResources(String name) {
        if (closed || remoteJarList == null) {
            return null;
        }

        final List<URL> result;
        try {
            result = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<List<URL>>) () -> {
                        String pkg;
                        if (name.indexOf('/') == -1) {
                            pkg = "/";
                        } else {
                            pkg = name.substring(0, name.lastIndexOf("/"));
                        }
                        List<URL> rtrnList = new ArrayList<>();
                        for (RemoteJarHolder remoteJarHolder : remoteJarList) {
                            if (remoteJarHolder.pkg == null) {
                                remoteJarHolder.pkg = remoteJarHolder.remoteJar.getPackages();
                            }
                            if (Arrays.binarySearch(remoteJarHolder.pkg, pkg) > -1) {
                                if (remoteJarHolder.remoteJar.findEntry(name) > -1) {
                                    rtrnList.add(new URL("jns", null, -1, remoteJarHolder.name+"!/"+name, new JinixFileStreamHandler()));
                                }
                            }
                        }
                        return rtrnList;
                    }, acc);
        } catch (java.security.PrivilegedActionException pae) {
            if (pae.getException() instanceof MalformedURLException) {
                throw new RuntimeException("Internal Error", pae.getException());
            }
            throw new RuntimeException(pae.getException());
        }

        return new Enumeration<>() {
            private final Iterator<URL> iterator = result.iterator();

            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            public URL nextElement() {
                return iterator.next();
            }
        };
    }

    /**
     * Get the absolute pathname of the jar file supplying classes to this classloader.
     *
     * @return the absolute pathname of the jar file
     */
    public String getJarFileName() {
        return this.jarFileName;
    }

    private void loadManifest() throws RemoteException {
        if (this.jarManifest == null && !missingManifest) {
            this.jarManifest = remoteJarList.get(0).remoteJar.getManifest();
            if (this.jarManifest == null) {
                missingManifest = true;
            }
        }
    }

    /**
     * Return an array of dependent libraries or a zero length
     * array if no "Class-Path" manifest attribute was defined.
     *
     * @return
     * @throws IOException
     */
    private String[] getLibraryNames() throws IOException {
        if (remoteJarList == null) {
            throw new NoSuchFileException(jarFileName);
        }

        loadManifest();

        if (missingManifest) {
            return new String[0];
        }

        JarAttributes attr = this.jarManifest.getMainAttributes();
        String classPath = null;
        if (attr != null) {
            classPath = attr.getValue(Attributes.Name.CLASS_PATH);
        }
        if (classPath == null) {
            return new String[0];
        }
        String[] rtrn = classPath.split("\\s");
        return rtrn;
    }

    boolean isPrivileged() {
        return this.isPrivileged;
    }

    /**
     * Resolve all of the jar files in the manifest classpath in the library path, and add to the remoteJarList list.
     */
    private void resolveExecClassPath() throws IOException {
        String[] execClassPath = getLibraryNames();
        for (String jarFile : execClassPath) {
            addLibraryToClasspath(jarFile);
        }
    }

    public void close() {
        for (RemoteJarHolder remoteJarHolder : remoteJarList) {
            try {
                remoteJarHolder.remoteJar.close();
            } catch (RemoteException e) {
                // Ignore this error when closing
            }
        }
        closed = true;
    }

    private static class RemoteJarHolder {
        private final String name;
        private String[] pkg;
        private final RemoteJarFileAccessor remoteJar;

        private RemoteJarHolder(String name, RemoteJarFileAccessor remoteJar) {
            this.name = name;
            this.remoteJar = remoteJar;
        }
    }
}
