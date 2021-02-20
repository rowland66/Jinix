package org.rowland.jinix;

import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.io.JinixFileDescriptor;
import org.rowland.jinix.io.JinixPipe;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.ProcessSignalHandler;
import org.rowland.jinix.naming.NameSpace;
import org.rowland.jinix.proc.ProcessManager;
import org.rowland.jinix.terminal.TerminalAttributes;

import javax.naming.Context;
import java.io.FileNotFoundException;
import java.rmi.Remote;
import java.util.EnumSet;
import java.util.Properties;

public class ConsoleFakeJinixRuntime extends JinixRuntime {

    @Override
    public Object lookup(String path) {
        return null;
    }

    @Override
    public void bind(String path, Object obj) {

    }

    @Override
    public void unbind(String path) {

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
    public int exec(Properties env, String cmd, String[] args, int processGroupId, int sessionId, JinixFileDescriptor stdin, JinixFileDescriptor stdout, JinixFileDescriptor stderr) throws FileNotFoundException, InvalidExecutableException {
        return 0;
    }

    @Override
    public int fork() throws FileNotFoundException, InvalidExecutableException {
        return 0;
    }

    @Override
    public int fork(JinixFileDescriptor in, JinixFileDescriptor out, JinixFileDescriptor error) throws FileNotFoundException, InvalidExecutableException {
        return 0;
    }

    @Override
    public JinixFileDescriptor getStandardFileDescriptor(StandardFileDescriptor sfd) {
        return null;
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
    public ProcessManager.ChildEvent waitForChild(int pid, boolean nowait) {
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
    public JinixFile getTranslatorFile() {
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
    public void bindTranslator(String pathName, String translatorCmd, String[] translatorArgs, EnumSet<NameSpace.BindTranslatorOption> options) throws FileNotFoundException, InvalidExecutableException {

    }

    @Override
    public void unbindTranslator(String pathName, EnumSet<NameSpace.BindTranslatorOption> options) {

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
    public short getProcessTerminalId() {
        return 0;
    }

    @Override
    public void setProcessTerminalId(short terminalId) {

    }

    @Override
    public void setForegroundProcessGroupId(int processGroupId) {

    }

    @Override
    public int getTerminalColumns() {
        return 0;
    }

    @Override
    public int getTerminalLines() {
        return 0;
    }

    @Override
    public TerminalAttributes getTerminalAttributes(short terminalId) {
        return null;
    }

    @Override
    public void setTerminalAttributes(short terminalId, TerminalAttributes terminalAttributes) {

    }

    @Override
    public void exit(int status) {

    }

    @Override
    public void addLibraryToClassloader(String jarFile) {

    }
}
