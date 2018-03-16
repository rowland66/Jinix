package org.rowland.jinix.exec;

import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.nio.JinixFileSystem;
import org.rowland.jinix.nio.JinixPath;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.logging.*;

/**
 * A java.util.logging Handler that the ExecLauncher uses for translators. Since
 * translators have no console, the ExecLauncher creates this handler and replaces
 * the default ConsoleHandler on the root Logger. Any logging performed by a
 * translator goes to this Handler by default. ExecLauncher also redirects the
 * translator's System.out and System.err streams to the root logger at the INFO
 * level.
 */
class ExecLauncherLoggingHandler extends StreamHandler {

    ExecLauncherLoggingHandler(Path logFilePath) throws IOException {
        OutputStream logStream = Files.newOutputStream(logFilePath,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE);
        setFormatter(new DefaultTranslatorFormatter());
        setOutputStream(logStream);
        setLevel(Level.ALL);
    }

    @Override
    public synchronized void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    private static class DefaultTranslatorFormatter extends Formatter {
        private static final String ROOT_LOGGER_FORMAT =
                "%1$tH:%1$tM:%1$tS %2$s: %3$s%n";
        private static final String CHILD_LOGGER_FORMAT =
                "%1$tH:%1$tM:%1$tS %2$s: %3$s: %4$s%n";

        @Override
        public synchronized String format(LogRecord record) {
            if (record.getLoggerName().isEmpty()) {
                return String.format(ROOT_LOGGER_FORMAT,
                        new Date(record.getMillis()),
                        record.getLevel().getLocalizedName(),
                        formatMessage(record));
            }
            return String.format(CHILD_LOGGER_FORMAT,
                    new Date(record.getMillis()),
                    record.getLoggerName(),
                    record.getLevel().getLocalizedName(),
                    formatMessage(record));
        }
    }

}
