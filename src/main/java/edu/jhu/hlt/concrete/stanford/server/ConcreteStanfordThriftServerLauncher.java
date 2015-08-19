/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford.server;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import edu.jhu.hlt.concrete.server.ConcreteServer;
import edu.jhu.hlt.concrete.server.ServerException;
import edu.jhu.hlt.concrete.stanford.PipelineLanguage;
import edu.jhu.hlt.utilt.ex.LoggedUncaughtExceptionHandler;
import edu.jhu.hlt.utilt.sys.SystemErrDisabler;

/**
 *
 */
public class ConcreteStanfordThriftServerLauncher {

  private static final Logger logger = LoggerFactory.getLogger(ConcreteStanfordThriftServerLauncher.class);

  @Parameter
  private List<String> paramList = new ArrayList<>();

  @Parameter(names = "--help", help = true, description = "Print the usage information and exit.")
  private boolean help;

  @Parameter(names = "--language", required = true, description = "The language to launch. Supported: en, cn")
  private String language;

  @Parameter(names = "--port", description = "The port on which to listen for clients.")
  private Integer port = 33221;

  /**
   *
   */
  public ConcreteStanfordThriftServerLauncher() {
    // TODO Auto-generated constructor stub
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    Thread.setDefaultUncaughtExceptionHandler(new LoggedUncaughtExceptionHandler());
    ConcreteStanfordThriftServerLauncher rl = new ConcreteStanfordThriftServerLauncher();
    JCommander jc = new JCommander(rl, args);
    jc.setProgramName(ConcreteStanfordThriftServerLauncher.class.getName());
    if (rl.help) {
      jc.usage();
      return;
    }

    PipelineLanguage lang;
    try {
      lang = PipelineLanguage.getEnumeration(rl.language);
    } catch (IllegalArgumentException iae) {
      throw new RuntimeException("Language " + rl.language + " is not supported.");
    }

    try {
      // annoying Stanford junk
      SystemErrDisabler dis = new SystemErrDisabler();
      dis.disable();
      ConcreteServer.createServer(new ConcreteStanfordThriftServer(lang), rl.port);
    } catch (ServerException | UnsupportedEncodingException e) {
      logger.error("Caught exception while running the server.", e);
    }
  }
}
