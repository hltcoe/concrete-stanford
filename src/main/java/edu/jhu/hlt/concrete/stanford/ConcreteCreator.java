/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Constituent;
import edu.jhu.hlt.concrete.Dependency;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.Parse;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TaggedToken;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.TheoryDependencies;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.TokenRefSequence;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.TokenizationKind;
import edu.jhu.hlt.concrete.UUID;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;

public class ConcreteCreator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteCreator.class);

  /**
   *
   */
  private final ConcreteAnnotator concreteAnnotator;

  /**
   * @param concreteAnnotator
   */
  ConcreteCreator(ConcreteAnnotator concreteAnnotator) {
    this.concreteAnnotator = concreteAnnotator;
  }

  /**
   * Create and add sentences to an existing Concrete {@code Section}.
   *
   * @param sectToAnnotate
   * @param coreNlpSection
   * @param charOffset
   * @param sb
   * @throws AnalyticException
   */
  public void makeSentences(Section sectToAnnotate,
      Annotation coreNlpSection, int procCharOffset, StringBuilder sb)
      throws AnalyticException {
    List<CoreMap> sentAnnos = coreNlpSection.get(SentencesAnnotation.class);
    if (sentAnnos == null) {
      throw new AnalyticException("Section " + sectToAnnotate.getUuid()
          + " has a null CoreNLP sentences annotation");
    }
    final int n = sentAnnos.size();
    LOGGER.debug("Adding {} sentences to section {}", n,
        sectToAnnotate.getUuid());
    int currOffset = procCharOffset;
    if (n <= 0)
      throw new IllegalArgumentException("The number of sentence annotations was <= 0.");

    // assert n > 0 : "n=" + n;
    int i = 0;
    for (CoreMap sentAnno : sentAnnos) {
      String sentText = ConcreteAnnotator.flattenText(sentAnno);
      // the second argument is the estimated character provenance offset.
      Sentence st = makeConcreteSentence(sentAnno, currOffset, sentText);
      sb.append(sentText);
      currOffset += sentText.length();
      if ((i + 1) < n) {
        sb.append("\n");
        currOffset++;
      }
      LOGGER.debug(sentText);
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
   * @throws AnalyticException
   */
  public Tokenization makeTokenization(CoreMap sent, int charOffset)
      throws AnalyticException {
    if (charOffset < 0) {
      throw new AnalyticException(
          "The provided character offset cannot be < 0");
    }

    UUID tUuid = UUIDFactory.newUUID();
    Tokenization tokenization = new Tokenization()
        .setUuid(tUuid)
        .setMetadata(
            this.concreteAnnotator.getMetadata(" http://nlp.stanford.edu/software/tokensregex.shtml"))
        .setKind(TokenizationKind.TOKEN_LIST);
    TokenList tokenList = new TokenList();
    List<CoreLabel> tokens = this.concreteAnnotator.verifyNonNull(sent
        .get(CoreAnnotations.TokensAnnotation.class));
    int tokId = 0;
    for (CoreLabel token : tokens) {
      Token concToken = makeToken(token, tokId, charOffset);
      tokenList.addToTokenList(concToken);
      ++tokId;
      // a single space between tokens
      charOffset += concToken.getText().length() + 1;
    }

    if (!tokenList.isSetTokenList()) {
      tokenList.setTokenList(new ArrayList<Token>());
      LOGGER.warn("Tokenization {} has empty list of tokens",
          tokenization.getUuid());
    }
    tokenization.setTokenList(tokenList);

    this.concreteAnnotator.augmentTokenization(tokenization, sent, this, charOffset);

    return tokenization;
  }

  private Token makeToken(CoreLabel token, int id, int processedOffset)
      throws AnalyticException {
    String word = this.concreteAnnotator.verifyNonNull(token
        .get(CoreAnnotations.TextAnnotation.class));
    Token ttok = new Token().setTokenIndex(id).setText(word);
    // The character offsets stored in each CoreLabel token represent the
    // original offsets; see StanfordAgigaPipe.updateCharOffsetSetToken
    // The actual offsets are processed on-the-fly
    int origBegin = this.concreteAnnotator.verifyNonNull(token
        .get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    int origEnd = this.concreteAnnotator.verifyNonNull(token
        .get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    ttok.setRawTextSpan(new TextSpan().setStart(origBegin).setEnding(origEnd));
    ttok.setTextSpan(new TextSpan().setStart(processedOffset).setEnding(
        processedOffset + word.length()));
    //logger.info("New token: {}, {}, [{}, {}), {}, [{},{}), {}", id, word, origBegin, origEnd, ttok.getRawTextSpan(), processedOffset, processedOffset + word.length(), ttok.getTextSpan());
    return ttok;
  }

  TaggedToken makeTaggedToken(String tag, int tokId) {
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
   * @throws AnalyticException
   */
  public Parse makeConcreteCParse(Tree root, int n, UUID tokenizationUUID)
      throws AnalyticException {
    int left = 0;
    int right = root.getLeaves().size();
    if (right != n)
      throw new AnalyticException("number of leaves in the parse (" + right
          + ") is not equal to the number of tokens in the sentence (" + n
          + ")");
    int[] idCounter = new int[] { 0 };

    Parse p = new Parse().setUuid(UUIDFactory.newUUID());
    TheoryDependencies deps = new TheoryDependencies();
    deps.addToTokenizationTheoryList(tokenizationUUID);
    AnnotationMetadata md = this.concreteAnnotator.getMetadata(this.concreteAnnotator.getProps().getCParseToolName())
        .setDependencies(deps);
    p.setMetadata(md);
    constructConstituent(root, idCounter, left, right, n, p, tokenizationUUID);
    if (!p.isSetConstituentList()) {
      LOGGER.warn("Setting constituent list to compensate for the empty parse for tokenization id {} and tree {}", tokenizationUUID, root);
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
   * @throws AnalyticException
   */
  private int constructConstituent(Tree root, int[] idCounter, int left,
      int right, int n, Parse p, UUID tokenizationUUID)
      throws AnalyticException {
    if (idCounter.length != 1)
      throw new AnalyticException("ID counter must be one, but was: "
          + idCounter.length);

    Constituent constituent = new Constituent();
    constituent.setId(idCounter[0]++);
    constituent.setTag(root.value());
    constituent.setStart(left);
    constituent.setEnding(right);
    Tree headTree = null;
    if (!root.isLeaf()) {
      try {
        headTree = this.concreteAnnotator.getHeadFinder().determineHead(root);
      } catch (java.lang.IllegalArgumentException iae) {
        LOGGER.warn("Failed to find head, falling back on rightmost constituent.", iae);
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
      throws AnalyticException {
    Tokenization tokenization = makeTokenization(sentAnno,
        charsFromStartOfCommunication);
    // TODO: replace this call to getConcreteUUID with a call to a utility
    // library function that does various UUID schemes
    Sentence concSent = new Sentence().setUuid(UUIDFactory.newUUID());
    List<Token> tokenList = tokenization.getTokenList().getTokenList();
    int numTokens = tokenList.size();
    Token firstToken = tokenList.get(0);
    Token lastToken = tokenList.get(numTokens - 1);
    TextSpan rawTS = this.concreteAnnotator.makeSafeSpan(firstToken.getRawTextSpan().getStart(),
        lastToken.getRawTextSpan().getEnding());
    concSent.setRawTextSpan(rawTS);
    LOGGER.debug("Setting sentence raw text span to : {}" , rawTS);

    if (charsFromStartOfCommunication < 0) {
      throw new AnalyticException("bad character offset of "
          + charsFromStartOfCommunication + " for converting sent");
    }
    concSent.setTextSpan(new TextSpan().setStart(
        charsFromStartOfCommunication).setEnding(
        charsFromStartOfCommunication + sentenceText.length()));

    concSent.setTokenization(tokenization);
    return concSent;
  }

  public List<DependencyParse> constructDependencyParses(CoreMap sentence,
      UUID tokUuid) throws AnalyticException {
    List<Class<? extends TypesafeMap.Key<SemanticGraph>>> whichDeps = new ArrayList<>();
    whichDeps.add(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    whichDeps.add(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    whichDeps.add(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
    String[] names = { "basic-deps", "col-dependencies", "col-ccproc-deps" };

    List<DependencyParse> depParseList = new ArrayList<>();
    int i = 0;
    for (Class<? extends TypesafeMap.Key<SemanticGraph>> whichDepId : whichDeps) {
      SemanticGraph semGraph = sentence.get(whichDepId);
      if (semGraph == null) {
        LOGGER.warn("Dependency parse {} is null", whichDepId.getName());
        continue;
      }
      // TODO: we could add a guard for semGraph.size() == 0
      DependencyParse dp = makeDepParse(semGraph, names[i++], tokUuid);
      depParseList.add(dp);
    }
    return depParseList;
  }

  private DependencyParse makeDepParse(SemanticGraph semGraph,
      String depParseType, UUID tokenizationUUID) throws AnalyticException {
    DependencyParse depParse = new DependencyParse();
    depParse.setUuid(UUIDFactory.newUUID());
    TheoryDependencies td = new TheoryDependencies();
    td.addToTokenizationTheoryList(tokenizationUUID);
    String toolName = depParseType + " " + this.concreteAnnotator.getProps().getDParseToolName();
    AnnotationMetadata md = this.concreteAnnotator.getMetadata(toolName).setDependencies(td);
    depParse.setMetadata(md);
    List<Dependency> dependencies = makeDependencies(semGraph);
    depParse.setDependencyList(dependencies);
    return depParse;
  }

  private List<Dependency> makeDependencies(SemanticGraph graph)
      throws AnalyticException {
    if (graph == null) {
      throw new AnalyticException("Semantic graph is null");
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

  private EntityMention makeEntityMention(CorefChain.CorefMention coreMention, UUID tokenizationUuid, boolean representative) throws AnalyticException {
    EntityMention concEntityMention = new EntityMention().setUuid(UUIDFactory.newUUID());
    TokenRefSequence trs = ConcreteAnnotator.extractTokenRefSequence(coreMention, tokenizationUuid, representative);
    concEntityMention.setTokens(trs);
    concEntityMention.setText(coreMention.mentionSpan);
    // TODO: we could possibly add mention types. We could use a feature of
    // CoreNLP:
    // MentionType mentionType = coreMention.mentionType;
    // or we could use a heuristic (see concrete-agiga).
    // String emType = getEntityMentionType(em, tokenization);
    return concEntityMention;
  }

  Entity makeEntity(CorefChain chain, EntityMentionSet ems,
      List<Tokenization> tokenizations) throws AnalyticException {
    Entity concEntity = new Entity().setUuid(UUIDFactory.newUUID());
    CorefChain.CorefMention coreHeadMention = chain.getRepresentativeMention();
    // CoreNLP uses 1-based indexing for the sentences
    Tokenization tkz = ConcreteAnnotator.getTokenizationSafe(tokenizations, ConcreteAnnotator
        .coreMentionSentenceAsIndex(coreHeadMention.sentNum));
    UUID tkzUuid = tkz.getUuid();
    LOGGER.debug("Creating EntityMention based on tokenization: {}", tkzUuid.getUuidString());
    EntityMention concHeadMention = makeEntityMention(coreHeadMention, tkzUuid, true);
    TokenRefSequence trs = concHeadMention.getTokens();
    List<Integer> trsIntList = trs.getTokenIndexList();
    Set<Integer> tkzIntSet = tkz.getTokenList().getTokenList().stream()
        .map(tk -> tk.getTokenIndex())
        .collect(Collectors.toSet());

    LOGGER.debug("Set tokens: {}", tkzIntSet.toString());
    LOGGER.debug("TRS tokens: {}", trsIntList.toString());

    if (!tkzIntSet.containsAll(trsIntList)) {
      LOGGER.error("The produced TokenRefSequence for Tokenization {} is invalid.", tkzUuid.getUuidString());
      LOGGER.error("The token indices do not align.");
      LOGGER.error("Tokenization indices: {}", tkzIntSet.toString());
      LOGGER.error("TokenRefSeq indices: {}", trsIntList.toString());
      throw new AnalyticException("TokenRefSequence tokens are not a subset of Tokenization tokens for Tokenization: " + tkzUuid.getUuidString());
    }

    concEntity.setCanonicalName(coreHeadMention.mentionSpan);
    concEntity.addToMentionIdList(concHeadMention.getUuid());
    ems.addToMentionList(concHeadMention);
    for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
      if (mention == coreHeadMention)
        continue;
      // CoreNLP uses 1-based indexing for the sentences
      Tokenization localTkz = ConcreteAnnotator.getTokenizationSafe(tokenizations,
          ConcreteAnnotator.coreMentionSentenceAsIndex(mention.sentNum));
      Set<Integer> localTkzIntSet = localTkz.getTokenList().getTokenList().stream()
          .map(tk -> tk.getTokenIndex())
          .collect(Collectors.toSet());

      LOGGER.debug("Set tokens: {}", localTkzIntSet.toString());
      EntityMention concMention = this.makeEntityMention(mention, localTkz.getUuid(), false);
      List<Integer> localTrsIntList = concMention.getTokens().getTokenIndexList();
      LOGGER.debug("TRS local tokens: {}", localTrsIntList.toString());
      if (!localTkzIntSet.containsAll(localTrsIntList)) {
        LOGGER.error("The produced TokenRefSequence for Tokenization {} is invalid.", localTkz.getUuid().getUuidString());
        LOGGER.error("The token indices do not align.");
        LOGGER.error("Local Tokenization indices: {}", localTkzIntSet.toString());
        LOGGER.error("Local TokenRefSeq indices: {}", localTrsIntList.toString());

        throw new AnalyticException("TokenRefSequence tokens are not a subset of Tokenization tokens for Tokenization: " + tkzUuid.getUuidString());
      }

      ems.addToMentionList(concMention);
      concEntity.addToMentionIdList(concMention.getUuid());
    }
    return concEntity;
  }
}