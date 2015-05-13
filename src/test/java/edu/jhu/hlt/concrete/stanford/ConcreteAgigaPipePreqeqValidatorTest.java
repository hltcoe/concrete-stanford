/**
 *
 */
package edu.jhu.hlt.concrete.stanford;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.UUID;
import edu.jhu.hlt.concrete.stanford.AnnotateNonTokenizedConcrete.PrereqValidator;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.jhu.hlt.concrete.util.Timing;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;

/**
 * @author fferraro
 *
 */
public class ConcreteAgigaPipePreqeqValidatorTest extends PrereqValidator {

  @Test
  public void testVerifyCommunication() throws ConcreteException {
    Communication comm = null;
    // nullity check
    assertFalse(verifyCommunication(comm));
    comm = new Communication().setUuid(UUIDFactory.newUUID())
        .setType("A story");
    // check for doc id
    assertFalse(verifyCommunication(comm));
    comm.setId("my_story");
    // check for .text
    assertFalse(verifyCommunication(comm));
    comm.setText("");
    // check for non-empty .text
    assertFalse(verifyCommunication(comm));
    comm.setText("This is some sample text.");
    // check for verifiable sections
    assertFalse(verifyCommunication(comm));
    Section section = new Section().setUuid(UUIDFactory.newUUID()).setTextSpan(
        new TextSpan().setStart(0).setEnding(5));
    comm.addToSectionList(section);
    // check for metadata
    assertFalse(verifyCommunication(comm));
    AnnotationMetadata metadata = new AnnotationMetadata();
    metadata.setTimestamp(Timing.currentLocalTime());
    comm.setMetadata(metadata);
    // check for set tool name
    assertFalse(verifyCommunication(comm));
    metadata.setTool("");
    // check for non-empty tool name
    assertFalse(verifyCommunication(comm));
    metadata.setTool("My Analytic");
    // should be good
    assertTrue(verifyCommunication(comm));
  }

  @Test
  public void testVerifySection() {
    StringBuilder sb = new StringBuilder();
    // nullity check
    assertFalse(verifySection(null, sb));
    assertEquals("Section cannot be null.", sb.toString().trim());
    sb = new StringBuilder();
    // /////////////////////////////////
    UUID uuid = UUIDFactory.newUUID();
    Section section = new Section().setUuid(uuid);
    assertFalse(section == null);
    // section must have text span
    assertFalse(verifySection(section, sb));
    assertEquals("Section " + uuid + " must have .textSpan set.", sb.toString()
        .trim());
    sb = new StringBuilder();
    // ////////////////////////////////
    // section with invalid text span
    section.setTextSpan(new TextSpan().setStart(0).setEnding(0));
    assertFalse(verifySection(section, sb));
    sb = new StringBuilder();
    // section with valid text span
    section.setTextSpan(new TextSpan().setStart(0).setEnding(5));
    assertTrue(verifySection(section, sb));
    sb = new StringBuilder();
    // section with set, but empty sentences
    List<Sentence> sentences = new LinkedList<Sentence>();
    section.setSentenceList(sentences);
    assertFalse(verifySection(section, sb));
    sb = new StringBuilder();
    // and now once we've added a valid sentence
    Sentence sentence = new Sentence().setUuid(UUIDFactory.newUUID());
    sentence.setTextSpan(new TextSpan().setStart(0).setEnding(5));
    sentences.add(sentence);
    assertTrue(verifySection(section, sb));
  }

  @Test
  public void testVerifySentence() {
    StringBuilder sb = new StringBuilder();
    // nullity check
    assertFalse(verifySentence(null, sb));
    assertEquals("Sentence cannot be null.", sb.toString().trim());
    sb = new StringBuilder();
    // /////////////////////////////////
    UUID uuid = UUIDFactory.newUUID();
    Sentence sentence = new Sentence().setUuid(uuid);
    assertFalse(sentence == null);
    // sentence must have text span
    assertFalse(verifySentence(sentence, sb));
    assertEquals("Sentence " + uuid + " must have a .textSpan set.", sb
        .toString().trim());
    sb = new StringBuilder();
    // ////////////////////////////////
    // sentence with invalid text span
    sentence.setTextSpan(new TextSpan().setStart(0).setEnding(0));
    assertFalse(verifySentence(sentence, sb));
    sb = new StringBuilder();
    // sentence with valid text span
    sentence.setTextSpan(new TextSpan().setStart(0).setEnding(5));
    assertTrue(verifySentence(sentence, sb));
    sb = new StringBuilder();
    // sentence with Tokenization set
    sentence.setTokenization(new Tokenization());
    assertFalse(verifySentence(sentence, sb));
    assertEquals("Sentence " + uuid
        + " must not have a tokenization set (it will be overwritten!).", sb
        .toString().trim());
  }

  @Test
  public void testVerifyTextSpan() {
    assertFalse(verifyTextSpan(null, new StringBuilder()));
    // This block is testing for failing verifications
    {
      // this tests: (-1, -1), (0, -1), (1, 0), (1, 1))
      int[] starts = { -1, 0, 1, 1 };
      int[] ends = { -1, -1, 0, 1 };
      assertEquals(starts.length, ends.length);
      for (int i = 0; i < starts.length; ++i) {
        StringBuilder sb = new StringBuilder();
        TextSpan ts = new TextSpan();
        ts.setStart(starts[i]).setEnding(ends[i]);
        boolean res = verifyTextSpan(ts, sb);
        assertFalse(res);
      }
    }
    // This block is testing for succeeding verifications
    {
      // this tests: (0, 1)
      int[] starts = { 0 };
      int[] ends = { 1 };
      assertEquals(starts.length, ends.length);
      for (int i = 0; i < starts.length; ++i) {
        StringBuilder sb = new StringBuilder();
        TextSpan ts = new TextSpan();
        ts.setStart(starts[i]).setEnding(ends[i]);
        boolean res = verifyTextSpan(ts, sb);
        assertTrue(res);
      }
    }

  }
}
