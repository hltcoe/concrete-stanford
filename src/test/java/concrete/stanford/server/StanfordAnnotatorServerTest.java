/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package concrete.stanford.server;

import static org.junit.Assert.assertTrue;

import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import concrete.util.data.ConcreteFactory;
import edu.jhu.hlt.asphalt.AsphaltException;
import edu.jhu.hlt.asphalt.services.Annotator;
import edu.jhu.hlt.ballast.InvalidInputException;
import edu.jhu.hlt.ballast.server.AbstractServiceTest;
import edu.jhu.hlt.ballast.server.BallastServer;
import edu.jhu.hlt.ballast.tools.SingleSectionSegmenter;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.SectionSegmentation;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 * @author max
 *
 */
public class StanfordAnnotatorServerTest extends AbstractServiceTest {

  private Annotator.Client client;
  private ConcreteFactory cf = new ConcreteFactory();
  
  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    this.srv = new BallastServer(new ConcreteStanfordAnnotator(), LISTEN_PORT);
    this.serviceThread = new Thread(srv);
    this.serviceThread.setDaemon(true);
    this.serviceThread.start();
    
    this.initializeServiceFields();
    this.client = new Annotator.Client(this.protocol);
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
    this.xport.close();
    this.srv.close();
  }

  @Test(expected=AsphaltException.class)
  public void noSectionsShouldException() throws AsphaltException, TException, ConcreteException {
    Communication c = this.cf.randomCommunication()
        .setType("News")
        .setText("Hello world! Testing this out. John is an entity mention.");
    
    Communication wSections = this.client.annotate(c);
    assertTrue(wSections.isSetSectionSegmentationList());
    assertTrue(new SuperCommunication(wSections).firstSection().isSetSentenceSegmentationList());
    assertTrue(wSections.isSetEntitySetList());
    assertTrue(wSections.isSetEntityMentionSetList());
  }
  
  @Test
  public void okCommShouldPass() throws AsphaltException, TException, ConcreteException, InvalidInputException {
    Communication c = this.cf.randomCommunication()
        .setType("News")
        .setText("Hello world! Testing this out. John is an entity mention.");
    SectionSegmentation ss = new SingleSectionSegmenter().annotateDiff(c);
    c.addToSectionSegmentationList(ss);
    
    Communication result = this.client.annotate(c);
    assertTrue(result.isSetEntitySetList());
    assertTrue(result.isSetEntityMentionSetList());
    // assertTrue(result.isSetSectionSegmentations());
    // assertTrue(new SuperCommunication(result).firstSection().isSetSentenceSegmentation());
  }
}
