package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.stanford.languages.PipelineLanguage;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;

public class NonPTBSpanishTextTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(NonPTBSpanishTextTest.class);

  public static final String text = "holis, bien o k, como se va? que tal es Maria?";

  public static Communication comm;

  @BeforeClass
  public static void setUp() throws Exception {
    AnalyticUUIDGeneratorFactory f = new AnalyticUUIDGeneratorFactory();
    AnalyticUUIDGenerator g = f.create();
    comm = new Communication()
        .setId("spa-1")
        .setUuid(g.next())
        .setType("news")
        .setText(text);
    AnnotationMetadata md = new AnnotationMetadata().setTool("concrete-stanford:test").setTimestamp(System.currentTimeMillis() / 1000);
    comm.setMetadata(md);
    Section section = new Section()
        .setUuid(UUIDFactory.newUUID())
        .setTextSpan(new TextSpan(0, text.length()))
        .setKind("Passage");
    comm.addToSectionList(section);
  }

  @Test
  public void test() throws Exception {
    TokenizedCommunication wDepParse = PipelineLanguage.SPANISH
        .getPreCorefAnalytic()
        .annotate(new Communication(comm));
    Tokenization ntkz = wDepParse.getTokenizations().get(0);
    List<TokenTagging> ttList = ntkz.getTokenTaggingList();
    LOGGER.info("token taggings: {}", ttList.size());
    ttList.forEach(tt -> LOGGER.debug("Got TokenTagging: {}", tt));
    List<TokenTagging> nerTTs = ttList.stream()
        .filter (tt -> tt.getTaggingType().equalsIgnoreCase("NER")).collect(Collectors.toList());
    assertEquals(1, nerTTs.size());

    // AnnotateTokenizedConcrete atc = new AnnotateTokenizedConcrete(PipelineLanguage.ENGLISH);
    // atc.annotateWithStanfordNlp(englishComm);
    assertTrue(ntkz.isSetParseList());
    assertEquals(1, ntkz.getParseListSize());

    Section sect = wDepParse.getSections().get(0);
    sect.unsetSentenceList();
    LOGGER.info("Got section: {}", sect);
    Sentence sent = wDepParse.getSentences().get(0);
    sent.unsetTokenization();
    LOGGER.info("Got sentence: {}", sent);
  }
}
