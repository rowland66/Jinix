package org.rowland.jinix.terminal;

public enum LocalMode {

    /////////////////////////////// L-flags //////////////////////////////////

    /**
     * Enable signals INTR, QUIT, [D]SUSP.
     */
    ISIG,
    /**
     * Canonicalize input lines.
     */
    ICANON,
    /**
     * Enable input and output of uppercase characters by preceding their
     * lowercase equivalents with &quot;\&quot;.
     */
    XCASE,
    /**
     * Enable echoing.
     */
    ECHO,
    /**
     * Visually erase chars.
     */
    ECHOE,
    /**
     * Kill character discards current line.
     */
    ECHOK,
    /**
     * Echo NL even if ECHO is off.
     */
    ECHONL,
    /**
     * Don't flush after interrupt.
     */
    NOFLSH,
    /**
     * Stop background jobs from output.
     */
    TOSTOP,
    /**
     * Enable extensions.
     */
    IEXTEN,
    /**
     * Echo control characters as ^(Char).
     */
    ECHOCTL,
    /**
     * Visual erase for line kill.
     */
    ECHOKE,
    /**
     * Retype pending input.
     */
    PENDIN
}
