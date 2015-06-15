/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.depparsed.DependencyParsedCommunication;
import edu.jhu.hlt.concrete.miscommunication.entitied.EntitiedCommunication;
import edu.jhu.hlt.concrete.miscommunication.entitied.EntityMentionedCommunication;
import edu.jhu.hlt.concrete.miscommunication.lemma.LemmatizedCommunication;
import edu.jhu.hlt.concrete.miscommunication.ne.NamedEntityTaggedCommunication;
import edu.jhu.hlt.concrete.miscommunication.pos.PartOfSpeechTaggedCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;

/**
 * Class representing a typed {@link Communication} that is produced by the Stanford pipeline
 * when run on either Communications with {@link Section}s and no {@link Sentence}s, or Communications
 * with Sections and Sentences but no {@link Tokenization}s.
 */
public class StanfordPostNERCommunication implements TokenizedCommunication, EntitiedCommunication, EntityMentionedCommunication,
    DependencyParsedCommunication, NamedEntityTaggedCommunication, PartOfSpeechTaggedCommunication, LemmatizedCommunication {

  private static final Logger LOGGER = LoggerFactory.getLogger(StanfordPostNERCommunication.class);

  private final StanfordPreNERCommunication preNER;

  private final EntityMentionSet ems;
  private final EntitySet es;

  /**
   *
   */
  StanfordPostNERCommunication(final Communication c) throws MiscommunicationException {
    this.preNER = new StanfordPreNERCommunication(c);

    Iterator<EntityMentionSet> emsIter = c.getEntityMentionSetListIterator();
    this.ems = emsIter.next();
    if (emsIter.hasNext())
      LOGGER.info("Communication has >1 EntityMentionSet...");

    Iterator<EntitySet> esIter = c.getEntitySetListIterator();
    this.es = esIter.next();
    if (esIter.hasNext())
      LOGGER.info("Communication has >1 EntitySet...");
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.sentenced.SentencedCommunication#getSentences()
   */
  @Override
  public List<Sentence> getSentences() {
    return this.preNER.getSentences();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.sectioned.SectionedCommunication#getSections()
   */
  @Override
  public List<Section> getSections() {
    return this.preNER.getSections();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.WrappedCommunication#getRoot()
   */
  @Override
  public Communication getRoot() {
    return this.preNER.getRoot();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.pos.PartOfSpeechTaggedCommunication#getPOSTaggings()
   */
  @Override
  public List<TokenTagging> getPOSTaggings() {
    return this.preNER.getPOSTaggings();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.ne.NamedEntityTaggedCommunication#getNETaggings()
   */
  @Override
  public List<TokenTagging> getNETaggings() {
    return this.preNER.getNETaggings();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.depparsed.DependencyParsedCommunication#getDependencyParses()
   */
  @Override
  public List<DependencyParse> getDependencyParses() {
    return this.preNER.getDependencyParses();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.entitied.EntityMentionedCommunication#getEntityMentions()
   */
  @Override
  public List<EntityMention> getEntityMentions() {
    return new ArrayList<>(this.ems.getMentionList());
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.entitied.EntitiedCommunication#getEntities()
   */
  @Override
  public List<Entity> getEntities() {
    return new ArrayList<>(this.es.getEntityList());
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication#getTokenizations()
   */
  @Override
  public List<Tokenization> getTokenizations() {
    return this.preNER.getTokenizations();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.lemma.LemmatizedCommunication#getLemmaTaggings()
   */
  @Override
  public List<TokenTagging> getLemmaTaggings() {
    return this.preNER.getLemmaTaggings();
  }
}
