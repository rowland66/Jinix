package org.rowland.jinix.nio;

import org.rowland.jinix.io.JinixNativeAccessPermission;
import org.rowland.jinix.lang.JinixRuntime;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.security.AccessControlException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * An implementation FileSystem that may overlay the existing platform default
 * FileSystem.
 */
public class JinixFileSystem extends FileSystem {

    private final JinixFileSystemProvider provider;
    private FileSystem defaultFileSystem; // this field can be null
    private byte[] defaultDirectory = null;
    private final JinixPath rootDirectory;

    public JinixFileSystem() {
        this(new JinixFileSystemProvider(null));
    }

    // package-private
    JinixFileSystem(JinixFileSystemProvider provider) {
        this.provider = provider;
        if (provider.getDefaultFileSystemProvider() != null) {
            this.defaultFileSystem = provider.getDefaultFileSystemProvider().
                    getFileSystem(URI.create("file:///"));
        }
        // the root directory
        this.rootDirectory = new JinixPath(this, "/");
    }

    // package-private
    byte[] defaultDirectory() {
        if (this.defaultDirectory == null) {
            this.defaultDirectory = Util.toBytes(JinixPath.normalizeAndCheck(System.getProperty(JinixRuntime.JINIX_ENV_PWD)));
            if (this.defaultDirectory[0] != '/') {
                throw new RuntimeException("default directory must be absolute");
            }
        }
        return defaultDirectory;
    }

    JinixPath rootDirectory() {
        return rootDirectory;
    }

    @Override
    public final FileSystemProvider provider() {
        return provider;
    }

    @Override
    public final String getSeparator() {
        if (defaultFileSystem == null) {
            return "/";
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return "/";
            }
        }

        return defaultFileSystem.getSeparator();

    }

    @Override
    public final boolean isOpen() {
        if (defaultFileSystem == null) {
            return true;
        }

        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return true;
            }
        }

        return defaultFileSystem.isOpen();
    }

    @Override
    public final boolean isReadOnly() {
        if (defaultFileSystem == null) {
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

        return defaultFileSystem.isReadOnly();
    }

    @Override
    public final void close() throws IOException {
        if (defaultFileSystem == null) {
            throw new UnsupportedOperationException();
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                throw new UnsupportedOperationException();
            }
        }

        defaultFileSystem.close();
    }

    /**
     * Unix systems only have a single root directory (/)
     */
    @Override
    public final Iterable<Path> getRootDirectories() {
        if (defaultFileSystem == null) {
            return getRootDirectoriesInternal();
        }

        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return getRootDirectoriesInternal();
            }
        }

        return defaultFileSystem.getRootDirectories();
    }

    private final Iterable<Path> getRootDirectoriesInternal() {
        final List<Path> allowedList =
                Collections.unmodifiableList(Arrays.asList((Path)rootDirectory));
        return new Iterable<Path>() {
            public Iterator<Path> iterator() {
                try {
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null)
                        sm.checkRead(rootDirectory.toString());
                    return allowedList.iterator();
                } catch (SecurityException x) {
                    List<Path> disallowed = Collections.emptyList();
                    return disallowed.iterator();
                }
            }
        };
    }

    @Override
    public final Iterable<FileStore> getFileStores() {
        if (defaultFileSystem == null) {
            return null;
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return null;
            }
        }

        return defaultFileSystem.getFileStores();
    }

    @Override
    public Path getPath(String first, String... more) {
        if (defaultFileSystem == null) {
            return getPathInternal(first, more);
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return getPathInternal(first, more);
            }
        }

        return defaultFileSystem.getPath(first, more);
    }

    private final Path getPathInternal(String first, String... more) {
        String path;
        if (more.length == 0) {
            path = first;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(first);
            for (String segment: more) {
                if (segment.length() > 0) {
                    if (sb.length() > 0)
                        sb.append('/');
                    sb.append(segment);
                }
            }
            path = sb.toString();
        }
        return new JinixPath(this, path);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        if (defaultFileSystem == null) {
            return getPathMatcherInternal(syntaxAndPattern);
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return getPathMatcherInternal(syntaxAndPattern);
            }
        }

        return defaultFileSystem.getPathMatcher(syntaxAndPattern);
    }

    private PathMatcher getPathMatcherInternal(String syntaxAndInput) {
        int pos = syntaxAndInput.indexOf(':');
        if (pos <= 0 || pos == syntaxAndInput.length())
            throw new IllegalArgumentException();
        String syntax = syntaxAndInput.substring(0, pos);
        String input = syntaxAndInput.substring(pos+1);

        String expr;
        if (syntax.equals(GLOB_SYNTAX)) {
            expr = Globs.toUnixRegexPattern(input);
        } else {
            if (syntax.equals(REGEX_SYNTAX)) {
                expr = input;
            } else {
                throw new UnsupportedOperationException("Syntax '" + syntax +
                        "' not recognized");
            }
        }

        // return matcher
        final Pattern pattern = compilePathMatchPattern(expr);

        return new PathMatcher() {
            @Override
            public boolean matches(Path path) {
                return pattern.matcher(path.toString()).matches();
            }
        };
    }

    private static final String GLOB_SYNTAX = "glob";
    private static final String REGEX_SYNTAX = "regex";

    @Override
    public final UserPrincipalLookupService getUserPrincipalLookupService() {
        if (defaultFileSystem == null) {
            return null;
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return null;
            }
        }

        return defaultFileSystem.getUserPrincipalLookupService();
    }

    // Override if the platform has different path match requrement, such as
    // case insensitive or Unicode canonical equal on MacOSX
    Pattern compilePathMatchPattern(String expr) {
        return Pattern.compile(expr);
    }

    // Override if the platform uses different Unicode normalization form
    // for native file path. For example on MacOSX, the native path is stored
    // in Unicode NFD form.
    char[] normalizeNativePath(char[] path) {
        return path;
    }

    // Override if the native file path use non-NFC form. For example on MacOSX,
    // the native path is stored in Unicode NFD form, the path need to be
    // normalized back to NFC before passed back to Java level.
    String normalizeJavaPath(String path) {
        return path;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        if (defaultFileSystem == null) {
            return Collections.EMPTY_SET;
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return Collections.EMPTY_SET;
            }
        }

        return defaultFileSystem.supportedFileAttributeViews();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        if (defaultFileSystem == null) {
            return null;
        }
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return null;
            }
        }

        return defaultFileSystem.newWatchService();
    }
}
