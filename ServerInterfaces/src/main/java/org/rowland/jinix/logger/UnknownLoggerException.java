package org.rowland.jinix.logger;

/**
 * Exception thrown to indicate a requested operation on a logger that does not exist.
 */
public class UnknownLoggerException extends Exception {

    public UnknownLoggerException(String unknownLogger) {
        super("Unknown logger: "+unknownLogger);
    }
}
