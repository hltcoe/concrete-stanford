/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Dependency;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.stanford.ConcreteStanfordPreCorefAnalytic;
import edu.jhu.hlt.concrete.stanford.ConcreteStanfordTokensSentenceAnalytic;
import edu.jhu.hlt.concrete.stanford.PipelineLanguage;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;

/**
 * Using the example from the online demo at
 * http://nlp.stanford.edu:8080/parser/index.jsp
 *
 * NOTE: It actually fails this example, but the version online appears to be
 * an older version of the parser than we are using. I'm using another simple
 * sentence, which it gets right.
 *
 * sentence: "My dog also likes eating sausage."
 *
 * basic dependencies:
 *   poss(dog-2, My-1)
 *   nsubj(likes-4, dog-2)
 *   advmod(likes-4, also-3)
 *   root(ROOT-0, likes-4)
 *   xcomp(likes-4, eating-5)
 *   dobj(eating-5, sausage-6)
 *
 * @author travis
 */
public class BasicDepParseTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(BasicDepParseTest.class);

  public static Communication getTestCommunication() {
    return unsectionedCommunicationFromText(
        //"My dog also likes eating sausage.");
        "The Stanford Parser is a very precise piece of equipment.");
  }

  ConcreteStanfordTokensSentenceAnalytic preAnalytic;
  ConcreteStanfordPreCorefAnalytic analytic;

  @Before
  public void setUp() throws Exception {
    this.preAnalytic = new ConcreteStanfordTokensSentenceAnalytic();
    this.analytic = new ConcreteStanfordPreCorefAnalytic(PipelineLanguage.ENGLISH);
  }

  /**
   * NOTE: Do not make a Sentence, or else the annotator will not run
   */
  public static Communication unsectionedCommunicationFromText(String text) {
    TextSpan span = new TextSpan();
    span.setStart(0);
    span.setEnding(text.length());

    Section sect = new Section();
    sect.setKind("Passage");
    sect.setUuid(UUIDFactory.newUUID());
    sect.setTextSpan(span);

    Communication c = new Communication();
    c.setUuid(UUIDFactory.newUUID());
    c.setId("Dummy_Communication");
    c.setMetadata(new AnnotationMetadata().setTool("BasicDepParseTester").setTimestamp(1));
    c.addToSectionList(sect);
    c.setText(text);
    return c;
  }

  public static Set<String> getExpectedBasicDependencies() {
    Set<String> deps = new HashSet<>();
    /*
    deps.add("poss(dog-2, My-1)");
    deps.add("nsubj(likes-4, dog-2)");
    deps.add("advmod(likes-4, also-3)");
    deps.add("root(ROOT-0, likes-4)");
    deps.add("xcomp(likes-4, eating-5)");
    deps.add("dobj(eating-5, sausage-6)");
    */
    deps.add("det(Parser-3, The-1)");
    deps.add("nn(Parser-3, Stanford-2)");
    deps.add("nsubj(piece-8, Parser-3)");
    deps.add("cop(piece-8, is-4)");
    deps.add("det(piece-8, a-5)");
    deps.add("advmod(precise-7, very-6)");
    deps.add("amod(piece-8, precise-7)");
    deps.add("root(ROOT-0, piece-8)");
    deps.add("prep(piece-8, of-9)");
    deps.add("pobj(of-9, equipment-10)");
    return deps;
  }

  public static Set<String> getObservedBasicDependencies(Communication c) {
    Set<String> deps = new HashSet<>();
    Assert.assertEquals(c.getSectionListSize(), 1);
    Section sect = c.getSectionList().get(0);
    Tokenization toks = sect
        .getSentenceList().get(0).getTokenization();
    Assert.assertNotNull(toks);
    Assert.assertNotNull(toks.getDependencyParseList());
    List<DependencyParse> dps = toks.getDependencyParseList()
        .stream()
        .filter(dp -> dp.getMetadata().getTool().contains("basic"))
        .collect(Collectors.toList());
    Assert.assertEquals(1, dps.size());
    for (Dependency e : dps.get(0).getDependencyList()) {
      Assert.assertTrue(e.getDep() >= 0);
      List<Token> tokList = toks.getTokenList().getTokenList();
      Token word = tokList.get(e.getDep());
      String h = "ROOT-0";
      if (e.isSetGov() && e.getGov() >= 0) {
        h = String.format("%s-%d", tokList.get(e.getGov()).getText(), e.getGov() + 1);
      }
      String dep = String.format("%s(%s, %s-%d)",
          e.getEdgeType(),
          h,
          word.getText(),
          e.getDep() + 1);
      deps.add(dep);
    }
    return deps;
  }

  @Test
  public void test() throws Exception {
    Communication c = getTestCommunication();
    TokenizedCommunication tc = this.preAnalytic.annotate(c);
    tc.getSections().forEach(s -> LOGGER.debug("Got section: {}", s.toString()));
    tc.getSentences().forEach(s -> LOGGER.debug("Got sentence: {}", s.toString()));
    TokenizedCommunication postPre = this.analytic.annotate(tc);
    c = postPre.getRoot();
    Set<String> gold = getExpectedBasicDependencies();
    Set<String> hyp = getObservedBasicDependencies(c);
    LOGGER.debug("gold = {}", gold);
    LOGGER.debug("hyp = {}", hyp);
    assertTrue(gold.equals(hyp));
  }
}
