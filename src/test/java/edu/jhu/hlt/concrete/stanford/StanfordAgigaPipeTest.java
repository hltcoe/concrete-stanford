/*
 * 
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertTrue;

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
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.SectionSegmentation;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.ConcreteUUIDFactory;

/**
 * @author max
 *
 */
public class StanfordAgigaPipeTest {

  private static final Logger logger = LoggerFactory.getLogger(StanfordAgigaPipeTest.class);

  String dataPath ="src/test/resources/test-out-v.0.1.2.concrete";

  Communication testComm;

  static Set<String> runOverThese = new HashSet<>();
  static {
      runOverThese.add("Other");
      runOverThese.add("Passage");
  }
  static final StanfordAgigaPipe pipe = new StanfordAgigaPipe(runOverThese);
  static final Communication shakeHandComm = new Communication().setId("sample_communication")
      .setType("article");
  static Communication processedShakeHandComm;
  static final String shakeHandText = "The man ran to shake the U.S. \nPresident's hand. ";
  static void createShakeHandComm() throws Exception{
      ConcreteUUIDFactory cuf = new ConcreteUUIDFactory();
      shakeHandComm.setText(shakeHandText).setUuid(cuf.getConcreteUUID());
      Section section = new Section().setUuid(cuf.getConcreteUUID())
          .setTextSpan(new TextSpan().setStart(0).setEnding(shakeHandText.length()))
          .setKind("Passage");
      SectionSegmentation ss = new SectionSegmentation().setUuid(cuf.getConcreteUUID());
      ss.addToSectionList(section);
      shakeHandComm.addToSectionSegmentations(ss);
      StanfordAgigaPipeTest.processedShakeHandComm = pipe.process(shakeHandComm);
  }
  static {
        try {
            createShakeHandComm();
        } catch(Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

  
  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
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
    
    Communication nc = StanfordAgigaPipeTest.pipe.process(this.testComm);
    assertTrue(nc.isSetEntityMentionSets());
    assertTrue(nc.isSetEntitySets());
    new SuperCommunication(nc).writeToFile("src/test/resources/post-stanford.concrete", true);
  }
  
  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
  @Test
  public void testShake1_text() throws TException, InvalidInputException, IOException, ConcreteException {
      assertTrue(StanfordAgigaPipeTest.processedShakeHandComm.getText().equals(StanfordAgigaPipeTest.shakeHandText));
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
  @Test
  public void testShake1_numSents() throws TException, InvalidInputException, IOException, ConcreteException {
      Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
      List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
      assertTrue(nSentList.size() == 1);
  }


  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
  @Test
  public void testShake1_sentOffsets() throws TException, InvalidInputException, IOException, ConcreteException {
      Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
      List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
      assertTrue(nSentList.size() == 1);
      Sentence nsent = nSentList.get(0);
      assertTrue("Beginning char should be 0, but is " + nsent.getTextSpan().getStart(),
                 nsent.getTextSpan().getStart() == 0);
      assertTrue("Ending char should be 48, but is " + nsent.getTextSpan().getEnding(), 
                 nsent.getTextSpan().getEnding() == 48);
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
  @Test
  public void testShake1_sentText() throws TException, InvalidInputException, IOException, ConcreteException {
      Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
      Sentence nsent = nsect.getSentenceSegmentation().get(0).getSentenceList().get(0);
      TextSpan tts = nsent.getTextSpan();
      String pulledText = StanfordAgigaPipeTest.processedShakeHandComm.getText()
          .substring(tts.getStart(), tts.getEnding());
      assertTrue(pulledText.equals(StanfordAgigaPipeTest.shakeHandText.trim()));
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
  @Test
  public void testShake1_numTokens() throws TException, InvalidInputException, IOException, ConcreteException {
      Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
      List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
      Sentence nsent = nSentList.get(0);
      Tokenization ntokenization = nsent.getTokenizationList().get(0);
      String[] stokens = {"The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "."};
      StringBuilder actualTokensSB = new StringBuilder();
      for(Token tok : ntokenization.getTokenList().getTokens()){
          actualTokensSB.append("("+tok.text+", " + tok.tokenIndex+") ");
      }
      assertTrue("Expected tokens length = " + stokens.length + ";" + 
                 "Actual   tokens length = " + ntokenization.getTokenList().getTokens().size()+ "; " + 
                 "Actual tokens = " + actualTokensSB.toString(), 
                 ntokenization.getTokenList().getTokens().size() == stokens.length);
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
  @Test
  public void testShake1_verifyTokens() throws TException, InvalidInputException, IOException, ConcreteException {
      Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
      List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
      Sentence nsent = nSentList.get(0);
      Tokenization ntokenization = nsent.getTokenizationList().get(0);
      String[] stokens = {"The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "."};
      String docText = StanfordAgigaPipeTest.processedShakeHandComm.getText();
      int tokIdx = 0;
      for(Token token : ntokenization.getTokenList().getTokens()){
          assertTrue("tokIdx = " + tokIdx + "; token.tokenIndex = " + token.tokenIndex,
                     token.tokenIndex == tokIdx);
          assertTrue("expected = [" + stokens[tokIdx] +
                     "]; token.text = [" + token.text + "]",
                     token.text.equals(stokens[tokIdx]));
          tokIdx++;
      }
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
  @Test
  public void testShake1_verifyTokensToFull() throws TException, InvalidInputException, IOException, ConcreteException {
      Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
      List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
      Sentence nsent = nSentList.get(0);
      Tokenization ntokenization = nsent.getTokenizationList().get(0);
      String[] stokens = {"The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "."};
      String docText = StanfordAgigaPipeTest.processedShakeHandComm.getText();
      int tokIdx = 0;
      for(Token token : ntokenization.getTokenList().getTokens()){
          StringBuilder sb = new StringBuilder();
          TextSpan tts = token.getTextSpan();
          String substr = docText.substring(tts.getStart(), tts.getEnding());
          assertTrue("expected = ["+ stokens[tokIdx] + "];" +
                     "docText("+ tts +") = [" + substr + "]",
                     stokens[tokIdx].equals(substr));
          tokIdx++;
      }
  }


/**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
  @Test
  public void testShake1_verifyTokenSpans() throws TException, InvalidInputException, IOException, ConcreteException {
      Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
      List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
      Sentence nsent = nSentList.get(0);
      Tokenization ntokenization = nsent.getTokenizationList().get(0);
      String[] stokens = {"The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "."};
      int[] start = {0, 4,  8, 12, 15, 21, 25, 31, 40, 43, 47};
      int[] end   = {3, 7, 11, 14, 20, 24, 29, 40, 42, 47, 48};
      int tokIdx = 0;
      for(Token token : ntokenization.getTokenList().getTokens()){
          TextSpan tts = token.getTextSpan();
          assertTrue(token.text + "(" + tokIdx +") starts at " + tts.getStart() +"; it should start at " +start[tokIdx], 
                     tts.getStart() == start[tokIdx]);
          assertTrue(token.text + "(" + tokIdx +") starts at " + tts.getEnding() +"; it should start at " +end[tokIdx], 
                     tts.getEnding() == end[tokIdx]);
          tokIdx++;
      }
  }


  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
    //@Test
  // public void processSample() throws TException, InvalidInputException, IOException, ConcreteException {
  //     ConcreteUUIDFactory cuf = new ConcreteUUIDFactory();
  //     String text = "The man ran quickly toward the front line to shake the U.S. \n" +
  //         "President's hand. The man, Mr. Foo Bar, shared many interests \n" + 
  //         "with the leader --- their favorite author was J.D. Salinger.\n " +
  //         "Another similarity was that blue was their favorite colour.";
  //     Communication sample = new Communication().setId("sample_communication")
  //         .setUuid(cuf.getConcreteUUID())
  //         .setType("article")
  //         .setText(text);
  //     Section section = new Section().setUuid(cuf.getConcreteUUID())
  //         .setTextSpan(new TextSpan().setStart(0).setEnding(text.length()))
  //         .setKind("Passage");
  //     SectionSegmentation ss = new SectionSegmentation().setUuid(cuf.getConcreteUUID());
  //     ss.addToSectionList(section);
  //     sample.addToSectionSegmentations(ss);
      
  //     Communication nc = this.pipe.process(sample);
  //     new SuperCommunication(nc).writeToFile("src/test/resources/sample_para_processed.concrete", true);
  // }

//  @Test
//  public void processBadMessage() throws Exception {
//    Communication c = new Communication();
//    c.id = "10505_corpus_x";
//    c.uuid = UUID.randomUUID().toString();
//    c.type = CommunicationType.BLOG;
//    c.text = "Hello world! Testing this out.";
//    SectionSegmentation ss = new SingleSectionSegmenter().annotateDiff(c);
//    c.addToSectionSegmentations(ss);
//    
//    Communication nc = this.pipe.process(c);
//    Serialization.toBytes(nc);
//    assertTrue(nc.isSetEntityMentionSets());
//    assertTrue(nc.isSetEntitySets());
//  }
}
