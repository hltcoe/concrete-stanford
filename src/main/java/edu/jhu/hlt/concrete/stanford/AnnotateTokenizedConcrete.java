/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.analytics.base.TokenizationedCommunicationAnalytic;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.Timing;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.util.CoreMap;

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
public class AnnotateTokenizedConcrete implements TokenizationedCommunicationAnalytic<StanfordPreNERCommunication> {

  private static final Logger logger = LoggerFactory
      .getLogger(AnnotateTokenizedConcrete.class);

  private final InMemoryAnnoPipeline pipeline;
  // private final String language;
  private final PipelineLanguage lang;

  private final static String[] ChineseSectionName = new String[] { "</TURN>",
      "</HEADLINE>", "</TEXT>", "</POST>", "</post>", "</quote>" };
  private static Set<String> ChineseSectionNameSet = new HashSet<String>(
      Arrays.asList(ChineseSectionName));

  public AnnotateTokenizedConcrete(PipelineLanguage lang) {
    logger.info("Loading models for Stanford tools");
    this.lang = lang;
    this.pipeline = new InMemoryAnnoPipeline(lang);
  }

  /**
   * Annotates a Concrete {@link Communication} with the Stanford NLP tools.<br>
   * <br>
   * NOTE: Currently, this only supports per-sentence annotation. Coreference
   * resolution is not performed.
   *
   * @param comm
   *          The concrete communication.
   */
  public void annotateWithStanfordNlp(Communication comm) throws AnalyticException {
    StringBuilder sb = new StringBuilder();
    for (Section cSection : comm.getSectionList()) {
      if (cSection.isSetLabel()
          && !ChineseSectionNameSet.contains(cSection.getLabel()))
        continue;
      Annotation sSectionAnno = getSectionAsAnnotation(cSection, comm);
      try {
        pipeline.annotateLocalStages(sSectionAnno);
        String[] annotationList = { "pos", "cparse", "dparse" };
        ConcreteAnnotator ca = new ConcreteAnnotator(this.lang, annotationList);
        int procCharOffset = cSection.getTextSpan().getStart();
        ca.augmentSectionAnnotations(cSection, sSectionAnno, procCharOffset, sb);
      } catch (IOException e) {
        throw new AnalyticException(e);
      }
    }
  }

  /**
   * Annotates a Concrete {@link Sentence} with the Stanford NLP tools.
   *
   * @param cSent
   *          The concrete sentence.
   * @param comm
   *          The communication from which to extract the source text.
   */
  public void annotateWithStanfordNlp(Sentence cSent, Communication comm)
      throws AnalyticException {
    Annotation sSentAnno = getSentenceAsAnnotation(cSent, comm);
    try {
      pipeline.annotateLocalStages(sSentAnno);
      String[] annotationList = { "pos", "cparse", "dparse" };
      ConcreteAnnotator ca = new ConcreteAnnotator(this.lang, annotationList);
      int procCharOffset = cSent.getTextSpan().getStart();
      ca.augmentTokenization(cSent.getTokenization(), sSentAnno, procCharOffset);
    } catch (IOException e) {
      throw new AnalyticException(e);
    }
  }

  /**
   * Converts a Concrete {@link Section} to a Stanford {@link Annotation}.
   *
   * @param cSection
   *          The concrete section.
   * @param comm
   *          The communication from which to extract the source text.
   * @return The annotation representing the section.
   */
  private Annotation getSectionAsAnnotation(Section cSection, Communication comm)
      throws AnalyticException {
    List<Sentence> cSents = cSection.getSentenceList();
    return concreteSentListToAnnotation(cSents, comm);
  }

  /**
   * Converts a Concrete {@link Sentence} to a Stanford {@link Annotation}.
   *
   * @param cSection
   *          The concrete sentence.
   * @param comm
   *          The communication from which to extract the source text.
   * @return The annotation representing the section.
   */
  private Annotation getSentenceAsAnnotation(Sentence cSent, Communication comm)
      throws AnalyticException {
    List<Sentence> cSents = new ArrayList<>();
    cSents.add(cSent);
    return concreteSentListToAnnotation(cSents, comm);
  }

  /**
   * Converts a {@link List} of Concrete {@link Sentence} to a Stanford
   * {@link Annotation}.
   *
   * @param cSents
   *          The list of concrete sentences.
   * @param comm
   *          The communication from which to extract the source text.
   * @return The annotation representing the list of sentences.
   */
  private Annotation concreteSentListToAnnotation(List<Sentence> cSents,
      Communication comm) throws AnalyticException {
    Annotation sSectionAnno = new Annotation(comm.getText());
    // Done by constructor: sectionAnno.set(CoreAnnotations.TextAnnotation,
    // null);

    List<CoreLabel> sToks = new ArrayList<>();
    List<List<CoreLabel>> sSents = new ArrayList<>();
    for (Sentence cSent : cSents) {
      List<CoreLabel> sSent = concreteSentToCoreLabels(cSent, comm);
      sToks.addAll(sSent);
      sSents.add(sSent);
    }

    List<CoreMap> sentences = mimicWordsToSentsAnnotator(sSents, comm.getText());

    logger.info("The tokenlist = {}", sToks);
    sSectionAnno.set(CoreAnnotations.TokensAnnotation.class, sToks);
    sSectionAnno.set(CoreAnnotations.SentencesAnnotation.class, sentences);
    return sSectionAnno;
  }

  /**
   * Converts a Concrete {@link Sentence} to a {@link List} of {@Link
   * CoreLabel}s representing each token.
   *
   * @param cSent
   *          The concrete sentence.
   * @param comm
   *          The communication from which to extract the source text.
   * @return The list of core labels.
   */
  private List<CoreLabel> concreteSentToCoreLabels(Sentence cSent,
      Communication comm) {
    CoreLabelTokenFactory coreLabelTokenFactory = new CoreLabelTokenFactory();
    List<CoreLabel> sSent = new ArrayList<>();
    Tokenization cToks = cSent.getTokenization();
    for (Token cTok : cToks.getTokenList().getTokenList()) {
      TextSpan cSpan = cTok.getTextSpan();
      String text = cTok.getText();
      if (text.equals("(")) {
        // cTok.setText("（");
        // text = "（";
      } else if (text.equals(")")) {
        // cTok.setText("）");
        // text = "）";
      }
      int length = cSpan.getEnding() - cSpan.getStart();
      CoreLabel sTok = coreLabelTokenFactory.makeToken(text, comm.getText(),
          cSpan.getStart(), length);
      sSent.add(sTok);
    }
    if (logger.isDebugEnabled()) {
      StringBuilder sb = new StringBuilder();
      for (CoreLabel sTok : sSent) {
        sb.append(sTok.word());
        sb.append(" ");
      }
      logger.debug("Converted sentence: {}", sb.toString());
    }
    return sSent;
  }

  /**
   * This method mimics the behavior of Stanford's WordsToSentencesAnnotator to
   * create a List<CoreMap>s from a List<List<CoreLabel>>.
   */
  private List<CoreMap> mimicWordsToSentsAnnotator(
      List<List<CoreLabel>> sSents, String text) throws AnalyticException {
    int tokenOffset = 0;
    List<CoreMap> sentences = new ArrayList<CoreMap>();
    for (List<CoreLabel> sentenceTokens : sSents) {
      if (sentenceTokens.isEmpty()) {
        throw new AnalyticException("unexpected empty sentence: "
            + sentenceTokens);
      }

      // get the sentence text from the first and last character offsets
      int begin = sentenceTokens.get(0).get(
          CharacterOffsetBeginAnnotation.class);
      int last = sentenceTokens.size() - 1;
      int end = sentenceTokens.get(last)
          .get(CharacterOffsetEndAnnotation.class);
      String sentenceText = "";
      switch (this.lang) {
      case ENGLISH:
        sentenceText = text.substring(begin, end);
        break;
      case CHINESE:
        StringBuilder sb = new StringBuilder();
        int cnt = 0;
        for (CoreLabel token : sentenceTokens) {
          if (cnt != 0)
            sb.append(" ");
          sb.append(token.word());
          cnt++;
        }
        sentenceText = sb.toString();
        break;
      default:
        throw new IllegalArgumentException("Language: " + this.lang.toString() + " is not yet supported.");
      }

      // create a sentence annotation with text and token offsets
      Annotation sentence = new Annotation(sentenceText);
      sentence.set(CharacterOffsetBeginAnnotation.class, begin);
      sentence.set(CharacterOffsetEndAnnotation.class, end);
      sentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);
      sentence.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffset);
      tokenOffset += sentenceTokens.size();
      sentence.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffset);

      // add the sentence to the list
      sentences.add(sentence);
    }
    return sentences;
  }

  /**
   * Usage is: inputPath outputPath [language] Currently, three modes between
   * inputPath and outputPath are supported:
   * <ul>
   * <li>zip file to zip file</li>
   * <li>single comm to single comm</li>
   * <li>directory of comms to directory of comms</li>
   * </ul>
   * <br/>
   * The optional third argument language defaults to en (English). Currently,
   * the only other supported option is cn (Chinese).
   */
  public static void main(String[] args) throws IOException, ConcreteException,
      AnalyticException {
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
    TokenizationedCommunicationAnalytic<StanfordPreNERCommunication> annotator = new AnnotateTokenizedConcrete(pl);
    Path inPath = Paths.get(args[0]);
    Path outPath = Paths.get(args[1]);
    new ConcreteStanfordRunner().run(inPath, outPath, annotator);
  }


  /**
   * A validator object to ensure that all prerequisites are met. The
   * communication must:
   * <ul>
   * <li>Have a non-empty .text field
   * <li>Have sections with
   * </ul>
   */
  static class PrereqValidator {
    /*
     *
     * @param comm
     * @param useThrow
     *          If true, throw an exception rather than returning {@code false}
     *          if given a non-valid communication. Otherwise, return
     *          true/false.
     * @return True iff {@code comm} satisfies the requirements of a
     *         communication to be annotated.
     *         <ul>
     *         <li>It must not be null.</li>
     *         <li>It must be a valid Concrete communication. In particular:
     *         <ul>
     *         <li>It must have a non-empty .id field set.</li>
     *         <li>It must have a non-empty .text field set.</li>
     *         </ul>
     *         <li>It must have verifiable sections (see {@code verifySentence}.
     *         </li>
     *         <li>It must have a .metadata field set with a non-empty .tool
     *         field.</li>
     *         </ul>
     */

    /*
     *
     * @param section
     * @param sb
     * @return True iff {@code section} satisfies the requirements of a section
     *         to be annotated.
     *         <ul>
     *         <li>It must not be null.</li>
     *         <li>It must have a .textSpan field set.</li>
     *         <li>It must have verifiable Sentences.</li>
     *         </ul>
     */

    /*
     *
     * @param sentence
     * @param sb
     * @return True iff {@code sentence} satisfies the requirements of a
     *         sentence to be annotated.
     *         <ul>
     *         <li>It must not be null.</li>
     *         <li>It must have a .textSpan field set.</li>
     *         <li>It must have a .tokenization field.</li>
     *         </ul>
     */


    /*
     *
     * @param tokenization
     * @param sb
     * @return True iff:
     *         <ul>
     *         <li>The tokenization is not null and has a set .tokenList
     *         <li>Every token in .tokenList is a valid token
     *         </ul>
     */

    /*
     *
     * @param token
     * @param sb
     * @return True iff {@code token}:
     *         <ul>
     *         <li>Is not null;
     *         <li>Has a valid .textSpan; and
     *         <li>Has a valid .text set.
     *         </ul>
     */

    /*
     *
     * @param textSpan
     * @param sb
     * @return True iff {@code textSpan} satisfies the requirements of a valid
     *         (enough) TextSpan.
     *         <ul>
     *         <li>It must not be null.</li>
     *         <li>Its endpoints must be non-negative.</li>
     *         <li>It must have non-zero length.</li>
     *         </ul>
     */
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.analytics.base.Analytic#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public StanfordPreNERCommunication annotate(Communication arg0) throws AnalyticException {
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
  public StanfordPreNERCommunication annotate(TokenizedCommunication arg0) throws AnalyticException {
    Communication cpy = new Communication(arg0.getRoot());
    annotateWithStanfordNlp(cpy);
    try {
      return new StanfordPreNERCommunication(cpy);
    } catch (MiscommunicationException e) {
      throw new AnalyticException(e);
    }
  }
}
