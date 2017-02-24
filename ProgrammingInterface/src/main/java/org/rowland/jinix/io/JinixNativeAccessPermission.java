package org.rowland.jinix.io;

import java.security.Permission;

/**
 * Jinix permissions required to access the native file system.
 *
 * NativeAccess
 * Allow access to the underlying Linux filesystem. The default JinixPolicy grants this permission to code not
 * loaded by the ProtectionDomain ExecClassLoader.
 */
public class JinixNativeAccessPermission extends Permission {

    public JinixNativeAccessPermission() {
        super("NativeAccess");
    }

    @Override
    public boolean implies(Permission permission) {
        return permission.getClass() == getClass();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if ((obj == null) || (obj.getClass() != getClass()))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public String getActions() {
        return "";
    }
}
