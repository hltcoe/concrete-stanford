/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import com.nytlabs.corpus.NYTCorpusDocumentParser;

import edu.jhu.hlt.annotatednyt.AnnotatedNYTDocument;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.ingesters.annotatednyt.CommunicationizableAnnotatedNYTDocument;
import edu.jhu.hlt.concrete.miscommunication.sectioned.CachedSectionedCommunication;

/**
 *
 */
public class AnnotatedNYTTest {

  Path p = Paths.get("src/test/resources/hopkins-stanford-a-la-nyt.xml");
  NYTCorpusDocumentParser parser = new NYTCorpusDocumentParser();

  Communication nytComm;

  @Before
  public void setUp() throws Exception {
    try(InputStream is = Files.newInputStream(p);
        BufferedInputStream bin = new BufferedInputStream(is, 1024 * 8 * 16);) {
      byte[] nytdocbytes = IOUtils.toByteArray(bin);
      this.nytComm = new CommunicationizableAnnotatedNYTDocument(new AnnotatedNYTDocument(parser.fromByteArray(nytdocbytes, false))).toCommunication();
    }
  }

  public Communication annotate (Communication orig) throws AnalyticException {
    Communication cp = new Communication(orig);
    final String commTxt = cp.getText();

    return cp;
  }

  @Test
  public void test() throws Exception {
    CachedSectionedCommunication csc = new CachedSectionedCommunication(this.nytComm);
    List<Section> sectList = csc.getSections();
  }
}
