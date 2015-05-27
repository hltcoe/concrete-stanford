/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nytlabs.corpus.NYTCorpusDocument;
import com.nytlabs.corpus.NYTCorpusDocumentParser;

import edu.jhu.hlt.annotatednyt.AnnotatedNYTDocument;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.ingesters.annotatednyt.CommunicationizableAnnotatedNYTDocument;
import edu.jhu.hlt.concrete.miscommunication.sectioned.CachedSectionedCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.utilt.sys.SystemErrDisabler;

/**
 *
 */
public class TestAnnotatedNYTData {

  private static final Logger logger = LoggerFactory.getLogger(TestAnnotatedNYTData.class);

  final NYTCorpusDocumentParser parser = new NYTCorpusDocumentParser();
  final AnnotateNonTokenizedConcrete pipe = new AnnotateNonTokenizedConcrete();

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
  }

  private Communication extractNYTDoc(Path p) throws Exception {
    byte[] ba = Files.readAllBytes(p);
    NYTCorpusDocument nytd = parser.fromByteArray(ba, false);
    AnnotatedNYTDocument ad = new AnnotatedNYTDocument(nytd);
    Communication c = new CommunicationizableAnnotatedNYTDocument(ad).toCommunication();
    CachedSectionedCommunication csc = new CachedSectionedCommunication(c);
    assertEquals(csc.getRoot(), c);
    return c;
  }

  @Test
  public void testFirstNYTDoc() throws Exception {
    SystemErrDisabler dis = new SystemErrDisabler();
    dis.disable();
    Path p = Paths.get("src/test/resources/0.xml");
    Communication c = this.extractNYTDoc(p);
    Communication fromStanford = pipe.annotate(c).getRoot();
    String rawOrigText = fromStanford.getOriginalText();
    assertEquals(c.getText(), rawOrigText);
    CachedTokenizationCommunication ctc = new CachedTokenizationCommunication(fromStanford);
    int nTokens = 0;
    int nTokensDiff = 0;
    for (Tokenization tkz : ctc.getTokenizations()) {
      List<Token> tkList = tkz.getTokenList().getTokenList();
      for (Token t : tkList) {
        TextSpan rawTS = t.getRawTextSpan();
        String origTokenText = rawOrigText.substring(rawTS.getStart(), rawTS.getEnding());
        String grabbed = fromStanford.getText().substring(t.getTextSpan().getStart(), t.getTextSpan().getEnding());
        String ttxt = t.getText();
        logger.debug("Examining token: {}, {}, {}, {}, {}", ttxt, grabbed, origTokenText, t.getTextSpan(), rawTS);
        if (!ttxt.equals(origTokenText)) {
          logger.warn("Got different tokenization texts: new {} vs. orig. {}", ttxt, origTokenText);
          nTokensDiff++;
        }

        nTokens++;
      }
    }

    logger.info("Total tokens checked: {} ; number of differences: {}", nTokens, nTokensDiff);
  }

  @Test
  public void testOddNYTDoc() throws Exception {
    SystemErrDisabler dis = new SystemErrDisabler();
    dis.disable();
    Path p = Paths.get("src/test/resources/1000052.xml");
    Communication c = this.extractNYTDoc(p);
    logger.info("Got text: {}", c.getText());
    Communication fromStanford = pipe.annotate(c).getRoot();
    String rawOrigText = fromStanford.getOriginalText();
    assertEquals(c.getText(), rawOrigText);
    logger.info("New text: {}", fromStanford.getText());
  }

  @Test
  public void testNYT() throws Exception{
    Path p = Paths.get("src/test/resources/1012740.xml");
    Communication c = this.extractNYTDoc(p);
    Section first = c.getSectionListIterator().next();

    Communication fromStanford = pipe.annotate(c).getRoot();
    CachedSectionedCommunication cscPost = new CachedSectionedCommunication(fromStanford);
    Communication sRoot = cscPost.getRoot();
    logger.info("New text: {}", sRoot.getText());
    assertEquals(first.getTextSpan(), sRoot.getSectionListIterator().next().getTextSpan());

//    for (Section s : cscPost.getSections()) {
//      SuperTextSpan sts = new SuperTextSpan(s.getTextSpan(), sRoot);
//      logger.info("On section: {}", s.toString());
//      logger.info("Got TextSpan text: {}", sts.getText());
//      logger.info("Does it have sentences? {}", s.isSetSentenceList());
//    }
//
//    CachedTokenizationCommunication ctkc = new CachedTokenizationCommunication(fromStanford);
//    final Communication tkroot = ctkc.getRoot();
//    for (Section s : ctkc.getSections()) {
//      SuperTextSpan sts = new SuperTextSpan(s.getTextSpan(), tkroot);
//      logger.info("Got new section TextSpan text: {}", sts.getText());
//    }
//
//    for (Sentence s : ctkc.getSentences()) {
//      SuperTextSpan sts = new SuperTextSpan(s.getTextSpan(), tkroot);
//      logger.info("Got new sentence TextSpan text: {}", sts.getText());
//    }
  }
}
