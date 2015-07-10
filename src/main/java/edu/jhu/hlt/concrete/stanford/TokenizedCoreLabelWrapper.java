/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.spans.TextSpanFactory;
import edu.stanford.nlp.ling.CoreAnnotations.AfterAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.BeforeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentenceIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ValueAnnotation;
import edu.stanford.nlp.ling.CoreLabel;

/**
 *
 */
public class TokenizedCoreLabelWrapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(TokenizedCoreLabelWrapper.class);

  private final String value;
  private final String text;
  private final String originalText;
  private final String before;
  private final String after;

  private final int idx;
  private final int sentenceIdx;
  private final int startSentenceOffset;
  private final int endSentenceOffset;

  private final Optional<Integer> startOffset;
  private final Optional<Integer> endOffset;

  /**
   *
   */
  public TokenizedCoreLabelWrapper(final CoreLabel cl) {
    this.value = cl.get(ValueAnnotation.class);
    this.text = cl.get(TextAnnotation.class);
    LOGGER.trace("Wrapping token text: {}", this.text);
    this.originalText = cl.get(OriginalTextAnnotation.class);
    this.before = cl.get(BeforeAnnotation.class);
    this.after = cl.get(AfterAnnotation.class);

    this.startSentenceOffset = cl.get(CharacterOffsetBeginAnnotation.class);
    this.endSentenceOffset = cl.get(CharacterOffsetEndAnnotation.class);

    this.startOffset = Optional.ofNullable(cl.get(TokenBeginAnnotation.class));
    this.endOffset = Optional.ofNullable(cl.get(TokenEndAnnotation.class));
    LOGGER.trace("TokenBegin: {}", this.startOffset);
    LOGGER.trace("TokenEnd: {}", this.endOffset);

    this.idx = cl.get(IndexAnnotation.class);
    this.sentenceIdx = cl.get(SentenceIndexAnnotation.class);
    LOGGER.trace("Got sentence idx: {}", this.sentenceIdx);
  }

  public Token toConcreteToken(int cOffset) throws AnalyticException {
    TextSpan ts = TextSpanFactory.withOffset(this.startSentenceOffset, this.endSentenceOffset, cOffset);
    LOGGER.debug("Creating concrete token text span: {}", ts);
    final int stanIdx = this.getIndex();
    final int concIndex = stanIdx - 1;
    if (concIndex < 0)
      throw new AnalyticException("The concrete token index was somehow less than 0. Original index: " + stanIdx);
    Token t = new Token(concIndex);
    t.setTextSpan(ts);
    // might be null (?)
    final String ttxt = this.text;
    LOGGER.debug("Setting Concrete token text: {}", ttxt);
    t.setText(ttxt);
    return t;
  }

  public String getValue() {
    return this.value;
  }

  public String getText() {
    return this.text;
  }

  public String getOriginalText() {
    return this.originalText;
  }

  public int getStartOffset() {
    return this.startSentenceOffset;
  }

  public int getEndOffset() {
    return this.endSentenceOffset;
  }

  public String getBeforeAnnotation() {
    return this.before;
  }

  public String getAfterAnnotation() {
    return this.after;
  }

  public int getIndex() {
    return this.idx;
  }

  public int getSentenceIndex() {
    return this.sentenceIdx;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("TokenizedCoreLabelWrapper [value=");
    builder.append(value);
    builder.append(", text=");
    builder.append(text);
    builder.append(", originalText=");
    builder.append(originalText);
    builder.append(", before=");
    builder.append(before);
    builder.append(", after=");
    builder.append(after);
    builder.append(", idx=");
    builder.append(idx);
    builder.append(", sentenceIdx=");
    builder.append(sentenceIdx);
    builder.append(", startSentenceOffset=");
    builder.append(startSentenceOffset);
    builder.append(", endSentenceOffset=");
    builder.append(endSentenceOffset);
    builder.append(", startOffset=");
    builder.append(startOffset);
    builder.append(", endOffset=");
    builder.append(endOffset);
    builder.append("]");
    return builder.toString();
  }
}
