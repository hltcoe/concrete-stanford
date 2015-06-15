/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.analytics.base.NonSentencedSectionedCommunicationAnalytic;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.sectioned.NonSentencedSectionedCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.util.Timing;
import edu.jhu.hlt.utilt.ex.LoggedUncaughtExceptionHandler;

public class AnnotateNonTokenizedConcrete implements NonSentencedSectionedCommunicationAnalytic<StanfordPostNERCommunication> {

  private static final Logger logger = LoggerFactory.getLogger(AnnotateNonTokenizedConcrete.class);

  private ConcreteStanfordTokensSentenceAnalytic analytic;
  private ConcreteStanfordPreCorefAnalytic corefAnalytic;

  public AnnotateNonTokenizedConcrete() {
    this(PipelineLanguage.ENGLISH);
  }

  public AnnotateNonTokenizedConcrete(PipelineLanguage lang) {
    this.analytic = new ConcreteStanfordTokensSentenceAnalytic(lang);
    this.corefAnalytic = new ConcreteStanfordPreCorefAnalytic(lang);
  }

  /*
   * (non-Javadoc)
   * @see edu.jhu.hlt.concrete.analytics.base.UncheckedAnalytic#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public StanfordPostNERCommunication annotate(Communication arg0) throws AnalyticException {
    // try to loosely infer what is being dealt with
    Optional.ofNullable(arg0.getSectionList())
        .orElseThrow(() -> new AnalyticException("Communication must have sections."));
    try {
      return this.annotate(new NonSentencedSectionedCommunication(arg0));
    } catch (MiscommunicationException e) {
      throw new AnalyticException(e);
    }
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
    return AnnotateNonTokenizedConcrete.class.getSimpleName();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolVersion()
   */
  @Override
  public String getToolVersion() {
    return ProjectConstants.VERSION;
  }

  /**
   * @param c
   *          a {@link NonSentencedSectionedCommunication}
   * @return a {@link StanfordPostNERCommunication} with the analytic's annotations
   * @throws AnalyticException
   *           on analytic error
   */
  @Override
  public StanfordPostNERCommunication annotate(NonSentencedSectionedCommunication c) throws AnalyticException {
    TokenizedCommunication tc = this.analytic.annotate(c);
    TokenizedCommunication wCoref = this.corefAnalytic.annotate(tc);
    try {
      return new StanfordPostNERCommunication(wCoref.getRoot());
    } catch (MiscommunicationException e) {
      throw new AnalyticException(e);
    }
  }

  public static void main(String[] args) {
    Thread.setDefaultUncaughtExceptionHandler(new LoggedUncaughtExceptionHandler());
    int argLen = args.length;
    if (argLen < 2) {
      logger.info("This program takes at least 2 arguments:");
      logger.info("Argument 1: path to a .concrete file (representing a communication), a .tar file, or .tar.gz file"
          + " with concrete communication objects.");
      logger.info("The input communication(s) must have Sections, but no Sentences.");
      logger.info("Argument 2: path to an output file, including the extension.");
      logger.info("Argument 3 (optional): language. Default: en. Supported: en [English], cn [Chinese]");

      logger.info("Usage example: {} {} {} [{}]", AnnotateNonTokenizedConcrete.class.toString(),
          "path/to/input/file.extension", "path/to/output/file.extension", "en");
      System.exit(1);
    }

    // infer language
    String langStr = argLen >= 3 ? args[2] : "en";
    PipelineLanguage pl = PipelineLanguage.getEnumeration(langStr);
    NonSentencedSectionedCommunicationAnalytic<StanfordPostNERCommunication> annotator = new AnnotateNonTokenizedConcrete(pl);
    Path inPath = Paths.get(args[0]);
    Path outPath = Paths.get(args[1]);
    new ConcreteStanfordRunner().run(inPath, outPath, annotator);
  }
}
