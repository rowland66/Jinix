package org.rowland.jinix.exec;

import org.rowland.jinix.naming.RemoteFileAccessor;
import org.rowland.jinix.naming.RemoteFileHandle;

import java.io.Serializable;
import java.util.Properties;

/**
 * Data provided by the ExecServer to a new process's ExecLauncher when it calls back after startup.
 */
public class ExecLauncherData implements Serializable {

    public Properties environment;
    public String cmd;
    public String[] args;
    public RemoteFileAccessor stdIn;
    public RemoteFileAccessor stdOut;
    public RemoteFileAccessor stdErr;
    public RemoteFileHandle translatorNode;
    public String translatorNodePath;
}
