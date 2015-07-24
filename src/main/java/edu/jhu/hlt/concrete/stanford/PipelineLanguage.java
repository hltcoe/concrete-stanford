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

    @Override
    public String[] getUpToTokenizationAnnotators() {
      return new String[] {"tokenize", "ssplit" };
    }

    @Override
    public Properties getUpToTokenizationProperties() {
      Properties props = new Properties();
      String annotatorList = "tokenize, ssplit";
      logger.debug("Using annotators: {}", annotatorList);

      props.put("annotators", annotatorList);
      return props;
    }
  },
  CHINESE ("cn") {
    @Override
    public String[] getPostTokenizationAnnotators() {
      return new String[] { "pos", "ner", };
    }

    @Override
    public Properties getProperties() {
      Properties props = new Properties();
      String annotatorList = "segment, ssplit, pos, ner";
      logger.debug("Using annotators: {}", annotatorList);

      props.setProperty("customAnnotatorClass.segment", "edu.stanford.nlp.pipeline.ChineseSegmenterAnnotator");

      props.setProperty("segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz");
      props.setProperty("segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese");
      props.setProperty("segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz");
      props.setProperty("segment.sighanPostProcessing", "true");

      props.setProperty("ssplit.boundaryTokenRegex", "[.]|[!?]+|[。]|[！？]+");

      props.setProperty("pos.model", "edu/stanford/nlp/models/pos-tagger/chinese-distsim/chinese-distsim.tagger");

      props.setProperty("ner.model", "edu/stanford/nlp/models/ner/chinese.misc.distsim.crf.ser.gz");
      props.setProperty("ner.applyNumericClassifiers", "false");
      props.setProperty("ner.useSUTime", "false");

      props.setProperty("parse.model", "edu/stanford/nlp/models/lexparser/chinesePCFG.ser.gz");

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

    @Override
    public String[] getUpToTokenizationAnnotators() {
      // TODO Auto-generated method stub
      return new String[] { "segment", "ssplit" };
    }

    @Override
    public Properties getUpToTokenizationProperties() {
      Properties props = new Properties();
      String annotatorList = "segment, ssplit";
      logger.debug("Using annotators: {}", annotatorList);

      props.setProperty("customAnnotatorClass.segment", "edu.stanford.nlp.pipeline.ChineseSegmenterAnnotator");

      props.setProperty("segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz");
      props.setProperty("segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese");
      props.setProperty("segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz");
      props.setProperty("segment.sighanPostProcessing", "true");

      props.setProperty("ssplit.boundaryTokenRegex", "[.]|[!?]+|[。]|[！？]+");

      props.put("annotators", annotatorList);
      return props;
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

  public abstract String[] getUpToTokenizationAnnotators();

  public abstract Properties getProperties();
  public abstract Properties getUpToTokenizationProperties();

  public abstract GrammaticalStructureFactory getGrammaticalFactory();

  public abstract HeadFinder getHeadFinder();
}