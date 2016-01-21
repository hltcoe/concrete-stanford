/*
 * Copyright 2012-2015 Johns Hopkins University HLTCOE. All rights reserved.
 * See LICENSE in the project root directory.
 */

package edu.jhu.hlt.concrete.stanford;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Constituent;
import edu.jhu.hlt.concrete.Dependency;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Parse;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TheoryDependencies;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.UUID;
import edu.jhu.hlt.concrete.analytics.base.AnalyticException;
import edu.jhu.hlt.concrete.tokenization.DependencyFactory;
import edu.jhu.hlt.concrete.tokenization.ParseFactory;
import edu.jhu.hlt.concrete.util.Timing;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

/**
 *
 */
public class PreNERCoreMapWrapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(PreNERCoreMapWrapper.class);

  private final CoreMapWrapper wrapper;
  private final Optional<Tree> tree;
  private final Optional<SemanticGraph> basicDeps;
  private final Optional<SemanticGraph> colDeps;
  private final Optional<SemanticGraph> colCCDeps;

  private final HeadFinder hf;
  private final AnalyticUUIDGenerator gen;

  /**
   *
   */
  public PreNERCoreMapWrapper(final CoreMap cm, final HeadFinder hf, final AnalyticUUIDGenerator gen) {
    this.wrapper = new CoreMapWrapper(cm, gen);
    this.hf = hf;
    this.tree = Optional.ofNullable(cm.get(TreeAnnotation.class));
    this.basicDeps = Optional.ofNullable(cm.get(BasicDependenciesAnnotation.class));
    this.colDeps = Optional.ofNullable(cm.get(CollapsedDependenciesAnnotation.class));
    this.colCCDeps = Optional.ofNullable(cm.get(CollapsedCCProcessedDependenciesAnnotation.class));
    this.gen = gen;
  }

  /**
   * Whenever there's an empty parse, this method will set the required
   * constituent list to be an empty list. It's up to the caller on what to do
   * with the returned Parse.
   *
   * @param n
   *          is the number of tokens in the sentence
   *
   * @throws AnalyticException
   */
  private Parse makeConcreteCParse(Tree root, int n, UUID tokenizationUUID, HeadFinder hf) throws AnalyticException {
    int left = 0;
    int right = root.getLeaves().size();
    if (right != n)
      throw new AnalyticException("number of leaves in the parse (" + right + ") is not equal to the number of tokens in the sentence (" + n + ")");

    Parse p = new ParseFactory(this.gen).create();
    TheoryDependencies deps = new TheoryDependencies();
    deps.addToTokenizationTheoryList(tokenizationUUID);
    AnnotationMetadata md = new AnnotationMetadata("Stanford CoreNLP", Timing.currentLocalTime(), 1);
    p.setMetadata(md);
    constructConstituent(root, left, right, n, p, tokenizationUUID, hf);
    if (!p.isSetConstituentList()) {
      LOGGER.warn("Setting constituent list to compensate for the empty parse for tokenization id {} and tree {}", tokenizationUUID, root);
      p.setConstituentList(new ArrayList<Constituent>());
    }
    return p;
  }

  /**
  *
  * @param root
  * @param left
  * @param right
  * @param n
  *          is the length of the sentence is tokens.
  * @param p
  * @param tokenizationUUID
  * @return The constituent ID
  * @throws AnalyticException
  */
 private static int constructConstituent(Tree root, int left,
     int right, int n, Parse p, UUID tokenizationUUID, HeadFinder hf)
     throws AnalyticException {

   Constituent constituent = new Constituent();
   constituent.setId(p.getConstituentListSize());
   constituent.setTag(root.value());
   constituent.setStart(left);
   constituent.setEnding(right);
   p.addToConstituentList(constituent);
   Tree headTree = null;
   if (!root.isLeaf()) {
     try {
       headTree = hf.determineHead(root);
     } catch (java.lang.IllegalArgumentException iae) {
       LOGGER.warn("Failed to find head, falling back on rightmost constituent.", iae);
       headTree = root.children()[root.numChildren() - 1];
     }
   }
   int i = 0, headTreeIdx = -1;

   int leftPtr = left;
   for (Tree child : root.getChildrenAsList()) {
     int width = child.getLeaves().size();
     int childId = constructConstituent(child, leftPtr, leftPtr
         + width, n, p, tokenizationUUID, hf);
     constituent.addToChildList(childId);

     leftPtr += width;
     if (headTree != null && child == headTree) {
       assert (headTreeIdx < 0);
       headTreeIdx = i;
     }
     i++;
   }

   if (headTreeIdx >= 0)
     constituent.setHeadChildIndex(headTreeIdx);

   if (!constituent.isSetChildList())
     constituent.setChildList(new ArrayList<Integer>());
   return constituent.getId();
 }

  private List<DependencyParse> constructDependencyParses(UUID tokUuid) throws AnalyticException {
    List<DependencyParse> depParseList = new ArrayList<>();
    // possibly add a check if sg.size() == 0
    this.basicDeps.ifPresent(sg -> {
      LOGGER.debug("Generating DependencyParse from basic dependencies.");
      depParseList.add(this.makeDepParse(sg, tokUuid, "Stanford CoreNLP basic"));
    });
    this.colDeps.ifPresent(sg -> {
      LOGGER.debug("Generating DependencyParse from collapsed dependencies.");
      depParseList.add(this.makeDepParse(sg, tokUuid, "Stanford CoreNLP col"));
    });
    this.colCCDeps.ifPresent(sg -> {
      LOGGER.debug("Generating DependencyParse from collapsed-CC dependencies.");
      depParseList.add(this.makeDepParse(sg, tokUuid, "Stanford CoreNLP col-CC"));
    });

    return depParseList;
  }

  private DependencyParse makeDepParse(SemanticGraph semGraph, UUID tokenizationUUID, String toolName) {
    DependencyParse depParse = new DependencyParse();
    depParse.setUuid(this.gen.next());
    TheoryDependencies td = new TheoryDependencies();
    td.addToTokenizationTheoryList(tokenizationUUID);
    AnnotationMetadata md = new AnnotationMetadata(toolName, Timing.currentLocalTime(), 1);
    depParse.setMetadata(md);
    List<Dependency> dependencies = makeDependencies(semGraph);
    depParse.setDependencyList(dependencies);
    return depParse;
  }

  private List<Dependency> makeDependencies(SemanticGraph graph) {
    List<Dependency> depList = new ArrayList<Dependency>();
    for (IndexedWord root : graph.getRoots()) {
      // this mimics CoreNLP's handling
      String rel = GrammaticalRelation.ROOT.getLongName().replaceAll("\\s+", "");
      int dep = root.index() - 1;
      Dependency depend = DependencyFactory.create(dep, rel, -1);
      depList.add(depend);
    }
    for (SemanticGraphEdge edge : graph.edgeListSorted()) {
      String rel = edge.getRelation().toString().replaceAll("\\s+", "");
      int gov = edge.getSource().index() - 1;
      int dep = edge.getTarget().index() - 1;
      Dependency depend = DependencyFactory.create(dep, rel, gov);
      depList.add(depend);
    }
    return depList;
  }

  public Sentence toSentence(final int offset) throws AnalyticException {
    Sentence pre = this.wrapper.toSentence(offset);
    LOGGER.debug("Got sentence from original wrapper: {}", pre.toString());
    // adds annotations in-place.
    this.addStanfordAnalyticOutput(pre);
    return pre;
  }

  /**
   * Adds annotations to an already established {@link Sentence} object.
   * <br>
   * <br>
   * Not consistent with the "return something new" paradigm - this mutates
   * the passed in sentence object.
   *
   * @param st the {@link Sentence} to add annotations to
   * @throws AnalyticException on error generating {@link Parse} or {@link DependencyParse}
   */
  private void addStanfordAnalyticOutput(final Sentence st) throws AnalyticException {
    Tokenization newTkz = st.getTokenization();
    UUID tkzID = newTkz.getUuid();
    List<DependencyParse> dpList = this.constructDependencyParses(tkzID);
    dpList.forEach(dp -> newTkz.addToDependencyParseList(dp));
    // cannot use functional style here b/c of checked ex.
    if (this.tree.isPresent()) {
      Parse p = makeConcreteCParse(tree.get(), newTkz.getTokenList().getTokenListSize(), tkzID, this.hf);
      newTkz.addToParseList(p);
    }
  }

  public Sentence toSentence(final int offset, final Sentence origSent) throws AnalyticException {
    Sentence updated = this.wrapper.toSentence(offset, origSent);
    this.addStanfordAnalyticOutput(updated);
    return updated;
  }
}
