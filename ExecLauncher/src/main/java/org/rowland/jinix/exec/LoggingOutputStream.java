package org.rowland.jinix.exec;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Logger;

/**
 * Created by rsmith on 2/26/2017.
 */
class LoggingOutputStream extends OutputStream {

    Logger l;
    char[] buffer = new char[2048];
    int limit = 0;

    LoggingOutputStream(Logger logger) {
        l = logger;
    }

    @Override
    public void write(int b) throws IOException {
        if (b == '\r') {
            return;
        }
        if (b == '\n' || limit == buffer.length) {
            if (limit > 0) {
                l.info(String.copyValueOf(buffer, 0, limit));
                limit = 0;
            }
            return;
        }

        buffer[limit++] = (char) b;
    }
}
