package org.rowland.jinix.terminal;

public enum InputMode {

    ///////////////////////////////// I-flags ////////////////////////////////

    /**
     * Map NL into CR on input.
     */
    INLCR,
    /**
     * Ignore CR on input.
     */
    IGNCR,
    /**
     * Map CR to NL on input.
     */
    ICRNL,
    /**
     * Translate uppercase characters to lowercase.
     */
    IUCLC,
    /**
     * Enable output flow control.
     */
    IXON,
    /**
     * Any char will restart after stop.
     */
    IXANY,
    /**
     * Enable input flow control.
     */
    IXOFF,
    /**
     * Ring bell on input queue full.
     */
    IMAXBEL
}
