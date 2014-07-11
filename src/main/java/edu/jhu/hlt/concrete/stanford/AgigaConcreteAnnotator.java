package edu.jhu.hlt.concrete.stanford;

import java.util.AbstractMap.SimpleEntry;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.agiga.AgigaCoref;
import edu.jhu.agiga.AgigaDocument;
import edu.jhu.agiga.AgigaSentence;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.SentenceSegmentation;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.agiga.AgigaConverter;
import edu.jhu.hlt.concrete.util.ConcreteUUIDFactory;

/**
 * given a Communication (with Sections and Sentences added) and Stanford's annotations via an AgigaDocument, add these annotations and return a new
 * Communication
 */
public class AgigaConcreteAnnotator {
  
  private static final Logger logger = LoggerFactory.getLogger(AgigaConcreteAnnotator.class);
  private final ConcreteUUIDFactory idFactory = new ConcreteUUIDFactory();
  // Since we're converting from raw (pretokenized) text, we don't want
  // the agiga converter to add TextSpan fields.
  private final AgigaConverter ag = new AgigaConverter(false);

  public AnnotationMetadata metadata() {
    return new AnnotationMetadata()
      .setTool("anno-pipeline-v2")
      .setTimestamp(System.currentTimeMillis() / 1000);
  }

  public SimpleEntry<EntityMentionSet, EntitySet> convertCoref(Communication in, AgigaDocument agigaDoc, List<Tokenization> tokenizations) {
    EntityMentionSet ems = new EntityMentionSet()
      .setUuid(this.idFactory.getConcreteUUID())
      .setMetadata(metadata());
    List<Entity> elist = new LinkedList<Entity>();
    for (AgigaCoref coref : agigaDoc.getCorefs()) {
      if (!coref.getMentions().isEmpty()) {
        Entity e = this.ag.convertCoref(ems, coref, agigaDoc, tokenizations);
        elist.add(e);
      } else {
        logger.warn("There were not any mentions for coref: " + coref.toString());
      }
    }
    
    EntitySet es = new EntitySet()
      .setUuid(this.idFactory.getConcreteUUID())
      .setMetadata(metadata())
      .setEntityList(elist);
    
    return new SimpleEntry<EntityMentionSet, EntitySet>(ems, es);
  }

  public void convertSection(Section section, AgigaDocument agigaDoc, List<Tokenization> tokenizations) {
    SentenceSegmentation ss = createSentenceSegmentation(section, agigaDoc, tokenizations, 0, 0);
    section.addToSentenceSegmentation(ss);
  }

  public SentenceSegmentation createSentenceSegmentation(Section in, AgigaDocument ad, List<Tokenization> tokenizations, int sentOffset, int charOffset) {
    logger.debug("f3");
    SentenceSegmentation ss = new SentenceSegmentation().setUuid(this.idFactory.getConcreteUUID()).setMetadata(metadata());
    ss.sectionId = in.getUuid();
    addSentences(ss, ad, tokenizations);
    return ss;
  }

  // add all Sentences
  private void addSentences(SentenceSegmentation in, AgigaDocument ad, List<Tokenization> tokenizations) {
    logger.debug("f4");
    final int n = ad.getSents().size();
    int sentPtr = 0;
    assert n > 0 : "n=" + n;
    for (int i = 0; i < n; i++) {
        AgigaSentence asent = ad.getSents().get(sentPtr++);
        //the second argument is the estimated character provenance offset. 
        //We're not filling the optional textSpan fields, so the exact parameter
        //value doesn't matter.
        Sentence st = this.ag.convertSentence(asent, 0, tokenizations);
        String sentText = this.ag.flattenText(asent);
        logger.debug(sentText);
        in.addToSentenceList(st);
    }
  }
}
