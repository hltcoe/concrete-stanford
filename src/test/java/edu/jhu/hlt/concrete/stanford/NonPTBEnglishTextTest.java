package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.validation.CommunicationValidator;
import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Constituent;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.SpanLink;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.TokenList;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.TokenizationKind;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.miscommunication.tokenized.TokenizedCommunication;
import edu.jhu.hlt.concrete.random.RandomConcreteFactory;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;

public class NonPTBEnglishTextTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(NonPTBEnglishTextTest.class);

  private final RandomConcreteFactory cf = new RandomConcreteFactory();

  // Note in the English sentence below, we have carefully included extra whitespace at the start, middle, and end of the sentence.
  public static final String englishText1 = "   John ( a boy )    ran fast .  ";

  @Test
  public void testEnglish1() throws Exception {
    Communication englishComm = this.cf.communication().setText(englishText1);
    AnnotationMetadata md = new AnnotationMetadata().setTool("concrete-stanford:test").setTimestamp(System.currentTimeMillis() / 1000);
    englishComm.setMetadata(md);
    // Note: the section excludes the first whitespace.
    assertEquals(" ", englishText1.substring(0, 1)); // This is an expectation, not a test.
    Section section = new Section().setUuid(UUIDFactory.newUUID()).setTextSpan(new TextSpan().setStart(1).setEnding(englishText1.length())).setKind("Passage");
    englishComm.addToSectionList(section);
    // Note: the sentence excludes the first two whitespaces.
    assertEquals("  ", englishText1.substring(0, 2)); // This is an expectation, not a test.
    Sentence sentence = new Sentence().setUuid(UUIDFactory.newUUID()).setTextSpan(new TextSpan().setStart(2).setEnding(englishText1.length()));
    section.addToSentenceList(sentence);
    Tokenization tokenization = new Tokenization().setUuid(UUIDFactory.newUUID()).setMetadata(md).setKind(TokenizationKind.TOKEN_LIST);
    // Below is purely for verification purposes:
    // analytic should not destroy old tokenization
    // annotations.
    tokenization.addToSpanLinkList(new SpanLink());
    tokenization.addToSpanLinkList(new SpanLink());
    TokenList tokenList = new TokenList();
    int tokId = 0;
    Pattern whitespace = Pattern.compile("[^ ]+");
    Matcher tokMatcher = whitespace.matcher(englishText1);
    while (tokMatcher.find()) {
      String tokenStr = tokMatcher.group();
      int tokenStart = tokMatcher.start();
      int tokenEnd = tokMatcher.end();
      System.out.printf("token: id=%d start=%d end=%d str=%s\n", tokId, tokenStart, tokenEnd, tokenStr);
      Token token = new Token().setTokenIndex(tokId++).setText(tokenStr).setTextSpan(new TextSpan().setStart(tokenStart).setEnding(tokenEnd));
      tokenList.addToTokenList(token);
    }

    tokenization.setTokenList(tokenList);
    tokenization.setTokenTaggingList(new ArrayList<>());
    sentence.setTokenization(tokenization);
    assertTrue(new CommunicationValidator(englishComm).validate());
    TokenizedCommunication tc = new CachedTokenizationCommunication(englishComm);
    assertEquals(1, tc.getTokenizations().size());
    TokenizedCommunication wDepParse = new ConcreteStanfordPreCorefAnalytic().annotate(tc);
    assertEquals(1, wDepParse.getTokenizations().size());
    Tokenization ntkz = wDepParse.getTokenizations().get(0);
    List<TokenTagging> ttList = ntkz.getTokenTaggingList();
    LOGGER.info("token taggings: {}", ttList.size());
    ttList.forEach(tt -> LOGGER.debug("Got TokenTagging: {}", tt));
    List<TokenTagging> nerTTs = ttList.stream()
        .filter (tt -> tt.getTaggingType().equalsIgnoreCase("NER")).collect(Collectors.toList());
    assertEquals(1, nerTTs.size());

    assertEquals(2, ntkz.getSpanLinkListSize());
    TokenList ntl = ntkz.getTokenList();
    // AnnotateTokenizedConcrete atc = new AnnotateTokenizedConcrete(PipelineLanguage.ENGLISH);
    // atc.annotateWithStanfordNlp(englishComm);
    assertEquals(8, ntl.getTokenListSize());
    assertTrue(ntkz.isSetParseList());
    assertEquals(1, ntkz.getParseListSize());
    int maxCParseSpan = 0;
    for (Constituent cons : ntkz.getParseList().get(0).getConstituentList()) {
      maxCParseSpan = cons.getEnding() > maxCParseSpan ? cons.getEnding() : maxCParseSpan;
    }
    assertEquals(8, maxCParseSpan);

    assertEquals("John", ntl.getTokenList().get(0).getText());
    // assertEquals("(", ntl.getTokenList().get(1).getText());
    assertEquals("ran", ntl.getTokenList().get(5).getText());
    assertEquals("fast", ntl.getTokenList().get(6).getText());

    Section sect = wDepParse.getSections().get(0);
    sect.unsetSentenceList();
    LOGGER.info("Got section: {}", sect);
    Sentence sent = wDepParse.getSentences().get(0);
    sent.unsetTokenization();
    LOGGER.info("Got sentence: {}", sent);

    TextSpan ts;
    ts = ntl.getTokenList().get(0).getTextSpan();
    assertEquals("John", englishText1.substring(ts.getStart(), ts.getEnding()));
    ts = ntl.getTokenList().get(1).getTextSpan();
    assertEquals("(", englishText1.substring(ts.getStart(), ts.getEnding()));
    ts = ntl.getTokenList().get(5).getTextSpan();
    assertEquals("ran", englishText1.substring(ts.getStart(), ts.getEnding()));
    ts = ntl.getTokenList().get(6).getTextSpan();
    assertEquals("fast", englishText1.substring(ts.getStart(), ts.getEnding()));
  }
}
