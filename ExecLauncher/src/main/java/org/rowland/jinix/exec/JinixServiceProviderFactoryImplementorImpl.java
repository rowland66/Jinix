package org.rowland.jinix.exec;

import org.rowland.jinix.io.*;
import org.rowland.jinix.lang.JinixSystem;
import org.rowland.jinixspi.*;

import java.io.FileNotFoundException;
import java.net.URI;
import java.util.List;
import java.util.Properties;

public class JinixServiceProviderFactoryImplementorImpl implements JinixServiceProviderFactoryImplementor {

    @Override
    public JinixFileSP createJinixFile(String path) {
        return new JinixFile(path);
    }

    @Override
    public JinixFileSP createJinixFile(String parent, String child) {
        return new JinixFile(parent, child);
    }

    @Override
    public JinixFileSP createJinixFile(JinixFileSP jinixFileSP, String child) {
        return new JinixFile((JinixFile) jinixFileSP, child);
    }

    @Override
    public JinixFileSP createJinixFile(URI uri) {
        return new JinixFile(uri);
    }

    @Override
    public JinixFileInputStreamSP createJinixFileInputStream(JinixFileSP jinixFileSP) throws FileNotFoundException {
        return new JinixFileInputStream((JinixFile) jinixFileSP);
    }

    @Override
    public JinixFileInputStreamSP createJinixFileInputStream(Object descriptor) {
        return new JinixFileInputStream((JinixFileDescriptor) descriptor);
    }

    @Override
    public JinixFileOutputStreamSP createJinixFileOutputStream(JinixFileSP jinixFileSP, boolean append) throws FileNotFoundException {
        return new JinixFileOutputStream((JinixFile) jinixFileSP, append);
    }

    @Override
    public JinixFileOutputStreamSP createJinixFileOutputStream(Object descriptor) {
        return new JinixFileOutputStream((JinixFileDescriptor) descriptor);
    }

    @Override
    public JinixRandomAccessFileSP createJinixRandomAccessFile(JinixFileSP jinixFileSP, String mode, boolean openAndDelete) throws FileNotFoundException {
        return new JinixRandomAccessFile((JinixFile) jinixFileSP, mode, openAndDelete);
    }

    @Override
    public void registerJinixThread(Thread thread) {

    }

    @Override
    public JinixSystemSP getJinixSystem() {
        return new JinixSystemSP() {
            @Override
            public Properties getJinixProperties() {
                return JinixSystem.getJinixProperties();
            }

            @Override
            public List<String> getJinixPropOverrides() {
                return JinixSystem.getJinixPropOverrides();
            }
        };
    }

    @Override
    public JinixRuntimeSP getJinixRuntime() {
        return new ExecLauncher.JinixRuntimeImpl();
    }
}
