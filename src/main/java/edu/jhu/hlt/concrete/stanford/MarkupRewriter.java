/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */
package edu.jhu.hlt.concrete.stanford;

import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

/**
 *
 */
public class MarkupRewriter {

  private static final Set<String> gwMarkupTags = new HashSet<>();
  static {
    gwMarkupTags.add("<HEADLINE>");
    gwMarkupTags.add("</HEADLINE>");
    gwMarkupTags.add("<DATELINE>");
    gwMarkupTags.add("</DATELINE>");
    gwMarkupTags.add("<TEXT>");
    gwMarkupTags.add("</TEXT>");
    gwMarkupTags.add("<P>");
    gwMarkupTags.add("</P>");
    gwMarkupTags.add("</DOC>");
  }

  /**
   *
   */
  private MarkupRewriter() {
    // TODO Auto-generated constructor stub
  }

  public static final String removeMarkup(final String gigawordDocumentContent) {
    return replaceMarkupWithSpaces(gigawordDocumentContent);
  }

  private static final void stringToSpaces(final String toSpace, final StringBuilder toPad) {
    final int len = toSpace.length();
    for (int i = 0; i < len; i++)
      toPad.append(" ");
  }

  private static final String replaceMarkupWithSpaces(final String gwText) {
    StringBuilder newText = new StringBuilder();
    try (Scanner sc = new Scanner(gwText);) {
      String fLine = sc.nextLine();
      stringToSpaces(fLine, newText);
      newText.append("\n");

      while(sc.hasNextLine()) {
        final String nLine = sc.nextLine();
        if (gwMarkupTags.contains(nLine))
          stringToSpaces(nLine, newText);
        else
          newText.append(nLine);

        newText.append("\n");
      }
    }

    return newText.toString();
  }
}
