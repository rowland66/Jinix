package org.rowland.jinix.proc;

import java.io.Serializable;

public class ChildEventImpl implements ProcessManager.ChildEvent, Serializable {
    private int p, pg;
    private ProcessManager.ProcessState s;
    private int exitStatus;

    public ChildEventImpl(int pid, int processGroupId, ProcessManager.ProcessState state, int exitStatus) {
        p = pid;
        pg = processGroupId;
        s = state;
        this.exitStatus = exitStatus;
    }

    @Override
    public int getPid() {
        return p;
    }

    @Override
    public int getProcessGroupId() {
        return pg;
    }

    @Override
    public ProcessManager.ProcessState getState() {
        return s;
    }

    @Override
    public int getExitStatus() {
        return exitStatus;
    }
}
