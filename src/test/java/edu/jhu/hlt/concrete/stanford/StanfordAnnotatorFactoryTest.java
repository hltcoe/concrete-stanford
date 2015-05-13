package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.analytics.base.SectionedCommunicationAnalytic;
import edu.jhu.hlt.concrete.random.RandomConcreteFactory;
import edu.jhu.hlt.concrete.util.ConcreteException;

public class StanfordAnnotatorFactoryTest {

  RandomConcreteFactory cf = new RandomConcreteFactory();

  @Test
  public void testAnnotateNonTokenized() throws IOException, ConcreteException {
    Communication comm = this.cf.communication();
    comm.addToSectionList(new Section());
    SectionedCommunicationAnalytic gsa = StanfordAnnotatorFactory
        .getAppropriateAnnotator(comm, "en");
    assertEquals("edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete",
        gsa.getClass().getName());
  }

  @Test
  public void testAnnotateTokenized() throws IOException, ConcreteException {
    Communication comm = this.cf.communication();
    Section section = new Section();
    Tokenization tokenization = new Tokenization();
    tokenization.setTokenList(new TokenList());
    Sentence sentence = new Sentence().setTokenization(tokenization);
    section.addToSentenceList(sentence);
    comm.addToSectionList(section);
    SectionedCommunicationAnalytic gsa = StanfordAnnotatorFactory
        .getAppropriateAnnotator(comm, "en");
//    assertEquals("edu.jhu.hlt.concrete.stanford.AnnotateTokenizedConcrete", gsa
//        .getClass().getName());
  }

}
