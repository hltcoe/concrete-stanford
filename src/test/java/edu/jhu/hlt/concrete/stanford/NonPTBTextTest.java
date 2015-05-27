package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.validation.CommunicationValidator;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Constituent;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.TokenizationKind;
import edu.jhu.hlt.concrete.random.RandomConcreteFactory;
import edu.jhu.hlt.concrete.stanford.InMemoryAnnoPipeline.Languages;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;

public class NonPTBTextTest {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(NonPTBTextTest.class);

  RandomConcreteFactory cf = new RandomConcreteFactory();

  public static final String chineseText1 = "德国 工程 集团 西门子 和 瑞典 能源 公司 Vattenfall 已 将 邯峰 ( Hanfeng ) 火力 发电厂 40%  的 股份 转让 给 中国 华 能 集团 ( ChinaHuanengGroup ) 和 中信 ( CITIC ) .";
  public static final String englishText1 = "John ( a boy ) ran fast .";

  @Test
  public void testChinese1() throws Exception {
    Communication chineseComm = this.cf.communication().setText(chineseText1);
    AnnotationMetadata md = new AnnotationMetadata().setTool(
        "concrete-stanford:test").setTimestamp(
        System.currentTimeMillis() / 1000);
    chineseComm.setMetadata(md);
    Section section = new Section()
        .setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(0).setEnding(chineseText1.length()))
        .setKind("</TEXT>");
    chineseComm.addToSectionList(section);
    Sentence sentence = new Sentence().setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(0).setEnding(chineseText1.length()));
    section.addToSentenceList(sentence);
    Tokenization tokenization = new Tokenization()
        .setUuid(UUIDFactory.newUUID()).setMetadata(md)
        .setKind(TokenizationKind.TOKEN_LIST);
    TokenList tokenList = new TokenList();
    int tokId = 0;
    int tokenStart = 0, tokenEnd = 0;
    for (String tokenStr : chineseText1.split(" +")) {
      tokenEnd += tokenStr.length();
      Token token = new Token().setTokenIndex(tokId++).setText(tokenStr)
          .setTextSpan(new TextSpan().setStart(tokenStart).setEnding(tokenEnd));
      tokenStart = tokenEnd + 1;
      tokenEnd = tokenStart;
      tokenList.addToTokenList(token);
    }
    tokenization.setTokenTaggingList(new ArrayList<>());
    tokenization.setTokenList(tokenList);
    sentence.setTokenization(tokenization);

    assertTrue(new CommunicationValidator(chineseComm).validate());

    AnnotateTokenizedConcrete atc = new AnnotateTokenizedConcrete(Languages.CHINESE);
    atc.annotateWithStanfordNlp(chineseComm);
    assertTrue(tokenization.isSetParseList());
    assertEquals(1, tokenization.getParseListSize());
  }

  @Test
  public void testEnglish1() throws Exception {
    Communication englishComm = this.cf.communication().setText(englishText1);
    AnnotationMetadata md = new AnnotationMetadata().setTool(
        "concrete-stanford:test").setTimestamp(
        System.currentTimeMillis() / 1000);
    englishComm.setMetadata(md);
    Section section = new Section()
        .setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(0).setEnding(englishText1.length()))
        .setKind("Passage");
    englishComm.addToSectionList(section);
    Sentence sentence = new Sentence().setUuid(UUIDFactory.newUUID())
        .setTextSpan(
            new TextSpan().setStart(0).setEnding(englishText1.length()));
    section.addToSentenceList(sentence);
    Tokenization tokenization = new Tokenization()
        .setUuid(UUIDFactory.newUUID()).setMetadata(md)
        .setKind(TokenizationKind.TOKEN_LIST);
    TokenList tokenList = new TokenList();
    int tokId = 0;
    int tokenStart = 0, tokenEnd = 0;
    for (String tokenStr : englishText1.split(" +")) {
      tokenEnd += tokenStr.length();
      Token token = new Token().setTokenIndex(tokId++).setText(tokenStr)
          .setTextSpan(new TextSpan().setStart(tokenStart).setEnding(tokenEnd));
      tokenStart = tokenEnd + 1;
      tokenEnd = tokenStart;
      tokenList.addToTokenList(token);
    }
    tokenization.setTokenList(tokenList);
    tokenization.setTokenTaggingList(new ArrayList<>());
    sentence.setTokenization(tokenization);
    assertTrue(new CommunicationValidator(englishComm).validate());
    AnnotateTokenizedConcrete atc = new AnnotateTokenizedConcrete(Languages.ENGLISH);
    atc.annotateWithStanfordNlp(englishComm);
    String[] expectedTokens = englishText1.split(" +");
    assertEquals(expectedTokens.length, tokenList.getTokenListSize());
    assertTrue(tokenization.isSetParseList());
    assertEquals(1, tokenization.getParseListSize());
    int maxCParseSpan = 0;
    for (Constituent cons : tokenization.getParseList().get(0)
        .getConstituentList()) {
      maxCParseSpan = cons.getEnding() > maxCParseSpan ? cons.getEnding()
          : maxCParseSpan;
    }
    assertEquals(8, maxCParseSpan);
    assertEquals("(", tokenList.getTokenList().get(1).getText());
    int tokIdx = 0;
    tokenStart = 0;
    tokenEnd = 0;
    for (String tokenStr : expectedTokens) {
      assertEquals(tokenStr, tokenList.getTokenList().get(tokIdx).getText());
      tokenEnd += tokenStr.length();
      assertEquals(tokenStr, englishText1.substring(tokenStart, tokenEnd));
      tokenStart = tokenEnd + 1;
      tokenEnd = tokenStart;
      ++tokIdx;
    }
  }
}
