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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.asphalt.AsphaltException;
import edu.jhu.hlt.ballast.InvalidInputException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.ThriftIO;

/**
 * @author fferraro
 * 
 */
public class StanfordAgigaPipe_AFP0623Test {

  private static final Logger logger = LoggerFactory.getLogger(StanfordAgigaPipe_AFP0623Test.class);

  Communication testComm;

  static String afp0623Text = "" + "Protest over arrest of Sri Lanka reporter linked to Fonseka"
      + "\nSri Lankan media groups Thursday protested against the arrest of a reporter\n"
      + "close to Sarath Fonseka, the detained ex-army chief who tried to unseat the\n" + "president in recent elections.\n"
      + "\nThe groups issued a joint statement demanding the release of Ruwan Weerakoon, a\n"
      + "reporter with the Nation newspaper, who was arrested this week.\n"
      + "\n\"We request the Inspector General of Police to disclose the reasons behind the\n"
      + "arrest and detention of Ruwan Weerakoon and make arrangements for him to receive\n" + "legal aid immediately,\" the statement added.\n"
      + "\nWeerakoon maintained close contact with Fonseka when the general led the\n"
      + "military during the final phase of last year's war against Tamil Tiger rebels.\n"
      + "\nFonseka was an ally of President Mahinda Rajapakse when the rebel Liberation\n"
      + "Tigers of Tamil Eelam (LTTE) were crushed in May, but the two men later fell out\n" + "and contested the presidency in January's elections.\n"
      + "\nFonseka was arrested soon after losing the poll and appeared in front of a court\n" + "martial this week. The case was adjourned.\n"
      + "\nLocal and international rights groups have accused Rajapakse of cracking down on\n" + "dissent, a charge the government has denied.\n";
  
  final StanfordAgigaPipe pipe = new StanfordAgigaPipe();
  Communication afp0623Comm;
  Communication processedComm;

  static {
    try {
      afp0623Comm = ThriftIO.readFile("src/test/resources/AFP_ENG_20100318.0623.concrete");
      processedComm = pipe.process(afp0623Comm);
      new SuperCommunication(processedComm).writeToFile("src/test/resources/AFP_ENG_20100318.0623_processed.concrete", true);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
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
  public void testAFP0623_textCharByChar() throws TException, InvalidInputException, IOException, ConcreteException {
    String mine = StanfordAgigaPipe_AFP0623Test.afp0623Text;
    String theirs = StanfordAgigaPipe_AFP0623Test.processedComm.getText();
    int iterlen = mine.length() < theirs.length() ? mine.length() : theirs.length();
    for (int i = 0; i < iterlen; i++) {
      assertTrue(
          i + " : me => <" + mine.substring(i, i + 1) + ">, them => <" + theirs.substring(i, i + 1) + "> ::: "
              + (mine.substring(i, i + 1).equals(theirs.substring(i, i + 1))), mine.substring(i, i + 1).equals(theirs.substring(i, i + 1)));
    }
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
  public void testAFP0623_textLen() throws TException, InvalidInputException, IOException, ConcreteException {
    String mine = StanfordAgigaPipe_AFP0623Test.afp0623Text;
    String theirs = StanfordAgigaPipe_AFP0623Test.processedComm.getText();
    assertTrue(mine.length() + " vs. " + theirs.length(), mine.length() == theirs.length());
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
  public void testAFP0623_text() throws TException, InvalidInputException, IOException, ConcreteException {
    String mine = StanfordAgigaPipe_AFP0623Test.afp0623Text;
    String theirs = StanfordAgigaPipe_AFP0623Test.processedComm.getText();
    assertTrue(mine.equals(theirs));
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
  public void testAFP0623_numSects() throws TException, InvalidInputException, IOException, ConcreteException {
    List<Section> nsects = StanfordAgigaPipe_AFP0623Test.processedComm.getSectionSegmentationList().get(0).getSectionList();
    assertTrue("Found " + nsects.size() + " sections; should have found 8", nsects.size() == 8);
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
  public void testAFP0623_numSentsTotal() throws TException, InvalidInputException, IOException, ConcreteException {
    List<Section> nsects = StanfordAgigaPipe_AFP0623Test.processedComm.getSectionSegmentationList().get(0).getSectionList();
    int numSents = 0;
    for (Section sect : nsects) {
      if (sect.getSentenceSegmentationList() != null && sect.getSentenceSegmentationList().get(0) != null) {
        numSents += sect.getSentenceSegmentationList().get(0).getSentenceList().size();
      }
    }
    assertTrue("Found " + numSents + " processed sentences; should have found 8", numSents == 8);
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
  public void testAFP0623_firstSentSpan() throws TException, InvalidInputException, IOException, ConcreteException {
    int begin = 60;
    int end = 242;
    Sentence sent = StanfordAgigaPipe_AFP0623Test.processedComm.getSectionSegmentationList().get(0).getSectionList().get(1).getSentenceSegmentationList()
        .get(0).getSentenceList().get(0);
    TextSpan tts = sent.getTextSpan();
    assertTrue("start should be " + begin + ", but is " + tts.getStart(), begin == tts.getStart());
    assertTrue("end should be " + end + ", but is " + tts.getEnding(), end == tts.getEnding());
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
  public void testAFP0623_sentText() throws TException, InvalidInputException, IOException, ConcreteException {
    String[] sentences = {
        "Sri Lankan media groups Thursday protested against the arrest of a reporter\n"
            + "close to Sarath Fonseka, the detained ex-army chief who tried to unseat the\n" + "president in recent elections.",
        "The groups issued a joint statement demanding the release of Ruwan Weerakoon, a\n" + "reporter with the Nation newspaper, who was arrested this week.",
        "\"We request the Inspector General of Police to disclose the reasons behind the\n"
            + "arrest and detention of Ruwan Weerakoon and make arrangements for him to receive\n" + "legal aid immediately,\" the statement added.",
        "Weerakoon maintained close contact with Fonseka when the general led the\n"
            + "military during the final phase of last year's war against Tamil Tiger rebels.",
        "Fonseka was an ally of President Mahinda Rajapakse when the rebel Liberation\n"
            + "Tigers of Tamil Eelam (LTTE) were crushed in May, but the two men later fell out\n" + "and contested the presidency in January's elections.",
        "Fonseka was arrested soon after losing the poll and appeared in front of a court\n" + "martial this week.", "The case was adjourned.",
        "Local and international rights groups have accused Rajapakse of cracking down on\n" + "dissent, a charge the government has denied." };
    int sentIdx = 0;
    for (Section sect : StanfordAgigaPipe_AFP0623Test.processedComm.getSectionSegmentationList().get(0).getSectionList()) {
      if (sect.getSentenceSegmentationList() != null) {
        for (Sentence sent : sect.getSentenceSegmentationList().get(0).getSentenceList()) {
          TextSpan tts = sent.getTextSpan();
          String grabbed = StanfordAgigaPipe_AFP0623Test.afp0623Text.substring(tts.getStart(), tts.getEnding()).trim();
          // System.out.println("SentId = " + sentIdx + ", grabbing [[" + grabbed + "]], should be looking at <<" + sentences[sentIdx] + ">> .... " +
          // grabbed.equals(sentences[sentIdx]));

          assertTrue("SentId = " + sentIdx + ", grabbing [[" + grabbed + "]], should be looking at <<" + sentences[sentIdx] + ">>",
              grabbed.equals(sentences[sentIdx]));
          sentIdx++;
        }
      }
    }
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
  public void testAFP0623_verifyTokensToFull() throws TException, InvalidInputException, IOException, ConcreteException {
    String docText = StanfordAgigaPipe_AFP0623Test.processedComm.getText();
    int numEq = 0;
    int numTot = 0;
    for (Section nsect : StanfordAgigaPipe_AFP0623Test.processedComm.getSectionSegmentationList().get(0).getSectionList()) {
      if (nsect.getSentenceSegmentationList() == null)
        continue;
      for (Sentence nsent : nsect.getSentenceSegmentationList().get(0).getSentenceList()) {
        for (Token token : nsent.getTokenizationList().get(0).getTokenList().getTokenList()) {
          TextSpan tts = token.getTextSpan();
          String substr = docText.substring(tts.getStart(), tts.getEnding());
          boolean areEq = token.getText().equals(substr);
          if (!areEq) {
            logger.warn("expected = [" + token.getText() + "];" + "docText(" + tts + ") = [" + substr + "]");
          } else {
            numEq++;
          }
          numTot++;
        }
      }
    }
    double fracPassing = ((double) numEq / (double) numTot);
    assertTrue("WARNING: only " + fracPassing + "% of tokens matched!", fracPassing >= 0.8);
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
  public void testAFP0623_verifyThreeDepParses() throws TException, InvalidInputException, IOException, ConcreteException {
    String docText = StanfordAgigaPipe_AFP0623Test.processedComm.getText();
    int expNumDepParses = 3;
    for (Section nsect : StanfordAgigaPipe_AFP0623Test.processedComm.getSectionSegmentationList().get(0).getSectionList()) {
      if (nsect.getSentenceSegmentationList() == null)
        continue;
      for (Sentence nsent : nsect.getSentenceSegmentationList().get(0).getSentenceList()) {
        Tokenization tokenization = nsent.getTokenizationList().get(0);
        assertTrue(tokenization.getDependencyParseList().size() == expNumDepParses);
      }
    }
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
  public void testAFP0623_verifyNonEmptyDepParses() throws TException, InvalidInputException, IOException, ConcreteException {
    String docText = StanfordAgigaPipe_AFP0623Test.processedComm.getText();
    for (Section nsect : StanfordAgigaPipe_AFP0623Test.processedComm.getSectionSegmentationList().get(0).getSectionList()) {
      if (nsect.getSentenceSegmentationList() == null)
        continue;
      for (Sentence nsent : nsect.getSentenceSegmentationList().get(0).getSentenceList()) {
        Tokenization tokenization = nsent.getTokenizationList().get(0);
        for (DependencyParse depParse : tokenization.getDependencyParseList()) {
          assertTrue("DependencyParse " + depParse.getMetadata().getTool() + " is empty", depParse.getDependencyList().size() > 0);
        }
      }
    }
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}. This verifies that there's at least
   * one entity with a canonical name set.
   * 
   * @throws TException
   * @throws AsphaltException
   * @throws InvalidInputException
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void testAFP0623_verifySomeCanonicalNames() throws TException, InvalidInputException, IOException, ConcreteException {
    Communication comm = StanfordAgigaPipe_AFP0623Test.processedComm;
    assertTrue(comm.getEntitySetList().size() > 0);
    assertTrue(comm.getEntitySetList().get(0).getEntityList().size() > 0);
    boolean atLeastOne = false;
    for (Entity entity : comm.getEntitySetList().get(0).getEntityList()) {
      atLeastOne |= (entity.getCanonicalName() != null && entity.getCanonicalName().length() > 0);
    }
    assertTrue(atLeastOne);
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}. This verifies that there's at least
   * one entity with a canonical name set.
   * 
   * @throws TException
   * @throws AsphaltException
   * @throws InvalidInputException
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void testAFP0623_verifyAllCanonicalNames() throws TException, InvalidInputException, IOException, ConcreteException {
    Communication comm = StanfordAgigaPipe_AFP0623Test.processedComm;
    assertTrue(comm.getEntitySetList().size() > 0);
    assertTrue(comm.getEntitySetList().get(0).getEntityList().size() > 0);
    boolean allSet = true;
    for (Entity entity : comm.getEntitySetList().get(0).getEntityList()) {
      allSet &= (entity.getCanonicalName() != null && entity.getCanonicalName().length() > 0);
    }
    assertTrue(allSet);
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}. This verifies that all mentions have
   * an anchor token.
   * 
   * @throws TException
   * @throws AsphaltException
   * @throws InvalidInputException
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void testAFP0623_verifyMentionAnchors() throws TException, InvalidInputException, IOException, ConcreteException {
    Communication comm = StanfordAgigaPipe_AFP0623Test.processedComm;
    int numWithout = 0;
    int total = 0;
    for (EntityMention em : comm.getEntityMentionSetList().get(0).getMentionList()) {
      numWithout += (em.getTokens().anchorTokenIndex >= 0 ? 0 : 1);
      // logger.info("In memory, token head via member" + em.getTokenList().anchorTokenIndex);
      // logger.info("In memory, token head via function " + em.getTokenList().getAnchorTokenIndex());
      total++;
    }
    assertTrue("There were " + numWithout + " entity mentions out of " + total + "with no anchor token set", numWithout == 0);
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}. This verifies that all mentions have
   * an anchor token.
   * 
   * @throws TException
   * @throws AsphaltException
   * @throws InvalidInputException
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void testAFP0623_verifyMentionAnchorsFromSerialized() throws TException, InvalidInputException, IOException, ConcreteException {
    Communication comm = ThriftIO.readFile("src/test/resources/AFP_ENG_20100318.0623_processed.concrete");
    int numWithout = 0;
    int total = 0;
    for (EntityMention em : comm.getEntityMentionSetList().get(0).getMentionList()) {
      numWithout += (em.getTokens().anchorTokenIndex >= 0 ? 0 : 1);
      // logger.info("Serialized, token head via member" + em.getTokenList().anchorTokenIndex);
      // logger.info("Serialized, token head via function" + em.getTokenList().getAnchorTokenIndex());
      total++;
    }
    assertTrue("There were " + numWithout + " entity mentions out of " + total + "with no anchor token set", numWithout == 0);
  }
}
