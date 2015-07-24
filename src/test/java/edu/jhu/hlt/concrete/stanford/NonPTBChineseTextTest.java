package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.validation.CommunicationValidator;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.TokenizationKind;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.random.RandomConcreteFactory;
import edu.jhu.hlt.concrete.util.SuperTextSpan;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;

public class NonPTBChineseTextTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(NonPTBChineseTextTest.class);

  private final RandomConcreteFactory cf = new RandomConcreteFactory();

  public static final String chineseText1 = "德国 工程 集团 西门子 和 瑞典 能源 公司 Vattenfall 已 将 邯峰 ( Hanfeng ) 火力 发电厂 40%  的 股份 转让 给 中国 华 能 集团 ( ChinaHuanengGroup ) 和 中信 ( CITIC ) .";

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

    TokenizedCommunication tc = new CachedTokenizationCommunication(chineseComm);
    TokenizedCommunication wDepParse = new ConcreteStanfordPreCorefAnalytic(PipelineLanguage.CHINESE).annotate(tc);

    List<Section> sectList = tc.getSections();
    sectList.forEach(s -> {
      LOGGER.info("Got kind: {}", s.getKind());
      LOGGER.info("Got text span: {}", new SuperTextSpan(s.getTextSpan(), chineseComm).getText());

      s.getSentenceList().forEach(st -> {
        LOGGER.info("Got sentence: {}", new SuperTextSpan(st.getTextSpan(), chineseComm).getText());
        Tokenization tkz = st.getTokenization();
        tkz.getTokenList().getTokenList().forEach(tok -> {
          LOGGER.info("Got token text: {}", new SuperTextSpan(tok.getTextSpan(), chineseComm).getText());
        });
        tkz.getTokenTaggingList().forEach(tt -> {
          LOGGER.info("Got TT: {} [kind: {}]", tt.getUuid().getUuidString(), tt.getTaggingType());
        });
      });
    });

    // List<Tokenization> ntkzList = wDepParse.getTokenizations();
    // Tokenization ntkz = ntkzList.get(0);
    // parse disabled by default
    // assertTrue(ntkz.isSetParseList());
    // assertEquals(1, ntkz.getParseListSize());
  }
}
