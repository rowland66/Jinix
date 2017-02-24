package org.rowland.jinix.lang;

import org.rowland.jinix.proc.ProcessManager;

/**
 * Created by rsmith on 1/10/2017.
 */
public interface ProcessSignalHandler {

    void handleSignal(ProcessManager.Signal signal);
}
