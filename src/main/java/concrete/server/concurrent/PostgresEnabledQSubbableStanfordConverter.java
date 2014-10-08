/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.concurrent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Iterator;

import org.apache.commons.lang.time.StopWatch;
import org.apache.thrift.TException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.interfaces.ProxyCommunication;
import concrete.server.sql.PostgresClient;
import concrete.tools.AnnotationException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.gigaword.ClojureIngester;
import edu.jhu.hlt.gigaword.ProxyCommunicationConverter;

/**
 * @author max
 *
 */
public class PostgresEnabledQSubbableStanfordConverter {

  private static final Logger logger = LoggerFactory.getLogger(PostgresEnabledQSubbableStanfordConverter.class);

  /**
   * 
   */
  public PostgresEnabledQSubbableStanfordConverter() {
    // TODO Auto-generated constructor stub
  }

  /**
   * @param args
   */
  public static void main(String[] args) throws IOException {
    SystemErrDisabler sed = new SystemErrDisabler();
    sed.disable();

    ClojureIngester ci = new ClojureIngester();
    StanfordAgigaPipe pipe = new StanfordAgigaPipe();
    Path inGz = Paths.get(args[0]);
    Path name = inGz.getFileName();
    Path out = Paths.get("/home/max/data/agiga2").resolve(name);
    try {
      Files.createDirectories(out);
    } catch (IOException e1) {
      // ?
      logger.error("Got IOEx.", e1);
    }

    logger.info("Ingest beginning at: {}", new DateTime().toString());
    StopWatch sw = new StopWatch();
    sw.start();
    
    String host = System.getenv("GIGAWORD_HOST");
    String dbName = System.getenv("GIGAWORD_DB");
    String user = System.getenv("GIGAWORD_USER");
    byte[] pass = System.getenv("GIGAWORD_PASS").getBytes();

    try (PostgresClient pc = new PostgresClient(host, dbName, user, pass)) {
      while (pc.availableUnannotatedCommunications()) {
        ProxyCommunication comm = pc.getUnannotatedCommunication();
        logger.info("Annotating comm: {}", comm.getId());
        Communication c = new ProxyCommunicationConverter(comm).toCommunication();
        try {
          Communication postStanford = pipe.process(c);
          pc.insertCommunication(postStanford);
        } catch (IOException | TException | ConcreteException | AnnotationException e) {
          logger.warn("Caught an exception while annotating a document.", e);
          logger.warn("Document in question: {}", comm.getId());
        } catch (SQLException sqe) {
          logger.warn("Caught SQLException:", sqe);
          logger.warn("Document in question: {}", comm.getId());
        }
      }
    } catch (SQLException e1) {
      logger.error("Caught SQLEx retrieving documents.", e1);
    }

    sw.stop();
    logger.info("Finished. Took {} ms.", sw.getTime());

    sed.enable();
  }
}
