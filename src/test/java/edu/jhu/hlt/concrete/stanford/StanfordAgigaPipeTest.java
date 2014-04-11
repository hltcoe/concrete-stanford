/**
 * 
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.EnumSet;
import java.util.UUID;

import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.jhu.hlt.asphalt.AsphaltException;
import edu.jhu.hlt.ballast.InvalidInputException;
import edu.jhu.hlt.ballast.tools.SingleSectionSegmenter;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.CommunicationType;
import edu.jhu.hlt.concrete.SectionKind;
import edu.jhu.hlt.concrete.SectionSegmentation;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.Serialization;
import edu.jhu.hlt.concrete.util.SuperCommunication;

/**
 * @author max
 *
 */
public class StanfordAgigaPipeTest {

  StanfordAgigaPipe pipe;
  
  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    EnumSet<SectionKind> runOverThese = EnumSet.noneOf(SectionKind.class);
    runOverThese.add(SectionKind.OTHER);
    runOverThese.add(SectionKind.PASSAGE);
    
    pipe = new StanfordAgigaPipe(runOverThese);
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
  public void processNonPassages() throws AsphaltException, TException, InvalidInputException, IOException, ConcreteException {
    Communication c = new Communication("test_id", UUID.randomUUID().toString(), CommunicationType.NEWS);
    c.setText("Hello world! Sample text here.");
    SectionSegmentation ss = new SingleSectionSegmenter().annotateDiff(c);
    c.addToSectionSegmentations(ss);
    SuperCommunication sc = new SuperCommunication(c);
    assertTrue(sc.hasSectionSegmentations());
    assertTrue(sc.hasSections());
    
    Communication nc = this.pipe.process(c);
    assertTrue(nc.isSetEntityMentionSets());
    assertTrue(nc.isSetEntitySets());
  }
  
  @Test
  public void processBadMessage() throws Exception {
    Communication c = new Communication();
    c.id = "10505_corpus_x";
    c.uuid = UUID.randomUUID().toString();
    c.type = CommunicationType.BLOG;
    c.text = "Hello world! Testing this out.";
    SectionSegmentation ss = new SingleSectionSegmenter().annotateDiff(c);
    c.addToSectionSegmentations(ss);
    
    Communication nc = this.pipe.process(c);
    Serialization.toBytes(nc);
    assertTrue(nc.isSetEntityMentionSets());
    assertTrue(nc.isSetEntitySets());
  }
}
