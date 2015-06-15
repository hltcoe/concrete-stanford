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
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.joda.time.Duration;
import org.joda.time.Minutes;
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

  public void run(Path inPath, Path outPath, Analytic<? extends WrappedCommunication> analytic) {
    LOGGER.debug("Checking input and output directories.");
    try {
      prepareInputOutput(inPath, outPath);
    } catch (IOException e) {
      LOGGER.error("Caught IOException when checking input and output directories.", e);
      System.exit(1);
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
      boolean isTarGzExt = lowerOutPathStr.endsWith(".tar.gz");
      boolean isConcreteExt = lowerOutPathStr.endsWith(".concrete") || lowerOutPathStr.endsWith(".comm");

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
          // SectionedCommunicationAnalytic<StanfordPostNERCommunication> pipe = new AnnotateNonTokenizedConcrete();
          WrappedCommunication annotated = analytic.annotate(c);
          Communication ar = annotated.getRoot();
          String fileName = ar.getId() + ".concrete";
          Path concreteOutPath = outPath.resolve(fileName);
          new WritableCommunication(ar).writeToFile(concreteOutPath, true);
        } catch (Exception e) {
          LOGGER.error("Caught exception when closing the input stream.", e);
        }
      } else {
        int nElementsInitPath = inPath.getNameCount();
        Path inputFileName = inPath.getName(nElementsInitPath - 1);
        String noExtStr = inputFileName.toString().split("\\.")[0];
        String fileName = noExtStr + ".tar";
        Path localOutPath = outPath.resolve(fileName);
        // Iterate over the archive.
        AutoCloseableIterator<byte[]> iter;
        try (InputStream is = Files.newInputStream(inPath);
            BufferedInputStream bis = new BufferedInputStream(is, 1024 * 8 * 24);
            OutputStream os = Files.newOutputStream(localOutPath);
            BufferedOutputStream bos = new BufferedOutputStream(os, 1024 * 8 * 24);
            TarArchiver archiver = new TarArchiver(bos);) {
          // If .tar - read from .tar.
          if (isTarExt)
            iter = new TarArchiveEntryByteIterator(bis);
          // If .tar.gz - read from .tar.gz.
          else
            iter = new TarGzArchiveEntryByteIterator(bis);

          final StopWatch sw = new StopWatch();
          sw.start();

          int docCtr = 0;
          if (iter.hasNext()) {
            LOGGER.info("Iterating over archive: {}", inPath.toString());

            Communication comm = ser.fromBytes(iter.next());
            WrappedCommunication annot = analytic.annotate(comm);
            archiver.addEntry(new ArchivableCommunication(annot.getRoot()));
            docCtr++;
          } else {
            LOGGER.info("Iterating over archive: {}", inPath.toString());
            LOGGER.warn("Archive {} is empty", inPath.toString());
          }

          while (iter.hasNext()) {
            Communication n = ser.fromBytes(iter.next());
            LOGGER.info("Annotating communication: {}", n.getId());
            WrappedCommunication a = analytic.annotate(n);
            archiver.addEntry(new ArchivableCommunication(a.getRoot()));
            docCtr++;
          }

          try {
            iter.close();
          } catch (Exception e) {
            // unlikely.
            LOGGER.error("Caught exception closing iterator.", e);
          }

          sw.stop();
          Minutes m = new Duration(sw.getTime()).toStandardMinutes();
          int minutesInt = m.getMinutes();

          LOGGER.info("Complete.");
          LOGGER.info("Runtime: approximately {} minutes.", minutesInt);
          LOGGER.info("Processed {} documents.", docCtr);
          if (docCtr > 0 && minutesInt > 0) {
            float perMin = (float) docCtr / (float) minutesInt;
            LOGGER.info("Processed approximately {} documents/minute.", perMin);
          }
        }
      }
    } catch (IOException | ConcreteException | AnalyticException e) {
      LOGGER.error("Caught exception while running the analytic over archive.", e);
    }
  }

  public static void prepareInputOutput(Path in, Path out) throws IOException {
    if (!Files.exists(in))
      throw new IOException(in.toString() + " does not exist. Ensure it exists and re-run this program.");

    if (!Files.exists(out)) {
      LOGGER.debug("Attempting to create output directory.");
      Files.createDirectories(out);
    }
  }
}
