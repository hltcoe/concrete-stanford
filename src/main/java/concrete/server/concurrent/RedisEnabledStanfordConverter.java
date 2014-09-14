/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.concurrent;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

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
  public static final String FINISHED_KEY = "gigaword-finished-keys";

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

    SystemErrDisabler sed = new SystemErrDisabler();
    sed.disable();

    ClojureIngester ci = new ClojureIngester();
    StanfordAgigaPipe pipe = new StanfordAgigaPipe();

    Map<String, Communication> idToCommMap = new HashMap<>(1000);
    JedisPool jp = new JedisPool(redisHost, redisPort);
    try (Jedis jedis = jp.getResource();) {
      Optional<String> idToProcess = Optional.ofNullable(jedis.spop(REDIS_SET_KEY));
      while (idToProcess.isPresent()) {
        String idToGrab = idToProcess.get();
        logger.info("On document: {}", idToGrab);
        String document = jedis.get(idToGrab);
        try {
          Communication wSections = ci.proxyDocStringToProxyDoc(document).sectionedCommunication();
          Communication postStanford = pipe.process(wSections);
          idToCommMap.put(idToGrab, postStanford);
          // sqlcli.insert(postStanford);
          // jedis.sadd(FINISHED_KEY, idToGrab);
        } catch (IOException | TException | ConcreteException | AnnotationException e) {
          logger.warn("Caught an exception while annotating a document.", e);
          logger.warn("Document in question: {}", idToGrab);
          // Put key in error set.
          jedis.sadd(ERRORS_KEY, idToGrab);
        }

        if (idToCommMap.size() == 100) {
          logger.info("Attempting to write to DB.");
          int connAttemptsRemaining = 10;
          while (connAttemptsRemaining > 0) {
            connAttemptsRemaining--;
            Set<Entry<String, Communication>> eSet = new HashSet<>();
            try (SQLiteClient sqlcli = new SQLiteClient(args[2], 101)) {
              Iterator<Entry<String, Communication>> iter = idToCommMap.entrySet().iterator();
              while (iter.hasNext()) {
                Entry<String, Communication> entry = iter.next();
                String docId = entry.getKey();
                Communication annotated = entry.getValue();
                try {
                  sqlcli.insert(annotated);
                  jedis.sadd(FINISHED_KEY, docId);
                  eSet.add(entry);
                } catch (ConcreteException e) {
                  logger.warn("Caught a ConcreteException during writing to the DB.", e);
                  jedis.sadd(ERRORS_KEY, docId);
                  iter.remove();
                }
              }

            } catch (SQLException se) {
              logger.warn("Caught SQLException; backing off and trying again.", se);
              try {
                Thread.sleep(2 * (2 * (10 - connAttemptsRemaining)) * 1000);
              } catch (InterruptedException e) {
                logger.warn("Sleep messed up (won't happen)");
              }
            }

            // Remove committed docs, then retry if needed.
            idToCommMap.entrySet().removeAll(eSet);
            // Reaching this point means that the iteration has finished.
            // Check size. If 0, break out of SQL loop.
            if (idToCommMap.size() == 0)
              break;
            else
              logger.info("Attempting to reconnect.");
          }

          if (connAttemptsRemaining == 0 && idToCommMap.size() > 0) {
            logger.error("Failed to connect to the SQL database after 10 tries. Crashing.");
            idToCommMap.keySet().forEach(i -> jedis.sadd(ERRORS_KEY, i));
            System.exit(1);
          }
        }

        idToProcess = Optional.ofNullable(jedis.spop(REDIS_SET_KEY));
      }
    }

    sed.enable();
    jp.destroy();
  }
}

// catch (SQLException se) {
// logger.error("Caught a SQLException when inserting document.", se);
// logger.error("Document in question: {}", idToGrab);
// try (Jedis nj = jp.getResource()) {
// nj.sadd(ERRORS_KEY, idToGrab);
// }
// }
