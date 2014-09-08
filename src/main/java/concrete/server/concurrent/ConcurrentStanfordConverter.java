/*
 *
 */
package concrete.server.concurrent;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
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
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;
import edu.jhu.hlt.concrete.util.CommunicationSerialization;
import edu.jhu.hlt.concrete.util.ConcreteException;
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
    // this.runner = Executors.newCachedThreadPool();
    // int aThreads = Runtime.getRuntime().availableProcessors();
    // int toUse = aThreads > 16 ? aThreads - 8 : aThreads;
    this.runner = Executors.newFixedThreadPool(16);
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
  public static void main(String[] args) {
    // int threads = Runtime.getRuntime().availableProcessors();
    // if (threads < 8) {
    // logger.info("You need at least 8 threads to run this program. You only have {} available.", threads);
    // System.exit(1);
    // }

    if (args.length != 1) {
      logger.info("This program takes 1 argument: the path to a .txt file with paths to agiga documents, 1 per line.");
      System.exit(1);
    }
    
    logger.info("Setting up uncaught exception handler.");
    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        logger.error("Caught unhandled exception in thread: [{}]", t.getName());
        logger.error("Exception is as follows.", e);
      }
    });
    
    Path pathToCommFiles = Paths.get(args[0]);
    if (!Files.exists(pathToCommFiles)) {
      logger.error("No file at: {} ; can't ingest anything.", pathToCommFiles.toString());
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
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://" + psqlHost.get() + "/" + psqlDBName.get(), props);) {
      conn.setAutoCommit(false);
      logger.info("Successfully connected to database. Getting previously ingested IDs.");

      Set<String> idSet = new HashSet<>();
      try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM documents");) {
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
          String id = rs.getString(1);
          idSet.add(id);
        }
      }

      logger.info("Got previously ingested IDs. There are {} previously ingested documents.", idSet.size());
      CommunicationSerialization cs = new CommunicationSerialization();

      logger.info("Warming up models. This is a good time for profilers to hook in.");
      new StanfordAgigaPipe();
      
      Thread.sleep(5000);
      logger.info("Proceeding.");

      // this is silly, but needed for stanford logging disable.
      PrintStream err = System.err;

      System.setErr(new PrintStream(new OutputStream() {
        public void write(int b) {
        }
      }));

      StopWatch sw = new StopWatch();
      logger.info("Ingest beginning at: {}", new DateTime().toString());

      ClojureIngester ci = new ClojureIngester();
      ConcurrentStanfordConverter annotator = new ConcurrentStanfordConverter();

      List<String> pathStrs = new ArrayList<>();
      try (Scanner sc = new Scanner(pathToCommFiles.toFile())) {
        while (sc.hasNextLine())
          pathStrs.add(sc.nextLine());
      } catch (FileNotFoundException fnfe) {
        // Catch clause will only ever be reached if file is deleted between
        // beginning of program execution and this line. (not likely)
        logger.error("There was no file at: {}", pathToCommFiles.toString());
        System.exit(1);
      }

      int kProcessed = 0;
      int kPending = 0;
      for (String pathStr : pathStrs) {
        ArrayDeque<Communication> dq = new ArrayDeque<Communication>(1100);
        
        logger.info("Processing file: {}", pathStr);
        Iterator<ProxyDocument> iter = ci.proxyGZipPathToProxyDocIter(pathStr);
        while (iter.hasNext()) {
          ProxyDocument pd = iter.next();
          String pId = pd.getId();
          if (idSet.contains(pId)) {
            logger.info("Already ingested document: {}; skipping.", pId);
            continue;
          }

          Communication c = pd.sectionedCommunication();
          dq.push(c);
        }
        
        logger.info("Mapping complete. Submitting tasks.");
        while (dq.peek() != null) {
          Communication pending = dq.pop();
          annotator.annotate(pending);
          kPending++;
        }
        
        logger.info("Tasks submitted. Preparing SQL inserts.");
        while (kPending != 0) {
          logger.info("Waiting on next document in driver...");
          // c = annotator.srv.poll(60 * 3, TimeUnit.SECONDS);
          Future<Communication> c = annotator.srv.poll(60 * 3, TimeUnit.SECONDS);
          Communication ac = c.get();
          logger.debug("Retrieved communication: {}", ac.getId());
          kPending--;
          try (PreparedStatement ps = conn.prepareStatement("INSERT INTO documents (id, bytez) VALUES (?,?)");) {
            ps.setString(1, ac.getId());
            ps.setBytes(2, cs.toBytes(ac));
            ps.executeUpdate();
            kProcessed++;

            if (kProcessed % 100 == 0) {
              logger.info("Converted {} documents; committing.", kProcessed);
              conn.commit();
            }
          } catch (SQLException e) {
            logger.error("Caught an SQLException inserting documents.", e);
          } catch (ConcreteException e) {
            logger.error("There was an error creating a byte array from communication: {}", ac.getId());
          }
        }
      }

      logger.info("Database transaction prepared. Committing.");
      conn.commit();
      logger.info("Committed successfully. Shutting down database connection.");
      conn.close();

      sw.stop();
      logger.info("Ingest complete. Took {} ms.", sw.getTime());
      
      try {
        annotator.close();
      } catch (Exception e) {
        logger.error("An error occurred when closing the ConcurrentStanfordAnnotator object.", e);
      }
      
      System.setErr(err);
    } catch (SQLException ex) {
      logger.error("An SQLException was caught while processing the connection.", ex);
    } catch (InterruptedException ex) {
      logger.error("An InterruptedException was caught while processing documents.", ex);
    } catch (ExecutionException e) {
      logger.error("An ExecutionException was caught during task submission or queue retrieval.", e);
    } 
  }

  @Override
  public void close() throws Exception {
    this.runner.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    this.runner.shutdown();
  }

}
