package edu.jhu.hlt.concrete.stanford.languages;

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
}
