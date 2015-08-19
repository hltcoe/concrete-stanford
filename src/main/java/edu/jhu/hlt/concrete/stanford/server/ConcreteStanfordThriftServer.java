/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford.server;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.analytics.base.SectionedCommunicationAnalytic;
import edu.jhu.hlt.concrete.analytics.base.TokenizationedCommunicationAnalytic;
import edu.jhu.hlt.concrete.metadata.tools.TooledMetadataConverter;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.services.Annotator;
import edu.jhu.hlt.concrete.services.ConcreteThriftException;
import edu.jhu.hlt.concrete.stanford.ConcreteStanfordPreCorefAnalytic;
import edu.jhu.hlt.concrete.stanford.ConcreteStanfordTokensSentenceAnalytic;
import edu.jhu.hlt.concrete.stanford.PipelineLanguage;

/**
 * Implementation of concrete-stanford as a service.
 */
public class ConcreteStanfordThriftServer implements Annotator.Iface {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteStanfordThriftServer.class);

  private final SectionedCommunicationAnalytic<TokenizedCommunication> tokensAnnotator;
  private final TokenizationedCommunicationAnalytic<TokenizedCommunication> upToCorefAnnotator;

  /**
   *
   */
  public ConcreteStanfordThriftServer(PipelineLanguage lang) {
    this.tokensAnnotator = new ConcreteStanfordTokensSentenceAnalytic(lang);
    this.upToCorefAnnotator = new ConcreteStanfordPreCorefAnalytic(lang);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.services.Annotator.Iface#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public Communication annotate(Communication original) throws ConcreteThriftException, TException {
    LOGGER.info("Received annotation request. Annotating: {} [UUID: {}]", original.getId(), original.getUuid().getUuidString());
    try {
      TokenizedCommunication intermed = this.tokensAnnotator.annotate(original);
      TokenizedCommunication full = this.upToCorefAnnotator.annotate(intermed);
      return full.getRoot();
    } catch (AnalyticException e) {
      throw new ConcreteThriftException(e.getMessage());
    }
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.services.Annotator.Iface#getMetadata()
   */
  @Override
  public AnnotationMetadata getMetadata() throws TException {
    return TooledMetadataConverter.convert(this.tokensAnnotator);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.services.Annotator.Iface#getDocumentation()
   */
  @Override
  public String getDocumentation() throws TException {
    return "Runs stanford tools on the input Communication.";
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.services.Annotator.Iface#shutdown()
   */
  @Override
  public void shutdown() throws TException {

  }
}
