package org.rowland.jinix.proc;

import java.io.Serializable;

public class ProcessData implements Serializable {
    public int id;
    public int parentId;
    public int processGroupId;
    public int sessionId;
    public int terminalId;
    public ProcessManager.ProcessState state;
    public String cmd;
    public String[] args;
    public long startTime;
}
