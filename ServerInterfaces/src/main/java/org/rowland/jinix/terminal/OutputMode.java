package org.rowland.jinix.terminal;

public enum OutputMode {

    /////////////////////////// O-flags //////////////////////////////////////

    /**
     * Enable output processing.
     */
    OPOST,
    /**
     * Convert lowercase to uppercase.
     */
    OLCUC,
    /**
     * Map NL to CR-NL.
     */
    ONLCR,
    /**
     * Translate carriage return to newline (output).
     */
    OCRNL,
    /**
     * Translate newline to carriage return-newline (output).
     */
    ONOCR,
    /**
     * Newline performs a carriage return (output).
     */
    ONLRET
}
