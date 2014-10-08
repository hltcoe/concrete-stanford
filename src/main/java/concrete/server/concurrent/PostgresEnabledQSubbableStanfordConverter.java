/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.concurrent;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

import org.apache.commons.lang.time.StopWatch;
import org.apache.thrift.TException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import proxy.interfaces.ProxyCommunication;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import concrete.server.LoggedUncaughtExceptionHandler;
import concrete.server.RedisLoader;
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
    Thread.setDefaultUncaughtExceptionHandler(new LoggedUncaughtExceptionHandler());
    SystemErrDisabler sed = new SystemErrDisabler();
    sed.disable();

    StanfordAgigaPipe pipe = new StanfordAgigaPipe();
    JedisPool jp = new JedisPool("test4", 45445);
    logger.info("Ingest beginning at: {}", new DateTime().toString());
    StopWatch sw = new StopWatch();
    sw.start();

    String host = System.getenv("GIGAWORD_HOST");
    String dbName = System.getenv("GIGAWORD_DB");
    String user = System.getenv("GIGAWORD_USER");
    byte[] pass = System.getenv("GIGAWORD_PASS").getBytes();

    int backoffCounter = 1;

    try (Jedis jedis = jp.getResource(); 
        PostgresClient pc = new PostgresClient(host, dbName, user, pass)) {
      StopWatch pgsq = new StopWatch();
      pgsq.start();
      // IF null, stop - nothing left.
      Optional<String> id = Optional.ofNullable(jedis.spop(RedisLoader.IDS_KEY));
      pgsq.stop();
      logger.info("Got document ID in: {} ms", pgsq.getTime());
      while (id.isPresent() && backoffCounter <= 100000) {
        try {
          if (pc.isDocumentAnnotated(id.get())) {
            logger.info("Document: {} already annotated. Trying again.", id.get());
            id = Optional.ofNullable(jedis.spop(RedisLoader.IDS_KEY));
            continue;
          }

          pgsq.reset();
          pgsq.start();
          ProxyCommunication comm = pc.getDocument(id.get());
          pgsq.stop();
          logger.info("Got a document to annotate in: {} ms", pgsq.getTime());
          logger.info("Annotating comm: {}", comm.getId());
          Communication c = new ProxyCommunicationConverter(comm).toCommunication();
          pgsq.reset();
          pgsq.start();
          Communication postStanford = pipe.process(c);
          pgsq.stop();
          logger.info("Annotated document in: {} ms", pgsq.getTime());
          pc.insertCommunication(postStanford);
          id = Optional.ofNullable(jedis.spop(RedisLoader.IDS_KEY));
        } catch (IOException | TException | ConcreteException | AnnotationException e) {
          logger.warn("Caught an exception while annotating a document.", e);
          logger.warn("Document in question: {}", id.get());
          id = Optional.ofNullable(jedis.spop(RedisLoader.IDS_KEY));
        } catch (SQLException sqe) {
          logger.info("Waiting for a bit, then attempting to reconnect.");
          backoffCounter *= 10;
          // 600, 6000, 60000, 600000, 6000000
          try {
            Thread.sleep(backoffCounter * backoffMulti);
          } catch (InterruptedException ie) {
            logger.warn("Won't happen.");
          }

          logger.info("Trying again.");
        }
      }
    } catch (SQLException e1) {
      logger.error("SQLexception during setup. Not trying to reconnect.", e1);
    }
    
    sw.stop();
    logger.info("Finished (or backoffs exceeded). Took {} ms.", sw.getTime());
    sed.enable();
    
    jp.close();
    jp.destroy();
  }
}
