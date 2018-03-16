package org.rowland.jinix;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.*;
import java.nio.channels.FileChannel;

public class RandomAccessFileTest {

    public static void main(String[] args) {

        try {
            while(true) {
                RandomAccessFile f = new RandomAccessFile("TestFile.txt", "r");
                f.seek(200);
                byte[] b = new byte[20];
                f.read(b);
                long pos = f.getFilePointer();
                String str = new String(b);
                System.out.println(str + ":" + pos);
                FileChannel fc = f.getChannel();
                f.seek(0);
                ByteBuffer bb = ByteBuffer.allocate(20);
                int n = fc.read(bb);
                str = new String(bb.array());
                System.out.println(str + ":" + bb.limit());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    System.exit(0);
                }
                fc.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
