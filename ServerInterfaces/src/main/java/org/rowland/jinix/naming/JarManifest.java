package org.rowland.jinix.naming;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;

public class JarManifest implements Serializable {
    JarAttributes attr;
    Map<String, JarAttributes> entries;

    public JarManifest(Attributes attributes, Map<String, Attributes> entries) {
        this.attr = new JarAttributes(attributes.entrySet());
        this.entries = new HashMap<String, JarAttributes>(entries.size());
        for (Map.Entry<String,Attributes> e : entries.entrySet()) {
            this.entries.put(e.getKey(), new JarAttributes(e.getValue().entrySet()));
        }
    }
    public JarAttributes getMainAttributes() {
        return attr;
    }

    public Map<String, JarAttributes> getEntries() {
        return entries;
    }

    public JarAttributes getAttribute(String name) {
        return entries.get(name);
    }
}
