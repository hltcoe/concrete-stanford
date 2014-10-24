/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server;

import java.sql.SQLException;
import java.util.Set;

import org.apache.commons.lang.time.StopWatch;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import concrete.server.sql.PostgresClient;

/**
 * @author max
 *
 */
public class SentenceCounterRedisLoader {

  public static final String SENTENCE_KEY = "gigaword-sents";

  private static final Logger logger = LoggerFactory.getLogger(SentenceCounterRedisLoader.class);
  
  /**
   * 
   */
  public SentenceCounterRedisLoader() {
    // TODO Auto-generated constructor stub
  }

  /**
   * @param args
   * @throws SQLException 
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub
    Thread.setDefaultUncaughtExceptionHandler(new LoggedUncaughtExceptionHandler());
    String redisHost = args[0];
    int redisPort = Integer.parseInt(args[1]);
    String keyName = args[2];

    logger.info("Redis loading beginning at: {}", new DateTime().toString());
    StopWatch sw = new StopWatch();
    sw.start();
    
    String host = System.getenv("GIGAWORD_HOST");
    String dbName = System.getenv("GIGAWORD_DB");
    String user = System.getenv("GIGAWORD_USER");
    byte[] pass = System.getenv("GIGAWORD_PASS").getBytes();

    JedisPool jp = new JedisPool(redisHost, redisPort);
    try (Jedis jedis = jp.getResource();
        PostgresClient pc = new PostgresClient(host, dbName, user, pass);) {
      StopWatch msw = new StopWatch();
      msw.start();
      Set<String> annotatedIds = pc.getAnnotatedDocIds();
      Set<String> countedIds = pc.getCountedDocIds();
      annotatedIds.removeAll(countedIds);
      msw.stop();
      logger.info("Got document IDs in: {} ms", msw.getTime());
      msw.reset();
      msw.start();
      for (String id : annotatedIds)
        jedis.sadd(keyName, id);
      
      msw.stop();
      logger.info("Loaded document IDs in: {} ms", msw.getTime());
    } catch (SQLException e) {
      logger.error("Caught a sql exception while loading Redis.", e);
    }
    
    sw.stop();
    logger.info("Finished. Took {} ms.", sw.getTime());

    jp.close();
    jp.destroy();
  }
}
