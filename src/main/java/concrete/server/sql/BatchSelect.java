/*
 * Copyright 2012-2014 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package concrete.server.sql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 * @author max
 *
 */
public class BatchSelect {

  private static final Logger logger = LoggerFactory.getLogger(BatchSelect.class);

  /**
   *
   */
  public BatchSelect() {
    // TODO Auto-generated constructor stub
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 2) {
      logger.info("Usage: {} {}", "/path/to/csv/file/with/ids/in/col/1", "/path/to/out/folder");
      System.exit(1);
    }

    String pathStr = args[0];
    try {
      SQLCreds creds = new GigawordCreds();
      Path path = Paths.get(pathStr);
      Path outPath = Paths.get(args[1]);
      if (!Files.exists(path)) {
        logger.error("No file at: {}; exiting.", args[0]);
        System.exit(1);
      }

      if (!Files.exists(outPath))
        Files.createDirectory(outPath);

      List<String> idList = new ArrayList<>();
      try (Scanner sc = new Scanner(path, StandardCharsets.UTF_8.toString())) {
        while (sc.hasNextLine())
          idList.add(sc.nextLine().split(",")[0]); // first column
      } catch (IOException e1) {
        logger.error("Caught an IOException trying to read in the CSV.", e1);
        System.exit(1);
      }

      try (PostgresClient cli = new PostgresClient(creds)) {
        cli.batchSelect(idList, outPath);
      } catch (SQLException e) {
        logger.error("Caught a SQLException.", e);
      } catch (ConcreteException e) {
        logger.error("Caught a ConcreteException.", e);
      }
    } catch (UnsetEnvironmentVariableException e) {
      logger.error("Creds were not set.", e);
      System.exit(1);
    } catch (IOException e2) {
      logger.error("Caught an IOException trying to make the output directory.", e2);
      System.exit(1);
    }
  }
}
