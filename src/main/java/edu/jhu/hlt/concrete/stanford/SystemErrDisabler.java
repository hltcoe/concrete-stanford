/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Disable/enable System.err calls.
 * 
 * @author max
 *
 */
public class SystemErrDisabler {

  private final PrintStream err;
  
  /**
   * 
   */
  public SystemErrDisabler() {
    this.err = System.err;
  }
  
  public void disable() {
    System.setErr(new PrintStream(new OutputStream() {
      public void write(int b) {
      }
    }));
  }
  
  public void enable() {
    System.setErr(this.err);
  }

  /**
   * @param args
   */
  public static void main(String[] args) {

  }
}
