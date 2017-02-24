package org.rowland.jinix.io;

import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.DirectoryFileData;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.naming.LookupResult;
import org.rowland.jinix.naming.RemainingPath;
import org.rowland.jinix.nio.JinixFileAttributes;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;

/**
 * Created by rsmith on 12/2/2016.
 */
public class JinixFile {

    private String path;

    public JinixFile(String pathname) {
        if (pathname == null) {
            this.path = "";
        }
        this.path = normalize(pathname);
    }

    public JinixFile(String parent, String child) {
        if (child == null) {
            throw new NullPointerException();
        }

        String path;
        if (parent != null) {
            if (parent.equals("")) {
                path = normalize(child);
            } else {
                path = resolve(normalize(parent), normalize(child));
            }
        } else {
            path = normalize(child);
        }

        this.path = path;
    }

    public JinixFile(URI uri) {

        // Check our many preconditions
        if (!uri.isAbsolute())
            throw new IllegalArgumentException("URI is not absolute");
        if (uri.isOpaque())
            throw new IllegalArgumentException("URI is not hierarchical");
        String scheme = uri.getScheme();
        if ((scheme == null) || !scheme.equalsIgnoreCase("file"))
            throw new IllegalArgumentException("URI scheme is not \"file\"");
        if (uri.getAuthority() != null)
            throw new IllegalArgumentException("URI has an authority component");
        if (uri.getFragment() != null)
            throw new IllegalArgumentException("URI has a fragment component");
        if (uri.getQuery() != null)
            throw new IllegalArgumentException("URI has a query component");

        // Okay, now initialize
        String path = uri.getPath();
        if (path.equals(""))
            throw new IllegalArgumentException("URI path component is empty");
        if ((path.length() > 1) && path.endsWith("/")) {
            // "/foo/" --> "/foo"
            path = path.substring(0, path.length() - 1);
        }

        this.path = path;
    }

    public JinixFile(JinixFile parent, String child) {
        if (child == null) {
            throw new NullPointerException();
        }

        String path;
        if (parent != null) {
            if (parent.path.equals("")) {
                path = normalize(child);
            } else {
                path = resolve(parent.path, normalize(child));
            }
        } else {
            path = normalize(child);
        }

        this.path = path;
    }

    public String getName() {
        int index = path.lastIndexOf('/');
        if (index < 0) return path;
        return path.substring(path.lastIndexOf('/')+1);
    }

    public String getParent() {
        int prefixLength = 0;
        if (path.length() > 0 && path.charAt(0) == '/') prefixLength = 1;
        int index = path.lastIndexOf('/');
        if (index < prefixLength) {
            if ((prefixLength > 0) && (path.length() > prefixLength))
                return path.substring(0, prefixLength);
            return null;
        }
        return path.substring(0, index);
    }

    public JinixFile getParentFile() {
        String p = this.getParent();
        if (p == null) return null;
        return new JinixFile(p);
    }

    public String getPath() {
        return path;
    }

    public boolean isAbsolute() {
        return path.startsWith(Character.toString('/'));
    }

    public String getAbsolutePath() {

        if (path.startsWith("/")) {
            return path;
        }

        String workingDirectory = System.getProperty(JinixRuntime.JINIX_ENV_PWD);

        return workingDirectory + "/" + this.path;
    }

    public JinixFile getAbsoluteFile() {
        String absPath = getAbsolutePath();
        return new JinixFile(absPath);
    }

    public String getCanonicalPath() throws IOException {
        String absolutePath = getAbsolutePath();

        if (!absolutePath.endsWith("/")) absolutePath = absolutePath + "/";

        String[] cannonicalPathElement = new String[256];
        int cannonicalPathSize = 0;
        String remainingPath = absolutePath.substring(1);
        int i;
        while ((i = remainingPath.indexOf('/')) > -1) {
            String element = remainingPath.substring(0, i);
            remainingPath = remainingPath.substring(i+1);
            if (element.equals(".")) {
                continue;
            }
            if (element.equals("..")) {
                cannonicalPathSize--;
                if (cannonicalPathSize < 0) {
                    throw new IOException("Invalid file path: "+getAbsolutePath());
                }
                continue;
            }
            if (cannonicalPathSize == cannonicalPathElement.length - 1) {
                throw new RuntimeException("Jinix file path to large: " + getAbsolutePath());
            }
            cannonicalPathElement[cannonicalPathSize++] = element;
        }

        if (cannonicalPathSize == 0) return "/";

        String canonicalPath = "";
        for (i=0; i<cannonicalPathSize; i++) {
            canonicalPath = canonicalPath + "/" + cannonicalPathElement[i];
        }

        return canonicalPath;
    }

    public JinixFile getCanonicalFile() throws IOException {
        String canonPath = getCanonicalPath();
        return new JinixFile(canonPath);
    }

    public boolean exists() throws InvalidPathException {

        try {
            try {
                LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
                if (!(lookup.remote instanceof FileNameSpace)) {
                    return false;
                }
                FileNameSpace fns = (FileNameSpace) lookup.remote;
                return fns.exists(lookup.remainingPath);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isDirectory() {

        try {
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            return new JinixFileAttributes(
                    fns.getFileAttributes(lookup.remainingPath)).isDirectory();

        } catch (NoSuchFileException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isFile() {

        try {
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            return new JinixFileAttributes(
                    fns.getFileAttributes(lookup.remainingPath)).isRegularFile();

        } catch (NoSuchFileException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long lastModified() {

        try {
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            return new JinixFileAttributes(fns.getFileAttributes(
                            lookup.remainingPath)).lastModifiedTime().toMillis();
        } catch (ClassCastException | NoSuchFileException e) {
            return 0L;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setLastModified(long time) {
        try {
            if (!exists()) {
                throw new RuntimeException("Failure setting lastModified");
            }
            DirectoryFileData dfd = new DirectoryFileData();
            dfd.lastModified = time;
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fns.setFileAttributes(lookup.remainingPath, dfd);
        } catch (NoSuchFileException e) {
            return;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long length() {

        try {
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            return new JinixFileAttributes(fns.getFileAttributes(lookup.remainingPath)).size();
        } catch (NoSuchFileException e) {
            return 0L;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean delete() {
        try {
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fns.delete(lookup.remainingPath);
            return true;
        } catch (NoSuchFileException | DirectoryNotEmptyException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String[] list() {
        return AccessController.doPrivileged(new PrivilegedAction<String[]>() {
            @Override
            public String[] run() {
                try {
                    LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
                    FileNameSpace fns = (FileNameSpace) lookup.remote;
                    return fns.list(lookup.remainingPath);
                } catch (NotDirectoryException e) {
                    return null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public JinixFile[] listFiles() {

        try {
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            DirectoryFileData[] dirData = fns.listDirectoryFileData(lookup.remainingPath);

            JinixFile[] rtrnJinixFile = new JinixFile[dirData.length];
            for (int i=0; i<dirData.length; i++) {
                JinixFile jf = new JinixFile(this, dirData[i].name);
                rtrnJinixFile[i] = jf;
            }
            return rtrnJinixFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean createNewFile() throws IOException {
        LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
        FileNameSpace fns = (FileNameSpace) lookup.remote;
        return fns.createFileAtomically(lookup.remainingPath);
    }

    public boolean mkdir() {
        try {
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fns.createDirectory(lookup.remainingPath);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean mkdirs() {
        try {
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fns.createDirectories(lookup.remainingPath);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean renameTo(JinixFile dest) {
        try {
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(getCanonicalPath());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fns.move(lookup.remainingPath, dest.getAbsolutePath());
            return true;
        } catch (NoSuchFileException | FileAlreadyExistsException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Integer[] getOffsets(String path, String root) {
        ArrayList<Integer> list = new ArrayList<>();
        if (path.equals("")) {
            // empty path considered to have one name element
            list.add(0);
        } else {
            int start = 0;
            int off = 0;
            if (root != null) {
                start = root.length();
                off = root.length();
            }
            while (off < path.length()) {
                if (path.charAt(off) != '/') {
                    off++;
                } else {
                    list.add(start);
                    start = ++off;
                }
            }
            if (start != off)
                list.add(start);
        }
        return list.toArray(new Integer[list.size()]);
    }

    private String elementAsString(String path, Integer[] offsets, int i) {
        if (i == (offsets.length-1))
            return path.substring(offsets[i]);
        return path.substring(offsets[i], offsets[i+1]-1);
    }

    private String normalize(String path) {
        try {
            String root = null;
            if (path.length() > 0 && path.charAt(0) == '/') {
                root = "/";
            }
            Integer[] offsets = getOffsets(path, root);
            final int count = offsets.length;
            if (count == 0 || path.length() == 0)
                return path;

            boolean[] ignore = new boolean[count];      // true => ignore name
            int remaining = count;                      // number of names remaining

            // multiple passes to eliminate all occurrences of "." and "name/.."
            int prevRemaining;
            do {
                prevRemaining = remaining;
                int prevName = -1;
                for (int i=0; i<count; i++) {
                    if (ignore[i])
                        continue;

                    String name = elementAsString(path, offsets, i);

                    // not "." or ".."
                    if (name.length() > 2) {
                        prevName = i;
                        continue;
                    }

                    // "." or something else
                    if (name.length() == 1) {
                        // ignore "."
                        if (name.charAt(0) == '.') {
                            ignore[i] = true;
                            remaining--;
                        } else {
                            prevName = i;
                        }
                        continue;
                    }

                    // not ".."
                    if (name.charAt(0) != '.' || name.charAt(1) != '.') {
                        prevName = i;
                        continue;
                    }

                    // ".." found
                    if (prevName >= 0) {
                        // name/<ignored>/.. found so mark name and ".." to be
                        // ignored
                        ignore[prevName] = true;
                        ignore[i] = true;
                        remaining = remaining - 2;
                        prevName = -1;
                    } else {
                        // Cases:
                        //    \<ignored>..
                        boolean hasPrevious = false;
                        for (int j=0; j<i; j++) {
                            if (!ignore[j]) {
                                hasPrevious = true;
                                break;
                            }
                        }
                        if (!hasPrevious) {
                            // all proceeding names are ignored
                            ignore[i] = true;
                            remaining--;
                        }
                    }
                }
            } while (prevRemaining > remaining);

            // no redundant names
            if (remaining == count)
                return path;

            // corner case - all names removed
            if (remaining == 0) {
                if (root != null) {
                    return root;
                }
                return "";
            }

            // re-constitute the path from the remaining names.
            StringBuilder result = new StringBuilder();
            if (root != null) {
                result.append(root);
            }
            for (int i=0; i<count; i++) {
                if (!ignore[i]) {
                    result.append(elementAsString(path, offsets, i));
                    result.append("/");
                }
            }

            String rtrn = result.toString();
            if (rtrn.equals("/")) {
                return rtrn;
            }
            if (rtrn.length() > 0) {
                return rtrn.substring(0, rtrn.length() - 1);
            }
            return "";
        } catch (Exception e) {
            throw new RuntimeException("Normalize failed on :["+path+"]", e);
        }
    }

    private String resolve(String parent, String child) {
        int pn = parent.length();
        if (pn == 0) return child;
        int cn = child.length();
        if (cn == 0) return parent;

        String c = child;
        int childStart = 0;
        int parentEnd = pn;

        if ((cn > 1) && (c.charAt(0) == '/')) {
            /* Drop prefix when child is drive-relative */
            childStart = 1;
        }

        if (parent.charAt(pn - 1) == '/')
            parentEnd--;

        int strlen = parentEnd + cn - childStart;
        char[] theChars = null;
        theChars = new char[strlen + 1];
        parent.getChars(0, parentEnd, theChars, 0);
        theChars[parentEnd] = '/';
        child.getChars(childStart, cn, theChars, parentEnd + 1);
        return new String(theChars);
    }

    private volatile transient Path filePath;

    public Path toPath() {
        Path result = filePath;
        if (result == null) {
            synchronized (this) {
                result = filePath;
                if (result == null) {
                    result = FileSystems.getDefault().getPath(path);
                    filePath = result;
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        JinixFile j = new JinixFile("./Test.java");
        System.out.println(j.getPath());
        System.out.println(j.getName());
    }
}