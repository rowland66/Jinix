package org.rowland.jinixspi;

import java.security.AccessControlException;

public class JinixAccess {

    public static boolean isJinix() {
        SecurityManager securityManager = System.getSecurityManager();

        if (securityManager != null) {
            try {
                securityManager.checkPermission(new JinixNativeAccessPermission());
            } catch (AccessControlException e) {
                return true;
            }
        }
        return false;
    }
}
