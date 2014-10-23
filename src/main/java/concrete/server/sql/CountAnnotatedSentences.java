/**
 *
 */
package concrete.server.sql;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author max
 *
 */
public class CountAnnotatedSentences {

  private static final Logger logger = LoggerFactory.getLogger(CountAnnotatedSentences.class);


  /**
   * @param args
   */
  public static void main(String[] args) {
    Optional<String> psqlHost = Optional.ofNullable(System.getenv("GIGAWORD_HOST"));
    Optional<String> psqlDBName = Optional.ofNullable(System.getenv("GIGAWORD_DB"));
    Optional<String> psqlUser = Optional.ofNullable(System.getenv("GIGAWORD_USER"));
    Optional<String> psqlPass = Optional.ofNullable(System.getenv("GIGAWORD_PASS"));

    if (!psqlHost.isPresent() || !psqlDBName.isPresent() || !psqlUser.isPresent() || !psqlPass.isPresent()) {
      logger.info("You need to set the following environment variables to run this program:");
      logger.info("GIGAWORD_HOST : hostname of a postgresql server");
      logger.info("GIGAWORD_DB : database name to use");
      logger.info("GIGAWORD_USER : database user with appropriate privileges");
      logger.info("GIGAWORD_PASS : password for user");
      System.exit(1);
    }

    try (PostgresClient cli = new PostgresClient(psqlHost.get(), psqlDBName.get(), psqlUser.get(), psqlPass.get().getBytes())) {
      logger.info("Beginning.");
      logger.info("There were {} annotated sentences.", cli.countNumberAnnotatedSentences());
      logger.info("Done.");
    } catch (Exception e) {
      logger.error("Caught SQL/ConcreteException.", e);
    }
  }
}
