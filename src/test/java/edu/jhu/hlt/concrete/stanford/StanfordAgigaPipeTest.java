/*
 * 
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import concrete.util.data.ConcreteFactory;
import edu.jhu.hlt.asphalt.AsphaltException;
import edu.jhu.hlt.ballast.InvalidInputException;
import edu.jhu.hlt.ballast.tools.SingleSectionSegmenter;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 * @author max
 *
 */
public class StanfordAgigaPipeTest {

  String dataPath ="src/test/resources/test-out-v.0.1.2.concrete";
  StanfordAgigaPipe pipe;
  Communication testComm;
  
  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    Set<String> runOverThese = new HashSet<>();
    runOverThese.add("Other");
    runOverThese.add("Passage");
    
    this.pipe = new StanfordAgigaPipe(runOverThese);
    Communication c = new ConcreteFactory().randomCommunication();
    this.testComm = new SingleSectionSegmenter().annotate(c); 
    // new Serialization().fromBytes(new Communication(), Files.readAllBytes(p));
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
   * @throws TException 
   * @throws AsphaltException 
   * @throws InvalidInputException 
   * @throws ConcreteException 
   * @throws IOException 
   */
  @Test
  public void processNonPassages() throws TException, InvalidInputException, IOException, ConcreteException {
    SuperCommunication sc = new SuperCommunication(this.testComm);
    assertTrue(sc.hasSectionSegmentations());
    assertTrue(sc.hasSections());
    
    Communication nc = this.pipe.process(this.testComm);
    assertTrue(nc.isSetEntityMentionSets());
    assertTrue(nc.isSetEntitySets());
    new SuperCommunication(nc).writeToFile("src/test/resources/post-stanford.concrete", true);
  }
  
//  @Test
//  public void processBadMessage() throws Exception {
//    Communication c = new Communication();
//    c.id = "10505_corpus_x";
//    c.uuid = UUID.randomUUID().toString();
//    c.type = CommunicationType.BLOG;
//    c.text = "Hello world! Testing this out.";
//    SectionSegmentation ss = new SingleSectionSegmenter().annotateDiff(c);
//    c.addToSectionSegmentations(ss);
//    
//    Communication nc = this.pipe.process(c);
//    Serialization.toBytes(nc);
//    assertTrue(nc.isSetEntityMentionSets());
//    assertTrue(nc.isSetEntitySets());
//  }
}
