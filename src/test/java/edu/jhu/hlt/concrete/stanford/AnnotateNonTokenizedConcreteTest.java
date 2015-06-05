/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.Parse;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.communications.WritableCommunication;
import edu.jhu.hlt.concrete.ingesters.gigaword.GigawordDocumentConverter;
import edu.jhu.hlt.concrete.miscommunication.sectioned.CachedSectionedCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.random.RandomConcreteFactory;
import edu.jhu.hlt.concrete.section.SingleSectionSegmenter;
import edu.jhu.hlt.concrete.serialization.CommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.CompactCommunicationSerializer;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.SuperTextSpan;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;

/**
 * @author max
 * @author fferraro
 */
public class AnnotateNonTokenizedConcreteTest {

  private static final Logger logger = LoggerFactory
      .getLogger(AnnotateNonTokenizedConcreteTest.class);

  public static final String SHAKE_HAND_TEXT_STRING = "The man ran to shake the U.S. \nPresident's hand. ";
  public static final String AFP_0623_TEXT = ""
      + "Protest over arrest of Sri Lanka reporter linked to Fonseka"
      + "\nCOLOMBO, March 18, 2010 (AFP)"
      + "\nSri Lankan media groups Thursday protested against the arrest of a reporter"
      + "\nclose to Sarath Fonseka, the detained ex-army chief who tried to unseat the"
      + "\npresident in recent elections."
      + "\nThe groups issued a joint statement demanding the release of Ruwan Weerakoon, a"
      + "\nreporter with the Nation newspaper, who was arrested this week."
      + "\n\"We request the Inspector General of Police to disclose the reasons behind the"
      + "\narrest and detention of Ruwan Weerakoon and make arrangements for him to receive"
      + "\nlegal aid immediately,\" the statement added."
      + "\nWeerakoon maintained close contact with Fonseka when the general led the"
      + "\nmilitary during the final phase of last year's war against Tamil Tiger rebels."
      + "\nFonseka was an ally of President Mahinda Rajapakse when the rebel Liberation"
      + "\nTigers of Tamil Eelam (LTTE) were crushed in May, but the two men later fell out"
      + "\nand contested the presidency in January's elections."
      + "\nFonseka was arrested soon after losing the poll and appeared in front of a court"
      + "\nmartial this week. The case was adjourned."
      + "\nLocal and international rights groups have accused Rajapakse of cracking down on"
      + "\ndissent, a charge the government has denied.\n";

  final String pathToAFPComm = "./src/test/resources/AFP_ENG_20100318.0623.xml";
  final String pathToNYTComm = "./src/test/resources/NYT_ENG_20070319.0077.xml";
  final String pathTo1999NYTComm = "./src/test/resources/NYT_ENG_19991220.0301.xml";

  RandomConcreteFactory cf = new RandomConcreteFactory();
  CommunicationSerializer cs = new CompactCommunicationSerializer();

  Communication randomTestComm;
  Communication mapped;
  Communication wonkyNYT;
  Communication nyt1999;

  AnnotateNonTokenizedConcrete pipe;
  Set<String> kindsToAnnotate;

  @Rule
  public TemporaryFolder tf = new TemporaryFolder();

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    this.pipe = new AnnotateNonTokenizedConcrete();
    this.kindsToAnnotate = this.pipe.getSectionTypesToAnnotate();

    Communication c = this.cf.communication();
    c.addToSectionList(SingleSectionSegmenter.createSingleSection(c, "Passage"));

    GigawordDocumentConverter conv = new GigawordDocumentConverter();
    this.mapped = conv.fromPath(this.pathToAFPComm);
    this.randomTestComm = new Communication(c);

    this.wonkyNYT = conv.fromPath(this.pathToNYTComm);
    this.nyt1999 = conv.fromPath(this.pathTo1999NYTComm);
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {

  }

  /**
   * Test method for
   * {@link edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete#process(edu.jhu.hlt.concrete.Communication)}
   * .
   *
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void processPassages() throws Exception {
    new CachedSectionedCommunication(this.randomTestComm);

    Communication nc = this.pipe.annotate(this.randomTestComm).getRoot();
    assertTrue(nc.isSetEntityMentionSetList());
    assertTrue(nc.isSetEntitySetList());
    new WritableCommunication(nc).writeToFile(this.tf.getRoot().toPath().resolve("entities.concrete"), true);
  }

  /**
   * Test method for
   * {@link edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete#process(edu.jhu.hlt.concrete.Communication)}
   * .
   *
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void testNoMentions() throws Exception {
    Communication c = this.cf.communication().setText("gobljsfoewj");
    c.addToSectionList(SingleSectionSegmenter.createSingleSection(c, "Passage"));
    new CachedSectionedCommunication(c);

    StanfordPostNERCommunication nc = this.pipe.annotate(c);
    Communication root = nc.getRoot();
    assertTrue(root.isSetEntityMentionSetList());
    assertTrue(root.isSetEntitySetList());
    new WritableCommunication(root).writeToFile(this.tf.getRoot().toPath().resolve("post-stanford_garbage_processed.concrete"), true);
  }

  /**
   * Test method for
   * {@link edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete#process(edu.jhu.hlt.concrete.Communication)}
   * .
   *
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void processHandshakeCommunication() throws Exception {
    Communication shakeHandComm = this.cf.communication().setText(
        SHAKE_HAND_TEXT_STRING);
    AnnotationMetadata md = new AnnotationMetadata().setTool(
        "concrete-stanford:test").setTimestamp(
        System.currentTimeMillis() / 1000);
    shakeHandComm.setMetadata(md);
    Section section = new Section()
        .setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(0).setEnding(
                SHAKE_HAND_TEXT_STRING.length())).setKind("Passage");
    shakeHandComm.addToSectionList(section);

    assertTrue("Error in creating original communication",
        cs.toBytes(shakeHandComm) != null);

    Communication processedShakeHandComm = pipe.annotate(shakeHandComm).getRoot();
    assertTrue("Error in serializing processed communication",
        cs.toBytes(processedShakeHandComm) != null);
    final String docText = processedShakeHandComm.getOriginalText();
    final String[] stokens = { "The", "man", "ran", "to", "shake", "the",
        "U.S.", "President", "'s", "hand", "." };

    assertTrue(docText.equals(AnnotateNonTokenizedConcreteTest.SHAKE_HAND_TEXT_STRING));

    // Sections
    verifyNumSections(shakeHandComm, processedShakeHandComm);

    final Section firstSection = processedShakeHandComm.getSectionList().get(0);
    final List<Sentence> firstSentList = firstSection.getSentenceList();
    final Sentence firstSent = firstSentList.get(0);
    final Tokenization firstTokenization = firstSent.getTokenization();
    assertTrue(firstSentList.size() == 1);

    // Test spans
    assertTrue(firstSentList.size() == 1);
    assertTrue("firstSent.rawTextSpan should be set",
        firstSent.isSetRawTextSpan());
    assertEquals("Beginning char should be 0.", 0, firstSent.getRawTextSpan()
        .getStart());
    assertEquals("Ending char should be 48.", 48, firstSent.getRawTextSpan()
        .getEnding());

    TextSpan tts = firstSent.getRawTextSpan();
    String pulledText = docText.substring(tts.getStart(), tts.getEnding());
    assertTrue(pulledText.equals(SHAKE_HAND_TEXT_STRING.trim()));

    // Test # Tokens
    StringBuilder actualTokensSB = new StringBuilder();
    for (Token tok : firstTokenization.getTokenList().getTokenList()) {
      actualTokensSB.append("(" + tok.getText() + ", " + tok.getTokenIndex()
          + ") ");
    }
    assertTrue("Expected tokens length = " + stokens.length + ";"
        + "Actual   tokens length = "
        + firstTokenization.getTokenList().getTokenList().size() + "; "
        + "Actual tokens = " + actualTokensSB.toString(), firstTokenization
        .getTokenList().getTokenList().size() == stokens.length);

    // Verify tokens
    int tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      assertTrue(
          "tokIdx = " + tokIdx + "; token.getTokenIndex() = "
              + token.getTokenIndex(), token.getTokenIndex() == tokIdx);
      assertTrue("expected = [" + stokens[tokIdx] + "]; token.getText() = ["
          + token.getText() + "]", token.getText().equals(stokens[tokIdx]));
      tokIdx++;
    }

    // Verify tokens to full
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + token.getText() + "];" + "docText(" + tts
          + ") = [" + substr + "]", token.getText().equals(substr));
    }

    // Verify tokens to full seeded
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + stokens[tokIdx] + "];" + "docText(" + tts
          + ") = [" + substr + "]", stokens[tokIdx].equals(substr));
      tokIdx++;
    }

    // Verify token spans
    int[] start = { 0, 4, 8, 12, 15, 21, 25, 31, 40, 43, 47 };
    int[] end = { 3, 7, 11, 14, 20, 24, 29, 40, 42, 47, 48 };
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      assertTrue(
          token.getText() + "(" + tokIdx + ") starts at " + tts.getStart()
              + "; it should start at " + start[tokIdx],
          tts.getStart() == start[tokIdx]);
      assertTrue(
          token.getText() + "(" + tokIdx + ") starts at " + tts.getEnding()
              + "; it should start at " + end[tokIdx],
          tts.getEnding() == end[tokIdx]);
      tokIdx++;
    }

    // Verify # entities
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    assertEquals("Should be three entities.", 3, processedShakeHandComm
        .getEntitySetList().get(0).getEntityList().size());

    // Verify # Singleton entities
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      assertEquals(entity.getCanonicalName() + " is not singleton", 1, entity
          .getMentionIdList().size());
    }

    // Verify entity names
    Set<String> expEnts = new HashSet<String>();
    expEnts.add("The man");
    expEnts.add("the U.S. President 's hand");
    expEnts.add("the U.S. President 's");
    Set<String> seenEnts = new HashSet<String>();
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      seenEnts.add(entity.getCanonicalName());
    }
    assertTrue(seenEnts.equals(expEnts));

    // Verify some canonical entity names
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    boolean atLeastOne = false;
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      atLeastOne |= (entity.getCanonicalName() != null && entity
          .getCanonicalName().length() > 0);
    }
    assertTrue(atLeastOne);

    // Verify metadata toolnames
    // this.verifyToolNames(processedShakeHandComm);
  }

  private void verifyNumSections(Communication orig, Communication proc) {
    assertTrue("orig comm " + orig.getUuid() + " does not have a section list",
        orig.isSetSectionList());
    assertTrue("processed comm " + proc.getUuid()
        + " does not have a section list", proc.isSetSectionList());
    assertTrue("num sections in original comm (" + orig.getUuid() + ") = "
        + orig.getSectionList().size() + " != num sections in processed comm ("
        + proc.getUuid() + ") = " + proc.getSectionList().size(), orig
        .getSectionList().size() == proc.getSectionList().size());
  }

  private void verifyTitleSection(Communication processed) {
    int sectionIdx = 0;
    int foundTitleIn = -1;
    int numFound = 0;
    for (Section section : processed.getSectionList()) {
      if (section.getKind().equals("Title")) {
        numFound++;
        foundTitleIn = sectionIdx;
      }
      sectionIdx++;
    }
    assertTrue(
        "Found " + numFound + " title sections in " + processed.getUuid() + "/"
            + processed.getId(), numFound == 1);
    Section title = processed.getSectionList().get(foundTitleIn);
    assertTrue(
        "Title section (" + title.getUuid() + ") in comm " + processed.getId()
            + " does not have text span set", title.isSetTextSpan());
    assertTrue(
        "Title section (" + title.getUuid() + ") in comm " + processed.getId()
            + " does not have sentences", title.isSetSentenceList());
    assertTrue(
        "Title section (" + title.getUuid() + ") in comm " + processed.getId()
            + " does not have one sentence",
        title.getSentenceList().size() == 1);
    Sentence sentence = title.getSentenceList().get(0);
    assertTrue("Title sentence (" + sentence.getUuid() + ") in comm "
        + processed.getId() + " does not have text span set",
        title.isSetTextSpan());
  }

  private void verifyToolNamesSub(String given, String prefix, String expected) {
    String joined = prefix + ": " + expected;
    assertEquals(joined, given);
  }

  private void verifyToolNames(Communication comm) throws IOException {
    ConcreteStanfordProperties props = new ConcreteStanfordProperties();

    String toolNamePrefix = props.getToolName();
    for (Section section : comm.getSectionList()) {
      // we don't verify section toolnames
      if (section.isSetSentenceList())
        for (Sentence sentence : section.getSentenceList()) {
          if (sentence.isSetTokenization()) {
            Tokenization tokenization = sentence.getTokenization();
            verifyToolNamesSub(tokenization.getMetadata().getTool(),
                toolNamePrefix, props.getTokenizerToolName());
            if (tokenization.isSetTokenTaggingList()) {
              for (TokenTagging tokenTagging : tokenization
                  .getTokenTaggingList()) {
                switch (tokenTagging.getTaggingType()) {
                case "POS":
                  verifyToolNamesSub(tokenTagging.getMetadata().getTool(),
                      toolNamePrefix, props.getPOSToolName());
                  break;
                case "LEMMA":
                  verifyToolNamesSub(tokenTagging.getMetadata().getTool(),
                      toolNamePrefix, props.getLemmatizerToolName());
                  break;
                case "NER":
                  verifyToolNamesSub(tokenTagging.getMetadata().getTool(),
                      toolNamePrefix, props.getNERToolName());
                  break;
                default:
                  assertTrue(
                      "unknown tagging type " + tokenTagging.getTaggingType(),
                      false);
                  break;
                }
              }
            }

            if (tokenization.isSetParseList())
              for (Parse parse : tokenization.getParseList())
                verifyToolNamesSub(parse.getMetadata().getTool(),
                    toolNamePrefix, props.getCParseToolName());

            if (tokenization.isSetDependencyParseList()) {
              for (DependencyParse dparse : tokenization
                  .getDependencyParseList()) {
                String[] wsSplit = dparse.getMetadata().getTool().trim()
                    .split(" ");
                verifyToolNamesSub(dparse.getMetadata().getTool(),
                    toolNamePrefix, props.getDParseToolName() + " "
                        + wsSplit[wsSplit.length - 1]);
              }
            }
          }
        }
    }
    // verify coref
    if (comm.isSetEntityMentionSetList()) {
      for (EntityMentionSet ems : comm.getEntityMentionSetList()) {
        verifyToolNamesSub(ems.getMetadata().getTool(), toolNamePrefix,
            props.getCorefToolName());
      }
    }
    if (comm.isSetEntitySetList()) {
      for (EntitySet es : comm.getEntitySetList()) {
        verifyToolNamesSub(es.getMetadata().getTool(), toolNamePrefix,
            props.getCorefToolName());
      }
    }
  }

  private void testTextSpan(TextSpan ts, int expectedStart, int expectedEnd) {
    assertEquals(expectedStart, ts.getStart());
    assertEquals(expectedEnd, ts.getEnding());
  }

  private void testSentenceText(String[] sentences, Communication target) {
    String processedOriginalText = target.getOriginalText();
    int sentIdx = 0;
    for (Section sect : target.getSectionList()) {
      if (sect.isSetSentenceList())
        for (Sentence st : sect.getSentenceList()) {
          TextSpan span = st.getRawTextSpan();
          String grabbed = processedOriginalText.substring(span.getStart(),
              span.getEnding()).trim();
          // assertTrue("SentId = " + sentIdx + ", grabbing [[" + grabbed +
          // "]], should be looking at <<" + sentences[sentIdx] + ">>",
          // grabbed.equals(sentences[sentIdx]));
          assertEquals(sentences[sentIdx], grabbed);
          sentIdx++;
        }
    }
  }

  private void verifyTokens(Communication target, boolean useRawTokens) {
    String textToCheck = useRawTokens ? target.getOriginalText() : target
        .getText();
    int numEq = 0;
    int numTot = 0;
    // wrt the raw text
    for (Section nsect : target.getSectionList()) {
      if (nsect.isSetSentenceList())
        for (Sentence nsent : nsect.getSentenceList()) {
          for (Token token : nsent.getTokenization().getTokenList()
              .getTokenList()) {
            TextSpan span = useRawTokens ? token.getRawTextSpan() : token
                .getTextSpan();
            String substr = textToCheck.substring(span.getStart(),
                span.getEnding());
            boolean areEq = token.getText().equals(substr);
            if (!areEq)
              logger.warn("expected = [" + token.getText() + "];" + "docText("
                  + span + ") = [" + substr + "]");
            else
              numEq++;

            numTot++;
          }
        }
    }
    double fracPassing = ((double) numEq / (double) numTot);
    assertTrue("WARNING: only " + fracPassing + "% of tokens matched!",
        fracPassing >= 0.8);
  }

  private void testSectionOffsetsSet(List<Section> nsects,
      Set<String> kindsThatShouldHaveSections) {
    int i = 0;
    for (Section sect : nsects) {
      assertTrue(sect.isSetKind());
      if (kindsThatShouldHaveSections.contains(sect.getKind())) {
        assertTrue("section #" + i + " (uuid = " + sect.getUuid()
            + ") doesn't have text spans set", sect.isSetTextSpan());
        i++;
      }
    }
  }

  @Test
  public void processAFPComm() throws Exception {
    Communication afpProcessedComm = this.pipe.annotate(this.mapped).getRoot();
    final String processedText = afpProcessedComm.getText();
    final String processedRawText = afpProcessedComm.getOriginalText();

    // Text equality
    assertEquals("Text should be equal.", AFP_0623_TEXT, processedRawText);
    assertTrue("Communication should have text field set",
        afpProcessedComm.isSetText());

    // Sections
    List<Section> nsects = afpProcessedComm.getSectionList();
    assertEquals("Should have found 9 sections.", 9, nsects.size());
    verifyNumSections(this.mapped, afpProcessedComm);
    verifyTitleSection(afpProcessedComm);

    this.testSectionOffsetsSet(nsects, this.kindsToAnnotate);
    this.testTextSpan(nsects.get(0).getTextSpan(), 0, 59);
    this.testTextSpan(nsects.get(1).getTextSpan(), 61, 102);
    this.testTextSpan(nsects.get(2).getTextSpan(), 104, 288);

    // Sentences
    int numSents = 0;
    for (Section sect : nsects)
      if (sect.isSetSentenceList())
        numSents += sect.getSentenceList().size();

    assertEquals("Should have found 10 sentences.", 10, numSents);

    // First sentence span test wrt RAW
    Sentence ofInterest = afpProcessedComm.getSectionList().get(1)
        .getSentenceList().get(0);
    TextSpan raw = ofInterest.getRawTextSpan();
    this.testTextSpan(raw, 60, 89);

    // First sentence span test wrt processed
    this.testTextSpan(afpProcessedComm.getSectionList().get(1)
        .getSentenceList().get(0).getTextSpan(), 61, 102);

    // Second sentence span test wrt processed
    this.testTextSpan(afpProcessedComm.getSectionList().get(2)
        .getSentenceList().get(0).getTextSpan(), 104, 288);

    // Sentences test
    String[] sentences = {
        "Protest over arrest of Sri Lanka reporter linked to Fonseka",
        "Sri Lankan media groups Thursday protested against the arrest of a reporter\n"
            + "close to Sarath Fonseka, the detained ex-army chief who tried to unseat the\n"
            + "president in recent elections.",
        "The groups issued a joint statement demanding the release of Ruwan Weerakoon, a\n"
            + "reporter with the Nation newspaper, who was arrested this week.",
        "\"We request the Inspector General of Police to disclose the reasons behind the\n"
            + "arrest and detention of Ruwan Weerakoon and make arrangements for him to receive\n"
            + "legal aid immediately,\" the statement added.",
        "Weerakoon maintained close contact with Fonseka when the general led the\n"
            + "military during the final phase of last year's war against Tamil Tiger rebels.",
        "Fonseka was an ally of President Mahinda Rajapakse when the rebel Liberation\n"
            + "Tigers of Tamil Eelam (LTTE) were crushed in May, but the two men later fell out\n"
            + "and contested the presidency in January's elections.",
        "Fonseka was arrested soon after losing the poll and appeared in front of a court\n"
            + "martial this week.",
        "The case was adjourned.",
        "Local and international rights groups have accused Rajapakse of cracking down on\n"
            + "dissent, a charge the government has denied." };

    // test sentences wrt processed
    String[] processedSentences = {

        "Protest over arrest of Sri Lanka reporter linked to Fonseka",
        "Sri Lankan media groups Thursday protested against the arrest of a reporter close to Sarath Fonseka , the detained ex-army chief who tried to unseat the president in recent elections .",
        "The groups issued a joint statement demanding the release of Ruwan Weerakoon , a reporter with the Nation newspaper , who was arrested this week .",
        "`` We request the Inspector General of Police to disclose the reasons behind the arrest and detention of Ruwan Weerakoon and make arrangements for him to receive legal aid immediately , '' the statement added .",
        "Weerakoon maintained close contact with Fonseka when the general led the military during the final phase of last year 's war against Tamil Tiger rebels .",
        "Fonseka was an ally of President Mahinda Rajapakse when the rebel Liberation Tigers of Tamil Eelam -LRB- LTTE -RRB- were crushed in May , but the two men later fell out and contested the presidency in January 's elections .",
        "Fonseka was arrested soon after losing the poll and appeared in front of a court martial this week .",
        "The case was adjourned .",
        "Local and international rights groups have accused Rajapakse of cracking down on dissent , a charge the government has denied ." };

    // this.testSentenceText(sentences, afpProcessedComm);

    // TODO: fix this failure
    // this.testSentenceText(processedSentences, afpProcessedComm);

    // Verify tokens wrt RAW
    this.verifyTokens(afpProcessedComm, true);
    // Verify tokens wrt PROCESSED
    this.verifyTokens(afpProcessedComm, false);

    // Dependency parses
    // Verify non-empty dependency parses
    this.testNDependencyParses(3, afpProcessedComm);

    // Verify some NEs
    assertTrue(afpProcessedComm.getEntitySetList().size() > 0);
    assertTrue(afpProcessedComm.getEntitySetList().get(0).getEntityList()
        .size() > 0);
    boolean allSet = true;
    for (Entity entity : afpProcessedComm.getEntitySetList().get(0)
        .getEntityList())
      allSet &= (entity.getCanonicalName() != null && entity.getCanonicalName()
          .length() > 0);

    assertTrue(allSet);

    // Verify anchor tokens
    int numWithout = 0;
    for (EntityMention em : afpProcessedComm.getEntityMentionSetList().get(0)
        .getMentionList())
      numWithout += (em.getTokens().getAnchorTokenIndex() >= 0 ? 0 : 1);

    assertEquals("Shouldn't be any non-anchor tokens.", 0, numWithout);

    assertTrue("Error in serializing processed communication",
        cs.toBytes(afpProcessedComm) != null);
    new WritableCommunication(afpProcessedComm).writeToFile(
        "src/test/resources/AFP_ENG_20100318.0623_processed.compact.concrete",
        true);
  }

  private void testNDependencyParses(int expected, Communication target) {
    for (Section nsect : target.getSectionList()) {
      if (nsect.isSetSentenceList() && nsect.getKind().equals("Passage"))
        for (Sentence nsent : nsect.getSentenceList()) {
          Tokenization tokenization = nsent.getTokenization();
          assertEquals(expected, tokenization.getDependencyParseList().size());
          for (DependencyParse depParse : tokenization.getDependencyParseList())
            assertTrue("Shouldn't get an empty DepParse.", depParse
                .getDependencyList().size() > 0);
        }
    }
  }

  /**
   * This following test is useful because it uses a number of numeric
   * fractions. The Stanford tokenizer is a bit strange with those: for a
   * fraction of K n/m (e.g., 66 6/16), it will add a non-breaking space in
   * between K and n. Therefore, it may appear that "K n/m" is two tokens, but
   * it really is one. This test primarily verifies that those boundaries are
   * respected in both the token.getText() and token.getText()Span fields.
   */
  @Test
  public void process1999NYTComm() throws Exception {
    Communication nytProcessedComm = this.pipe.annotate(this.nyt1999).getRoot();
    final String processedText = nytProcessedComm.getText();
    final String processedRawText = nytProcessedComm.getOriginalText();

    assertTrue("Communication should have text field set",
        nytProcessedComm.isSetText());

    // Sections
    List<Section> nsects = nytProcessedComm.getSectionList();
    final int correctSections = 16;
    assertEquals("Should have found " + correctSections + ".", correctSections,
        nsects.size());
    verifyNumSections(this.nyt1999, nytProcessedComm);
    verifyTitleSection(nytProcessedComm);

    this.testSectionOffsetsSet(nsects, this.kindsToAnnotate);
    Section first = nsects.get(0);
    TextSpan fts = first.getTextSpan();
    this.testTextSpan(fts, 0, 52);
    logger.info("First text span text: {}", new SuperTextSpan(fts,
        nytProcessedComm).getText());
    Section second = nsects.get(1);
    TextSpan sts = second.getTextSpan();
    this.testTextSpan(sts, 54, 87);
    logger.info("Second text span text: {}", new SuperTextSpan(sts,
        nytProcessedComm).getText());

    // Verify tokens wrt RAW
    this.verifyTokens(nytProcessedComm, true);
    // Verify tokens wrt PROCESSED
    this.verifyTokens(nytProcessedComm, false);

    int nTokens = 0;
    int nTokensDiff = 0;
    CachedTokenizationCommunication ctc = new CachedTokenizationCommunication(nytProcessedComm);
    for (Tokenization tkz : ctc.getTokenizations()) {
      List<Token> tkList = tkz.getTokenList().getTokenList();
      for (Token t : tkList) {
        TextSpan rawTS = t.getRawTextSpan();
        String origTokenText = processedRawText.substring(rawTS.getStart(), rawTS.getEnding());
        if (!t.getText().equals(origTokenText))
          nTokensDiff++;

        nTokens++;
      }
    }

    logger.info("Total tokens checked: {} ; number of differences: {}", nTokens, nTokensDiff);

    // Dependency parses
    // Verify non-empty dependency parses
    this.testNDependencyParses(3, nytProcessedComm);

    // Verify some NEs
    assertTrue(nytProcessedComm.getEntitySetList().size() > 0);
    assertTrue(nytProcessedComm.getEntitySetList().get(0).getEntityList()
        .size() > 0);
    boolean allSet = true;
    for (Entity entity : nytProcessedComm.getEntitySetList().get(0)
        .getEntityList())
      allSet &= (entity.getCanonicalName() != null && entity.getCanonicalName()
          .length() > 0);

    assertTrue(allSet);

    // Verify anchor tokens
    int numWithout = 0;
    for (EntityMention em : nytProcessedComm.getEntityMentionSetList().get(0)
        .getMentionList())
      numWithout += (em.getTokens().getAnchorTokenIndex() >= 0 ? 0 : 1);

    assertEquals("Shouldn't be any non-anchor tokens.", 0, numWithout);
    assertTrue("Error in serializing processed communication",
        cs.toBytes(nytProcessedComm) != null);
    new WritableCommunication(nytProcessedComm)
        .writeToFile(
            "src/test/resources/post-stanford.NYT_ENG_19991220.0301.concrete",
            true);
  }

  /**
   * Test method for
   * {@link edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete#process(edu.jhu.hlt.concrete.Communication)}
   * .
   *
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void processHandshakeCommunicationWithSentences() throws Exception {
    Communication shakeHandComm = this.cf.communication().setText(
        SHAKE_HAND_TEXT_STRING);
    AnnotationMetadata md = new AnnotationMetadata().setTool(
        "concrete-stanford:test").setTimestamp(
        System.currentTimeMillis() / 1000);
    shakeHandComm.setMetadata(md);
    Section section = new Section()
        .setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(0).setEnding(
                SHAKE_HAND_TEXT_STRING.length())).setKind("Passage");
    shakeHandComm.addToSectionList(section);
    Sentence sentence = new Sentence().setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(0).setEnding(
                SHAKE_HAND_TEXT_STRING.length()));
    section.addToSentenceList(sentence);

    assertTrue("Error in creating original communication",
        cs.toBytes(shakeHandComm) != null);

    Communication processedShakeHandComm = pipe.annotate(shakeHandComm).getRoot();
    assertTrue("Error in serializing processed communication",
        cs.toBytes(processedShakeHandComm) != null);
    final String docText = processedShakeHandComm.getOriginalText();
    final String[] stokens = { "The", "man", "ran", "to", "shake", "the",
        "U.S.", "President", "'s", "hand", "." };

    assertTrue(docText.equals(AnnotateNonTokenizedConcreteTest.SHAKE_HAND_TEXT_STRING));

    // Sections
    verifyNumSections(shakeHandComm, processedShakeHandComm);

    final Section firstSection = processedShakeHandComm.getSectionList().get(0);
    final List<Sentence> firstSentList = firstSection.getSentenceList();
    final Sentence firstSent = firstSentList.get(0);
    final Tokenization firstTokenization = firstSent.getTokenization();
    assertTrue(firstSentList.size() == 1);

    // Test spans
    assertTrue(firstSentList.size() == 1);
    assertTrue("firstSent.rawTextSpan should be set",
        firstSent.isSetRawTextSpan());
    assertEquals("Beginning char should be 0.", 0, firstSent.getRawTextSpan()
        .getStart());
    assertEquals("SHAKE_HAND_TEXT_STRING has a different length.", 49,
        SHAKE_HAND_TEXT_STRING.length());
    assertEquals("Ending char should be 49.", 49, firstSent.getRawTextSpan()
        .getEnding());

    TextSpan tts = firstSent.getRawTextSpan();
    String pulledText = docText.substring(tts.getStart(), tts.getEnding());
    assertEquals(pulledText.trim(), SHAKE_HAND_TEXT_STRING.trim());

    // Test # Tokens
    StringBuilder actualTokensSB = new StringBuilder();
    for (Token tok : firstTokenization.getTokenList().getTokenList()) {
      actualTokensSB.append("(" + tok.getText() + ", " + tok.getTokenIndex()
          + ") ");
    }
    assertTrue("Expected tokens length = " + stokens.length + ";"
        + "Actual   tokens length = "
        + firstTokenization.getTokenList().getTokenList().size() + "; "
        + "Actual tokens = " + actualTokensSB.toString(), firstTokenization
        .getTokenList().getTokenList().size() == stokens.length);

    // Verify tokens
    int tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      assertTrue(
          "tokIdx = " + tokIdx + "; token.getTokenIndex() = "
              + token.getTokenIndex(), token.getTokenIndex() == tokIdx);
      assertTrue("expected = [" + stokens[tokIdx] + "]; token.getText() = ["
          + token.getText() + "]", token.getText().equals(stokens[tokIdx]));
      tokIdx++;
    }

    // Verify tokens to full
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + token.getText() + "];" + "docText(" + tts
          + ") = [" + substr + "]", token.getText().equals(substr));
    }

    // Verify tokens to full seeded
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + stokens[tokIdx] + "];" + "docText(" + tts
          + ") = [" + substr + "]", stokens[tokIdx].equals(substr));
      tokIdx++;
    }

    // Verify token spans
    int[] start = { 0, 4, 8, 12, 15, 21, 25, 31, 40, 43, 47 };
    int[] end = { 3, 7, 11, 14, 20, 24, 29, 40, 42, 47, 48 };
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      assertTrue(
          token.getText() + "(" + tokIdx + ") starts at " + tts.getStart()
              + "; it should start at " + start[tokIdx],
          tts.getStart() == start[tokIdx]);
      assertTrue(
          token.getText() + "(" + tokIdx + ") starts at " + tts.getEnding()
              + "; it should start at " + end[tokIdx],
          tts.getEnding() == end[tokIdx]);
      tokIdx++;
    }

    // Verify # entities
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    assertEquals("Should be three entities.", 3, processedShakeHandComm
        .getEntitySetList().get(0).getEntityList().size());

    // Verify # Singleton entities
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      assertEquals(entity.getCanonicalName() + " is not singleton", 1, entity
          .getMentionIdList().size());
    }

    // Verify entity names
    Set<String> expEnts = new HashSet<String>();
    expEnts.add("The man");
    expEnts.add("the U.S. President 's hand");
    expEnts.add("the U.S. President 's");
    Set<String> seenEnts = new HashSet<String>();
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      seenEnts.add(entity.getCanonicalName());
    }
    assertTrue(seenEnts.equals(expEnts));

    // Verify some canonical entity names
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    boolean atLeastOne = false;
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      atLeastOne |= (entity.getCanonicalName() != null && entity
          .getCanonicalName().length() > 0);
    }
    assertTrue(atLeastOne);

    // Verify metadata toolnames
    // this.verifyToolNames(processedShakeHandComm);
  }

  /**
   * Test method for
   * {@link edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete#process(edu.jhu.hlt.concrete.Communication)}
   * .
   *
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void processHandshakeCommunicationWithRepeatedSentences() throws Exception {
    String SHAKE_HAND_TEXT_STRING_1 = "The boy ran to shake the U.S. \nPresident's hand. ";
    String SHAKE_HAND_TEXT_STRING_2 = "The dog ran to shake the U.S. \nPresident's hand.";
    final int eachSentenceLength = SHAKE_HAND_TEXT_STRING.length() - 1;
    assertEquals(48, eachSentenceLength);
    final String origCommText = SHAKE_HAND_TEXT_STRING
        + SHAKE_HAND_TEXT_STRING_1 + SHAKE_HAND_TEXT_STRING_2;
    Communication shakeHandComm = this.cf.communication().setText(origCommText);
    AnnotationMetadata md = new AnnotationMetadata().setTool(
        "concrete-stanford:test").setTimestamp(
        System.currentTimeMillis() / 1000);
    shakeHandComm.setMetadata(md);
    Section section1 = new Section()
        .setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(0).setEnding(2 * eachSentenceLength + 1))
        .setKind("Passage");
    section1.addToSentenceList(new Sentence().setUuid(UUIDFactory.newUUID())
        .setTextSpan(new TextSpan().setStart(0).setEnding(eachSentenceLength)));
    section1.addToSentenceList(new Sentence().setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(eachSentenceLength + 1).setEnding(
                1 + 2 * eachSentenceLength)));
    shakeHandComm.addToSectionList(section1);
    // Section 2
    int section2Start = 2 * eachSentenceLength + 2;
    assertEquals(98, section2Start);
    int section2End = 3 * eachSentenceLength + 2;
    assertEquals(146, section2End);
    Section section2 = new Section()
        .setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(section2Start).setEnding(section2End))
        .setKind("Passage");
    section2.addToSentenceList(new Sentence().setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(section2Start).setEnding(section2End)));
    shakeHandComm.addToSectionList(section2);

    assertTrue("Error in creating original communication",
        cs.toBytes(shakeHandComm) != null);

    Communication processedShakeHandComm = pipe.annotate(shakeHandComm).getRoot();
    assertTrue("Error in serializing processed communication",
        cs.toBytes(processedShakeHandComm) != null);
    final String docText = processedShakeHandComm.getOriginalText();
    // final String[] stokens = { "The", "man", "ran", "to", "shake", "the",
    // "U.S.", "President", "'s", "hand", ".", "The", "boy", "ran", "to",
    // "shake", "the", "U.S.", "President", "'s", "hand", ".", "The", "dog",
    // "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "." };
    final String[] stokens = { "The", "dog", "ran", "to", "shake", "the",
        "U.S.", "President", "'s", "hand", "." };

    assertTrue(docText.equals(origCommText));

    // Sections
    assertEquals("Processed communication should have 2 sections; it has "
        + processedShakeHandComm.getSectionList().size(), 2,
        processedShakeHandComm.getSectionList().size());
    verifyNumSections(shakeHandComm, processedShakeHandComm);

    assertTrue(processedShakeHandComm.getSectionList().get(0).getSentenceList()
        .size() == 2);
    final Section secondSection = processedShakeHandComm.getSectionList()
        .get(1);
    final List<Sentence> firstSentList = secondSection.getSentenceList();
    assertTrue(firstSentList.size() == 1);
    final Sentence firstSent = firstSentList.get(0);
    final Tokenization firstTokenization = firstSent.getTokenization();

    // Test spans
    assertTrue(firstSentList.size() == 1);
    assertTrue("firstSent.rawTextSpan should be set",
        firstSent.isSetRawTextSpan());
    assertEquals("Beginning char should be 98.", section2Start, firstSent
        .getRawTextSpan().getStart());
    assertEquals("Ending char should be 146.", section2End, firstSent
        .getRawTextSpan().getEnding());

    TextSpan tts = firstSent.getRawTextSpan();
    String pulledText = docText.substring(tts.getStart(), tts.getEnding());
    assertTrue("Received " + pulledText + ", should have gotten "
        + SHAKE_HAND_TEXT_STRING_2,
        pulledText.equals(SHAKE_HAND_TEXT_STRING_2.trim()));

    // Test # Tokens
    StringBuilder actualTokensSB = new StringBuilder();
    for (Token tok : firstTokenization.getTokenList().getTokenList()) {
      actualTokensSB.append("(" + tok.getText() + ", " + tok.getTokenIndex()
          + ") ");
    }
    assertTrue("Expected tokens length = " + stokens.length + ";"
        + "Actual   tokens length = "
        + firstTokenization.getTokenList().getTokenList().size() + "; "
        + "Actual tokens = " + actualTokensSB.toString(), firstTokenization
        .getTokenList().getTokenList().size() == stokens.length);

    // Verify tokens
    int tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      assertTrue(
          "tokIdx = " + tokIdx + "; token.getTokenIndex() = "
              + token.getTokenIndex(), token.getTokenIndex() == tokIdx);
      assertTrue("expected = [" + stokens[tokIdx] + "]; token.getText() = ["
          + token.getText() + "]", token.getText().equals(stokens[tokIdx]));
      tokIdx++;
    }

    // Verify tokens to full
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + token.getText() + "];" + "docText(" + tts
          + ") = [" + substr + "]", token.getText().equals(substr));
    }

    // Verify tokens to full seeded
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + stokens[tokIdx] + "];" + "docText(" + tts
          + ") = [" + substr + "]", stokens[tokIdx].equals(substr));
      tokIdx++;
    }

    // Verify token spans
    int[] start = { 0, 4, 8, 12, 15, 21, 25, 31, 40, 43, 47 };
    int[] end = { 3, 7, 11, 14, 20, 24, 29, 40, 42, 47, 48 };
    // record the original offset
    int oOffset = 98;
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      assertTrue(
          token.getText() + "(" + tokIdx + ") starts at " + tts.getStart()
              + "; it should start at " + start[tokIdx] + oOffset,
          tts.getStart() == start[tokIdx] + oOffset);
      assertTrue(
          token.getText() + "(" + tokIdx + ") starts at " + tts.getEnding()
              + "; it should start at " + end[tokIdx] + oOffset,
          tts.getEnding() == end[tokIdx] + oOffset);
      tokIdx++;
    }

    // Verify # entities
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    assertEquals("Should be 5 entities.", 5, processedShakeHandComm
        .getEntitySetList().get(0).getEntityList().size());

    // Verify entity names
    Set<String> expEnts = new HashSet<String>();
    expEnts.add("The boy");
    expEnts.add("The dog");
    expEnts.add("The man");
    expEnts.add("the U.S. President 's hand");
    expEnts.add("the U.S. President 's");
    Set<String> seenEnts = new HashSet<String>();
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      seenEnts.add(entity.getCanonicalName());
    }
    assertTrue(seenEnts.equals(expEnts));

    // Verify some canonical entity names
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    boolean atLeastOne = false;
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      atLeastOne |= (entity.getCanonicalName() != null && entity
          .getCanonicalName().length() > 0);
    }
    assertTrue(atLeastOne);

    // Verify metadata toolnames
    // this.verifyToolNames(processedShakeHandComm);
    new WritableCommunication(processedShakeHandComm)
        .writeToFile(
            "src/test/resources/post-stanford.handshake_multiple_sections.concrete",
            true);
  }

  /**
   * Test method for
   * {@link edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete#process(edu.jhu.hlt.concrete.Communication)}
   * .
   *
   * @throws ConcreteException
   * @throws IOException
   */
  @Test
  public void processHandshakeCommunicationWithRepeatedSentencesAndTitleSection() throws Exception{
    String SHAKE_HAND_TEXT_STRING_1 = "The boy ran to shake the U.S. \nPresident's hand. ";
    String SHAKE_HAND_TEXT_STRING_2 = "The dog ran to shake the U.S. \nPresident's hand.";
    final int eachSentenceLength = SHAKE_HAND_TEXT_STRING.length() - 1;
    assertEquals(48, eachSentenceLength);
    final String origCommText = SHAKE_HAND_TEXT_STRING
        + SHAKE_HAND_TEXT_STRING_1 + SHAKE_HAND_TEXT_STRING_2;
    Communication shakeHandComm = this.cf.communication().setText(origCommText);
    AnnotationMetadata md = new AnnotationMetadata().setTool(
        "concrete-stanford:test").setTimestamp(
        System.currentTimeMillis() / 1000);
    shakeHandComm.setMetadata(md);
    Section section1 = new Section()
        .setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(0).setEnding(2 * eachSentenceLength + 1))
        .setKind("Title");
    section1.addToSentenceList(new Sentence().setUuid(UUIDFactory.newUUID())
        .setTextSpan(new TextSpan().setStart(0).setEnding(eachSentenceLength)));
    section1.addToSentenceList(new Sentence().setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(eachSentenceLength + 1).setEnding(
                1 + 2 * eachSentenceLength)));
    shakeHandComm.addToSectionList(section1);
    // Section 2
    int section2Start = 2 * eachSentenceLength + 2;
    assertEquals(98, section2Start);
    int section2End = 3 * eachSentenceLength + 2;
    assertEquals(146, section2End);
    Section section2 = new Section()
        .setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(section2Start).setEnding(section2End))
        .setKind("Passage");
    section2.addToSentenceList(new Sentence().setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(section2Start).setEnding(section2End)));
    shakeHandComm.addToSectionList(section2);

    assertTrue("Error in creating original communication",
        cs.toBytes(shakeHandComm) != null);

    Communication processedShakeHandComm = pipe.annotate(shakeHandComm).getRoot();
    assertTrue("Error in serializing processed communication",
        cs.toBytes(processedShakeHandComm) != null);
    final String docText = processedShakeHandComm.getOriginalText();
    // final String[] stokens = { "The", "man", "ran", "to", "shake", "the",
    // "U.S.", "President", "'s", "hand", ".", "The", "boy", "ran", "to",
    // "shake", "the", "U.S.", "President", "'s", "hand", ".", "The", "dog",
    // "ran", "to", "shake", "the", "U.S.", "President", "'s", "hand", "." };
    final String[] stokens = { "The", "dog", "ran", "to", "shake", "the",
        "U.S.", "President", "'s", "hand", "." };

    assertTrue(docText.equals(origCommText));

    // Sections
    assertEquals("Processed communication should have 2 sections; it has "
        + processedShakeHandComm.getSectionList().size(), 2,
        processedShakeHandComm.getSectionList().size());
    verifyNumSections(shakeHandComm, processedShakeHandComm);

    assertTrue(processedShakeHandComm.getSectionList().get(0).getSentenceList()
        .size() == 2);
    final Section secondSection = processedShakeHandComm.getSectionList()
        .get(1);
    final List<Sentence> firstSentList = secondSection.getSentenceList();
    assertTrue(firstSentList.size() == 1);
    final Sentence firstSent = firstSentList.get(0);
    final Tokenization firstTokenization = firstSent.getTokenization();

    // Test spans
    assertTrue(firstSentList.size() == 1);
    assertTrue("firstSent.rawTextSpan should be set",
        firstSent.isSetRawTextSpan());
    assertEquals("Beginning char should be 98.", section2Start, firstSent
        .getRawTextSpan().getStart());
    assertEquals("Ending char should be 146.", section2End, firstSent
        .getRawTextSpan().getEnding());

    TextSpan tts = firstSent.getRawTextSpan();
    String pulledText = docText.substring(tts.getStart(), tts.getEnding());
    assertTrue("Received " + pulledText + ", should have gotten "
        + SHAKE_HAND_TEXT_STRING_2,
        pulledText.equals(SHAKE_HAND_TEXT_STRING_2.trim()));

    // Test # Tokens
    StringBuilder actualTokensSB = new StringBuilder();
    for (Token tok : firstTokenization.getTokenList().getTokenList()) {
      actualTokensSB.append("(" + tok.getText() + ", " + tok.getTokenIndex()
          + ") ");
    }
    assertTrue("Expected tokens length = " + stokens.length + ";"
        + "Actual   tokens length = "
        + firstTokenization.getTokenList().getTokenList().size() + "; "
        + "Actual tokens = " + actualTokensSB.toString(), firstTokenization
        .getTokenList().getTokenList().size() == stokens.length);

    // Verify tokens
    int tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      assertTrue(
          "tokIdx = " + tokIdx + "; token.getTokenIndex() = "
              + token.getTokenIndex(), token.getTokenIndex() == tokIdx);
      assertTrue("expected = [" + stokens[tokIdx] + "]; token.getText() = ["
          + token.getText() + "]", token.getText().equals(stokens[tokIdx]));
      tokIdx++;
    }

    // Verify tokens to full
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + token.getText() + "];" + "docText(" + tts
          + ") = [" + substr + "]", token.getText().equals(substr));
    }

    // Verify tokens to full seeded
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      String substr = docText.substring(tts.getStart(), tts.getEnding());
      assertTrue("expected = [" + stokens[tokIdx] + "];" + "docText(" + tts
          + ") = [" + substr + "]", stokens[tokIdx].equals(substr));
      tokIdx++;
    }

    // Verify token spans
    int[] start = { 0, 4, 8, 12, 15, 21, 25, 31, 40, 43, 47 };
    int[] end = { 3, 7, 11, 14, 20, 24, 29, 40, 42, 47, 48 };
    // record the original offset
    int oOffset = 98;
    tokIdx = 0;
    for (Token token : firstTokenization.getTokenList().getTokenList()) {
      tts = token.getRawTextSpan();
      assertTrue(
          token.getText() + "(" + tokIdx + ") starts at " + tts.getStart()
              + "; it should start at " + start[tokIdx] + oOffset,
          tts.getStart() == start[tokIdx] + oOffset);
      assertTrue(
          token.getText() + "(" + tokIdx + ") starts at " + tts.getEnding()
              + "; it should start at " + end[tokIdx] + oOffset,
          tts.getEnding() == end[tokIdx] + oOffset);
      tokIdx++;
    }

    // Verify # entities
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    assertEquals("Should be 3 entities.", 3, processedShakeHandComm
        .getEntitySetList().get(0).getEntityList().size());

    // Verify entity names
    Set<String> expEnts = new HashSet<String>();
    expEnts.add("The dog");
    expEnts.add("the U.S. President 's hand");
    expEnts.add("the U.S. President 's");
    Set<String> seenEnts = new HashSet<String>();
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      seenEnts.add(entity.getCanonicalName());
    }
    assertTrue(seenEnts.equals(expEnts));

    // Verify some canonical entity names
    assertTrue(processedShakeHandComm.getEntitySetList().size() > 0);
    boolean atLeastOne = false;
    for (Entity entity : processedShakeHandComm.getEntitySetList().get(0)
        .getEntityList()) {
      atLeastOne |= (entity.getCanonicalName() != null && entity
          .getCanonicalName().length() > 0);
    }
    assertTrue(atLeastOne);

    // Verify metadata toolnames
    // this.verifyToolNames(processedShakeHandComm);
  }

  // @Test
  // public void processWonkyNYTComm() throws Exception {
  // Communication nytProcessedComm = this.pipe.process(this.wonkyNYT);
  // final String processedText = nytProcessedComm.getText();
  // final String processedRawText = nytProcessedComm.getOriginalText();

  // // Text equality
  // //assertEquals("Text should be equal.", AFP_0623_TEXT, processedRawText);
  // assertTrue("Communication should have text field set",
  // nytProcessedComm.isSetText());

  // // Sections
  // List<Section> nsects = nytProcessedComm.getSectionList();
  // assertEquals("Should have found 28 sections (including title): has " +
  // nsects.size(),
  // 28, nsects.size());

  // // First sentence span test wrt RAW
  // // {
  // // int begin = 60;
  // // int end = 242;
  // // Sentence sent =
  // afpProcessedComm.getSectionList().get(1).getSentenceSegmentationList()
  // // .get(0).getSentenceList().get(0);
  // // TextSpan tts = sent.getRawTextSpan();
  // // assertEquals("Start should be " + begin, begin, tts.getStart());
  // // assertEquals("End should be " + end, end, tts.getEnding());
  // // }

  // // Verify tokens wrt RAW
  // {
  // int numEq = 0;
  // int numTot = 0;
  // // wrt the raw text
  // for (Section nsect : nytProcessedComm.getSectionList()) {
  // if (nsect.getSentenceSegmentationList() == null)
  // continue;
  // for (Sentence nsent : nsect.getSentenceList()) {
  // for (Token token :
  // nsent.getTokenizationList().get(0).getTokenList().getTokenList()) {
  // TextSpan span = token.getRawTextSpan();
  // String substr = processedRawText.substring(span.getStart(),
  // span.getEnding());
  // boolean areEq = token.getText().equals(substr);
  // if (!areEq) {
  // logger.warn("expected = [" + token.getText() + "];" + "docText(" + span +
  // ") = [" + substr + "]");
  // } else {
  // numEq++;
  // }
  // numTot++;
  // }
  // }
  // }
  // double fracPassing = ((double) numEq / (double) numTot);
  // assertTrue("WARNING: only " + fracPassing + "% of tokens matched!",
  // fracPassing >= 0.8);
  // }
  // // Verify tokens wrt PROCESSED
  // {
  // int numEq = 0;
  // int numTot = 0;
  // // wrt the processed text
  // for (Section nsect : nytProcessedComm.getSectionList()) {
  // if (nsect.getSentenceSegmentationList() == null)
  // continue;
  // for (Sentence nsent : nsect.getSentenceList()) {
  // for (Token token :
  // nsent.getTokenizationList().get(0).getTokenList().getTokenList()) {
  // assertTrue("token " + token.getTokenIndex() +
  // " shouldn't have null textspan",
  // token.isSetTextSpan());
  // TextSpan span = token.getTextSpan();
  // assertTrue("ending " + span.getEnding() +
  // " is out of range ("+processedText.length()+")",
  // span.getEnding() < processedText.length());
  // String substr = processedText.substring(span.getStart(), span.getEnding());
  // boolean areEq = token.getText().equals(substr);
  // if (!areEq) {
  // logger.warn("expected = [" + token.getText() + "];" + " docText(" + span +
  // ") = [" + substr + "]");
  // } else {
  // numEq++;
  // }
  // numTot++;
  // }
  // }
  // }
  // double fracPassing = ((double) numEq / (double) numTot);
  // assertTrue("WARNING: only " + fracPassing + "% of tokens matched!",
  // fracPassing == 1.0);
  // }

  // // Dependency parses
  // {
  // int expNumDepParses = 3;
  // for (Section nsect : nytProcessedComm.getSectionList()) {
  // if (nsect.getSentenceSegmentationList() == null)
  // continue;
  // for (Sentence nsent : nsect.getSentenceList()) {
  // Tokenization tokenization = nsent.getTokenizationList().get(0);
  // assertEquals(expNumDepParses,
  // tokenization.getDependencyParseList().size());
  // }
  // }
  // }

  // // Verify non-empty dependency parses
  // {
  // for (Section nsect : nytProcessedComm.getSectionList()) {
  // if (nsect.getSentenceSegmentationList() == null)
  // continue;
  // for (Sentence nsent : nsect.getSentenceList()) {
  // Tokenization tokenization = nsent.getTokenizationList().get(0);
  // for (DependencyParse depParse : tokenization.getDependencyParseList()) {
  // assertTrue("DependencyParse " + depParse.getMetadata().getTool() +
  // " is empty", depParse.getDependencyList().size() > 0);
  // }
  // }
  // }
  // }

  // // Verify some NEs
  // {
  // assertTrue(nytProcessedComm.getEntitySetList().size() > 0);
  // assertTrue(nytProcessedComm.getEntitySetList().get(0).getEntityList().size()
  // > 0);
  // boolean allSet = true;
  // for (Entity entity :
  // nytProcessedComm.getEntitySetList().get(0).getEntityList()) {
  // allSet &= (entity.getCanonicalName() != null &&
  // entity.getCanonicalName().length() > 0);
  // }
  // assertTrue(allSet);
  // }

  // // Verify anchor tokens
  // {
  // int numWithout = 0;
  // for (EntityMention em :
  // nytProcessedComm.getEntityMentionSetList().get(0).getMentionList()) {
  // numWithout += (em.getTokens().anchorTokenIndex >= 0 ? 0 : 1);
  // }
  // assertEquals("Shouldn't be any non-anchor tokens.", 0, numWithout);
  // }

  // {
  // assertTrue("Error in serializing processed communication",
  // new CommunicationSerialization().toBytes(nytProcessedComm) != null);
  // }
  // }

  // TODO: needs enabling/fixing.
  // @Test
  // public void testNYTMessage() throws Exception {
  // Communication processedNYT = this.pipe.process(this.wonkyNYT);
  // new
  // WritableCommunication(processedNYT).writeToFile("target/test-nyt-out.concrete",
  // true);
  // }
}
