/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
import edu.jhu.hlt.concrete.communications.WritableCommunication;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.serialization.CommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.CompactCommunicationSerializer;
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
public class AnnotateTokenizedConcrete implements TokenizationedCommunicationAnalytic {

  private static final Logger log = LoggerFactory
      .getLogger(AnnotateTokenizedConcrete.class);

  private final InMemoryAnnoPipeline pipeline;
  private final String language;

  private final static String[] ChineseSectionName = new String[] { "</TURN>",
      "</HEADLINE>", "</TEXT>", "</POST>", "</post>", "</quote>" };
  private static Set<String> ChineseSectionNameSet = new HashSet<String>(
      Arrays.asList(ChineseSectionName));

  public AnnotateTokenizedConcrete(String lang) {
    log.info("Loading models for Stanford tools");
    language = lang;
    pipeline = new InMemoryAnnoPipeline(lang);
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
        ConcreteAnnotator ca = new ConcreteAnnotator(language, annotationList);
        int procCharOffset = cSection.getTextSpan().getStart();
        ca.augmentSectionAnnotations(cSection, sSectionAnno, procCharOffset, sb);
      } catch (IOException e) {
        throw new RuntimeException(e);
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
      ConcreteAnnotator ca = new ConcreteAnnotator(language, annotationList);
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
      /*
       * for (CoreLabel tok : sSent) { if (tok.word().equals("("))
       * tok.setWord("（"); else if (tok.word().equals(")")) tok.setWord("）"); }
       */
      sToks.addAll(sSent);
      sSents.add(sSent);
    }

    List<CoreMap> sentences = mimicWordsToSentsAnnotator(sSents, comm.getText());

    log.info("The tokenlist = {}", sToks);
    // log.info("The sentencelist = " + sentences);
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
    if (log.isDebugEnabled()) {
      StringBuilder sb = new StringBuilder();
      for (CoreLabel sTok : sSent) {
        sb.append(sTok.word());
        sb.append(" ");
      }
      log.debug("Converted sentence: {}", sb.toString());
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
      if (language.equals("en")) {
        sentenceText = text.substring(begin, end);
      } else if (language.equals("cn")) {
        StringBuilder sb = new StringBuilder();
        int cnt = 0;
        for (CoreLabel token : sentenceTokens) {
          if (cnt != 0)
            sb.append(" ");
          sb.append(token.word());
          cnt++;
        }
        sentenceText = sb.toString();
      } else {
        log.error("Do not support language {}", language);
        throw new IllegalArgumentException("Do not support language "
            + language);
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

  public static FileSystem getNewZipFileSystem(Path zipFile) throws IOException {
    if (Files.exists(zipFile)) {
      Files.delete(zipFile);
    }
    URI uri = URI.create("jar:file:" + zipFile.toUri().getPath());
    Map<String, String> env = new HashMap<>();
    env.put("create", "true");
    return FileSystems.newFileSystem(uri, env);
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
    Path inPath = Paths.get(args[0]);
    Path outPath = Paths.get(args[1]);
    String lang = "en";
    if (args.length >= 3) {
      lang = args[2];
    }
    CommunicationSerializer cs = new CompactCommunicationSerializer();
    AnnotateTokenizedConcrete annotator = new AnnotateTokenizedConcrete(lang);

    if (args[0].endsWith(".zip") && args[1].endsWith(".zip")) {
      // Write out to a zip file.
      try (FileSystem zipfs = getNewZipFileSystem(outPath)) {
        try (ZipFile zf = new ZipFile(inPath.toFile())) {
          Enumeration<? extends ZipEntry> e = zf.entries();
          while (e.hasMoreElements()) {
            ZipEntry ze = e.nextElement();
            log.info("Annotating communication: " + ze.getName());
            final Communication comm = cs
                .fromInputStream(zf.getInputStream(ze));
            annotator.annotateWithStanfordNlp(comm);
            new WritableCommunication(comm).writeToFile(
                zipfs.getPath(ze.getName()), true);
          }
        }
      }
    } else if (args[0].endsWith(".comm") && args[1].endsWith(".comm")) {
      // Write out to a file.
      log.info("Annotating communication: " + inPath.getFileName());
      final Communication comm = cs.fromPath(inPath);
      annotator.annotateWithStanfordNlp(comm);
      new WritableCommunication(comm).writeToFile(outPath, true);
    } else {
      // This assumes directory --> directory
      // Write out to a directory.
      if (!Files.exists(inPath)) {
        throw new IOException("Input directory " + inPath + " doesn't exist.");
      }
      if (!Files.isDirectory(inPath)) {
        throw new IOException("Input path " + inPath
            + " exists, but is not a directory.");
      }
      if (!Files.exists(outPath)) {
        Files.createDirectory(outPath);
      }

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(inPath)) {
        for (Path inFile : stream) {
          log.info("Annotating communication: {}", inFile.getFileName());
          final Communication comm = cs.fromPath(inFile);
          annotator.annotateWithStanfordNlp(comm);
          new WritableCommunication(comm).writeToFile(
              outPath.resolve(inFile.getFileName()), true);
        }
      }
    }
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
  public Communication annotate(Communication arg0) throws AnalyticException {
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
  public Communication annotate(TokenizedCommunication arg0) throws AnalyticException {
    Communication cpy = new Communication(arg0.getRoot());
    annotateWithStanfordNlp(cpy);
    return cpy;
  }
}
