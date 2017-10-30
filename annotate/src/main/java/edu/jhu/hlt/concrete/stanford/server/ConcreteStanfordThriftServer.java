/*
 * Copyright 2012-2017 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford.server;

import java.util.List;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.Analytic;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.annotate.AnnotateCommunicationService;
import edu.jhu.hlt.concrete.metadata.AnnotationMetadataFactory;
import edu.jhu.hlt.concrete.miscommunication.WrappedCommunication;
import edu.jhu.hlt.concrete.services.ConcreteThriftException;

/**
 * Implementation of concrete-stanford as a service.
 */
public class ConcreteStanfordThriftServer implements AnnotateCommunicationService.Iface {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteStanfordThriftServer.class);

  private final List<Analytic<? extends WrappedCommunication>> analytics;
  private final Analytic<? extends WrappedCommunication> first;
  private final List<Analytic<? extends WrappedCommunication>> rest;

  /**
   * package ctor is guaranteed to get >0 analytics
   */
  ConcreteStanfordThriftServer(List<Analytic<? extends WrappedCommunication>> analytics) {
    this.analytics = analytics;
    final int aSize = this.analytics.size();
    if (aSize == 0)
      throw new IllegalArgumentException("need >0 analytics");
    this.first = analytics.get(0);
    if (aSize > 1)
      this.rest = this.analytics.subList(1, aSize);
    else
      this.rest = ImmutableList.of();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.annotate.AnnotateCommunicationService.Iface#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public Communication annotate(Communication original) throws ConcreteThriftException, TException {
    LOGGER.info("Received annotation request. Annotating: {} [UUID: {}]", original.getId(), original.getUuid().getUuidString());
    try {
      WrappedCommunication intermed = this.first.annotate(original);
      for (Analytic<? extends WrappedCommunication> a : this.rest) {
        intermed = a.annotate(intermed.getRoot());
      }
      return intermed.getRoot();
    } catch (AnalyticException e) {
      throw new ConcreteThriftException(e.getMessage());
    }
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.annotate.AnnotateCommunicationService.Iface#getMetadata()
   */
  @Override
  public AnnotationMetadata getMetadata() throws TException {
    return AnnotationMetadataFactory.fromCurrentLocalTime("concrete-stanford");
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.annotate.AnnotateCommunicationService.Iface#getDocumentation()
   */
  @Override
  public String getDocumentation() throws TException {
    return "Runs stanford tools on the input Communication.";
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.annotate.AnnotateCommunicationService.Iface#shutdown()
   */
  @Override
  public void shutdown() throws TException {
    LOGGER.info("Shutdown request received");
  }
}
