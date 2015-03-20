/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Disable/enable System.err calls. 
 * <br/><br/>
 * Mainly used to quiet the large amount of output from Stanford CoreNLP.
 */
public class SystemErrDisabler {

  private final PrintStream err;
  
  /**
   * Default ctor. Save a pointer to the current System.err so it can be enabled/disabled.
   */
  public SystemErrDisabler() {
    this.err = System.err;
  }
  
  /**
   * Disable writing to System.err.
   */
  public void disable() {
    System.setErr(new PrintStream(new OutputStream() {
      public void write(int b) {
      }
    }));
  }
  
  /**
   * Enable writing to System.err.
   */
  public void enable() {
    System.setErr(this.err);
  }
}
