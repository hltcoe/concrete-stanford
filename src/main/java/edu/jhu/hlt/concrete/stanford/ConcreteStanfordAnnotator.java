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
import java.nio.file.Paths;

import org.apache.commons.lang3.time.StopWatch;
import org.joda.time.Duration;
import org.joda.time.Minutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.acute.archivers.tar.TarArchiver;
import edu.jhu.hlt.acute.iterators.tar.TarArchiveEntryByteIterator;
import edu.jhu.hlt.acute.iterators.tar.TarGzArchiveEntryByteIterator;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.analytics.base.SectionedCommunicationAnalytic;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.serialization.CommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.CompactCommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.archiver.ArchivableCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.utilt.AutoCloseableIterator;
import edu.jhu.hlt.utilt.sys.SystemErrDisabler;

/**
 *
 */
public class ConcreteStanfordAnnotator {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(ConcreteStanfordAnnotator.class);

  private ConcreteStanfordAnnotator() {
  }

  public static void main(String[] args) {
    if (args.length != 2) {
      LOGGER.info("This program takes 2 arguments.");
      LOGGER
          .info("The first is a path to a .concrete file, .tar file containing .concrete files, or "
              + "a .tar.gz with .concrete files.");
      LOGGER.info("Each .concrete file must contain section segmentations.");
      LOGGER
          .info("The second argument is a path to the output file, matching the input file "
              + "(if arg 1 == .concrete file, then output == .concrete file, etc.)");
      LOGGER.info("Example usage: ");
      LOGGER.info(ConcreteStanfordAnnotator.class.getName() + " "
          + "/path/to/.concrete/file /path/to/output/file");
      System.exit(1);
    }

    String initPathStr = args[0];
    Path initPath = Paths.get(initPathStr);
    if (!Files.exists(initPath)) {
      LOGGER.error(
          "Path {} does not exist. Ensure it exists and re-run this program.",
          initPathStr);
      System.exit(1);
    }

    String outPathStr = args[1];
    Path outPath = Paths.get(outPathStr);
    if (!Files.exists(outPath)) {
      try {
        LOGGER.debug("Attempting to create output directory.");
        Files.createDirectories(outPath);
      } catch (IOException ioe) {
        LOGGER
            .error("Caught IOException while creating output directory.", ioe);
        System.exit(1);
      }
    }

    String lowerOutPathStr = initPathStr.toLowerCase();
    CommunicationSerializer ser = new CompactCommunicationSerializer();
    try {
      SystemErrDisabler sed = new SystemErrDisabler();
      sed.disable();

      // Outcomes of outPathStr ending:
      // No valid ending (program exit)
      // Ends with .concrete (first if)
      // Ends with .tar (else, first if)
      // Ends with .tar.gz (else, second if)
      boolean isTarExt = lowerOutPathStr.endsWith(".tar");
      boolean isTarGzExt = lowerOutPathStr.endsWith(".tar.gz");
      boolean isConcreteExt = lowerOutPathStr.endsWith(".concrete");

      // If no extention matches, exit.
      if (!isTarExt && !isTarGzExt && !isConcreteExt) {
        LOGGER
            .error("Input file extension was not '.concrete', '.tar', or '.tar.gz'; exiting.");
        System.exit(1);
      } else if (isConcreteExt) {
        // IF .concrete, run single communication.
        LOGGER.info("Annotating single .concrete file at: {}",
            initPath.toString());
        byte[] inputBytes = Files.readAllBytes(initPath);

        Communication c = ser.fromBytes(inputBytes);
        SectionedCommunicationAnalytic pipe = new AnnotateNonTokenizedConcrete();
        Communication annotated = pipe.annotate(c);
        String fileName = annotated.getId() + ".concrete";
        Path concreteOutPath = outPath.resolve(fileName);
        new SuperCommunication(annotated).writeToFile(concreteOutPath, true);
      } else {
        int nElementsInitPath = initPath.getNameCount();
        Path inputFileName = initPath.getName(nElementsInitPath - 1);
        // LOGGER.info("Input FN: {}", inputFileName.toString());
        String noExtStr = inputFileName.toString().split("\\.")[0];
        String fileName = noExtStr + ".tar";
        Path localOutPath = outPath.resolve(fileName);
        // Iterate over the archive.
        AutoCloseableIterator<byte[]> iter;
        try (InputStream is = Files.newInputStream(initPath);
            BufferedInputStream bis = new BufferedInputStream(is);
            OutputStream os = Files.newOutputStream(localOutPath);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            TarArchiver archiver = new TarArchiver(bos);) {
          // If .tar - read from .tar.
          if (isTarExt)
            iter = new TarArchiveEntryByteIterator(bis);
          // If .tar.gz - read from .tar.gz.
          else
            iter = new TarGzArchiveEntryByteIterator(bis);

          SectionedCommunicationAnalytic pipe = null;
          StopWatch sw = null;
          int docCtr = 0;
          if (iter.hasNext()) {
            Communication comm = ser.fromBytes(iter.next());
            pipe = new AnnotateNonTokenizedConcrete();
            LOGGER.info("Iterating over archive: {}", initPath.toString());
            sw = new StopWatch();
            sw.start();
            Communication annot = pipe.annotate(comm);
            archiver.addEntry(new ArchivableCommunication(annot));
            docCtr++;
          } else {
            LOGGER.info("Iterating over archive: {}", initPath.toString());
            LOGGER.warn("Archive {} is empty", initPath.toString());
            sw = new StopWatch();
            sw.start();
          }
          while (iter.hasNext()) {
            Communication n = ser.fromBytes(iter.next());
            LOGGER.info("Annotating communication: {}", n.getId());
            Communication a = pipe.annotate(n);
            archiver.addEntry(new ArchivableCommunication(a));
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
      LOGGER.error(
          "Caught exception while running StanfordAgigaPipe over archive.", e);
    }
  }
}
