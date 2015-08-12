/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.analytics.base.TokenizationedCommunicationAnalytic;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.util.Timing;
import edu.jhu.hlt.utilt.ex.LoggedUncaughtExceptionHandler;

/**
 * Given tokenized Concrete as input, this class will annotate sentences with
 * the Stanford NLP tools and add the annotations back in their Concrete
 * representations.<br>
 * <br>
 * This class assumes that the input has been tokenized using a PTB-like
 * tokenization. There is a known bug in the Stanford library which will throw
 * an exception when trying to perform semantic head finding on after parsing
 * the sentence "( CROSSTALK )". The error will not occur given the input
 * "-LRB- CROSSTALK -RRB-".
 *
 * @author mgormley
 * @author npeng
 */
public class AnnotateTokenizedConcrete implements TokenizationedCommunicationAnalytic<StanfordPostNERCommunication> {

  private static final Logger logger = LoggerFactory
      .getLogger(AnnotateTokenizedConcrete.class);

  private final ConcreteStanfordPreCorefAnalytic analytic;

  public AnnotateTokenizedConcrete(PipelineLanguage lang) {
    this.analytic = new ConcreteStanfordPreCorefAnalytic(lang);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.analytics.base.Analytic#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public StanfordPostNERCommunication annotate(Communication arg0) throws AnalyticException {
    try {
      return this.annotate(new CachedTokenizationCommunication(arg0));
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
    return AnnotateTokenizedConcrete.class.getSimpleName();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolVersion()
   */
  @Override
  public String getToolVersion() {
    return ProjectConstants.VERSION;
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.analytics.base.TokenizationedCommunicationAnalytic#annotate(edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication)
   */
  @Override
  public StanfordPostNERCommunication annotate(TokenizedCommunication arg0) throws AnalyticException {
    try {
      TokenizedCommunication tc = this.analytic.annotate(arg0);
      return new StanfordPostNERCommunication(tc.getRoot());
    } catch (MiscommunicationException e) {
      throw new AnalyticException(e);
    }
  }

  /**
   * Usage is: inputPath outputPath [language] Currently, three modes between
   * inputPath and outputPath are supported:
   * <ul>
   * <li>.tar to .tar</li>
   * <li>.tar.gz to .tar.gz</li>
   * <li>single comm to single comm</li>
   * </ul>
   * <br>
   * The optional third argument language defaults to en (English). Currently,
   * the only other supported option is cn (Chinese).
   */
  public static void main(String[] args) {
    Thread.setDefaultUncaughtExceptionHandler(new LoggedUncaughtExceptionHandler());
    int argLen = args.length;
    if (argLen < 2) {
      logger.info("This program takes at least 2 arguments:");
      logger.info("Argument 1: path to a .concrete file (representing a communication), a .tar file, or .tar.gz file"
          + " with concrete communication objects.");
      logger.info("The input communication(s) must have Tokenizations.");
      logger.info("Argument 2: path to an output file, including the extension.");
      logger.info("Argument 3 (optional): language. Default: en. Supported: en [English], cn [Chinese]");

      logger.info("Usage example: {} {} {} [{}]", AnnotateTokenizedConcrete.class.toString(),
          "path/to/input/file.extension", "path/to/output/file.extension", "en");
      System.exit(1);
    }

    // infer language
    String langStr = argLen >= 3 ? args[2] : "en";
    PipelineLanguage pl = PipelineLanguage.getEnumeration(langStr);
    TokenizationedCommunicationAnalytic<StanfordPostNERCommunication> annotator = new AnnotateTokenizedConcrete(pl);
    Path inPath = Paths.get(args[0]);
    Path outPath = Paths.get(args[1]);
    new ConcreteStanfordRunner().run(inPath, outPath, annotator);
  }
}
