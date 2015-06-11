/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.miscommunication.sentenced.NonTokenizedSentencedCommunication;
import edu.jhu.hlt.concrete.random.RandomConcreteFactory;
import edu.jhu.hlt.concrete.section.SingleSectionSegmenter;
import edu.jhu.hlt.concrete.sentence.SentenceFactory;
import edu.jhu.hlt.concrete.serialization.CommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.CompactCommunicationSerializer;
import edu.jhu.hlt.concrete.util.SuperTextSpan;

/**
 *
 */
public class NonTokenizedSentencesTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(NonTokenizedSentencesTest.class);

  RandomConcreteFactory cf = new RandomConcreteFactory();
  CommunicationSerializer cs = new CompactCommunicationSerializer();

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
  }

  private Communication getTestComm() throws Exception {
    Communication c = this.cf.communication();
    c.setText("Sample sentence. Another sentence, with an entity, Carl.");
    Section s = SingleSectionSegmenter.createSingleSection(c, "Passage");
    c.addToSectionList(s);

    return c;
  }

  @Test
  public void testNonSentenced() throws Exception {
    Communication c = this.getTestComm();
    StanfordPostNERCommunication nc = this.pipe.annotate(c);
    Communication nroot = nc.getRoot();

    for (Section st : nc.getSections()) {
      final TextSpan ts = st.getTextSpan();
      LOGGER.info("Got section: {}", new SuperTextSpan(ts, nroot).getText());
    }

    for (Sentence st : nc.getSentences()) {
      final TextSpan ts = st.getTextSpan();
      LOGGER.info("Got sentence: {}", new SuperTextSpan(ts, nroot).getText());
    }

    for (Entity e : nc.getEntities()) {
      LOGGER.info("Got entity: {}", e.getCanonicalName());
    }
  }
// TODO fix
  @Test
  public void testSentenced() throws Exception {
    Communication c = this.getTestComm();
    final int firstSentEnd = 16;
    final int secondSentStart = 17;
    final int secondSentEnd = c.getText().length();

    Sentence sto = SentenceFactory.create();
    sto.setTextSpan(new TextSpan(0, firstSentEnd));

    Sentence stt = SentenceFactory.create();
    stt.setTextSpan(new TextSpan(secondSentStart, secondSentEnd));

    Section ptr = c.getSectionList().get(0);
    ptr.addToSentenceList(sto);
    ptr.addToSentenceList(stt);

    NonTokenizedSentencedCommunication ntsc = new NonTokenizedSentencedCommunication(c);
    Communication ncroot = ntsc.getRoot();
    LOGGER.info("Post nonsent comm: {}", ncroot);
    StanfordPostNERCommunication nc = this.pipe.annotate(ncroot);
    Communication postRoot = nc.getRoot();
    int commTextLen = postRoot.getText().length();
    LOGGER.info("Comm text length: {}", commTextLen);

    for (Section st : nc.getSections()) {
      final TextSpan ts = st.getTextSpan();
      LOGGER.info("Got section text span: {}", ts.toString());
      assertTrue("Section ending can't be larger than comm text length.", commTextLen >= ts.getEnding());
      LOGGER.info("Got section: {}", new SuperTextSpan(ts, postRoot).getText());
    }

    for (Sentence st : nc.getSentences()) {
      final TextSpan ts = st.getTextSpan();
      LOGGER.info("Got text span: {}", ts.toString());
      LOGGER.info("Got sentence: {}", new SuperTextSpan(ts, postRoot).getText());
    }

    for (Entity e : nc.getEntities()) {
      LOGGER.info("Got entity: {}", e.getCanonicalName());
    }
  }
}
