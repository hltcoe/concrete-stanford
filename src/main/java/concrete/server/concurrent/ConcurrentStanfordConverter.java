/*
 * 
 */
package concrete.server.concurrent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.time.StopWatch;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.gigaword.ClojureIngester;
import edu.jhu.hlt.gigaword.ProxyDocument;

/**
 * @author max
 *
 */
public class ConcurrentStanfordConverter implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(ConcurrentStanfordConverter.class);
  
  private final ExecutorService runner;
  private final CompletionService<Communication> srv;
  
  /**
   * 
   */
  public ConcurrentStanfordConverter() {
    this.runner = Executors.newCachedThreadPool();
    this.srv = new ExecutorCompletionService<Communication>(this.runner);
  }
  
  public Future<Communication> annotate(Future<Communication> fc) throws InterruptedException, ExecutionException {
    return this.srv.submit(new CallableConcreteServer(fc));
  }
  
  public Future<Communication> annotate(Communication c) throws InterruptedException, ExecutionException {
    return this.srv.submit(new CallableConcreteServer(c));
  }
  
  /**
   * @param args
   * @throws Exception 
   */
  public static void main(String[] args) throws Exception {
    int threads = Runtime.getRuntime().availableProcessors();
    if (threads < 8) {
      logger.info("You need at least 8 threads to run this program. You only have {} available.", threads);
      System.exit(1);
    }
    
    if (args.length != 1) {
      logger.info("This program takes 1 argument: the path to a .txt file with paths to agiga documents, 1 per line.");
      System.exit(1);
    }
    
    StopWatch sw = new StopWatch();
    logger.info("Ingest beginning at: {}", new DateTime().toString());
    Path pathToCommFiles = Paths.get(args[0]);
    
    ClojureIngester ci = new ClojureIngester();
    ConcurrentStanfordConverter annotator = new ConcurrentStanfordConverter();
    
    try(Scanner sc = new Scanner(pathToCommFiles.toFile())) {
      while (sc.hasNextLine()) {
        // paths.add(Paths.get(sc.nextLine()));
        String pathStr = sc.nextLine();
        logger.info("Processing file: {}", pathStr);
        Iterator<ProxyDocument> iter = ci.proxyGZipPathToProxyDocIter(pathStr);
        while (iter.hasNext()) {
          ProxyDocument pd = iter.next();
          Communication c = pd.sectionedCommunication();
          Future<Communication> fc = annotator.annotate(c);
          Communication ac = fc.get();
          logger.info("Successfully retrieved communication: {}", ac.getId());
        }
      }
    }
    
    sw.stop();
    logger.info("Ingest complete. Took {} ms.", sw.getTime());
    annotator.close();
  }

  @Override
  public void close() throws Exception {
    this.runner.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    this.runner.shutdown();
  }

}
