/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.tools.AnnotationException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.serialization.CommunicationTarGzSerializer;
import edu.jhu.hlt.concrete.serialization.TarGzCompactCommunicationSerializer;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 *
 */
public class ConcreteStanfordAnnotator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteStanfordAnnotator.class);

  private ConcreteStanfordAnnotator() {

  }

  public static void main(String[] args) {
    if (args.length != 2) {
      LOGGER.info("This program takes 2 arguments.");
      LOGGER.info("The first is a path to a .concrete file, .tar file containing .concrete files, or "
          + "a .tar.gz with .concrete files.");
      LOGGER.info("Each .concrete file must contain section segmentations.");
      LOGGER.info("The second argument is a path to the output file, matching the input file "
          + "(if arg 1 == .concrete file, then output == .concrete file, etc.)");
      LOGGER.info("Example usage: ");
      LOGGER.info(ConcreteStanfordAnnotator.class.getName() + " " + "/path/to/.concrete/file /path/to/output/file");
      System.exit(1);
    }

    String initPathStr = args[0];
    Path initPath = Paths.get(initPathStr);
    if (!Files.exists(initPath)) {
      LOGGER.error("Path {} does not exist. Ensure it exists and re-run this program.", initPathStr);
      System.exit(1);
    }

    String outPathStr = args[1];
    Path outPath = Paths.get(outPathStr);
    if (!Files.exists(outPath)) {
      try {
        LOGGER.debug("Attempting to create output directory.");
        Files.createDirectories(outPath);
      } catch (IOException ioe) {
        LOGGER.error("Caught IOException while creating output directory.", ioe);
        System.exit(1);
      }
    }

    StanfordAgigaPipe pipe = null;
    try {
      pipe = new StanfordAgigaPipe();
    } catch (IOException e1) {
      LOGGER.error("Caught IOException while initializing StanfordAgigaPipe.", e1);
      System.exit(1);
    }

    CommunicationTarGzSerializer ser = new TarGzCompactCommunicationSerializer();
    String lowerOutPathStr = outPathStr.toLowerCase();
    try {

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
        LOGGER.error("Input file extension was not '.concrete', '.tar', or '.tar.gz'; exiting.");
        System.exit(1);
      } else if (isConcreteExt) {
        // IF .concrete, run single communication.
        LOGGER.info("Annotating single .concrete file.");
        byte[] inputBytes = Files.readAllBytes(initPath);
        Communication c = ser.fromBytes(inputBytes);
        Communication annotated = pipe.process(c);
        new SuperCommunication(annotated).writeToFile(outPath, true);
      } else {
        // Iterate over the archive.
        Iterator<Communication> iter;
        try (InputStream is = Files.newInputStream(initPath);) {
          // If .tar - read from .tar.
          if (isTarExt)
            iter = ser.fromTar(is);
          // If .tar.gz - read from .tar.gz.
          else
            iter = ser.fromTarGz(is);

          Set<Communication> commSet = new HashSet<Communication>();
          while (iter.hasNext()); {
            Communication n = iter.next();
            LOGGER.info("Annotating communication: {}", n.getId());
            Communication a = pipe.process(n);
            commSet.add(a);
          }
          ser.toTar(commSet, outPath);
        }
      }
    } catch (IOException | ConcreteException | AnnotationException e) {
      LOGGER.error("Caught exception while running StanfordAgigaPipe over archive.", e);
    }
  }
}
