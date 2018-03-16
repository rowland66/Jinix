package java.lang;

import org.rowland.jinix.io.*;
import org.rowland.jinix.lang.JinixRuntime;
import org.rowland.jinix.lang.JinixSystem;

import java.io.IOException;

import java.lang.ProcessBuilder.*;
import java.util.Map;
import java.util.Properties;

/**
 * This class is for the exclusive use of ProcessBuilder.start() to
 * create new processes.
 *
 * @author Martin Buchholz
 * @since   1.5
 */
final class ProcessImpl {

    private ProcessImpl() {
    }    // Not instantiable

    // Only for use by ProcessBuilder.start()
    static Process start(String[] cmdarray,
                         java.util.Map<String, String> environment,
                         String dir,
                         ProcessBuilder.Redirect[] redirects,
                         boolean redirectErrorStream)
            throws IOException {
        assert cmdarray != null && cmdarray.length > 0;

        // Convert arguments to a contiguous block; it's easier to do
        // memory management in Java than in C.

        JinixFileDescriptor[] child_fds, parent_fds;

        JinixFileInputStream f0 = null;
        JinixFileOutputStream f1 = null;
        JinixFileOutputStream f2 = null;

        try {
            if (redirects == null) {
                redirects = new ProcessBuilder.Redirect[]{Redirect.PIPE, Redirect.PIPE, Redirect.PIPE};
            }

            child_fds = new JinixFileDescriptor[3];
            parent_fds = new JinixFileDescriptor[3];

            if (redirects[0] == Redirect.PIPE) {
                JinixPipe pipe = JinixRuntime.getRuntime().pipe();
                child_fds[0] = pipe.getInputFileDescriptor();
                parent_fds[0] = pipe.getOutputFileDescriptor();
            } else if (redirects[0] == ProcessBuilder.Redirect.INHERIT) {
                child_fds[0] = JinixRuntime.getRuntime().getStandardFileDescriptor(JinixRuntime.StandardFileDescriptor.IN);
                parent_fds[0] = null;
            } else {
                f0 = new JinixFileInputStream(new JinixFile(redirects[0].file().getPath()));
                child_fds[0] = f0.getFD();
                parent_fds[0] = null;
            }

            if (redirects[1] == Redirect.PIPE) {
                JinixPipe pipe = JinixRuntime.getRuntime().pipe();
                child_fds[1] = pipe.getOutputFileDescriptor();
                parent_fds[1] = pipe.getInputFileDescriptor();
            } else if (redirects[1] == Redirect.INHERIT) {
                child_fds[1] = JinixRuntime.getRuntime().getStandardFileDescriptor(JinixRuntime.StandardFileDescriptor.OUT);
                parent_fds[1] = null;
            } else {
                f1 = new JinixFileOutputStream(new JinixFile(redirects[1].file().getPath()),
                        redirects[1].append());
                child_fds[1] = f1.getFD();
                parent_fds[1] = null;
            }

            if (redirects[2] == Redirect.PIPE) {
                JinixPipe pipe = JinixRuntime.getRuntime().pipe();
                child_fds[2] = pipe.getOutputFileDescriptor();
                parent_fds[2] = pipe.getInputFileDescriptor();
            } else if (redirects[2] == Redirect.INHERIT) {
                child_fds[2] = JinixRuntime.getRuntime().getStandardFileDescriptor(JinixRuntime.StandardFileDescriptor.ERROR);
                parent_fds[2] = null;
            } else {
                f2 = new JinixFileOutputStream(new JinixFile(redirects[2].file().getPath()),
                        redirects[2].append());
                child_fds[2] = f2.getFD();
                parent_fds[2] = null;
            }

            String cmd = cmdarray[0];
            String[] args = new String[cmdarray.length - 1];
            for (int i = 0; i < args.length; i++) {
                args[i] = cmdarray[i + 1];
            }

            Properties env = JinixSystem.getJinixProperties();
            if (environment != null) {
                for (Map.Entry<String, String> envEntry : environment.entrySet()) {
                    env.setProperty(envEntry.getKey(), envEntry.getValue());
                }
            }

            Process rtrnProc = new JinixProcess(cmd, args, env, dir, child_fds, parent_fds, redirectErrorStream);

            // Close the child side of any pipes since JinixRuntime.exec() duplicates the fds that it is passed
            for (int i=0; i<3; i++) {
                if (redirects[i] == Redirect.PIPE) {
                    child_fds[i].close();
                }
            }

            return rtrnProc;
        } finally {
            // In theory, close() can throw IOException
            // (although it is rather unlikely to happen here)
            try {
                if (f0 != null) f0.close();
            } finally {
                try {
                    if (f1 != null) f1.close();
                } finally {
                    if (f2 != null) f2.close();
                }
            }
        }
    }
}
