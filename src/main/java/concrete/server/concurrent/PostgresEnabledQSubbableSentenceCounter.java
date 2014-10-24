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
import concrete.server.sql.PostgresClient;
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

    String host = System.getenv("GIGAWORD_HOST");
    String dbName = System.getenv("GIGAWORD_DB");
    String user = System.getenv("GIGAWORD_USER");
    byte[] pass = System.getenv("GIGAWORD_PASS").getBytes();

    int backoffCounter = 1;

    try (Jedis jedis = jp.getResource();
        PostgresClient pc = new PostgresClient(host, dbName, user, pass)) {
      // IF null, stop - nothing left.
      Optional<String> id = Optional.ofNullable(jedis.spop(RedisLoader.SENTENCE_KEY));
      while (id.isPresent() && backoffCounter <= 100000) {
        try {
          pc.countSentences(id.get());
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
