package org.rowland.jinix.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

public class RuntimeAgent {

    public static void premain(String agentArgs, Instrumentation inst) {

        if (!inst.isNativeMethodPrefixSupported()) {
            throw new RuntimeException("Failure to start. JVM does not support native method prefixing.");
        }

        ClassFileTransformer transformer = new RuntimeAgentClassFileTransformerImpl();
        inst.addTransformer(transformer);
        inst.setNativeMethodPrefix(transformer, "$$$Jinix$$$_");

        /**
        Class[] loadedClasses = inst.getAllLoadedClasses();
        for (int i=0; i<loadedClasses.length; i++) {
            if (loadedClasses[i].getName().startsWith("java.io")) {
                System.out.println(loadedClasses[i].getName());
            }
        }
         **/
    }
}
