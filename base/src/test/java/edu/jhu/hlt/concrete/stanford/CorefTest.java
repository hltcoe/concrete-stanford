/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.ingesters.gigaword.GigawordDocumentConverter;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.stanford.ConcreteStanfordTokensSentenceAnalytic;
import edu.jhu.hlt.concrete.stanford.ConcreteToStanfordMapper;
import edu.jhu.hlt.concrete.stanford.MarkupRewriter;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

/**
 *
 */
public class CorefTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(CorefTest.class);

  String txt;
  Path p = Paths.get("src/test/resources/serif_dateline.sgml");
  Communication comm;

  StanfordCoreNLP pipeline;

  @Before
  public void setUp() throws Exception {
    this.comm = new GigawordDocumentConverter().fromPath(this.p);
    LOGGER.info("Loaded comm: {} [UUID: {}]", comm.getId(), comm.getUuid().getUuidString());
    txt = MarkupRewriter.removeMarkup(this.comm.getText());

    // this.pipeline = StanfordPipelineFactory.fullPipeline();
  }

  @Test
  public void test() throws Exception {
    ConcreteStanfordTokensSentenceAnalytic firstAnalytic = new ConcreteStanfordTokensSentenceAnalytic();
    TokenizedCommunication tc = firstAnalytic.annotate(this.comm);
    List<CoreMap> allCmList = new ArrayList<>();
    tc.getSections().forEach(sect -> {
      LOGGER.debug("Annotation section: {}", sect.getUuid().getUuidString());
      // TextSpan ts = sect.getTextSpan();
      // String sectText = this.txt.substring(ts.getStart(), ts.getEnding());
      allCmList.addAll(ConcreteToStanfordMapper.concreteSectionToCoreMapList(sect, this.txt));
    });

    Annotation at = new Annotation(allCmList);
    at.set(TextAnnotation.class, this.txt);
//    (StanfordCoreNLP.getExistingAnnotator("pos")).annotate(at);
//    (StanfordCoreNLP.getExistingAnnotator("lemma")).annotate(at);
//    (StanfordCoreNLP.getExistingAnnotator("ner")).annotate(at);
//    (StanfordCoreNLP.getExistingAnnotator("parse")).annotate(at);
//    (StanfordCoreNLP.getExistingAnnotator("dcoref")).annotate(at);
    // this.pipeline.annotate(at);
//    LOGGER.info("Coref results:");
    LOGGER.info(at.toShorterString(new String[0]));
    for (CoreMap cm : allCmList) {
      LOGGER.info("Got CoreMap: {}", cm.toShorterString(new String[0]));
    }
  }
}
