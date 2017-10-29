/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.analytics.base.Analytic;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.util.Timing;
import edu.jhu.hlt.tift.Tokenizer;

/**
 * TODO move this
 */
public class TiftTokenizerAnalytic implements Analytic<TokenizedCommunication> {

  /**
   *
   */
  public TiftTokenizerAnalytic() {
    // TODO Auto-generated constructor stub
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.safe.metadata.SafeAnnotationMetadata#getTimestamp()
   */
  @Override
  public long getTimestamp() {
    return Timing.currentLocalTime();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolName()
   */
  @Override
  public String getToolName() {
    return TiftTokenizerAnalytic.class.getSimpleName();
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.metadata.tools.MetadataTool#getToolVersion()
   */
  @Override
  public String getToolVersion() {
    return ProjectConstants.VERSION;
  }

  /* (non-Javadoc)
   * @see edu.jhu.hlt.concrete.analytics.base.Analytic#annotate(edu.jhu.hlt.concrete.Communication)
   */
  @Override
  public TokenizedCommunication annotate(Communication c) throws AnalyticException {
    Communication cpy = new Communication(c);
    Tokenization tkz = Tokenizer.WHITESPACE.tokenizeToConcrete(c.getText(), 0);
    cpy.getSectionListIterator().next().getSentenceListIterator().next().setTokenization(tkz);
    try {
      return new CachedTokenizationCommunication(cpy);
    } catch (MiscommunicationException e) {
      throw new AnalyticException(e);
    }
  }
}
