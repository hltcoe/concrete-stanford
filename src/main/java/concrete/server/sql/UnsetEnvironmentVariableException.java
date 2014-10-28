/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server.sql;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author max
 *
 */
public class UnsetEnvironmentVariableException extends Exception {

  /**
   * 
   */
  private static final long serialVersionUID = 4839881316245515440L;

  /**
   * @param envVar
   */
  UnsetEnvironmentVariableException(String envVar) {
    super("Environemnt variable :" + envVar + " was not set, but is required.");
  }
  
  private static String envVarsToString(Collection<String> envVarColl) {
    if (envVarColl.size() == 0)
      throw new IllegalArgumentException("Don't call this ctor with 0 arg coll.");
    StringBuilder sb = new StringBuilder();
    Iterator<String> sIter = envVarColl.iterator();
    String next = sIter.next();
    sb.append(next);
    while (sIter.hasNext()) {
      sb.append(", ");
      sb.append(sIter.next());
    }
    
    return sb.toString();
  }
  
  /**
   * @param envVarColl
   */
  UnsetEnvironmentVariableException(Collection<String> envVarColl) {
    super("The following environment variables must be set :" + envVarsToString(envVarColl));
  }
}
