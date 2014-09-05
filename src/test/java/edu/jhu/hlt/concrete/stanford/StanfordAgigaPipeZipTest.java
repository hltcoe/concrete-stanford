/*
 * 
 */
package edu.jhu.hlt.concrete.stanford;


/**
 * @author fferraro
 * 
 */
public class StanfordAgigaPipeZipTest {

//  private static final Logger logger = LoggerFactory.getLogger(StanfordAgigaPipe_ZipTest.class);
//
//  Communication testComm;
//  
//  final StanfordAgigaPipe pipe = new StanfordAgigaPipe();
//  Communication afp0623Comm;
//  Communication processedComm;
//  List<Communication> processedCommunications;
//
//  static {
//    try {
//      ZipFile zf = new ZipFile("src/test/resources/two_newswire.zip");
//      logger.info("Beginning annotation.");
//      processedCommunications = pipe.process(zf);
//      logger.info("Finished.");
//    } catch (Exception e) {
//      throw new RuntimeException(e.getMessage());
//    }
//  }
//
//  @Test
//  public void test_writeProcessedZip() throws TException, IOException {
//    ThriftIO.writeFile("src/test/resources/two_newswire_processed.zip", processedCommunications);
//  }
//
//  @Test
//  public void test_numProcessedZip() throws TException, IOException {
//    assertTrue("Expected 2 processed communications, got " + processedCommunications.size(), processedCommunications.size() == 2);
//  }
//
//  /**
//   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
//   * 
//   * @throws TException
//   * @throws AsphaltException
//   * @throws InvalidInputException
//   * @throws ConcreteException
//   * @throws IOException
//   */
//  @Test
//  public void testZipTest_sentText() throws TException, InvalidInputException, IOException, ConcreteException {
//    String[] sentences = {
//        "Sri Lankan media groups Thursday protested against the arrest of a reporter\n"
//            + "close to Sarath Fonseka, the detained ex-army chief who tried to unseat the\n" + "president in recent elections.",
//        "The groups issued a joint statement demanding the release of Ruwan Weerakoon, a\n" + "reporter with the Nation newspaper, who was arrested this week.",
//        "\"We request the Inspector General of Police to disclose the reasons behind the\n"
//            + "arrest and detention of Ruwan Weerakoon and make arrangements for him to receive\n" + "legal aid immediately,\" the statement added.",
//        "Weerakoon maintained close contact with Fonseka when the general led the\n"
//            + "military during the final phase of last year's war against Tamil Tiger rebels.",
//        "Fonseka was an ally of President Mahinda Rajapakse when the rebel Liberation\n"
//            + "Tigers of Tamil Eelam (LTTE) were crushed in May, but the two men later fell out\n" + "and contested the presidency in January's elections.",
//        "Fonseka was arrested soon after losing the poll and appeared in front of a court\n" + "martial this week.", "The case was adjourned.",
//        "Local and international rights groups have accused Rajapakse of cracking down on\n" + "dissent, a charge the government has denied." };
//    int sentIdx = 0;
//    for (Section sect : processedCommunications.get(0).getSectionSegmentationList().get(0).getSectionList()) {
//      if (sect.getSentenceSegmentationList() != null) {
//        for (Sentence sent : sect.getSentenceSegmentationList().get(0).getSentenceList()) {
//          TextSpan tts = sent.getTextSpan();
//          String grabbed = StanfordAgigaPipe_AFP0623Test.afp0623Text.substring(tts.getStart(), tts.getEnding()).trim();
//          // System.out.println("SentId = " + sentIdx + ", grabbing [[" + grabbed + "]], should be looking at <<" + sentences[sentIdx] + ">> .... " +
//          // grabbed.equals(sentences[sentIdx]));
//
//          assertTrue("SentId = " + sentIdx + ", grabbing [[" + grabbed + "]], should be looking at <<" + sentences[sentIdx] + ">>",
//              grabbed.equals(sentences[sentIdx]));
//          sentIdx++;
//        }
//      }
//    }
//  }
//
//  /**
//   * Test method for {@link edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe#process(edu.jhu.hlt.concrete.Communication)}.
//   * 
//   * @throws TException
//   * @throws AsphaltException
//   * @throws InvalidInputException
//   * @throws ConcreteException
//   * @throws IOException
//   */
//  @Test
//  public void testTwoZip_verifyTokensToFull() throws TException, InvalidInputException, IOException, ConcreteException {
//    int commIndex = 0;
//    for (Communication pcomm : StanfordAgigaPipe_ZipTest.processedCommunications) {
//      logger.info("commIndex = " + commIndex);
//      String docText = pcomm.getText();
//      int numEq = 0;
//      int numTot = 0;
//      for (Section nsect : pcomm.getSectionSegmentationList().get(0).getSectionList()) {
//        if (nsect.getSentenceSegmentationList() == null)
//          continue;
//        for (Sentence nsent : nsect.getSentenceSegmentationList().get(0).getSentenceList()) {
//          for (Token token : nsent.getTokenizationList().get(0).getTokenList().getTokenList()) {
//            TextSpan tts = token.getTextSpan();
//            String substr = docText.substring(tts.getStart(), tts.getEnding());
//            boolean areEq = token.getText().equals(substr);
//            if (!areEq) {
//              logger.warn("Communication " + pcomm.getId() + " (index = " + commIndex + ") :: expected = [" + token.getText() + "];" + "docText(" + tts
//                  + ") = [" + substr + "]");
//            } else {
//              numEq++;
//            }
//            numTot++;
//          }
//        }
//      }
//      double fracPassing = ((double) numEq / (double) numTot);
//      assertTrue("WARNING: only " + fracPassing + "% of tokens matched!", fracPassing >= 0.8);
//      commIndex += 1;
//    }
//  }
}
