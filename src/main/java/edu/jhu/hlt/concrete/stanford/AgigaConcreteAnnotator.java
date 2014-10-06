package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.agiga.util.ConcreteAgigaProperties;
import concrete.tools.AnnotationException;
import edu.jhu.agiga.AgigaCoref;
import edu.jhu.agiga.AgigaDocument;
import edu.jhu.agiga.AgigaSentence;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TheoryDependencies;
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
  private final ConcreteAgigaProperties agigaProps;
  private final ConcreteStanfordProperties csProps;
  private final AgigaConverter ag;

  // public AgigaConcreteAnnotator(boolean setSpans) throws IOException {
  //   ag = new AgigaConverter(setSpans, false);
  //   csProps = new ConcreteStanfordProperties();
  //   ag.setToolName(csProps.getToolName());
  // }

  public AgigaConcreteAnnotator(boolean setSpans) throws IOException {
    this.agigaProps = new ConcreteAgigaProperties();
    this.csProps = new ConcreteStanfordProperties();
    
    ag = new AgigaConverter(setSpans, this.csProps.getAllowEmptyMentions());
  }

  public AnnotationMetadata metadata(String name) {
    return new AnnotationMetadata().setTool(name).setTimestamp(System.currentTimeMillis() / 1000);
  }

  public SimpleEntry<EntityMentionSet, EntitySet> convertCoref(Communication in, AgigaDocument agigaDoc, List<Tokenization> tokenizations)
    throws AnnotationException {
    EntityMentionSet ems = new EntityMentionSet().setUuid(this.idFactory.getConcreteUUID());
    TheoryDependencies td = new TheoryDependencies();
    for (Tokenization t : tokenizations)
      td.addToTokenizationTheoryList(t.getUuid());
    AnnotationMetadata md = this.metadata(this.agigaProps.getCorefToolName()).setDependencies(td);
    ems.setMetadata(md);

    List<Entity> elist = new ArrayList<Entity>();
    for (AgigaCoref coref : agigaDoc.getCorefs()) {
      if (!coref.getMentions().isEmpty()) {
        Entity e = this.ag.convertCoref(ems, coref, agigaDoc, tokenizations);
        elist.add(e);
      } else
        logger.warn("There were not any mentions for coref: " + coref.toString());

    }

    if (!ems.isSetMentionList())
      ems.setMentionList(new ArrayList<EntityMention>());

    EntitySet es = new EntitySet().setUuid(this.idFactory.getConcreteUUID()).setMetadata(md).setEntityList(elist);

    return new SimpleEntry<EntityMentionSet, EntitySet>(ems, es);
  }

  public void convertSection(Section section, AgigaDocument agigaDoc, int charOffset,
      StringBuilder sb) throws AnnotationException {
    this.addSentences(section, agigaDoc, charOffset, sb);
    sb.append("\n\n");
  }

  // add all Sentences
  private void addSentences(Section in, AgigaDocument ad, int charOffset, StringBuilder sb)
      throws AnnotationException {
    logger.debug("f4");
    final int n = ad.getSents().size();
    int sentPtr = 0;
    int currOffset = charOffset;
    assert n > 0 : "n=" + n;
    for (int i = 0; i < n; i++) {
      AgigaSentence asent = ad.getSents().get(sentPtr++);
      // the second argument is the estimated character provenance offset.
      // We're not filling the optional textSpan fields, so the exact parameter
      // value doesn't matter.
      Sentence st = this.ag.convertSentence(asent, currOffset, true);
      String sentText = this.ag.flattenText(asent);
      sb.append(sentText);
      currOffset += sentText.length();
      if ((i + 1) < n) {
        sb.append("\n");
        currOffset++;
      }
      logger.debug(sentText);
      in.addToSentenceList(st);
    }
  }
}
