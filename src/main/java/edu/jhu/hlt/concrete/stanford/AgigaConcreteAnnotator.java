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
import edu.jhu.agiga.AgigaToken;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TheoryDependencies;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.agiga.AgigaConverter;
import edu.jhu.hlt.concrete.util.ConcreteUUIDFactory;
import edu.jhu.hlt.concrete.validation.ValidatableTextSpan;

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

  public AgigaConcreteAnnotator(boolean setSpans, String language) throws IOException {
    this.agigaProps = new ConcreteAgigaProperties();
    this.csProps = new ConcreteStanfordProperties();
    
    ag = new AgigaConverter(setSpans, this.csProps.getAllowEmptyMentions(), language);
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
    if(agigaDoc.getCorefs().size() == 0) {
      logger.warn("There were no coref chains found");
    }
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
                             StringBuilder sb, boolean preserveTokenTaggings) throws AnnotationException {
    if(section.isSetSentenceList()) {
      this.convertSentences(section, agigaDoc, charOffset, sb, preserveTokenTaggings);
    } else {
      this.addSentences(section, agigaDoc, charOffset, sb, preserveTokenTaggings);
    }
    sb.append("\n\n");
  }

  // add all Sentences
  private void addSentences(Section in, AgigaDocument ad, int charOffset,
                            StringBuilder sb, boolean preserveTokenTaggings)
    throws AnnotationException {
    final int n = ad.getSents().size();
    logger.debug("Adding " + n + " sentences to section " + in.getUuid());
    int sentPtr = 0;
    int currOffset = charOffset;
    assert n > 0 : "n=" + n;
    for (int i = 0; i < n; i++) {
      AgigaSentence asent = ad.getSents().get(sentPtr++);
      // the second argument is the estimated character provenance offset.
      Sentence st = this.ag.convertSentence(asent, currOffset, preserveTokenTaggings);
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

  // add all Sentences
  private void convertSentences(Section in, AgigaDocument ad, int currOffset,
                                StringBuilder sb, boolean preserveTokenTaggings)
    throws AnnotationException {
    logger.debug("Section has : " + in.getSentenceList().size() + " sentences");
    // for(AgigaSentence as : ad.getSents()) {
    //   logger.debug("new AgigaSentence");
    //   for(AgigaToken at : as.getTokens()) {
    //     logger.debug("AgigaToken: " + at.getWord() + ": " + at.getCharOffBegin() + " -> " + at.getCharOffEnd());
    //   }
    // }
    logger.debug("convertSentences for " + in.getUuid() );
    final int n = ad.getSents().size();
    List<Sentence> concreteSentences = in.getSentenceList();
    assert n > 0 : "n=" + n;
    for (int i = 0; i < n; i++) {
      Sentence concSent = concreteSentences.get(i);
      AgigaSentence asent = ad.getSents().get(i);

      Tokenization tokenization = this.ag.convertTokenization(asent, currOffset, preserveTokenTaggings);
      concSent.setTokenization(tokenization);

      if (currOffset < 0)
        throw new AnnotationException("bad character offset of " + currOffset
                                      + " for converting sent " + asent);

      String sentText = this.ag.flattenText(asent);

      // NOTE: as a work-around for previous design limitations, we store the
      // **original** offsets in the AgigaToken char begin/end values.
      // This means that we need to compute the actual offsets on-the-fly.
      TextSpan sentTS = new TextSpan(currOffset, currOffset + sentText.length());
      boolean isValidSentTS = new ValidatableTextSpan(sentTS).isValid();
      if (!isValidSentTS)
        throw new AnnotationException("TextSpan was not valid: " + sentTS.toString());
      concSent.setTextSpan(sentTS);

      // NOTE: as a work-around for previous design limitations, we store the
      // **original** offsets in the AgigaToken char begin/end values.
      AgigaToken firstToken = asent.getTokens().get(0);
      AgigaToken lastToken = asent.getTokens().get(asent.getTokens().size() - 1);
      TextSpan compTS = new TextSpan(firstToken.getCharOffBegin(), lastToken.getCharOffEnd());
      boolean isValidCompTS = new ValidatableTextSpan(compTS).isValid();
      if (!isValidCompTS)
        throw new AnnotationException("Computed TextSpan was not valid: " + compTS.toString());
      concSent.setRawTextSpan(compTS);
      logger.debug("Setting section raw text span to : " + compTS);
      // and finally, add the sentence text to the string builder
      sb.append(sentText);
      currOffset += sentText.length();
      if ((i + 1) < n) {
        sb.append("\n");
        currOffset++;
      }
      logger.debug(sentText);

    }
  }
}
