package org.rowland.jinixspi;

import java.io.FileNotFoundException;
import java.net.URI;

public class JinixServiceProviderFactory {
    private static JinixServiceProviderFactoryImplementor factoryImpl = null;

    public static void setFactoryImpl(JinixServiceProviderFactoryImplementor factory) {
        if (factoryImpl == null) {
            JinixServiceProviderFactory.factoryImpl = factory;
        }
    }

    public static JinixFileSP createJinixFile(String path) {
        return factoryImpl.createJinixFile(path);
    }

    public static JinixFileSP createJinixFile(String parent, String child) {
        return factoryImpl.createJinixFile(parent, child);
    }

    public static JinixFileSP createJinixFile(JinixFileSP parent, String child) {
        return factoryImpl.createJinixFile(parent, child);
    }

    public static JinixFileSP createJinixFile(URI uri) {
        return factoryImpl.createJinixFile(uri);
    }

    public static JinixFileInputStreamSP createJinixFileInputStream(JinixFileSP file) throws FileNotFoundException {
        return factoryImpl.createJinixFileInputStream(file);
    }

    public static JinixFileInputStreamSP createJinixFileInputStream(Object descriptor) {
        return factoryImpl.createJinixFileInputStream(descriptor);
    }

    public static JinixFileOutputStreamSP createJinixFileOutputStream(JinixFileSP file, boolean append) throws FileNotFoundException {
        return factoryImpl.createJinixFileOutputStream(file, append);
    }

    public static JinixFileOutputStreamSP createJinixFileOutputStream(Object descriptor) {
        return factoryImpl.createJinixFileOutputStream(descriptor);
    }

    public static JinixRandomAccessFileSP createJinixRandomAccessFile(JinixFileSP file, String mode, boolean openAndDelete) throws FileNotFoundException {
        return factoryImpl.createJinixRandomAccessFile(file, mode, openAndDelete);
    }

    public static JinixSystemSP getJinixSystem() {
        return factoryImpl.getJinixSystem();
    }

    public static JinixRuntimeSP getJinixRuntime() {
        return factoryImpl.getJinixRuntime();
    }
}


