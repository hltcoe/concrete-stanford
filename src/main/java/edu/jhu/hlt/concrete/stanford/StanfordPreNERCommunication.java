/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.depparsed.DependencyParsedCommunication;
import edu.jhu.hlt.concrete.miscommunication.lemma.LemmatizedCommunication;
import edu.jhu.hlt.concrete.miscommunication.ne.NamedEntityTaggedCommunication;
import edu.jhu.hlt.concrete.miscommunication.pos.PartOfSpeechTaggedCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;

/**
 * Class representing a typed {@link Communication} that is produced by the Stanford pipeline
 * when run on either Communications with {@link Section}s and no {@link Sentence}s, or Communications
 * with Sections and Sentences but no {@link Tokenization}s.
 */
public class StanfordPreNERCommunication implements TokenizedCommunication,
    DependencyParsedCommunication, NamedEntityTaggedCommunication, PartOfSpeechTaggedCommunication, LemmatizedCommunication {

  private final CachedTokenizationCommunication ctc;

  private final List<TokenTagging> nerTTList;
  private final List<TokenTagging> lemmaTTList;
  private final List<TokenTagging> posTTList;
  private final List<DependencyParse> depParseList;

  /**
   *
   */
  StanfordPreNERCommunication(final Communication c) throws MiscommunicationException {
    this.ctc = new CachedTokenizationCommunication(c);
    final List<TokenTagging> ttList = new ArrayList<TokenTagging>();
    List<Tokenization> tkzList = this.ctc.getTokenizations();
    tkzList.stream().filter(tkz -> tkz.isSetTokenTaggingList())
        .forEach(tkz -> ttList.addAll(tkz.getTokenTaggingList()));

    this.posTTList = ttList.stream()
        .filter(tt -> tt.getTaggingType().equalsIgnoreCase("POS"))
        .collect(Collectors.toList());

    this.nerTTList = ttList.stream()
        .filter(tt -> tt.getTaggingType().equalsIgnoreCase("NER"))
        .collect(Collectors.toList());

    this.lemmaTTList = ttList.stream()
        .filter(tt -> tt.getTaggingType().equalsIgnoreCase("lemma"))
        .collect(Collectors.toList());

    this.depParseList = new ArrayList<>();
    this.ctc.getTokenizations().stream()
        .filter(tkz -> tkz.isSetDependencyParseList())
        .forEach(tkz -> this.depParseList.addAll(tkz.getDependencyParseList()));
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.sentenced.SentencedCommunication#getSentences()
   */
  @Override
  public List<Sentence> getSentences() {
    return this.ctc.getSentences();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.sectioned.SectionedCommunication#getSections()
   */
  @Override
  public List<Section> getSections() {
    return this.ctc.getSections();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.WrappedCommunication#getRoot()
   */
  @Override
  public Communication getRoot() {
    return this.ctc.getRoot();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.pos.PartOfSpeechTaggedCommunication#getPOSTaggings()
   */
  @Override
  public List<TokenTagging> getPOSTaggings() {
    return new ArrayList<>(this.posTTList);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.ne.NamedEntityTaggedCommunication#getNETaggings()
   */
  @Override
  public List<TokenTagging> getNETaggings() {
    return new ArrayList<>(this.nerTTList);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.depparsed.DependencyParsedCommunication#getDependencyParses()
   */
  @Override
  public List<DependencyParse> getDependencyParses() {
    return new ArrayList<>(this.depParseList);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication#getTokenizations()
   */
  @Override
  public List<Tokenization> getTokenizations() {
    return this.ctc.getTokenizations();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.lemma.LemmatizedCommunication#getLemmaTaggings()
   */
  @Override
  public List<TokenTagging> getLemmaTaggings() {
    return new ArrayList<>(this.lemmaTTList);
  }
}
