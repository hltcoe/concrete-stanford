/*
 * 
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.util.data.ConcreteFactory;
import edu.jhu.hlt.asphalt.AsphaltException;
import edu.jhu.hlt.ballast.InvalidInputException;
import edu.jhu.hlt.ballast.tools.SingleSectionSegmenter;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.SectionSegmentation;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.ConcreteUUIDFactory;

/**
 * @author max
 * @author fferraro
 */
public class StanfordAgigaPipeTest {

  private static final Logger logger = LoggerFactory.getLogger(StanfordAgigaPipeTest.class);
  private static final String shakeHandText = "The man ran to shake the U.S. \nPresident's hand. ";

  ConcreteUUIDFactory cuf = new ConcreteUUIDFactory();
  ConcreteFactory cf = new ConcreteFactory();

  Communication testComm;
  
  StanfordAgigaPipe pipe;
  AnnotationMetadata md;

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    this.md = new AnnotationMetadata().setConfidence(1.0d).setTool("concrete stanford unit tests").setTimestamp(System.currentTimeMillis());

    this.pipe = new StanfordAgigaPipe();

    Communication c = new ConcreteFactory().randomCommunication();
    this.testComm = new SingleSectionSegmenter().annotate(c);
    // new Serialization().fromBytes(new Communication(), Files.readAllBytes(p));


  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * 
   * @throws TException
   * @throws AsphaltException
   * @throws InvalidInputException
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void processNonPassages() throws TException, InvalidInputException, IOException, ConcreteException {
    SuperCommunication sc = new SuperCommunication(this.testComm);
    assertTrue(sc.hasSectionSegmentations());
    assertTrue(sc.hasSections());

    Communication nc = this.pipe.process(this.testComm);
    logger.info("this testcomm = {}", this.testComm.getText());
    assertTrue(nc.isSetEntityMentionSetList());
    assertTrue(nc.isSetEntitySetList());
    new SuperCommunication(nc).writeToFile("src/test/resources/post-stanford.concrete", true);
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * 
   * @throws TException
   * @throws AsphaltException
   * @throws InvalidInputException
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void testNoMentions() throws TException, InvalidInputException, IOException, ConcreteException {
    Communication c = new ConcreteFactory().randomCommunication().setText("gobljsfoewj");
    Communication c1 = new SingleSectionSegmenter().annotate(c);
    SuperCommunication sc = new SuperCommunication(c1);
    assertTrue(sc.hasSectionSegmentations());
    assertTrue(sc.hasSections());

    Communication nc = this.pipe.process(c1);
    System.out.println("this testcomm = " + this.testComm.getText());
    assertTrue(nc.isSetEntityMentionSetList());
    assertTrue(nc.isSetEntitySetList());
    new SuperCommunication(nc).writeToFile("src/test/resources/post-stanford_garbage_processed.concrete", true);
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * 
   * @throws TException
   * @throws AsphaltException
   * @throws InvalidInputException
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void processHandshakeCommunication() throws TException, InvalidInputException, IOException, ConcreteException {
    Communication shakeHandComm = this.cf.randomCommunication().setText(shakeHandText);
    Section section = new Section().setUuid(cuf.getConcreteUUID()).setTextSpan(new TextSpan().setStart(0).setEnding(shakeHandText.length())).setKind("Passage");
    SectionSegmentation ss = new SectionSegmentation().setUuid(cuf.getConcreteUUID());
    ss.addToSectionList(section);
    shakeHandComm.addToSectionSegmentationList(ss);

    Communication processedShakeHandComm = pipe.process(shakeHandComm);    
    final String docText = processedShakeHandComm.getText();
    final String[] stokens = { "The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "." };
    
    assertTrue(docText.equals(StanfordAgigaPipeTest.shakeHandText));
    
    Section nsect = processedShakeHandComm.getSectionSegmentationList().get(0).getSectionList().get(0);
    List<Sentence> nSentList = nsect.getSentenceSegmentationList().get(0).getSentenceList();
    assertTrue(nSentList.size() == 1);
    
    // Test spans
    nsect = processedShakeHandComm.getSectionSegmentationList().get(0).getSectionList().get(0);
    nSentList = nsect.getSentenceSegmentationList().get(0).getSentenceList();
    assertTrue(nSentList.size() == 1);
    Sentence nsent = nSentList.get(0);
    assertTrue("Beginning char should be 0, but is " + nsent.getTextSpan().getStart(), nsent.getTextSpan().getStart() == 0);
    assertTrue("Ending char should be 48, but is " + nsent.getTextSpan().getEnding(), nsent.getTextSpan().getEnding() == 48);
    
    nsect = processedShakeHandComm.getSectionSegmentationList().get(0).getSectionList().get(0);
    nsent = nsect.getSentenceSegmentationList().get(0).getSentenceList().get(0);
    TextSpan tts = nsent.getTextSpan();
    String pulledText = docText.substring(tts.getStart(), tts.getEnding());
    assertTrue(pulledText.equals(shakeHandText.trim()));
    
    // Test # Tokens
    nsect = processedShakeHandComm.getSectionSegmentationList().get(0).getSectionList().get(0);
    nSentList = nsect.getSentenceSegmentationList().get(0).getSentenceList();
    nsent = nSentList.get(0);
    Tokenization ntokenization = nsent.getTokenizationList().get(0);
    
    StringBuilder actualTokensSB = new StringBuilder();
    for (Token tok : ntokenization.getTokenList().getTokenList()) {
      actualTokensSB.append("(" + tok.text + ", " + tok.tokenIndex + ") ");
    }
    assertTrue("Expected tokens length = " + stokens.length + ";" + "Actual   tokens length = " + ntokenization.getTokenList().getTokenList().size() + "; "
        + "Actual tokens = " + actualTokensSB.toString(), ntokenization.getTokenList().getTokenList().size() == stokens.length);
    
    // Verify tokens
    nsect = processedShakeHandComm.getSectionSegmentationList().get(0).getSectionList().get(0);
    nSentList = nsect.getSentenceSegmentationList().get(0).getSentenceList();
    nsent = nSentList.get(0);
    ntokenization = nsent.getTokenizationList().get(0);
    
    int tokIdx = 0;
    for (Token token : ntokenization.getTokenList().getTokenList()) {
      assertTrue("tokIdx = " + tokIdx + "; token.tokenIndex = " + token.tokenIndex, token.tokenIndex == tokIdx);
      assertTrue("expected = [" + stokens[tokIdx] + "]; token.text = [" + token.text + "]", token.text.equals(stokens[tokIdx]));
      tokIdx++;
    }
    
    // Verify tokens to full
    nsect = processedShakeHandComm.getSectionSegmentationList().get(0).getSectionList().get(0);
    nSentList = nsect.getSentenceSegmentationList().get(0).getSentenceList();
    nsent = nSentList.get(0);
    ntokenization = nsent.getTokenizationList().get(0);
    for (Token token : ntokenization.getTokenList().getTokenList()) {
      tts = token.getTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + token.getText() + "];" + "docText(" + tts + ") = [" + substr + "]", token.getText().equals(substr));
    }
    
    // Verify tokens to full seeded
    nsect = processedShakeHandComm.getSectionSegmentationList().get(0).getSectionList().get(0);
    nSentList = nsect.getSentenceSegmentationList().get(0).getSentenceList();
    nsent = nSentList.get(0);
    ntokenization = nsent.getTokenizationList().get(0);
    tokIdx = 0;
    for (Token token : ntokenization.getTokenList().getTokenList()) {
      tts = token.getTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + stokens[tokIdx] + "];" + "docText(" + tts + ") = [" + substr + "]", stokens[tokIdx].equals(substr));
      tokIdx++;
    }
    
    // Verify token spans
    nsect = processedShakeHandComm.getSectionSegmentationList().get(0).getSectionList().get(0);
    nSentList = nsect.getSentenceSegmentationList().get(0).getSentenceList();
    nsent = nSentList.get(0);
    ntokenization = nsent.getTokenizationList().get(0);
    int[] start = { 0, 4, 8, 12, 15, 21, 25, 31, 40, 43, 47 };
    int[] end = { 3, 7, 11, 14, 20, 24, 29, 40, 42, 47, 48 };
    tokIdx = 0;
    for (Token token : ntokenization.getTokenList().getTokenList()) {
      tts = token.getTextSpan();
      assertTrue(token.text + "(" + tokIdx + ") starts at " + tts.getStart() + "; it should start at " + start[tokIdx], tts.getStart() == start[tokIdx]);
      assertTrue(token.text + "(" + tokIdx + ") starts at " + tts.getEnding() + "; it should start at " + end[tokIdx], tts.getEnding() == end[tokIdx]);
      tokIdx++;
    }
    
    // Verify # entities
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    assertEquals("Should be three entities.", 3,
        processedShakeHandComm.getEntitySetList().get(0).getEntityList().size());
    
    // Verify # Singleton entities
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0).getEntityList()) {
      assertEquals(entity.getCanonicalName() + " is not singleton", 1, entity.getMentionIdList().size());
    }
    
    // Verify entity names
    Set<String> expEnts = new HashSet<String>();
    expEnts.add("The man");
    expEnts.add("the U.S. President 's hand");
    expEnts.add("the U.S. President 's");
    Set<String> seenEnts = new HashSet<String>();
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0).getEntityList()) {
      seenEnts.add(entity.getCanonicalName());
    }
    assertTrue(seenEnts.equals(expEnts));
    
    // Verify some canonical entity names
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    boolean atLeastOne = false;
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0).getEntityList()) {
      atLeastOne |= (entity.getCanonicalName() != null && entity.getCanonicalName().length() > 0);
    }
    assertTrue(atLeastOne);
  }



  // @Test
  // public void processBadMessage() throws Exception {
  // Communication c = new Communication();
  // c.id = "10505_corpus_x";
  // c.uuid = UUID.randomUUID().toString();
  // c.type = CommunicationType.BLOG;
  // c.text = "Hello world! Testing this out.";
  // SectionSegmentation ss = new SingleSectionSegmenter().annotateDiff(c);
  // c.addToSectionSegmentations(ss);
  //
  // Communication nc = this.pipe.process(c);
  // Serialization.toBytes(nc);
  // assertTrue(nc.isSetEntityMentionSets());
  // assertTrue(nc.isSetEntitySets());
  // }
}
