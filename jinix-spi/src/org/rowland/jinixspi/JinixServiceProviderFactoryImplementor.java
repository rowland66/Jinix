package org.rowland.jinixspi;

import java.io.FileNotFoundException;
import java.net.URI;

public interface JinixServiceProviderFactoryImplementor {

    JinixFileSP createJinixFile(String path);

    JinixFileSP createJinixFile(String parent, String child);

    JinixFileSP createJinixFile(JinixFileSP parent, String child);

    JinixFileSP createJinixFile(URI uri);

    JinixFileInputStreamSP createJinixFileInputStream(JinixFileSP file) throws FileNotFoundException;

    JinixFileInputStreamSP createJinixFileInputStream(Object descriptor);

    JinixFileOutputStreamSP createJinixFileOutputStream(JinixFileSP file, boolean append) throws FileNotFoundException;

    JinixFileOutputStreamSP createJinixFileOutputStream(Object descriptor);

    JinixRandomAccessFileSP createJinixRandomAccessFile(JinixFileSP file, String mode, boolean openAndDelete) throws FileNotFoundException;

    void registerJinixThread(Thread t);

    JinixSystemSP getJinixSystem();

    JinixRuntimeSP getJinixRuntime();
}
