/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import edu.jhu.hlt.concrete.analytics.base.Analytic;
import edu.jhu.hlt.concrete.miscommunication.WrappedCommunication;
import edu.jhu.hlt.concrete.server.ConcreteServer;
import edu.jhu.hlt.concrete.server.ServerException;
import edu.jhu.hlt.concrete.stanford.languages.PipelineLanguage;
import edu.jhu.hlt.concrete.stanford.runners.LanguageConverter;
import edu.jhu.hlt.concrete.stanford.runners.StanfordParameterDelegate;
import edu.jhu.hlt.utilt.ex.LoggedUncaughtExceptionHandler;
import edu.jhu.hlt.utilt.sys.SystemErrDisabler;

/**
 *
 */
public class ConcreteStanfordThriftServerLauncher {

  private static final Logger logger = LoggerFactory.getLogger(ConcreteStanfordThriftServerLauncher.class);

  @Parameter
  private List<String> paramList = new ArrayList<>();

  @Parameter(names = "--help", help = true,
      description = "Print the usage information and exit.")
  private boolean help;

  @Parameter(names = "--language", required = true,
      description = "The language to launch.",
      converter = LanguageConverter.class)
  private PipelineLanguage language;

  @Parameter(names = "--port",
      description = "The port on which to listen for clients.")
  private int port = 33221;

  @ParametersDelegate
  private StanfordParameterDelegate stanfordParams = new StanfordParameterDelegate();

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
    JCommander jc = JCommander.newBuilder().addObject(rl).build();
    jc.parse(args);
    jc.setProgramName(ConcreteStanfordThriftServerLauncher.class.getName());
    if (rl.help) {
      jc.usage();
      return;
    }

    try {
      // annoying Stanford junk
      SystemErrDisabler dis = new SystemErrDisabler();
      dis.disable();
      PipelineLanguage lang = rl.language;
      List<Analytic<? extends WrappedCommunication>> analytics =
          rl.stanfordParams.getAnalytics(lang);

      ConcreteStanfordThriftServer srv = new ConcreteStanfordThriftServer(analytics);
      ConcreteServer.createServer(srv, rl.port);
    } catch (ServerException | UnsupportedEncodingException e) {
      logger.error("Caught exception while running the server.", e);
    } catch (IOException e) {
      logger.error("Error setting up analytics", e);
    }
  }
}
