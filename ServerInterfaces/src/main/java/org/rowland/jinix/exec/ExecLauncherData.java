package org.rowland.jinix.exec;

import org.rowland.jinix.naming.FileChannel;
import org.rowland.jinix.naming.FileNameSpace;

import java.io.Serializable;
import java.util.Properties;

/**
 * Data provided by the ExecServer to a new process's ExecLauncher when it calls back after startup.
 */
public class ExecLauncherData implements Serializable {

    public Properties environment;
    public String cmd;
    public String[] args;
    public FileChannel stdIn;
    public FileChannel stdOut;
    public FileChannel stdErr;
    public FileChannel translatorNode;
    public String translatorNodePath;
}
