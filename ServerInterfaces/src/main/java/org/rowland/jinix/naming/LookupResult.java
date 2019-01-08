package org.rowland.jinix.naming;

import java.io.Serializable;
import java.rmi.Remote;

/**
 * The value returned by calls to NameSpace.lookup(). When a lookup is called, the path may lead
 * to a sub namespace (FileNameSpace). The remote variable references the sub namespace, and the
 * remainingPath String provides the path to the resource relative to the root of the sub namespace.
 */
public class LookupResult implements Serializable {
    public Remote remote;
    public String remainingPath;
}
