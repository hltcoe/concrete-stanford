/**
 *
 */
package concrete.server.sql;

import java.sql.SQLException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.util.ConcreteException;

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
      logger.info("There were {} annotated sentences.", cli.countNumberAnnotatedSentences());
    } catch (SQLException | ConcreteException e) {
      logger.error("Caught SQL/ConcreteException.", e);
    }
  }
}
