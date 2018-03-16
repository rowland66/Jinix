package org.rowland.jinix.naming;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;

public class JarAttributes implements Serializable {

    protected Map<String,String> map;

    public JarAttributes() {
        map = new HashMap<String,String>();
    }

    public JarAttributes(Set<Map.Entry<Object,Object>> attrSet) {
        map = new HashMap<String,String>(attrSet.size());
        for (Map.Entry<Object,Object> e : attrSet) {
            map.put(e.getKey().toString(), e.getValue().toString());
        }
    }

    public String getValue(String name) {
        return map.get(name);
    }

    public String getValue(Attributes.Name name) {
        return map.get(name.toString());
    }
}
