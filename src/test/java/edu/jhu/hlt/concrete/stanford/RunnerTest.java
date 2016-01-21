/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.jhu.hlt.acute.archivers.tar.TarArchiver;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.analytics.base.Analytic;
import edu.jhu.hlt.concrete.communications.WritableCommunication;
import edu.jhu.hlt.concrete.metadata.AnnotationMetadataFactory;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.section.SingleSectionSegmenter;
import edu.jhu.hlt.concrete.serialization.archiver.ArchivableCommunication;
import edu.jhu.hlt.concrete.serialization.iterators.TarArchiveEntryCommunicationIterator;
import edu.jhu.hlt.concrete.serialization.iterators.TarGzArchiveEntryCommunicationIterator;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator;

/**
 *
 */
public class RunnerTest {

  ConcreteStanfordRunner runner;
  Communication sc;
  String uuidStr;
  Analytic<? extends TokenizedCommunication> tift;
  Path inp;
  Path inpTar;
  Path inpTgz;
  Path outp;
  Path outpComm;
  Path outpTar;
  Path outpTarGz;

  @Rule
  public TemporaryFolder tf = new TemporaryFolder();

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    this.runner = new ConcreteStanfordRunner();
    this.tift = new TiftTokenizerAnalytic();
    this.sc = new Communication();
    AnalyticUUIDGeneratorFactory f = new AnalyticUUIDGeneratorFactory();
    AnalyticUUIDGenerator g = f.create();
    this.sc.setId("test");
    this.sc.setUuid(g.next());
    this.sc.setText("This is some sample text.");
    Section origs = SingleSectionSegmenter.createSingleSection(this.sc, "passage");
    this.sc.addToSectionList(origs);
    this.sc.setType("doc");
    this.uuidStr = this.sc.getUuid().getUuidString();
    AnnotationMetadata am = AnnotationMetadataFactory.fromCurrentLocalTime().setTool("argh");
    this.sc.setMetadata(am);
    Sentence st = new Sentence();
    st.setUuid(g.next());
    Section ptr = this.sc.getSectionListIterator().next();
    st.setTextSpan(ptr.getTextSpan());
    ptr.addToSentenceList(st);

    this.inp = this.tf.newFile("comm.comm").toPath();
    new WritableCommunication(sc).writeToFile(inp, true);
    this.outp = this.tf.newFolder("output").toPath();
    this.outpComm = this.outp.resolve("out.comm");
    this.outpTar = this.outp.resolve("out.tar");
    this.outpTarGz = this.outp.resolve("out.tar.gz");

    this.inpTar = this.tf.newFile("comms.tar").toPath();
    try (OutputStream os = Files.newOutputStream(this.inpTar);
        BufferedOutputStream bos = new BufferedOutputStream(os, 1024 * 8 * 24);
        TarArchiver archiver = new TarArchiver(bos);) {
      archiver.addEntry(new ArchivableCommunication(sc));
    }

    this.inpTgz = this.tf.newFile("comms.tar.gz").toPath();
    try (OutputStream os = Files.newOutputStream(this.inpTgz);
        BufferedOutputStream bos = new BufferedOutputStream(os, 1024 * 8 * 24);
        GzipCompressorOutputStream gzo = new GzipCompressorOutputStream(bos);
        TarArchiver archiver = new TarArchiver(gzo);) {
      archiver.addEntry(new ArchivableCommunication(sc));
    }
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.ConcreteStanfordRunner#run(java.nio.file.Path, java.nio.file.Path, edu.jhu.hlt.concrete.analytics.base.Analytic)}.
   */
  @Test
  public void commToComm() throws Exception {
    this.runner.run(inp, outpComm, tift);
    assertTrue(Files.exists(outpComm));
  }

  @Test
  public void commToDir() throws Exception {
    this.runner.run(inp, outp, tift);
    assertTrue(Files.exists(outp.resolve("comm.comm")));
  }

  @Test
  public void tarToTar() throws Exception {
    this.runner.run(inpTar, this.outpTar, tift);
    assertTrue(Files.exists(outpTar));
    this.testArchiveEntry(this.outpTar);
  }

  private void testArchiveEntry(Path inputPath) throws Exception {
    try (InputStream is = Files.newInputStream(inputPath);
        BufferedInputStream bis = new BufferedInputStream(is);
        TarArchiveEntryCommunicationIterator iter = new TarArchiveEntryCommunicationIterator(bis);) {
      Communication fromTar = iter.next();
      assertEquals(this.uuidStr, fromTar.getUuid().getUuidString());
    }
  }

  private void testArchiveGzEntry(Path inputPath) throws Exception {
    try (InputStream is = Files.newInputStream(inputPath);
        BufferedInputStream bis = new BufferedInputStream(is);) {
      TarGzArchiveEntryCommunicationIterator iter = new TarGzArchiveEntryCommunicationIterator(bis);
      Communication fromTar = iter.next();
      assertEquals(this.uuidStr, fromTar.getUuid().getUuidString());
    }
  }

  @Test
  public void tarToDir() throws Exception {
    this.runner.run(inpTar, outp, tift);
    Path output = outp.resolve("comms.tar");
    assertTrue(Files.exists(output));
    this.testArchiveEntry(output);
  }

  @Test
  public void tgzToTgz() throws Exception {
    this.runner.run(inpTgz, outpTarGz, tift);
    assertTrue(Files.exists(outpTarGz));
    this.testArchiveGzEntry(this.outpTarGz);
  }

  @Test
  public void tgzToDir() throws Exception {
    this.runner.run(inpTgz, outp, tift);
    Path output = outp.resolve("comms.tar.gz");
    assertTrue(Files.exists(output));
    this.testArchiveGzEntry(output);
  }

  /**
   * Test method for {@link edu.jhu.hlt.concrete.stanford.ConcreteStanfordRunner#prepareInputOutput(java.nio.file.Path, java.nio.file.Path)}.
   */
  @Test(expected=IOException.class)
  public void exOnNoInput() throws Exception {
    ConcreteStanfordRunner.prepareInputOutput(Paths.get("/u3fo2uto235o23h5o235"), Paths.get("/452u52u5iuh25i3h5"));
  }
}
