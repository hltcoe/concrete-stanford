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
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.Serialization;

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
    
    Path pathList = Paths.get(pathListStr);
    if (!Files.exists(pathList)) {
      logger.error("No file found at path: {}", pathList.toString());
      System.exit(1);
    }
    
    Set<String> pathStringSet = new HashSet<>();
    logger.info("Reading in concrete file paths.");
    try (Scanner sc = new Scanner(pathList.toFile())) {
      while (sc.hasNextLine())
        pathStringSet.add(sc.nextLine());
    } catch (FileNotFoundException e) {
      // shouldn't throw.
      logger.error(e.getMessage(), e);
      System.exit(1);
    }
    
    logger.info("Completed reading in paths.");  
    Serialization sr = new Serialization();
    Set<Communication> commSet = new HashSet<>(pathStringSet.size());
    
    logger.info("Loading communications from disk.");
    try {
      for (String ps : pathStringSet) {
        Path cp = Paths.get(ps);
        byte[] commBytes = Files.readAllBytes(cp);
        commSet.add(sr.fromBytes(new Communication(), commBytes));
      }
      
    } catch (IOException | ConcreteException e) {
      logger.error("Error reading in concrete files.", e);
      System.exit(1);
    }
    
    logger.info("Communications loaded.");
    boolean delete = Boolean.parseBoolean(deleteString);
    Path outPath = Paths.get(outDir);
    outPath.toFile().mkdirs();
    
    logger.info("Initializing Stanford Pipe.");    
    StanfordAgigaPipe pipe = new StanfordAgigaPipe();
    logger.info("Initialization complete.");
    
    int nComms = commSet.size();
    int processedComms = 0;
    StopWatch sw = new StopWatch();
    sw.start();
    
    logger.info("Attempting to process {} communications.", nComms);
    try {
      logger.info("[{}%] Processed {} comms.", processedComms / nComms, processedComms);
      for (Communication c : commSet) {
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
    }
    
    sw.stop();
    int elapsedSecs = (int)sw.getTime();
    logger.info("Finished.");
    logger.info("Processed {} comms ({}%) in {} seconds [{} comms/s]", processedComms, 
        processedComms / nComms, elapsedSecs, processedComms / elapsedSecs);
  }
}
