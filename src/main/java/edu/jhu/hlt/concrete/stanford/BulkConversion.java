/*
 * 
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.lang.time.StopWatch;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.util.concurrent.ConcurrentCommunicationLoader;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 * @author max
 *
 */
public class BulkConversion {

  private static final Logger logger = LoggerFactory.getLogger(BulkConversion.class);
  
  /**
   * 
   */
  public BulkConversion() {

  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 3) {
      logger.info("Usage: <path-to-sectioned-comm-list> <path-to-out-dir> <delete-old-dir>");
      System.exit(1);
    }
    
    String pathListStr = args[0];
    String outDir = args[1];
    String deleteString = args[2];
    
    logger.debug("Loading communications from disk.");
    List<Future<Communication>> comms = new ArrayList<>();
    try(ConcurrentCommunicationLoader ccl = new ConcurrentCommunicationLoader(Runtime.getRuntime().availableProcessors())) {
      comms.addAll(ccl.bulkLoad(pathListStr));
    } catch (FileNotFoundException e) {
      logger.error("No file found at path: {}", pathListStr);
      System.exit(1);
    } catch (Exception e1) {
      logger.error("Issue closing bulk loader.", e1);
      System.exit(1);
    }

    logger.debug("Communications loaded.");
    boolean delete = Boolean.parseBoolean(deleteString);
    Path outPath = Paths.get(outDir);
    outPath.toFile().mkdirs();
    
    logger.info("Initializing Stanford Pipe.");    
    StanfordAgigaPipe pipe = new StanfordAgigaPipe();
    logger.info("Initialization complete.");
    
    int nComms = comms.size();
    int processedComms = 0;
    StopWatch sw = new StopWatch();
    sw.start();
    
    logger.info("Attempting to process {} communications.", nComms);
    try {
      logger.info("[{}%] Processed {} comms.", processedComms / nComms, processedComms);
      for (Future<Communication> cf : comms) {
        Communication c = cf.get();
        logger.info("Processing comm: {}", c.getId());
        String outPathStr = outPath.toString() + File.separator + c.getId() + ".concrete";
        Path commOutPath = Paths.get(outPathStr);
        if (!delete && Files.exists(commOutPath)) {
          logger.warn("Delete parameter false, but file {} exists already; skipping.", commOutPath.toString());
          continue;
        }
        
        Communication converted = pipe.process(c);
        new SuperCommunication(converted).writeToFile(commOutPath, delete);
        processedComms++;
        pipe.resetGlobals();
      }
    } catch (TException e) {
      logger.error("De/serialization issue.", e);
      System.exit(1);
    } catch (IOException e) {
      logger.error("I/O issue.", e);
      System.exit(1);
    } catch (ConcreteException e) {
      logger.error("Concrete issue.", e);
      System.exit(1);
    } catch (InterruptedException e) {
      logger.error("InterruptedException:", e);
    } catch (ExecutionException e) {
      logger.error("ExecutionException:", e);
    }
    
    sw.stop();
    int elapsedSecs = (int)sw.getTime();
    logger.info("Finished.");
    logger.info("Processed {} comms ({}%) in {} seconds [{} comms/s]", processedComms, 
        processedComms / nComms, elapsedSecs, processedComms / elapsedSecs);
  }
}
