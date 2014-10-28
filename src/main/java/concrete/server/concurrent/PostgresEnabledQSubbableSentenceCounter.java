/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.concurrent;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import concrete.server.LoggedUncaughtExceptionHandler;
import concrete.server.RedisLoader;
import concrete.sql.GigawordCreds;
import concrete.sql.PostgresClient;
import concrete.sql.SQLCreds;
import concrete.sql.UnsetEnvironmentVariableException;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 * @author max
 *
 */
public class PostgresEnabledQSubbableSentenceCounter {

  private static final Logger logger = LoggerFactory.getLogger(PostgresEnabledQSubbableSentenceCounter.class);
  private static final int backoffMulti = 60;

  /**
   *
   */
  public PostgresEnabledQSubbableSentenceCounter() {
    // TODO Auto-generated constructor stub
  }

  /**
   * @param args
   */
  public static void main(String[] args) throws IOException {
    Thread.setDefaultUncaughtExceptionHandler(new LoggedUncaughtExceptionHandler());
    SystemErrDisabler sed = new SystemErrDisabler();
    sed.disable();

    JedisPool jp = new JedisPool("test4", 45445);
    logger.info("Count beginning.");
    StopWatch sw = new StopWatch();
    sw.start();

    SQLCreds creds = null;
    try {
      creds = new GigawordCreds();
    } catch (UnsetEnvironmentVariableException e2) {
      logger.error("Credentials were not set.", e2);
      System.exit(1);
    }

    int backoffCounter = 1;
    int nProcessed = 0;

    try (Jedis jedis = jp.getResource();
        PostgresClient pc = new PostgresClient(creds)) {
      pc.setAutoCommit(false);
      // IF null, stop - nothing left.
      Optional<String> id = Optional.ofNullable(jedis.spop(RedisLoader.SENTENCE_KEY));
      while (id.isPresent() && backoffCounter <= 100000) {
        try {
          pc.countSentences(id.get());
          nProcessed++;
          
          if (nProcessed % 10000 == 0)
            pc.commit();
          
          id = Optional.ofNullable(jedis.spop(RedisLoader.SENTENCE_KEY));
        } catch (ConcreteException e) {
          logger.warn("Caught an exception while annotating a document.", e);
          logger.warn("Document in question: {}", id.get());
          id = Optional.ofNullable(jedis.spop(RedisLoader.SENTENCE_KEY));
        } catch (SQLException sqe) {
          logger.warn("Caught a SQLException.", sqe);
          logger.warn("Waiting for a bit, then attempting to reconnect.");
          backoffCounter *= 10;
          // 600, 6000, 60000, 600000, 6000000
          try {
            Thread.sleep(backoffCounter * backoffMulti);
          } catch (InterruptedException ie) {
            logger.warn("Won't happen.");
          }

          logger.warn("Trying again.");
        }
      }
      
      pc.commit();
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
