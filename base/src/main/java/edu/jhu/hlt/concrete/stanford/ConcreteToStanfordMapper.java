/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.Tokenization;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentenceIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

/**
 *
 */
public class ConcreteToStanfordMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteToStanfordMapper.class);

  private static final CoreLabelTokenFactory factory = new CoreLabelTokenFactory();

  /**
   *
   */
  private ConcreteToStanfordMapper() {

  }

  public static List<CoreMap> concreteSectionToCoreMapList(final Section sect, final String commText) {
    List<CoreMap> toRet = new ArrayList<>();
    List<Sentence> sentList = sect.getSentenceList();
    int tokOffset = 0;
    for (int i = 0; i < sentList.size(); i++) {
      Sentence st = sentList.get(i);
      CoreMap cm = new ArrayCoreMap();
      cm.set(SentenceIndexAnnotation.class, i);
      final TextSpan sts = st.getTextSpan();
      final int sentCharStart = sts.getStart();
      final int sentCharEnd = sts.getEnding();
      LOGGER.debug("Setting stanford sentence BeginChar = {}", sentCharStart);
      cm.set(CharacterOffsetBeginAnnotation.class, sentCharStart);
      LOGGER.debug("Setting stanford sentence EndChar = {}", sentCharEnd);
      cm.set(CharacterOffsetEndAnnotation.class, sentCharEnd);
      String sectText = commText.substring(sentCharStart, sentCharEnd);
      LOGGER.debug("Setting text: {}", sectText);
      cm.set(TextAnnotation.class, sectText);

      Tokenization tkz = st.getTokenization();
      List<CoreLabel> clList = tokenizationToCoreLabelList(tkz, i, sentCharStart);
      final int maxIdx = clList.size();
      LOGGER.debug("Setting stanford sentence token begin: {}", tokOffset);
      cm.set(TokenBeginAnnotation.class, tokOffset);
      final int tokEnd = tokOffset + maxIdx;
      LOGGER.debug("Setting stanford sentence token end: {}", tokEnd);
      cm.set(TokenEndAnnotation.class, tokEnd);
      cm.set(TokensAnnotation.class, clList);

      tokOffset = tokEnd;
      toRet.add(cm);
    }

    return toRet;
  }

  private static List<CoreLabel> tokenizationToCoreLabelList(final Tokenization tkz, int sentIdx, int offset) {
    List<CoreLabel> clList = new ArrayList<CoreLabel>();

    TokenList tl = tkz.getTokenList();
    List<Token> tokList = tl.getTokenList();
    for (Token tok : tokList) {
      final TextSpan ts = tok.getTextSpan();
      final int idx = tok.getTokenIndex();
      final int idxPlusOne = idx + 1;

      final int begin = ts.getStart() - offset;
      final int length = ts.getEnding() - ts.getStart();
      CoreLabel cl = factory.makeToken(tok.getText(), begin, length);
      cl.setIndex(idxPlusOne);
      cl.setSentIndex(sentIdx);
      // cl.setOriginalText(tok.getText());
      // cl.set(OriginalTextAnnotation.class, tok.getText());
      clList.add(cl);
    }

    return clList;
  }
}
