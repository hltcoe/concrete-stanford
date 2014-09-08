/*
 * 
 */
package concrete.server.concurrent;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 * @author max
 *
 */
public class CallableConcreteServer implements Callable<Communication> {

  private static final Logger logger = LoggerFactory.getLogger(CallableConcreteServer.class);
  
  private final StanfordAgigaPipe pipe;
  private final Communication c;
  
  /**
   * 
   */
  public CallableConcreteServer(Communication c) {
    this.pipe = new StanfordAgigaPipe();
    this.c = c;
  }
  
  /**
   * @throws ExecutionException 
   * @throws InterruptedException
   */
  public CallableConcreteServer(Future<Communication> fc) throws InterruptedException, ExecutionException {
    this.pipe = new StanfordAgigaPipe();
    this.c = fc.get();
  }

  @Override
  public Communication call() throws Exception {
    try {
      logger.debug("Processing communication: {}", c.getId());
      Communication proced = this.pipe.process(this.c);
      logger.debug("Finished.");
      return proced;
    } catch (IOException | TException | ConcreteException e) {
      logger.error("Caught an exception processing {}: ", c.getId());
      logger.error("Exception follows:", e);
      return null;
    }
  }
}
