/*
 * Copyright 2012-2016 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.lang3.time.StopWatch;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import edu.jhu.hlt.acute.archivers.tar.TarArchiver;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.Analytic;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.miscommunication.WrappedCommunication;
import edu.jhu.hlt.concrete.serialization.archiver.ArchivableCommunication;
import edu.jhu.hlt.concrete.serialization.iterators.TarGzArchiveEntryCommunicationIterator;
import edu.jhu.hlt.utilt.ex.LoggedUncaughtExceptionHandler;
import edu.jhu.hlt.utilt.io.ExistingNonDirectoryFile;
import edu.jhu.hlt.utilt.io.NotFileException;

/**
 *
 */
public class Runner {

  private static final Logger LOGGER = LoggerFactory.getLogger(Runner.class);

  @Parameter(required=true, names="--input-path", description="The path to the input file. Should be a tarred, gzipped file of communications.")
  private String inputPath;

  @Parameter(names="--output-path", description="The path to place output files.")
  private String outputPath = ".";

  @Parameter(names="--output-file", description="The name of the output file.")
  private String outputName = "comms.tar.gz";

  @Parameter(names = { "--help", "-h" }, help = true, description="Print usage information and exit.")
  private boolean help;

  @Parameter(names = "--fail-fast", description="Stop with a non-zero status code on the first exception. Useful if each document is expected to be successfully processed.")
  private boolean exitOnException = false;

  @Parameter(names = "--overwrite", description="Overwrite output file if it exists. If file exists and this option is false, returns a non-zero exit code.")
  private boolean overwrite = false;

  @Parameter(names = "--with-coref", description="If true (default), run coref.")
  private boolean enableCoref = true;

  @Parameter(names = "--tokenized-input", description="If true, assume input has already been tokenized.")
  private boolean isInputTokenized = false;

  @Parameter(names= { "--language", "-l" }, description="The language and models of the Stanford Annotator.")
  private String lang = "en";

  /**
   *
   */
  public Runner() {
    // TODO Auto-generated constructor stub
  }

  /**
   * @param args
   */
  public static void main(String... args) {
    Thread.setDefaultUncaughtExceptionHandler(new LoggedUncaughtExceptionHandler());

    Runner run = new Runner();
    JCommander jc = new JCommander(run, args);
    jc.setProgramName(Runner.class.getName());
    if (run.help) {
      jc.usage();
      System.exit(0);
    }

    int nDocsSeen = 0;
    int nDocsFailed = 0;
    List<String> exIds = new ArrayList<>();
    boolean haveSeenException = false;

    Path outF = Paths.get(run.outputPath);
    Path inp = Paths.get(run.inputPath);
    LOGGER.info("Input path: {}", inp.toString());
    LOGGER.info("Output folder: {}", outF.toString());

    try {
      new ExistingNonDirectoryFile(inp);
      if (!Files.exists(outF)) {
        LOGGER.info("Creating output directory.");
        Files.createDirectories(outF);
      }

      Path outFile = outF.resolve(run.outputName);
      if (Files.exists(outFile)) {
        if (run.overwrite)
          Files.delete(outFile);
        else {
          LOGGER.info("File exists and overwrite = false. Not continuing.");
          System.exit(1);
        }
      }

      PipelineLanguage lang = PipelineLanguage.getEnumeration(run.lang);
      Analytic<? extends WrappedCommunication> a;
        if (run.isInputTokenized)
          a = new AnnotateTokenizedConcrete(lang);
        else
          a = new AnnotateNonTokenizedConcrete(lang);

      StopWatch sw = new StopWatch();
      sw.start();
      LOGGER.info("Beginning ingest at: {}", new DateTime().toString());
      try (InputStream in = Files.newInputStream(inp);
          OutputStream os = Files.newOutputStream(outFile);
          GzipCompressorOutputStream gout = new GzipCompressorOutputStream(os);
          TarArchiver arch = new TarArchiver(gout);) {
        TarGzArchiveEntryCommunicationIterator iter = new TarGzArchiveEntryCommunicationIterator(in);
        while (iter.hasNext()) {
          Communication c = iter.next();
          nDocsSeen++;
          try {
            arch.addEntry(new ArchivableCommunication(a.annotate(c).getRoot()));
          } catch (AnalyticException e) {
            LOGGER.warn("Caught analytic exception on document: " + c.getId());
            nDocsFailed++;
            exIds.add(c.getId());
            haveSeenException = true;
            if (run.exitOnException)
              break;
          }
        }
      }

      if (run.exitOnException
          && haveSeenException)
        System.exit(1);

      sw.stop();
      LOGGER.info("Ingest completed at: {}", new DateTime().toString());
      Duration d = new Duration(sw.getTime());
      Period p = d.toPeriod();
      LOGGER.info("Ingest took {}d{}m{}s.", p.getDays(), p.getMinutes(), p.getSeconds());
      final int seenLessFailed = nDocsSeen - nDocsFailed;
      float ratio = nDocsSeen > 0 ? (float)seenLessFailed / nDocsSeen * 100 : 0;
      LOGGER.info("Converted {}% of documents successfully. [{} / {} total]",
          ratio, seenLessFailed, nDocsSeen);
      if (haveSeenException)
        exIds.forEach(eid -> LOGGER.info("Caught exception on document: {}", eid));
    } catch (IOException | NotFileException e) {
      throw new RuntimeException(e);
    }
  }
}
