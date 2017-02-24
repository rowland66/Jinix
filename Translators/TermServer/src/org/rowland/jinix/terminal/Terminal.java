package org.rowland.jinix.terminal;

import org.rowland.jinix.naming.FileChannel;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

/**
 * A Terminal provides a mechanism for interaction between a Jinix process and a
 * virtual terminal. A Terminal provides a MasterTerminalFileDescriptor and a
 * SlaveTerminalFileDescriptor. The master provides input and output streams for
 * the virtual terminal. The slave provides the same for the process. Data written
 * to the output stream of the master is presented on the input stream of the slave.
 * Data written to the output stream of the slave is presented on the input stream
 * of the master.
 */
public class Terminal {

    private short id;
    private int linkedProcess;
    private TermBuffer inputTermBuffer;
    private TermBuffer outputTermBuffer;
    private Map<PtyMode, Integer> modes;

    /**
     * Constructor used by the TermServerServer to create a new Terminal
     *
     * @param terminalId the ID that identifies the terminal
     * @param rawPtyModes a map of PtyModes
     */
    Terminal(short terminalId, Map<PtyMode, Integer> rawPtyModes) {
        id = terminalId;
        inputTermBuffer = new TermBuffer("I",1024); // output from the running process
        outputTermBuffer = new TermBuffer("O",1024); // input for the running process
        this.modes = rawPtyModes;

        System.out.println("Terminal opened:"+id);
    }
    FileChannel getMasterTerminalFileDescriptor() throws RemoteException {
        return new TerminalFileChannel(id, "Master", outputTermBuffer, inputTermBuffer, modes);
    }

    FileChannel getSlaveTerminalFileDescriptor() throws RemoteException {
        return new TerminalFileChannel(id, "Slave", inputTermBuffer, outputTermBuffer, null);
    }

    public void close() {
        System.out.println("Terminal closed:"+id);
        outputTermBuffer.reset();
    }

    void setLinkedProcess(int pid) {
        linkedProcess = pid;
    }

    int getLinkedProcess() {
        return linkedProcess;
    }

    private Map<PtyMode, Integer> convertRawTtyOptions(Map<Byte, Integer> inputMap) {
        Map<PtyMode, Integer> outputMap = new HashMap<PtyMode, Integer>(inputMap.size());
        for(Map.Entry<Byte, Integer> entry : inputMap.entrySet()) {
            outputMap.put(PtyMode.fromInt(entry.getKey()), entry.getValue());
        }
        return outputMap;
    }
}
