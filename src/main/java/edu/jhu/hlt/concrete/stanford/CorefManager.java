/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.TheoryDependencies;
import edu.jhu.hlt.concrete.TokenRefSequence;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.UUID;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.metadata.AnnotationMetadataFactory;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * Handles Stanford CoreNLP's coreference annotations and adding them
 * to a {@link Communication} object.
 */
class CorefManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(CorefManager.class);

  private final TokenizedCommunication tc;
  private final Annotation annotation;
  private final AnalyticUUIDGenerator gen;

  /**
   *
   */
  public CorefManager(final TokenizedCommunication tc, final Annotation annotation) {
    this.tc = tc;
    this.annotation = annotation;
    this.gen = new AnalyticUUIDGeneratorFactory(tc.getRoot()).create();
  }

  public TokenizedCommunication addCoreference() throws AnalyticException {
    List<Tokenization> tkzList = this.tc.getTokenizations();
    Communication root = this.tc.getRoot();

    final String commId = root.getId();

    Optional<List<CoreMap>> coreSentences = Optional.ofNullable(this.annotation.get(CoreAnnotations.SentencesAnnotation.class));
    List<CoreMap> cmList = coreSentences.orElseThrow(() -> new AnalyticException("Communication " + commId + " did not have any CoreNLP sentences."));

    final int cmListSize = cmList.size();
    final int tkzListSize = tkzList.size();
    if (cmListSize != tkzListSize)
      throw new AnalyticException("Communication " + commId + " had a different number of coreMaps and Tokenizations."
          + "\nCoreMaps: " + cmListSize + " vs. Tokenizations: " + tkzListSize);

    EntityMentionSet ems = new EntityMentionSet()
        .setUuid(gen.next())
        .setMentionList(new ArrayList<>());
    TheoryDependencies td = new TheoryDependencies();
    tkzList.forEach(t -> td.addToTokenizationTheoryList(t.getUuid()));

    // AnnotationMetadata md = this.getMetadata(this.csProps.getCorefToolName())
    // .setDependencies(td);
    AnnotationMetadata md = AnnotationMetadataFactory.fromCurrentLocalTime().setTool("Stanford Coref").setDependencies(td);
    ems.setMetadata(md);
    EntitySet es = new EntitySet().setUuid(gen.next())
        .setMetadata(md)
        .setEntityList(new ArrayList<Entity>())
        .setMentionSetId(ems.getUuid());

    Optional<Map<Integer, CorefChain>> coreNlpChainsOption = Optional.ofNullable(this.annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class));
    if (coreNlpChainsOption.isPresent()) {
      Map<Integer, CorefChain> chains = coreNlpChainsOption.get();
      for (CorefChain chain : chains.values()) {
        Entity entity = this.makeEntity(chain, ems, tkzList);
        es.addToEntityList(entity);
      }
    } else
      LOGGER.warn("No coref chains found for Communication: " + commId);

    if (!ems.isSetMentionList())
      ems.setMentionList(new ArrayList<EntityMention>());

//    if (ems == null || !ems.isSetMentionList())
//      throw new AnalyticException(
//          "Concrete-agiga produced a null EntityMentionSet, or a null mentionList.");
//
//    if (ems.getMentionListSize() == 0
//        && !this.allowEmptyEntitiesAndEntityMentions)
//      throw new AnalyticException(
//          "Empty entity mentions are disallowed and no entity mentions were produced for communication: "
//              + comm.getId());
//    else
//      comm.addToEntityMentionSetList(ems);
//
//    if (es == null || !es.isSetEntityList())
//      throw new AnalyticException(
//          "Concrete-agiga produced a null EntitySet, or a null entityList.");
//
//    if (es.getEntityListSize() == 0
//        && !this.allowEmptyEntitiesAndEntityMentions)
//      throw new AnalyticException(
//          "Empty entities are disallowed and no entities were produced for communication: "
//              + comm.getId());
//    else
    root.addToEntityMentionSetList(ems);
    root.addToEntitySetList(es);

    try {
      return new CachedTokenizationCommunication(root);
    } catch (MiscommunicationException e) {
      throw new AnalyticException(e);
    }
  }

  private void validateTokenRefSeqValidity(final TokenRefSequence trs, final Tokenization owner) throws AnalyticException {
    String uuidStr = owner.getUuid().getUuidString();
    List<Integer> trsIntList = trs.getTokenIndexList();
    Set<Integer> tkzIntSet = owner.getTokenList().getTokenList().stream()
        .map(tk -> tk.getTokenIndex())
        .collect(Collectors.toSet());

    LOGGER.debug("Set tokens: {}", tkzIntSet.toString());
    LOGGER.debug("TRS tokens: {}", trsIntList.toString());

    if (!tkzIntSet.containsAll(trsIntList)) {
      LOGGER.error("The produced TokenRefSequence for Tokenization {} is invalid.", uuidStr);
      LOGGER.error("The token indices do not align.");
      LOGGER.error("Tokenization indices: {}", tkzIntSet.toString());
      LOGGER.error("TokenRefSeq indices: {}", trsIntList.toString());
      throw new AnalyticException("TokenRefSequence tokens are not a subset of Tokenization tokens for Tokenization: " + uuidStr);
    }
  }

  private Entity makeEntity(CorefChain chain, EntityMentionSet ems, List<Tokenization> tokenizations) throws AnalyticException {
    Entity concEntity = new Entity().setUuid(this.gen.next());
    CorefChain.CorefMention coreHeadMention = chain.getRepresentativeMention();
    // CoreNLP uses 1-based indexing for the sentences
    // just subtract 1.
    Tokenization tkz = tokenizations.get(coreHeadMention.sentNum - 1);
    UUID tkzUuid = tkz.getUuid();
    LOGGER.debug("Creating EntityMention based on tokenization: {}", tkzUuid.getUuidString());
    EntityMention concHeadMention = makeEntityMention(coreHeadMention, tkzUuid, true);
    TokenRefSequence trs = concHeadMention.getTokens();

    // TODO: below throws if they're invalid. maybe this can be removed in the future.
    this.validateTokenRefSeqValidity(trs, tkz);

    concEntity.setCanonicalName(coreHeadMention.mentionSpan);
    concEntity.addToMentionIdList(concHeadMention.getUuid());
    ems.addToMentionList(concHeadMention);
    for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
      if (mention == coreHeadMention)
        continue;
      // CoreNLP uses 1-based indexing for the sentences
      // we'll just subtract one.
      Tokenization localTkz = tokenizations.get(mention.sentNum - 1);
      EntityMention concMention = this.makeEntityMention(mention, localTkz.getUuid(), false);
      TokenRefSequence localTrs = concMention.getTokens();
      this.validateTokenRefSeqValidity(localTrs, localTkz);

      ems.addToMentionList(concMention);
      concEntity.addToMentionIdList(concMention.getUuid());
    }
    return concEntity;
  }

  private EntityMention makeEntityMention(CorefChain.CorefMention coreMention, UUID tokenizationUuid, boolean representative) throws AnalyticException {
    EntityMention concEntityMention = new EntityMention().setUuid(this.gen.next());
    TokenRefSequence trs = extractTokenRefSequence(coreMention, tokenizationUuid, representative);
    concEntityMention.setTokens(trs);
    concEntityMention.setText(coreMention.mentionSpan);
    // TODO: we could possibly add mention types. We could use a feature of
    // CoreNLP:
    // MentionType mentionType = coreMention.mentionType;
    // or we could use a heuristic (see concrete-agiga).
    // String emType = getEntityMentionType(em, tokenization);
    return concEntityMention;
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
    int start = coreMention.startIndex - 1;
    int end   = coreMention.endIndex - 1;
    LOGGER.debug("Working on mention string: {}", coreMention.mentionSpan);
    int head = coreMention.headIndex - 1;
    if (end - start < 0) {
      throw new AnalyticException(
          "Calling extractTokenRefSequence on mention " + coreMention
              + " with head = " + head + ", UUID = " + tokUuid);
    } else if (end == start) {
      TokenRefSequence tb = new TokenRefSequence();
      tb.setTokenizationId(tokUuid).setTokenIndexList(new ArrayList<Integer>());
      if (representative)
        tb.setAnchorTokenIndex(head);

      LOGGER.warn("Creating an EMPTY mention for mention {}, UUID {}", coreMention, tokUuid);
      return tb;
    }
    return extractTokenRefSequence(start, end, head, tokUuid);
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
    LOGGER.debug("Working on TRS: {}", uuid.getUuidString());
    tb.setTokenizationId(uuid);

    for (int tid = left; tid < right; tid++) {
      LOGGER.debug("Appending TRS index: {}", tid);
      tb.addToTokenIndexList(tid);
      if (head != null && head == tid)
        tb.setAnchorTokenIndex(tid);

    }
    return tb;
  }
}
