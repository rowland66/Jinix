package org.rowland.jinix.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Jinix extensions to java.lang.System.
 */
public class JinixSystem {

    private static Properties jinixProps;
    private static List<String> jinixPropOverrides = new ArrayList<String>(20);

    static {
        jinixProps = new Properties();
    }

    public static Properties getJinixProperties() {
        return jinixProps;
    }

    public static List<String> getJinixPropOverrides() {
        return jinixPropOverrides;
    }

    public static String setJinixProperty(String key, String value) {
        checkKey(key);
        if (value == null) {
            if (!jinixPropOverrides.contains(key)) {
                jinixPropOverrides.add(key);
                return null;
            }
        }

        if (jinixPropOverrides.contains(key)) {
            jinixPropOverrides.remove(key);
        }
        return (String) jinixProps.setProperty(key, value);
    }

    private static void checkKey(String key) {
        if (key == null) {
            throw new NullPointerException("key can't be null");
        }
        if (key.equals("")) {
            throw new IllegalArgumentException("key can't be empty");
        }
    }

}
