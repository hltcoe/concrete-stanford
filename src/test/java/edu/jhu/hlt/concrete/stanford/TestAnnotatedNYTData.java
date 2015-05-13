/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
import edu.jhu.hlt.concrete.ingesters.annotatednyt.CommunicationizableAnnotatedNYTDocument;
import edu.jhu.hlt.concrete.miscommunication.sectioned.CachedSectionedCommunication;
import edu.jhu.hlt.concrete.util.SuperTextSpan;

/**
 *
 */
public class TestAnnotatedNYTData {

  private static final Logger logger = LoggerFactory.getLogger(TestAnnotatedNYTData.class);

  final NYTCorpusDocumentParser parser = new NYTCorpusDocumentParser();

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

  private Communication extractNYTDoc() throws Exception {
    Path p = Paths.get("src/test/resources/1012740.xml");
    byte[] ba = Files.readAllBytes(p);
    NYTCorpusDocument nytd = parser.fromByteArray(ba, false);
    AnnotatedNYTDocument ad = new AnnotatedNYTDocument(nytd);
    Communication c = new CommunicationizableAnnotatedNYTDocument(ad).toCommunication();
    for (Section s : c.getSectionList()) {
      SuperTextSpan sts = new SuperTextSpan(s.getTextSpan(), c);
      logger.info("Got TextSpan text: {}", sts.getText());
    }

    CachedSectionedCommunication csc = new CachedSectionedCommunication(c);
    assertEquals(csc.getRoot(), c);

    for (Section s : c.getSectionList()) {
      SuperTextSpan sts = new SuperTextSpan(s.getTextSpan(), c);
      logger.info("Got TextSpan text: {}", sts.getText());
    }

    return c;
  }

  @Test
  public void testNYT() throws Exception{
    AnnotateNonTokenizedConcrete pipe = new AnnotateNonTokenizedConcrete();
    Communication c = this.extractNYTDoc();
    Section first = c.getSectionListIterator().next();

    Communication fromStanford = pipe.process(c);
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
