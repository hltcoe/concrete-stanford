package edu.jhu.hlt.concrete.stanford.runners;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.collect.ImmutableList;

import edu.jhu.hlt.acute.archivers.tar.TarArchiver;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.Analytic;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.ingesters.base.IngesterParameterDelegate;
import edu.jhu.hlt.concrete.miscommunication.WrappedCommunication;
import edu.jhu.hlt.concrete.serialization.archiver.ArchivableCommunication;
import edu.jhu.hlt.concrete.serialization.iterators.TarGzArchiveEntryCommunicationIterator;
import edu.jhu.hlt.concrete.stanford.languages.PipelineLanguage;

public class StanfordOpts {

  private static final Logger LOGGER = LoggerFactory.getLogger(StanfordOpts.class);

  @ParametersDelegate
  private StanfordParameterDelegate stanfordParams = new StanfordParameterDelegate();

  @ParametersDelegate
  private IngesterParameterDelegate ingesterParams = new IngesterParameterDelegate();

  @Parameter(description = "/path/to/1.tar.gz /path/to/2.tar.gz ...",
      required = true)
  private List<String> paths;

  public void handleStdError() throws UnsupportedEncodingException {
    this.stanfordParams.handleStdErr();
  }

  public boolean noPaths() {
    return this.paths.isEmpty();
  }

  public boolean help() {
    return this.ingesterParams.help;
  }

  public List<Path> validatePaths() throws IOException {
    ImmutableList.Builder<Path> pb = ImmutableList.builder();
    for (String pathStr : this.paths) {
      Path p = Paths.get(pathStr);
      if (!Files.exists(p))
        throw new IOException("No file at: " + p.toString());
      pb.add(p);
    }
    return pb.build();
  }

  /**
   *
   * @param lang
   *
   * @throws IOException
   */
  public void pipeline(PipelineLanguage lang) throws IOException {
    if (this.noPaths())
      throw new IOException("No paths specified");
    List<Analytic<? extends WrappedCommunication>> analytics =
        this.stanfordParams.getAnalytics(lang);
    // pull out the first analytic to get into the interface
    // e.g. Comm -> WrappedComm
    Analytic<? extends WrappedCommunication> first = analytics.get(0);
    List<Analytic<? extends WrappedCommunication>> rest;
    if (analytics.size() > 1)
      rest = analytics.subList(1, analytics.size());
    else
      rest = ImmutableList.of();
    this.ingesterParams.prepare();
    LOGGER.info("Ingest beginning");
    StopWatch sw = new StopWatch();
    sw.start();
    try (TarArchiver arch = this.ingesterParams.getArchiver();) {
      for (Path p : this.validatePaths()) {
        try (InputStream in = Files.newInputStream(p);
            BufferedInputStream bin = new BufferedInputStream(in);
            TarGzArchiveEntryCommunicationIterator iter = new TarGzArchiveEntryCommunicationIterator(bin);) {
          while (iter.hasNext()) {
            Communication c = iter.next();
            try {
              WrappedCommunication wc = first.annotate(c);
              // chain add'l analytics
              for (Analytic<? extends WrappedCommunication> a : rest) {
                wc = a.annotate(wc.getRoot());
              }
              // write output
              arch.addEntry(new ArchivableCommunication(wc.getRoot()));
            } catch (AnalyticException ae) {
              LOGGER.warn("Caught exception on document", ae);
              if (this.stanfordParams.exitOnException)
                throw new IOException("Processing failure and fail-fast enabled");
            }
          }
        }
      }
    }

    sw.stop();
    LOGGER.info("Ingest completed");
    Duration d = new Duration(sw.getTime());
    LOGGER.info("Ingest duration: {}", d.toString());
  }
}
