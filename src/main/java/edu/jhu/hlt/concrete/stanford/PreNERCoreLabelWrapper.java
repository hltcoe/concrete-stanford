/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford;

import java.util.Optional;

import edu.jhu.hlt.concrete.TaggedToken;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreLabel;

/**
 *
 */
public class PreNERCoreLabelWrapper {

  private final TokenizedCoreLabelWrapper orig;

  private final Optional<String> posTag;
  private final Optional<String> nerTag;
  private final Optional<String> lemmaTag;

  /**
   *
   */
  public PreNERCoreLabelWrapper(final CoreLabel cl) {
    this.orig = new TokenizedCoreLabelWrapper(cl);

    this.posTag = Optional.ofNullable(cl.get(PartOfSpeechAnnotation.class));
    this.nerTag = Optional.ofNullable(cl.get(NamedEntityTagAnnotation.class));
    this.lemmaTag = Optional.ofNullable(cl.get(LemmaAnnotation.class));
  }

  private TaggedToken toTaggedToken(final String tag) {
    TaggedToken tt = new TaggedToken();
    int idx = this.orig.getIndex() - 1;

    tt.setTokenIndex(idx);
    tt.setTag(tag);

    return tt;
  }

  public Optional<TaggedToken> toNERToken() {
    return this.nerTag.map(x -> this.toTaggedToken(x));
  }

  public Optional<TaggedToken> toLemmaToken() {
    return this.lemmaTag.map(x -> this.toTaggedToken(x));
  }

  public Optional<TaggedToken> toPOSToken() {
    return this.posTag.map(x -> this.toTaggedToken(x));
  }

  /**
   * @return the orig
   */
  public TokenizedCoreLabelWrapper getOrig() {
    return orig;
  }

  /**
   * @return the pos
   */
  public Optional<String> getPOSTag() {
    return posTag;
  }

  /**
   * @return the ner
   */
  public Optional<String> getNERTag() {
    return nerTag;
  }

  /**
   * @return the lemma
   */
  public Optional<String> getLemmaTag() {
    return lemmaTag;
  }
}
