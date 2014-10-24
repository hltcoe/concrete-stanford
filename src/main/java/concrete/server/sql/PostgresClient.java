/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import proxy.interfaces.ProxyCommunication;
import clojure.java.api.Clojure;
import clojure.lang.IFn;
import concrete.server.concurrent.CallableBytesToConcreteSentenceCount;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.CommunicationSerialization;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.gigaword.ClojureIngester;

/**
 * @author max
 *
 */
public class PostgresClient implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(PostgresClient.class);

  public static final String DOCUMENTS_TABLE = "documents_raw";
  public static final String ANNOTATED_TABLE = "annotated";
  public static final String SENTENCE_COUNT_TABLE = "sentence_counts";

  private final String randQuery = "SELECT raw FROM " + DOCUMENTS_TABLE + " WHERE id NOT IN"
       + " (SELECT documents_id FROM " + ANNOTATED_TABLE + " ) AND random() < 0.01 LIMIT 100";

  private final String host;
  private final String dbName;
  private final String userName;
  private final byte[] pass;

  private final CommunicationSerialization cs = new CommunicationSerialization();
  private final Connection conn;

  private final PreparedStatement insertPS;
  private final PreparedStatement isAnnotatedPS;
  private final PreparedStatement getDocumentPS;
  private final PreparedStatement nextCommPS;
  private final PreparedStatement annotatedCommPS;
  private final PreparedStatement insertCountPS;

  private final ClojureIngester ci = new ClojureIngester();
  
  private boolean isAutoCommitEnabled = false;

  public void setAutoCommit(boolean ac) throws SQLException {
    this.conn.setAutoCommit(ac);
    this.isAutoCommitEnabled = ac;
  }
  
  public boolean getAutoCommitStatus() {
    return this.isAutoCommitEnabled;
  }
  
  public void commit() throws SQLException {
    this.conn.commit();
  }
  
  /**
   * @throws SQLException
   *
   */
  public PostgresClient(String host, String dbName, String userName, byte[] pass) throws SQLException {
    IFn req = Clojure.var("clojure.core", "require");
    // req.invoke(Clojure.read("gigaword-ingester.giga"));
    req.invoke(Clojure.read("gigaword-ingester.giga"));

    this.host = host;
    this.dbName = dbName;
    this.userName = userName;
    this.pass = pass;

    this.conn = this.getConnector();
    this.insertPS = this.conn.prepareStatement("INSERT INTO " + ANNOTATED_TABLE + " (documents_id, bytez) VALUES (?,?)");
    this.isAnnotatedPS = this.conn.prepareStatement("SELECT documents_id FROM annotated WHERE documents_id = ?");
    this.getDocumentPS = this.conn.prepareStatement("SELECT raw FROM documents_raw WHERE id = ?");
    this.nextCommPS = this.conn.prepareStatement(randQuery);
    this.annotatedCommPS = this.conn.prepareStatement("SELECT bytez FROM annotated WHERE documents_id = ?");
    this.insertCountPS = this.conn.prepareStatement("INSERT INTO " + SENTENCE_COUNT_TABLE
        + " (documents_id, count, token_count) VALUES (?, ?, ?)");
  }

  public boolean isDocumentAnnotated(String id) throws SQLException {
    this.isAnnotatedPS.setString(1, id);
    try (ResultSet rs = this.isAnnotatedPS.executeQuery();) {
      return rs.next();
    }
  }

  public boolean availableUnannotatedCommunications() throws SQLException {
    try (ResultSet rs = this.nextCommPS.executeQuery()) {
      return rs.next();
    }
  }

  public ProxyCommunication getUnannotatedCommunication() throws SQLException {
    try (ResultSet rs = this.nextCommPS.executeQuery()) {
      if (rs.next()) {
        String rawDoc = rs.getString("raw");
        return this.ci.proxyStringToProxyCommunication(rawDoc);
      } else {
        throw new SQLException("Thought a document would come back, but got no results.");
      }
    }
  }

  public Communication get(String id) throws SQLException, ConcreteException {
    this.annotatedCommPS.setString(1, id);
    try (ResultSet rs = this.annotatedCommPS.executeQuery()) {
      if (rs.next())
        return this.cs.fromBytes(rs.getBytes("bytez"));
      else
        throw new SQLException("No annotated document found for ID: " + id);
    }
  }

  public ProxyCommunication getDocument(String id) throws SQLException {
    this.getDocumentPS.setString(1, id);
    try (ResultSet rs = this.getDocumentPS.executeQuery()) {
      if (rs.next()) {
        String rawDoc = rs.getString("raw");
        return this.ci.proxyStringToProxyCommunication(rawDoc);
      } else {
        throw new SQLException("Thought a document would come back, but got no results.");
      }
    }
  }

  private Connection getConnector() throws SQLException {
    Properties props = new Properties();
    props.setProperty("user", this.userName);
    props.setProperty("password", new String(pass));

    return DriverManager.getConnection("jdbc:postgresql://" + this.host + "/" + this.dbName, props);
  }

  public void insertCommunication(Communication c) throws SQLException {
    String docId = c.getId();
    try {
      if (this.isDocumentAnnotated(docId)) {
        logger.info("Beat to the punch: {} already annotated.", docId);
        return;
      }

      this.insertPS.setString(1, docId);
      this.insertPS.setBytes(2, this.cs.toBytes(c));
      this.insertPS.executeUpdate();
    } catch (SQLException e) {
      logger.error("Caught an SQLException inserting documents.", e);
      // logger.error("Problematic document file: {}", pathStr);
      logger.error("Problematic document ID: {}", docId);
    } catch (ConcreteException e) {
      logger.error("There was an error creating a byte array from communication: {}", docId);
      // logger.error("Problematic document file: {}", pathStr);
      logger.error("Problematic document ID: {}", docId);
    }
  }
  
  public Set<String> getAnnotatedDocIds() throws SQLException {
    Set<String> idSet = new HashSet<String>(12000000);

    try (Connection conn = this.getConnector();
        PreparedStatement ps = conn.prepareStatement("SELECT documents_id FROM annotated");) {
      conn.setAutoCommit(false);
      ps.setFetchSize(10000);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        String id = rs.getString(1);
        idSet.add(id);
      }
    }

    return idSet;
  }

  public Set<String> getIngestedDocIds() throws SQLException {
    Set<String> idSet = new HashSet<String>(12000000);

    try (Connection conn = this.getConnector();
        PreparedStatement ps = conn.prepareStatement("SELECT id FROM documents_raw");) {
      conn.setAutoCommit(false);
      ps.setFetchSize(10000);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        String id = rs.getString(1);
        idSet.add(id);
      }
    }

    return idSet;
  }
  
  public Set<String> getCountedDocIds() throws SQLException {
    Set<String> idSet = new HashSet<String>(12000000);

    try (Connection conn = this.getConnector();
        PreparedStatement ps = conn.prepareStatement("SELECT documents_id FROM sentence_counts");) {
      conn.setAutoCommit(false);
      ps.setFetchSize(10000);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        String id = rs.getString(1);
        idSet.add(id);
      }
    }

    return idSet;
  }

  public Communication getSingleDocument() throws SQLException, ConcreteException {
    try (Connection conn = this.getConnector();
        PreparedStatement ps = conn.prepareStatement("SELECT bytez FROM annotated LIMIT 1");) {
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        byte[] bytez = rs.getBytes("bytez");
        return this.cs.fromBytes(bytez);
      } else {
        throw new SQLException("No documents available.");
      }
    }
  }

  public void countSentences(String id) throws SQLException, ConcreteException {
    if (!this.isDocumentAnnotated(id)) {
      logger.warn("Document ID {} has not been annotated - need to investigate this");
      return;
    }

    int nSentences = 0;
    int nTokens = 0;
      
    Communication c = this.get(id);
    if (c.isSetSectionList())
      for (Section s : c.getSectionList())
        if (s.isSetSentenceList()) {
          nSentences += s.getSentenceListSize();
          for (Sentence sent : s.getSentenceList()) {
            if (sent.isSetTokenization()) {
              Tokenization tkz = sent.getTokenization();
              if (tkz.isSetTokenList()) {
                TokenList tl = tkz.getTokenList();
                if (tl.isSetTokenList())
                  nTokens += tl.getTokenListSize();
                else
                  logger.warn("Document: {} has an unset token list inside TokenList.");
              } else
                logger.warn("Document: {} has an unset TokenList inside Tokenization.");
            }
          }
        }

    logger.info("Document {} sentence count: {}", id, nSentences);
    logger.info("Document {} token count: {}", id, nTokens);
    
    this.insertCountPS.setString(1, id);
    this.insertCountPS.setInt(2, nSentences);
    this.insertCountPS.setInt(3, nTokens);
    
    this.insertCountPS.executeUpdate();
  }

  public int countNumberAnnotatedSentences() throws Exception {
    try (Connection conn = this.getConnector();
        PreparedStatement ps = conn.prepareStatement("SELECT bytez FROM annotated");) {

      ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
      CompletionService<Integer> srv = new ExecutorCompletionService<>(exec);
      conn.setAutoCommit(false);
      final int fetchCtr = 10000;
      ps.setFetchSize(fetchCtr);
      int nSentences = 0;
      int docCounter = 0;

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          byte[] bytes = rs.getBytes("bytez");
          srv.submit(new CallableBytesToConcreteSentenceCount(bytes));
          docCounter++;

          if (docCounter % fetchCtr == 0) {
            logger.info("Counting {} sentences.", fetchCtr);
            StopWatch sw = new StopWatch();
            sw.start();
            for (int i = 0; i < fetchCtr; i++)
              nSentences += srv.take().get();

            sw.stop();
            logger.info("Counted sentences in {} ms.", sw.getTime());
          }
        }
      }

      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      exec.shutdown();
      return nSentences;
    }
  }

  /* (non-Javadoc)
   * @see java.lang.AutoCloseable#close()
   */
  @Override
  public void close() throws SQLException {
    logger.debug("Closing.");
    if (this.isAutoCommitEnabled)
      this.commit();
    this.conn.close();
  }

  public static void main (String... args) {
    if (args.length != 1) {
      logger.info("This program takes 1 argument: a path on disk to serialize a Communication object.");
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

    try (PostgresClient cli = new PostgresClient(psqlHost.get(), psqlDBName.get(), psqlUser.get(), psqlPass.get().getBytes())) {
      new SuperCommunication(cli.getSingleDocument()).writeToFile(args[0], true);
    } catch (SQLException | ConcreteException e) {
      logger.error("Caught SQL/ConcreteException.", e);
    }
  }
}
