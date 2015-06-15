/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.nlp.trees.EnglishGrammaticalStructureFactory;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalStructureFactory;
import edu.stanford.nlp.trees.international.pennchinese.ChineseSemanticHeadFinder;

public enum PipelineLanguage {

  ENGLISH ("en") {
    @Override
    public String[] getPostTokenizationAnnotators() {
      return new String[] { "pos", "lemma", "parse", "ner", "dcoref" };
    }

    @Override
    public Properties getProperties() {
      Properties props = new Properties();
      String annotatorList = "tokenize, ssplit, pos, lemma, parse, ner, dcoref";
      logger.debug("Using annotators: {}", annotatorList);

      props.put("annotators", annotatorList);
      props.setProperty("output.printSingletonEntities", "true");
      return props;
    }

    @Override
    public GrammaticalStructureFactory getGrammaticalFactory() {
      return new EnglishGrammaticalStructureFactory();
    }

    @Override
    public HeadFinder getHeadFinder() {
      return new SemanticHeadFinder();
    }
  },
  CHINESE ("cn") {
    @Override
    public String[] getPostTokenizationAnnotators() {
      return new String[] { "pos", "parse" };
    }

    @Override
    public Properties getProperties() {
      Properties props = new Properties();
      String annotatorList = "segment, ssplit, pos, parse";
      logger.debug("Using annotators: {}", annotatorList);

      props.setProperty("customAnnotatorClass.segment",
          "edu.stanford.nlp.pipeline.ChineseSegmenterAnnotator");

      props.setProperty("segment.model",
          "edu/stanford/nlp/models/segmenter/chinese/ctb.gz");
      props.setProperty("segment.sighanCorporaDict",
          "edu/stanford/nlp/models/segmenter/chinese");
      props.setProperty("segment.serDictionary",
          "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz");
      props.setProperty("segment.sighanPostProcessing", "true");

      props.setProperty("ssplit.boundaryTokenRegex", "[.]|[!?]+|[。]|[！？]+");
      logger.debug("Loading segmentation models and resources.");

      props.setProperty("pos.model", "edu/stanford/nlp/models/pos-tagger/chinese-distsim/chinese-distsim.tagger");
      logger.debug("Loading pos models and resources.");

      props.setProperty("parse.model",
          "edu/stanford/nlp/models/lexparser/chinesePCFG.ser.gz");
      logger.debug("Loading parser models and resources.");
      props.put("annotators", annotatorList);
      return props;
    }

    @Override
    public GrammaticalStructureFactory getGrammaticalFactory() {
      return new ChineseGrammaticalStructureFactory();
    }

    @Override
    public HeadFinder getHeadFinder() {
      return new ChineseSemanticHeadFinder();
    }
  };

  private static final Logger logger = LoggerFactory.getLogger(PipelineLanguage.class);

  private final String v;

  private PipelineLanguage(String v) {
    this.v = v;
  }

  /*
   * (non-Javadoc)
   * @see java.lang.Enum#toString()
   */
  @Override
  public String toString() {
    return this.v;
  }

  public static final PipelineLanguage getEnumeration(String v) {
    for (PipelineLanguage c : PipelineLanguage.values())
      if (c.toString().equalsIgnoreCase(v))
        return c;
    throw new IllegalArgumentException("No matching Languages for value: " + v);
  }

  public abstract String[] getPostTokenizationAnnotators();

  public abstract Properties getProperties();

  public abstract GrammaticalStructureFactory getGrammaticalFactory();

  public abstract HeadFinder getHeadFinder();
}