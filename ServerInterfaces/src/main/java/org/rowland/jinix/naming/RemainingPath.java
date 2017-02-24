package org.rowland.jinix.naming;

import java.io.Serializable;

/**
 * A container for the remaining path used when calling lookup
 */
public class RemainingPath implements Serializable {
    private String remainingPath;

    public RemainingPath() {
        remainingPath = "";
    }

    public RemainingPath(String s) {
        remainingPath = s;
        if (remainingPath == null) {
            remainingPath = "";
        }
    }

    public void setPath(String s) {
        remainingPath = s;
    }

    public String getPath() {
        return remainingPath;
    }
}
