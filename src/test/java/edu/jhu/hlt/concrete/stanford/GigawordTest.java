/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.validation.CommunicationValidator;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TaggedToken;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.communications.WritableCommunication;
import edu.jhu.hlt.concrete.ingesters.gigaword.GigawordDocumentConverter;
import edu.jhu.hlt.concrete.miscommunication.sectioned.CachedSectionedCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.util.SuperTextSpan;

/**
 *
 */
public class GigawordTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(GigawordTest.class);

  Path p = Paths.get("src/test/resources/serif_dateline.sgml");
  Communication comm;
  ConcreteStanfordTokensSentenceAnalytic analytic;
  ConcreteStanfordPreCorefAnalytic preCorefAnalytic;

  public Set<String> ptbTokens = new HashSet<>();

  @Before
  public void setUp() throws Exception {
    this.comm = new GigawordDocumentConverter().fromPath(this.p);
    LOGGER.info("Loaded comm: {} [UUID: {}]", comm.getId(), comm.getUuid().getUuidString());

    ptbTokens.add("-LRB-");
    ptbTokens.add("-RRB-");
  }

  @Rule
  public TemporaryFolder tf = new TemporaryFolder();

  private static void assertSectionListEquality(List<Section> l1, List<Section> l2) {
    assertEquals(l1.size(), l2.size());
    for (int i = 0; i < l1.size(); i++) {
      Section os = l1.get(i);
      Section as = l2.get(i);
      assertEquals(os.getUuid().getUuidString(), as.getUuid().getUuidString());
      assertEquals(os.getTextSpan(), as.getTextSpan());
    }
  }

  @Test
  public void markupRemoval() throws Exception {
    String commText = this.comm.getText();
    String nt = MarkupRewriter.removeMarkup(commText);
    assertEquals(commText.length(), nt.length());
  }

  @Test
  public void gigawordDocument() throws Exception {
    this.analytic = new ConcreteStanfordTokensSentenceAnalytic();
    this.preCorefAnalytic = new ConcreteStanfordPreCorefAnalytic(PipelineLanguage.ENGLISH);

    CachedSectionedCommunication csc = new CachedSectionedCommunication(this.comm);
    TokenizedCommunication tkzc = this.analytic.annotate(csc);
    Communication newComm = tkzc.getRoot();
    List<Section> osList = tkzc.getSections();
    osList.forEach(sect -> {
      final Section ns = new Section(sect);
      ns.unsetSentenceList();
      LOGGER.info("Got section: {}", ns.toString());

      sect.getSentenceList().forEach(sent -> {
        final Sentence nsent = new Sentence(sent);
        nsent.unsetTokenization();

        LOGGER.info("Got sentence: {}", nsent.toString());

        sent.getTokenization().getTokenList().getTokenList().forEach(tok -> {
          LOGGER.info("Got token: {}", tok);
          String tokTxt = tok.getText();
          if (!this.ptbTokens.contains(tokTxt))
            assertEquals("Token text and text from textspan should be equal.", tok.getText(), new SuperTextSpan(tok.getTextSpan(), newComm).getText());
        });
      });
    });

    Communication nRoot = tkzc.getRoot();
    assertTrue(new CommunicationValidator(nRoot).validate());
    assertEquals(this.comm.getText(), nRoot.getText());
    List<Section> asList = nRoot.getSectionList();
    assertSectionListEquality(osList, asList);

    Section firstAnnotated = asList.get(0);
    TextSpan fSectTs = firstAnnotated.getTextSpan();
    LOGGER.info("Got section TS: {}", fSectTs);
    LOGGER.info("Got section text: {}", new SuperTextSpan(fSectTs, nRoot).getText());
    List<Sentence> aSentList = firstAnnotated.getSentenceList();
    assertEquals(1, aSentList.size());
    Sentence firstSent = aSentList.get(0);
    LOGGER.info("Got sentence text: {}", new SuperTextSpan(firstSent.getTextSpan(), nRoot).getText());
    assertEquals(firstAnnotated.getTextSpan(), firstSent.getTextSpan());
    Tokenization tkz = firstSent.getTokenization();
    for (Token t : tkz.getTokenList().getTokenList()) {
      TextSpan tts = t.getTextSpan();
      // LOGGER.info("Got Token TS: {}", tts);
    }

    Section secondSect = asList.get(1);
    TextSpan sSectTS = secondSect.getTextSpan();
    LOGGER.info("Got 2nd Section TS: {}", sSectTS);
    assertEquals(1, secondSect.getSentenceListSize());

    for (Token t : secondSect.getSentenceListIterator().next().getTokenization().getTokenList().getTokenList()) {
      TextSpan tts = t.getTextSpan();
      // LOGGER.info("Got token TS: {}", tts);
    }

    Section thirdSect = asList.get(2);
    TextSpan tSectTS = thirdSect.getTextSpan();
    LOGGER.info("Got 3rd Section TS: {}", tSectTS);
    assertEquals(2, thirdSect.getSentenceListSize());
    for (Sentence st : thirdSect.getSentenceList()) {
      Tokenization ltkz = st.getTokenization();
      for (Token t : ltkz.getTokenList().getTokenList()) {
        TextSpan tts = t.getTextSpan();
        // LOGGER.info("Got token TS: {}", tts);
      }

      LOGGER.info("Any tagged tokens? {}", ltkz.isSetTokenTaggingList());
    }

    LOGGER.debug("Prepping pre-coref.");
    TokenizedCommunication preCoref = this.preCorefAnalytic.annotate(tkzc);
    CommunicationValidator cv = new CommunicationValidator(preCoref.getRoot());
    assertTrue(cv.validate());
    Communication preCorefRoot = preCoref.getRoot();
    List<Sentence> allOldSents = tkzc.getSentences();
    List<Sentence> allNewSents = preCoref.getSentences();
    allOldSents.forEach(sent -> {
      // LOGGER.info("Got old sent: {}", sent);
    });
    LOGGER.info("");
    allNewSents.forEach(sent -> {
      // LOGGER.info("Got new sent: {}", sent);
    });
    assertEquals(allOldSents.size(), allNewSents.size());
    for (int i = 0; i < allOldSents.size(); i++) {
      Sentence oSent = allOldSents.get(i);
      Sentence nSent = allNewSents.get(i);
      assertEquals(oSent.getTextSpan(), nSent.getTextSpan());
      assertEquals(oSent.getUuid().getUuidString(), nSent.getUuid().getUuidString());
    }

    Section newThirdSect = preCorefRoot.getSectionList().get(2);
    final TextSpan ntsts = newThirdSect.getTextSpan();
    LOGGER.info("Got new 3rd Section TS: {}", ntsts);
    for (Sentence st : newThirdSect.getSentenceList()) {
      Tokenization ltkz = st.getTokenization();
      for (Token t : ltkz.getTokenList().getTokenList()) {
        TextSpan tts = t.getTextSpan();
        // LOGGER.info("Got token TS: {}", tts);
      }

      LOGGER.info("Any tagged tokens? {}", ltkz.isSetTokenTaggingList());
      List<TokenTagging> ttList = ltkz.getTokenTaggingList();
      assertEquals(3, ttList.size());
      LOGGER.info("Tagged tokens:");
      for (TokenTagging tt : ttList) {
        for (TaggedToken tagt : tt.getTaggedTokenList()) {
          LOGGER.info("Got tagged token: {}", tagt.toString());
        }
      }
    }

    new WritableCommunication(preCorefRoot).writeToFile(this.tf.getRoot().toPath(), true);
  }
}
