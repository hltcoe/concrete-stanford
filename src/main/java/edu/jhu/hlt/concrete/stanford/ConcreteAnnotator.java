/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nu.xom.Attribute;
import nu.xom.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.agiga.util.ConcreteAgigaProperties;
import concrete.tools.AnnotationException;
import edu.jhu.agiga.AgigaCoref;
import edu.jhu.agiga.AgigaMention;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Constituent;
import edu.jhu.hlt.concrete.Dependency;
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
import edu.jhu.hlt.concrete.TokenizationKind;
import edu.jhu.hlt.concrete.UUID;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;
import edu.jhu.hlt.concrete.validation.ValidatableTextSpan;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
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

  public static final String toolName = "Concrete-Stanfor Pipeline";
  public static final String corpusName = "";
  public static final long annotationTime = System.currentTimeMillis();
  /**
  *
  */
  private static final HeadFinder HEAD_FINDER = new SemanticHeadFinder();

  // TODO: refactor the ConcreteAgigaProperties
  @Deprecated
  private final ConcreteAgigaProperties agigaProps;
  private final ConcreteStanfordProperties csProps;
  private final boolean allowEmptyMentions;

  private final boolean addTextSpans = true;

  public ConcreteAnnotator(String language) throws IOException {
    this.agigaProps = new ConcreteAgigaProperties();
    this.csProps = new ConcreteStanfordProperties();
    this.allowEmptyMentions = this.csProps.getAllowEmptyMentions();
  }

  @SuppressWarnings("unused")
  private AnnotationMetadata getMetadataCurrentTime(String name) {
    return new AnnotationMetadata().setTool(name).setTimestamp(
        System.currentTimeMillis() / 1000);
  }

  private AnnotationMetadata getMetadata() {
    return getMetadata(null);
  }

  private AnnotationMetadata getMetadata(String addToToolName) {
    String fullToolName = toolName;
    if (addToToolName != null)
      fullToolName += addToToolName;

    AnnotationMetadata md = new AnnotationMetadata();
    md.setTool(fullToolName);
    md.setTimestamp(annotationTime);
    return md;
  }

  public String flattenText(CoreMap coreNlpSentence) throws AnnotationException {
    StringBuilder sb = new StringBuilder();
    List<CoreLabel> tokens = coreNlpSentence
        .get(CoreAnnotations.TokensAnnotation.class);
    if (tokens == null) {
      throw new AnnotationException(
          "Cannot find tokens in the provided CoreMap");
    }
    for (CoreLabel token : tokens) {
      String word = token.get(CoreAnnotations.TextAnnotation.class);
      if (word == null) {
        throw new AnnotationException("found a null word");
      }
      sb.append(word);
      sb.append(" ");
    }
    return sb.toString().trim();
  }

  SimpleEntry<EntityMentionSet, EntitySet> convertCoref(Communication in,
      Annotation coreNlpDoc, List<Tokenization> tokenizations)
      throws AnnotationException {
    EntityMentionSet ems = new EntityMentionSet()
        .setUuid(UUIDFactory.newUUID());
    TheoryDependencies td = new TheoryDependencies();
    for (Tokenization t : tokenizations)
      td.addToTokenizationTheoryList(t.getUuid());
    AnnotationMetadata md = this
        .getMetadata(this.agigaProps.getCorefToolName()).setDependencies(td);
    ems.setMetadata(md);

    List<Entity> elist = new ArrayList<Entity>();
    if (agigaDoc.getCorefs().size() == 0) {
      logger.warn("There were no coref chains found");
    }
    for (AgigaCoref coref : agigaDoc.getCorefs()) {
      if (!coref.getMentions().isEmpty()) {
        Entity e = this.ag.convertCoref(ems, coref, agigaDoc, tokenizations);
        elist.add(e);
      } else
        logger.warn("There were not any mentions for coref: "
            + coref.toString());

    }

    if (!ems.isSetMentionList())
      ems.setMentionList(new ArrayList<EntityMention>());

    EntitySet es = new EntitySet().setUuid(UUIDFactory.newUUID())
        .setMetadata(md).setEntityList(elist);

    return new SimpleEntry<EntityMentionSet, EntitySet>(ems, es);
  }

  void convertSection(Section section, Annotation coreNlpSection,
      int charOffset, StringBuilder sb, boolean preserveTokenTaggings)
      throws AnnotationException {
    if (section.isSetSentenceList()) {
      this.convertSentences(section, coreNlpSection, charOffset, sb,
          preserveTokenTaggings);
    } else {
      this.addSentences(section, coreNlpSection, charOffset, sb,
          preserveTokenTaggings);
    }
    sb.append("\n\n");
  }

  /**
   * Create and add sentences to an existing Concrete {@code Section}.
   * 
   * @param sectToAnnotate
   * @param coreNlpSection
   * @param charOffset
   * @param sb
   * @param preserveTokenTaggings
   * @throws AnnotationException
   */
  private void addSentences(Section sectToAnnotate, Annotation coreNlpSection,
      int charOffset, StringBuilder sb, boolean preserveTokenTaggings)
      throws AnnotationException {
    List<CoreMap> sentAnnos = coreNlpSection.get(SentencesAnnotation.class);
    if (sentAnnos == null) {
      throw new AnnotationException("Section " + sectToAnnotate.getUuid()
          + " has a null CoreNLP sentences annotation");
    }
    final int n = sentAnnos.size();
    logger.debug("Adding " + n + " sentences to section "
        + sectToAnnotate.getUuid());
    int currOffset = charOffset;
    assert n > 0 : "n=" + n;
    int i = 0;
    for (CoreMap sentAnno : sentAnnos) {
      String sentText = this.flattenText(sentAnno);
      // the second argument is the estimated character provenance offset.
      Sentence st = this.makeConcreteSentence(sentAnno, currOffset,
          preserveTokenTaggings, sentText);
      sb.append(sentText);
      currOffset += sentText.length();
      if ((i + 1) < n) {
        sb.append("\n");
        currOffset++;
      }
      logger.debug(sentText);
      sectToAnnotate.addToSentenceList(st);
      ++i;
    }
  }

  private Sentence makeConcreteSentence(CoreMap sentAnno,
      int charsFromStartOfCommunication, boolean preserveTokenTaggings,
      String sentenceText) throws AnnotationException {
    Tokenization tokenization = this.makeTokenization(sentAnno,
        charsFromStartOfCommunication);
    // TODO: replace this call to getConcreteUUID with a call to a utility
    // library function that does various UUID schemes
    Sentence concSent = new Sentence().setUuid(UUIDFactory.newUUID());
    List<Token> tokenList = tokenization.getTokenList().getTokenList();
    int numTokens = tokenList.size();
    Token firstToken = tokenList.get(0);
    Token lastToken = tokenList.get(numTokens - 1);
    // if (charsFromStartOfCommunication < 0 && firstToken.getCharOffBegin() >=
    // 0
    // && lastToken.getCharOffEnd() > firstToken.getCharOffBegin()) {
    // concSent.setTextSpan(new TextSpan()
    // .setStart(firstToken.getCharOffBegin()).setEnding(
    // lastToken.getCharOffEnd()));
    // } else {
    // if (charsFromStartOfCommunication < 0) {
    // throw new RuntimeException("bad character offset of "
    // + charsFromStartOfCommunication + " for converting sent " + sent);
    // }
    // concSent.setTextSpan(new TextSpan().setStart(
    // charsFromStartOfCommunication).setEnding(
    // charsFromStartOfCommunication + sentenceText.length()));
    // }

    if (charsFromStartOfCommunication < 0) {
      throw new AnnotationException("bad character offset of "
          + charsFromStartOfCommunication + " for converting sent");
    }
    concSent.setTextSpan(new TextSpan().setStart(charsFromStartOfCommunication)
        .setEnding(charsFromStartOfCommunication + sentenceText.length()));

    concSent.setTokenization(tokenization);
    return concSent;
  }

  /**
   * This assumes that {@code concSect} has a SentenceList already initialized.
   */
  private void convertSentences(Section concSect, Annotation coreNlpSection,
      int currOffset, StringBuilder sb, boolean preserveTokenTaggings)
      throws AnnotationException {
    logger.debug("Section has : " + concSect.getSentenceList().size()
        + " sentences");
    logger.debug("convertSentences for " + concSect.getUuid());

    List<CoreMap> sentAnnos = coreNlpSection.get(SentencesAnnotation.class);
    if (sentAnnos == null) {
      throw new AnnotationException("Section " + concSect.getUuid()
          + " has a null CoreNLP sentences annotation");
    }
    final int n = sentAnnos.size();
    if (n != concSect.getSentenceList().size()) {
      throw new AnnotationException("Section " + concSect.getUuid() + " has "
          + concSect.getSentenceList().size() + " but corenlp has " + n);
    }
    logger.debug("Adding " + n + " sentences to section " + concSect.getUuid());
    assert n > 0 : "n=" + n;
    List<Sentence> concreteSentences = concSect.getSentenceList();
    for (int i = 0; i < n; i++) {
      Sentence concSent = concreteSentences.get(i);
      CoreMap coreSent = sentAnnos.get(i);
      Tokenization tokenization = this.makeTokenization(coreSent, currOffset);
      concSent.setTokenization(tokenization);

      if (currOffset < 0) {
        throw new AnnotationException("bad character offset of " + currOffset
            + " for converting sent " + concSent.getUuid());
      }

      String sentText = this.flattenText(coreSent);
      // we store the original offsets in the AgigaToken char begin/end values.
      // This means that we need to compute the actual offsets on-the-fly.
      TextSpan sentTS = makeSafeSpan(currOffset, currOffset + sentText.length());
      concSent.setTextSpan(sentTS);

      List<Token> tokenList = tokenization.getTokenList().getTokenList();
      int numTokens = tokenList.size();
      // TODO: I'm not sure we actually need to set the raw textspan on the
      // sentence
      Token firstToken = tokenList.get(0);
      Token lastToken = tokenList.get(numTokens - 1);
      TextSpan rawTS = makeSafeSpan(firstToken.getRawTextSpan().getStart(),
          lastToken.getRawTextSpan().getEnding());
      concSent.setRawTextSpan(rawTS);
      logger.debug("Setting section raw text span to : " + rawTS);
      // and finally, add the sentence text to the string builder
      sb.append(sentText);
      currOffset += sentText.length();
      if ((i + 1) < n) {
        sb.append("\n");
        currOffset++;
      }
      logger.debug(sentText);
    }
  }

  public TextSpan makeSafeSpan(int start, int end) throws AnnotationException {
    TextSpan span = new TextSpan(start, end);
    boolean isValidSentTS = new ValidatableTextSpan(span).isValid();
    if (!isValidSentTS)
      throw new AnnotationException("TextSpan was not valid: "
          + span.toString());
    return span;
  }

  private <T> T verifyNonNull(T obj) throws AnnotationException {
    if (obj == null) {
      throw new AnnotationException("attempting to use a null object");
    }
    return obj;
  }

  /**
   * Create a Tokenization based on the given sentence. If we're looking to add
   * TextSpans, then we will first default to using the token character offsets
   * within the sentence itself if charOffset is negative. If those are not set,
   * then we will use the provided charOffset, as long as it is non-negative.
   * Otherwise, this will throw a runtime exception.
   * 
   * @throws AnnotationException
   */
  public Tokenization makeTokenization(CoreMap sent, int charOffset)
      throws AnnotationException {
    if (charOffset < 0) {
      throw new AnnotationException(
          "The provided character offset cannot be < 0");
    }

    UUID tUuid = UUIDFactory.newUUID();
    Tokenization tokenization = new Tokenization()
        .setUuid(tUuid)
        .setMetadata(
            getMetadata(" http://nlp.stanford.edu/software/tokensregex.shtml"))
        .setKind(TokenizationKind.TOKEN_LIST);
    TokenList tokenList = new TokenList();
    TokenTagging lemma = new TokenTagging().setUuid(UUIDFactory.newUUID())
        .setMetadata(getMetadata()).setTaggingType("LEMMA");
    TokenTagging pos = new TokenTagging().setUuid(UUIDFactory.newUUID())
        .setMetadata(getMetadata()).setTaggingType("POS");
    TokenTagging ner = new TokenTagging().setUuid(UUIDFactory.newUUID())
        .setMetadata(getMetadata()).setTaggingType("NER");
    List<CoreLabel> tokens = verifyNonNull(sent
        .get(CoreAnnotations.TokensAnnotation.class));
    final int numTaggings = 3;
    boolean[] tagsGood = { true, true, true };
    List<Class<? extends TypesafeMap.Key<String>>> annotClasses = new ArrayList<>();
    annotClasses.add(CoreAnnotations.LemmaAnnotation.class);
    annotClasses.add(CoreAnnotations.PartOfSpeechAnnotation.class);
    annotClasses.add(CoreAnnotations.NamedEntityTagAnnotation.class);
    TokenTagging[] tokenTaggingLists = { lemma, pos, ner };
    int tokId = 0;
    for (CoreLabel token : tokens) {
      Token concToken = makeToken(token, tokId, charOffset);
      tokenList.addToTokenList(concToken);
      for (int i = 0; i < numTaggings; ++i) {
        String tag = token.get(annotClasses.get(i));
        // TODO: ensure this knows to check for annotations that may not be
        // there and fail gracefully
        if (tag != null) {
          TaggedToken tagTok = this.makeTaggedToken(tag, tokId);
          tokenTaggingLists[i].addToTaggedTokenList(tagTok);
        }
      }
      ++tokId;
      // a single space between tokens
      charOffset += concToken.getText().length() + 1;
    }

    if (!tokenList.isSetTokenList()) {
      tokenList.setTokenList(new ArrayList<Token>());
      logger.warn("Tokenization {} has empty list of tokens",
          tokenization.getUuid());
    }
    tokenization.setTokenList(tokenList);

    for (int i = 0; i < numTaggings; ++i) {
      if (tagsGood[i]) {
        tokenization.addToTokenTaggingList(tokenTaggingLists[i]);
      }
    }

    Tree tree = sent.get(TreeCoreAnnotations.TreeAnnotation.class);
    if (tree != null) {
      List<Parse> parseList = new ArrayList<Parse>();
      parseList.add(makeConcreteCParse(tree, tokId, tUuid));
      tokenization.setParseList(parseList);
    } else {
      logger.warn("Tokenization {} has an empty constituency parse",
          tokenization.getUuid());
    }
    List<DependencyParse> dependencyParses = constructDependencyParses(sent,
        tUuid);
    tokenization.setDependencyParseList(dependencyParses);
    return tokenization;
  }

  private Token makeToken(CoreLabel token, int id, int processedOffset)
      throws AnnotationException {
    String word = verifyNonNull(token.get(CoreAnnotations.TextAnnotation.class));
    Token ttok = new Token().setTokenIndex(id).setText(word);
    // The character offsets stored in each CoreLabel token represent the
    // original offsets; see StanfordAgigaPipe.updateCharOffsetSetToken
    // The actual offsets are processed on-the-fly
    int origBegin = verifyNonNull(token
        .get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    int origEnd = verifyNonNull(token
        .get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    ttok.setRawTextSpan(new TextSpan().setStart(origBegin).setEnding(origEnd));
    ttok.setTextSpan(new TextSpan().setStart(processedOffset).setEnding(
        processedOffset + word.length()));
    return ttok;
  }

  private TaggedToken makeTaggedToken(String tag, int tokId) {
    return new TaggedToken().setTokenIndex(tokId).setTag(tag).setConfidence(1f);
  }

  /**
   * Whenever there's an empty parse, this method will set the required
   * constituent list to be an empty list. It's up to the caller on what to do
   * with the returned Parse.
   *
   * @param n
   *          is the number of tokens in the sentence
   *
   * @throws AnnotationException
   */
  public Parse makeConcreteCParse(Tree root, int n, UUID tokenizationUUID)
      throws AnnotationException {
    int left = 0;
    int right = root.getLeaves().size();
    if (right != n)
      throw new AnnotationException("number of leaves in the parse (" + right
          + ") is not equal to the number of tokens in the sentence (" + n
          + ")");
    int[] idCounter = new int[] { 0 };

    Parse p = new Parse().setUuid(UUIDFactory.newUUID());
    TheoryDependencies deps = new TheoryDependencies();
    deps.addToTokenizationTheoryList(tokenizationUUID);
    AnnotationMetadata md = this.getMetadata(
        this.agigaProps.getCParseToolName()).setDependencies(deps);
    p.setMetadata(md);
    constructConstituent(root, idCounter, left, right, n, p, tokenizationUUID);
    if (!p.isSetConstituentList()) {
      logger
          .warn("Setting constituent list to compensate for the empty parse for tokenization id"
              + tokenizationUUID + " and tree " + root);
      p.setConstituentList(new ArrayList<Constituent>());
    }
    return p;
  }

  /**
   *
   * @param root
   * @param idCounter
   *          is basically an int*, lets this recursive method update max id
   *          value
   * @param left
   * @param right
   * @param n
   *          is the length of the sentence is tokens.
   * @param p
   * @param tokenizationUUID
   * @return The constituent ID
   * @throws AnnotationException
   */
  private int constructConstituent(Tree root, int[] idCounter, int left,
      int right, int n, Parse p, UUID tokenizationUUID)
      throws AnnotationException {
    if (idCounter.length != 1)
      throw new AnnotationException("ID counter must be one, but was: "
          + idCounter.length);

    Constituent constituent = new Constituent();
    constituent.setId(idCounter[0]++);
    constituent.setTag(root.value());
    constituent.setStart(left);
    constituent.setEnding(right);
    Tree headTree = null;
    if (!root.isLeaf()) {
      try {
        headTree = HEAD_FINDER.determineHead(root);
      } catch (java.lang.IllegalArgumentException iae) {
        logger.warn(
            "Failed to find head, falling back on rightmost constituent.", iae);
        headTree = root.children()[root.numChildren() - 1];
      }
    }
    int i = 0, headTreeIdx = -1;

    int leftPtr = left;
    for (Tree child : root.getChildrenAsList()) {
      int width = child.getLeaves().size();
      int childId = constructConstituent(child, idCounter, leftPtr, leftPtr
          + width, n, p, tokenizationUUID);
      constituent.addToChildList(childId);

      leftPtr += width;
      if (headTree != null && child == headTree) {
        assert (headTreeIdx < 0);
        headTreeIdx = i;
      }
      i++;
    }

    if (headTreeIdx >= 0)
      constituent.setHeadChildIndex(headTreeIdx);

    p.addToConstituentList(constituent);
    if (!constituent.isSetChildList())
      constituent.setChildList(new ArrayList<Integer>());
    return constituent.getId();
  }

  /**
   * In order to allow for possibly empty mentions, this will always return a
   * validating TokenRefSequence, provided m.end &gt;= m.start. When the end
   * points are equal, the token index list will be the empty list, and a
   * warning will be logged.
   *
   * @throws AnnotationException
   */
  public TokenRefSequence extractTokenRefSequence(AgigaMention m, UUID uuid)
      throws AnnotationException {
    int start = m.getStartTokenIdx();
    int end = m.getEndTokenIdx();
    if (end - start < 0)
      throw new AnnotationException(
          "Calling extractTokenRefSequence on mention " + m + " with head = "
              + m.getHeadTokenIdx() + ", UUID = " + uuid);
    else if (end == start) {
      TokenRefSequence tb = new TokenRefSequence();
      tb.setTokenizationId(uuid).setTokenIndexList(new ArrayList<Integer>());
      if (m.getHeadTokenIdx() >= 0)
        tb.setAnchorTokenIndex(m.getHeadTokenIdx());

      logger.warn("Creating an EMPTY mention for mention " + m
          + " with UUID = " + uuid);
      return tb;
    }
    return extractTokenRefSequence(start, end, m.getHeadTokenIdx(), uuid);
  }

  /**
   * This creates a TokenRefSequence with provided {@link UUID}
   *
   * @param left
   *          The left endpoint (inclusive) of the token range.
   * @param right
   *          The right endpoint (exclusive) of the token range. Note that
   *          {@code right} must be strictly greater than {@code left};
   *          otherwise, a runtime exception is called.
   * @throws AnnotationException
   */
  public TokenRefSequence extractTokenRefSequence(int left, int right,
      Integer head, UUID uuid) throws AnnotationException {
    if (right - left <= 0)
      throw new AnnotationException(
          "Calling extractTokenRefSequence with right <= left: left = " + left
              + ", right = " + right + ", head = " + head + ", UUID = " + uuid);

    TokenRefSequence tb = new TokenRefSequence();
    tb.setTokenizationId(uuid);

    for (int tid = left; tid < right; tid++) {
      tb.addToTokenIndexList(tid);
      if (head != null && head == tid) {
        tb.setAnchorTokenIndex(tid);
      }
    }
    return tb;
  }

  public List<DependencyParse> constructDependencyParses(CoreMap sentence,
      UUID tokUuid) throws AnnotationException {
    List<Class<? extends TypesafeMap.Key<SemanticGraph>>> whichDeps = new ArrayList<>();
    whichDeps
        .add(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    whichDeps
        .add(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    whichDeps
        .add(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
    String[] names = { "basic-deps", "col-dependencies", "col-ccproc-deps" };

    List<DependencyParse> depParseList = new ArrayList<>();
    int i = 0;
    for (Class<? extends TypesafeMap.Key<SemanticGraph>> whichDepId : whichDeps) {
      SemanticGraph semGraph = sentence.get(whichDepId);
      if (semGraph == null) {
        logger.warn("Dependency parse {} is null", whichDepId.getName());
        continue;
      }
      // TODO: we could add a guard for semGraph.size() == 0
      DependencyParse dp = makeDepParse(semGraph, names[i++], tokUuid);
      depParseList.add(dp);
    }
    return depParseList;
  }

  private DependencyParse makeDepParse(SemanticGraph semGraph,
      String depParseType, UUID tokenizationUUID) throws AnnotationException {
    DependencyParse depParse = new DependencyParse();
    depParse.setUuid(UUIDFactory.newUUID());
    TheoryDependencies td = new TheoryDependencies();
    td.addToTokenizationTheoryList(tokenizationUUID);
    String toolName = depParseType + " " + this.agigaProps.getDParseToolName();
    AnnotationMetadata md = this.getMetadata(toolName).setDependencies(td);
    depParse.setMetadata(md);
    List<Dependency> dependencies = makeDependencies(semGraph);
    depParse.setDependencyList(dependencies);
    return depParse;
  }

  private List<Dependency> makeDependencies(SemanticGraph graph)
      throws AnnotationException {
    if (graph == null) {
      throw new AnnotationException("Semantic graph is null");
    }
    List<Dependency> depList = new ArrayList<Dependency>();
    for (IndexedWord root : graph.getRoots()) {
      // this mimics CoreNLP's handling
      String rel = GrammaticalRelation.ROOT.getLongName()
          .replaceAll("\\s+", "");
      int dep = root.index() - 1;
      depList.add(makeDependency(rel, -1, dep));
    }
    for (SemanticGraphEdge edge : graph.edgeListSorted()) {
      String rel = edge.getRelation().toString().replaceAll("\\s+", "");
      int gov = edge.getSource().index() - 1;
      int dep = edge.getTarget().index() - 1;
      Dependency depend = makeDependency(rel, gov, dep);
      depList.add(depend);
    }
    return depList;
  }

  private Dependency makeDependency(String rel, int gov, int dep) {
    Dependency depObj = new Dependency().setEdgeType(rel).setDep(dep);
    if (gov >= 0) { // otherwise, we're dealing with the root
      depObj.setGov(gov);
    }
    return depObj;
  }

  public void constructEntityStuff(CoreMap sentence) {
    Map<Integer, CorefChain> corefChains =
        annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
      if (corefChains != null) {
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        Element corefInfo = new Element("coreference", NAMESPACE_URI);
        if (addCorefGraphInfo(options, corefInfo, sentences, corefChains, NAMESPACE_URI))
          docElem.appendChild(corefInfo);
      }
    
    
    // add the MR entities and relations
    List<edu.stanford.nlp.ie.machinereading.structure.EntityMention> entities = sentence
        .get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    if(entities == null) {
      logger.info("No entities found for "));
    }
    if (entities != null && entities.size() > 0) {
      for (EntityMention e: entities) {        
        Element top = new Element("entity", curNS);
        top.addAttribute(new Attribute("id", entity.getObjectId()));
        Element type = new Element("type", curNS);
        type.appendChild(entity.getType());
        top.appendChild(entity.getType());
        if (entity.getNormalizedName() != null){
          Element nm = new Element("normalized", curNS);
          nm.appendChild(entity.getNormalizedName());
          top.appendChild(nm);
        }

        if (entity.getSubType() != null){
          Element subtype = new Element("subtype", curNS);
          subtype.appendChild(entity.getSubType());
          top.appendChild(subtype);
        }
        Element span = new Element("span", curNS);
        span.addAttribute(new Attribute("start", Integer.toString(entity.getHeadTokenStart())));
        span.addAttribute(new Attribute("end", Integer.toString(entity.getHeadTokenEnd())));
        top.appendChild(span);

        top.appendChild(makeProbabilitiesElement(entity, curNS));
      }
    }
  }
}
