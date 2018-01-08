package org.rowland.jinix;

import java.io.Serializable;

/**
 * Jinix server exception indicating the an operation has been attempted that is illegal.
 */
public class IllegalOperationException extends RuntimeException {

    public IllegalOperationException(String reason) {
        super(reason);
    }
}
