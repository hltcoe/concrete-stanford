/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.TokenizationKind;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.metadata.AnnotationMetadataFactory;
import edu.jhu.hlt.concrete.sentence.SentenceFactory;
import edu.jhu.hlt.concrete.spans.TextSpanFactory;
import edu.jhu.hlt.concrete.tokenization.TokenTaggingFactory;
import edu.jhu.hlt.concrete.tokenization.TokenizationFactory;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentenceIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

/**
 *
 */
public class CoreMapWrapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(CoreMapWrapper.class);

  private final int idx;
  private final int startOffset;
  private final int endOffset;

  private final int tokenBeginOffset;
  private final int tokenEndOffset;

  private final String text;

  private final List<CoreLabel> clList;

  // "lazily" computed.
  private Sentence st;

  private final AnalyticUUIDGenerator gen;

  /**
   *
   */
  public CoreMapWrapper(final CoreMap cm, final AnalyticUUIDGenerator gen) {
    this.text = cm.get(TextAnnotation.class);
    this.idx = cm.get(SentenceIndexAnnotation.class);

    this.startOffset = cm.get(CharacterOffsetBeginAnnotation.class);
    this.endOffset = cm.get(CharacterOffsetEndAnnotation.class);

    this.tokenBeginOffset = cm.get(TokenBeginAnnotation.class);
    this.tokenEndOffset = cm.get(TokenEndAnnotation.class);
    this.clList = cm.get(TokensAnnotation.class);
    LOGGER.trace("CoreLabel list has {} elements.", clList.size());
    this.gen = gen;
  }

  /**
   * @return the idx
   */
  public int getIndex() {
    return idx;
  }

  /**
   * @return the startOffset
   */
  public int getStartOffset() {
    return startOffset;
  }

  /**
   * @return the endOffset
   */
  public int getEndOffset() {
    return endOffset;
  }

  /**
   * @return the tokenBeginOffset
   */
  public int getTokenBeginOffset() {
    return tokenBeginOffset;
  }

  /**
   * @return the tokenEndOffset
   */
  public int getTokenEndOffset() {
    return tokenEndOffset;
  }

  /**
   * @return the text
   */
  public String getText() {
    return text;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "CoreMapWrapper [idx=" + idx + ", startOffset=" + startOffset + ", endOffset=" + endOffset + ", tokenBeginOffset=" + tokenBeginOffset
        + ", tokenEndOffset=" + tokenEndOffset + ", text=" + text + "]";
  }

  public Sentence toSentence() throws AnalyticException {
    return this.toSentence(0);
  }

  public Sentence toSentence(final int charOffset, final Sentence orig) throws AnalyticException {
    if (this.st != null)
      return st;

    // could probably be cleaned up
    Tokenization updatedLocalTkz = this.coreLabelToTokenization(charOffset, orig.getTokenization());
    orig.setTokenization(updatedLocalTkz);
    this.st = orig;
    return orig;
  }

  public Sentence toSentence(final int charOffset) throws AnalyticException {
    // If previously computed, return.
    if (this.st != null)
      return st;

    Sentence st = new SentenceFactory(this.gen).create();
    Integer bi = this.startOffset;
    LOGGER.debug("Current char offset: {}", charOffset);
    LOGGER.debug("Stanford sentence start offset: {}", bi);
    Integer ei = this.endOffset;
    LOGGER.debug("Stanford sentence end offset: {}", ei);
    LOGGER.trace("Stanford sentence idx: {}", this.idx);
    LOGGER.trace("Stanford sentence token begin: {}", this.tokenBeginOffset);
    LOGGER.trace("Stanford sentence token end: {}", this.tokenEndOffset);

    if (bi == null || ei == null)
      throw new AnalyticException("Unable to create a textspan from CoreMap: either the begin or end index was null.");
    TextSpan ts = TextSpanFactory.withOffset(bi, ei, charOffset);
    st.setTextSpan(ts);
    try {
      Tokenization tkz = this.coreLabelToTokenization(charOffset);
      st.setTokenization(tkz);
      if (!st.isSetTokenization())
        // TODO: needed?
        LOGGER.warn("Tokenization isn't set for sentence: {}", st.getUuid().getUuidString());

      // Cache for future use.
      this.st = st;
      return st;
    } catch (ConcreteException e) {
      throw new AnalyticException(e);
    }
  }

  private StanfordToConcreteConversionOutput convertCoreLabels(final int cOffset) throws AnalyticException {
    TokenTagging nerTT = new TokenTaggingFactory(this.gen).create("NER").setMetadata(AnnotationMetadataFactory.fromCurrentLocalTime().setTool("Stanford CoreNLP"));
    TokenTagging posTT = new TokenTaggingFactory(this.gen).create("POS").setMetadata(AnnotationMetadataFactory.fromCurrentLocalTime().setTool("Stanford CoreNLP"));
    TokenTagging lemmaTT = new TokenTaggingFactory(this.gen).create("LEMMA").setMetadata(AnnotationMetadataFactory.fromCurrentLocalTime().setTool("Stanford CoreNLP"));

    List<Token> tokList = new ArrayList<>(this.clList.size());
    for (CoreLabel cl : this.clList) {
      final Set<Class<?>> keySet = cl.keySet();
      Token t;
      if (keySet.contains(PartOfSpeechAnnotation.class)) {
        PreNERCoreLabelWrapper wrapper = new PreNERCoreLabelWrapper(cl);
        t = wrapper.getOrig().toConcreteToken(cOffset);
        wrapper.toPOSToken().ifPresent(tt -> posTT.addToTaggedTokenList(tt));
        wrapper.toNERToken().ifPresent(tt -> nerTT.addToTaggedTokenList(tt));
        wrapper.toLemmaToken().ifPresent(tt -> lemmaTT.addToTaggedTokenList(tt));
      } else {
        LOGGER.trace("Preparing to wrap CoreLabel: {}", cl.toShorterString(new String[0]));
        TokenizedCoreLabelWrapper wrapper = new TokenizedCoreLabelWrapper(cl);
        t = wrapper.toConcreteToken(cOffset);
      }

      tokList.add(t);
    }

    // this is literally just a 4-tuple
    // to make other things cleaner
    return new StanfordToConcreteConversionOutput(tokList, nerTT, posTT, lemmaTT);
  }

  private static void addToTokenTaggingListIfNotEmpty(TokenTagging tt, Tokenization tkz) {
    if (tt.isSetTaggedTokenList() && tt.getTaggedTokenListSize() > 0)
      tkz.addToTokenTaggingList(tt);
  }

  private Tokenization coreLabelToTokenization(final int cOffset, final Tokenization orig) throws AnalyticException {
    StanfordToConcreteConversionOutput output = this.convertCoreLabels(cOffset);
    List<Token> outputTL = output.getTokenList();
    List<Token> origTokenList = orig.getTokenList().getTokenList();
    if (origTokenList.isEmpty())
      origTokenList.addAll(outputTL);

    // if the "previous" tokenization had tokens,
    // make sure they equal the new ones
    else if (!origTokenList.equals(outputTL)) {
      LOGGER.error("Token lists did not match.");
      LOGGER.error("Original tokens: ");
      origTokenList.forEach(t -> LOGGER.error("{}", t));
      LOGGER.error("New tokens: ");
      outputTL.forEach(t -> LOGGER.error("{}", t));
      throw new AnalyticException("Token lists did not match.");
    }

    addToTokenTaggingListIfNotEmpty(output.getNerTT(), orig);
    addToTokenTaggingListIfNotEmpty(output.getPosTT(), orig);
    addToTokenTaggingListIfNotEmpty(output.getLemmaTT(), orig);

    return orig;
  }

  private Tokenization coreLabelToTokenization(int cOffset) throws AnalyticException, ConcreteException {
    Tokenization tkz = new TokenizationFactory(this.gen).create();
    tkz.setKind(TokenizationKind.TOKEN_LIST);
    List<Token> tlist = new ArrayList<>();
    tkz.setTokenList(new TokenList(tlist));
    AnnotationMetadata md = AnnotationMetadataFactory.fromCurrentLocalTime().setTool("Stanford CoreNLP PTB");
    tkz.setMetadata(md);
    return this.coreLabelToTokenization(cOffset, tkz);
  }
}
