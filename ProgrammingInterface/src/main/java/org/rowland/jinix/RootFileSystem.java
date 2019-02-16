package org.rowland.jinix;

import org.rowland.jinix.naming.NameSpace;

/**
 * Any FileNameSpace that can be used as the root file system for a Jinix system
 * must implement this interface
 */
public interface RootFileSystem {

    void sync();

    void shutdown();
}
