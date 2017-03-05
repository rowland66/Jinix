package org.rowland.jinix;

import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A simple and brief Log formatter for the Jinix kernel
 */
public class BriefKernelLogFormatter extends Formatter {
    private static final String DEFAULT_FORMAT =
            "%1$tH:%1$tM:%1$tS %2$s: %3$s: %4$s%n";

    @Override
    public synchronized String format(LogRecord record) {
        return String.format(DEFAULT_FORMAT,
                new Date(record.getMillis()),
                record.getLoggerName(),
                record.getLevel().getLocalizedName(),
                formatMessage(record));
    }
}
