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

  /**
   *
   */
  public CoreMapWrapper(final CoreMap cm) {
    this.text = cm.get(TextAnnotation.class);
    this.idx = cm.get(SentenceIndexAnnotation.class);

    this.startOffset = cm.get(CharacterOffsetBeginAnnotation.class);
    this.endOffset = cm.get(CharacterOffsetEndAnnotation.class);

    this.tokenBeginOffset = cm.get(TokenBeginAnnotation.class);
    this.tokenEndOffset = cm.get(TokenEndAnnotation.class);
    this.clList = cm.get(TokensAnnotation.class);
    LOGGER.trace("CoreLabel list has {} elements.", clList.size());
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


  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "CoreMapWrapper [idx=" + idx + ", startOffset=" + startOffset + ", endOffset=" + endOffset
        + ", tokenBeginOffset=" + tokenBeginOffset + ", tokenEndOffset=" + tokenEndOffset + ", text=" + text + "]";
  }

  public Sentence toSentence() throws AnalyticException {
    return this.toSentence(0);
  }

  public Sentence toSentence(final int charOffset) throws AnalyticException {
    // If previously computed, return.
    if (this.st != null)
      return st;

    Sentence st = SentenceFactory.create();
    Integer bi = this.startOffset;
    LOGGER.trace("Stanford sentence start offset: {}", bi);
    Integer ei = this.endOffset;
    LOGGER.trace("Stanford sentence end offset: {}", ei);
    // Optional<Integer> stidx = Optional.ofNullable(cm.get(SentenceIndexAnnotation.class));
    LOGGER.trace("Stanford sentence idx: {}", this.idx);
    LOGGER.trace("Stanford sentence token begin: {}", this.tokenBeginOffset);
    LOGGER.trace("Stanford sentence token end: {}", this.tokenEndOffset);

    if (bi == null || ei == null)
      throw new AnalyticException("Unable to create a textspan from CoreMap: either the begin or end index was null.");
    TextSpan ts = TextSpanFactory.withOffset(bi, ei, charOffset);
    // TextSpan ts = new TextSpan(bi, ei);
    st.setTextSpan(ts);
    try {
      Tokenization tkz = this.coreLabelToTokenization(bi);
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

  private Tokenization coreLabelToTokenization(int cOffset) throws AnalyticException, ConcreteException {
    Tokenization tkz = TokenizationFactory.create();
    tkz.setKind(TokenizationKind.TOKEN_LIST);
    List<Token> cTokenList = new ArrayList<>();

    List<TokenTagging> tokTagList = new ArrayList<>();

    TokenTagging nerTT = TokenTaggingFactory.create("NER")
        .setMetadata(AnnotationMetadataFactory.fromCurrentLocalTime().setTool("Stanford CoreNLP"));
    TokenTagging posTT = TokenTaggingFactory.create("POS")
        .setMetadata(AnnotationMetadataFactory.fromCurrentLocalTime().setTool("Stanford CoreNLP"));
    TokenTagging lemmaTT = TokenTaggingFactory.create("LEMMA")
        .setMetadata(AnnotationMetadataFactory.fromCurrentLocalTime().setTool("Stanford CoreNLP"));

    for (CoreLabel cl : clList) {
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

      cTokenList.add(t);
    }

    tokTagList.add(nerTT);
    tokTagList.add(posTT);
    tokTagList.add(lemmaTT);
    tkz.setTokenTaggingList(tokTagList);
    TokenList tl = new TokenList(cTokenList);
    tkz.setTokenList(tl);
    AnnotationMetadata md = AnnotationMetadataFactory.fromCurrentLocalTime().setTool("Stanford CoreNLP PTB");
    tkz.setMetadata(md);
    return tkz;
  }
}
