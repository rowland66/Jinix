package org.rowland.jinix.terminal;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by rsmith on 12/30/2016.
 */
public class LineDiscipline {

    public static final int CODE_BEL = 7; // Ring the bell
    public static final int CODE_DC1 = 17; // XON
    public static final int CODE_DC3 = 19; // XOFF

    public static final Set<PtyMode> OUTPUT_OPTIONS =
            Collections.unmodifiableSet(EnumSet.of(PtyMode.ECHO, PtyMode.INLCR, PtyMode.ICRNL, PtyMode.IGNCR));
    public static final Set<PtyMode> INPUT_OPTIONS =
            Collections.unmodifiableSet(EnumSet.of(PtyMode.ONLCR, PtyMode.OCRNL, PtyMode.ONLRET, PtyMode.ONOCR));

    private TermBuffer outputBuffer;
    private TermBuffer inputBuffer;
    private final Set<PtyMode> outputTtyOptions;
    private final Set<PtyMode> inputTtyOptions;
    private int lastChar = -1;
    private byte pushbackByte = 0;
    private boolean raw = false;
    private byte verase = 0;

    LineDiscipline(TermBuffer outputBuffer, TermBuffer inputBuffer, Map<PtyMode, Integer> modes) {
        this.outputBuffer = outputBuffer;
        this.inputBuffer = inputBuffer;
        this.outputTtyOptions = PtyMode.resolveEnabledOptions(modes, OUTPUT_OPTIONS);
        this.inputTtyOptions = PtyMode.resolveEnabledOptions(modes, INPUT_OPTIONS);
        if (modes.containsKey(PtyMode.VERASE)) {
            verase = modes.get(PtyMode.VERASE).byteValue();
        }

        if (modes.size() == 0) {
            raw = true; // No modes means we are in raw mode. The slave LineDiscipline is always raw.
        }
    }

    public void write(int c) throws IOException {
        if (c == '\r') {
            handleOutputCR();
        } else if (c == '\n') {
            handleOutputLF();
        } else if (c == verase) {
            if (this.outputBuffer.erase()) {
                echoCharacter((byte) c);
            } else {
                echoCharacter((byte) CODE_BEL);
            }
        } else if (c == CODE_DC3) {
            this.inputBuffer.suspendFlow();
        } else if (c == CODE_DC1) {
            this.inputBuffer.resumeFlow();
        } else {
            writeRawOutput(c);
        }
    }

    protected void handleOutputCR() throws IOException {
        if (outputTtyOptions.contains(PtyMode.ICRNL)) {
            writeRawOutput('\n');   // Map CR to NL on input
            this.outputBuffer.flip();
        } else if (outputTtyOptions.contains(PtyMode.IGNCR)) {
            return;    // Ignore CR on input
        } else {
            writeRawOutput('\r');
        }
    }

    protected void handleOutputLF() throws IOException {
        if (outputTtyOptions.contains(PtyMode.INLCR)) {
            writeRawOutput('\r');   // Map NL into CR on input
        } else {
            writeRawOutput('\n');
        }

        if (!raw) {
            this.outputBuffer.flip();
        }
    }

    protected void writeRawOutput(int c) throws IOException {
        int bytesPut = this.outputBuffer.put((byte) c);
        echoCharacter((byte) c);
    }

    protected void flush() {
        if (raw) {
            this.outputBuffer.flip();
        }
    }

    public int available() {
        if (pushbackByte > 0) {
            return 1;
        }
        return inputBuffer.available();
    }

    public int read() throws IOException {
        int c = readRawInput();

        if (c == '\r') {
            c = handleInputCR();
        } else if (c == '\n') {
            c = handleInputLF();
        }

        lastChar = c;
        return c;
    }

    protected int handleInputCR() throws IOException {
        if (inputTtyOptions.contains(PtyMode.OCRNL)) {
            return '\n';    // Translate carriage return to newline
        } else {
            return '\r';
        }
    }

    protected int handleInputLF() throws IOException {
        // Map NL to CR-NL.
        if ((inputTtyOptions.contains(PtyMode.ONLCR) || inputTtyOptions.contains(PtyMode.ONOCR)) && (lastChar != '\r')) {
            this.pushbackByte = '\n';
            return '\r';
        } else if (inputTtyOptions.contains(PtyMode.ONLRET)) {   // Newline performs a carriage return
            return '\r';
        } else {
            return '\n';
        }
    }

    protected int readRawInput() throws IOException {

        int c;
        if (pushbackByte != 0) {
            c = pushbackByte;
            pushbackByte = 0;
            return c;
        }
        return inputBuffer.get();
    }

    private void echoCharacter(byte c) {
        if (outputTtyOptions.contains(PtyMode.ECHO)) {
            if (inputBuffer.putIfPossible(c) > 0) {
                inputBuffer.flip();
            }
        }

    }

}
