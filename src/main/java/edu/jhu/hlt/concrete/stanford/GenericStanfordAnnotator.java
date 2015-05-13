package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;

import concrete.tools.AnnotationException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.util.ConcreteException;

public interface GenericStanfordAnnotator {
  /**
   *
   * @param comm
   *          : An input {@code Communication} that passes
   *          {@code PrereqValidator.verifyCommunication}.
   * @return An annotated deep copy of the input {@code Communication}. The
   *         input Communication will be unchanged.
   * @throws IOException
   * @throws ConcreteException
   * @throws AnnotationException
   */
  public Communication process(Communication comm) throws IOException,
      ConcreteException, AnnotationException;

  /**
   *
   * @param comm
   *          An input {@link Communication}.
   * @return True iff the {@link Communication} satisfies all preconditions for
   *         this annotator.
   */
  public boolean ensurePreconditionsMet(Communication comm);
}
