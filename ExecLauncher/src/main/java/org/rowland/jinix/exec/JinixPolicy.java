package org.rowland.jinix.exec;

import org.rowland.jinixspi.JinixNativeAccessPermission;

import java.security.*;
import java.util.Enumeration;
import java.util.PropertyPermission;

/**
 * The java.security Policy active for all Jinix programs. Jinix overrides of native java.* classes make
 * permission checks to determine if the native java.* class code or the Jinix override code should be invoked
 * in a method call. All classes loaded by the ExecClassLoader (ie Jinix programs) execute under a security manager
 * with this Policy in place. As a result, Jinix programs are restricted from accessing the native filesystem.
 */
public class JinixPolicy extends Policy {

    private final PermissionCollection allowed;

    JinixPolicy() {
        allowed = new Permissions();
        allowed.add(new PropertyPermission("*", "read"));
        allowed.add(new RuntimePermission("*", null));
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {

        if (domain.getClassLoader() instanceof ExecClassLoader) {
            if (((ExecClassLoader) domain.getClassLoader()).isPrivileged()) {
                return true; // privileged classloader grants native access and all privileges
            }
            if (permission instanceof JinixNativeAccessPermission) {
                return false;
            }
        }
        return true;
    }

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {

        if (codesource.getLocation() == null && codesource.getCertificates() == null) {
            Permissions pc = new Permissions();
            Enumeration<Permission> en = allowed.elements();
            while (en.hasMoreElements()) {
                pc.add(en.nextElement());
            }
            return pc;
        }
        return null;
    }
}
