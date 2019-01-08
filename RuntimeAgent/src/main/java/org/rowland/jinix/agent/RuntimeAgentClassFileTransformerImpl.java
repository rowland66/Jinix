package org.rowland.jinix.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Array;
import java.security.ProtectionDomain;
import java.util.Arrays;

public class RuntimeAgentClassFileTransformerImpl implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

        if(loader == null && (className.equals("java/io/RandomAccessFile") || className.equals("java/io/FileDescriptor"))) {
            return Arrays.copyOf(classfileBuffer, classfileBuffer.length);
        }
        return null;
    }
}
