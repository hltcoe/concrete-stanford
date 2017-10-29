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

  public ConcreteStanfordProperties() {
    try (InputStream is = ConcreteStanfordProperties.class.getClassLoader()
        .getResourceAsStream("concrete-stanford.properties");) {
      if (is == null)
        throw new RuntimeException("Error finding concrete-stanford.properties on the classpath. Ensure it exists.");
      this.props.load(is);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean getAllowEmptyMentions() {
    return Boolean.parseBoolean(props.getProperty("allow.empty.mentions"));
  }

  public String getToolName() {
    return this.props.getProperty("tool.name");
  }

  public String getTokenizerToolName() {
    return props.getProperty("tokenizer.name");
  }

  public String getLemmatizerToolName() {
    return props.getProperty("lemmatizer.name");
  }

  public String getPOSToolName() {
    return props.getProperty("pos-tagger.name");
  }

  public String getNERToolName() {
    return props.getProperty("ner-tagger.name");
  }

  public String getCParseToolName() {
    return props.getProperty("constituency-parser.name");
  }

  public String getDParseToolName() {
    return props.getProperty("dependency-parser.name");
  }

  public String getCorefToolName() {
    return props.getProperty("coref.name");
  }
}
