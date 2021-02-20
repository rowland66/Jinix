package org.rowland.jinixspi;

import java.util.List;
import java.util.Properties;

public interface JinixSystemSP {

    Properties getJinixProperties();

    List<String> getJinixPropOverrides();
}
