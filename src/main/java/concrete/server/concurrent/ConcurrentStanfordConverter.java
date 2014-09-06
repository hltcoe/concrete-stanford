/*
 *
 */
package concrete.server.concurrent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.time.StopWatch;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.util.CommunicationSerialization;
import edu.jhu.hlt.gigaword.ClojureIngester;
import edu.jhu.hlt.gigaword.ProxyDocument;

/**
 * @author max
 *
 */
public class ConcurrentStanfordConverter implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(ConcurrentStanfordConverter.class);

  private final ExecutorService runner;
  private final CompletionService<Communication> srv;

  /**
   *
   */
  public ConcurrentStanfordConverter() {
    this.runner = Executors.newCachedThreadPool();
    this.srv = new ExecutorCompletionService<Communication>(this.runner);
  }

  public Future<Communication> annotate(Future<Communication> fc) throws InterruptedException, ExecutionException {
    return this.srv.submit(new CallableConcreteServer(fc));
  }

  public Future<Communication> annotate(Communication c) throws InterruptedException, ExecutionException {
    return this.srv.submit(new CallableConcreteServer(c));
  }

  /**
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
//    int threads = Runtime.getRuntime().availableProcessors();
//    if (threads < 8) {
//      logger.info("You need at least 8 threads to run this program. You only have {} available.", threads);
//      System.exit(1);
//    }

    if (args.length != 1) {
      logger.info("This program takes 1 argument: the path to a .txt file with paths to agiga documents, 1 per line.");
      System.exit(1);
    }

    Optional<String> psqlHost = Optional.ofNullable(System.getenv("HURRICANE_HOST"));
    Optional<String> psqlDBName = Optional.ofNullable(System.getenv("HURRICANE_DB"));
    Optional<String> psqlUser = Optional.ofNullable(System.getenv("HURRICANE_USER"));
    Optional<String> psqlPass = Optional.ofNullable(System.getenv("HURRICANE_PASS"));

    if (!psqlHost.isPresent() || !psqlDBName.isPresent() || !psqlUser.isPresent() || !psqlPass.isPresent()) {
      logger.info("You need to set the following environment variables to run this program:");
      logger.info("HURRICANE_HOST : hostname of a postgresql server");
      logger.info("HURRICANE_DB : database name to use");
      logger.info("HURRICANE_USER : database user with appropriate privileges");
      logger.info("HURRICANE_PASS : password for user");
      System.exit(1);
    }

    Properties props = new Properties();
    props.setProperty("user", psqlUser.get());
    props.setProperty("password", psqlPass.get());
    // props.setProperty("ssl", "true");
    Connection conn = DriverManager.getConnection("jdbc:postgresql://" + psqlHost.get() + "/" + psqlDBName.get(), props);
    conn.setAutoCommit(false);
    logger.info("Successfully connected to database.");

    CommunicationSerialization cs = new CommunicationSerialization();

    logger.info("Sleeping to allow profiler hooks...");
    Thread.sleep(10000);
    logger.info("Proceeding.");

    // this is silly, but needed for stanford logging disable.
    PrintStream err = System.err;

    System.setErr(new PrintStream(new OutputStream() {
      public void write(int b) { }
    }));

    StopWatch sw = new StopWatch();
    logger.info("Ingest beginning at: {}", new DateTime().toString());
    Path pathToCommFiles = Paths.get(args[0]);

    ClojureIngester ci = new ClojureIngester();
    ConcurrentStanfordConverter annotator = new ConcurrentStanfordConverter();

    List<Future<Communication>> comms = new ArrayList<>();
    try(Scanner sc = new Scanner(pathToCommFiles.toFile())) {
      while (sc.hasNextLine()) {
        // paths.add(Paths.get(sc.nextLine()));
        String pathStr = sc.nextLine();
        logger.info("Processing file: {}", pathStr);
        Iterator<ProxyDocument> iter = ci.proxyGZipPathToProxyDocIter(pathStr);
        int k = 0;
        while (iter.hasNext() && k < 3) {
          ProxyDocument pd = iter.next();
          Communication c = pd.sectionedCommunication();
          Future<Communication> fc = annotator.annotate(c);
          logger.info("Task submitted.");
          comms.add(fc);
          k++;
        }
      }
    }

    logger.info("All tasks submitted. Preparing SQL inserts.");
    for (Future<Communication> c : comms) {
      Communication ac = c.get();
      logger.info("Retrieved communication: {}", ac.getId());
      try (PreparedStatement ps = conn.prepareStatement("INSERT INTO documents (id, bytez) VALUES (?,?)");) {
        ps.setString(1, ac.getId());
        ps.setBytes(2, cs.toBytes(ac));
        ps.executeUpdate();
      } catch (SQLException e) {
        logger.error("Got SQL Exception.", e);
      }
    }

    logger.info("Database transaction prepared. Committing.");
    conn.commit();
    logger.info("Committed successfully. Shutting down database connection.");
    conn.close();

    sw.stop();
    logger.info("Ingest complete. Took {} ms.", sw.getTime());
    annotator.close();
    System.setErr(err);
  }

  @Override
  public void close() throws Exception {
    this.runner.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    this.runner.shutdown();
  }

}
