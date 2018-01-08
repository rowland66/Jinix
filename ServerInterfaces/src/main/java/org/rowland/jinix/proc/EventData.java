package org.rowland.jinix.proc;

import java.io.Serializable;

/**
 * Created by rsmith on 12/4/2016.
 */
public class EventData implements Serializable {

    public int pid; // The pid of the process that terminated
    public int pgid;
    public int sessionId; // The session Id of the process that terminated
    public short terminalId; // The terminal of the process that terminated
}
