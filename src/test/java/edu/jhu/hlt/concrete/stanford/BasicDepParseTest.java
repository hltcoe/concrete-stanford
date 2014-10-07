package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Dependency;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.util.ConcreteUUIDFactory;

/**
 * Using the example from the online demo at
 * http://nlp.stanford.edu:8080/parser/index.jsp
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
 * collapsed dependencies:
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
  private static final ConcreteUUIDFactory cuf = new ConcreteUUIDFactory();

  public static Communication getTestCommunication() {
    return unsectionedCommunicationFromText("My dog also likes eating sausage.");
  }

  public static Communication unsectionedCommunicationFromText(String text) {
    TextSpan span = new TextSpan();
    span.setStart(0);
    span.setEnding(text.length());

    Sentence sent = new Sentence();
    sent.setUuid(cuf.getConcreteUUID());
    sent.setRawTextSpan(span);
    sent.setTextSpan(span);

    Section sect = new Section();
    sect.setUuid(cuf.getConcreteUUID());
    sect.setTextSpan(span);
    sect.addToSentenceList(sent);

    Communication c = new Communication();
    c.setUuid(cuf.getConcreteUUID());
    c.addToSectionList(sect);
    c.setText(text);
    return c;
  }

  public static Set<String> getExpectedBasicDependencies() {
    Set<String> deps = new HashSet<>();
    deps.add("poss(dog-2, My-1)");
    deps.add("nsubj(likes-4, dog-2)");
    deps.add("advmod(likes-4, also-3)");
    deps.add("root(ROOT-0, likes-4)");
    deps.add("xcomp(likes-4, eating-5)");
    deps.add("dobj(eating-5, sausage-6)");
    return deps;
  }

  public static Set<String> getObservedBasicDependencies(Communication c) {
    Set<String> deps = new HashSet<>();
    Tokenization toks = c.getSectionList().get(0).getSentenceList().get(0).getTokenization();
    List<DependencyParse> dps = toks.getDependencyParseList().stream().filter(dp -> dp.getMetadata().getTool().contains("basic")).collect(Collectors.toList());
    Assert.assertEquals(dps.size(), 1);
    for (Dependency e : dps.get(0).getDependencyList()) {
      Token word = toks.getTokenList().getTokenList().get(e.getDep());
      Token head = toks.getTokenList().getTokenList().get(e.getGov());
      String dep = String.format("%s(%s-%d, %s-%d)", e.getEdgeType(), word.getText(), e.getDep(), head.getText(), e.getGov());
      deps.add(dep);
    }
    return deps;
  }

//  @Test
//  public void test() throws Exception {
//    Communication c = getTestCommunication();
//    StanfordAgigaPipe pipe = new StanfordAgigaPipe();
//    c = pipe.process(c);
//    Set<String> gold = getExpectedBasicDependencies();
//    Set<String> hyp = getObservedBasicDependencies(c);
//    assertTrue(gold.equals(hyp));
//  }
}
