package org.rowland.jinix.mbeans;

import java.net.URI;

public interface FileSystemMBean {

    int getBlockSize();

    long getBlocksCount();

    long getReservedBlocksCount();

    long getFreeBlocksCount();

    String getMountPath();

    URI getURI();
}
