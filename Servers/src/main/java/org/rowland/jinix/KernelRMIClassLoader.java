package org.rowland.jinix;

import org.rowland.jinix.naming.RemoteFileHandle;
import org.rowland.jinix.naming.RemoteJarFileAccessor;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.rmi.server.RMIClassLoaderSpi;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * The RMI ClassLoader that runs in the Jinix kernel process. This ClassLoader uses the codebase URL's to load classes from
 * jar files in the Jinix root NameSpace. This allows Jinix processes to provide implementations of interfaces in kernel
 * server remote method calls.
 */
public class KernelRMIClassLoader extends RMIClassLoaderSpi{

    private static Map<String, ServerRMIClassLoader> codebaseLoaderMap = new HashMap<>();

    public KernelRMIClassLoader() {
        super();
    }

    @Override
    public Class<?> loadClass(String codebase, String name, ClassLoader defaultLoader) throws MalformedURLException, ClassNotFoundException {

        // The default gets the first opportunity to load the class. The defaultLoader parameter is often null.
        if (defaultLoader != null) {
            try {
                Class<?> c = Class.forName(name, false, defaultLoader);
                return c;
            } catch (ClassNotFoundException e) {
                // If unable to find the class in the default loader, fall through here.
            }
        }

        // If a codebase is provided, get a loader for the codebase. These loaders are cached using codebase as the key.
        // Each codebase classloader has the processes primary ServerRMIClassLoader as a parent and defers loading to the parent.
        ClassLoader loader = null;
        if (codebase != null) {
            loader = getClassLoader(codebase);
        }

        // The parent classloader is just the context classloader associated with the thread. On RMI threads, this will
        // be the ApplicationClassLoader.
        ClassLoader parent = getRMIContextClassLoader();

        try {
            return Class.forName(name, false, (loader != null ? loader : parent));
        } catch (ClassNotFoundException e) {
            throw e;
        }
    }

    @Override
    public Class<?> loadProxyClass(String codebase, String[] interfaces, ClassLoader defaultLoader) throws MalformedURLException, ClassNotFoundException {

        ClassLoader parent = getRMIContextClassLoader();

        ClassLoader loader = null;
        if (codebase != null) {
            loader = getClassLoader(codebase);
        }

        try {
            Class<?> c = loadProxyClass(interfaces, defaultLoader, (loader != null ? loader : parent), true);
            return c;
        } catch (ClassNotFoundException e) {
            throw e;
        }
    }

    @Override
    public synchronized ClassLoader getClassLoader(String codebase) throws MalformedURLException {

        if (codebase == null) {
            return null;
        }

        if (codebase.startsWith("file://")) {
            codebase = codebase.substring("file://".length());
        }

        if (codebaseLoaderMap.containsKey(codebase)) {
            return codebaseLoaderMap.get(codebase);
        }

        ClassLoader parent = getRMIContextClassLoader();

        try {
            Object lookup = JinixKernel.getNameSpaceRoot().lookup(codebase);
            if (lookup == null || !(lookup instanceof RemoteFileHandle)) {
                return null;
            }
            RemoteJarFileAccessor remoteJarAccessor = (RemoteJarFileAccessor) ((RemoteFileHandle) lookup).getParent().
                    getRemoteFileAccessor(-1, ((RemoteFileHandle) lookup).getPath(), EnumSet.of(StandardOpenOption.READ));
            ServerRMIClassLoader cl = new ServerRMIClassLoader(codebase, false, remoteJarAccessor, parent);
            codebaseLoaderMap.put(codebase, cl);
            return cl;
        } catch (RemoteException e) {
            throw new RuntimeException("Internal error", e);
        } catch (FileAlreadyExistsException | NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public String getClassAnnotation(Class<?> cl) {
        String name = cl.getName();

        // This code is copied from the JDK implementation. Not sure what it does.
        int nameLength = name.length();
        if (nameLength > 0 && name.charAt(0) == '[') {
            // skip past all '[' characters (see bugid 4211906)
            int i = 1;
            while (nameLength > i && name.charAt(i) == '[') {
                i++;
            }
            if (nameLength > i && name.charAt(i) != 'L') {
                return null;
            }
        }

        // This method is called when objects are marshalled out to a stream. This adds a string that annotates the
        // class. Only ServerRMIClassLoader classes need to be annotated. Any other classes should be part of the JDK libraries
        // or Jinix libraries that are provided in every process classpath.
        ClassLoader loader = cl.getClassLoader();
        if (loader != null && loader instanceof ServerRMIClassLoader) {
            return ((ServerRMIClassLoader) loader).getJarFileName();
        }

        return null;
    }

    /**
     * Close all of the codebase ClassLoaders opened during the life of the process. This method should be called when
     * the Jinix process shuts down.
     */
    static void close() {
        for (ServerRMIClassLoader cl : codebaseLoaderMap.values()) {
            cl.close();
        }
        codebaseLoaderMap.clear();
    }

    // *****************************************************************************************************************
    // The following methods were copied with slight modification from the JDK RMIClassLoader. They implement some
    // pretty complicated logic for creating Proxy classes that I don't understand.
    // *****************************************************************************************************************

    /**
     * Return the class loader to be used as the parent for an RMI class
     * loader used in the current execution context.
     */
    private static ClassLoader getRMIContextClassLoader() {
        /*
         * The current implementation simply uses the current thread's
         * context class loader.
         */
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * Define a proxy class in the default loader if appropriate.
     * Define the class in an RMI class loader otherwise.  The proxy
     * class will implement classes which are named in the supplied
     * interfaceNames.
     */
    private static Class<?> loadProxyClass(String[] interfaceNames,
                                           ClassLoader defaultLoader,
                                           ClassLoader codebaseLoader,
                                           boolean preferCodebase)
            throws ClassNotFoundException
    {
        ClassLoader proxyLoader = null;
        Class<?>[] classObjs = new Class<?>[interfaceNames.length];
        boolean[] nonpublic = { false };

        defaultLoaderCase:
        if (defaultLoader != null) {
            try {
                proxyLoader =
                        loadProxyInterfaces(interfaceNames, defaultLoader,
                                classObjs, nonpublic);
            } catch (ClassNotFoundException e) {
                break defaultLoaderCase;
            }
            if (!nonpublic[0]) {
                if (preferCodebase) {
                    try {
                        return Proxy.getProxyClass(codebaseLoader, classObjs);
                    } catch (IllegalArgumentException e) {
                    }
                }
                proxyLoader = defaultLoader;
            }
            return loadProxyClass(proxyLoader, classObjs);
        }

        nonpublic[0] = false;
        proxyLoader = loadProxyInterfaces(interfaceNames, codebaseLoader,
                classObjs, nonpublic);

        if (!nonpublic[0]) {
            proxyLoader = codebaseLoader;
        }
        return loadProxyClass(proxyLoader, classObjs);
    }

    /**
     * Define a proxy class in the given class loader.  The proxy
     * class will implement the given interfaces Classes.
     */
    private static Class<?> loadProxyClass(ClassLoader loader, Class<?>[] interfaces)
            throws ClassNotFoundException
    {
        try {
            return Proxy.getProxyClass(loader, interfaces);
        } catch (IllegalArgumentException e) {
            throw new ClassNotFoundException(
                    "error creating dynamic proxy class", e);
        }
    }

    /*
     * Load Class objects for the names in the interfaces array fron
     * the given class loader.
     *
     * We pass classObjs and nonpublic arrays to avoid needing a
     * multi-element return value.  nonpublic is an array to enable
     * the method to take a boolean argument by reference.
     *
     * nonpublic array is needed to signal when the return value of
     * this method should be used as the proxy class loader.  Because
     * null represents a valid class loader, that value is
     * insufficient to signal that the return value should not be used
     * as the proxy class loader.
     */
    private static ClassLoader loadProxyInterfaces(String[] interfaces,
                                                   ClassLoader loader,
                                                   Class<?>[] classObjs,
                                                   boolean[] nonpublic)
            throws ClassNotFoundException
    {
        /* loader of a non-public interface class */
        ClassLoader nonpublicLoader = null;

        for (int i = 0; i < interfaces.length; i++) {
            Class<?> cl =
                    (classObjs[i] = Class.forName(interfaces[i], false, loader));

            if (!Modifier.isPublic(cl.getModifiers())) {
                ClassLoader current = cl.getClassLoader();
                if (!nonpublic[0]) {
                    nonpublicLoader = current;
                    nonpublic[0] = true;
                } else if (current != nonpublicLoader) {
                    throw new IllegalAccessError(
                            "non-public interfaces defined in different " +
                                    "class loaders");
                }
            }
        }
        return nonpublicLoader;
    }


}
