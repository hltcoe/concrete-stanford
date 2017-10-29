/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nytlabs.corpus.NYTCorpusDocumentParser;

import edu.jhu.hlt.annotatednyt.AnnotatedNYTDocument;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.ingesters.annotatednyt.CommunicationizableAnnotatedNYTDocument;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

/**
 *
 */
public class AnnotatedNYTTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnnotatedNYTTest.class);

  Path p = Paths.get("src/test/resources/hopkins-stanford-a-la-nyt.xml");
  NYTCorpusDocumentParser parser = new NYTCorpusDocumentParser();

  Communication nytComm;

  @Before
  public void setUp() throws Exception {
    try(InputStream is = Files.newInputStream(p);
        BufferedInputStream bin = new BufferedInputStream(is, 1024 * 8 * 16);) {
      byte[] nytdocbytes = IOUtils.toByteArray(bin);
      this.nytComm = new CommunicationizableAnnotatedNYTDocument(new AnnotatedNYTDocument(parser.fromByteArray(nytdocbytes, false))).toCommunication();
    }
  }

  public Communication annotate (Communication orig) throws AnalyticException {
    Communication cp = new Communication(orig);
    final String commTxt = cp.getText();
    return cp;
  }

  @Test
  public void test() throws Exception {
    String text = "Johns Hopkins University was started by Johns Hopkins. Johns Hopkins was a good man.";
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    // create an empty Annotation just with the given text
    Annotation document = new Annotation(text);

    // run all Annotators on this text
    pipeline.annotate(document);

    // This is the coreference link graph
    // Each chain stores a set of mentions that link to each other,
    // along with a method for getting the most representative mention
    // Both sentence and token offsets start at 1!
    Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);
    graph.entrySet().forEach(e -> {
      LOGGER.info("Got coref key: {}", e.getKey());
      LOGGER.info("Got coref val: {}", e.getValue());
      e.getValue().getMentionsInTextualOrder().forEach(m -> LOGGER.info("Got mention: {}", m.toString()));
    });

    LOGGER.info("Got document: {}", document);
    LOGGER.info("Got document: {}", document.toString());

    AnnotateNonTokenizedConcrete tk = new AnnotateNonTokenizedConcrete();
    StanfordPostNERCommunication postNER = tk.annotate(this.nytComm);
    postNER.getEntityMentions().forEach(em -> LOGGER.info("Got EM: {}", em));
  }
}
