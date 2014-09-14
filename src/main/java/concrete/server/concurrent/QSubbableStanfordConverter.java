/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.concurrent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.commons.lang.time.StopWatch;
import org.apache.thrift.TException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.tools.AnnotationException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.gigaword.ClojureIngester;
import edu.jhu.hlt.gigaword.ProxyDocument;

/**
 * @author max
 *
 */
public class QSubbableStanfordConverter {

  private static final Logger logger = LoggerFactory.getLogger(QSubbableStanfordConverter.class);

  /**
   * 
   */
  public QSubbableStanfordConverter() {
    // TODO Auto-generated constructor stub
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    SystemErrDisabler sed = new SystemErrDisabler();
    sed.disable();

    ClojureIngester ci = new ClojureIngester();
    StanfordAgigaPipe pipe = new StanfordAgigaPipe();
    Path inGz = Paths.get(args[0]);
    Path name = inGz.getFileName();
    Path out = Paths.get("/export/common/max/data/agiga2").resolve(name);
    try {
      Files.createDirectories(out);
    } catch (IOException e1) {
      // ?
      logger.error("Got IOEx.", e1);
    }

    logger.info("Ingest beginning at: {}", new DateTime().toString());
    StopWatch sw = new StopWatch();
    sw.start();

    Iterator<ProxyDocument> pdIter = ci.proxyGZipPathToProxyDocIter(args[2]);
    logger.info("Got document iterator.");
    while (pdIter.hasNext()) {
      ProxyDocument pd = pdIter.next();
      try {
        Communication wSections = pd.sectionedCommunication();
        Communication postStanford = pipe.process(wSections);
        Path fileOut = out.resolve(pd.getId() + ".concrete");
        new SuperCommunication(postStanford).writeToFile(fileOut, true);
      } catch (IOException | TException | ConcreteException | AnnotationException e) {
        logger.warn("Caught an exception while annotating a document.", e);
        logger.warn("Document in question: {}", pd.getId());
      }
    }

    sw.stop();
    logger.info("Finished. Took {} ms.", sw.getTime());

    sed.enable();
  }
}
