/*
 * 
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;
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
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.SectionSegmentation;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.ConcreteUUIDFactory;
import edu.jhu.hlt.gigaword.ClojureIngester;
import edu.jhu.hlt.gigaword.ProxyDocument;

/**
 * @author max
 * @author fferraro
 */
public class StanfordAgigaPipeTest {

  private static final Logger logger = LoggerFactory.getLogger(StanfordAgigaPipeTest.class);

  public static final String SHAKE_HAND_TEXT_STRING = "The man ran to shake the U.S. \nPresident's hand. ";
  public static final String afp0623Text = "" + "Protest over arrest of Sri Lanka reporter linked to Fonseka"
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

  final String pathToAFPComm = "./src/test/resources/AFP_ENG_20100318.0623.xml";

  ConcreteUUIDFactory cuf = new ConcreteUUIDFactory();
  ConcreteFactory cf = new ConcreteFactory();

  Communication randomTestComm;
  Communication afpTestComm;

  StanfordAgigaPipe pipe;

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    this.pipe = new StanfordAgigaPipe();

    Communication c = new ConcreteFactory().randomCommunication();
    this.randomTestComm = new SingleSectionSegmenter().annotate(c);

    ProxyDocument pdc = new ClojureIngester().proxyDocPathToProxyDoc(this.pathToAFPComm);
    Communication mapped = pdc.sectionedCommunication(); 
    this.afpTestComm = this.pipe.process(mapped);
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
    SuperCommunication sc = new SuperCommunication(this.randomTestComm);
    assertTrue(sc.hasSectionSegmentations());
    assertTrue(sc.hasSections());

    Communication nc = this.pipe.process(this.randomTestComm);
    logger.info("this testcomm = {}", this.randomTestComm.getText());
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
    System.out.println("this testcomm = " + this.randomTestComm.getText());
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
    Communication shakeHandComm = this.cf.randomCommunication().setText(SHAKE_HAND_TEXT_STRING);
    Section section = new Section().setUuid(cuf.getConcreteUUID()).setTextSpan(new TextSpan().setStart(0).setEnding(SHAKE_HAND_TEXT_STRING.length()))
        .setKind("Passage");
    SectionSegmentation ss = new SectionSegmentation().setUuid(cuf.getConcreteUUID());
    ss.addToSectionList(section);
    shakeHandComm.addToSectionSegmentationList(ss);

    Communication processedShakeHandComm = pipe.process(shakeHandComm);
    final String docText = processedShakeHandComm.getText();
    final String[] stokens = { "The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "." };

    assertTrue(docText.equals(StanfordAgigaPipeTest.SHAKE_HAND_TEXT_STRING));

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
    assertTrue(pulledText.equals(SHAKE_HAND_TEXT_STRING.trim()));

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
    assertEquals("Should be three entities.", 3, processedShakeHandComm.getEntitySetList().get(0).getEntityList().size());

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

  @Test
  public void processAFPComm() throws Exception {
    final String theirs = afpTestComm.getText();
    
    // Text equality
    assertEquals("Text should be equal.", afp0623Text, theirs);
    
    // Sections
    List<Section> nsects = afpTestComm.getSectionSegmentationList().get(0).getSectionList();
    assertEquals("Should have found 8 sections.", 8, nsects.size());
    
    // Sentences
    int numSents = 0;
    for (Section sect : nsects)
      if (sect.isSetSentenceSegmentationList() && sect.getSentenceSegmentationList().get(0) != null) 
        numSents += sect.getSentenceSegmentationList().get(0).getSentenceList().size();
    
    assertEquals("Should have found 8 sentences.", 8, numSents);
    
    // First sentence span test
    int begin = 60;
    int end = 242;
    Sentence sent = afpTestComm.getSectionSegmentationList().get(0).getSectionList().get(1).getSentenceSegmentationList()
        .get(0).getSentenceList().get(0);
    TextSpan tts = sent.getTextSpan();
    assertEquals("Start should be " + begin, begin, tts.getStart());
    assertEquals("End should be " + end, end, tts.getEnding());
    
    // Sentences test
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
    for (Section sect : afpTestComm.getSectionSegmentationList().get(0).getSectionList()) {
      if (sect.getSentenceSegmentationList() != null) {
        for (Sentence st : sect.getSentenceSegmentationList().get(0).getSentenceList()) {
          TextSpan span = st.getTextSpan();
          String grabbed = afp0623Text.substring(span.getStart(), span.getEnding()).trim();
          // System.out.println("SentId = " + sentIdx + ", grabbing [[" + grabbed + "]], should be looking at <<" + sentences[sentIdx] + ">> .... " +
          // grabbed.equals(sentences[sentIdx]));

          assertTrue("SentId = " + sentIdx + ", grabbing [[" + grabbed + "]], should be looking at <<" + sentences[sentIdx] + ">>",
              grabbed.equals(sentences[sentIdx]));
          sentIdx++;
        }
      }
    }
    
    // Verify tokens
    int numEq = 0;
    int numTot = 0;
    for (Section nsect : afpTestComm.getSectionSegmentationList().get(0).getSectionList()) {
      if (nsect.getSentenceSegmentationList() == null)
        continue;
      for (Sentence nsent : nsect.getSentenceSegmentationList().get(0).getSentenceList()) {
        for (Token token : nsent.getTokenizationList().get(0).getTokenList().getTokenList()) {
          TextSpan span = token.getTextSpan();
          String substr = theirs.substring(span.getStart(), span.getEnding());
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
    
    // Dependency parses
    int expNumDepParses = 3;
    for (Section nsect : afpTestComm.getSectionSegmentationList().get(0).getSectionList()) {
      if (nsect.getSentenceSegmentationList() == null)
        continue;
      for (Sentence nsent : nsect.getSentenceSegmentationList().get(0).getSentenceList()) {
        Tokenization tokenization = nsent.getTokenizationList().get(0);
        assertEquals(expNumDepParses, tokenization.getDependencyParseList().size());
      }
    }
    
    // Verify non-empty dependency parses
    for (Section nsect : afpTestComm.getSectionSegmentationList().get(0).getSectionList()) {
      if (nsect.getSentenceSegmentationList() == null)
        continue;
      for (Sentence nsent : nsect.getSentenceSegmentationList().get(0).getSentenceList()) {
        Tokenization tokenization = nsent.getTokenizationList().get(0);
        for (DependencyParse depParse : tokenization.getDependencyParseList()) {
          assertTrue("DependencyParse " + depParse.getMetadata().getTool() + " is empty", depParse.getDependencyList().size() > 0);
        }
      }
    }
    
    // Verify some NEs
    assertTrue(afpTestComm.getEntitySetList().size() > 0);
    assertTrue(afpTestComm.getEntitySetList().get(0).getEntityList().size() > 0);
    boolean allSet = true;
    for (Entity entity : afpTestComm.getEntitySetList().get(0).getEntityList()) {
      allSet &= (entity.getCanonicalName() != null && entity.getCanonicalName().length() > 0);
    }
    assertTrue(allSet);
    
    // Verify anchor tokens
    int numWithout = 0;
    for (EntityMention em : afpTestComm.getEntityMentionSetList().get(0).getMentionList()) {
      numWithout += (em.getTokens().anchorTokenIndex >= 0 ? 0 : 1);
      // logger.info("In memory, token head via member" + em.getTokenList().anchorTokenIndex);
      // logger.info("In memory, token head via function " + em.getTokenList().getAnchorTokenIndex());
    }
    
    assertEquals("Shouldn't be any non-anchor tokens.", 0, numWithout);
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
