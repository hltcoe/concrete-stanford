/*
 * 
 */
package concrete.server.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;

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
    logger.info("Processing communication: {}", c.getId());
    Communication proced = this.pipe.process(this.c);
    logger.info("Finished.");
    return proced;
  }
}
