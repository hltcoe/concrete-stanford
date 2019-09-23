/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford.languages;

import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import edu.jhu.hlt.concrete.stanford.ConcreteStanfordPreCorefAnalytic;
import edu.jhu.hlt.concrete.stanford.ConcreteStanfordTokensSentenceAnalytic;
import edu.stanford.nlp.trees.EnglishGrammaticalStructureFactory;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalStructureFactory;
import edu.stanford.nlp.trees.international.pennchinese.ChineseSemanticHeadFinder;
import edu.stanford.nlp.trees.international.spanish.SpanishHeadFinder;

public enum PipelineLanguage {
  ENGLISH ("en") {
    @Override
    public Properties getProperties(String annotators) {
      logger.debug("Using annotators: {}", annotators);
      Properties props = withAnnotatorsSet(annotators);
      props.setProperty("output.printSingletonEntities", "true");
      return props;
    }

    @Override
    public Optional<GrammaticalStructureFactory> getGrammaticalFactory() {
      return Optional.of(new EnglishGrammaticalStructureFactory());
    }

    @Override
    public HeadFinder getHeadFinder() {
      return new SemanticHeadFinder();
    }

    @Override
    String tokenizationAnnotators() {
      return "tokenize, ssplit";
    }

    @Override
    String preCorefAnnotators() {
      return this.tokenizationAnnotators() + ", pos, lemma, parse, ner, dcoref";
    }

    @Override
    String allAvailableAnnotators() {
//      return this.preCorefAnnotators() + ", dcoref";
      return this.preCorefAnnotators();
    }
//
//    @Override
//    public Properties getUpToTokenizationProperties() {
//      Properties props = new Properties();
//      String annotatorList = "tokenize, ssplit";
//      logger.debug("Using annotators: {}", annotatorList);
//
//      props.put("annotators", annotatorList);
//      return props;
//    }
  },

  SPANISH ("es") {
    @Override
    public Properties getProperties(String annotators) {
      logger.debug("Using annotators: {}", annotators);
      Properties props = withAnnotatorsSet(annotators);

      props.setProperty("output.printSingletonEntities", "true");

      props.setProperty("tokenize.language", "es");
      props.setProperty("pos.model", "edu/stanford/nlp/models/pos-tagger/spanish/spanish-distsim.tagger");
      props.setProperty("ner.model", "edu/stanford/nlp/models/ner/spanish.ancora.distsim.s512.crf.ser.gz");
      props.setProperty("ner.applyNumericClassifiers", "false");
      props.setProperty("ner.useSUTime", "false");
      props.setProperty("parse.model", "edu/stanford/nlp/models/lexparser/spanishPCFG.ser.gz");

      return props;
    }

    @Override
    public Optional<GrammaticalStructureFactory> getGrammaticalFactory() {
      return Optional.empty();
    }

    @Override
    public HeadFinder getHeadFinder() {
      return new SpanishHeadFinder();
    }

    @Override
    String tokenizationAnnotators() {
      return "tokenize, ssplit";
    }

    @Override
    String preCorefAnnotators() {
      return this.tokenizationAnnotators() + ", pos, ner, parse";
    }

    @Override
    String allAvailableAnnotators() {
      return this.preCorefAnnotators();
    }

//    @Override
//    public Properties getUpToTokenizationProperties() {
//      Properties props = new Properties();
//      String annotatorList = "tokenize, ssplit";
//      logger.debug("Using annotators: {}", annotatorList);
//
//      props.put("annotators", annotatorList);
//      return props;
//    }
  },

  CHINESE ("cn") {
    @Override
    public Properties getProperties(String annotators) {
      logger.debug("Using annotators: {}", annotators);
      Properties props = withAnnotatorsSet(annotators);

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
      return props;
    }

    @Override
    public Optional<GrammaticalStructureFactory> getGrammaticalFactory() {
      return Optional.of(new ChineseGrammaticalStructureFactory());
    }

    @Override
    public HeadFinder getHeadFinder() {
      return new ChineseSemanticHeadFinder();
    }

    @Override
//    String tokenizationAnnotators() {
//      return "segment, ssplit, tokenize";
//    }
    String tokenizationAnnotators() { return "tokenize, ssplit"; }

    @Override
    String preCorefAnnotators() {
      return this.tokenizationAnnotators() + ", pos, ner, parse, dcoref";
    }

    @Override
    String allAvailableAnnotators() {
      return this.preCorefAnnotators();
    }

//    @Override
//    public Properties getUpToTokenizationProperties() {
//      Properties props = new Properties();
//      String annotatorList = "segment, ssplit";
//      logger.debug("Using annotators: {}", annotatorList);
//
//      props.setProperty("customAnnotatorClass.segment", "edu.stanford.nlp.pipeline.ChineseSegmenterAnnotator");
//
//      props.setProperty("segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz");
//      props.setProperty("segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese");
//      props.setProperty("segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz");
//      props.setProperty("segment.sighanPostProcessing", "true");
//
//      props.setProperty("ssplit.boundaryTokenRegex", "[.]|[!?]+|[。]|[！？]+");
//
//      props.put("annotators", annotatorList);
//      return props;
//    }
  },
  ;

  private static final Logger logger = LoggerFactory.getLogger(PipelineLanguage.class);

  private static final ImmutableSet<String> SENTENCE_TOKENS_ANNOTATORS =
      ImmutableSet.of("ssplit", "tokenize", "segment");

  private final String v;
  private PipelineLanguage(String v) {
    this.v = v;
  }

  /*
   * this is not great, but want to maintain the order -
   * max thinks it matters.
   */
  public ImmutableList<String> getNonTokenizationAnnotators() {
    ImmutableList<String> spl = ImmutableList.copyOf(this.allAvailableAnnotators().split(", "));
    ImmutableList.Builder<String> b = ImmutableList.builder();
    for (String s : spl) {
      if (!SENTENCE_TOKENS_ANNOTATORS.contains(s))
        b.add(s);
    }
    return b.build();
  }

  /*
   * (non-Javadoc)
   * @see java.lang.Enum#toString()
   */
  @Override
  public String toString() {
    return this.v;
  }

  private static Properties withAnnotatorsSet(String annotators) {
    Properties props = new Properties();
    props.setProperty("annotators", annotators);
    return props;
  }

  public static final PipelineLanguage getEnumeration(String v) {
    final String lower = v.toLowerCase(Locale.ENGLISH);
    if (lower.equals("zho"))
      return PipelineLanguage.CHINESE;
    else if (lower.equals("esp"))
      return PipelineLanguage.SPANISH;
    else if (lower.equals("eng"))
      return PipelineLanguage.ENGLISH;
    for (PipelineLanguage c : PipelineLanguage.values())
      if (c.toString().equalsIgnoreCase(v))
        return c;
    throw new IllegalArgumentException("No matching Languages for value: " + v);
  }

  abstract Properties getProperties(String annotators);

  abstract String tokenizationAnnotators();
  abstract String preCorefAnnotators();
  abstract String allAvailableAnnotators();

  public abstract Optional<GrammaticalStructureFactory> getGrammaticalFactory();
  public abstract HeadFinder getHeadFinder();

  public ConcreteStanfordTokensSentenceAnalytic getSentenceTokenizationAnalytic() {
    return new ConcreteStanfordTokensSentenceAnalytic(this.getProperties(this.tokenizationAnnotators()));
  }

  public ConcreteStanfordPreCorefAnalytic getPreCorefAnalytic() {
    Properties props = this.getProperties(this.preCorefAnnotators());
    return new ConcreteStanfordPreCorefAnalytic(props,
        this.getHeadFinder(), this.getGrammaticalFactory(),
        this.getNonTokenizationAnnotators(), false);
  }

  public ConcreteStanfordPreCorefAnalytic getAllAnalytic() {
    Properties props = this.getProperties(this.preCorefAnnotators());
    return new ConcreteStanfordPreCorefAnalytic(props,
        this.getHeadFinder(), this.getGrammaticalFactory(),
        this.getNonTokenizationAnnotators(), true);
  }
}
