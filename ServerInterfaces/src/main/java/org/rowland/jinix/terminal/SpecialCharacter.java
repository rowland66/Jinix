package org.rowland.jinix.terminal;

import java.io.Serializable;
import java.util.*;

/**
 * POSIX Special Characters
 */
public enum SpecialCharacter implements Serializable {

    /////////////////////////////// Special Control Chars ////////////////////////////////////

    /**
     * Interrupt character; 255 if none. Similarly for the other characters.
     * Not all of these characters are supported on all systems.
     */
    VINTR,
    /**
     * The quit character (sends SIGQUIT signal on POSIX systems).
     */
    VQUIT,
    /**
     * Erase the character to left of the cursor.
     */
    VERASE,
    /**
     * Kill the current input line.
     */
    VKILL,
    /**
     * End-of-file character (sends EOF from the terminal).
     */
    VEOF,
    /**
     * End-of-line character in addition to carriage return and/or line-feed.
     */
    VEOL,
    /**
     * Suspends the current program.
     */
    VSUSP,
    /**
     * Pauses output (normally control-S).
     */
    VSTOP,
    /**
     * Continues paused output (normally control-Q).
     */
    VSTART;
}
