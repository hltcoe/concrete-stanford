package edu.jhu.hlt.concrete.stanford.languages;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public enum StanfordLanguageAnnotators {

  ENGLISH {
    @Override
    String tokenizationAnnotators() {
      return "tokenize, ssplit";
    }

    @Override
    String preCorefAnnotators() {
      return this.tokenizationAnnotators() + ", pos, lemma, parse, ner";
    }

    @Override
    String allAvailableAnnotators() {
      return this.preCorefAnnotators() + ", dcoref";
    }
  },
  SPANISH {
    @Override
    String tokenizationAnnotators() {
      return "tokenize, ssplit";
    }

    @Override
    String preCorefAnnotators() {
      return this.tokenizationAnnotators() + ", pos, ner, parse";
    }

    @Override
    String allAvailableAnnotators() {
      return this.preCorefAnnotators();
    }
  },
  CHINESE {
    @Override
    String tokenizationAnnotators() {
      return "segment, ssplit";
    }

    @Override
    String preCorefAnnotators() {
      return this.tokenizationAnnotators() + ", pos, ner, parse";
    }

    @Override
    String allAvailableAnnotators() {
      return this.preCorefAnnotators();
    }
  },
  ;

  abstract String tokenizationAnnotators();
  abstract String preCorefAnnotators();
  abstract String allAvailableAnnotators();

  private static final ImmutableSet<String> SENTENCE_TOKENS_ANNOTATORS =
      ImmutableSet.of("ssplit", "tokenize", "segment");

  public String getAnnotators(boolean isOnlyTokenization, boolean isCorefSkipped) {
    if (isOnlyTokenization)
      return this.tokenizationAnnotators();
    else {
      if (isCorefSkipped)
        return this.preCorefAnnotators();
      else
        return this.allAvailableAnnotators();
    }
  }

  /*
   * this is not great, but want to maintain the order -
   * max thinks it matters.
   */
  public ImmutableList<String> getNonTokenizationAnnotators() {
    ImmutableList<String> spl = ImmutableList.copyOf(this.allAvailableAnnotators().split(", "));
    ImmutableList.Builder<String> b = ImmutableList.builder();
    for (String s : spl) {
      if (!SENTENCE_TOKENS_ANNOTATORS.contains(s))
        b.add(s);
    }
    return b.build();
  }
}
