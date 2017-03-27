package org.rowland.jinix;

import org.rowland.jinix.naming.NameSpace;

/**
 * Any FileNameSpace that can be used as the root file system for a Jinix system
 * must implement this interface
 */
public interface RootFileSystem {

    /**
     * Set the root name space for which this file system is the root file system.
     *
     * @param root
     */
    void setRootNameSpace(NameSpace root);
}
