/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConcreteStanfordProperties {
  private final Properties props = new Properties();

  public ConcreteStanfordProperties() throws IOException {
    try (InputStream is = ConcreteStanfordProperties.class.getClassLoader().getResourceAsStream("concrete-stanford.properties");) {
      if (is == null)
        throw new IOException("Error finding concrete-stanford.properties on the classpath. Ensure it exists.");
      this.props.load(is);
    }
  }

  public boolean getAllowEmptyMentions() {
    return Boolean.parseBoolean(props.getProperty("allow.empty.mentions"));
  }
  
  public String getToolName() {
    return this.props.getProperty("tool.name");
  }
}
