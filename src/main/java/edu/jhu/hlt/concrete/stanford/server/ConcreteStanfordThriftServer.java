/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford.server;

import org.apache.thrift.TException;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.analytics.base.TokenizationedCommunicationAnalytic;
import edu.jhu.hlt.concrete.metadata.tools.TooledMetadataConverter;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.services.Annotator;
import edu.jhu.hlt.concrete.services.ConcreteThriftException;
import edu.jhu.hlt.concrete.stanford.ConcreteStanfordPreCorefAnalytic;
import edu.jhu.hlt.concrete.stanford.PipelineLanguage;

/**
 * Implementation of concrete-stanford as a service.
 */
public class ConcreteStanfordThriftServer implements Annotator.Iface {

  private final TokenizationedCommunicationAnalytic<TokenizedCommunication> annotator;

  /**
   *
   */
  public ConcreteStanfordThriftServer(PipelineLanguage lang) {
    this.annotator = new ConcreteStanfordPreCorefAnalytic(lang);
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.services.Annotator.Iface#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public Communication annotate(Communication original) throws ConcreteThriftException, TException {
    try {
      return this.annotator.annotate(original).getRoot();
    } catch (AnalyticException e) {
      throw new ConcreteThriftException(e.getMessage());
    }
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.services.Annotator.Iface#getMetadata()
   */
  @Override
  public AnnotationMetadata getMetadata() throws TException {
    return TooledMetadataConverter.convert(this.annotator);
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
