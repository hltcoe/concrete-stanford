/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.List;

import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenTagging;

/**
 * Lotta work for a 4-tuple:
 * {@code List<Token>, TokenTagging, TokenTagging, TokenTagging}.
 * <br>
 * <br>
 * Believe it or not, makes things a little cleaner.
 */
class StanfordToConcreteConversionOutput {
  private final List<Token> tokenList;
  private final TokenTagging nerTT;
  private final TokenTagging posTT;
  private final TokenTagging lemmaTT;

  /**
   *
   */
  public StanfordToConcreteConversionOutput(final List<Token> tokenList,
      final TokenTagging nerTT, final TokenTagging posTT, final TokenTagging lemmaTT) {
    this.tokenList = tokenList;
    this.nerTT = nerTT;
    this.posTT = posTT;
    this.lemmaTT = lemmaTT;
  }

  /**
   * @return the tokenList
   */
  public List<Token> getTokenList() {
    return tokenList;
  }

  /**
   * @return the nerTT
   */
  public TokenTagging getNerTT() {
    return nerTT;
  }

  /**
   * @return the posTT
   */
  public TokenTagging getPosTT() {
    return posTT;
  }

  /**
   * @return the lemmaTT
   */
  public TokenTagging getLemmaTT() {
    return lemmaTT;
  }
}