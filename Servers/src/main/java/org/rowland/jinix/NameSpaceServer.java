package org.rowland.jinix;

import org.rowland.jinix.exec.ExecServer;
import org.rowland.jinix.exec.InvalidExecutableException;
import org.rowland.jinix.naming.*;
import org.rowland.jinix.proc.ProcessManager;

import java.io.*;
import java.nio.file.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;
import java.util.logging.Logger;

/**
 * A server that provides the OS naming service. The NameSpaceServer provides a hierarchicle name space that
 * overlay an underlying OS file system. Files and directories in the underlying file system are the only
 * permanent objects in the namespace. Any other object can be bound into the name space overlaying an objects
 * in the underlying file system with the same name.
 */
class NameSpaceServer extends JinixKernelUnicastRemoteObject implements NameSpace {

    private static final Logger logger = Logger.getLogger(SERVER_LOGGER);

    private final Path TRANSLATOR_CONFIG_PATH = Paths.get("./config/translator.config");
    private final Properties translatorConfig = new Properties();
    private final Map<String, Object> namingOverlay;
    private FileNameSpace rootFileSystem;
    private List<FileNameSpace> subFileNameSpaceList = new LinkedList<FileNameSpace>();

    NameSpaceServer(FileNameSpace rootFileSystem) throws RemoteException {
        super();
        this.rootFileSystem = rootFileSystem;
        subFileNameSpaceList.add(rootFileSystem);

        namingOverlay = new HashMap<String, Object>();
        try {
            if (Files.exists(TRANSLATOR_CONFIG_PATH)) {
                InputStream fis = Files.newInputStream(TRANSLATOR_CONFIG_PATH,StandardOpenOption.READ);
                try {
                    translatorConfig.load(fis);
                } finally {
                    fis.close();
                }

                for (Map.Entry<Object, Object> entry : translatorConfig.entrySet()) {
                    String translatorNode = (String) entry.getKey();
                    String translatorCmd = (String) entry.getValue();
                    TranslatorDefinition td = new TranslatorDefinition();
                    td.node = translatorNode;
                    td.command = translatorCmd;
                    namingOverlay.put(translatorNode, td);
                }
            }
        } catch (IOException e) {
            throw new RemoteException("IOException loading translator config file: "+TRANSLATOR_CONFIG_PATH, e);
        }
    }

    @Override
    public void bind(String pathName, Remote remoteObj) throws RemoteException {
        if (namingOverlay.containsKey(pathName)) {
            Object obj = namingOverlay.get(pathName);
            if (obj instanceof TranslatorDefinition) {
                TranslatorDefinition td = (TranslatorDefinition) obj;
                synchronized (td) {
                    if (td.remote != null) {
                        throw new IllegalStateException("Attempt to bind a translator where translator already bound: " + pathName);
                    }
                    td.remote = remoteObj;
                    td.notify();
                }
                return;
            }
            namingOverlay.remove(pathName);
        }
        namingOverlay.put(pathName, remoteObj);
    }

    @Override
    public void translatorFailure(String pathName) throws RemoteException {
        if (namingOverlay.containsKey(pathName)) {
            Object obj = namingOverlay.get(pathName);
            if (obj instanceof TranslatorDefinition) {
                TranslatorDefinition td = (TranslatorDefinition) obj;
                synchronized (td) {
                    td.remote = null;
                    td.notify();
                }
                return;
            }
            namingOverlay.remove(pathName);
        }
    }

    @Override
    public void unbind(String pathName) throws RemoteException {
        if (namingOverlay.containsKey(pathName)) {
            namingOverlay.remove(pathName);
            return;
        }
    }

    @Override
    public void bindTranslator(String path, String cmd, String[] args, EnumSet<BindTranslatorOption> options)
            throws FileNotFoundException, InvalidExecutableException, RemoteException {

        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("bindTranslator path must be absolute: "+path);
        }

        if (cmd == null || cmd.isEmpty()) {
            throw new IllegalArgumentException("bindTranslator cmd must be a non-empty String");
        }

        if (args == null) {
            args = new String[0];
        }

        RemainingPath remainingPath = new RemainingPath("");
        Object obj = lookupInternal(path, remainingPath, false, -1);

        //TODO: Eventually we need to account for a possibility that lookup internal leads to another server. We need to
        // fix this so that path is a handle. This will allow lookupInternal to return the remainder of the path so that
        // we can call bindTranslator on the namespace with the remainder of the path.

        if (obj instanceof TranslatorDefinition) {
            TranslatorDefinition td = (TranslatorDefinition) obj;
            if (td.remote != null) { // there is an active translator here
                if (options.contains(BindTranslatorOption.FORCE)) {
                    terminateTranslator(td.pid);
                } else {
                    return; // If a translator is active and no force, exit.
                }
            }
            namingOverlay.remove(path);
            if (translatorConfig.containsKey(path)) {
                td = new TranslatorDefinition();
                td.node = path;
                td.command = translatorConfig.getProperty(path);
                namingOverlay.put(path, td);
            }
        }

        if (options.contains(BindTranslatorOption.PASSIVE)) {
            translatorConfig.put(path, translatorDefString(cmd, args));
            try {
                OutputStream fos = Files.newOutputStream(TRANSLATOR_CONFIG_PATH,StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    translatorConfig.store(fos, null);
                } finally {
                    fos.close();
                }
            } catch (IOException e) {
                throw new RemoteException("IOException updating translator config file: "+TRANSLATOR_CONFIG_PATH, e);
            }
            TranslatorDefinition td = new TranslatorDefinition();
            td.node = path;
            td.command = cmd;
            namingOverlay.put(path, td);
        }

        if (options.contains(BindTranslatorOption.ACTIVATE)) {
            TranslatorDefinition td = new TranslatorDefinition();
            td.node = path;
            td.command = cmd;
            namingOverlay.put(path, td);
            try {
                startTranslator(td); // startTranslator() will populate remote in the TranslatorDefinition
            } catch (InterruptedException e) {
                throw new RemoteException("Interrupted waiting for translator to register: " + path);
            } catch (InvalidExecutableException e) {

            } catch (IOException e) {
                throw new RemoteException("IOException starting translator: "+path,e);
            }
        }
    }

    @Override
    public void unbindTranslator(String path, EnumSet<BindTranslatorOption> options) throws RemoteException {

        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("NameSpaceServer: unbindTranslator path must be absolute: "+path);
        }

        if (options.contains(BindTranslatorOption.ACTIVATE)) {
            Object obj = null;
            try {
                RemainingPath remainingPath = new RemainingPath("");
                obj = lookupInternal(path, remainingPath, false, -1);
            } catch (FileNotFoundException | InvalidExecutableException e) {
                // We should never get this exception since we are not activating translators
            }

            if (obj instanceof TranslatorDefinition) {
                TranslatorDefinition td = (TranslatorDefinition) obj;
                if (td.remote != null) { // there is an active translator here
                    terminateTranslator(td.pid);
                }
                namingOverlay.remove(path);
                if (translatorConfig.containsKey(path)) {
                    td = new TranslatorDefinition();
                    td.node = path;
                    td.command = translatorConfig.getProperty(path);
                    namingOverlay.put(path, td);
                }
            }
        }

        if (options.contains(BindTranslatorOption.PASSIVE)) {
            if (translatorConfig.containsKey(path)) {
                translatorConfig.remove(path);
                try {
                    OutputStream fos = Files.newOutputStream(TRANSLATOR_CONFIG_PATH, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                    try {
                        translatorConfig.store(fos, null);
                    } finally {
                        fos.close();
                    }
                } catch (IOException e) {
                    throw new RemoteException("IOException updating translator config file: " + TRANSLATOR_CONFIG_PATH, e);
                }
            }
        }
    }

    @Override
    public Object lookup(String path) throws RemoteException {
        return lookup(0, path);
    }

    @Override
    public Object lookup(int pid, String path) throws RemoteException {

        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Lookup path must begin with slash: "+path);
        }

        RemainingPath remainingPath = new RemainingPath("");
        Object obj = null;
        try {
            obj = lookupInternal(path, remainingPath, true, pid);
        } catch (FileNotFoundException | InvalidExecutableException e) {
            // This means a translator executable cannot be found throw IOException
            throw new RemoteException("NameSpaceServer: translator executable not found", e);
        }

        if (obj == null) {
            return rootFileSystem.lookup(pid, remainingPath.getPath());
        } else {
            if (remainingPath.getPath().isEmpty()) {
                return obj;
            }
            if (obj instanceof FileNameSpace) {
                return ((FileNameSpace) obj).lookup(pid, remainingPath.getPath());
            }
        }

        return null;
    }

    @Override
    public List<FileAccessorStatistics> getOpenFiles(int pid) throws RemoteException {
        List<FileAccessorStatistics> rtrnList = new ArrayList<FileAccessorStatistics>(64);
        for (FileNameSpace ns : subFileNameSpaceList) {
            List<FileAccessorStatistics> nsList = ns.getOpenFiles(pid);
            if (nsList != null) {
                rtrnList.addAll(nsList);
            }
        }
        return rtrnList;
    }

    private static String translatorDefString(String cmd, String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append(cmd);
        for (String arg : args) {
            sb.append(" ").append(arg); //TODO: add escaping
        }
        return sb.toString();
    }

    /**
     * Lookup a name in the namespace. The name is hierarchical set of names separated with '/' characters.
     *
     * @param path the hierarchical name to lookup in the namespace
     * @param remainingPath
     * @param activateTranslators
     * @param pid the process ID of the process calling lookup. 0 for Jinix kernel lookups.
     * @return
     * @throws FileNotFoundException
     * @throws InvalidExecutableException
     * @throws RemoteException
     */
    private Object lookupInternal(String path, RemainingPath remainingPath, boolean activateTranslators, int pid)
            throws FileNotFoundException, InvalidExecutableException, RemoteException {

        String parentPath = path;
        while (!parentPath.isEmpty()) {
            if (namingOverlay.containsKey(parentPath)) {
                Object obj = namingOverlay.get(parentPath);
                if (obj instanceof TranslatorDefinition) {
                    TranslatorDefinition td = (TranslatorDefinition) obj;
                    // We only want to start the translator if it has not been started (td.remote == null) and if the lookup
                    // goes into the translator name space except when the translator is performing the lookup itself. We
                    // allow a translator on top of a file system directory to see the directory contents even if the
                    // translator is translating the contents for all other processes. Last call from translator bind and
                    // unbind do not start a translator.
                    if (td.remote == null && pid != td.pid && (!remainingPath.getPath().isEmpty() || activateTranslators)) {
                        try {
                            startTranslator(td); // startTranslator() will populate remote in the TranslatorDefinition
                        } catch (FileNotFoundException e) {
                            throw e;
                        } catch (InterruptedException e) {
                            throw new RemoteException("Interrupted waiting for translator to register: "+path);
                        } catch (IOException e) {
                            throw new RemoteException("IOException starting translator: "+path,e);
                        }
                    }
                    if (remainingPath.getPath().isEmpty() && !activateTranslators) {
                        return obj; // this will be a translator. bindTranslator uses this value.
                    }
                    if (pid != td.pid) {
                        obj = td.remote;
                    } else {
                        // We only get here when the translator is looking up a name under the translators name. Put
                        // the entire parentPath in the remainingPath and return
                        remainingPath.setPath(parentPath + remainingPath.getPath());
                        return null;
                    }
                }

                return obj;
            }
            int i = parentPath.lastIndexOf('/');
            remainingPath.setPath(parentPath.substring(i, parentPath.length()) + remainingPath.getPath());
            parentPath = parentPath.substring(0, i);
        }
        return null;
    }

    @Override
    public boolean unexport() {
        if (((JinixKernelUnicastRemoteObject) rootFileSystem).unexport()) {
            return super.unexport();
        }
        return false;
    }

    FileNameSpace getRootFileSystem() {
        return rootFileSystem;
    }

    /**
     * Terminate any active translators. We do this by checking all of the translators in translatorConfig.
     */
    void shutdown() {
        for (Object key : translatorConfig.keySet()) {
            Object obj = namingOverlay.get((String) key);
            if (obj != null && obj instanceof TranslatorDefinition) {
                TranslatorDefinition td = (TranslatorDefinition) obj;
                if (td.remote != null) {
                    terminateTranslator(td.pid);
                }
            }
        }
    }

    private void startTranslator(TranslatorDefinition td)
            throws InterruptedException, InvalidExecutableException, IOException {
        ExecServer es = (ExecServer) lookup(ExecServer.SERVER_NAME);

        Map<String,String> env = new HashMap<String,String>();

        String[] cmdToken = td.command.split("\\s");
        String cmd = cmdToken[0];
        if (cmd == null) {
            return;
        }

        ArrayList<String> execArgsList = new ArrayList<String>(cmdToken.length-1);
        for (int i=1; i<cmdToken.length; i++) {
            execArgsList.add(cmdToken[i]);
        }
        String[] execArgs = new String[execArgsList.size()];
        execArgsList.toArray(execArgs);

        // We cannot use lookupInternal because we need the fs file, and not the translator
        Object translatorTarget = rootFileSystem.lookup(0, td.node);
        if (translatorTarget == null || !(translatorTarget instanceof RemoteFileHandle)) { // remove the leading '/'
            throw new RemoteException("Illegal attempt to start a translator on a filesystem node that does not exist: "+td.node);
        }


        try {
            td.pid = es.execTranslator(cmd, execArgs, (RemoteFileHandle) translatorTarget, td.node);
        } catch (FileNotFoundException | InvalidExecutableException | RemoteException e) {
            throw e;
        }

        //TODO: Create a way to pass the pid of the new process to the filesystem to associate the open file with the pid.

        synchronized (td) {
            if (td.remote == null) {
                try {
                    td.wait();
                } catch (InterruptedException e) {
                    throw e;
                }
            }
        }

        // This means that the translator failed to start.
        if (td.remote == null) {
            throw new RemoteException("Failure to start translator at: "+td.node);
        }

        // If the translator is a FileNameSpace, add it to the list of sub namespaces.
        if (td.remote instanceof FileNameSpace) {
            subFileNameSpaceList.add((FileNameSpace) td.remote);
        }

        logger.info("Successfully started translator "+td.command + " at: "+td.node);
    }

    private void terminateTranslator(int pid) {
        try {
            ProcessManager pm = (ProcessManager) lookup(ProcessManager.SERVER_NAME);
            //pm.registerEventNotificationHandler(td.pid, ProcessManager.EventName.DEREGISTER, );
            pm.sendSignal(pid, ProcessManager.Signal.TERMINATE);
        } catch (RemoteException e) {
            throw new RuntimeException("Failure terminating translator", e);
        }
    }

    private static class TranslatorDefinition {
        String node;
        String command;
        int pid = Integer.MIN_VALUE; // PID is populated when the translator is activated
        Remote remote; // Remote interface of the translator when the translator is activated
    }
}
