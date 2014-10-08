/*
 *
 */
package concrete.server.concurrent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
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

import concrete.interfaces.ProxyCommunication;
import concrete.server.LoggedUncaughtExceptionHandler;
import concrete.server.sql.PostgresClient;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe;
import edu.jhu.hlt.gigaword.ClojureIngester;
import edu.jhu.hlt.gigaword.ProxyCommunicationConverter;

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
  public ConcurrentStanfordConverter(int nThreads, String... paths) {
    // this.runner = Executors.newCachedThreadPool();
    // int aThreads = Runtime.getRuntime().availableProcessors();
    // int toUse = aThreads > 16 ? aThreads - 8 : aThreads;
    this.runner = Executors.newFixedThreadPool(nThreads);
    this.srv = new ExecutorCompletionService<Communication>(this.runner);
  }

  public Future<Communication> annotate(Future<Communication> fc) throws InterruptedException, ExecutionException, IOException {
    return this.srv.submit(new CallableConcreteServer(fc));
  }

  public Future<Communication> annotate(Communication c) throws InterruptedException, ExecutionException, IOException {
    return this.srv.submit(new CallableConcreteServer(c));
  }

  /**
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) {
    if (args.length != 2) {
      logger.info("This program takes 1 argument: the path to a .txt file with paths to agiga documents, 1 per line, and how many threads to use [minimum 4].");
      System.exit(1);
    }

    logger.info("Setting up uncaught exception handler.");
    Thread.setDefaultUncaughtExceptionHandler(new LoggedUncaughtExceptionHandler());

    Path pathToCommFiles = Paths.get(args[0]);
    if (!Files.exists(pathToCommFiles)) {
      logger.error("No file at: {} ; can't ingest anything.", pathToCommFiles.toString());
      System.exit(1);
    }

    int nThreadsToUse = -1;
    try {
      nThreadsToUse = Integer.parseInt(args[1]);
    } catch (NumberFormatException e1) {
      logger.info("Couldn't interpret {} as an integer. Try again.");
      System.exit(1);
    }

    if (nThreadsToUse < 4) {
      logger.info("Minimum 4 threads required.");
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

    try (PostgresClient pgc = new PostgresClient(psqlHost.get(), psqlDBName.get(), psqlUser.get(), psqlPass.get().getBytes());) {
      logger.info("Successfully connected to database. Getting previously ingested IDs.");
      Set<String> idSet = pgc.getIngestedDocIds();
      logger.info("Got previously ingested IDs. There are {} previously ingested documents.", idSet.size());

      logger.info("Warming up models. This is a good time for profilers to hook in.");
      new StanfordAgigaPipe();

      Thread.sleep(2500);
      logger.info("Proceeding.");

      logger.info("Disabling System.err.");
      SystemErrDisabler disabler = new SystemErrDisabler();
      disabler.disable();

      logger.info("Using {} threads.", nThreadsToUse);
      StopWatch sw = new StopWatch();
      logger.info("Ingest beginning at: {}", new DateTime().toString());
      sw.start();

      ClojureIngester ci = new ClojureIngester();
      ConcurrentStanfordConverter annotator = new ConcurrentStanfordConverter(nThreadsToUse);

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
      for (String pathStr : pathStrs) {
        ArrayDeque<Communication> dq = new ArrayDeque<Communication>(12000);
        Set<String> docIdsToProcess = new HashSet<String>(12000);

        logger.info("Processing file: {}", pathStr);
        Iterator<ProxyCommunication> iter = ci.getProxyCommunicationIteratable(pathStr);
        while (iter.hasNext()) {
          ProxyCommunication pd = iter.next();
          String pId = pd.getId();
          if (idSet.contains(pId))
            continue;

          Communication c = new ProxyCommunicationConverter(pd).toCommunication();
          dq.push(c);
        }

        logger.info("Mapping complete. Submitting tasks.");
        while (dq.peek() != null) {
          Communication pending = dq.pop();
          annotator.annotate(pending);
          docIdsToProcess.add(pending.getId());
        }

        logger.info("{} tasks submitted. Preparing SQL inserts.", docIdsToProcess.size());
        while (!docIdsToProcess.isEmpty()) {
          logger.debug("Waiting on next document in driver...");
          // c = annotator.srv.poll(60 * 3, TimeUnit.SECONDS);
          Optional<Future<Communication>> oc = Optional.ofNullable(annotator.srv.poll(60 * 3, TimeUnit.SECONDS));
          if (!oc.isPresent()) {
            logger.warn("No documents were retrieved within 3 minutes.");
            logger.warn("It is likely a task died unexpectedly.");
            logger.warn("Here are the document IDs that have not been ingested:");
            docIdsToProcess.forEach(i -> logger.error(i));
            logger.warn("These documents should be checked for errors.");

            docIdsToProcess.clear();
            break;
          }

          try {
            Communication ac = oc.get().get();
            String docId = ac.getId();
            logger.debug("Retrieved communication: {}", docId);
            pgc.insertCommunication(ac);
            kProcessed++;
            if (kProcessed % 100 == 0)
              pgc.commit();
          } catch (InterruptedException | ExecutionException e1) {
            logger.error("Caught an Exception, likely when waiting for a Communication to process.", e1);
            logger.error("Remaining doc IDs:");
            docIdsToProcess.forEach(i -> logger.error(i));
          } finally {
            docIdsToProcess.clear();
          }
        }
      }

      sw.stop();
      logger.info("Ingest complete. Took {} ms.", sw.getTime());
      disabler.enable();

      try {
        annotator.close();
      } catch (Exception e) {
        logger.error("An error occurred when closing the ConcurrentStanfordAnnotator object.", e);
      }

    } catch (SQLException ex) {
      logger.error("An SQLException was caught while processing the connection.", ex);
    } catch (InterruptedException ex) {
      logger.error("An InterruptedException was caught while processing documents.", ex);
    } catch (ExecutionException e) {
      logger.error("An ExecutionException was caught during task submission or queue retrieval.", e);
    } catch (IOException e) {
      logger.error("An IOException was caught during task submission or queue retrieval.", e);
    }
  }

  @Override
  public void close() throws Exception {
    this.runner.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    this.runner.shutdown();
  }
}
