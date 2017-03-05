package org.rowland.jinix.nio;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Created by rsmith on 1/7/2017.
 */
public class JinixPath implements Path {

    private static ThreadLocal<SoftReference<CharsetEncoder>> encoder =
            new ThreadLocal<SoftReference<CharsetEncoder>>();

    private final JinixFileSystem fs;

    // internal representation
    private final byte[] path;

    // String representation (created lazily)
    private volatile String stringValue;

    // cached hashcode (created lazily, no need to be volatile)
    private int hash;

    // array of offsets of elements in path (created lazily)
    private volatile int[] offsets;

    public JinixPath(String input) {
        this(new JinixFileSystem(), encode(normalizeAndCheck(input)));
    }

    JinixPath(JinixFileSystem fs, byte[] path) {
        this.fs = fs;
        this.path = path;
    }

    JinixPath(JinixFileSystem fs, String input) {
        // removes redundant slashes and checks for invalid characters
        this(fs, encode(normalizeAndCheck(input)));
    }

    // package-private
    // removes redundant slashes and check input for invalid characters
    static String normalizeAndCheck(String input) {
        int n = input.length();
        char prevChar = 0;
        for (int i=0; i < n; i++) {
            char c = input.charAt(i);
            if ((c == '/') && (prevChar == '/'))
                return normalize(input, n, i - 1);
            checkNotNul(input, c);
            prevChar = c;
        }
        if (prevChar == '/')
            return normalize(input, n, n - 1);
        return input;
    }

    private static void checkNotNul(String input, char c) {
        if (c == '\u0000')
            throw new InvalidPathException(input, "Nul character not allowed");
    }

    private static String normalize(String input, int len, int off) {
        if (len == 0)
            return input;
        int n = len;
        while ((n > 0) && (input.charAt(n - 1) == '/')) n--;
        if (n == 0)
            return "/";
        StringBuilder sb = new StringBuilder(input.length());
        if (off > 0)
            sb.append(input.substring(0, off));
        char prevChar = 0;
        for (int i=off; i < n; i++) {
            char c = input.charAt(i);
            if ((c == '/') && (prevChar == '/'))
                continue;
            checkNotNul(input, c);
            sb.append(c);
            prevChar = c;
        }
        return sb.toString();
    }

    // encodes the given path-string into a sequence of bytes
    private static byte[] encode(String input) {
        SoftReference<CharsetEncoder> ref = encoder.get();
        CharsetEncoder ce = (ref != null) ? ref.get() : null;
        if (ce == null) {
            ce = Util.jnuEncoding().newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            encoder.set(new SoftReference<CharsetEncoder>(ce));
        }

        char[] ca = input.toCharArray();

        // size output buffer for worse-case size
        byte[] ba = new byte[(int)(ca.length * (double)ce.maxBytesPerChar())];

        // encode
        ByteBuffer bb = ByteBuffer.wrap(ba);
        CharBuffer cb = CharBuffer.wrap(ca);
        ce.reset();
        CoderResult cr = ce.encode(cb, bb, true);
        boolean error;
        if (!cr.isUnderflow()) {
            error = true;
        } else {
            cr = ce.flush(bb);
            error = !cr.isUnderflow();
        }
        if (error) {
            throw new InvalidPathException(input,
                    "Malformed input or input contains unmappable characters");
        }

        // trim result to actual length if required
        int len = bb.position();
        if (len != ba.length)
            ba = Arrays.copyOf(ba, len);

        return ba;
    }

    // package-private
    byte[] asByteArray() {
        return path;
    }

    // use this message when throwing exceptions
    String getPathForExceptionMessage() {
        return toString();
    }

    // Checks that the given file is a UnixPath
    static JinixPath toJinixPath(Path obj) {
        if (obj == null)
            throw new NullPointerException();
        if (!(obj instanceof JinixPath))
            throw new ProviderMismatchException();
        return (JinixPath)obj;
    }

    // create offset list if not already created
    private void initOffsets() {
        if (offsets == null) {
            int count, index;

            // count names
            count = 0;
            index = 0;
            if (isEmpty()) {
                // empty path has one name
                count = 1;
            } else {
                while (index < path.length) {
                    byte c = path[index++];
                    if (c != '/') {
                        count++;
                        while (index < path.length && path[index] != '/')
                            index++;
                    }
                }
            }

            // populate offsets
            int[] result = new int[count];
            count = 0;
            index = 0;
            while (index < path.length) {
                byte c = path[index];
                if (c == '/') {
                    index++;
                } else {
                    result[count++] = index++;
                    while (index < path.length && path[index] != '/')
                        index++;
                }
            }
            synchronized (this) {
                if (offsets == null)
                    offsets = result;
            }
        }
    }

    // returns {@code true} if this path is an empty path
    private boolean isEmpty() {
        return path.length == 0;
    }

    // returns an empty path
    private JinixPath emptyPath() {
        return new JinixPath(getFileSystem(), new byte[0]);
    }

    @Override
    public JinixFileSystem getFileSystem() {
        return fs;
    }

    @Override
    public JinixPath getRoot() {
        if (path.length > 0 && path[0] == '/') {
            return getFileSystem().rootDirectory();
        } else {
            return null;
        }
    }

    @Override
    public JinixPath getFileName() {
        initOffsets();

        int count = offsets.length;

        // no elements so no name
        if (count == 0)
            return null;

        // one name element and no root component
        if (count == 1 && path.length > 0 && path[0] != '/')
            return this;

        int lastOffset = offsets[count-1];
        int len = path.length - lastOffset;
        byte[] result = new byte[len];
        System.arraycopy(path, lastOffset, result, 0, len);
        return new JinixPath(getFileSystem(), result);
    }

    @Override
    public JinixPath getParent() {
        initOffsets();

        int count = offsets.length;
        if (count == 0) {
            // no elements so no parent
            return null;
        }
        int len = offsets[count-1] - 1;
        if (len <= 0) {
            // parent is root only (may be null)
            return getRoot();
        }
        byte[] result = new byte[len];
        System.arraycopy(path, 0, result, 0, len);
        return new JinixPath(getFileSystem(), result);
    }

    @Override
    public int getNameCount() {
        initOffsets();
        return offsets.length;
    }

    @Override
    public JinixPath getName(int index) {
        initOffsets();
        if (index < 0)
            throw new IllegalArgumentException();
        if (index >= offsets.length)
            throw new IllegalArgumentException();

        int begin = offsets[index];
        int len;
        if (index == (offsets.length-1)) {
            len = path.length - begin;
        } else {
            len = offsets[index+1] - begin - 1;
        }

        // construct result
        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return new JinixPath(getFileSystem(), result);
    }

    @Override
    public JinixPath subpath(int beginIndex, int endIndex) {
        initOffsets();

        if (beginIndex < 0)
            throw new IllegalArgumentException();
        if (beginIndex >= offsets.length)
            throw new IllegalArgumentException();
        if (endIndex > offsets.length)
            throw new IllegalArgumentException();
        if (beginIndex >= endIndex) {
            throw new IllegalArgumentException();
        }

        // starting offset and length
        int begin = offsets[beginIndex];
        int len;
        if (endIndex == offsets.length) {
            len = path.length - begin;
        } else {
            len = offsets[endIndex] - begin - 1;
        }

        // construct result
        byte[] result = new byte[len];
        System.arraycopy(path, begin, result, 0, len);
        return new JinixPath(getFileSystem(), result);
    }

    @Override
    public boolean isAbsolute() {
        return (path.length > 0 && path[0] == '/');
    }

    // Resolve child against given base
    private static byte[] resolve(byte[] base, byte[] child) {
        int baseLength = base.length;
        int childLength = child.length;
        if (childLength == 0)
            return base;
        if (baseLength == 0 || child[0] == '/')
            return child;
        byte[] result;
        if (baseLength == 1 && base[0] == '/') {
            result = new byte[childLength + 1];
            result[0] = '/';
            System.arraycopy(child, 0, result, 1, childLength);
        } else {
            result = new byte[baseLength + 1 + childLength];
            System.arraycopy(base, 0, result, 0, baseLength);
            result[base.length] = '/';
            System.arraycopy(child, 0, result, baseLength+1, childLength);
        }
        return result;
    }

    @Override
    public JinixPath resolve(Path obj) {
        byte[] other = toJinixPath(obj).path;
        if (other.length > 0 && other[0] == '/')
            return ((JinixPath)obj);
        byte[] result = resolve(path, other);
        return new JinixPath(getFileSystem(), result);
    }

    JinixPath resolve(byte[] other) {
        return resolve(new JinixPath(getFileSystem(), other));
    }

    @Override
    public JinixPath relativize(Path obj) {
        JinixPath other = toJinixPath(obj);
        if (other.equals(this))
            return emptyPath();

        // can only relativize paths of the same type
        if (this.isAbsolute() != other.isAbsolute())
            throw new IllegalArgumentException("'other' is different type of Path");

        // this path is the empty path
        if (this.isEmpty())
            return other;

        int bn = this.getNameCount();
        int cn = other.getNameCount();

        // skip matching names
        int n = (bn > cn) ? cn : bn;
        int i = 0;
        while (i < n) {
            if (!this.getName(i).equals(other.getName(i)))
                break;
            i++;
        }

        int dotdots = bn - i;
        if (i < cn) {
            // remaining name components in other
            JinixPath remainder = other.subpath(i, cn);
            if (dotdots == 0)
                return remainder;

            // other is the empty path
            boolean isOtherEmpty = other.isEmpty();

            // result is a  "../" for each remaining name in base
            // followed by the remaining names in other. If the remainder is
            // the empty path then we don't add the final trailing slash.
            int len = dotdots*3 + remainder.path.length;
            if (isOtherEmpty) {
                assert remainder.isEmpty();
                len--;
            }
            byte[] result = new byte[len];
            int pos = 0;
            while (dotdots > 0) {
                result[pos++] = (byte)'.';
                result[pos++] = (byte)'.';
                if (isOtherEmpty) {
                    if (dotdots > 1) result[pos++] = (byte)'/';
                } else {
                    result[pos++] = (byte)'/';
                }
                dotdots--;
            }
            System.arraycopy(remainder.path, 0, result, pos, remainder.path.length);
            return new JinixPath(getFileSystem(), result);
        } else {
            // no remaining names in other so result is simply a sequence of ".."
            byte[] result = new byte[dotdots*3 - 1];
            int pos = 0;
            while (dotdots > 0) {
                result[pos++] = (byte)'.';
                result[pos++] = (byte)'.';
                // no tailing slash at the end
                if (dotdots > 1)
                    result[pos++] = (byte)'/';
                dotdots--;
            }
            return new JinixPath(getFileSystem(), result);
        }
    }

    @Override
    public Path normalize() {
        final int count = getNameCount();
        if (count == 0 || isEmpty())
            return this;

        boolean[] ignore = new boolean[count];      // true => ignore name
        int[] size = new int[count];                // length of name
        int remaining = count;                      // number of names remaining
        boolean hasDotDot = false;                  // has at least one ..
        boolean isAbsolute = isAbsolute();

        // first pass:
        //   1. compute length of names
        //   2. mark all occurrences of "." to ignore
        //   3. and look for any occurrences of ".."
        for (int i=0; i<count; i++) {
            int begin = offsets[i];
            int len;
            if (i == (offsets.length-1)) {
                len = path.length - begin;
            } else {
                len = offsets[i+1] - begin - 1;
            }
            size[i] = len;

            if (path[begin] == '.') {
                if (len == 1) {
                    ignore[i] = true;  // ignore  "."
                    remaining--;
                }
                else {
                    if (path[begin+1] == '.')   // ".." found
                        hasDotDot = true;
                }
            }
        }

        // multiple passes to eliminate all occurrences of name/..
        if (hasDotDot) {
            int prevRemaining;
            do {
                prevRemaining = remaining;
                int prevName = -1;
                for (int i=0; i<count; i++) {
                    if (ignore[i])
                        continue;

                    // not a ".."
                    if (size[i] != 2) {
                        prevName = i;
                        continue;
                    }

                    int begin = offsets[i];
                    if (path[begin] != '.' || path[begin+1] != '.') {
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
                        // Case: /<ignored>/.. so mark ".." as ignored
                        if (isAbsolute) {
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
                }
            } while (prevRemaining > remaining);
        }

        // no redundant names
        if (remaining == count)
            return this;

        // corner case - all names removed
        if (remaining == 0) {
            return isAbsolute ? getFileSystem().rootDirectory() : emptyPath();
        }

        // compute length of result
        int len = remaining - 1;
        if (isAbsolute)
            len++;

        for (int i=0; i<count; i++) {
            if (!ignore[i])
                len += size[i];
        }
        byte[] result = new byte[len];

        // copy names into result
        int pos = 0;
        if (isAbsolute)
            result[pos++] = '/';
        for (int i=0; i<count; i++) {
            if (!ignore[i]) {
                System.arraycopy(path, offsets[i], result, pos, size[i]);
                pos += size[i];
                if (--remaining > 0) {
                    result[pos++] = '/';
                }
            }
        }
        return new JinixPath(getFileSystem(), result);
    }

    @Override
    public boolean startsWith(Path other) {
        if (!(Objects.requireNonNull(other) instanceof JinixPath))
            return false;
        JinixPath that = (JinixPath)other;

        // other path is longer
        if (that.path.length > path.length)
            return false;

        int thisOffsetCount = getNameCount();
        int thatOffsetCount = that.getNameCount();

        // other path has no name elements
        if (thatOffsetCount == 0 && this.isAbsolute()) {
            return that.isEmpty() ? false : true;
        }

        // given path has more elements that this path
        if (thatOffsetCount > thisOffsetCount)
            return false;

        // same number of elements so must be exact match
        if ((thatOffsetCount == thisOffsetCount) &&
                (path.length != that.path.length)) {
            return false;
        }

        // check offsets of elements match
        for (int i=0; i<thatOffsetCount; i++) {
            Integer o1 = offsets[i];
            Integer o2 = that.offsets[i];
            if (!o1.equals(o2))
                return false;
        }

        // offsets match so need to compare bytes
        int i=0;
        while (i < that.path.length) {
            if (this.path[i] != that.path[i])
                return false;
            i++;
        }

        // final check that match is on name boundary
        if (i < path.length && this.path[i] != '/')
            return false;

        return true;
    }

    @Override
    public boolean endsWith(Path other) {
        if (!(Objects.requireNonNull(other) instanceof JinixPath))
            return false;
        JinixPath that = (JinixPath)other;

        int thisLen = path.length;
        int thatLen = that.path.length;

        // other path is longer
        if (thatLen > thisLen)
            return false;

        // other path is the empty path
        if (thisLen > 0 && thatLen == 0)
            return false;

        // other path is absolute so this path must be absolute
        if (that.isAbsolute() && !this.isAbsolute())
            return false;

        int thisOffsetCount = getNameCount();
        int thatOffsetCount = that.getNameCount();

        // given path has more elements that this path
        if (thatOffsetCount > thisOffsetCount) {
            return false;
        } else {
            // same number of elements
            if (thatOffsetCount == thisOffsetCount) {
                if (thisOffsetCount == 0)
                    return true;
                int expectedLen = thisLen;
                if (this.isAbsolute() && !that.isAbsolute())
                    expectedLen--;
                if (thatLen != expectedLen)
                    return false;
            } else {
                // this path has more elements so given path must be relative
                if (that.isAbsolute())
                    return false;
            }
        }

        // compare bytes
        int thisPos = offsets[thisOffsetCount - thatOffsetCount];
        int thatPos = that.offsets[0];
        if ((thatLen - thatPos) != (thisLen - thisPos))
            return false;
        while (thatPos < thatLen) {
            if (this.path[thisPos++] != that.path[thatPos++])
                return false;
        }

        return true;
    }

    @Override
    public int compareTo(Path other) {
        int len1 = path.length;
        int len2 = ((JinixPath) other).path.length;

        int n = Math.min(len1, len2);
        byte v1[] = path;
        byte v2[] = ((JinixPath) other).path;

        int k = 0;
        while (k < n) {
            int c1 = v1[k] & 0xff;
            int c2 = v2[k] & 0xff;
            if (c1 != c2) {
                return c1 - c2;
            }
            k++;
        }
        return len1 - len2;
    }

    @Override
    public boolean equals(Object ob) {
        if ((ob != null) && (ob instanceof JinixPath)) {
            return compareTo((Path)ob) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        // OK if two or more threads compute hash
        int h = hash;
        if (h == 0) {
            for (int i = 0; i< path.length; i++) {
                h = 31*h + (path[i] & 0xff);
            }
            hash = h;
        }
        return h;
    }

    @Override
    public String toString() {
        // OK if two or more threads create a String
        if (stringValue == null) {
            stringValue = fs.normalizeJavaPath(Util.toString(path));     // platform encoding
        }
        return stringValue;
    }

    // -- file operations --


    @Override
    public JinixPath toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        // The path is relative so need to resolve against default directory,
        // taking care not to reveal the user.dir
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPropertyAccess("user.dir");
        }
        return new JinixPath(getFileSystem(),
                resolve(getFileSystem().defaultDirectory(), path));
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {

        JinixPath absolute = toAbsolutePath();

        // if not resolving links then eliminate "." and also ".."
        // where the previous element is not a link.
        JinixPath result = fs.rootDirectory();
        for (int i=0; i<absolute.getNameCount(); i++) {
            JinixPath element = absolute.getName(i);

            // eliminate "."
            if ((element.asByteArray().length == 1) && (element.asByteArray()[0] == '.'))
                continue;

            // cannot eliminate ".." if previous element is a link
            if ((element.asByteArray().length == 2) && (element.asByteArray()[0] == '.') &&
                    (element.asByteArray()[1] == '.'))
            {
                result = result.getParent();
                if (result == null) {
                    result = fs.rootDirectory();
                }
                continue;
            }
            result = result.resolve(element);
        }

        // check file exists (without following links)
        /*
        try {
            UnixFileAttributes.get(result, false);
        } catch (UnixException x) {
            x.rethrowAsIOException(result);
        }
        */
        return result;
    }

    @Override
    public URI toUri() {
        return JinixUriUtils.toUri(this);
    }

    @Override
    public WatchKey register(WatchService watcher,
                             WatchEvent.Kind<?>[] events,
                             WatchEvent.Modifier... modifiers)
            throws IOException
    {
        if (watcher == null)
            throw new NullPointerException();
        return null;
    }

    @Override
    public final boolean startsWith(String other) {
        return startsWith(getFileSystem().getPath(other));
    }

    @Override
    public final boolean endsWith(String other) {
        return endsWith(getFileSystem().getPath(other));
    }

    @Override
    public final Path resolve(String other) {
        return resolve(getFileSystem().getPath(other));
    }

    @Override
    public final Path resolveSibling(Path other) {
        if (other == null)
            throw new NullPointerException();
        Path parent = getParent();
        return (parent == null) ? other : parent.resolve(other);
    }

    @Override
    public final Path resolveSibling(String other) {
        return resolveSibling(getFileSystem().getPath(other));
    }

    @Override
    public final Iterator<Path> iterator() {
        return new Iterator<Path>() {
            private int i = 0;
            @Override
            public boolean hasNext() {
                return (i < getNameCount());
            }
            @Override
            public Path next() {
                if (i < getNameCount()) {
                    Path result = getName(i);
                    i++;
                    return result;
                } else {
                    throw new NoSuchElementException();
                }
            }
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public final File toFile() {
        return new File(toString());
    }

    @Override
    public final WatchKey register(WatchService watcher,
                                   WatchEvent.Kind<?>... events)
            throws IOException
    {
        return register(watcher, events, new WatchEvent.Modifier[0]);
    }
}
