package org.rowland.jinix.init;

import org.rowland.jinix.io.JinixFile;
import org.rowland.jinix.io.JinixFileInputStream;
import org.rowland.jinix.lang.JinixRuntime;

import java.io.*;

/**
 * The first(and only) Jinix executable launched by the Jinix kernel after starting the core kernel servers. Init reads
 * a simple command file (/config/init.config) and creates a process to run each command.
 */
public class Init {

    public static void main(String[] args) {

        try {
            JinixFile f = new JinixFile("/config/init.config");
            BufferedReader initTabFileReader = new BufferedReader(new InputStreamReader(new JinixFileInputStream(f)));
            String initLine = initTabFileReader.readLine();
            while (initLine != null) {
                initLine = initLine.trim();
                if (!initLine.isEmpty() && !initLine.startsWith("#")) {
                    executeInitLine(initLine);
                    initLine = initTabFileReader.readLine();
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Init: Unable to find inittab file at /config/init.config");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Init: IO Exception reading /config/inittab");
            System.exit(1);
        }

        try {
            Thread.currentThread().sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {

        }

        System.exit(0);
    }

    private static void executeInitLine(String initLine) {

        String[] cmd = initLine.split(" ");
        String[] args = new String[cmd.length-1];
        for (int i=1; i<cmd.length; i++) {
            args[i-1] = cmd[i];
        }
        try {
            JinixRuntime.getRuntime().exec(cmd[0], args);
        } catch (FileNotFoundException e) {
            System.err.println("Init: Unable to find file: " + cmd[0]);
        } catch (org.rowland.jinix.exec.InvalidExecutableException e) {
            System.err.println("Init: Invalid executable file: " + cmd[0]);
        }
    }
}
