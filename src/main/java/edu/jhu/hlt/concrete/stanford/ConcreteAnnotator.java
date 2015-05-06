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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.tools.AnnotationException;
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
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
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

  public static final String toolName = "Concrete-Stanford Pipeline";
  public static final String corpusName = "";
  public static final long annotationTime = System.currentTimeMillis();
  /**
  *
  */
  private static final HeadFinder HEAD_FINDER = new SemanticHeadFinder();

  // TODO: refactor the ConcreteAgigaProperties
  private final ConcreteStanfordProperties csProps;
  // private final boolean allowEmptyMentions;
  private final String language;

  private final String[] annotatorList;

  public ConcreteAnnotator(String language) throws IOException {
    this.csProps = new ConcreteStanfordProperties();
    // this.allowEmptyMentions = this.csProps.getAllowEmptyMentions();
    this.language = language;
    this.annotatorList = ConcreteAnnotator.getDefaultAnnotators();
  }

  public ConcreteAnnotator(String language, String[] annotators)
      throws IOException {
    this.csProps = new ConcreteStanfordProperties();
    // this.allowEmptyMentions = this.csProps.getAllowEmptyMentions();
    this.language = language;
    this.annotatorList = annotators;
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

  public static String flattenText(CoreMap coreNlpSentence)
      throws AnnotationException {
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
    List<CoreMap> coreSentences = coreNlpDoc
        .get(CoreAnnotations.SentencesAnnotation.class);
    if (coreSentences == null) {
      throw new AnnotationException("Communication " + in.getId()
          + " has a null list of CoreNLP sentences");
    }
    if (coreSentences.size() != tokenizations.size()) {
      throw new AnnotationException("Communication " + in.getId()
          + " knows of " + tokenizations.size()
          + " valid tokenizations, but CoreNLP reports having "
          + coreSentences.size() + " sentences. These values must agree.");
    }
    if (tokenizations.size() == 0) {
      logger.warn("Communication " + in.getId()
          + " has no valid tokenizations.");
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
      ConcreteCreator cc = new ConcreteCreator();
      for (CorefChain chain : coreNlpChains.values()) {
        Entity entity = cc.makeEntity(chain, ems, tokenizations);
        es.addToEntityList(entity);
      }
    }

    if (!ems.isSetMentionList())
      ems.setMentionList(new ArrayList<EntityMention>());

    return new SimpleEntry<EntityMentionSet, EntitySet>(ems, es);
  }

  private UUID getTokenizationUuidSafe(List<Tokenization> tokenizations, int idx)
      throws AnnotationException {
    if (idx >= tokenizations.size()) {
      throw new AnnotationException("the sentence number of the mention ("
          + idx + ") is out of range of the known sentences (of size "
          + tokenizations.size() + ")");
    }
    return tokenizations.get(idx).getUuid();
  }

  class ConcreteCreator {

    /**
     * Create and add sentences to an existing Concrete {@code Section}.
     * 
     * @param sectToAnnotate
     * @param coreNlpSection
     * @param charOffset
     * @param sb
     * @throws AnnotationException
     */
    public void makeSentences(Section sectToAnnotate,
        Annotation coreNlpSection, int procCharOffset, StringBuilder sb)
        throws AnnotationException {
      List<CoreMap> sentAnnos = coreNlpSection.get(SentencesAnnotation.class);
      if (sentAnnos == null) {
        throw new AnnotationException("Section " + sectToAnnotate.getUuid()
            + " has a null CoreNLP sentences annotation");
      }
      final int n = sentAnnos.size();
      logger.debug("Adding " + n + " sentences to section "
          + sectToAnnotate.getUuid());
      int currOffset = procCharOffset;
      assert n > 0 : "n=" + n;
      int i = 0;
      for (CoreMap sentAnno : sentAnnos) {
        String sentText = flattenText(sentAnno);
        // the second argument is the estimated character provenance offset.
        Sentence st = makeConcreteSentence(sentAnno, currOffset, sentText);
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

    /**
     * Create a Tokenization based on the given sentence. If we're looking to
     * add TextSpans, then we will first default to using the token character
     * offsets within the sentence itself if charOffset is negative. If those
     * are not set, then we will use the provided charOffset, as long as it is
     * non-negative. Otherwise, this will throw a runtime exception.
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
      String word = verifyNonNull(token
          .get(CoreAnnotations.TextAnnotation.class));
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
      return new TaggedToken().setTokenIndex(tokId).setTag(tag)
          .setConfidence(1f);
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
      AnnotationMetadata md = getMetadata(csProps.getCParseToolName())
          .setDependencies(deps);
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
              "Failed to find head, falling back on rightmost constituent.",
              iae);
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

    private Sentence makeConcreteSentence(CoreMap sentAnno,
        int charsFromStartOfCommunication, String sentenceText)
        throws AnnotationException {
      Tokenization tokenization = makeTokenization(sentAnno,
          charsFromStartOfCommunication);
      // TODO: replace this call to getConcreteUUID with a call to a utility
      // library function that does various UUID schemes
      Sentence concSent = new Sentence().setUuid(UUIDFactory.newUUID());
      List<Token> tokenList = tokenization.getTokenList().getTokenList();
      int numTokens = tokenList.size();
      Token firstToken = tokenList.get(0);
      Token lastToken = tokenList.get(numTokens - 1);
      TextSpan rawTS = makeSafeSpan(firstToken.getRawTextSpan().getStart(),
          lastToken.getRawTextSpan().getEnding());
      concSent.setRawTextSpan(rawTS);
      logger.debug("Setting sentence raw text span to : " + rawTS);

      if (charsFromStartOfCommunication < 0) {
        throw new AnnotationException("bad character offset of "
            + charsFromStartOfCommunication + " for converting sent");
      }
      // TODO: add raw text span
      concSent.setTextSpan(new TextSpan().setStart(
          charsFromStartOfCommunication).setEnding(
          charsFromStartOfCommunication + sentenceText.length()));

      concSent.setTokenization(tokenization);
      return concSent;
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
      String toolName = depParseType + " " + csProps.getDParseToolName();
      AnnotationMetadata md = getMetadata(toolName).setDependencies(td);
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
        String rel = GrammaticalRelation.ROOT.getLongName().replaceAll("\\s+",
            "");
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

    private EntityMention makeEntityMention(
        CorefChain.CorefMention coreMention, UUID tokenizationUuid,
        boolean representative) throws AnnotationException {
      EntityMention concEntityMention = new EntityMention().setUuid(UUIDFactory
          .newUUID());
      TokenRefSequence trs = extractTokenRefSequence(coreMention,
          tokenizationUuid, representative);
      concEntityMention.setTokens(trs);
      concEntityMention.setText(coreMention.mentionSpan);
      // TODO: we could possibly add mention types. We could use a feature of
      // CoreNLP:
      // MentionType mentionType = coreMention.mentionType;
      // or we could use a heuristic (see concrete-agiga).
      // String emType = getEntityMentionType(em, tokenization);
      return concEntityMention;
    }

    private Entity makeEntity(CorefChain chain, EntityMentionSet ems,
        List<Tokenization> tokenizations) throws AnnotationException {
      Entity concEntity = new Entity().setUuid(UUIDFactory.newUUID());
      CorefChain.CorefMention coreHeadMention = chain
          .getRepresentativeMention();
      // CoreNLP uses 1-based indexing for the sentences
      EntityMention concHeadMention = makeEntityMention(
          coreHeadMention,
          getTokenizationUuidSafe(tokenizations, ConcreteAnnotator
              .coreMentionSentenceAsIndex(coreHeadMention.sentNum)), true);
      concEntity.setCanonicalName(coreHeadMention.mentionSpan);
      concEntity.addToMentionIdList(concHeadMention.getUuid());
      ems.addToMentionList(concHeadMention);
      for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
        if (mention == coreHeadMention)
          continue;
        // CoreNLP uses 1-based indexing for the sentences
        EntityMention concMention = this.makeEntityMention(
            mention,
            getTokenizationUuidSafe(tokenizations,
                ConcreteAnnotator.coreMentionSentenceAsIndex(mention.sentNum)),
            false);
        ems.addToMentionList(concMention);
        concEntity.addToMentionIdList(concMention.getUuid());
      }
      return concEntity;
    }

  }

  /**
   * In order to allow for possibly empty mentions, this will always return a
   * validating TokenRefSequence, provided m.end &gt;= m.start. When the end
   * points are equal, the token index list will be the empty list, and a
   * warning will be logged.
   *
   * @throws AnnotationException
   */
  public TokenRefSequence extractTokenRefSequence(CorefMention coreMention,
      UUID tokUuid, boolean representative) throws AnnotationException {
    int start = coreMention.startIndex;
    int end = coreMention.endIndex;
    int head = coreMention.headIndex;
    if (end - start < 0) {
      throw new AnnotationException(
          "Calling extractTokenRefSequence on mention " + coreMention
              + " with head = " + head + ", UUID = " + tokUuid);
    } else if (end == start) {
      TokenRefSequence tb = new TokenRefSequence();
      tb.setTokenizationId(tokUuid).setTokenIndexList(new ArrayList<Integer>());
      if (representative) {
        tb.setAnchorTokenIndex(head);
      }
      logger.warn("Creating an EMPTY mention for mention " + coreMention
          + " with UUID = " + tokUuid);
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
   * @throws AnnotationException
   */
  public void augmentSectionAnnotations(Section section,
      Annotation coreNlpSection, int procCharOffset, StringBuilder sb)
      throws AnnotationException {
    if (section.isSetSentenceList()) {
      this.augmentSentences(section, coreNlpSection, procCharOffset, sb);
    } else {
      ConcreteCreator cc = new ConcreteCreator();
      cc.makeSentences(section, coreNlpSection, procCharOffset, sb);
    }
    sb.append("\n\n");
  }

  /**
   * Augment an existing Tokenization based on the given sentence.
   * 
   * @throws AnnotationException
   */
  public void augmentTokenization(Tokenization tokenization, CoreMap sent,
      ConcreteCreator cc, int charOffset) throws AnnotationException {
    if (charOffset < 0) {
      throw new AnnotationException(
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
    boolean[] tagsGood = { true, true, true };
    int tokId = 0;
    for (CoreLabel token : tokens) {
      for (int i = 0; i < numTaggings; ++i) {
        String tag = token.get(annotClasses.get(i));
        // TODO: ensure this knows to check for annotations that may not be
        // there and fail gracefully
        if (tag != null) {
          TaggedToken tagTok = cc.makeTaggedToken(tag, tokId);
          tokenTaggingLists.get(i).addToTaggedTokenList(tagTok);
        }
      }
      ++tokId;
    }

    for (int i = 0; i < numTaggings; ++i) {
      if (tagsGood[i]) {
        tokenization.addToTokenTaggingList(tokenTaggingLists.get(i));
      }
    }
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
   * @throws AnnotationException
   */
  public void augmentSentences(Section concSect, Annotation coreNlpSection,
      int procCurrOffset, StringBuilder sb) throws AnnotationException {
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
    ConcreteCreator cc = new ConcreteCreator();
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
          throw new AnnotationException("Sentence " + concSent.getUuid()
              + " does not have a valid tokenization or iterable token list");
        }
        this.augmentTokenization(tokenization, coreSent, cc, procCurrOffset);
        whichBranch = 1;
        if (procCurrOffset != concSent.getTextSpan().getStart()
            && (procCurrOffset + sentText.length()) != concSent.getTextSpan()
                .getEnding()) {
          throw new AnnotationException(
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
        continue;
      } else {
        if (priorBranch != whichBranch) {
          throw new AnnotationException(
              "Section "
                  + concSect.getUuid()
                  + " has some sentences with Tokenizations set, and others without Tokenizations set");
        }
      }

      if (procCurrOffset < 0) {
        throw new AnnotationException("bad character offset of "
            + procCurrOffset + " for converting sent " + concSent.getUuid());
      }

      List<Token> tokenList = tokenization.getTokenList().getTokenList();
      int numTokens = tokenList.size();
      // TODO: I'm not sure we actually need to set the raw textspan on the
      // sentence
      if (!concSent.isSetRawTextSpan()) {
        logger
            .warn("Concrete sentence "
                + concSent.getUuid()
                + " does not have raw text spans set. We can compute these on the fly, but something might be wrong.");
        Token firstToken = tokenList.get(0);
        Token lastToken = tokenList.get(numTokens - 1);
        TextSpan rawTS = makeSafeSpan(firstToken.getRawTextSpan().getStart(),
            lastToken.getRawTextSpan().getEnding());
        concSent.setRawTextSpan(rawTS);
        logger.debug("Setting sentence raw text span to : " + rawTS);
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
   *          otherwise, a runtime exception is called.
   * @throws AnnotationException
   */
  public static TokenRefSequence extractTokenRefSequence(int left, int right,
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

  public String getLanguage() {
    return language;
  }

}
