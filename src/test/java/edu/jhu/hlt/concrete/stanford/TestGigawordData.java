/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.validation.CommunicationValidator;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.ingesters.gigaword.GigawordDocumentConverter;
import edu.jhu.hlt.concrete.miscommunication.sectioned.CachedSectionedCommunication;

/**
 *
 */
public class TestGigawordData {

  private static final Logger logger = LoggerFactory.getLogger(TestGigawordData.class);

  private final GigawordDocumentConverter conv = new GigawordDocumentConverter();
  private final AnnotateNonTokenizedConcrete pipe = new AnnotateNonTokenizedConcrete();

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

  private Communication extractGigawordDoc(Path p) throws Exception {
    Communication c = conv.fromPath(p);
    CachedSectionedCommunication csc = new CachedSectionedCommunication(c);
    assertEquals(csc.getRoot(), c);
    return c;
  }

  @Test
  public void testGiga() throws Exception {
    Path p = Paths.get("src/test/resources/NYT_ENG_20021026.0143.xml");
    Communication c = this.extractGigawordDoc(p);

    Communication fromStanford = pipe.annotate(c).getRoot();
    CachedSectionedCommunication cscPost = new CachedSectionedCommunication(fromStanford);
    Communication sRoot = cscPost.getRoot();
//    List<Tokenization> tkzList = new CachedTokenizationCommunication(sRoot).getTokenizations();
//    for (int i = 0; i < tkzList.size(); i++) {
//      Tokenization tkz = tkzList.get(i);
//      logger.info("Validating tokenization {}", i);
//      assertTrue(new ValidatableTokenization(tkz).validate(sRoot));
//    }
//
//    EntityMentionSet ems = sRoot.getEntityMentionSetListIterator().next();
//    List<EntityMention> emList = ems.getMentionList();
//    for (int i = 0; i < ems.getMentionListSize(); i++) {
//      EntityMention em = emList.get(0);
//      logger.info("Validating EntityMention {}", i);
//      assertTrue(new ValidatableEntityMention(em).validate(sRoot));
//    }

    assertTrue(new CommunicationValidator(sRoot).validate());

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
