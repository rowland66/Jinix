package org.rowland.jinix;

import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.io.JinixPipe;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.ProcessSignalHandler;
import org.rowland.jinix.naming.NameSpace;
import org.rowland.jinix.proc.ProcessManager;

import javax.naming.Context;
import java.io.FileNotFoundException;
import java.rmi.Remote;
import java.util.Properties;

public class ConsoleFakeJinixRuntime extends JinixRuntime {

    @Override
    public NameSpace getRootNamespace() {
        return null;
    }

    @Override
    public Context getNamingContext() {
        return null;
    }

    @Override
    public int exec(String cmd, String[] args) throws FileNotFoundException, InvalidExecutableException {
        return 0;
    }

    @Override
    public int exec(Properties env, String cmd, String[] args, int processGroupId, JinixFileDescriptor stdin, JinixFileDescriptor stdout, JinixFileDescriptor stderr) throws FileNotFoundException, InvalidExecutableException {
        return 0;
    }

    @Override
    public int fork() throws FileNotFoundException, InvalidExecutableException {
        return 0;
    }

    @Override
    public boolean isForkChild() {
        return false;
    }

    @Override
    public int getPid() {
        return 0;
    }

    @Override
    public int getProcessGroupId() {
        return 0;
    }

    @Override
    public ProcessManager.ChildEvent waitForChild(boolean nowait) {
        return null;
    }

    @Override
    public JinixPipe pipe() {
        return null;
    }

    @Override
    public void sendSignal(int pid, ProcessManager.Signal signal) {

    }

    @Override
    public void sendSignalProcessGroup(int processGroupId, ProcessManager.Signal signal) {

    }

    @Override
    public void registerSignalHandler(ProcessSignalHandler handler) {

    }

    @Override
    public JinixFileDescriptor getTranslatorFile() {
        return null;
    }

    @Override
    public String getTranslatorNodePath() {
        return null;
    }

    @Override
    public void bindTranslator(Remote translator) {

    }

    @Override
    public void registerJinixThread(Thread t) {

    }

    @Override
    public void setProcessGroupId(int processGroupId) {

    }

    @Override
    public int getProcessSessionId() {
        return 0;
    }

    @Override
    public void setProcessSessionId() throws IllegalOperationException {

    }

    @Override
    public void setProcessTerminalId(short terminalId) {

    }

    @Override
    public void setForegroundProcessGroupId(int processGroupId) {

    }
}
