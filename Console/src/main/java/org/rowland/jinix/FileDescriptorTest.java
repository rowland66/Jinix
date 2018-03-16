package org.rowland.jinix;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileDescriptorTest {

    public static void main(String[] args) {
        try {
            FileInputStream fis = new FileInputStream("TestFile.txt");
            FileDescriptor fd = fis.getFD();
            FileOutputStream fos = new FileOutputStream(fd);
            fos.write("Bogus".getBytes());
            fos.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
