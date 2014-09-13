/*
 * 
 */
package concrete.server.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.util.CommunicationSerialization;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 * @author max
 *
 */
public class SQLiteClient implements AutoCloseable {

  public static final String DOCUMENTS_TABLE = "documents";
  
  private static final Logger logger = LoggerFactory.getLogger(SQLiteClient.class);
  
  private final String pathString;
  private final Connection conn;
  private final CommunicationSerialization cs = new CommunicationSerialization();
  private PreparedStatement ps = null;
  
  private int kSinceCommit = 0;
  private final int kUntilCommit;
  
  public SQLiteClient (String pathString, int kUntilCommit) {
    try {
      Class.forName("org.sqlite.JDBC");
      this.pathString = pathString;
      this.kUntilCommit = kUntilCommit;
      this.conn = DriverManager.getConnection("jdbc:sqlite:" + this.pathString);
      this.conn.setAutoCommit(false);
      logger.info("Database connection successful.");
    } catch (ClassNotFoundException | SQLException e) {
      throw new RuntimeException("Couldn't initialize SQLiteClient.", e);
    }
  }
  
  public SQLiteClient (String pathString) {
    this(pathString, 10);
  }
  
  private void initializeDb() throws SQLException {
    Statement s = this.conn.createStatement();
    s.execute("CREATE TABLE " + DOCUMENTS_TABLE + " (id text not null primary key, bytez blob not null)");
    s.close();
  }
  
  public void insert(Communication c) throws SQLException, ConcreteException {
    if (this.ps == null)
      this.ps = this.conn.prepareStatement("INSERT INTO " + DOCUMENTS_TABLE + " (id, bytez) VALUES (?, ?)");
    
    this.ps.setString(1, c.getId());
    this.ps.setBytes(2, this.cs.toBytes(c));
    this.ps.executeUpdate();
    kSinceCommit++;
    
    if (kSinceCommit % kUntilCommit == 0)
      this.conn.commit();
  }
  
  @Override
  public void close() throws SQLException {
    if (this.ps != null)
      this.ps.close();
    
    this.conn.commit();
    this.conn.close();
  }
  
  public static void main(String... args) {
    String pathStr = args[0];
    try (SQLiteClient cli = new SQLiteClient(pathStr)) {
      cli.initializeDb();
      logger.info("Database at {} has been initialized successfully.", pathStr);
    } catch (SQLException e) {
      logger.error("Failed to create and/or initialize the db.", e);
    }
  }
}
