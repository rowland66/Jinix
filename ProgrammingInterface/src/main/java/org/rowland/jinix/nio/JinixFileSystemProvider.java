package org.rowland.jinix.nio;

import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinixspi.JinixNativeAccessPermission;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

    FileSystemProvider defaultFileSystemProvider; // this field can be null
    JinixFileSystem jinixFileSystem;

    /**
     * Represents the flags from a user-supplied set of open options.
     */
    private static class Flags {
        boolean read;
        boolean write;
        boolean append;

        static Flags toFlags(Set<? extends OpenOption> options) {
            Flags flags = new Flags();
            for (OpenOption option: options) {
                if (option instanceof StandardOpenOption) {
                    switch ((StandardOpenOption)option) {
                        case READ : flags.read = true; break;
                        case WRITE : flags.write = true; break;
                        case APPEND : flags.append = true; break;
                        default: throw new UnsupportedOperationException();
                    }
                    continue;
                }
                if (option == null)
                    throw new NullPointerException();
                throw new UnsupportedOperationException();
            }
            return flags;
        }
    }

    /**
     * This contructor is called by FileSystems.getDefaultProvider() to create
     * FileSystemProvider objects for the JVM. This constructor is also called
     * from JinixFileSystem to create provider to go with an explicitly created
     * JinixFileSystem
     *
     * @param provider a default provider, can be null in which case only Jinix
     *                 is supported.
     */
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
        if (defaultFileSystemProvider == null) {
            return JinixUriUtils.fromUri(jinixFileSystem, uri);
        }
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
        if (defaultFileSystemProvider == null) {

            // default is reading; append => writing
            if (!options.contains(StandardOpenOption.READ) && !options.contains(StandardOpenOption.WRITE)) {
                if (options.contains(StandardOpenOption.APPEND)) {
                    options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                } else {
                    options = EnumSet.of(StandardOpenOption.READ);
                }
            }

            JinixFileDescriptor fd = new JinixFileDescriptor(open(path, options));
            return JinixFileChannel.open(fd, options, null);
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                // default is reading; append => writing
                if (!options.contains(StandardOpenOption.READ) && !options.contains(StandardOpenOption.WRITE)) {
                    if (options.contains(StandardOpenOption.APPEND)) {
                        options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    } else {
                        options = EnumSet.of(StandardOpenOption.READ);
                    }
                }
                JinixFileDescriptor fd = new JinixFileDescriptor(open(path, options));
                return JinixFileChannel.open(fd, options, null);
            }
        }

        return defaultFileSystemProvider.newFileChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        if (defaultFileSystemProvider == null) {
            return newDirectoryStreamInternal(dir, filter);
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return newDirectoryStreamInternal(dir, filter);
            }
        }

        return defaultFileSystemProvider.newDirectoryStream(dir, filter);
    }

    private DirectoryStream<Path> newDirectoryStreamInternal(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        Object lookup = JinixRuntime.getRuntime().lookup(
                dir.toAbsolutePath().normalize().toString());
        String[] dirList = null;
        if (lookup instanceof RemoteFileHandle &&
                ((RemoteFileHandle) lookup).getAttributes().type == DirectoryFileData.FileType.DIRECTORY ) {
            dirList = ((RemoteFileHandle) lookup).getParent().list(((RemoteFileHandle) lookup).getPath());
        }
        if (lookup instanceof FileNameSpace) {
            dirList = ((FileNameSpace) lookup).list("/");
        }

        if (dirList != null) {
            String[] finalDirList = dirList;
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
                    return new Iterator<Path>() {
                        Path p = null;

                        @Override
                        public boolean hasNext() {
                            try {
                                if (p != null) {
                                    return true;
                                }
                                if (i >= finalDirList.length) {
                                    return false;
                                }
                                Path next = new JinixPath(jinixFileSystem, finalDirList[i]);
                                while (!filter.accept(next)) {
                                    if (++i < finalDirList.length) {
                                        next = new JinixPath(jinixFileSystem, finalDirList[i]);
                                        continue;
                                    }
                                    p = null;
                                    return false;
                                }
                                p = dir.resolve(next);
                                return true;
                            } catch (IOException e1) {
                                throw new RuntimeException(e1);
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
        throw new NotDirectoryException(dir.toAbsolutePath().toString());
    }
    @Override
    public void createDirectory(Path dir, FileAttribute<?>[] attrs) throws IOException {
        if (defaultFileSystemProvider == null) {
            createDirectoryInternal(dir, attrs);
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                try {
                    createDirectoryInternal(dir, attrs);
                    return;
                } catch (RemoteException e1) {
                    throw new IOException(e1);
                }
            }
        }

        defaultFileSystemProvider.createDirectory(dir, attrs);
    }

    private void createDirectoryInternal(Path dir, FileAttribute<?>[] attrs) throws IOException {
        String dirString = dir.toAbsolutePath().normalize().toString();

        dirString = dirString.substring(0, dirString.lastIndexOf('/'));
        Object lookup = JinixRuntime.getRuntime().lookup(dirString);
        if (lookup instanceof RemoteFileHandle && ((RemoteFileHandle) lookup).getAttributes().type == DirectoryFileData.FileType.DIRECTORY ) {
            ((RemoteFileHandle) lookup).getParent().createDirectory(((RemoteFileHandle) lookup).getPath(), dir.getFileName().toString());
            return;
        }
        if (lookup instanceof FileNameSpace) {
            ((FileNameSpace) lookup).createDirectory("/", dir.getFileName().toString());
            return;
        }
        throw new FileAlreadyExistsException(dir.toAbsolutePath().toString());
    }

    @Override
    public void delete(Path path) throws IOException {
        if (defaultFileSystemProvider == null) {
            deleteInternal(path);
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                try {
                    deleteInternal(path);
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

    private void deleteInternal(Path path) throws IOException {
        Object lookup = JinixRuntime.getRuntime().lookup(path.toAbsolutePath().normalize().toString());
        if (lookup instanceof RemoteFileHandle) {
            ((RemoteFileHandle) lookup).getParent().delete(((RemoteFileHandle) lookup).getPath());
            return;
        }
        if (lookup instanceof FileNameSpace) {
            throw new DirectoryNotEmptyException(path.toAbsolutePath().toString());
        }
        throw new NoSuchFileException(path.toAbsolutePath().toString());
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        if (defaultFileSystemProvider == null) {
            copyInternal(source, target, options);
            return;
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                copyInternal(source, target, options);
                return;
            }
        }

        defaultFileSystemProvider.copy(source, target, options);
    }

    private void copyInternal(Path source, Path target, CopyOption... options) throws IOException {
        Object srcLookup = JinixRuntime.getRuntime().lookup(source.toAbsolutePath().normalize().toString());
        if (srcLookup instanceof RemoteFileHandle) {
            Path targetDirectory = target.toAbsolutePath().normalize().getParent();
            Object targetLookup = JinixRuntime.getRuntime().lookup(targetDirectory.toString());
            if (targetLookup instanceof RemoteFileHandle &&
                    ((RemoteFileHandle) targetLookup).getAttributes().type == DirectoryFileData.FileType.DIRECTORY) {
                copyInternal2((RemoteFileHandle) srcLookup, (RemoteFileHandle) targetLookup, target.getFileName().toString(), options);
                return;
            }
            if (targetLookup instanceof FileNameSpace) {
                RemoteFileHandle targetDirectoryFile = (RemoteFileHandle) ((FileNameSpace) targetLookup).lookup(-1, "/.");
                copyInternal2((RemoteFileHandle) srcLookup, targetDirectoryFile, target.getFileName().toString(), options);
                return;
            }
            throw new NoSuchFileException(target.toAbsolutePath().toString());
        }
        throw new NoSuchFileException(source.toAbsolutePath().toString());
    }

    private void copyInternal2(RemoteFileHandle srcLookup, RemoteFileHandle targetLookup, String fileName, CopyOption... options)
            throws IOException {

        if ( targetLookup.getParent().getURI().equals(srcLookup.getParent().getURI()) ) {
            srcLookup.getParent().copy(srcLookup, targetLookup, fileName, options);
            return;
        } else {
            if (srcLookup.getAttributes().type == DirectoryFileData.FileType.DIRECTORY) {
                throw new UnsupportedOperationException("Directory move between filesystems is not supported yet");
            } else {
                srcLookup.getParent().copy(srcLookup, targetLookup, fileName, options);
                return;
            }
        }
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        if (defaultFileSystemProvider == null) {
            moveInternal(source, target, options);
            return;
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                try {
                    moveInternal(source, target, options);
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

    private void moveInternal(Path source, Path target, CopyOption... options) throws IOException {
        Object srcLookup = JinixRuntime.getRuntime().lookup(source.toAbsolutePath().normalize().toString());
        if (srcLookup instanceof RemoteFileHandle) {
            target = target.toAbsolutePath().normalize();
            Path targetDirectory = target.getParent();
            String fileName = target.getFileName().toString();
            Object targetLookup = JinixRuntime.getRuntime().lookup(targetDirectory.toString());

            if (targetLookup instanceof RemoteFileHandle &&
                    ((RemoteFileHandle) targetLookup).getAttributes().type == DirectoryFileData.FileType.DIRECTORY) {
                moveInternal2((RemoteFileHandle) srcLookup, (RemoteFileHandle) targetLookup, fileName, options);
                return;
            }

            if (targetLookup instanceof FileNameSpace) {
                RemoteFileHandle targetDirectoryFile = (RemoteFileHandle) ((FileNameSpace) targetLookup).lookup(-1, "/.");
                moveInternal2((RemoteFileHandle) srcLookup, targetDirectoryFile, fileName, options);
                return;
            }

            throw new NoSuchFileException(target.toAbsolutePath().toString());
        }

        throw new NoSuchFileException(source.toAbsolutePath().toString());
    }

    private void moveInternal2(RemoteFileHandle srcLookup, RemoteFileHandle targetLookup, String fileName, CopyOption... options)
            throws IOException {
        if ( targetLookup.getParent().getURI().equals(srcLookup.getParent().getURI()) ) {
            srcLookup.getParent().move(srcLookup, targetLookup, fileName, options);
            return;
        } else {
            if (srcLookup.getAttributes().type == DirectoryFileData.FileType.DIRECTORY) {
                throw new UnsupportedOperationException("Directory move between filesystems is not supported yet");
            } else {
                srcLookup.getParent().copy(srcLookup, targetLookup, fileName, options);
                srcLookup.getParent().delete(srcLookup.getPath());
                return;
            }
        }
    }
    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        if (defaultFileSystemProvider == null) {
            throw new UnsupportedOperationException("JinixFileSystemProvider.isSameFile()");
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                if (path.toAbsolutePath().equals(path2.toAbsolutePath())) {
                    return true;
                }
                return false;
            }
        }

        return defaultFileSystemProvider.isSameFile(path, path2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        if (defaultFileSystemProvider == null) {
            return false;
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return false;
            }
        }

        return defaultFileSystemProvider.isHidden(path);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        if (defaultFileSystemProvider == null) {
            throw new UnsupportedOperationException("JinixFileSystemProvider.getFileStore()");
        }
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
        if (defaultFileSystemProvider == null) {
            checkAccessInternal(path, modes);
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                checkAccessInternal(path, modes);
                return;
            }
        }

        defaultFileSystemProvider.checkAccess(path, modes);
    }

    private void checkAccessInternal(Path path, AccessMode... modes) throws IOException {
        Object lookup = JinixRuntime.getRuntime().lookup(path.toAbsolutePath().normalize().toString());
        if (lookup == null) {
            throw new NoSuchFileException(path.toAbsolutePath().toString());
        }
        if (!(lookup instanceof RemoteFileHandle) && !(lookup instanceof FileNameSpace)) {
            throw new AccessDeniedException(path.toAbsolutePath().toString());
        }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        if (defaultFileSystemProvider == null) {
            if (type.isAssignableFrom(JinixFileAttributeView.class)) {
                return (V) new JinixFileAttributeView(path);
            }
            return null;
        }
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
        if (defaultFileSystemProvider == null) {
            return (A) (new JinixFileAttributeView(path)).readAttributes();
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return (A) (new JinixFileAttributeView(path)).readAttributes();
            }
        }

        try {
            Path defaultFileSystemPath = defaultFileSystemProvider.getPath(
                    new URI(defaultFileSystemProvider.getScheme(), "", path.toString(), null));
            return defaultFileSystemProvider.readAttributes(defaultFileSystemPath, type, options);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for underlying filesystem");
        }
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        if (defaultFileSystemProvider == null) {
            JinixFileAttributeView view = getFileAttributeView(path, JinixFileAttributeView.class, options);
            return view.readAttributes(attributes.split(","));
        }
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
        if (defaultFileSystemProvider == null) {
            JinixFileAttributeView view = getFileAttributeView(path, JinixFileAttributeView.class, options);
            if (attribute.equals("lastModifiedTime")) {
                view.setTimes((FileTime) value,null , null);
            }
            return;
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                JinixFileAttributeView view = getFileAttributeView(path, JinixFileAttributeView.class, options);
                if (attribute.equals("lastModifiedTime")) {
                    view.setTimes((FileTime) value,null , null);
                }
                return;
            }
        }

        defaultFileSystemProvider.setAttribute(path, attribute, value, options);
    }

    private RemoteFileAccessor open(Path path, Set<? extends OpenOption> options)
            throws IOException {
        try {
            int pid = JinixRuntime.getRuntime().getPid();
            path = path.toAbsolutePath();
            Object lookup = JinixRuntime.getRuntime().lookup(path.toString());
            if (lookup != null) {
                if (lookup instanceof RemoteFileHandle && ((RemoteFileHandle) lookup).getAttributes().type == DirectoryFileData.FileType.FILE) {
                    // Throws FileAlreadyExistsException with CREATE_NEW option
                    return ((RemoteFileHandle) lookup).getParent().getRemoteFileAccessor(pid, ((RemoteFileHandle) lookup).getPath(), options);
                } else {
                    throw new NoSuchFileException(path.toString());
                }
            } else {
                String fileName = path.getFileName().toString();
                Object directoryLookup = JinixRuntime.getRuntime().lookup(path.getParent().toString());
                if (directoryLookup != null) {
                    if (directoryLookup instanceof RemoteFileHandle && ((RemoteFileHandle) directoryLookup).getAttributes().type == DirectoryFileData.FileType.DIRECTORY) {
                        return ((RemoteFileHandle) directoryLookup).getParent().getRemoteFileAccessor(pid,
                                ((RemoteFileHandle) directoryLookup).getPath()+"/"+fileName, options);
                    } else if (directoryLookup instanceof FileNameSpace) {
                        directoryLookup = JinixRuntime.getRuntime().lookup(path.getParent().toString() + "/.");
                        return ((RemoteFileHandle) directoryLookup).getParent().getRemoteFileAccessor(pid,
                                ((RemoteFileHandle) directoryLookup).getPath()+"/"+fileName, options);
                    }
                }
            }
            throw new NoSuchFileException(path.toString());
        } catch (RemoteException e) {
            throw new IOException("IOException creating file channel", e.getCause());
        }
    }
}
