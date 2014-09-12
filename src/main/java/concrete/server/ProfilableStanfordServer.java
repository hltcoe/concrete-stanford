/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.stream.LongStream;

import org.apache.commons.lang.time.StopWatch;
import org.apache.thrift.TException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.server.concurrent.SystemErrDisabler;
import concrete.tools.AnnotationException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.gigaword.ClojureIngester;
import edu.jhu.hlt.gigaword.ProxyDocument;

/**
 * @author max
 *
 */
public class ProfilableStanfordServer {

  private static final Logger logger = LoggerFactory.getLogger(ProfilableStanfordServer.class);

  /**
   * 
   */
  public ProfilableStanfordServer() {
    // TODO Auto-generated constructor stub
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      logger.info("This program takes 1 argument: the path to a .gz file with giga documents.");
      System.exit(1);
    }

    logger.info("Setting up uncaught exception handler.");
    Thread.setDefaultUncaughtExceptionHandler(new LoggedUncaughtExceptionHandler());

    Path pathToCommFiles = Paths.get(args[0]);
    if (!Files.exists(pathToCommFiles)) {
      logger.error("No file at: {} ; can't ingest anything.", pathToCommFiles.toString());
      System.exit(1);
    }

    logger.info("Warming up models. This is a good time for profilers to hook in.");
    StanfordAgigaPipe pipe = new StanfordAgigaPipe();

    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      // shouldn't throw
      logger.info("Caught an exception while sleeping.", e);
    }

    logger.info("Disabling System.err.");
    SystemErrDisabler disabler = new SystemErrDisabler();
    disabler.disable();

    StopWatch sw = new StopWatch();
    logger.info("Ingest beginning at: {}", new DateTime().toString());
    sw.start();

    ClojureIngester ci = new ClojureIngester();
    ArrayDeque<Long> times = new ArrayDeque<Long>(12000);

    ArrayDeque<Communication> dq = new ArrayDeque<Communication>(12000);
    Iterator<ProxyDocument> iter = ci.proxyGZipPathToProxyDocIter(args[0]);
    while (iter.hasNext()) {
      ProxyDocument pd = iter.next();
      Communication c = pd.sectionedCommunication();
      dq.push(c);
    }

    logger.info("Mapping complete. Submitting tasks.");
    while (dq.peek() != null) {
      Communication pending = dq.pop();
      try {
        StopWatch subSw = new StopWatch();
        subSw.start();
        pipe.process(pending);
        subSw.stop();
        times.push(subSw.getTime());
      } catch (TException | IOException | ConcreteException | AnnotationException e) {
        logger.error("Caught Exception: ", e);
        logger.error("File path: {}", args[0]);
        logger.error("Document ID: {}", pending.getId());
      }
    }
    
    sw.stop();
    LongStream ls = times.stream().mapToLong(i -> i);
    long max = ls.max().getAsLong();
    long min = ls.min().getAsLong();
    double avg = ls.average().getAsDouble();
    logger.info("Ingest complete. Took {} ms.", sw.getTime());
    logger.info("Longest document took: {} ms.", max);
    logger.info("Shortest document took: {} ms.", min);
    logger.info("Average document took: {} ms.", avg);
  }
}
