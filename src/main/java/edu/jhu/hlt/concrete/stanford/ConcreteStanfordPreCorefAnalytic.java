/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.analytics.base.TokenizationedCommunicationAnalytic;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.util.ProjectConstants;
import edu.jhu.hlt.concrete.util.Timing;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.ParserAnnotatorUtils;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

/**
 *
 */
public class ConcreteStanfordPreCorefAnalytic implements TokenizationedCommunicationAnalytic<TokenizedCommunication> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteStanfordPreCorefAnalytic.class);

  private final HeadFinder hf;
  private final PipelineLanguage lang;

  /**
   *
   */
  public ConcreteStanfordPreCorefAnalytic(PipelineLanguage lang) {
    // this.pipeline = StanfordPipelineFactory.preCorefPipeline();
    this.lang = lang;
    this.hf = this.lang.getHeadFinder();

    // needed to avoid NPE when using existingAnnotator.
    new StanfordCoreNLP(this.lang.getProperties());
  }

  public ConcreteStanfordPreCorefAnalytic() {
    this(PipelineLanguage.ENGLISH);
  }

  /*
   * (non-Javadoc)
   *
   * @see edu.jhu.hlt.concrete.safe.metadata.SafeAnnotationMetadata#getTimestamp()
   */
  @Override
  public long getTimestamp() {
    return Timing.currentLocalTime();
  }

  /*
   * (non-Javadoc)
   *
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolName()
   */
  @Override
  public String getToolName() {
    return ConcreteStanfordPreCorefAnalytic.class.getSimpleName();
  }

  /*
   * (non-Javadoc)
   *
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolVersion()
   */
  @Override
  public String getToolVersion() {
    return ProjectConstants.VERSION;
  }

  /*
   * (non-Javadoc)
   *
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolNotes()
   */
  @Override
  public List<String> getToolNotes() {
    List<String> notes = new ArrayList<>();
    notes.add("NER tagging, Lemma tagging, POS tagging, Parse generation, DependencyParse generation, and Coref.");
    return notes;
  }

  private static List<Sentence> annotationToSentenceList(Annotation anno, HeadFinder hf, final List<Sentence> origSentListRef, final AnalyticUUIDGenerator gen)
      throws AnalyticException {
    List<Sentence> slist = new ArrayList<>();
    List<CoreMap> cmList = anno.get(SentencesAnnotation.class);
    final int cmListSize = cmList.size();
    for (int i = 0; i < cmListSize; i++) {
      CoreMap cm = cmList.get(i);
      Sentence orig = origSentListRef.get(i);
      final int sentOff = orig.getTextSpan().getStart();
      Sentence merged = new PreNERCoreMapWrapper(cm, hf, gen).toSentence(sentOff, orig);
      slist.add(merged);
    }

    return slist;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * edu.jhu.hlt.concrete.analytics.base.TokenizationedCommunicationAnalytic#annotate(edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication)
   */
  @Override
  public TokenizedCommunication annotate(TokenizedCommunication arg0) throws AnalyticException {
    final Communication root = new Communication(arg0.getRoot());
    if (!root.isSetText())
      throw new AnalyticException("communication.text must be set to run this analytic.");
    AnalyticUUIDGeneratorFactory f = new AnalyticUUIDGeneratorFactory(root);
    AnalyticUUIDGenerator g = f.create();
    final List<Section> sectList = root.getSectionList();
    final String commText = root.getText();

    List<CoreMap> allCoreMaps = new ArrayList<>();
    // String noMarkup = MarkupRewriter.removeMarkup(commText);
    String noMarkup = commText;
    sectList.forEach(sect -> {
      List<CoreMap> cmList = ConcreteToStanfordMapper.concreteSectionToCoreMapList(sect, commText);
      allCoreMaps.addAll(cmList);
    });

    allCoreMaps.forEach(cm -> LOGGER.trace("Got CoreMap pre-coref: {}", cm.toShorterString(new String[0])));
    Annotation anno = new Annotation(allCoreMaps);
    anno.set(TextAnnotation.class, noMarkup);

    // TODO: it's possible that fixNullDependencyGraphs needs to be called
    // before dcoref annotator is called. TB investigated further.
    for (String annotator : this.lang.getPostTokenizationAnnotators()) {
      LOGGER.debug("Running annotator: {}", annotator);
      (StanfordCoreNLP.getExistingAnnotator(annotator)).annotate(anno);
    }

    anno.get(SentencesAnnotation.class).forEach(cm -> LOGGER.trace("Got CoreMaps post-coref: {}", cm.toShorterString(new String[0])));
    // TODO: not sure if this is necessary - found it in the old code.
    anno.get(SentencesAnnotation.class).stream().filter(cm -> cm.containsKey(TreeAnnotation.class)).forEach(cm -> {
      Tree tree = cm.get(TreeAnnotation.class);
      List<Tree> treeList = new ArrayList<>();
      treeList.add(tree);
      ParserAnnotatorUtils.fillInParseAnnotations(false, true, this.lang.getGrammaticalFactory(), cm, treeList, GrammaticalStructure.Extras.NONE);
    });

    anno.get(SentencesAnnotation.class).forEach(cm -> LOGGER.trace("Got CoreMap post-fill-in: {}", cm.toShorterString(new String[0])));
    List<Sentence> postSentences = annotationToSentenceList(anno, hf, arg0.getSentences(), g);
    postSentences.forEach(st -> LOGGER.trace("Got pre-coref sentence: {}", st.toString()));
    Map<TextSpan, Sentence> tsToSentenceMap = new HashMap<>();
    postSentences.forEach(st -> tsToSentenceMap.put(st.getTextSpan(), st));
    tsToSentenceMap.keySet().forEach(k -> LOGGER.trace("Got TextSpan key: {}", k.toString()));

    sectList.forEach(sect -> {
      List<Sentence> sentList = sect.getSentenceList();
      sentList.forEach(st -> {
        TextSpan ts = st.getTextSpan();
        LOGGER.debug("Trying to find span: {}", ts.toString());
        if (tsToSentenceMap.containsKey(ts)) {
          Sentence newSent = tsToSentenceMap.get(ts);
          st.setTokenization(newSent.getTokenization());
        } else {
          throw new RuntimeException("Didn't find sentence in the new sentences. Old sentence UUID: " + st.getUuid().getUuidString());
        }
      });
    });

    try {
      // Coref.
      CorefManager coref = new CorefManager(new CachedTokenizationCommunication(root), anno);
      TokenizedCommunication tcWithCoref = coref.addCoreference();
      return tcWithCoref;
    } catch (MiscommunicationException e) {
      throw new AnalyticException(e);
    }
  }

  /**
   * sentences with no dependency structure have null values for the various dependency annotations. make sure these are empty dependencies instead to prevent
   * coref-resolution from dying
   */
  private static void fixNullDependencyGraphs(Annotation anno) {
    for (CoreMap sent : anno.get(SentencesAnnotation.class)) {
      if (sent.get(CollapsedDependenciesAnnotation.class) == null) {
        sent.set(CollapsedDependenciesAnnotation.class, new SemanticGraph());
      }
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see edu.jhu.hlt.concrete.analytics.base.Analytic#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public TokenizedCommunication annotate(Communication arg0) throws AnalyticException {
    try {
      return this.annotate(new CachedTokenizationCommunication(arg0));
    } catch (MiscommunicationException e) {
      throw new AnalyticException("Communication did not have required Tokenizations.", e);
    }
  }
}
