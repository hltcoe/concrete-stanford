package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;

import concrete.tools.AnnotationException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.util.ConcreteException;

public interface GenericStanfordAnnotator {
  /**
   *
   * @param comm
   *          An input {@link Communication} that passes
   *          {@code PrereqValidator.verifyCommunication}.
   * @return An annotated deep copy of the input {@code Communication}. The
   *         input Communication will be unchanged.
   * @throws ConcreteException
   * @throws AnnotationException
   */
  public Communication process(Communication comm) throws IOException,
      ConcreteException, AnnotationException;

  default Communication processWithValidation(final Communication comm) throws ConcreteException {
    if (!this.ensurePreconditionsMet(comm))
      throw new ConcreteException("Requirements were not met for this Communication.");
    try {
      return this.process(comm);
    } catch (Exception e) {
      throw new ConcreteException(e);
    }
  };

  /**
   *
   * @param comm
   *          An input {@link Communication}.
   * @return True iff the {@link Communication} satisfies all preconditions for
   *         this annotator.
   */
  public boolean ensurePreconditionsMet(Communication comm);
}
