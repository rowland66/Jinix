package org.rowland.jinix.exec;

/**
 * Exception to indicate an attempt to execute a file that is not valid for execution
 */
public class InvalidExecutableException extends Exception
{
    private String fileName;

    public InvalidExecutableException(String fileName) {
        super("File is not a valid Jinix executable: " + fileName);
        this.fileName = fileName;
    }

    public String getInvalidFile() {
        return fileName;
    }
}
