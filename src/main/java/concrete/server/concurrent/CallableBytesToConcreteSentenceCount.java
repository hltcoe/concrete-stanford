/**
 * 
 */
package concrete.server.concurrent;

import java.util.concurrent.Callable;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.util.CommunicationSerialization;

/**
 * @author max
 *
 */
public class CallableBytesToConcreteSentenceCount implements Callable<Integer> {

  private final CommunicationSerialization cs = new CommunicationSerialization();  
  private final byte[] bytes;
  
  public CallableBytesToConcreteSentenceCount(byte[] bytes) {
    this.bytes = bytes;
  }
  
  /* (non-Javadoc)
   * @see java.util.concurrent.Callable#call()
   */
  @Override
  public Integer call() throws Exception {
    int nSentences = 0;
    Communication c = this.cs.fromBytes(this.bytes);
    if (c.isSetSectionList() && c.getSectionListSize() > 0)
      for (Section s : c.getSectionList())
        if (s.isSetSentenceList())
          nSentences += s.getSentenceListSize();
    
    return nSentences;
  }
}
