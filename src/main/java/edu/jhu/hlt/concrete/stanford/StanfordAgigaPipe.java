/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.tools.AnnotationException;
import edu.jhu.agiga.AgigaDocument;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.communications.PerspectiveCommunication;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.serialization.CommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.CommunicationTarGzSerializer;
import edu.jhu.hlt.concrete.serialization.CompactCommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.TarGzCompactCommunicationSerializer;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentenceIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

public class StanfordAgigaPipe {

  private static final Logger logger = LoggerFactory.getLogger(StanfordAgigaPipe.class);
  // private static final String[] DEFAULT_KINDS_TO_ANNOTATE = new String[] { "Passage", "Other" };

  static final String usage = "You must specify an input path: java edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe --input path/to/input/file --output path/to/output/file\n"
      + "  Optional arguments: \n"
      + "       --annotate-sections <comma-separated-list of type names> (default: PASSAGE)\n"
      + "       --debug\n\t\tto print debugging messages (default: false)\n";

  private static final String[] defaultKindsToFullyProcess = new String[] { "Passage" };
  private static final String[] defaultKindsNoCoref = new String[] { "Title", "Dateline" };

  private int sentenceCount = 1; // for flat files, no document structure

  // private boolean aggregateSectionsByFirst = false;
  // private boolean tokenize = true;
  // private boolean parse = false;

  private final InMemoryAnnoPipeline pipeline;
  private final Set<String> kindsToProcessSet;
  private final Set<String> kindsForNoCoref;

  private final ConcreteStanfordProperties concStanProps;
  private String language;

  /**
   * The global character offset. The exact meaning is determined by {@code usingOriginalCharOffsets()}. When true, this counter is with respect to the
   * <em>original</em> text; when false, this counter is updated according to the processed text.
   */
  private int charOffset = 0;
  private int processedCharOffset = 0;

  /**
   * Whether {@code charOffset} should refer to the original text (true) or the processed text (false). By default, this is true.
   */
  private final boolean useOriginalCharOffsets = true;

  private final boolean allowEmptyEntitiesAndEntityMentions;

  public boolean usingOriginalCharOffsets() {
    return useOriginalCharOffsets;
  }

  // public void setUsingOriginalCharOffsets(boolean b){
  // this.useOriginalCharOffsets = b;
  // }

  private int globalTokenOffset = 0;

  private void resetGlobals() {
    globalTokenOffset = 0;
    sentenceCount = 1;
    charOffset = 0;
    processedCharOffset = 0;
    pipeline.prepForNext();
  }

  /**
   * Deprecated. This method will be removed in a future release.
   *
   * Use {@link ConcreteStanfordAnnotator#main(String[])} instead.
   *
   * @param args
   * @throws TException
   * @throws IOException
   * @throws ConcreteException
   * @throws AnnotationException
   */
  @Deprecated
  public static void main(String[] args) throws TException, IOException, ConcreteException, AnnotationException {
    if (args.length != 2) {
      System.out.println("Usage: " + StanfordAgigaPipe.class.getSimpleName() + " <input-concrete-file-with-section-segmentations> <output-file-name>");
      System.exit(1);
    }

    // this is silly, but needed for stanford logging disable.
    SystemErrDisabler disabler = new SystemErrDisabler();
    disabler.disable();

    StanfordAgigaPipe sap = new StanfordAgigaPipe();
    final CommunicationSerializer cs = new CompactCommunicationSerializer();

    final String inputPath = args[0];
    final String outputPath = args[1];
    String inputType = Files.probeContentType(Paths.get(inputPath));
    if (inputType.equals("application/zip")) {
      ZipFile zf = new ZipFile(inputPath);
      logger.info("Beginning annotation.");
      List<Communication> processedComms = sap.process(zf);
      logger.info("Finished.");

      // ThriftIO.writeFile(outputPath, processedComms);
      CommunicationTarGzSerializer tgz = new TarGzCompactCommunicationSerializer();
      tgz.toTarGz(processedComms, outputPath);
    } else {
      final Communication communication = cs.fromPathString(inputPath);
      logger.info("Beginning annotation.");
      Communication annotated = sap.process(communication);
      logger.info("Finished.");

      new SuperCommunication(annotated).writeToFile(outputPath, true);
    }

    disabler.enable();
  }

  public StanfordAgigaPipe() throws IOException {
    this(Arrays.asList(defaultKindsToFullyProcess), Arrays.asList(defaultKindsNoCoref), true);
  }

  public StanfordAgigaPipe(String lang) throws IOException {
    this();
    this.language = lang;
  }

  public StanfordAgigaPipe(Collection<String> typesToAnnotate, Collection<String> typesToTokenizeOnly, boolean allowEmptyMentions) throws IOException {
    this.kindsToProcessSet = new HashSet<>();
    this.kindsToProcessSet.addAll(typesToAnnotate);

    this.kindsForNoCoref = new HashSet<>();
    this.kindsForNoCoref.addAll(typesToTokenizeOnly);

    this.concStanProps = new ConcreteStanfordProperties();
    this.pipeline = new InMemoryAnnoPipeline();
    this.allowEmptyEntitiesAndEntityMentions = allowEmptyMentions;
    this.language = "en";
  }

  /**
   * NOTE: This method will be removed in a future release.
   *
   * @param zf
   * @return
   * @throws TException
   * @throws IOException
   * @throws ConcreteException
   * @throws AnnotationException
   */
  @Deprecated
  public List<Communication> process(ZipFile zf) throws TException, IOException, ConcreteException, AnnotationException {
    Enumeration<? extends ZipEntry> e = zf.entries();
    List<Communication> outList = new LinkedList<Communication>();
    final CommunicationSerializer ser = new CompactCommunicationSerializer();

    while (e.hasMoreElements()) {
      ZipEntry ze = e.nextElement();
      final Communication communication = ser.fromInputStream(zf.getInputStream(ze));
      final Communication nComm = process(communication);
      outList.add(nComm);
    }
    return outList;
  }

  public Communication process(Communication c) throws IOException, ConcreteException, AnnotationException {
    if (!c.isSetText())
      throw new ConcreteException("Expecting Communication Text, but was empty or none.");

    PerspectiveCommunication pc = new PerspectiveCommunication(c, "PerspectiveCreator");
    Communication persp = pc.getPerspective();

    // hopefully MD is never null
    AnnotationMetadata md = Optional.ofNullable(persp.getMetadata()).orElse(new AnnotationMetadata());
    String csToolName = this.concStanProps.getToolName();
    String newToolName = csToolName + " perspective";

    String mdToolName = md.isSetTool() ? md.getTool() : "";
    if (!mdToolName.isEmpty())
      newToolName += " on old tool: " + mdToolName;

    md.setTool(newToolName);
    persp.setMetadata(md);
    // String newToolName =
    // Communication cp = this.copyToRaw.copyCommunication(c);
    resetGlobals();
    this.runPipelineOnCommunicationSectionsAndSentences(persp);
    return persp;
  }

  /**
   * Construct a dummy Annotation object that will serve as an aggregator. The properties SentencesAnnotation.class and TokensAnnotation.class are initialized
   * with lists of CoreMap and CoreLabel objects, respectively.
   */
  private Annotation getSeededDocumentAnnotation() {
    Annotation documentAnnotation = new Annotation("");
    documentAnnotation.set(SentencesAnnotation.class, new ArrayList<CoreMap>());
    documentAnnotation.set(TokensAnnotation.class, new ArrayList<CoreLabel>());
    return documentAnnotation;
  }

  /**
   * This steps through the given communication. For each section segmentation, it will go through each of the sections, first doing what localized processing
   * it can (i.e., all but coref resolution), and then doing the global processing (coref).
   *
   * @throws AnnotationException
   * @throws IOException
   */
  private void runPipelineOnCommunicationSectionsAndSentences(Communication comm) throws AnnotationException, IOException {
    // if called multiple times, reset the sentence count
    sentenceCount = 1;
    String commText = comm.isSetText() ? comm.getText() : comm.getOriginalText();
    StringBuilder sb = new StringBuilder();
    logger.debug("Annotating communication: {}", comm.getId());
    logger.debug("\tuuid = " + comm.getUuid());
    logger.debug("\ttype = " + comm.getType());
    logger.debug("\treading from " + (comm.isSetText() ? "text" : "raw text"));
    logger.debug("\tfull = " + commText);

    List<Tokenization> tokenizations = new ArrayList<>();
    List<Section> sections = comm.getSectionList();
    // List<Integer> numberOfSentences = new ArrayList<Integer>();
    Annotation documentAnnotation = getSeededDocumentAnnotation();
    logger.debug("documentAnnotation = " + documentAnnotation);

    int previousSectionEnding = 0;
    for (Section section : sections) {
      if(section.isSetSentenceList() && section.isSetRawTextSpan()) {
        int interSectionWhitespaceDifference = section.getRawTextSpan().getStart() - previousSectionEnding;
        charOffset += interSectionWhitespaceDifference;
      }
      int sectionStartCharOffset = processedCharOffset;
      logger.debug("new section, processed offset = " + sectionStartCharOffset);
      TextSpan sts = section.getRawTextSpan();
      // 1) First *perform* the tokenization & sentence splits
      // Note we do this first, even before checking the content-type
      String sectionText = commText.substring(sts.getStart(), sts.getEnding());
      Annotation sectionAnnotation = pipeline.handleSection(section, sectionText);
      logger.debug("Annotating Section: {}", section.getUuid());
      logger.debug("\ttext = " + sectionText);
      logger.debug("\tkind = " + section.getKind() + " in annotateNames: " + this.kindsToProcessSet);
      boolean allButCoref = kindsForNoCoref.contains(section.getKind());
      boolean allWithCoref = kindsToProcessSet.contains(section.getKind());
      if (!allWithCoref && !allButCoref) {
        // We MUST update the character offset
        logger.debug("no good section: from " + charOffset + " to ");
        // NOTE: It's possible we want to account for sentences in non-contentful sections
        // If that's the case, then we need to update the globalToken and sentence offset
        // variables correctly.
        if (sectionAnnotation == null) {
          logger.debug("" + charOffset);
          continue;
        }

        // Note that we need to update the global character offset...
        List<CoreLabel> sentTokens = sectionAnnotation.get(TokensAnnotation.class);
        // int tokCount = 0;
        for (CoreLabel badToken : sentTokens) {
          updateCharOffsetSetToken(badToken, false, false);
        }

        logger.debug("" + charOffset);
        logger.debug("\t" + sectionText);
      } else if (allButCoref) {
        // Only tokenize & sentence split
        logger.debug("Special handling for section type {} section: {}", section.getKind(), section.getUuid());
        logger.debug(">> SectionText=[" + sectionText + "]");
        processSectionForNoCoref(section, sectionAnnotation, sectionStartCharOffset, sb);
      } else {
        // 2) Second, perform the other localized processing
        logger.debug("Additional processing on section: {}", section.getUuid());
        logger.debug(">> SectionText=[" + sectionText + "]");
        processSection(section, sectionAnnotation, documentAnnotation, sectionStartCharOffset, sb);
        addTokenizations(section, tokenizations);
      }
      // between sections are two line feeds
      // one is counted for in the sentTokens loop above
      processedCharOffset++;
      if(section.isSetRawTextSpan()) {
        previousSectionEnding = section.getRawTextSpan().getEnding();
      }
    }

    comm.setText(sb.toString());

    // 3) Third, do coref; cross-reference against sectionUUIDs
    logger.debug("Running coref.");
    processCoref(comm, documentAnnotation, tokenizations);
  }

  private void addTokenizations(Section section, List<Tokenization> tokenizations) {
    if (!section.isSetSentenceList())
      return;
    for (Sentence sentence : section.getSentenceList()) {
      if (sentence.isSetTokenization())
        tokenizations.add(sentence.getTokenization());
    }
  }

  /**
   * On a given annotation object representing a single section, run
   * <ul>
   * <li>part-of-speech tagging,</li>
   * <li>lemmatization,</li>
   * <li>constituency and dependency parsing, and</li>
   * <li>named entity recognition.</li>
   * </ul>
   * Note that corefence resolution is done only once all contentful sections have been properly annotated.
   *
   * @throws AnnotationException
   *
   */
  private AgigaDocument annotate(Annotation annotation) throws AnnotationException {
    try {
      return pipeline.annotate(annotation);
    } catch (IOException e) {
      throw new AnnotationException(e);
    }
  }

  private AgigaDocument getAgigaDocAllButCoref(Annotation annotation) throws AnnotationException {
    try {
      return pipeline.getAgigaDocAllButCoref(annotation);
    } catch (IOException e) {
      throw new AnnotationException(e);
    }
  }

  private AgigaDocument getAgigaDoc(Annotation annotation, boolean tokensOnly) throws AnnotationException {
    try {
      return pipeline.getAgigaDoc(annotation, tokensOnly);
    } catch (IOException e) {
      throw new AnnotationException(e);
    }
  }

  private AgigaDocument annotateCoref(Annotation annotation) throws AnnotationException {
    try {
      return pipeline.annotateCoref(annotation);
    } catch (IOException e) {
      throw new AnnotationException(e);
    }
  }

  /**
   * Convert tokenized sentences (<code>sentAnno</code>) into a document Annotation.<br/>
   * The global indexers {@code charOffset} and {@code globalTokenOffset} are updated here.
   *
   */
  private void sentencesToSection(Section concreteSection, CoreMap sectAnno, Annotation document) throws AnnotationException {
    if (sectAnno == null) {
      logger.warn("Encountered null annotated section. Skipping.");
      return;
    }

    List<CoreMap> docSents = document.get(SentencesAnnotation.class);
    List<CoreLabel> docTokens = document.get(TokensAnnotation.class);
    logger.debug("converting list of CoreMap sentences to Annotations, starting at token offset " + globalTokenOffset);

    List<CoreMap> sentAnnos = sectAnno.get(SentencesAnnotation.class);
    int maxCharEnding = -1;
    boolean isFirst = true;
    int sentenceIdx = 0;
    List<Sentence> concreteSentences = null;
    int numConcreteSentences = 0;
    boolean concreteSentencesSet = false;
    if(concreteSection.isSetSentenceList()) {
      concreteSentencesSet = true;
      concreteSentences = concreteSection.getSentenceList();
      numConcreteSentences = concreteSentences.size();
      if(concreteSection.getSentenceList().size() != sentAnnos.size()) {
        throw new AnnotationException("Section " + concreteSection.getUuid() + " has " +
                                      concreteSection.getSentenceList().size() + " sentences already created," +
                                      " but CoreNLP only found " + sentAnnos.size());
      }
    }
    for (CoreMap sentAnno : sentAnnos) {
      List<CoreLabel> sentTokens = sentAnno.get(TokensAnnotation.class);
      int tokenEnd = globalTokenOffset + sentTokens.size();
      sentAnno.set(TokenBeginAnnotation.class, globalTokenOffset);
      sentAnno.set(TokenEndAnnotation.class, tokenEnd);
      sentAnno.set(SentenceIndexAnnotation.class, sentenceCount++);
      logger.debug("SENTENCEINDEXANNO = " + sentAnno.get(SentenceIndexAnnotation.class));
      globalTokenOffset = tokenEnd;

      for (CoreLabel token : sentTokens) {
        // note that character offsets are global
        updateCharOffsetSetToken(token, isFirst, true);
        logger.debug("this token goes from " + token.get(CharacterOffsetBeginAnnotation.class) + " to " + token.get(CharacterOffsetEndAnnotation.class));
        logger.debug("\toriginal:[[" + token.originalText() + "]]");
        logger.debug("\tbefore:<<" + token.before() + ">>");
        logger.debug("\tafter:<<" + token.after() + ">>");
        if (isFirst) {
          isFirst = false;
        }
      }
      // if there are > 1 sentence, then we need to account for any space in between the Concrete sentences
      if(concreteSentencesSet) {
        if(sentenceIdx + 1 < numConcreteSentences) {
          Sentence concreteSentence = concreteSentences.get(sentenceIdx++);
          Sentence nextConcreteSentence = concreteSentences.get(sentenceIdx);
          int interSentenceWhitespaceDifference = nextConcreteSentence.getRawTextSpan().getStart() - concreteSentence.getRawTextSpan().getEnding();
          charOffset += interSentenceWhitespaceDifference;
        }
      }
      sentAnno.set(TokensAnnotation.class, sentTokens);
      sentAnno.set(CharacterOffsetBeginAnnotation.class, sentTokens.get(0).get(CharacterOffsetBeginAnnotation.class));
      int endingSentCOff = sentTokens.get(sentTokens.size() - 1).get(CharacterOffsetEndAnnotation.class);
      sentAnno.set(CharacterOffsetEndAnnotation.class, endingSentCOff);

      logger.debug("docTokens.size before = " + docTokens.size());
      docTokens.addAll(sentTokens);
      logger.debug("\t after = " + docTokens.size());
      document.set(TokensAnnotation.class, docTokens);
      logger.debug("\t retrieved = " + document.get(TokensAnnotation.class).size());
      docSents.add(sentAnno);
      if (sentAnno.get(CharacterOffsetEndAnnotation.class) > maxCharEnding)
        maxCharEnding = sentAnno.get(CharacterOffsetEndAnnotation.class);

    }
    document.set(SentencesAnnotation.class, docSents);
    Integer oldDocCharE = document.get(CharacterOffsetEndAnnotation.class);
    if (oldDocCharE != null && maxCharEnding < oldDocCharE)
      throw new AnnotationException("The max char ending for this section (" + maxCharEnding + ") is less than the current document char ending ( "
          + oldDocCharE + ")");
    document.set(CharacterOffsetEndAnnotation.class, maxCharEnding);
  }

  /**
   * Update character offsets for (<code>sentAnno</code>).<br/>
   * The global indexers {@code charOffset} and {@code globalTokenOffset} are updated here.
   *
   */
  private void sentencesToSection(Section concreteSection, CoreMap sectAnno) throws AnnotationException {
    if (sectAnno == null) {
      logger.warn("Encountered null annotated section. Skipping.");
      return;
    }

    logger.debug("converting list of CoreMap sentences to Annotations, starting at token offset " + globalTokenOffset);

    List<CoreMap> sentAnnos = sectAnno.get(SentencesAnnotation.class);
    int maxCharEnding = -1;
    boolean isFirst = true;
    int sentenceIdx = 0;
    List<Sentence> concreteSentences = null;
    int numConcreteSentences = 0;
    boolean concreteSentencesSet = false;
    if(concreteSection.isSetSentenceList()) {
      concreteSentencesSet = true;
      concreteSentences = concreteSection.getSentenceList();
      numConcreteSentences = concreteSentences.size();
      if(concreteSection.getSentenceList().size() != sentAnnos.size()) {
        throw new AnnotationException("Section " + concreteSection.getUuid() + " has " +
                                      concreteSection.getSentenceList().size() + " sentences already created," +
                                      " but CoreNLP only found " + sentAnnos.size());
      }
    }
    for (CoreMap sentAnno : sentAnnos) {
      List<CoreLabel> sentTokens = sentAnno.get(TokensAnnotation.class);
      int tokenEnd = globalTokenOffset + sentTokens.size();
      sentAnno.set(TokenBeginAnnotation.class, globalTokenOffset);
      sentAnno.set(TokenEndAnnotation.class, tokenEnd);
      // sentAnno.set(SentenceIndexAnnotation.class, sentenceCount++);
      logger.debug("SENTENCEINDEXANNO = " + sentAnno.get(SentenceIndexAnnotation.class));
      globalTokenOffset = tokenEnd;

      for (CoreLabel token : sentTokens) {
        // note that character offsets are global
        // String tokenText = token.get(TextAnnotation.class);
        updateCharOffsetSetToken(token, isFirst, true);
        logger.debug("this token goes from " + token.get(CharacterOffsetBeginAnnotation.class) + " to " + token.get(CharacterOffsetEndAnnotation.class));
        logger.debug("\toriginal:[[" + token.originalText() + "]]");
        logger.debug("\tbefore:<<" + token.before() + ">>");
        logger.debug("\tafter:<<" + token.after() + ">>");
        if (isFirst) {
          isFirst = false;
        }
      }
      // if there are > 1 sentence, then we need to account for any space in between the Concrete sentences
      if(concreteSentencesSet) {
        if(sentenceIdx + 1 < numConcreteSentences) {
          Sentence concreteSentence = concreteSentences.get(sentenceIdx++);
          Sentence nextConcreteSentence = concreteSentences.get(sentenceIdx);
          int interSentenceWhitespaceDifference = nextConcreteSentence.getRawTextSpan().getStart() - concreteSentence.getRawTextSpan().getEnding();
          charOffset += interSentenceWhitespaceDifference;
        }
      }
      sentAnno.set(TokensAnnotation.class, sentTokens);
      sentAnno.set(CharacterOffsetBeginAnnotation.class, sentTokens.get(0).get(CharacterOffsetBeginAnnotation.class));
      int endingSentCOff = sentTokens.get(sentTokens.size() - 1).get(CharacterOffsetEndAnnotation.class);
      sentAnno.set(CharacterOffsetEndAnnotation.class, endingSentCOff);

      if (sentAnno.get(CharacterOffsetEndAnnotation.class) > maxCharEnding)
        maxCharEnding = sentAnno.get(CharacterOffsetEndAnnotation.class);

    }
  }

  private void updateCharOffsetSetToken(CoreLabel token, boolean isFirst, boolean updateProcessedOff) {
    if (usingOriginalCharOffsets()) {
      if (isFirst) {
        // this is because when we have text like "foo bar", foo.after == " " AND bar.before == " "
        int beforeLength = token.before().length();
        charOffset += beforeLength;
      }
      logger.debug("[" + token.before() + ", " + token.before().length() + "] " + "[" + token.originalText() + "]" + " [" + token.after() + ", "
                   + token.after().length() + "] :: " + charOffset + " --> " +
                   (charOffset + token.originalText().length()));
      token.set(CharacterOffsetBeginAnnotation.class, charOffset);
      charOffset += token.originalText().length();
      token.set(CharacterOffsetEndAnnotation.class, charOffset);
      charOffset += token.after().length();
    } else {
      token.set(CharacterOffsetBeginAnnotation.class, charOffset);
      charOffset += token.get(TextAnnotation.class).length();
      token.set(CharacterOffsetEndAnnotation.class, charOffset);
      charOffset++;
    }
    if (updateProcessedOff) {
      processedCharOffset += token.get(TextAnnotation.class).length() + 1;
    }
  }

  /**
   * Transfer an individual section's annotations to the global accumulating document. This allows global annotators (coref) to use local information, such as
   * parses.
   */
  private void transferAnnotations(Annotation section, Annotation document) {
    List<CoreMap> sectionSents = section.get(SentencesAnnotation.class);
    ArrayList<CoreMap> documentSents = (ArrayList<CoreMap>) document.get(SentencesAnnotation.class);
    logger.debug("\t******Document sents*********");
    for (CoreMap sectSent : sectionSents) {
      int idx = sectSent.get(SentenceIndexAnnotation.class) - 1;
      logger.debug("My index is " + idx + " (" + sectSent.get(SentenceIndexAnnotation.class) + "), and can access up to " + documentSents.size()
          + " sentences globally");
      if (sectSent.containsKey(TreeAnnotation.class)) {
        CoreMap dSent = documentSents.get(idx);
        dSent.set(TreeAnnotation.class, sectSent.get(TreeAnnotation.class));
        logger.debug(dSent.get(TreeAnnotation.class).toString());
        logger.debug(dSent.get(TreeAnnotation.class).getLeaves().toString());
        logger.debug(sectSent.get(TokensAnnotation.class).toString());
        logger.debug(idx + " --> " + dSent.get(TokensAnnotation.class));
      }
    }
  }

  /**
   * Given a particular section {@link Section} from a {@link Communication}, further locally process {@link Annotation}; add those new annotations to an
   * aggregating {@link Annotation} to use for later global processing.
   *
   * @throws AnnotationException
   * @throws IOException
   */

  private void processSectionForNoCoref(Section section, Annotation sentenceSplitText, int sectionOffset, StringBuilder sb) throws AnnotationException,
      IOException {
    sentencesToSection(section, sentenceSplitText);

    //AgigaDocument agigaDoc = getAgigaDoc(sentenceSplitText, true);
    AgigaDocument agigaDoc = getAgigaDocAllButCoref(sentenceSplitText);
    logger.debug("after annotating");

    AgigaConcreteAnnotator agigaToConcrete = new AgigaConcreteAnnotator(usingOriginalCharOffsets(), this.language);
    agigaToConcrete.convertSection(section, agigaDoc, sectionOffset, sb, true);
    setSectionTextSpan(section, sectionOffset, processedCharOffset, true);
  }

  /**
   * Given a particular section {@link Section} from a {@link Communication}, further locally process {@link Annotation}; add those new annotations to an
   * aggregating {@link Annotation} to use for later global processing.
   *
   * @throws AnnotationException
   * @throws IOException
   */
  private void processSection(Section section, Annotation sentenceSplitText, Annotation docAnnotation, int sectionOffset, StringBuilder sb)
      throws AnnotationException, IOException {
    sentencesToSection(section, sentenceSplitText, docAnnotation);
    logger.debug("after sentencesToSection, before annotating");
    for (CoreMap cm : sentenceSplitText.get(SentencesAnnotation.class))
      logger.debug(cm.get(SentenceIndexAnnotation.class).toString());

    AgigaDocument agigaDoc = annotate(sentenceSplitText);
    logger.debug("after annotating");
    for (CoreMap cm : sentenceSplitText.get(SentencesAnnotation.class))
      logger.debug(cm.get(SentenceIndexAnnotation.class).toString());

    transferAnnotations(sentenceSplitText, docAnnotation);
    AgigaConcreteAnnotator agigaToConcrete = new AgigaConcreteAnnotator(usingOriginalCharOffsets(), this.language);
    agigaToConcrete.convertSection(section, agigaDoc, sectionOffset, sb, true);
    setSectionTextSpan(section, sectionOffset, processedCharOffset, true);
  }

  private static void setSectionTextSpan(Section section, int start, int end, boolean compensate) throws AnnotationException {
    if (!section.isSetTextSpan()) {
      int compE = compensate ? (end - 1) : end;
      if (compE <= start)
        throw new AnnotationException("Cannot create compensated textspan for section " + section.getUuid() + "; provided offsets = (" + start + "," + end
            + "), compensated offsets = (" + start + "," + compE + ")");

      TextSpan txs = new TextSpan().setStart(start).setEnding(compE);
      section.setTextSpan(txs);
    }
  }

  private void processCoref(Communication comm, Annotation docAnnotation, List<Tokenization> tokenizations) throws AnnotationException, IOException {
    AgigaDocument agigaDoc = annotateCoref(docAnnotation);
    AgigaConcreteAnnotator agigaToConcrete = new AgigaConcreteAnnotator(usingOriginalCharOffsets(), this.language);
    SimpleEntry<EntityMentionSet, EntitySet> tuple = agigaToConcrete.convertCoref(comm, agigaDoc, tokenizations);
    EntityMentionSet ems = tuple.getKey();
    EntitySet es = tuple.getValue();

    if (ems == null || !ems.isSetMentionList())
      throw new AnnotationException("Concrete-agiga produced a null EntityMentionSet, or a null mentionList.");

    if (ems.getMentionListSize() == 0 && !this.allowEmptyEntitiesAndEntityMentions)
      throw new AnnotationException("Empty entity mentions are disallowed and no entity mentions were produced for communication: " + comm.getId());
    else
      comm.addToEntityMentionSetList(ems);

    if (es == null || !es.isSetEntityList())
      throw new AnnotationException("Concrete-agiga produced a null EntitySet, or a null entityList.");

    if (es.getEntityListSize() == 0 && !this.allowEmptyEntitiesAndEntityMentions)
      throw new AnnotationException("Empty entities are disallowed and no entities were produced for communication: " + comm.getId());
    else
      comm.addToEntitySetList(es);
  }

  public Set<String> getSectionTypesToAnnotate() {
    return new HashSet<>(this.kindsToProcessSet);
  }

  /**
   * convert a tree t to its token representation
   *
   * @param t
   * @return
   * @throws IOException
   */
  protected String getText(Tree t) throws IOException {
    if (t == null)
      return null;

    StringBuilder sb = new StringBuilder();

    for (Tree tt : t.getLeaves()) {
      sb.append(tt.value());
      sb.append(" ");
    }
    return sb.toString().trim();
  }
}
