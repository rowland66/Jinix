package org.rowland.jinix.lang;

import org.rowland.jinix.proc.ProcessManager;

/**
 * Created by rsmith on 1/10/2017.
 */
public interface ProcessSignalHandler {

    /**
     * Handle a signal
     *
     * @param signal
     * @return true if the signal is handled, false to let Jinix handle the signal
     */
    boolean handleSignal(ProcessManager.Signal signal);
}
