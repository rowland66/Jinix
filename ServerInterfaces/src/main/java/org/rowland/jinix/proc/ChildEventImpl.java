package org.rowland.jinix.proc;

import java.io.Serializable;

public class ChildEventImpl implements ProcessManager.ChildEvent, Serializable {
    private int p, pg;
    private ProcessManager.ProcessState s;

    public ChildEventImpl(int pid, int processGroupId, ProcessManager.ProcessState state) {
        p = pid;
        pg = processGroupId;
        s = state;
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
}
