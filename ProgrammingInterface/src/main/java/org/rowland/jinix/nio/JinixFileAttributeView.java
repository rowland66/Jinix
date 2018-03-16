package org.rowland.jinix.nio;

import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.naming.DirectoryFileData;
import org.rowland.jinix.naming.FileNameSpace;
import org.rowland.jinix.naming.LookupResult;
import org.rowland.jinix.naming.RemainingPath;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.rmi.Remote;
import java.util.*;

/**
 * Created by rsmith on 1/14/2017.
 */
public class JinixFileAttributeView implements BasicFileAttributeView {

    private static final String SIZE_NAME = "size";
    private static final String CREATION_TIME_NAME = "creationTime";
    private static final String LAST_ACCESS_TIME_NAME = "lastAccessTime";
    private static final String LAST_MODIFIED_TIME_NAME = "lastModifiedTime";
    private static final String FILE_KEY_NAME = "fileKey";
    private static final String IS_DIRECTORY_NAME = "isDirectory";
    private static final String IS_REGULAR_FILE_NAME = "isRegularFile";
    private static final String IS_SYMBOLIC_LINK_NAME = "isSymbolicLink";
    private static final String IS_OTHER_NAME = "isOther";

    // the names of the basic attributes
    static final Set<String> basicAttributeNames =
            Util.newSet(SIZE_NAME,
                    CREATION_TIME_NAME,
                    LAST_ACCESS_TIME_NAME,
                    LAST_MODIFIED_TIME_NAME,
                    FILE_KEY_NAME,
                    IS_DIRECTORY_NAME,
                    IS_REGULAR_FILE_NAME,
                    IS_SYMBOLIC_LINK_NAME,
                    IS_OTHER_NAME);

    Path p;

    JinixFileAttributeView(Path path) {
        p = path.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "basic";
    }

    public void setAttribute(String attribute, Object value)
            throws IOException
    {
        if (attribute.equals(LAST_MODIFIED_TIME_NAME)) {
            setTimes((FileTime)value, null, null);
            return;
        }
        if (attribute.equals(LAST_ACCESS_TIME_NAME)) {
            setTimes(null, (FileTime)value, null);
            return;
        }
        if (attribute.equals(CREATION_TIME_NAME)) {
            setTimes(null, null, (FileTime)value);
            return;
        }
        throw new IllegalArgumentException("'" + name() + ":" +
                attribute + "' not recognized");
    }

    /**
     * Used to build a map of attribute name/values.
     */
    static class AttributesBuilder {
        private Set<String> names = new HashSet<>();
        private Map<String,Object> map = new HashMap<>();
        private boolean copyAll;

        private AttributesBuilder(Set<String> allowed, String[] requested) {
            for (String name: requested) {
                if (name.equals("*")) {
                    copyAll = true;
                } else {
                    if (!allowed.contains(name))
                        throw new IllegalArgumentException("'" + name + "' not recognized");
                    names.add(name);
                }
            }
        }

        /**
         * Creates builder to build up a map of the matching attributes
         */
        static AttributesBuilder create(Set<String> allowed, String[] requested) {
            return new AttributesBuilder(allowed, requested);
        }

        /**
         * Returns true if the attribute should be returned in the map
         */
        boolean match(String name) {
            return copyAll || names.contains(name);
        }

        void add(String name, Object value) {
            map.put(name, value);
        }

        /**
         * Returns the map. Discard all references to the AttributesBuilder
         * after invoking this method.
         */
        Map<String,Object> unmodifiableMap() {
            return Collections.unmodifiableMap(map);
        }
    }

    /**
     * Invoked by readAttributes or sub-classes to add all matching basic
     * attributes to the builder
     */
    final void addRequestedBasicAttributes(BasicFileAttributes attrs,
                                           AttributesBuilder builder)
    {
        if (builder.match(SIZE_NAME))
            builder.add(SIZE_NAME, attrs.size());
        if (builder.match(CREATION_TIME_NAME))
            builder.add(CREATION_TIME_NAME, attrs.creationTime());
        if (builder.match(LAST_ACCESS_TIME_NAME))
            builder.add(LAST_ACCESS_TIME_NAME, attrs.lastAccessTime());
        if (builder.match(LAST_MODIFIED_TIME_NAME))
            builder.add(LAST_MODIFIED_TIME_NAME, attrs.lastModifiedTime());
        if (builder.match(FILE_KEY_NAME))
            builder.add(FILE_KEY_NAME, attrs.fileKey());
        if (builder.match(IS_DIRECTORY_NAME))
            builder.add(IS_DIRECTORY_NAME, attrs.isDirectory());
        if (builder.match(IS_REGULAR_FILE_NAME))
            builder.add(IS_REGULAR_FILE_NAME, attrs.isRegularFile());
        if (builder.match(IS_SYMBOLIC_LINK_NAME))
            builder.add(IS_SYMBOLIC_LINK_NAME, attrs.isSymbolicLink());
        if (builder.match(IS_OTHER_NAME))
            builder.add(IS_OTHER_NAME, attrs.isOther());
    }

    @Override
    public JinixFileAttributes readAttributes() throws IOException {
        LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(p.toAbsolutePath().toString());
        if (!(lookup.remote instanceof FileNameSpace)) {
            DirectoryFileData dfd = new DirectoryFileData();
            dfd.name = p.getFileName().toString();
            dfd.lastModified = 0;
            dfd.length = 0;
            dfd.type = DirectoryFileData.FileType.FILE;
            return new JinixFileAttributes(dfd);
        }
        FileNameSpace fns = (FileNameSpace) lookup.remote;
        DirectoryFileData dfd = fns.getFileAttributes(lookup.remainingPath);
        return new JinixFileAttributes(dfd);
    }

    public Map<String,Object> readAttributes(String[] requested)
            throws IOException
    {
        AttributesBuilder builder =
                AttributesBuilder.create(basicAttributeNames, requested);
        addRequestedBasicAttributes(readAttributes(), builder);
        return builder.unmodifiableMap();
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {

        if (lastModifiedTime != null) {
            DirectoryFileData dfd = new DirectoryFileData();
            dfd.lastModified = lastModifiedTime.toMillis();
            LookupResult lookup = JinixRuntime.getRuntime().getRootNamespace().lookup(p.toAbsolutePath().toString());
            FileNameSpace fns = (FileNameSpace) lookup.remote;
            fns.setFileAttributes(lookup.remainingPath, dfd);
        }
    }
}
