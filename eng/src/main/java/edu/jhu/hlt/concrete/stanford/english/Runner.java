package edu.jhu.hlt.concrete.stanford.english;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;

import edu.jhu.hlt.concrete.stanford.languages.PipelineLanguage;
import edu.jhu.hlt.concrete.stanford.runners.StanfordOpts;

public class Runner {

  private static final Logger LOGGER = LoggerFactory.getLogger(Runner.class);

  public static void main(String[] args) {
    StanfordOpts opts = new StanfordOpts();
    JCommander jc = JCommander.newBuilder().addObject(opts).build();
    jc.parse(args);

    if (opts.help()) {
      jc.usage();
      return;
    }

    PipelineLanguage lang = PipelineLanguage.ENGLISH;
    boolean failed = false;
    try {
      opts.handleStdError();
      opts.pipeline(lang);
    } catch (IOException e) {
      LOGGER.error("Error during processing", e);
      failed = true;
    }

    if (failed)
      System.exit(128);
  }
}
