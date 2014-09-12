/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package concrete.server;

import java.lang.Thread.UncaughtExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author max
 *
 */
public class LoggedUncaughtExceptionHandler implements UncaughtExceptionHandler {
  
  private static final Logger logger = LoggerFactory.getLogger(LoggedUncaughtExceptionHandler.class);
  
  /**
   * 
   */
  public LoggedUncaughtExceptionHandler() {
    // TODO Auto-generated constructor stub
  }

  /* (non-Javadoc)
   * @see java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang.Thread, java.lang.Throwable)
   */
  @Override
  public void uncaughtException(Thread t, Throwable e) {
    logger.error("Caught unhandled exception in thread: [{}]", t.getName());
    logger.error("Exception is as follows.", e);
  }
}
