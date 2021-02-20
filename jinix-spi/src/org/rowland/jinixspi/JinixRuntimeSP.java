package org.rowland.jinixspi;

import java.io.IOException;

public interface JinixRuntimeSP {

    void registerJinixThread(Thread t);

    void unregisterJinixThread(Thread t);

    void exit(int status);

    Process createJinixProcess(String[] cmdarray,
                          java.util.Map<String,String> environment,
                          String dir,
                          ProcessBuilder.Redirect[] redirects,
                          boolean redirectErrorStream) throws IOException;
}
