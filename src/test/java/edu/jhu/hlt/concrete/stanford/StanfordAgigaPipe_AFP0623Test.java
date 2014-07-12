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
import edu.jhu.hlt.concrete.util.ThriftIO;

/**
 * @author max
 *
 */
public class StanfordAgigaPipe_AFP0623Test {

  private static final Logger logger = LoggerFactory.getLogger(StanfordAgigaPipe_AFP0623Test.class);

  String dataPath ="src/test/resources/test-out-v.0.1.2.concrete";

  Communication testComm;

  static String afp0623Text = "" +
      "Protest over arrest of Sri Lanka reporter linked to Fonseka\n" + 
      "Sri Lankan media groups Thursday protested against the arrest of a reporter " +
      "close to Sarath Fonseka, the detained ex-army chief who tried to unseat the" +
      "president in recent elections.\n\n" +
      "The groups issued a joint statement demanding the release of Ruwan Weerakoon, a" + 
      "reporter with the Nation newspaper, who was arrested this week.\n\n" + 
      "\"We request the Inspector General of Police to disclose the reasons behind the" +
      "arrest and detention of Ruwan Weerakoon and make arrangements for him to receive" +
      "legal aid immediately,\" the statement added.\n\n" + 
      "Weerakoon maintained close contact with Fonseka when the general led the" + 
      "military during the final phase of last year's war against Tamil Tiger rebels.\n\n" + 
      "Fonseka was an ally of President Mahinda Rajapakse when the rebel Liberation" +
      "Tigers of Tamil Eelam (LTTE) were crushed in May, but the two men later fell out" + 
      "and contested the presidency in January's elections.\n\n" + 
      "Fonseka was arrested soon after losing the poll and appeared in front of a court" + 
      "martial this week. The case was adjourned.\n\n" + 
      "Local and international rights groups have accused Rajapakse of cracking down on" + 
      "dissent, a charge the government has denied.";
  static Set<String> runOverThese = new HashSet<>();
  static {
      runOverThese.add("Other");
      runOverThese.add("Passage");
  }
  static final StanfordAgigaPipe pipe = new StanfordAgigaPipe(runOverThese);
  static Communication afp0623Comm; 
  static Communication processedComm;

  static {
        try {
            afp0623Comm = ThriftIO.readFile("src/test/resources/AFP_ENG_20100318.0623.concrete");
            processedComm = pipe.process(afp0623Comm);
        } catch(Exception e){
            throw new RuntimeException(e.getMessage());
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
  public void testAFP0623_text() throws TException, InvalidInputException, IOException, ConcreteException {
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
  public void testAFP0623_numSects() throws TException, InvalidInputException, IOException, ConcreteException {
      List<Section> nsects = StanfordAgigaPipe_AFP0623Test.processedComm.getSectionSegmentations().get(0).getSectionList();
      assertTrue("Found " + nsects.size() + " sections; should have found 8", nsects.size() == 8);
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
  public void testAFP0623_numSentsTotal() throws TException, InvalidInputException, IOException, ConcreteException {
      List<Section> nsects = StanfordAgigaPipe_AFP0623Test.processedComm.getSectionSegmentations().get(0).getSectionList();
      int numSents = 0;
      for(Section sect : nsects){
          if(sect.getSentenceSegmentation() != null &&
             sect.getSentenceSegmentation().get(0) != null) {
              numSents += sect.getSentenceSegmentation().get(0).getSentenceList().size();
          }
      }
      assertTrue("Found " + numSents + " processed sentences; should have found 8",
                 numSents == 8);
  }


//   /**
//    * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
//    * @throws TException 
//    * @throws AsphaltException 
//    * @throws InvalidInputException 
//    * @throws ConcreteException 
//    * @throws IOException 
//    */
//   @Test
//   public void testAFP0623_sentOffsets() throws TException, InvalidInputException, IOException, ConcreteException {
//       Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
//       List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
//       assertTrue(nSentList.size() == 1);
//       Sentence nsent = nSentList.get(0);
//       assertTrue("Beginning char should be 0, but is " + nsent.getTextSpan().getStart(),
//                  nsent.getTextSpan().getStart() == 0);
//       assertTrue("Ending char should be 48, but is " + nsent.getTextSpan().getEnding(), 
//                  nsent.getTextSpan().getEnding() == 48);
//   }

//   /**
//    * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
//    * @throws TException 
//    * @throws AsphaltException 
//    * @throws InvalidInputException 
//    * @throws ConcreteException 
//    * @throws IOException 
//    */
//   @Test
//   public void testAFP0623_sentText() throws TException, InvalidInputException, IOException, ConcreteException {
//       Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
//       Sentence nsent = nsect.getSentenceSegmentation().get(0).getSentenceList().get(0);
//       TextSpan tts = nsent.getTextSpan();
//       String pulledText = StanfordAgigaPipeTest.processedShakeHandComm.getText()
//           .substring(tts.getStart(), tts.getEnding());
//       assertTrue(pulledText.equals(StanfordAgigaPipeTest.shakeHandText.trim()));
//   }

//   /**
//    * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
//    * @throws TException 
//    * @throws AsphaltException 
//    * @throws InvalidInputException 
//    * @throws ConcreteException 
//    * @throws IOException 
//    */
//   @Test
//   public void testAFP0623_numTokens() throws TException, InvalidInputException, IOException, ConcreteException {
//       Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
//       List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
//       Sentence nsent = nSentList.get(0);
//       Tokenization ntokenization = nsent.getTokenizationList().get(0);
//       String[] stokens = {"The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "."};
//       StringBuilder actualTokensSB = new StringBuilder();
//       for(Token tok : ntokenization.getTokenList().getTokens()){
//           actualTokensSB.append("("+tok.text+", " + tok.tokenIndex+") ");
//       }
//       assertTrue("Expected tokens length = " + stokens.length + ";" + 
//                  "Actual   tokens length = " + ntokenization.getTokenList().getTokens().size()+ "; " + 
//                  "Actual tokens = " + actualTokensSB.toString(), 
//                  ntokenization.getTokenList().getTokens().size() == stokens.length);
//   }

//   /**
//    * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
//    * @throws TException 
//    * @throws AsphaltException 
//    * @throws InvalidInputException 
//    * @throws ConcreteException 
//    * @throws IOException 
//    */
//   @Test
//   public void testAFP0623_verifyTokens() throws TException, InvalidInputException, IOException, ConcreteException {
//       Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
//       List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
//       Sentence nsent = nSentList.get(0);
//       Tokenization ntokenization = nsent.getTokenizationList().get(0);
//       String[] stokens = {"The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "."};
//       String docText = StanfordAgigaPipeTest.processedShakeHandComm.getText();
//       int tokIdx = 0;
//       for(Token token : ntokenization.getTokenList().getTokens()){
//           assertTrue("tokIdx = " + tokIdx + "; token.tokenIndex = " + token.tokenIndex,
//                      token.tokenIndex == tokIdx);
//           assertTrue("expected = [" + stokens[tokIdx] +
//                      "]; token.text = [" + token.text + "]",
//                      token.text.equals(stokens[tokIdx]));
//           tokIdx++;
//       }
//   }

//   /**
//    * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
//    * @throws TException 
//    * @throws AsphaltException 
//    * @throws InvalidInputException 
//    * @throws ConcreteException 
//    * @throws IOException 
//    */
//   @Test
//   public void testAFP0623_verifyTokensToFull() throws TException, InvalidInputException, IOException, ConcreteException {
//       Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
//       List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
//       Sentence nsent = nSentList.get(0);
//       Tokenization ntokenization = nsent.getTokenizationList().get(0);
//       String[] stokens = {"The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "."};
//       String docText = StanfordAgigaPipeTest.processedShakeHandComm.getText();
//       int tokIdx = 0;
//       for(Token token : ntokenization.getTokenList().getTokens()){
//           StringBuilder sb = new StringBuilder();
//           TextSpan tts = token.getTextSpan();
//           String substr = docText.substring(tts.getStart(), tts.getEnding());
//           assertTrue("expected = ["+ stokens[tokIdx] + "];" +
//                      "docText("+ tts +") = [" + substr + "]",
//                      stokens[tokIdx].equals(substr));
//           tokIdx++;
//       }
//   }


// /**
//    * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
//    * @throws TException 
//    * @throws AsphaltException 
//    * @throws InvalidInputException 
//    * @throws ConcreteException 
//    * @throws IOException 
//    */
//   @Test
//   public void testAFP0623_verifyTokenSpans() throws TException, InvalidInputException, IOException, ConcreteException {
//       Section nsect = StanfordAgigaPipeTest.processedShakeHandComm.getSectionSegmentations().get(0).getSectionList().get(0);
//       List<Sentence> nSentList = nsect.getSentenceSegmentation().get(0).getSentenceList();
//       Sentence nsent = nSentList.get(0);
//       Tokenization ntokenization = nsent.getTokenizationList().get(0);
//       String[] stokens = {"The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "."};
//       int[] start = {0, 4,  8, 12, 15, 21, 25, 31, 40, 43, 47};
//       int[] end   = {3, 7, 11, 14, 20, 24, 29, 40, 42, 47, 48};
//       int tokIdx = 0;
//       for(Token token : ntokenization.getTokenList().getTokens()){
//           TextSpan tts = token.getTextSpan();
//           assertTrue(token.text + "(" + tokIdx +") starts at " + tts.getStart() +"; it should start at " +start[tokIdx], 
//                      tts.getStart() == start[tokIdx]);
//           assertTrue(token.text + "(" + tokIdx +") starts at " + tts.getEnding() +"; it should start at " +end[tokIdx], 
//                      tts.getEnding() == end[tokIdx]);
//           tokIdx++;
//       }
//   }

}
