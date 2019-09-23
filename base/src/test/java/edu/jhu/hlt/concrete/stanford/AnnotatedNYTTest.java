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

import edu.stanford.nlp.coref.data.CorefChain;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nytlabs.corpus.NYTCorpusDocumentParser;
//import edu.jhu.hlt.concrete.stanford.fixNullDependencyGraphs;
import edu.stanford.nlp.coref.CorefCoreAnnotations;
//import edu.stanford.nlp.coref.data.CorefChain;
import edu.jhu.hlt.annotatednyt.AnnotatedNYTDocument;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.ingesters.annotatednyt.CommunicationizableAnnotatedNYTDocument;
import edu.jhu.hlt.concrete.miscommunication.WrappedCommunication;
import edu.jhu.hlt.concrete.stanford.languages.PipelineLanguage;
//import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.util.CoreMap;


import java.util.Collection;
import java.util.Properties;


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

  @Test
  public void test() throws Exception {
//    String text = "Johns Hopkins University was started by Johns Hopkins. Johns Hopkins was a good man.";
    String text = "Barack Obama was born in Hawaii.  He is the president. Obama was elected in 2008.";
    Properties props = new Properties();
//    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
//    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref");
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    // create an empty Annotation just with the given text
    Annotation document = new Annotation(text);
    // run all Annotators on this text
    pipeline.annotate(document);
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      // Get the OpenIE triples for the sentence
      Collection<RelationTriple> triples =
              sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
      // Print the triples
      for (RelationTriple triple : triples) {
        System.out.println(triple.confidence + "\t" +
                triple.subjectLemmaGloss() + "\t" +
                triple.relationLemmaGloss() + "\t" +
                triple.objectLemmaGloss());
      }
    }


    LOGGER.debug("Debug document: {}", document);
//    LOGGER.debug("Core{}", document.get(CorefChainAnnotation.class));

  // run all Annotators on this text
//    pipeline.annotate(document);


    // This is the coreference link graph
    // Each chain stores a set of mentions that link to each other,
    // along with a method for getting the most representative mention
    // Both sentence and token offsets start at 1!
//    for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
//      System.out.println("\t" + cc);
//      LOGGER.debug("Got coref val: {}", cc);
//    }

    Map<Integer, CorefChain> graph = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    LOGGER.debug("dCoreref Graph ==> {}", graph);
    graph.entrySet().forEach(e -> {
      LOGGER.info("Got dcoref key: {}", e.getKey());
      LOGGER.info("Got dcoref val: {}", e.getValue());
      e.getValue().getMentionsInTextualOrder().forEach(m -> LOGGER.info("Got mention: {}", m.toString()));
    });

    LOGGER.info("Got document: {}", document);
    LOGGER.info("Got document: {}", document.toString());

    PipelineLanguage eng = PipelineLanguage.ENGLISH;
    ConcreteStanfordTokensSentenceAnalytic a1 = eng.getSentenceTokenizationAnalytic();
    WrappedCommunication wc = a1.annotate(this.nytComm);
    ConcreteStanfordPreCorefAnalytic a2 = eng.getPreCorefAnalytic();
    wc = a2.annotate(wc.getRoot());
    ConcreteStanfordPreCorefAnalytic a3 = eng.getAllAnalytic();
    wc = a3.annotate(wc.getRoot());
    LOGGER.debug("Get all analytic: {}", wc);
    StanfordPostNERCommunication postNER = new StanfordPostNERCommunication(wc.getRoot());
    postNER.getEntityMentions().forEach(em -> LOGGER.info("Got EM: {}", em));
  }
}
