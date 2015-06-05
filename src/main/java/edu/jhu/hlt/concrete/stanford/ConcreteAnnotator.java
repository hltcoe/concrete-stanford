/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.Parse;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TaggedToken;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.TheoryDependencies;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.TokenRefSequence;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.UUID;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.util.Timing;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;
import edu.jhu.hlt.concrete.validation.ValidatableTextSpan;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;

/**
 * Given a Communication (with Sections and Sentences added) and Stanford's
 * annotations via CoreNLP objects, add these annotations to existing Concrete
 * structures.
 */
class ConcreteAnnotator {

  private static final Logger logger = LoggerFactory
      .getLogger(ConcreteAnnotator.class);

  public static final String toolName = "Concrete-Stanford Pipeline";
  public static final String corpusName = "";

  private final HeadFinder HEAD_FINDER;

  public HeadFinder getHeadFinder() {
    return this.HEAD_FINDER;
  }

  private final String languageString;

  private final ConcreteStanfordProperties csProps;

  public ConcreteStanfordProperties getProps() {
    return this.csProps;
  }
  // private final boolean allowEmptyMentions;

  private final String[] annotatorList;

  public ConcreteAnnotator(PipelineLanguage language) {
    this(language, ConcreteAnnotator.getDefaultAnnotators());
  }

  public ConcreteAnnotator(PipelineLanguage language, String[] annotators) {
    this.csProps = new ConcreteStanfordProperties();
    // this.allowEmptyMentions = this.csProps.getAllowEmptyMentions();
    this.annotatorList = annotators;
    this.HEAD_FINDER = language.getHeadFinder();
    this.languageString = language.toString();
  }

  AnnotationMetadata getMetadata(String addToToolName) {
    String fullToolName = toolName;
    if (addToToolName != null)
      fullToolName += " " + addToToolName;

    AnnotationMetadata md = new AnnotationMetadata();
    md.setTool(fullToolName);
    md.setTimestamp(Timing.currentLocalTime());
    return md;
  }

  public static String flattenText(CoreMap coreNlpSentence)
      throws AnalyticException {
    StringBuilder sb = new StringBuilder();
    List<CoreLabel> tokens = coreNlpSentence
        .get(CoreAnnotations.TokensAnnotation.class);
    if (tokens == null) {
      throw new AnalyticException(
          "Cannot find tokens in the provided CoreMap");
    }
    for (CoreLabel token : tokens) {
      String word = token.get(CoreAnnotations.TextAnnotation.class);
      if (word == null) {
        throw new AnalyticException("found a null word");
      }
      sb.append(word);
      sb.append(" ");
    }
    return sb.toString().trim();
  }

  SimpleEntry<EntityMentionSet, EntitySet> convertCoref(Communication in,
      Annotation coreNlpDoc, List<Tokenization> tokenizations)
      throws AnalyticException {
    List<CoreMap> coreSentences = coreNlpDoc
        .get(CoreAnnotations.SentencesAnnotation.class);
    if (coreSentences == null) {
      throw new AnalyticException("Communication " + in.getId()
          + " has a null list of CoreNLP sentences");
    }
    if (coreSentences.size() != tokenizations.size()) {
      throw new AnalyticException("Communication " + in.getId()
          + " knows of " + tokenizations.size()
          + " valid tokenizations, but CoreNLP reports having "
          + coreSentences.size() + " sentences. These values must agree.");
    }
    if (tokenizations.size() == 0) {
      logger.warn("Communication {} has no valid tokenizations.", in.getId());
    }
    EntityMentionSet ems = new EntityMentionSet()
        .setUuid(UUIDFactory.newUUID());
    TheoryDependencies td = new TheoryDependencies();
    for (Tokenization t : tokenizations)
      td.addToTokenizationTheoryList(t.getUuid());
    AnnotationMetadata md = this.getMetadata(this.csProps.getCorefToolName())
        .setDependencies(td);
    ems.setMetadata(md);

    Map<Integer, CorefChain> coreNlpChains = coreNlpDoc
        .get(CorefCoreAnnotations.CorefChainAnnotation.class);
    if (coreNlpChains == null || coreNlpChains.size() == 0) {
      logger.warn("There were no coref chains found");
    }
    EntitySet es = new EntitySet().setUuid(UUIDFactory.newUUID())
        .setMetadata(md).setEntityList(new ArrayList<Entity>());
    if (coreNlpChains != null) {
      ConcreteCreator cc = new ConcreteCreator(this);
      for (CorefChain chain : coreNlpChains.values()) {
        Entity entity = cc.makeEntity(chain, ems, tokenizations);
        es.addToEntityList(entity);
      }
    }

    if (!ems.isSetMentionList())
      ems.setMentionList(new ArrayList<EntityMention>());

    return new SimpleEntry<EntityMentionSet, EntitySet>(ems, es);
  }

  static Tokenization getTokenizationSafe(List<Tokenization> tokenizations, int idx)
      throws AnalyticException {
    if (idx >= tokenizations.size()) {
      throw new AnalyticException("the sentence number of the mention ("
          + idx + ") is out of range of the known sentences (of size "
          + tokenizations.size() + ")");
    }
    return tokenizations.get(idx);
  }

  /**
   * In order to allow for possibly empty mentions, this will always return a
   * validating TokenRefSequence, provided m.end &gt;= m.start. When the end
   * points are equal, the token index list will be the empty list, and a
   * warning will be logged.
   *
   * @throws AnalyticException
   */
  public static TokenRefSequence extractTokenRefSequence(CorefMention coreMention,
      UUID tokUuid, boolean representative) throws AnalyticException {
    int start = ConcreteAnnotator.coreMentionTokenAsIndex(coreMention.startIndex);
    int end   = ConcreteAnnotator.coreMentionTokenAsIndex(coreMention.endIndex);
    logger.debug("Working on mention string: {}", coreMention.mentionSpan);
    int head = ConcreteAnnotator.coreMentionTokenAsIndex(coreMention.headIndex);
    if (end - start < 0) {
      throw new AnalyticException(
          "Calling extractTokenRefSequence on mention " + coreMention
              + " with head = " + head + ", UUID = " + tokUuid);
    } else if (end == start) {
      TokenRefSequence tb = new TokenRefSequence();
      tb.setTokenizationId(tokUuid).setTokenIndexList(new ArrayList<Integer>());
      if (representative) {
        tb.setAnchorTokenIndex(head);
      }
      logger.warn("Creating an EMPTY mention for mention {}, UUID {}", coreMention, tokUuid);
      return tb;
    }
    return extractTokenRefSequence(start, end, head, tokUuid);
  }

  /**
   *
   * @return
   */
  static String[] getDefaultAnnotators() {
    String[] annotators = { "POS", "NER", "LEMMA", "CPARSE", "DPARSE" };
    return annotators;
  }

  /**
   *
   * @param section
   * @param coreNlpSection
   * @param procCharOffset
   *          The current processed offset for the Section. The original offsets
   *          are stored in the {@code Annotation} object.
   * @param sb
   *          An aggregator to store the document text.
   * @param annotationList
   * @throws AnalyticException
   */
  public void augmentSectionAnnotations(Section section,
      Annotation coreNlpSection, int procCharOffset, StringBuilder sb)
      throws AnalyticException {
    if (section.isSetSentenceList()) {
      this.augmentSentences(section, coreNlpSection, procCharOffset, sb);
    } else {
      ConcreteCreator cc = new ConcreteCreator(this);
      cc.makeSentences(section, coreNlpSection, procCharOffset, sb);
    }
    sb.append("\n\n");
  }

  /**
   * Augment an existing {@link Tokenization} based on the given sentence. The
   * {@link Tokenization} must have a populated {@link TokenList}.
   *
   * @throws AnalyticException
   */
  public void augmentTokenization(Tokenization tokenization, CoreMap sent,
      ConcreteCreator cc, int charOffset) throws AnalyticException {
    if (charOffset < 0) {
      throw new AnalyticException(
          "The provided character offset cannot be < 0");
    }
    UUID tUuid = tokenization.getUuid();
    List<TokenTagging> tokenTaggingLists = new ArrayList<>();
    List<Class<? extends TypesafeMap.Key<String>>> annotClasses = new ArrayList<>();
    for (String annotation : this.annotatorList) {
      switch (annotation.toLowerCase()) {
      case "pos":
        TokenTagging pos = new TokenTagging().setUuid(UUIDFactory.newUUID())
            .setMetadata(this.getPOSMetadata(tUuid)).setTaggingType("POS");
        tokenTaggingLists.add(pos);
        annotClasses.add(CoreAnnotations.PartOfSpeechAnnotation.class);
        break;
      case "ner":
        TokenTagging ner = new TokenTagging().setUuid(UUIDFactory.newUUID())
            .setMetadata(this.getNERMetadata(tUuid)).setTaggingType("NER");
        tokenTaggingLists.add(ner);
        annotClasses.add(CoreAnnotations.NamedEntityTagAnnotation.class);
        break;
      case "lemma":
        TokenTagging lemma = new TokenTagging().setUuid(UUIDFactory.newUUID())
            .setMetadata(this.getLemmaMetadata(tUuid)).setTaggingType("LEMMA");
        tokenTaggingLists.add(lemma);
        annotClasses.add(CoreAnnotations.LemmaAnnotation.class);
        break;
      case "cparse":
        Tree tree = sent.get(TreeCoreAnnotations.TreeAnnotation.class);
        if (tree != null) {
          List<Parse> parseList = new ArrayList<Parse>();
          parseList.add(cc.makeConcreteCParse(tree,
              verifyNonNull(tokenization.getTokenList()).getTokenListSize(),
              tUuid));
          tokenization.setParseList(parseList);
        } else {
          logger.warn("Tokenization {} has an empty constituency parse",
              tokenization.getUuid());
        }
        break;
      case "dparse":
        List<DependencyParse> dependencyParses = cc.constructDependencyParses(
            sent, tUuid);
        tokenization.setDependencyParseList(dependencyParses);
        break;
      default:
        break;
      }
    }
    List<CoreLabel> tokens = verifyNonNull(sent
        .get(CoreAnnotations.TokensAnnotation.class));
    final int numTaggings = annotClasses.size();
    int tokId = 0;
    for (CoreLabel token : tokens) {
      for (int i = 0; i < numTaggings; ++i) {
        String tag = token.get(annotClasses.get(i));
        if (tag != null) {
          TaggedToken tagTok = cc.makeTaggedToken(tag, tokId);
          tokenTaggingLists.get(i).addToTaggedTokenList(tagTok);
        }
      }
      ++tokId;
    }

    for (int i = 0; i < numTaggings; ++i) {
      tokenization.addToTokenTaggingList(tokenTaggingLists.get(i));
      if (tokenTaggingLists.get(i).getTaggedTokenListSize() != tokId) {
        logger.warn("In Tokenization {}, TokenTagging {} has {} tagged tokens, but there are {} recorded tokens.", tokenization.getUuid(),
            tokenTaggingLists.get(i).getTaggingType(),
            tokenTaggingLists.get(i).getTaggedTokenListSize(),
            tokId);
      }
    }
  }

  /**
   * Augment an existing Tokenization based on the given sentence.
   *
   * @throws AnalyticException
   */
  public void augmentTokenization(Tokenization tokenization, CoreMap sent,
      int charOffset) throws AnalyticException {
    ConcreteCreator cc = new ConcreteCreator(this);
    this.augmentTokenization(tokenization, sent, cc, charOffset);
  }

  /**
   * This assumes that {@code concSect} has a SentenceList already initialized
   * and that valid {@code Concrete} {@link Sentence}s populate it. The
   * sentences may or may not have {@link Tokenization}s, but it must be
   * all-or-nothing: {@code concSect} cannot have some sentences with
   * Tokenizations and other sentences without Tokenizations.
   *
   * @param concSect
   * @param coreNlpSection
   * @param procCurrOffset
   *          The current processed offset for the Section. The original offsets
   *          are stored in the {@code Annotation} object.
   * @param sb
   *          An aggregator to store the document text.
   * @throws AnalyticException
   */
  public void augmentSentences(Section concSect, Annotation coreNlpSection,
      int procCurrOffset, StringBuilder sb) throws AnalyticException {
    logger.debug("Section has : {} sentences", concSect.getSentenceList().size());
    logger.debug("convertSentences for {}", concSect.getUuid());

    List<CoreMap> sentAnnos = coreNlpSection.get(SentencesAnnotation.class);
    if (sentAnnos == null) {
      throw new AnalyticException("Section " + concSect.getUuid()
          + " has a null CoreNLP sentences annotation");
    }
    final int n = sentAnnos.size();
    if (n != concSect.getSentenceList().size()) {
      throw new AnalyticException("Section " + concSect.getUuid() + " has "
          + concSect.getSentenceList().size() + " but corenlp has " + n);
    }
    logger.debug("Adding {} sentences to section {}", n, concSect.getUuid());
    if (n <= 0)
      throw new IllegalArgumentException("The number of sentences was <= 0.");
    // assert n > 0 : "n=" + n;
    List<Sentence> concreteSentences = concSect.getSentenceList();
    ConcreteCreator cc = new ConcreteCreator(this);
    int whichBranch = 0, priorBranch = 0;
    for (int i = 0; i < n; i++) {
      Sentence concSent = concreteSentences.get(i);
      CoreMap coreSent = sentAnnos.get(i);
      String sentText = ConcreteAnnotator.flattenText(coreSent);
      Tokenization tokenization;
      if (concSent.isSetTokenization()) {
        tokenization = concSent.getTokenization();
        if (tokenization == null || !tokenization.isSetTokenList()
            || tokenization.getTokenList() == null) {
          throw new AnalyticException("Sentence " + concSent.getUuid()
              + " does not have a valid tokenization or iterable token list");
        }
        this.augmentTokenization(tokenization, coreSent, cc, procCurrOffset);
        whichBranch = 1;
        if (procCurrOffset != concSent.getTextSpan().getStart()
            && (procCurrOffset + sentText.length()) != concSent.getTextSpan()
                .getEnding()) {
          throw new AnalyticException(
              "Sentence "
                  + concSent.getUuid()
                  + " already has tokens set, but its start/end values ( "
                  + concSent.getTextSpan()
                  + ") do not agree with the passed-in value of the processed offset ("
                  + procCurrOffset + ") and the computed sentence length ("
                  + sentText.length() + ")");
        }
      } else {
        tokenization = cc.makeTokenization(coreSent, procCurrOffset);
        concSent.setTokenization(tokenization);
        whichBranch = -1;
        // we store the original offsets in the AgigaToken char begin/end
        // values.
        // This means that we need to compute the actual offsets on-the-fly.
        TextSpan sentTS = makeSafeSpan(procCurrOffset, procCurrOffset
            + sentText.length());
        concSent.setTextSpan(sentTS);
      }
      // now check to make sure that we're not switching branches.
      if (priorBranch == 0) {
        priorBranch = whichBranch;
      } else {
        if (priorBranch != whichBranch) {
          throw new AnalyticException(
              "Section "
                  + concSect.getUuid()
                  + " has some sentences with Tokenizations set, and others without Tokenizations set");
        }
      }

      if (procCurrOffset < 0) {
        throw new AnalyticException("bad character offset of "
            + procCurrOffset + " for converting sent " + concSent.getUuid());
      }

      List<Token> tokenList = tokenization.getTokenList().getTokenList();
      int numTokens = tokenList.size();
      if (!concSent.isSetRawTextSpan()) {
        logger
            .warn("Concrete sentence {} does not have raw text spans set. We can compute these on the fly, but something might be wrong.", concSent.getUuid());
        Token firstToken = tokenList.get(0);
        Token lastToken = tokenList.get(numTokens - 1);
        TextSpan rawTS = makeSafeSpan(firstToken.getRawTextSpan().getStart(),
            lastToken.getRawTextSpan().getEnding());
        concSent.setRawTextSpan(rawTS);
        logger.debug("Setting sentence raw text span to : {}", rawTS);
      }
      // and finally, add the sentence text to the string builder
      sb.append(sentText);
      procCurrOffset += sentText.length();
      if ((i + 1) < n) {
        sb.append("\n");
        procCurrOffset++;
      }
      logger.debug(sentText);
    }
  }

  public TextSpan makeSafeSpan(int start, int end) throws AnalyticException {
    TextSpan span = new TextSpan(start, end);
    boolean isValidSentTS = new ValidatableTextSpan(span).isValid();
    if (!isValidSentTS)
      throw new AnalyticException("TextSpan was not valid: "
          + span.toString());
    return span;
  }

  <T> T verifyNonNull(T obj) throws AnalyticException {
    if (obj == null) {
      throw new AnalyticException("attempting to use a null object");
    }
    return obj;
  }

  private AnnotationMetadata createTokenizationDependentMetadata(
      UUID tokenizationUuid, String addlToolName) {
    TheoryDependencies taggingDeps = new TheoryDependencies();
    taggingDeps.addToTokenizationTheoryList(tokenizationUuid);

    AnnotationMetadata md = this.getMetadata(addlToolName).setDependencies(
        taggingDeps);
    return md;
  }

  /**
   * Create a lemma-list tagging {@link AnnotationMetadata} object.
   *
   * @param tUuid
   * @return
   */
  public AnnotationMetadata getLemmaMetadata(UUID tUuid) {
    return this.createTokenizationDependentMetadata(tUuid,
        this.csProps.getLemmatizerToolName());
  }

  public AnnotationMetadata getCorefMetadata() {
    return this.getMetadata(this.csProps.getCorefToolName());
  }

  /**
   * Create a POS tagging {@link AnnotationMetadata} object.
   *
   * @param tUuid
   * @return
   */
  public AnnotationMetadata getPOSMetadata(UUID tUuid) {
    return this.createTokenizationDependentMetadata(tUuid,
        this.csProps.getPOSToolName());
  }

  /**
   * Create an NER tagging {@link AnnotationMetadata} object.
   *
   * @param tUuid
   * @return
   */
  public AnnotationMetadata getNERMetadata(UUID tUuid) {
    return this.createTokenizationDependentMetadata(tUuid,
        this.csProps.getNERToolName());
  }

  /**
   * This creates a TokenRefSequence with provided {@link UUID}
   *
   * @param left
   *          The left endpoint (inclusive) of the token range.
   * @param right
   *          The right endpoint (exclusive) of the token range. Note that
   *          {@code right} must be strictly greater than {@code left};
   *          otherwise, a exception is thrown.
   * @throws AnalyticException
   */
  public static TokenRefSequence extractTokenRefSequence(int left, int right,
      Integer head, UUID uuid) throws AnalyticException {
    if (right - left <= 0)
      throw new AnalyticException(
          "Calling extractTokenRefSequence with right <= left: left = " + left
              + ", right = " + right + ", head = " + head + ", UUID = " + uuid);

    TokenRefSequence tb = new TokenRefSequence();
    logger.debug("Working on TRS: {}", uuid.getUuidString());
    tb.setTokenizationId(uuid);

    for (int tid = left; tid < right; tid++) {
      logger.debug("Appending TRS index: {}", tid);
      tb.addToTokenIndexList(tid);
      if (head != null && head == tid) {
        tb.setAnchorTokenIndex(tid);
      }
    }
    return tb;
  }

  /**
   * Convert from 1-based indexing to 0-based indexing. This is useful for
   * getting the proper sentence (out of a list or array) that a given CoreNLP
   * mention occurs in, since CoreNLP uses 1-based indexing when referring to
   * the sentence of a mention.
   *
   * While there might be some overhead in this function call, the JVM will
   * hopefully inline the function. However, even if it doesn't, it's better to
   * keep the book-keeping code as its own.
   */
  public static int coreMentionSentenceAsIndex(int sentNum) {
    return sentNum - 1;
  }

  public static int coreMentionTokenAsIndex(int tokenIndex) {
    return tokenIndex - 1;
  }
    
  public String getLanguage() {
    return this.languageString;
  }
}
