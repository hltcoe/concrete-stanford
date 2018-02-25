package edu.jhu.hlt.concrete.stanford.runners;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.Parameter;
import com.google.common.collect.ImmutableList;

import edu.jhu.hlt.concrete.analytics.base.Analytic;
import edu.jhu.hlt.concrete.miscommunication.WrappedCommunication;
import edu.jhu.hlt.concrete.stanford.languages.PipelineLanguage;
import edu.jhu.hlt.utilt.sys.SystemErrDisabler;

/**
 * Utility class for stanford command line parameters.
 */
public class StanfordParameterDelegate {

  private static final Logger LOGGER = LoggerFactory.getLogger(StanfordParameterDelegate.class);

  private SystemErrDisabler errDisabler = new SystemErrDisabler();

  @Parameter(names = "--fail-fast", description="Stop with a non-zero status code on the first exception. Useful if each document is expected to be successfully processed.")
  boolean exitOnException = false;

  @Parameter(names = "--tokenized-input", description="If true, assume input has already been tokenized.")
  boolean isInputTokenized = false;

  @Parameter(names = "--only-tokenize", description="If true, stop after tokenization and sentence split/segmentation.")
  boolean isOnlyUpToTokenization = false;

  @Parameter(names = "--run-coref",
      description = "Run coreference resolution on the communications. Currently only enabled for English.")
  boolean isCoreferenceEnabled = false;

  @Parameter(names = "--enable-std-err",
      description = "Enable standard error. By default, Stanford prints a lot of output to std err.")
  boolean isStdErrEnabled = false;

  public ImmutableList<Analytic<? extends WrappedCommunication>> getAnalytics(PipelineLanguage lang) throws IOException {
    List<Analytic<? extends WrappedCommunication>> al = new ArrayList<>();
    // if the input is not tokenized, the segment/tokenization
    // analytics have to be run first.
    if (!this.isInputTokenized)
      al.add(lang.getSentenceTokenizationAnalytic());
    else
      LOGGER.info("Omiting tokenization step");
    // if NOT stopping at tokenization, add other analytics
    if (!this.isOnlyUpToTokenization) {
      // if coref is enabled, only add it for english -
      // the others don't have it implemented.
      if (this.isCoreferenceEnabled) {
        if (lang == PipelineLanguage.ENGLISH) {
          al.add(lang.getAllAnalytic());
        } else {
          LOGGER.warn("Coreference not enabled for language: {}", lang.toString());
          al.add(lang.getPreCorefAnalytic());
        }
        // otherwise, just add the non-coref version
      } else {
        al.add(lang.getPreCorefAnalytic());
      }
    }

    ImmutableList<Analytic<? extends WrappedCommunication>> analytics =
        ImmutableList.copyOf(al);
    if (analytics.isEmpty())
      throw new IOException("Configuration resulted in no analytics specified");

    return analytics;
  }

  public void handleStdErr() throws UnsupportedEncodingException {
    if (!this.isStdErrEnabled)
      this.errDisabler.disable();
  }
}
