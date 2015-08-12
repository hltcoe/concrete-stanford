/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.joda.time.Duration;
import org.joda.time.Minutes;
import org.joda.time.Seconds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.acute.archivers.tar.TarArchiver;
import edu.jhu.hlt.acute.iterators.tar.TarArchiveEntryByteIterator;
import edu.jhu.hlt.acute.iterators.tar.TarGzArchiveEntryByteIterator;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.Analytic;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.communications.WritableCommunication;
import edu.jhu.hlt.concrete.miscommunication.WrappedCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.serialization.CommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.CompactCommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.archiver.ArchivableCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.utilt.AutoCloseableIterator;
import edu.jhu.hlt.utilt.sys.SystemErrDisabler;

/**
 * Utility class to help with running Concrete Stanford analytics.
 */
public class ConcreteStanfordRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteStanfordRunner.class);

  private final CommunicationSerializer ser = new CompactCommunicationSerializer();
  private final SystemErrDisabler sed = new SystemErrDisabler();

  /**
   *
   */
  public ConcreteStanfordRunner() {

  }

  public void run(Path inPath, Path outPath, Analytic<? extends TokenizedCommunication> analytic) {
    LOGGER.debug("Checking input and output directories.");
    try {
      prepareInputOutput(inPath, outPath);
    } catch (IOException e) {
      LOGGER.error("Caught IOException when checking input and output directories.", e);
    }

    String lowerOutPathStr = inPath.toString().toLowerCase();
    try {
      sed.disable();

      // Outcomes of outPathStr ending:
      // No valid ending (program exit)
      // Ends with .concrete (first if)
      // Ends with .tar (else, first if)
      // Ends with .tar.gz (else, second if)

      boolean isTarExt = lowerOutPathStr.endsWith(".tar");
      boolean isTarGzExt = lowerOutPathStr.endsWith(".tar.gz") || lowerOutPathStr.endsWith(".tgz");
      boolean isConcreteExt = lowerOutPathStr.endsWith(".concrete") || lowerOutPathStr.endsWith(".comm");

      int nElementsInitPath = inPath.getNameCount();
      Path inputFileName = inPath.getName(nElementsInitPath - 1);

      // If no extention matches, exit.
      if (!isTarExt && !isTarGzExt && !isConcreteExt) {
        LOGGER.error("Input file extension was not '.concrete', '.comm', '.tar', or '.tar.gz'; exiting.");
        System.exit(1);
      } else if (isConcreteExt) {
        // IF .concrete, run single communication.
        LOGGER.info("Annotating single .concrete file at: {}", inPath.toString());
        try (InputStream in = Files.newInputStream(inPath);
            BufferedInputStream bin = new BufferedInputStream(in, 1024 * 8 * 24);) {
          byte[] inputBytes = IOUtils.toByteArray(bin);
          Communication c = ser.fromBytes(inputBytes);
          WrappedCommunication annotated = analytic.annotate(c);
          Communication ar = annotated.getRoot();
          WritableCommunication wc = new WritableCommunication(ar);
          if (Files.isDirectory(outPath))
            wc.writeToFile(outPath.resolve(inputFileName), true);
          else
            wc.writeToFile(outPath, true);
        } catch (AnalyticException e) {
          LOGGER.error("Caught exception when running the analytic.", e);
        }
      } else {

        Path localOutPath;
        if (Files.isDirectory(outPath))
          // if directory, use same extension as input.
          localOutPath = outPath.resolve(inputFileName);
        else
          localOutPath = outPath;

        // Iterate over the archive.
        AutoCloseableIterator<byte[]> iter;
        try (InputStream is = Files.newInputStream(inPath);
            BufferedInputStream bis = new BufferedInputStream(is, 1024 * 8 * 24);) {

          // open iterator based on file extension
          iter = isTarExt ? new TarArchiveEntryByteIterator(bis) : new TarGzArchiveEntryByteIterator(bis);
          try (OutputStream os = Files.newOutputStream(localOutPath);
              BufferedOutputStream bos = new BufferedOutputStream(os, 1024 * 8 * 24);) {
            TarArchiver archiver = isTarExt ? new TarArchiver(bos) : new TarArchiver(new GzipCompressorOutputStream(bos));

            final StopWatch sw = new StopWatch();
            sw.start();

            int docCtr = 0;
            final AtomicInteger tokenCtr = new AtomicInteger(0);
            LOGGER.info("Iterating over archive: {}", inPath.toString());
            while (iter.hasNext()) {
              Communication n = ser.fromBytes(iter.next());
              LOGGER.info("Annotating communication: {}", n.getId());
              try {
                TokenizedCommunication a = analytic.annotate(n);
                a.getTokenizations().parallelStream()
                    .map(tkzToInt -> tkzToInt.getTokenList().getTokenListSize())
                    .forEach(ct -> tokenCtr.addAndGet(ct));
                archiver.addEntry(new ArchivableCommunication(a.getRoot()));
                docCtr++;
              } catch (AnalyticException | IOException | StringIndexOutOfBoundsException e) {
                LOGGER.error("Caught exception processing document: " + n.getId(), e);
              }
            }

            try {
              archiver.close();
              iter.close();
            } catch (Exception e) {
              // unlikely.
              LOGGER.info("Caught exception closing iterator.", e);
            }

            sw.stop();
            Duration rt = new Duration(sw.getTime());
            Seconds st = rt.toStandardSeconds();
            Minutes m = rt.toStandardMinutes();
            int minutesInt = m.getMinutes();

            LOGGER.info("Complete.");
            LOGGER.info("Runtime: approximately {} minutes.", minutesInt);
            LOGGER.info("Processed {} documents.", docCtr);
            final int tokens = tokenCtr.get();
            LOGGER.info("Processed {} tokens.", tokens);
            if (docCtr > 0 && minutesInt > 0) {
              final float minutesFloat = minutesInt;
              float perMin = docCtr / minutesFloat;
              LOGGER.info("Processed approximately {} documents/minute.", perMin);
              LOGGER.info("Processed approximately {} tokens/second.", st.getSeconds() / minutesFloat);
            }
          }
        }
      }
    } catch (IOException | ConcreteException e) {
      LOGGER.error("Caught exception while running the analytic over archive.", e);
    }
  }

  public static void prepareInputOutput(Path in, Path out) throws IOException {
    if (!Files.exists(in))
      throw new IOException(in.toString() + " does not exist. Ensure it exists and re-run this program.");

    Optional<Path> outPath = Optional.ofNullable(out.getParent());
    outPath.ifPresent(p -> {
      if (!Files.exists(p)) {
        LOGGER.debug("Attempting to create output directory: {}", outPath.toString());
        try {
          Files.createDirectories(p);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    });
  }
}
