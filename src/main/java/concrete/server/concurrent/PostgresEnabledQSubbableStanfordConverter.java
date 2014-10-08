/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.concurrent;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.commons.lang.time.StopWatch;
import org.apache.thrift.TException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.interfaces.ProxyCommunication;
import concrete.server.sql.PostgresClient;
import concrete.tools.AnnotationException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.gigaword.ProxyCommunicationConverter;

/**
 * @author max
 *
 */
public class PostgresEnabledQSubbableStanfordConverter {

  private static final Logger logger = LoggerFactory.getLogger(PostgresEnabledQSubbableStanfordConverter.class);
  private static final int backoffMulti = 60;

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

    StanfordAgigaPipe pipe = new StanfordAgigaPipe();
    logger.info("Ingest beginning at: {}", new DateTime().toString());
    StopWatch sw = new StopWatch();
    sw.start();

    String host = System.getenv("GIGAWORD_HOST");
    String dbName = System.getenv("GIGAWORD_DB");
    String user = System.getenv("GIGAWORD_USER");
    byte[] pass = System.getenv("GIGAWORD_PASS").getBytes();

    int backoffCounter = 1;
    boolean docsAvailable = true;
    while (docsAvailable && backoffCounter <= 100000) {
      try (PostgresClient pc = new PostgresClient(host, dbName, user, pass)) {
        while (docsAvailable) {
          ProxyCommunication comm = pc.getUnannotatedCommunication();
          logger.info("Annotating comm: {}", comm.getId());
          Communication c = new ProxyCommunicationConverter(comm).toCommunication();
          try {
            Communication postStanford = pipe.process(c);
            pc.insertCommunication(postStanford);
            docsAvailable = pc.availableUnannotatedCommunications();
          } catch (IOException | TException | ConcreteException | AnnotationException e) {
            logger.warn("Caught an exception while annotating a document.", e);
            logger.warn("Document in question: {}", comm.getId());
          } catch (SQLException sqe) {
            logger.warn("Caught SQLException during insertion or querying next:", sqe);
            logger.warn("Document in question: {}", comm.getId());
          }
        }
      } catch (SQLException e1) {
        logger.error("Caught SQLEx during annotation.", e1);
      }
      
      
      if (docsAvailable) {
        logger.info("Waiting for a bit, then attempting to reconnect.");
        backoffCounter *= 10;
        // 600, 6000, 60000, 600000, 6000000
        try {
          Thread.sleep(backoffCounter * backoffMulti);
        } catch (InterruptedException e) {
          logger.warn("Won't happen.");
        }
        
        logger.info("Trying again.");
      }
    }

    sw.stop();
    logger.info("Finished (or backoffs exceeded). Took {} ms.", sw.getTime());
    sed.enable();
  }
}
