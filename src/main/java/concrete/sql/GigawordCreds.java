/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 *
 */
public class GigawordCreds implements SQLCreds {

  public static final String GIGAWORD_HOST_VAR = "GIGAWORD_HOST";
  public static final String GIGAWORD_USER_VAR = "GIGAWORD_USER";
  public static final String GIGAWORD_DB_VAR = "GIGAWORD_DB";
  public static final String GIGAWORD_PASS_VAR = "GIGAWORD_PASS";
  
  private final String host;
  private final String dbName;
  private final String userName;
  private final byte[] pass; 
  
  /**
   * 
   */
  public GigawordCreds() throws UnsetEnvironmentVariableException {
    List<String> unsetVarList = new ArrayList<>();
    
    String host = System.getenv(GIGAWORD_HOST_VAR);
    String dbName = System.getenv(GIGAWORD_DB_VAR);
    String userName = System.getenv(GIGAWORD_USER_VAR);
    byte[] pass = System.getenv(GIGAWORD_PASS_VAR).getBytes();
    
    if (host == null)
      unsetVarList.add(GIGAWORD_HOST_VAR);
    if (dbName == null)
      unsetVarList.add(GIGAWORD_DB_VAR);
    if (userName == null)
      unsetVarList.add(GIGAWORD_USER_VAR);
    if (pass == null)
      unsetVarList.add(GIGAWORD_PASS_VAR);
    
    if (unsetVarList.size() > 0)
      throw new UnsetEnvironmentVariableException(unsetVarList);
    
    this.host = host;
    this.dbName = dbName;
    this.userName = userName;
    this.pass = pass;
  }

  /**
   * @return the host
   */
  public String getHost() {
    return host;
  }

  /**
   * @return the dbName
   */
  public String getDbName() {
    return dbName;
  }

  /**
   * @return the userName
   */
  public String getUserName() {
    return userName;
  }

  /**
   * @return the pass
   */
  public byte[] getPass() {
    return pass;
  }
}
