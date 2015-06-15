/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

  private final StanfordPreNERCommunication preNER;

  private final EntityMentionSet ems;
  private final EntitySet es;

  private final List<EntityMention> emList = new ArrayList<>();
  private final List<Entity> entityList = new ArrayList<>();

  /**
   *
   */
  StanfordPostNERCommunication(final Communication c) throws MiscommunicationException {
    this.preNER = new StanfordPreNERCommunication(c);
    Optional<EntityMentionSet> stanfordEMS = c.getEntityMentionSetList().stream()
        .filter(ems -> ems.getMetadata().getTool().contains("Stanford"))
        .findAny();
    if (!stanfordEMS.isPresent())
      throw new MiscommunicationException("No Stanford EntityMentionSet was found in this communication [ID: " + c.getId() + "]");
    this.ems = stanfordEMS.get();

    Optional<EntitySet> stanfordES = c.getEntitySetList().stream()
        .filter(ems -> ems.getMetadata().getTool().contains("Stanford"))
        .findAny();
    if (!stanfordES.isPresent())
      throw new MiscommunicationException("No Stanford EntitySet was found in this communication [ID: " + c.getId() + "]");
    this.es = stanfordES.get();

    c.getEntityMentionSetList().forEach(ems -> this.emList.addAll(ems.getMentionList()));
    c.getEntitySetList().forEach(es -> this.entityList.addAll(es.getEntityList()));
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
    return new ArrayList<>(this.emList);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.miscommunication.entitied.EntitiedCommunication#getEntities()
   */
  @Override
  public List<Entity> getEntities() {
    return new ArrayList<>(this.entityList);
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

  /**
   * @return the pointer to the {@link EntityMentionSet} produced by Stanford. This is a pointer: changes made to this object
   * will be reflected in the object.
   */
  public EntityMentionSet getStanfordEntityMentionSet() {
    return this.ems;
  }

  /**
   * @return the pointer to the {@link EntitySet} produced by Stanford. This is a pointer: changes made to this object
   * will be reflected in the object.
   */
  public EntitySet getStanfordEntitySet() {
    return this.es;
  }
}
