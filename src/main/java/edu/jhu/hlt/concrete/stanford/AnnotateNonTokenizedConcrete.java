/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.TokenizationKind;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.analytics.base.SectionedCommunicationAnalytic;
import edu.jhu.hlt.concrete.communications.PerspectiveCommunication;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.sectioned.CachedSectionedCommunication;
import edu.jhu.hlt.concrete.miscommunication.sectioned.NonSentencedSectionedCommunication;
import edu.jhu.hlt.concrete.miscommunication.sectioned.SectionedCommunication;
import edu.jhu.hlt.concrete.stanford.InMemoryAnnoPipeline.Languages;
import edu.jhu.hlt.concrete.tokenization.TokenizationFactory;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.Timing;
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

public class AnnotateNonTokenizedConcrete implements SectionedCommunicationAnalytic<StanfordPostNERCommunication> {

  private static final Logger logger = LoggerFactory
      .getLogger(AnnotateNonTokenizedConcrete.class);

  static final String usage = "You must specify an input path: java edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe --input path/to/input/file --output path/to/output/file\n"
      + "  Optional arguments: \n"
      + "       --annotate-sections <comma-separated-list of type names> (default: PASSAGE)\n"
      + "       --debug\n\t\tto print debugging messages (default: false)\n";

  private static final String[] defaultKindsToFullyProcess = new String[] { "Passage" };
  private static final String[] defaultKindsNoCoref = new String[] { "Title",
      "Dateline" };

  private int sentenceCount = 1; // for flat files, no document structure

  private final InMemoryAnnoPipeline pipeline;
  private final Set<String> kindsToProcessSet;
  private final Set<String> kindsForNoCoref;

  private Languages language;

  /**
   * The global character offset. The exact meaning is determined by
   * {@code usingOriginalCharOffsets()}. When true, this counter is with respect
   * to the <em>original</em> text; when false, this counter is updated
   * according to the processed text.
   */
  private int charOffset = 0;
  private int processedCharOffset = 0;

  /**
   * Whether {@code charOffset} should refer to the original text (true) or the
   * processed text (false). By default, this is true.
   */
  private final boolean useOriginalCharOffsets = true;

  private final boolean allowEmptyEntitiesAndEntityMentions;

  public boolean usingOriginalCharOffsets() {
    return useOriginalCharOffsets;
  }

  private int globalTokenOffset = 0;

  private void resetGlobals() {
    globalTokenOffset = 0;
    sentenceCount = 1;
    charOffset = 0;
    processedCharOffset = 0;
    pipeline.prepForNext();
  }

  public AnnotateNonTokenizedConcrete() {
    this(Languages.ENGLISH, Arrays.asList(defaultKindsToFullyProcess), Arrays
        .asList(defaultKindsNoCoref), true);
  }

  public AnnotateNonTokenizedConcrete(Languages lang) {
    this(lang, Arrays.asList(defaultKindsToFullyProcess), Arrays
        .asList(defaultKindsNoCoref), true);
  }

  public AnnotateNonTokenizedConcrete(Languages lang, Collection<String> typesToAnnotate,
      Collection<String> typesToTokenizeOnly, boolean allowEmptyMentions) {
    this.kindsToProcessSet = new HashSet<>();
    this.kindsToProcessSet.addAll(typesToAnnotate);

    this.kindsForNoCoref = new HashSet<>();
    this.kindsForNoCoref.addAll(typesToTokenizeOnly);

    this.language = Languages.ENGLISH;
    this.pipeline = new InMemoryAnnoPipeline(this.language);
    this.allowEmptyEntitiesAndEntityMentions = allowEmptyMentions;
  }

  /**
   * Construct a dummy Annotation object that will serve as an aggregator. The
   * properties SentencesAnnotation.class and TokensAnnotation.class are
   * initialized with lists of CoreMap and CoreLabel objects, respectively.
   */
  private Annotation getSeededDocumentAnnotation() {
    Annotation documentAnnotation = new Annotation("");
    documentAnnotation.set(SentencesAnnotation.class, new ArrayList<CoreMap>());
    documentAnnotation.set(TokensAnnotation.class, new ArrayList<CoreLabel>());
    return documentAnnotation;
  }

  /**
   * This steps through the given communication. Each section is first locally
   * processed (i.e., all but coref resolution). Once all sections have been
   * locally processed, global processing is done on the entire communication
   * (i.e., coref).
   *
   * It assumes that {@code comm} both passes
   * PrereqValidator.verifyCommunication and has been constructed via a new
   * perspective. In particular, rawTextSpans must be set.
   *
   * @throws AnalyticException
   */
  private void annotateSects(Communication comm) throws AnalyticException {
    // if called multiple times, reset the sentence count
    sentenceCount = 1;
    String commText = comm.getOriginalText();
    StringBuilder sb = new StringBuilder();
    logger.debug("Annotating communication: {}", comm.getId());
    logger.debug("\tuuid = {}", comm.getUuid());
    logger.debug("\ttype = {}", comm.getType());
    logger.debug("\tfull = {}", commText);

    List<Tokenization> tokenizations = new ArrayList<>();
    List<Section> sections = comm.getSectionList();
    Annotation documentAnnotation = getSeededDocumentAnnotation();

    int previousSectionEnding = 0;
    for (Section section : sections) {
      if (!section.isSetRawTextSpan()) {
        throw new AnalyticException("Cannot process section "
            + section.getUuid() + ", as it has no .rawTextSpan");
      }

      int interSectionWhitespaceDifference = section.getRawTextSpan()
        .getStart() - previousSectionEnding;
      charOffset += interSectionWhitespaceDifference;

      int sectionStartCharOffset = processedCharOffset;
      logger.debug("new section, processed offset = {}", sectionStartCharOffset);
      TextSpan sts = section.getRawTextSpan();
      // 1) First *perform* the tokenization & sentence splits
      // Note we do this first, even before checking the content-type
      String sectionText = commText.substring(sts.getStart(), sts.getEnding());
      Annotation sectionAnnotation = pipeline.splitAndTokenizeSection(section,
          sectionText);
      dispatchSection(section, sectionText, sectionAnnotation,
          documentAnnotation, sectionStartCharOffset, tokenizations, sb);

      // between sections are two line feeds
      // one is counted for in the sentTokens loop above
      processedCharOffset++;
      if (section.isSetRawTextSpan()) {
        previousSectionEnding = section.getRawTextSpan().getEnding();
      }
    }

    comm.setText(sb.toString());

    // 3) Third, do coref; cross-reference against sectionUUIDs
    logger.debug("Running coref.");
    processCoref(comm, documentAnnotation, tokenizations);
  }

  private void dispatchSection(Section section, String sectionText,
      Annotation sectionAnnotation, Annotation documentAnnotation,
      int sectionStartCharOffset, List<Tokenization> tokenizations,
      StringBuilder sb) throws AnalyticException {
    logger.debug("Annotating Section: {}", section.getUuid());
    logger.debug("\ttext = {}", sectionText);
    logger.debug("\tkind = {} in annotateNames: {}", section.getKind(), this.kindsToProcessSet);
    boolean allButCoref = kindsForNoCoref.contains(section.getKind());
    boolean allWithCoref = kindsToProcessSet.contains(section.getKind());
    if (!allWithCoref && !allButCoref) {
      basicProcessingOnly(section, sectionAnnotation, sectionStartCharOffset, sb);
    } else if (allButCoref) {
      // Only tokenize & sentence split
      logger.debug("Special handling for section type {} section: {}",
          section.getKind(), section.getUuid());
      logger.debug(">> SectionText={}", sectionText);
      processSectionForNoCoref(section, sectionAnnotation,
          sectionStartCharOffset, sb);
    } else {
      // 2) Second, perform the other localized processing
      logger.debug("Additional processing on section: {}", section.getUuid());
      logger.debug(">> SectionText={}", sectionText);
      processSection(section, sectionAnnotation, documentAnnotation,
          sectionStartCharOffset, sb);
      addTokenizations(section, tokenizations);
    }
  }

  /**
   * This method handles sections that we are explicitly <b>not</b> annotating
   * beyond tokenizing. The code updates the character offsets (global state)
   *
   * @param sectionAnnotation
   * @param sectionText
   */
  private void basicProcessingOnly(Section section, Annotation sentenceSplitText, int sectionOffset, StringBuilder sb) throws AnalyticException {
    logger.debug("tokenize/sent-split ONLY section: from {} ", sectionOffset);
    if (sentenceSplitText == null) {
      logger.debug("{}", sectionOffset);
      return;
    }

    // We need to update the global character offset...
    sentencesToSection(section, sentenceSplitText);
    ConcreteAnnotator addToConcrete = this.getFreshConcreteAnnotator(new String[]{});
    addToConcrete.augmentSectionAnnotations(section, sentenceSplitText,
        sectionOffset, sb);
    setSectionTextSpan(section, sectionOffset, processedCharOffset, true);
    logger.debug("{}", charOffset);
  }

  private void addTokenizations(Section section,
      List<Tokenization> tokenizations) {
    if (!section.isSetSentenceList()) {
      logger.warn("Section {} has no sentence list set");
      return;
    }
    for (Sentence sentence : section.getSentenceList()) {
      if (sentence.isSetTokenization())
        tokenizations.add(sentence.getTokenization());
      else {
        Tokenization tkz = TokenizationFactory.create();
        tkz.setKind(TokenizationKind.TOKEN_LIST);
        TokenList tl = new TokenList();
        tl.setTokenList(new ArrayList<Token>());
        tkz.setTokenList(tl);
        tkz.setDependencyParseList(new ArrayList<>());
        tkz.setTokenTaggingList(new ArrayList<>());
        tkz.setParseList(new ArrayList<>());
      }
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
   * Note that coreference resolution is done only once all contentful sections
   * have been properly annotated.
   *
   * @throws AnalyticException
   *
   */
  private boolean annotateLocalStages(Annotation annotation)
      throws AnalyticException {
    try {
      return pipeline.annotateLocalStages(annotation);
    } catch (IOException e) {
      throw new AnalyticException(e);
    }
  }

  private boolean annotateCoref(Annotation annotation)
      throws AnalyticException {
    try {
      return pipeline.annotateCoref(annotation);
    } catch (IOException e) {
      throw new AnalyticException(e);
    }
  }

  /**
   * Convert tokenized sentences (<code>sentAnno</code>) into a document
   * Annotation.<br/>
   * The global indexers {@code charOffset} and {@code globalTokenOffset} are
   * updated here.
   *
   */
  private void aggregateTokenizedSentences(Section concreteSection,
      CoreMap sectAnno, Annotation document) throws AnalyticException {
    if (sectAnno == null) {
      logger.warn("Encountered null annotated section. Skipping.");
      return;
    }

    List<CoreMap> docSents = document.get(SentencesAnnotation.class);
    List<CoreLabel> docTokens = document.get(TokensAnnotation.class);
    logger.debug("converting list of CoreMap sentences to Annotations, starting at token offset: {}", globalTokenOffset);

    List<CoreMap> sentAnnos = sectAnno.get(SentencesAnnotation.class);
    int maxCharEnding = -1;
    boolean isFirst = true;
    int sentenceIdx = 0;
    List<Sentence> concreteSentences = null;
    int numConcreteSentences = 0;
    boolean concreteSentencesSet = false;
    if (concreteSection.isSetSentenceList()) {
      concreteSentencesSet = true;
      concreteSentences = concreteSection.getSentenceList();
      numConcreteSentences = concreteSentences.size();
      if (numConcreteSentences != sentAnnos.size()) {
        throw new AnalyticException("Section " + concreteSection.getUuid()
            + " has " + numConcreteSentences + " sentences already created,"
            + " but CoreNLP only found " + sentAnnos.size());
      }
    }
    for (CoreMap sentAnno : sentAnnos) {
      List<CoreLabel> sentTokens = sentAnno.get(TokensAnnotation.class);
      int tokenEnd = globalTokenOffset + sentTokens.size();
      sentAnno.set(TokenBeginAnnotation.class, globalTokenOffset);
      sentAnno.set(TokenEndAnnotation.class, tokenEnd);
      sentAnno.set(SentenceIndexAnnotation.class, sentenceCount++);
      logger.debug("SENTENCEINDEXANNO = {}", sentAnno.get(SentenceIndexAnnotation.class));
      globalTokenOffset = tokenEnd;

      for (CoreLabel token : sentTokens) {
        // note that character offsets are global
        updateCharOffsetSetToken(token, isFirst, true);
        logger.debug("this token goes from {} to {}", token.get(CharacterOffsetBeginAnnotation.class),
            token.get(CharacterOffsetEndAnnotation.class));
        logger.debug("\toriginal:[[{}]]" , token.originalText());
        logger.debug("\tbefore:<<{}>>", token.before());
        logger.debug("\tafter:<<{}>>", token.after());
        if (isFirst)
          isFirst = false;

      }
      // if there are > 1 sentence, then we need to account for any space in
      // between the Concrete sentences
      if (concreteSentencesSet) {
        if (sentenceIdx + 1 < numConcreteSentences) {
          Sentence concreteSentence = concreteSentences.get(sentenceIdx++);
          Sentence nextConcreteSentence = concreteSentences.get(sentenceIdx);
          int interSentenceWhitespaceDifference = nextConcreteSentence
              .getRawTextSpan().getStart()
              - concreteSentence.getRawTextSpan().getEnding();
          charOffset += interSentenceWhitespaceDifference;
        }
      }
      sentAnno.set(TokensAnnotation.class, sentTokens);
      sentAnno.set(CharacterOffsetBeginAnnotation.class,
          sentTokens.get(0).get(CharacterOffsetBeginAnnotation.class));
      int endingSentCOff = sentTokens.get(sentTokens.size() - 1).get(
          CharacterOffsetEndAnnotation.class);
      sentAnno.set(CharacterOffsetEndAnnotation.class, endingSentCOff);

      logger.debug("docTokens.size before = {}", docTokens.size());
      docTokens.addAll(sentTokens);
      logger.debug("\t after = {}", docTokens.size());
      document.set(TokensAnnotation.class, docTokens);
      logger.debug("\t retrieved = {}", document.get(TokensAnnotation.class).size());
      docSents.add(sentAnno);
      if (sentAnno.get(CharacterOffsetEndAnnotation.class) > maxCharEnding)
        maxCharEnding = sentAnno.get(CharacterOffsetEndAnnotation.class);

    }
    document.set(SentencesAnnotation.class, docSents);
    Integer oldDocCharE = document.get(CharacterOffsetEndAnnotation.class);
    if (oldDocCharE != null && maxCharEnding < oldDocCharE)
      throw new AnalyticException("The max char ending for this section ("
          + maxCharEnding
          + ") is less than the current document char ending ( " + oldDocCharE
          + ")");
    document.set(CharacterOffsetEndAnnotation.class, maxCharEnding);
  }

  /**
   * Update character offsets for (<code>sentAnno</code>).<br/>
   * The global indexers {@code charOffset} and {@code globalTokenOffset} are
   * updated here.
   *
   */
  private void sentencesToSection(Section concreteSection, CoreMap sectAnno)
      throws AnalyticException {
    if (sectAnno == null) {
      logger.warn("Encountered null annotated section. Skipping.");
      return;
    }

    logger.debug("converting list of CoreMap sentences to Annotations, starting at token offset {}", globalTokenOffset);

    List<CoreMap> sentAnnos = sectAnno.get(SentencesAnnotation.class);
    int maxCharEnding = -1;
    boolean isFirst = true;
    int sentenceIdx = 0;
    List<Sentence> concreteSentences = null;
    int numConcreteSentences = 0;
    boolean concreteSentencesSet = false;
    if (concreteSection.isSetSentenceList()) {
      concreteSentencesSet = true;
      concreteSentences = concreteSection.getSentenceList();
      numConcreteSentences = concreteSentences.size();
      if (concreteSection.getSentenceList().size() != sentAnnos.size()) {
        throw new AnalyticException("Section " + concreteSection.getUuid()
            + " has " + concreteSection.getSentenceList().size()
            + " sentences already created," + " but CoreNLP only found "
            + sentAnnos.size());
      }
    }
    for (CoreMap sentAnno : sentAnnos) {
      List<CoreLabel> sentTokens = sentAnno.get(TokensAnnotation.class);
      int tokenEnd = globalTokenOffset + sentTokens.size();
      sentAnno.set(TokenBeginAnnotation.class, globalTokenOffset);
      sentAnno.set(TokenEndAnnotation.class, tokenEnd);
      // sentAnno.set(SentenceIndexAnnotation.class, sentenceCount++);
      logger.debug("SENTENCEINDEXANNO = {}", sentAnno.get(SentenceIndexAnnotation.class));
      globalTokenOffset = tokenEnd;

      for (CoreLabel token : sentTokens) {
        // note that character offsets are global
        // String tokenText = token.get(TextAnnotation.class);
        updateCharOffsetSetToken(token, isFirst, true);
        logger.debug("this token goes from {} to {}",
            token.get(CharacterOffsetBeginAnnotation.class),
            + token.get(CharacterOffsetEndAnnotation.class));
        logger.debug("\toriginal:[[{}]]", token.originalText());
        logger.debug("\tbefore:<<{}>>", token.before());
        logger.debug("\tafter:<<{}>>" + token.after());
        if (isFirst)
          isFirst = false;

      }
      // if there are > 1 sentence, then we need to account for any space in
      // between the Concrete sentences
      if (concreteSentencesSet) {
        if (sentenceIdx + 1 < numConcreteSentences) {
          Sentence concreteSentence = concreteSentences.get(sentenceIdx++);
          Sentence nextConcreteSentence = concreteSentences.get(sentenceIdx);
          int interSentenceWhitespaceDifference = nextConcreteSentence
              .getRawTextSpan().getStart()
              - concreteSentence.getRawTextSpan().getEnding();
          charOffset += interSentenceWhitespaceDifference;
        }
      }
      sentAnno.set(TokensAnnotation.class, sentTokens);
      sentAnno.set(CharacterOffsetBeginAnnotation.class,
          sentTokens.get(0).get(CharacterOffsetBeginAnnotation.class));
      int endingSentCOff = sentTokens.get(sentTokens.size() - 1).get(
          CharacterOffsetEndAnnotation.class);
      sentAnno.set(CharacterOffsetEndAnnotation.class, endingSentCOff);

      if (sentAnno.get(CharacterOffsetEndAnnotation.class) > maxCharEnding)
        maxCharEnding = sentAnno.get(CharacterOffsetEndAnnotation.class);
    }
  }

  /**
   * This updates the global character offset counter {@code charOffset}. It
   * also changes the {@code token} character offsets to represent the
   * <i>original</i> textspans. Optionally, the processed character offset will
   * be updated too.
   *
   * @param token
   * @param isFirst
   * @param updateProcessedOff
   */
  private void updateCharOffsetSetToken(CoreLabel token, boolean isFirst,
      boolean updateProcessedOff) {
    if (isFirst) {
      // this is because when we have text like "foo bar", foo.after == " "
      // AND bar.before == " "
      int beforeLength = token.before().length();
      charOffset += beforeLength;
    }

    final String before = token.before();
    final String after = token.after();
    logger.debug("[{}, {}] [{}] [{}, {}] :: {} --> {}", before, before.length(), token.originalText(),
        after, after.length(), charOffset, charOffset + token.originalText().length());
    token.set(CharacterOffsetBeginAnnotation.class, charOffset);
    charOffset += token.originalText().length();
    token.set(CharacterOffsetEndAnnotation.class, charOffset);
    charOffset += token.after().length();

    if (updateProcessedOff) {
      processedCharOffset += token.get(TextAnnotation.class).length() + 1;
    }
  }

  /**
   * Transfer an individual section's annotations to the global accumulating
   * document. This allows global annotators (coref) to use local information,
   * such as parses.
   */
  private void transferAnnotations(Annotation section, Annotation document) {
    List<CoreMap> sectionSents = section.get(SentencesAnnotation.class);
    ArrayList<CoreMap> documentSents = (ArrayList<CoreMap>) document
        .get(SentencesAnnotation.class);
    logger.debug("\t******Document sents*********");
    for (CoreMap sectSent : sectionSents) {
      int idx = sectSent.get(SentenceIndexAnnotation.class) - 1;
      logger.debug("My index is {} ({}), and can access up to {} sentences globally", idx,
          sectSent.get(SentenceIndexAnnotation.class), documentSents.size());
      if (sectSent.containsKey(TreeAnnotation.class)) {
        CoreMap dSent = documentSents.get(idx);
        dSent.set(TreeAnnotation.class, sectSent.get(TreeAnnotation.class));
        logger.debug(dSent.get(TreeAnnotation.class).toString());
        logger.debug(dSent.get(TreeAnnotation.class).getLeaves().toString());
        logger.debug(sectSent.get(TokensAnnotation.class).toString());
        logger.debug("{} --> {}", idx, dSent.get(TokensAnnotation.class));
      }
    }
  }

  /**
   * Given a particular section {@link Section} from a {@link Communication},
   * further locally process {@link Annotation}; add those new annotations to an
   * aggregating {@link Annotation} to use for later global processing.
   *
   * @throws AnalyticException
   */
  private void processSectionForNoCoref(Section section,
      Annotation sentenceSplitText, int sectionOffset, StringBuilder sb)
      throws AnalyticException {
    sentencesToSection(section, sentenceSplitText);

    boolean successfulAnnotation = annotateLocalStages(sentenceSplitText);
    logger.debug("after annotating, annotation was successful? ({})",
        successfulAnnotation);

    ConcreteAnnotator addToConcrete = this.getFreshConcreteAnnotator();
    addToConcrete.augmentSectionAnnotations(section, sentenceSplitText,
        sectionOffset, sb);
    setSectionTextSpan(section, sectionOffset, processedCharOffset, true);
  }

  private void logDebugSentencesAnnotation(Annotation anno, String message) {
    logger.debug(message);
    for (CoreMap cm : anno.get(SentencesAnnotation.class))
      logger.debug(cm.get(SentenceIndexAnnotation.class).toString());
  }

  /**
   * Given a particular section {@link Section} from a {@link Communication},
   * further locally process {@link Annotation}; add those new annotations to an
   * aggregating {@link Annotation} to use for later global processing.
   *
   * @throws AnalyticException
   */
  private void processSection(Section section, Annotation sentenceSplitText,
      Annotation docAnnotation, int sectionOffset, StringBuilder sb)
      throws AnalyticException {
    aggregateTokenizedSentences(section, sentenceSplitText, docAnnotation);
    logDebugSentencesAnnotation(sentenceSplitText,
        "after sentencesToSection, before annotating");
    boolean successfulAnnotation = annotateLocalStages(sentenceSplitText);
    logger.debug("after annotating, annotation was successful? ({})",
        successfulAnnotation);
    logDebugSentencesAnnotation(sentenceSplitText, "after annotating");
    transferAnnotations(sentenceSplitText, docAnnotation);
    ConcreteAnnotator agigaToConcrete = this.getFreshConcreteAnnotator();
    agigaToConcrete.augmentSectionAnnotations(section, sentenceSplitText,
        sectionOffset, sb);
    setSectionTextSpan(section, sectionOffset, processedCharOffset, true);
  }

  private static void setSectionTextSpan(Section section, int start, int end,
      boolean compensate) throws AnalyticException {
    if (!section.isSetTextSpan()) {
      int compE = compensate ? (end - 1) : end;
      if (compE <= start)
        throw new AnalyticException(
            "Cannot create compensated textspan for section "
                + section.getUuid() + "; provided offsets = (" + start + ","
                + end + "), compensated offsets = (" + start + "," + compE
                + ")");

      TextSpan txs = new TextSpan().setStart(start).setEnding(compE);
      section.setTextSpan(txs);
    }
  }

  private void processCoref(Communication comm, Annotation docAnnotation,
      List<Tokenization> tokenizations) throws AnalyticException {
    boolean successfulAnnotation = annotateCoref(docAnnotation);
    logger.debug("after annotating, annotation was successful? ({})",
        successfulAnnotation);
    ConcreteAnnotator agigaToConcrete = this.getFreshConcreteAnnotator();
    SimpleEntry<EntityMentionSet, EntitySet> tuple = agigaToConcrete
        .convertCoref(comm, docAnnotation, tokenizations);
    EntityMentionSet ems = tuple.getKey();
    EntitySet es = tuple.getValue();

    if (ems == null || !ems.isSetMentionList())
      throw new AnalyticException(
          "Concrete-agiga produced a null EntityMentionSet, or a null mentionList.");

    if (ems.getMentionListSize() == 0
        && !this.allowEmptyEntitiesAndEntityMentions)
      throw new AnalyticException(
          "Empty entity mentions are disallowed and no entity mentions were produced for communication: "
              + comm.getId());
    else
      comm.addToEntityMentionSetList(ems);

    if (es == null || !es.isSetEntityList())
      throw new AnalyticException(
          "Concrete-agiga produced a null EntitySet, or a null entityList.");

    if (es.getEntityListSize() == 0
        && !this.allowEmptyEntitiesAndEntityMentions)
      throw new AnalyticException(
          "Empty entities are disallowed and no entities were produced for communication: "
              + comm.getId());
    else
      comm.addToEntitySetList(es);
  }

  private ConcreteAnnotator getFreshConcreteAnnotator() {
    return new ConcreteAnnotator(this.language);
  }
  private ConcreteAnnotator getFreshConcreteAnnotator(String[] annotators) {
    return new ConcreteAnnotator(this.language, annotators);
  }

  public Set<String> getSectionTypesToAnnotate() {
    return new HashSet<>(this.kindsToProcessSet);
  }

  /**
   * convert a tree t to its token representation
   *
   * @param t
   * @return
   */
  @Deprecated
  protected String getText(Tree t) {
    if (t == null)
      return null;

    StringBuilder sb = new StringBuilder();

    for (Tree tt : t.getLeaves()) {
      sb.append(tt.value());
      sb.append(" ");
    }
    return sb.toString().trim();
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
     *         <li>It may or may not have Sentences. However, if it does, the
     *         sentence list must not be empty, and all sentences but be valid,
     *         as given by {@code verifySentence}.</li>
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
     *         <li>It must not have a .tokenization field.</li>
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
  public StanfordPostNERCommunication annotate(Communication arg0) throws AnalyticException {
    try {
      return this.annotate(new CachedSectionedCommunication(arg0));
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
   * @param comm
   *          a {@link Communication} with {@link Section}s, but whose sections do not contain {@link Sentence}s
   * @return a {@link Communication} with stanford annotations
   * @throws AnalyticException
   *           on analytic error
   */
  public StanfordPostNERCommunication annotate(NonSentencedSectionedCommunication comm) throws AnalyticException {
    return this.annotate(comm);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.analytics.base.SectionedCommunicationAnalytic#annotate(edu.jhu.hlt.concrete.miscommunication.sectioned.SectionedCommunication)
   */
  @Override
  public StanfordPostNERCommunication annotate(SectionedCommunication arg0) throws AnalyticException {
    try {
      PerspectiveCommunication pc = new PerspectiveCommunication(arg0.getRoot(),
          "PerspectiveCreator");
      Communication persp = pc.getPerspective();

      // hopefully MD is never null
      // The Optional.ofNullable can be removed due to the validator object,
      // but keeping it around won't hurt.
      AnnotationMetadata md = Optional.ofNullable(persp.getMetadata()).orElse(
          new AnnotationMetadata());
      String csToolName = ProjectConstants.PROJECT_NAME + " "
          + ProjectConstants.VERSION;
      String newToolName = csToolName + " perspective";

      String mdToolName = md.isSetTool() ? md.getTool() : "";
      if (!mdToolName.isEmpty())
        newToolName += " on old tool: " + mdToolName;

      md.setTool(newToolName);
      persp.setMetadata(md);
      resetGlobals();
      this.annotateSects(persp);
      StanfordPostNERCommunication post = new StanfordPostNERCommunication(persp);
      return post;
    } catch (ConcreteException | MiscommunicationException e) {
      throw new AnalyticException(e);
    }
  }
}
