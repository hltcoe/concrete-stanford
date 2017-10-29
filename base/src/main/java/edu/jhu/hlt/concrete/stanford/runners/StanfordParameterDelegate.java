package edu.jhu.hlt.concrete.stanford.runners;

import com.beust.jcommander.Parameter;

/**
 * Utility class for stanford command line parameters.
 */
public class StanfordParameterDelegate {

  @Parameter(names = "--fail-fast", description="Stop with a non-zero status code on the first exception. Useful if each document is expected to be successfully processed.")
  boolean exitOnException = false;

  @Parameter(names = "--tokenized-input", description="If true, assume input has already been tokenized.")
  boolean isInputTokenized = false;

  @Parameter(names = "--only-tokenize", description="If true, stop after tokenization and sentence split/segmentation.")
  boolean isOnlyUpToTokenization = false;
}
