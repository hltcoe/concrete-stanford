/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.concurrent;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import concrete.server.sql.SQLiteClient;
import concrete.tools.AnnotationException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.gigaword.ClojureIngester;

/**
 * @author max
 *
 */
public class RedisEnabledStanfordConverter {

  public static final String REDIS_SET_KEY = "gigaword-keys";
  public static final String ERRORS_KEY = "gigaword-error-keys";

  private static final Logger logger = LoggerFactory.getLogger(RedisEnabledStanfordConverter.class);
  
  /**
   * 
   */
  public RedisEnabledStanfordConverter() {
    // TODO Auto-generated constructor stub
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    String redisHost = args[0];
    int redisPort = Integer.parseInt(args[1]);
    
    ClojureIngester ci = new ClojureIngester();
    StanfordAgigaPipe pipe = new StanfordAgigaPipe();
    
    JedisPool jp = new JedisPool(redisHost, redisPort);
    try (Jedis jedis = jp.getResource();
        SQLiteClient sqlcli = new SQLiteClient(args[2])) {
      Optional<String> idToProcess = Optional.ofNullable(jedis.spop(REDIS_SET_KEY));
      while (idToProcess.isPresent()) {
        String idToGrab = idToProcess.get();
        String document = jedis.get(idToGrab);
        try {
          Communication wSections = ci.proxyDocStringToProxyDoc(document).sectionedCommunication();
          Communication postStanford = pipe.process(wSections);
          sqlcli.insert(postStanford);
        } catch (IOException | TException | ConcreteException | AnnotationException e) {
          logger.error("Caught an exception while annotating a document.", e);
          logger.error("Document in question: {}", idToGrab);
          // Put key in error set.
          try (Jedis nj = jp.getResource()) {
            nj.sadd(ERRORS_KEY, idToGrab);
          }

        } catch (SQLException se) {
          logger.error("Caught a SQLException when inserting document.", se);
          logger.error("Document in question: {}", idToGrab);
          try (Jedis nj = jp.getResource()) {
            nj.sadd(ERRORS_KEY, idToGrab);
          }
        }
        
        idToProcess = Optional.ofNullable(jedis.spop(REDIS_SET_KEY));
      }
    } catch (SQLException e1) {
      logger.error("Caught an exception when opening or closing the SQLiteClient.", e1);
    }
    
    jp.destroy();
  }
}
