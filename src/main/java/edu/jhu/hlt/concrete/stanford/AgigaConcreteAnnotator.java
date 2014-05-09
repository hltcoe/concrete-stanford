package edu.jhu.hlt.concrete.stanford;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

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
import edu.jhu.hlt.concrete.SectionSegmentation;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.SentenceSegmentation;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.agiga.AgigaConverter;
import edu.jhu.hlt.concrete.util.UUIDGenerator;

/**
 * given a Communication (with Sections and Sentences added) and Stanford's annotations via an AgigaDocument, add these annotations and return a new
 * Communication
 */
public class AgigaConcreteAnnotator {
  
  private static final Logger logger = LoggerFactory.getLogger(AgigaConcreteAnnotator.class);
  private long timestamp = System.currentTimeMillis() / 1000;

  public AgigaConcreteAnnotator() {
  }

  public AnnotationMetadata metadata() {
    return new AnnotationMetadata().setTool("anno-pipeline-v2").setTimestamp(timestamp);
  }

  // private Communication comm;
  private String sectionSegmentationId;
  private List<String> sectionIds;
  private AgigaDocument agigaDoc;
  // private int agigaSentPtr = -1;
  private int sectionPtr = -1;
  // need to reference this in building corefs
  private List<Tokenization> tokenizations;

  public synchronized void convertCommunication(Communication comm, String sectionSegmentationId, String sectionId, // relevant sections (look inside for
                                                                                                                    // #sentences)
      AgigaDocument agigaDoc) {

    if (sectionIds.size() == 0) {
      logger.debug("WARNING: calling annotate with no sections specified!");
    }

    logger.debug("[AgigaConcreteAnnotator debug]");
    logger.debug("sectionSegmentationId = " + sectionSegmentationId);
    
    this.timestamp = System.currentTimeMillis() / 1000;
    this.sectionSegmentationId = sectionSegmentationId;
    this.agigaDoc = agigaDoc;
    // this.agigaSentPtr = 0;
    this.sectionPtr = 0;
    this.tokenizations = new ArrayList<Tokenization>();
    flushCommunication(comm, false);
  }

  public synchronized void convertSection(Section section, AgigaDocument agigaDoc, List<Tokenization> tokenizations) {
    this.timestamp = System.currentTimeMillis() / 1000;
    SentenceSegmentation ss = addSentenceSegmentation(section, agigaDoc, tokenizations);
    section.addToSentenceSegmentation(ss);
  }

  public synchronized SimpleEntry<EntityMentionSet, EntitySet> convertCoref(Communication in, AgigaDocument agigaDoc, List<Tokenization> tokenizations) {
    EntityMentionSet ems = new EntityMentionSet()
      .setUuid(UUIDGenerator.make())
      .setMetadata(metadata());
    List<Entity> elist = new LinkedList<Entity>();
    for (AgigaCoref coref : agigaDoc.getCorefs()) {
      Entity e = AgigaConverter.convertCoref(ems, coref, agigaDoc, tokenizations);
      elist.add(e);
    }
    
    EntitySet es = new EntitySet()
      .setUuid(UUIDGenerator.make())
      .setMetadata(metadata())
      .setEntityList(elist);
    
    return new SimpleEntry<EntityMentionSet, EntitySet>(ems, es);
  }

  // get appropriate section segmentation, and change it
  private void flushCommunication(Communication in, boolean transferCorefs) {
    int remove = -1;
    int n = in.getSectionSegmentationsSize();
    for (int i = 0; i < n;) {
      SectionSegmentation ss = in.getSectionSegmentations().get(i);
      if (ss.getUuid().equals(this.sectionSegmentationId)) {
        remove = i;
        flushSections(ss);
      }
      break;
    }
    if (remove < 0)
      throw new RuntimeException("couldn't find SectionSegmentation with String=" + this.sectionSegmentationId);
    if (this.tokenizations.size() != this.agigaDoc.getSents().size()) {
      throw new RuntimeException("#agigaSents=" + agigaDoc.getSents().size() + ", #tokenizations=" + tokenizations.size());
    }
  }

  public void addCorefs(Communication in, List<Tokenization> tokenizations) {
    EntityMentionSet ems = new EntityMentionSet().setUuid(UUIDGenerator.make()).setMetadata(metadata());
    List<Entity> elist = new LinkedList<Entity>();
    for (AgigaCoref coref : this.agigaDoc.getCorefs()) {
      Entity e = AgigaConverter.convertCoref(ems, coref, this.agigaDoc, tokenizations);
      elist.add(e);
    }
    in.addToEntityMentionSets(ems);
    in.addToEntitySets(new EntitySet().setUuid(UUIDGenerator.make()).setMetadata(metadata()).setEntityList(elist));
  }

  // given a particular section segmentation, add information to appropriate sections
  private void flushSections(SectionSegmentation in) {
    int n = in.getSectionListSize();
    assert n > 0 : "n=" + n;

    // add them from source
    String target = this.sectionIds.get(this.sectionPtr);
    logger.debug("[f2] target=" + target);
    for (Section section : in.getSectionList()) {
      logger.debug("sectionPtr=%d sect.uuid=%s\n", sectionPtr, section.getUuid());
      if (section.getUuid().equals(target)) {
        addSentenceSegmentation(section);
        this.sectionPtr++;
        logger.debug("[f2] target=" + (this.sectionPtr < this.sectionIds.size() ? this.sectionIds.get(this.sectionPtr) : null));
      }
    }
    if (this.sectionPtr != this.sectionIds.size())
      throw new RuntimeException(String.format("found %d of %d sections", this.sectionPtr, this.sectionIds.size()));
  }

  // add SentenceSegmentation to the section
  public SentenceSegmentation addSentenceSegmentation(Section in, AgigaDocument ad, List<Tokenization> tokenizations) {
    logger.debug("f3");
    // create a sentence segmentation
    SentenceSegmentation ss = new SentenceSegmentation().setUuid(UUIDGenerator.make()).setMetadata(metadata());
    ss.sectionId = in.getUuid();
    addSentences(ss, ad, tokenizations);
    // in.addToSentenceSegmentation(ss);
    return ss;
  }

  private void addSentenceSegmentation(Section in) {
    addSentenceSegmentation(in, this.agigaDoc, this.tokenizations);
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
        Sentence st = AgigaConverter.convertSentence(asent, charOffset, tokenizations);
        String sentText = AgigaConverter.flattenText(asent);
        String docText = AgigaConverter.flattenText(ad);
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
