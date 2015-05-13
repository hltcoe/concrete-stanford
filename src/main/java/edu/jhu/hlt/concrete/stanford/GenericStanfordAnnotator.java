package edu.jhu.hlt.concrete.stanford;

import concrete.tools.AnnotationException;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.util.ConcreteException;

public interface GenericStanfordAnnotator {
  /**
   *
   * @param comm
   *          An input {@link Communication} that passes
   *          {@link #ensurePreconditionsMet(Communication)}.
   * @return An annotated deep copy of the input Communication. The
   *         input Communication will be unchanged.
   * @throws ConcreteException
   * @throws AnnotationException
   */
  public Communication process(Communication comm) throws AnalyticException;

  default Communication processWithValidation(final Communication comm) throws AnalyticException {
    if (!this.ensurePreconditionsMet(comm))
      throw new AnalyticException("Requirements were not met for this Communication.");
    try {
      return this.process(comm);
    } catch (Exception e) {
      throw new AnalyticException(e);
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
