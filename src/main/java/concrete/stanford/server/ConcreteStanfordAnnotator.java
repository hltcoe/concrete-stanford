/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package concrete.stanford.server;

import java.io.IOException;

import org.apache.thrift.TException;

import concrete.tools.AnnotationException;
import edu.jhu.hlt.asphalt.AsphaltException;
import edu.jhu.hlt.asphalt.services.Annotator;
import edu.jhu.hlt.ballast.ServerException;
import edu.jhu.hlt.ballast.server.BallastServer;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 * Implementation of {@link Annotator} interface for concrete-stanford.
 * 
 * @author max
 */
public class ConcreteStanfordAnnotator implements Annotator.Iface {

  private final StanfordAgigaPipe pipe;
  
  /**
   * 
   */
  public ConcreteStanfordAnnotator() {
    this.pipe = new StanfordAgigaPipe();
  }

  /*
   * (non-Javadoc)
   * @see edu.jhu.hlt.asphalt.services.Annotator.Iface#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public Communication annotate(Communication original) throws AsphaltException, TException {
    // message must have proper fields: 
    // >0 section segmentations
    // >0 sections
    // some text
    
    if (!original.isSetText())
      throw new AsphaltException("Your communication does not have text; text is required for this tool.");
    
    try {
      Communication annotated = this.pipe.process(original);
      return annotated;
    } catch (IOException | ConcreteException | AnnotationException e) {
      throw new AsphaltException("There was an error during processing: " + e.getMessage());
    }
  }

  /*
   * (non-Javadoc)
   * @see edu.jhu.hlt.asphalt.services.Annotator.Iface#getMetadata()
   */
  @Override
  public AnnotationMetadata getMetadata() throws TException {
    return new AnnotationMetadata()
      .setTool("Concrete Stanford Server");
  }
  
  public static void main (String... args) throws ServerException {
    BallastServer.createServer(new ConcreteStanfordAnnotator(), args);
  }
}
