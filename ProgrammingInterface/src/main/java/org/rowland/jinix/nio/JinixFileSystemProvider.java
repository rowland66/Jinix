package org.rowland.jinix.nio;

import org.rowland.jinix.io.JinixNativeAccessPermission;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.naming.LookupResult;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.rmi.RemoteException;
import java.security.AccessControlException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Created by rsmith on 1/7/2017.
 */
public class JinixFileSystemProvider extends FileSystemProvider {

    FileSystemProvider defaultFileSystemProvider;
    JinixFileSystem jinixFileSystem;

    public JinixFileSystemProvider(FileSystemProvider provider) {
        defaultFileSystemProvider = provider;
        jinixFileSystem = new JinixFileSystem(this);
    }

    FileSystemProvider getDefaultFileSystemProvider() {
        return defaultFileSystemProvider;
    }

    @Override
    public String getScheme() {
        return "file";
    }

    private void checkUri(URI uri) {
        if (!uri.getScheme().equalsIgnoreCase(getScheme()))
            throw new IllegalArgumentException("URI does not match this provider");
        if (uri.getAuthority() != null)
            throw new IllegalArgumentException("Authority component present");
        if (uri.getPath() == null)
            throw new IllegalArgumentException("Path component is undefined");
        if (!uri.getPath().equals("/"))
            throw new IllegalArgumentException("Path component should be '/'");
        if (uri.getQuery() != null)
            throw new IllegalArgumentException("Query component present");
        if (uri.getFragment() != null)
            throw new IllegalArgumentException("Fragment component present");
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        checkUri(uri);
        throw new FileSystemAlreadyExistsException();
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        checkUri(uri);
        return jinixFileSystem;
    }

    @Override
    public Path getPath(URI uri) {

        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return JinixUriUtils.fromUri(jinixFileSystem, uri);
            }
        }

        return defaultFileSystemProvider.getPath(uri);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>[] attrs)
            throws IOException {
        return newFileChannel(path, options, attrs);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>[] attrs) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return new JinixFileChannel(path, options, attrs);
            }
        }

        return defaultFileSystemProvider.newFileChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(
                        dir.toAbsolutePath().toString());
                FileNameSpace fns = (FileNameSpace) lookup.remote;
                final String[] dirList = fns.list(lookup.remainingPath);
                return new DirectoryStream<Path>() {
                    int i = 0;
                    boolean closed = false;
                    boolean iteratorReturned = false;

                    @Override
                    public Iterator<Path> iterator() {
                        if (closed) {
                            throw new IllegalStateException("DirectoryStream is already closed");
                        }
                        if (iteratorReturned) {
                            throw new IllegalStateException("DirectoryStream iterator already returned");
                        }
                        iteratorReturned = true;
                        return new Iterator<Path> () {
                            Path p = null;
                            @Override
                            public boolean hasNext() {
                                try {
                                    if (p != null) {
                                        return true;
                                    }
                                    if (i >= dirList.length) {
                                        return false;
                                    }
                                    Path next = new JinixPath(jinixFileSystem, dirList[i]);
                                    while (!filter.accept(next)) {
                                        if (++i < dirList.length) {
                                            next = new JinixPath(jinixFileSystem, dirList[i]);
                                            continue;
                                        }
                                        p = null;
                                        return false;
                                    }
                                    p = next;
                                    return true;
                                } catch (IOException e1) {
                                    throw new RuntimeException(e);
                                }
                            }

                            @Override
                            public Path next() {
                                if (!hasNext()) {
                                    throw new NoSuchElementException();
                                }
                                Path rtrn = p;
                                p = null;
                                i++;
                                return rtrn;
                            }
                        };
                    }

                    @Override
                    public void close() throws IOException {
                        closed = true;
                    }

                    @Override
                    public void forEach(Consumer<? super Path> action) {
                        for (Path p : this) {
                            action.accept(p);
                        }
                    }

                    @Override
                    public Spliterator<Path> spliterator() {
                        throw new UnsupportedOperationException();
                    }
                };

            }
        }

        return defaultFileSystemProvider.newDirectoryStream(dir, filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>[] attrs) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                try {
                    LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(
                            dir.toAbsolutePath().toString());
                    FileNameSpace fns = (FileNameSpace) lookup.remote;
                    fns.createDirectory(lookup.remainingPath);
                } catch (RemoteException e1) {
                    throw new IOException(e1);
                }
                return;
            }
        }

        defaultFileSystemProvider.createDirectory(dir, attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                try {
                    LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(
                            path.toAbsolutePath().toString());
                    FileNameSpace fns = (FileNameSpace) lookup.remote;
                    fns.delete(lookup.remainingPath);
                    return;
                } catch (ClassCastException e1) {
                    throw new NoSuchFileException(path.toAbsolutePath().toString());
                } catch (RemoteException e1) {
                    throw new IOException(e1);
                }
            }
        }

        defaultFileSystemProvider.delete(path);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                throw new UnsupportedOperationException("JinixFileSystemProvider.copy()");
            }
        }

        defaultFileSystemProvider.copy(source, target, options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                try {
                    LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(
                            source.toAbsolutePath().toString());
                    FileNameSpace fns = (FileNameSpace) lookup.remote;
                    fns.move(lookup.remainingPath, target.toAbsolutePath().toString(), options);
                    return;
                } catch (ClassCastException e1) {
                  throw new NoSuchFileException(source.toAbsolutePath().toString());
                } catch (RemoteException e1) {
                    throw new IOException(e1);
                }
            }
        }

        defaultFileSystemProvider.move(source, target, options);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                throw new UnsupportedOperationException("JinixFileSystemProvider.isSameFile()");
            }
        }

        return defaultFileSystemProvider.isSameFile(path, path2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                throw new UnsupportedOperationException("JinixFileSystemProvider.isHidden()");
            }
        }

        return defaultFileSystemProvider.isHidden(path);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                throw new UnsupportedOperationException("JinixFileSystemProvider.getFileStore()");
            }
        }

        return defaultFileSystemProvider.getFileStore(path);
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(
                        path.toAbsolutePath().toString());
                FileNameSpace fns = (FileNameSpace) lookup.remote;
                if (!fns.exists(lookup.remainingPath))
                {
                    throw new NoSuchFileException(path.toString());
                }
                return;
            }
        }

        defaultFileSystemProvider.checkAccess(path, modes);

    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                if (type.isAssignableFrom(JinixFileAttributeView.class)) {
                    return (V) new JinixFileAttributeView(path);
                }
                return null;
            }
        }

        return defaultFileSystemProvider.getFileAttributeView(path, type, options);

    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return (A) (new JinixFileAttributeView(path)).readAttributes();
            }
        }

        return defaultFileSystemProvider.readAttributes(path, type, options);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                JinixFileAttributeView view = getFileAttributeView(path, JinixFileAttributeView.class, options);
                return view.readAttributes(attributes.split(","));
            }
        }

        return defaultFileSystemProvider.readAttributes(path, attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                JinixFileAttributeView view = getFileAttributeView(path, JinixFileAttributeView.class, options);
                if (attribute.equals("lastModifiedTime")) {
                    view.setTimes((FileTime) value,null , null);
                }

            }
        }

        defaultFileSystemProvider.setAttribute(path, attribute, value, options);
    }
}
