/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.sql;

/**
 * @author max
 *
 */
public interface SQLCreds {
  public String getHost();
  public String getDbName();
  public String getUserName();
  public byte[] getPass();
}
