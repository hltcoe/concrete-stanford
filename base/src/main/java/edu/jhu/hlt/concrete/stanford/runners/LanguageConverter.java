package edu.jhu.hlt.concrete.stanford.runners;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;

import edu.jhu.hlt.concrete.stanford.languages.PipelineLanguage;

public class LanguageConverter implements IStringConverter<PipelineLanguage> {

  @Override
  public PipelineLanguage convert(String value) {
    try {
      return PipelineLanguage.getEnumeration(value);
    } catch (Exception e) {
      throw new ParameterException("Invalid language: " + value);
    }
  }
}
