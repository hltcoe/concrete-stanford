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
  private final AgigaConverter ag = new AgigaConverter();

  public AnnotationMetadata metadata() {
    return new AnnotationMetadata()
      .setTool("anno-pipeline-v2")
      .setTimestamp(System.currentTimeMillis() / 1000);
  }

  public void convertSection(Section section, AgigaDocument agigaDoc, List<Tokenization> tokenizations) {
    SentenceSegmentation ss = addSentenceSegmentation(section, agigaDoc, tokenizations);
    section.addToSentenceSegmentation(ss);
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

  // add SentenceSegmentation to the section
  public SentenceSegmentation addSentenceSegmentation(Section in, AgigaDocument ad, List<Tokenization> tokenizations) {
    logger.debug("f3");
    // create a sentence segmentation
    SentenceSegmentation ss = new SentenceSegmentation().setUuid(this.idFactory.getConcreteUUID()).setMetadata(metadata());
    ss.sectionId = in.getUuid();
    addSentences(ss, ad, tokenizations);
    // in.addToSentenceSegmentation(ss);
    return ss;
  }

  // add all Sentences
  private void addSentences(SentenceSegmentation in, AgigaDocument ad, List<Tokenization> tokenizations) {
    logger.debug("f4");
    int n = ad.getSents().size();
    int charOffset = 0;
    int sentPtr = 0;
    assert n > 0 : "n=" + n;
    for (int i = 0; i < n; i++) {
        AgigaSentence asent = ad.getSents().get(sentPtr++);
        Sentence st = this.ag.convertSentence(asent, charOffset, tokenizations);
        String sentText = this.ag.flattenText(asent);
        // String docText = AgigaConverter.flattenText(ad);
        //logger.debug(sentText);
        int l = sentText.length();
        int endingOffset;
        if(l == 0) {
            logger.error("sentence " + (sentPtr - 1) + " has 0 length!");
            endingOffset = 0;
        } else {
            endingOffset = sentText.charAt(l-1) == '\n' ? 1 : 0;
        }
        //logger.debug(docText.substring(charOffset, charOffset + l + endingOffset));
        charOffset += sentText.length() + endingOffset;
        in.addToSentenceList(st);
    }
  }
}
