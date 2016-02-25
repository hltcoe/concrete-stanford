/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.analytics.base.SectionedCommunicationAnalytic;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.sectioned.CachedSectionedCommunication;
import edu.jhu.hlt.concrete.miscommunication.sectioned.SectionedCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.util.Timing;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

/**
 *
 */
public class ConcreteStanfordTokensSentenceAnalytic implements SectionedCommunicationAnalytic<TokenizedCommunication> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteStanfordTokensSentenceAnalytic.class);

  private final StanfordCoreNLP pipeline;
  /**
   *
   */
  public ConcreteStanfordTokensSentenceAnalytic(PipelineLanguage lang) {
    this.pipeline = new StanfordCoreNLP(lang.getUpToTokenizationProperties());
  }

  /**
   *
   */
  public ConcreteStanfordTokensSentenceAnalytic() {
    this(PipelineLanguage.ENGLISH);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.safe.metadata.SafeAnnotationMetadata#getTimestamp()
   */
  @Override
  public long getTimestamp() {
    return Timing.currentLocalTime();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolName()
   */
  @Override
  public String getToolName() {
    return ConcreteStanfordTokensSentenceAnalytic.class.getSimpleName();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolVersion()
   */
  @Override
  public String getToolVersion() {
    return ProjectConstants.VERSION;
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolNotes()
   */
  @Override
  public List<String> getToolNotes() {
    List<String> notes = new ArrayList<>();
    notes.add("Tokenization and sentence splitting annotators only.");
    return notes;
  }

  private static List<Sentence> annotationToSentenceList(Annotation anno, int cOffset, final AnalyticUUIDGenerator gen) {
    List<Sentence> slist = new ArrayList<>();
    anno.get(SentencesAnnotation.class).stream()
      .map(cm -> {
        // LOGGER.info("Got Sentence offset: {}", cm.toString());
        try {
          return new CoreMapWrapper(cm, gen).toSentence(cOffset);
        } catch (AnalyticException e) {
          throw new RuntimeException(e);
        }
      })
    .sequential()
    .forEach(st -> slist.add(st));

    return slist;
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.analytics.base.Analytic#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public TokenizedCommunication annotate(Communication arg0) throws AnalyticException {
    try {
      return this.annotate(new CachedSectionedCommunication(arg0));
    } catch (MiscommunicationException e) {
      throw new AnalyticException("Input communication did not have required Section annotations present.", e);
    }
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.analytics.base.SectionedCommunicationAnalytic#annotate(edu.jhu.hlt.concrete.miscommunication.sectioned.SectionedCommunication)
   */
  @Override
  public TokenizedCommunication annotate(SectionedCommunication arg0) throws AnalyticException {
    final Communication cp = new Communication(arg0.getRoot());
    if(!cp.isSetText())
      throw new AnalyticException("communication.text must be set to run this analytic.");
    AnalyticUUIDGeneratorFactory f = new AnalyticUUIDGeneratorFactory(cp);
    AnalyticUUIDGenerator g = f.create();
    List<Section> sList = arg0.getSections()
        .stream()
        // temporary hack - filter out
        // any zero-length TextSpans.
        .filter(s -> {
          final TextSpan ts = s.getTextSpan();
          return ts.getStart() != ts.getEnding();
        })
        // temporary hack - filter out any
        // TextSpans that contain only whitespace.
        .filter(s -> {
          final TextSpan ts = s.getTextSpan();
          final int b = ts.getStart();
          final int e = ts.getEnding();
          String txt = cp.getText().substring(b, e);
          // that isn't enough, could get HTML encoded blank spaces.
          if (txt.contains("&nbsp"))
            txt = StringEscapeUtils.unescapeHtml4(txt);

          String slim = txt.trim().replaceAll("\\p{Zs}", "");
          return !slim.isEmpty();
        })
        .collect(Collectors.toList());
    final int newSize = sList.size();
    final int oSize = arg0.getSections().size();
    if (newSize < oSize)
      LOGGER.warn("Dropped {} section(s) because they were zero-length or contained only whitespace.", oSize - newSize);
    // for each section, run stanford tokenization and sentence splitting
    for (Section s : sList) {
      LOGGER.debug("Annotating section: {}", s.getUuid().getUuidString());
      final TextSpan sts = s.getTextSpan();
      final String sectTxt = cp.getText().substring(sts.getStart(), sts.getEnding());
      // final String sectTxt = new SuperTextSpan(sts, cp).getText();
      LOGGER.debug("Section text: {}", sectTxt);
      final Annotation sectAnnotation = new Annotation(sectTxt);
      LOGGER.debug("Got annotation keys:");
      sectAnnotation.keySet().forEach(k -> LOGGER.debug("{}", k));
      this.pipeline.annotate(sectAnnotation);
      LOGGER.trace("Post annotation annotation keys:");
      sectAnnotation.keySet().forEach(k -> LOGGER.trace("{}", k));

      List<CoreLabel> tokensOnly = sectAnnotation.get(TokensAnnotation.class);
      tokensOnly.forEach(cl -> LOGGER.trace("Got non-sent Stanford token: {}", cl.toShorterString(new String[0])));
      // LOGGER.debug("Got first sentence text annotation: {}", sectAnnotation.get(SentencesAnnotation.class).get(0).get(TextAnnotation.class));
      List<Sentence> stList = annotationToSentenceList(sectAnnotation, sts.getStart(), g);
      s.setSentenceList(stList);
    }

    cp.setSectionList(sList);
    try {
      return new CachedTokenizationCommunication(cp);
    } catch (MiscommunicationException e) {
      throw new AnalyticException(e);
    }
  }
}
