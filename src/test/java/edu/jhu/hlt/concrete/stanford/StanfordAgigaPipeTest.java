/*
 * 
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.jhu.hlt.ballast.InvalidInputException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.Serialization;

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
    
    pipe = new StanfordAgigaPipe(runOverThese);
    Path p = Paths.get(dataPath);
    if (!Files.exists(p))
      fail("You need to make sure that this file exists: " + dataPath);
    this.testComm = new Serialization().fromBytes(new Communication(), Files.readAllBytes(p));
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
