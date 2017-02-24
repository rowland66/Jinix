package org.rowland.jinix.nio;

import org.rowland.jinix.naming.DirectoryFileData;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/**
 * Created by rsmith on 1/19/2017.
 */
public class JinixFileAttributes implements BasicFileAttributes {
    DirectoryFileData fileAttributeData;

    public JinixFileAttributes(DirectoryFileData d) {
        fileAttributeData = d;
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.fromMillis(fileAttributeData.lastModified);
    }

    @Override
    public FileTime lastAccessTime() {
        return null;
    }

    @Override
    public FileTime creationTime() {
        return null;
    }

    @Override
    public boolean isRegularFile() {
        return (fileAttributeData.type == DirectoryFileData.FileType.FILE);
    }

    @Override
    public boolean isDirectory() {
        return (fileAttributeData.type == DirectoryFileData.FileType.DIRECTORY);
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public long size() {
        return fileAttributeData.length;
    }

    @Override
    public Object fileKey() {
        return null;
    }
}
