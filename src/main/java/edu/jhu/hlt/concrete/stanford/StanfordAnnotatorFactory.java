package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.util.ConcreteException;

public class StanfordAnnotatorFactory {

  private static final Logger logger = LoggerFactory
      .getLogger(StanfordAnnotatorFactory.class);

  /**
   * Based on the communication, return an appropriate
   * {@link GenericStanfordAnnotator}. For this method to succeed, all
   * {@link Communication}s must have an ID and non-empty {@link Section} list.
   * <ul>
   * <li>{@link AnnotateNonTokenizedConcrete}: This is chosen if the first
   * section does not have a set {@link Sentence} list.
   * <li>{@link AnnotateTokenizedConcrete}: This is chosen if the first
   * {@link Sentence} of the first {@link Section} has a {@link Tokenization}
   * with an initialized {@link TokenList}.
   * </ul>
   * 
   * Anything else will throw an exception.
   * 
   * Note that these are not the <i>only</i> requirements on
   * {@link Communication}s: see the method
   * {@link GenericStanfordAnnotator.ensurePreconditionsMet}.
   * 
   * @param comm
   * @param lang
   * @return
   * @throws IOException
   * @throws ConcreteException
   */
  public static GenericStanfordAnnotator getAppropriateAnnotator(
      Communication comm, String lang) throws IOException, ConcreteException {
    if (!comm.isSetId()) {
      logger.error("Communication does not have an ID");
    }
    if (comm.isSetSectionList() && comm.getSectionList() != null
        && comm.getSectionListSize() > 0) {
      Section sect0 = comm.getSectionList().get(0);
      if (!sect0.isSetSentenceList()) {
        return new AnnotateNonTokenizedConcrete(lang);
      }
      if (sect0.getSentenceList() == null) {
        throw new ConcreteException("Section " + sect0.getUuid()
            + " of Communicaiton " + comm.getId()
            + " does has a null, but set, sentence list");
      }
      if (sect0.getSentenceListSize() == 0) {
        throw new ConcreteException("Section " + sect0.getUuid()
            + " of Communicaiton " + comm.getId()
            + " has an empty, but set, sentence list");
      }
      Sentence sent0 = sect0.getSentenceList().get(0);
      if (sent0.isSetTokenization() && sent0.getTokenization() != null) {
        Tokenization tokenization = sent0.getTokenization();
        if (tokenization.isSetTokenList()
            && tokenization.getTokenList() != null) {
          return new AnnotateTokenizedConcrete(lang);
        }
        throw new ConcreteException("Tokenization is not valid");
      }
      throw new ConcreteException("Sentence is not valid");
    }
    logger.error("Communication " + comm.getId()
        + " does not have a valid section list set");

    throw new ConcreteException("Cannot determine appropriate Annotator to use");
  }
}
