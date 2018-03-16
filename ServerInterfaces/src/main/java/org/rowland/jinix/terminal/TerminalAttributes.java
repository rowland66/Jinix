package org.rowland.jinix.terminal;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class TerminalAttributes implements Serializable {
    public Set<InputMode> inputModes;
    public Set<OutputMode> outputModes;
    public Set<LocalMode> localModes;
    public Map<SpecialCharacter, Byte> specialCharacterMap;
}
