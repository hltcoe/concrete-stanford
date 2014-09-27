/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
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

import concrete.tools.AnnotationException;
import concrete.util.data.ConcreteFactory;
import edu.jhu.hlt.asphalt.AsphaltException;
import edu.jhu.hlt.ballast.InvalidInputException;
import edu.jhu.hlt.concrete.AnnotationMetadata;
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
import edu.jhu.hlt.concrete.util.CommunicationSerialization;
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
  public static final String AFP_0623_TEXT = "" + "Protest over arrest of Sri Lanka reporter linked to Fonseka"
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
  final String pathToNYTComm = "./src/test/resources/NYT_ENG_20070319.0077.xml";
  final String pathTo1999NYTComm = "./src/test/resources/NYT_ENG_19991220.0301.xml";

  ConcreteUUIDFactory cuf = new ConcreteUUIDFactory();
  ConcreteFactory cf = new ConcreteFactory();

  Communication randomTestComm;
  Communication mapped;
  Communication wonkyNYT;
  Communication nyt1999;

  StanfordAgigaPipe pipe;

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    this.pipe = new StanfordAgigaPipe();

    Communication c = new ConcreteFactory().randomCommunication();
    c.addToSectionList(new SuperCommunication(c).singleSection("Passage"));

    ClojureIngester ci = new ClojureIngester();
    ProxyDocument pdc = ci.proxyDocPathToProxyDoc(this.pathToAFPComm);
    this.mapped = pdc.sectionedCommunication();
    this.randomTestComm = new Communication(c);
    
    this.wonkyNYT = ci.proxyDocPathToProxyDoc(this.pathToNYTComm).sectionedCommunication();

    this.nyt1999 = ci.proxyDocPathToProxyDoc(this.pathTo1999NYTComm).sectionedCommunication();
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
   * @throws AnnotationException 
   */
  @Test
  public void processPassages() throws TException, InvalidInputException, IOException, ConcreteException, AnnotationException {
    SuperCommunication sc = new SuperCommunication(this.randomTestComm);
    assertTrue(sc.hasSections());

    Communication nc = this.pipe.process(this.randomTestComm);
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
   * @throws AnnotationException 
   */
  @Test
  public void testNoMentions() throws TException, InvalidInputException, IOException, ConcreteException, AnnotationException {
    Communication c = new ConcreteFactory().randomCommunication().setText("gobljsfoewj");
    c.addToSectionList(new SuperCommunication(c).singleSection("Passage"));
    SuperCommunication sc = new SuperCommunication(c);
    assertTrue(sc.hasSections());

    Communication nc = this.pipe.process(c);
    assertTrue(nc.isSetEntityMentionSetList());
    assertTrue(nc.isSetEntitySetList());
    new SuperCommunication(nc).writeToFile("target/post-stanford_garbage_processed.concrete", true);
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * 
   * @throws TException
   * @throws AsphaltException
   * @throws InvalidInputException
   * @throws ConcreteException
   * @throws IOException
   * @throws AnnotationException 
   */
  @Test
  public void processHandshakeCommunication() throws TException, InvalidInputException, IOException, ConcreteException, AnnotationException {
    Communication shakeHandComm = this.cf.randomCommunication().setText(SHAKE_HAND_TEXT_STRING);
    AnnotationMetadata md = new AnnotationMetadata()
        .setTool("concrete-stanford:test")
        .setTimestamp(System.currentTimeMillis() / 1000);
    shakeHandComm.setMetadata(md);
    Section section = new Section()
        .setUuid(cuf.getConcreteUUID())
        .setTextSpan(new TextSpan().setStart(0).setEnding(SHAKE_HAND_TEXT_STRING.length()))
        .setKind("Passage");
    shakeHandComm.addToSectionList(section);

    assertTrue("Error in creating original communication",
               new CommunicationSerialization().toBytes(shakeHandComm) != null);

    Communication processedShakeHandComm = pipe.process(shakeHandComm);
    assertTrue("Error in serializing processed communication",
               new CommunicationSerialization().toBytes(processedShakeHandComm) != null);
    final String docText = processedShakeHandComm.getOriginalText();
    final String[] stokens = { "The", "man", "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "." };

    assertTrue(docText.equals(StanfordAgigaPipeTest.SHAKE_HAND_TEXT_STRING));

    final Section firstSection = processedShakeHandComm.getSectionList().get(0);
    final List<Sentence> firstSentList = firstSection.getSentenceList();
    final Sentence firstSent = firstSentList.get(0);
    final Tokenization firstTokenization = firstSent.getTokenization();
    assertTrue(firstSentList.size() == 1);

    // Test spans
    assertTrue(firstSentList.size() == 1);
    assertTrue("firstSent.rawTextSpan should be set", firstSent.isSetRawTextSpan());
    assertEquals("Beginning char should be 0.", 0, firstSent.getRawTextSpan().getStart());
    assertEquals("Ending char should be 48.", 48, firstSent.getRawTextSpan().getEnding());

    TextSpan tts = firstSent.getRawTextSpan();
    String pulledText = docText.substring(tts.getStart(), tts.getEnding());
    assertTrue(pulledText.equals(SHAKE_HAND_TEXT_STRING.trim()));

    // Test # Tokens
    StringBuilder actualTokensSB = new StringBuilder();
    for (Token tok : firstTokenization.getTokenList().getTokenList()) {
      actualTokensSB.append("(" + tok.text + ", " + tok.tokenIndex + ") ");
    }
    assertTrue("Expected tokens length = " + stokens.length + ";" + "Actual   tokens length = " + firstTokenization.getTokenList().getTokenList().size() + "; "
        + "Actual tokens = " + actualTokensSB.toString(), firstTokenization.getTokenList().getTokenList().size() == stokens.length);

    // Verify tokens
    int tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      assertTrue("tokIdx = " + tokIdx + "; token.tokenIndex = " + token.tokenIndex, token.tokenIndex == tokIdx);
      assertTrue("expected = [" + stokens[tokIdx] + "]; token.text = [" + token.text + "]", token.text.equals(stokens[tokIdx]));
      tokIdx++;
    }

    // Verify tokens to full
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + token.getText() + "];" + "docText(" + tts + ") = [" + substr + "]", token.getText().equals(substr));
    }

    // Verify tokens to full seeded
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + stokens[tokIdx] + "];" + "docText(" + tts + ") = [" + substr + "]", stokens[tokIdx].equals(substr));
      tokIdx++;
    }

    // Verify token spans
    int[] start = { 0, 4, 8, 12, 15, 21, 25, 31, 40, 43, 47 };
    int[] end = { 3, 7, 11, 14, 20, 24, 29, 40, 42, 47, 48 };
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
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
    Communication afpProcessedComm = this.pipe.process(this.mapped);
    final String processedText = afpProcessedComm.getText();
    final String processedRawText = afpProcessedComm.getOriginalText();
    
    // Text equality
    assertEquals("Text should be equal.", AFP_0623_TEXT, processedRawText);
    assertTrue("Communication should have text field set", afpProcessedComm.isSetText());

    // Sections
    List<Section> nsects = afpProcessedComm.getSectionList();
    assertEquals("Should have found 8 sections.", 8, nsects.size());
    
    // Sentences
    {
        int numSents = 0;
        for (Section sect : nsects) {
                numSents += sect.getSentenceList().size();
        }
        assertEquals("Should have found 8 sentences.", 8, numSents);
    }
    
    // First sentence span test wrt RAW
    {
        int begin = 60;
        int end = 242;
        Sentence sent = afpProcessedComm.getSectionList().get(1)
            .getSentenceList().get(0);
        TextSpan tts = sent.getRawTextSpan();
        assertEquals("Start should be " + begin, begin, tts.getStart());
        assertEquals("End should be " + end, end, tts.getEnding());
    }

    // First sentence span test wrt processed
    {
        int begin = 0;
        int end = 184;
        Sentence sent = afpProcessedComm.getSectionList().get(1).getSentenceList().get(0);
        TextSpan tts = sent.getTextSpan();
        assertEquals("Start should be " + begin, begin, tts.getStart());
        assertEquals("End should be " + end, end, tts.getEnding());
    }
    // Second sentence span test wrt processed
    {
        int begin = 186;
        int end = 332;
        Sentence sent = afpProcessedComm
            .getSectionList().get(2)
            
            .getSentenceList().get(0);
        TextSpan tts = sent.getTextSpan();
        assertEquals("Start should be " + begin, begin, tts.getStart());
        assertEquals("End should be " + end, end, tts.getEnding());
    }
    
    // Sentences test
    {
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
        for (Section sect : afpProcessedComm.getSectionList()) {
                for (Sentence st : sect.getSentenceList()) {
                    TextSpan span = st.getRawTextSpan();
                    String grabbed = processedRawText.substring(span.getStart(), span.getEnding()).trim();
                    assertTrue("SentId = " + sentIdx + ", grabbing [[" + grabbed + "]], should be looking at <<" + sentences[sentIdx] + ">>",
                               grabbed.equals(sentences[sentIdx]));
                    sentIdx++;
                }
        }
    }
    //test sentences wrt processed
    {
        String[] processedSentences = {
            "Sri Lankan media groups Thursday protested against the arrest of a reporter close to Sarath Fonseka , the detained ex-army chief who tried to unseat the president in recent elections .",
            "The groups issued a joint statement demanding the release of Ruwan Weerakoon , a reporter with the Nation newspaper , who was arrested this week .",
            "`` We request the Inspector General of Police to disclose the reasons behind the arrest and detention of Ruwan Weerakoon and make arrangements for him to receive legal aid immediately , '' the statement added .",
            "Weerakoon maintained close contact with Fonseka when the general led the military during the final phase of last year 's war against Tamil Tiger rebels .",
            "Fonseka was an ally of President Mahinda Rajapakse when the rebel Liberation Tigers of Tamil Eelam -LRB- LTTE -RRB- were crushed in May , but the two men later fell out and contested the presidency in January 's elections .",
            "Fonseka was arrested soon after losing the poll and appeared in front of a court martial this week .", 
            "The case was adjourned .",
            "Local and international rights groups have accused Rajapakse of cracking down on dissent , a charge the government has denied ." };
        int sentIdx = 0;
        int numOkay = 0;
        int numTot = 0;
        for (Section sect : afpProcessedComm.getSectionList()) {
                for (Sentence st : sect.getSentenceList()) {
                    TextSpan span = st.getTextSpan();
                    String grabbed = processedText.substring(span.getStart(), span.getEnding()).trim();
                    boolean eq = grabbed.equals(processedSentences[sentIdx]);
                    if(eq) {
                        numOkay++;
                    } else {
                        logger.warn("SentId = " + sentIdx + ", span = "+span+", grabbing [[" + grabbed + "]], should be looking at <<" + processedSentences[sentIdx] + ">>");
                    }
                    numTot++;
                    sentIdx++;
                }
        }
        double fracPassing = ((double) numOkay / (double) numTot);
        assertTrue("WARNING: only " + (fracPassing*100) + "% of processed sentences matched!", fracPassing >= 0.8);
    }
    
    // Verify tokens wrt RAW
    {
        int numEq = 0;
        int numTot = 0;
        // wrt the raw text
        for (Section nsect : afpProcessedComm.getSectionList()) {
            for (Sentence nsent : nsect.getSentenceList()) {
                for (Token token : nsent.getTokenization().getTokenList().getTokenList()) {
                    TextSpan span = token.getRawTextSpan();
                    String substr = processedRawText.substring(span.getStart(), span.getEnding());
                    boolean areEq = token.getText().equals(substr);
                    if (!areEq) {
                        logger.warn("expected = [" + token.getText() + "];" + "docText(" + span + ") = [" + substr + "]");
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
    // Verify tokens wrt PROCESSED
    {
        int numEq = 0;
        int numTot = 0;
        // wrt the processed text
        for (Section nsect : afpProcessedComm.getSectionList()) {
            for (Sentence nsent : nsect.getSentenceList()) {
                for (Token token : nsent.getTokenization().getTokenList().getTokenList()) {
                    assertTrue("token " + token.getTokenIndex() + " shouldn't have null textspan",
                               token.isSetTextSpan());
                    TextSpan span = token.getTextSpan();
                    assertTrue("ending " + span.getEnding() + " is out of range ("+processedText.length()+")",
                               span.getEnding() < processedText.length());
                    String substr = processedText.substring(span.getStart(), span.getEnding());
                    boolean areEq = token.getText().equals(substr);
                    if (!areEq) {
                        logger.warn("expected = [" + token.getText() + "];" + " docText(" + span + ") = [" + substr + "]");
                    } else {
                        numEq++;
                    }
                    numTot++;
                }
            }
        }
        double fracPassing = ((double) numEq / (double) numTot);
        assertTrue("WARNING: only " + fracPassing + "% of tokens matched!", fracPassing == 1.0);
    }

    // Dependency parses
    {
        int expNumDepParses = 3;
        for (Section nsect : afpProcessedComm.getSectionList()) {
            for (Sentence nsent : nsect.getSentenceList()) {
                Tokenization tokenization = nsent.getTokenization();
                assertEquals(expNumDepParses, tokenization.getDependencyParseList().size());
            }
        }
    }
    
    // Verify non-empty dependency parses
    {
        for (Section nsect : afpProcessedComm.getSectionList()) {
            for (Sentence nsent : nsect.getSentenceList()) {
                Tokenization tokenization = nsent.getTokenization();
                for (DependencyParse depParse : tokenization.getDependencyParseList()) {
                    assertTrue("DependencyParse " + depParse.getMetadata().getTool() + " is empty", depParse.getDependencyList().size() > 0);
                }
            }
        }
    }
    
    // Verify some NEs
    {
        assertTrue(afpProcessedComm.getEntitySetList().size() > 0);
        assertTrue(afpProcessedComm.getEntitySetList().get(0).getEntityList().size() > 0);
        boolean allSet = true;
        for (Entity entity : afpProcessedComm.getEntitySetList().get(0).getEntityList()) {
            allSet &= (entity.getCanonicalName() != null && entity.getCanonicalName().length() > 0);
        }
        assertTrue(allSet);
    }
    
    // Verify anchor tokens
    {
        int numWithout = 0;
        for (EntityMention em : afpProcessedComm.getEntityMentionSetList().get(0).getMentionList()) {
            numWithout += (em.getTokens().anchorTokenIndex >= 0 ? 0 : 1);
            // logger.info("In memory, token head via member" + em.getTokenList().anchorTokenIndex);
            // logger.info("In memory, token head via function " + em.getTokenList().getAnchorTokenIndex());
        }    
        assertEquals("Shouldn't be any non-anchor tokens.", 0, numWithout);
    }

    {
        assertTrue("Error in serializing processed communication",
                   new CommunicationSerialization().toBytes(afpProcessedComm) != null);
    }
  }

  /**
   * This following test is useful because it uses a number
   * of numeric fractions. The Stanford tokenizer is a bit
   * strange with those: for a fraction of K n/m (e.g.,
   * 66 6/16), it will add a non-breaking space in between
   * K and n. Therefore, it may appear that "K n/m" is two
   * tokens, but it really is one. This test primarily
   * verifies that those boundaries are respected in both
   * the Token.text and Token.textSpan fields.
   */
  @Test
  public void process1999NYTComm() throws Exception {
    Communication nytProcessedComm = this.pipe.process(this.nyt1999);
    final String processedText = nytProcessedComm.getText();
    final String processedRawText = nytProcessedComm.getOriginalText();

    assertTrue("Communication should have text field set", nytProcessedComm.isSetText());

    // Sections
    List<Section> nsects = nytProcessedComm.getSectionList();
    assertEquals("Should have found 15 sections (including title): has " + nsects.size(),
                 15, nsects.size());

    // Verify tokens wrt RAW
    {
        int numEq = 0;
        int numTot = 0;
        // wrt the raw text
        for (Section nsect : nytProcessedComm.getSectionList()) {
            for (Sentence nsent : nsect.getSentenceList()) {
                for (Token token : nsent.getTokenization().getTokenList().getTokenList()) {
                    TextSpan span = token.getRawTextSpan();
                    String substr = processedRawText.substring(span.getStart(), span.getEnding());
                    boolean areEq = token.getText().equals(substr);
                    if (!areEq) {
                        logger.warn("verifying raw tokens: expected = [" + token.getText() + "];" + "docText(" + span + ") = [" + substr + "]");
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
    // Verify tokens wrt PROCESSED
    {
        int numEq = 0;
        int numTot = 0;
        // wrt the processed text
        for (Section nsect : nytProcessedComm.getSectionList()) {
            for (Sentence nsent : nsect.getSentenceList()) {
                for (Token token : nsent.getTokenization().getTokenList().getTokenList()) {
                    assertTrue("token " + token.getTokenIndex() + " shouldn't have null textspan",
                               token.isSetTextSpan());
                    TextSpan span = token.getTextSpan();
                    assertTrue("ending " + span.getEnding() + " is out of range ("+processedText.length()+")",
                               span.getEnding() < processedText.length());
                    String substr = processedText.substring(span.getStart(), span.getEnding());
                    boolean areEq = token.getText().equals(substr);
                    if (!areEq) {
                        logger.warn("verifying procesed tokens: expected = [" + token.getText() + "];" + " docText(" + span + ") = [" + substr + "]");
                    } else {
                        numEq++;
                    }
                    numTot++;
                }
            }
        }
        double fracPassing = ((double) numEq / (double) numTot);
        assertTrue("WARNING: only " + fracPassing + "% of tokens matched!", fracPassing == 1.0);
    }

    // Dependency parses
    {
        int expNumDepParses = 3;
        for (Section nsect : nytProcessedComm.getSectionList()) {
            for (Sentence nsent : nsect.getSentenceList()) {
                Tokenization tokenization = nsent.getTokenization();
                assertEquals(expNumDepParses, tokenization.getDependencyParseList().size());
            }
        }
    }

    // Verify non-empty dependency parses
    {
        for (Section nsect : nytProcessedComm.getSectionList()) {
            for (Sentence nsent : nsect.getSentenceList()) {
                Tokenization tokenization = nsent.getTokenization();
                for (DependencyParse depParse : tokenization.getDependencyParseList()) {
                    assertTrue("DependencyParse " + depParse.getMetadata().getTool() + " is empty", depParse.getDependencyList().size() > 0);
                }
            }
        }
    }

    // Verify some NEs
    {
        assertTrue(nytProcessedComm.getEntitySetList().size() > 0);
        assertTrue(nytProcessedComm.getEntitySetList().get(0).getEntityList().size() > 0);
        boolean allSet = true;
        for (Entity entity : nytProcessedComm.getEntitySetList().get(0).getEntityList()) {
            allSet &= (entity.getCanonicalName() != null && entity.getCanonicalName().length() > 0);
        }
        assertTrue(allSet);
    }

    // Verify anchor tokens
    {
        int numWithout = 0;
        for (EntityMention em : nytProcessedComm.getEntityMentionSetList().get(0).getMentionList()) {
            numWithout += (em.getTokens().anchorTokenIndex >= 0 ? 0 : 1);
        }    
        assertEquals("Shouldn't be any non-anchor tokens.", 0, numWithout);
    }

    {
        assertTrue("Error in serializing processed communication",
                   new CommunicationSerialization().toBytes(nytProcessedComm) != null);
    }
  }

  // @Test
  // public void processWonkyNYTComm() throws Exception {
  //   Communication nytProcessedComm = this.pipe.process(this.wonkyNYT);
  //   final String processedText = nytProcessedComm.getText();
  //   final String processedRawText = nytProcessedComm.getOriginalText();
    
  //   // Text equality
  //   //assertEquals("Text should be equal.", AFP_0623_TEXT, processedRawText);
  //   assertTrue("Communication should have text field set", nytProcessedComm.isSetText());

  //   // Sections
  //   List<Section> nsects = nytProcessedComm.getSectionList();
  //   assertEquals("Should have found 28 sections (including title): has " + nsects.size(), 
  //                28, nsects.size());

    
  //   // First sentence span test wrt RAW
  //   // {
  //   //     int begin = 60;
  //   //     int end = 242;
  //   //     Sentence sent = afpProcessedComm.getSectionList().get(1).getSentenceSegmentationList()
  //   //         .get(0).getSentenceList().get(0);
  //   //     TextSpan tts = sent.getRawTextSpan();
  //   //     assertEquals("Start should be " + begin, begin, tts.getStart());
  //   //     assertEquals("End should be " + end, end, tts.getEnding());
  //   // }
    
  //   // Verify tokens wrt RAW
  //   {
  //       int numEq = 0;
  //       int numTot = 0;
  //       // wrt the raw text
  //       for (Section nsect : nytProcessedComm.getSectionList()) {
  //           if (nsect.getSentenceSegmentationList() == null)
  //               continue;
  //           for (Sentence nsent : nsect.getSentenceList()) {
  //               for (Token token : nsent.getTokenizationList().get(0).getTokenList().getTokenList()) {
  //                   TextSpan span = token.getRawTextSpan();
  //                   String substr = processedRawText.substring(span.getStart(), span.getEnding());
  //                   boolean areEq = token.getText().equals(substr);
  //                   if (!areEq) {
  //                       logger.warn("expected = [" + token.getText() + "];" + "docText(" + span + ") = [" + substr + "]");
  //                   } else {
  //                       numEq++;
  //                   }
  //                   numTot++;
  //               }
  //           }
  //       }
  //       double fracPassing = ((double) numEq / (double) numTot);
  //       assertTrue("WARNING: only " + fracPassing + "% of tokens matched!", fracPassing >= 0.8);
  //   }
  //   // Verify tokens wrt PROCESSED
  //   {
  //       int numEq = 0;
  //       int numTot = 0;
  //       // wrt the processed text
  //       for (Section nsect : nytProcessedComm.getSectionList()) {
  //           if (nsect.getSentenceSegmentationList() == null)
  //               continue;
  //           for (Sentence nsent : nsect.getSentenceList()) {
  //               for (Token token : nsent.getTokenizationList().get(0).getTokenList().getTokenList()) {
  //                   assertTrue("token " + token.getTokenIndex() + " shouldn't have null textspan",
  //                              token.isSetTextSpan());
  //                   TextSpan span = token.getTextSpan();
  //                   assertTrue("ending " + span.getEnding() + " is out of range ("+processedText.length()+")",
  //                              span.getEnding() < processedText.length());
  //                   String substr = processedText.substring(span.getStart(), span.getEnding());
  //                   boolean areEq = token.getText().equals(substr);
  //                   if (!areEq) {
  //                       logger.warn("expected = [" + token.getText() + "];" + " docText(" + span + ") = [" + substr + "]");
  //                   } else {
  //                       numEq++;
  //                   }
  //                   numTot++;
  //               }
  //           }
  //       }
  //       double fracPassing = ((double) numEq / (double) numTot);
  //       assertTrue("WARNING: only " + fracPassing + "% of tokens matched!", fracPassing == 1.0);
  //   }

  //   // Dependency parses
  //   {
  //       int expNumDepParses = 3;
  //       for (Section nsect : nytProcessedComm.getSectionList()) {
  //           if (nsect.getSentenceSegmentationList() == null)
  //               continue;
  //           for (Sentence nsent : nsect.getSentenceList()) {
  //               Tokenization tokenization = nsent.getTokenizationList().get(0);
  //               assertEquals(expNumDepParses, tokenization.getDependencyParseList().size());
  //           }
  //       }
  //   }
    
  //   // Verify non-empty dependency parses
  //   {
  //       for (Section nsect : nytProcessedComm.getSectionList()) {
  //           if (nsect.getSentenceSegmentationList() == null)
  //               continue;
  //           for (Sentence nsent : nsect.getSentenceList()) {
  //               Tokenization tokenization = nsent.getTokenizationList().get(0);
  //               for (DependencyParse depParse : tokenization.getDependencyParseList()) {
  //                   assertTrue("DependencyParse " + depParse.getMetadata().getTool() + " is empty", depParse.getDependencyList().size() > 0);
  //               }
  //           }
  //       }
  //   }
    
  //   // Verify some NEs
  //   {
  //       assertTrue(nytProcessedComm.getEntitySetList().size() > 0);
  //       assertTrue(nytProcessedComm.getEntitySetList().get(0).getEntityList().size() > 0);
  //       boolean allSet = true;
  //       for (Entity entity : nytProcessedComm.getEntitySetList().get(0).getEntityList()) {
  //           allSet &= (entity.getCanonicalName() != null && entity.getCanonicalName().length() > 0);
  //       }
  //       assertTrue(allSet);
  //   }
    
  //   // Verify anchor tokens
  //   {
  //       int numWithout = 0;
  //       for (EntityMention em : nytProcessedComm.getEntityMentionSetList().get(0).getMentionList()) {
  //           numWithout += (em.getTokens().anchorTokenIndex >= 0 ? 0 : 1);
  //       }    
  //       assertEquals("Shouldn't be any non-anchor tokens.", 0, numWithout);
  //   }

  //   {
  //       assertTrue("Error in serializing processed communication",
  //                  new CommunicationSerialization().toBytes(nytProcessedComm) != null);
  //   }
  // }

  // TODO: needs enabling/fixing. 
//  @Test
//  public void testNYTMessage() throws Exception {
//    Communication processedNYT = this.pipe.process(this.wonkyNYT);
//    new SuperCommunication(processedNYT).writeToFile("target/test-nyt-out.concrete", true);
//  }

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
