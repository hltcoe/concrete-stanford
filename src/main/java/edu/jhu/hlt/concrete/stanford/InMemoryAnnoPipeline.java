package edu.jhu.hlt.concrete.stanford;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Serializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.agiga.AgigaDocument;
import edu.jhu.agiga.AgigaPrefs;
import edu.jhu.agiga.BytesAgigaDocumentReader;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.PTBTokenizerAnnotator;
import edu.stanford.nlp.pipeline.ParserAnnotatorUtils;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.WordsToSentencesAnnotator;
import edu.stanford.nlp.pipeline.XMLOutputter;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.EnglishGrammaticalStructureFactory;
import edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalStructureFactory;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;


/**
 * An in-memory version of the Annotated Gigaword pipeline, using only the Stanford CORE NLP tools.
 * 
 * This class allows AgigaDocument objects to be created entirely in-memory from an (unannotated) input represented as a Stanford Annotation object.
 * 
 * @author mgormley
 * @author fferraro
 */
public class InMemoryAnnoPipeline {

  private static final Logger logger = LoggerFactory.getLogger(InMemoryAnnoPipeline.class);
  //private static final String basedir = System.getProperty("InMemoryAnnoPipeline", "data");

  private static final boolean do_deps = true;
  // Document counter.
  private int docCounter;

  private PTBTokenizerAnnotator ptbTokenizerUnofficial;
  private PTBTokenizerAnnotator ptbTokenizer;
  private WordsToSentencesAnnotator words2SentencesAnnotator;
  // NOTE: we're only using this for its annotationToDoc method
  private StanfordCoreNLP pipeline;
  private static GrammaticalStructureFactory gsf;
  //private static String[] documentLevelStages = { "pos", "lemma", "parse", "ner" };
  private String[] documentLevelStages;

  private static String firstPassTokArgs = "" + 
      "invertible=true," + //default
      "tokenizeNLs=true," + //override
      "ptb3Escaping=false," + //override
      "americanize=false," + //override
      "normalizeSpace=false," + //override
      "normalizeAmpersandEntity=false," + //override
      "normalizeCurrency=false," + //override
      "normalizeFractions=false," + //override
      "normalizeParentheses=false," + //override
      "normalizeOtherBrackets=false," + //override
      "asciiQuotes=false," + //default
      "latexQuotes=false," + //override
      "unicodeQuotes=false," + //default
      "ptb3Ellipsis=false," + //override 
      "unicodeEllipsis=false," + //default
      "ptb3Dashes=false," + //override
      "splitAssimilations=true," + //default: Note that the docs say "keepAssimilations," but if you look at the source, it's actually "split"
      "escapeForwardSlashAsterisk=false," + //override
      "untokenizable=noneKeep," + //override
      "strictTreebank3=false"; //default

  public InMemoryAnnoPipeline() {
    documentLevelStages = new String[] { "pos", "lemma", "parse", "ner" };
    docCounter = 0;
    ptbTokenizer = new PTBTokenizerAnnotator();
    ptbTokenizerUnofficial = new PTBTokenizerAnnotator(true, firstPassTokArgs);
    gsf = new EnglishGrammaticalStructureFactory();
    words2SentencesAnnotator = new WordsToSentencesAnnotator();
    Properties props = new Properties();
    String annotatorList = "tokenize, ssplit, pos, lemma, parse, ner";
    logger.debug("Using annotators: {}", annotatorList);

    props.put("annotators", annotatorList);
    props.setProperty("output.printSingletonEntities", "true");
    logger.debug("Loading models and resources.");
    pipeline = new StanfordCoreNLP(props);
    logger.debug("Done.");
  }

  public InMemoryAnnoPipeline(String lang) {
    docCounter = 0;
    ptbTokenizer = new PTBTokenizerAnnotator();
    ptbTokenizerUnofficial = new PTBTokenizerAnnotator(true, firstPassTokArgs);
    words2SentencesAnnotator = new WordsToSentencesAnnotator();
    if (lang.equals("en")) {
	    documentLevelStages = new String[] { "pos", "lemma", "parse", "ner" };
    	    gsf = new EnglishGrammaticalStructureFactory();
	    Properties props = new Properties();
	    String annotatorList = "tokenize, ssplit, pos, lemma, parse, ner";
	    logger.debug("Using annotators: {}", annotatorList);

	    props.put("annotators", annotatorList);
	    props.setProperty("output.printSingletonEntities", "true");
	    logger.debug("Loading models and resources.");
	    pipeline = new StanfordCoreNLP(props);
    }
    else if (lang.equals("cn")) {
    	    gsf = new ChineseGrammaticalStructureFactory();
	    pipeline = makeChinesePipeline();
	    documentLevelStages = new String[] { "segment", "pos", "lemma", "parse" };
    }
    else {
    	System.err.println("Do not support language: " + lang);
	System.exit(0);
    }
    logger.debug("Done.");
  }

  
  public static StanfordCoreNLP makeChinesePipeline() {
	Properties props = new Properties();
      	String annotatorList = "segment, ssplit, pos, parse";
	logger.debug("Using annotators: {}", annotatorList);

	props.setProperty("customAnnotatorClass.segment", "edu.stanford.nlp.pipeline.ChineseSegmenterAnnotator");

	props.setProperty("segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz");
	props.setProperty("segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese");
	props.setProperty("segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz");
	props.setProperty("segment.sighanPostProcessing", "true");

	props.setProperty("ssplit.boundaryTokenRegex", "[.]|[!?]+|[。]|[！？]+");
	logger.debug("Loading segmentation models and resources."); 

	props.setProperty("pos.model", "edu/stanford/nlp/models/pos-tagger/chinese-distsim/chinese-distsim.tagger");
	logger.debug("Loading pos models and resources."); 

	props.setProperty("parse.model", "edu/stanford/nlp/models/lexparser/chinesePCFG.ser.gz");
	logger.debug("Loading parser models and resources."); 
	props.put("annotators", annotatorList);
	StanfordCoreNLP cpipeline = new StanfordCoreNLP(props);
	return cpipeline;	
  }


  public void prepForNext() {
      docCounter = 0;
  }

  /**
   * This only performs tokenization and sentence-splitting on a block of text. 
   * Part-of-speech tagging is handled elsewhere.
   * 
   * @param text
   *          A block of text; perhaps multiple sentences.
   * @return An annotation object containing the tokenized and sentence-split text.
   */
  public Annotation handleSection(Section concSection, String text) {
    Annotation stanfordSection = new Annotation(text);;
    if(! concSection.isSetSentenceList() ) {
      ptbTokenizer.annotate(stanfordSection);
      words2SentencesAnnotator.annotate(stanfordSection);
    } else {
      int sectionOffset = concSection.getRawTextSpan().getStart();
      logger.debug("Text is :: [" + text + "]");
      stanfordSection.set(CharacterOffsetEndAnnotation.class, sectionOffset);
      stanfordSection.set(CoreAnnotations.TokensAnnotation.class, new ArrayList<CoreLabel>());
      stanfordSection.set(SentencesAnnotation.class, new ArrayList<CoreMap>());
      int numSentences = 0;
      for(Sentence concSentence : concSection.getSentenceList()) {
        TextSpan sts = concSentence.getRawTextSpan();
        int start = sts.getStart() - sectionOffset;
        int end = sts.getEnding() - sectionOffset;
        //issue: next.start != current.end + 1
        String sentenceText = text.substring(start, end);
        Annotation sentenceAnnotation = new Annotation(sentenceText);
        ptbTokenizer.annotate(sentenceAnnotation);
        this.mimicWordsToSentsAnnotator(stanfordSection, sentenceAnnotation, numSentences);
        ++numSentences;
      }
    }
    return stanfordSection;
  }

  /**
   * This method transfers the Token {@link CoreLabel} annotations
   * for {@link stanfordSentence} to the section annotation,
   * {@link stanfordSection}.
   */
  private void mimicWordsToSentsAnnotator(Annotation stanfordSection,
                                          Annotation stanfordSentence,
                                          int numSentence) {
    // add a new sentence corresponding to stanfordSentence
    List<CoreMap> sectionSents = stanfordSection.get(SentencesAnnotation.class);
    sectionSents.add(stanfordSentence);
    stanfordSection.set(SentencesAnnotation.class, sectionSents);
    // each Stanford sentence is a list of CoreLabels
    List<CoreLabel> sentTokens = stanfordSentence.get(CoreAnnotations.TokensAnnotation.class);
    for(CoreLabel token : sentTokens) {
        logger.debug("" + token.get(CharacterOffsetBeginAnnotation.class) + " ===> " + (token.get(CharacterOffsetBeginAnnotation.class)));
        token.set(CharacterOffsetBeginAnnotation.class,
                  token.get(CharacterOffsetBeginAnnotation.class));
        token.set(CharacterOffsetEndAnnotation.class,
                  token.get(CharacterOffsetEndAnnotation.class));
    }
    stanfordSentence.set(CoreAnnotations.TokensAnnotation.class, sentTokens);
    // List<CoreLabel> sectionTokens = stanfordSection.get(CoreAnnotations.TokensAnnotation.class);
    // sectionTokens.addAll(sentTokens);
    // stanfordSection.set(CoreAnnotations.TokensAnnotation.class, sectionTokens);
    CoreLabel lastToken = sentTokens.get(sentTokens.size() - 1);
    //reset the stanfordSection endpoint
    stanfordSection.set(CharacterOffsetEndAnnotation.class,
                        lastToken.get(CharacterOffsetEndAnnotation.class));
    logger.debug("Setting section end = " + lastToken.get(CharacterOffsetEndAnnotation.class));
    //reset the stanfordSection token endpoints
    if(stanfordSection.get(CoreAnnotations.TokenEndAnnotation.class) == null) {
        stanfordSection.set(CoreAnnotations.TokenEndAnnotation.class, 0);
    }
    if(stanfordSection.get(CoreAnnotations.TokenBeginAnnotation.class) == null) {
        stanfordSection.set(CoreAnnotations.TokenBeginAnnotation.class, 0);
    }
    stanfordSection.set(CoreAnnotations.TokenEndAnnotation.class,
                        stanfordSection.get(CoreAnnotations.TokenEndAnnotation.class) + sentTokens.size());
  }

  /**
   * This only performs tokenization and sentence-splitting on a block of text. 
   * Part-of-speech tagging is handled elsewhere.
   * 
   * @param text
   *          A block of text; perhaps multiple sentences.
   * @return An annotation object containing the tokenized and sentence-split text.
   */
  public Annotation splitAndTokenizeText(String text) {
    Annotation sentence = new Annotation(text);
    ptbTokenizer.annotate(sentence);
    words2SentencesAnnotator.annotate(sentence);
    return sentence;
  }

  /**
   * Get as non-destructive a PTB tokenization and sentence splitting as possible.
   * 
   * @param text
   *          A block of text; perhaps multiple sentences.
   * @return An annotation object containing the tokenized and sentence-split text.
   */
  public Annotation splitAndTokenizeTextNonDestructive(String text) {
    Annotation sentence = new Annotation(text);
    ptbTokenizerUnofficial.annotate(sentence);
    words2SentencesAnnotator.annotate(sentence);
    // List<CoreMap> sentAnnos = sentence.get(SentencesAnnotation.class);
    // for (CoreMap sentAnno : sentAnnos) {
    //   List<CoreLabel> sentTokens = sentAnno.get(TokensAnnotation.class);
    //   System.out.println("SENTENCEINDEXANNO = " + sentAnno.get(SentenceIndexAnnotation.class));

    //   for (CoreLabel token : sentTokens) {
    //     // note that character offsets are global
    //     String tokenText = token.get(TextAnnotation.class);
    //     System.out.println("token:<<" + tokenText + ">>");
    //     System.out.println("\toriginal:[[" + token.originalText() + "]]");
    //     System.out.println("\tbefore:<<" + token.before() + ">>");
    //     System.out.println("\tafter:<<" + token.after() + ">>");
    //   }
    // }
    return sentence;
  }


  public AgigaDocument annotate(Annotation annotation) throws IOException {
    return annotate(pipeline, annotation);
  }

  public AgigaDocument annotateCoref(Annotation annotation) throws IOException {
    return annotateCoref(pipeline, annotation);
  }

  public AgigaDocument annotate(StanfordCoreNLP pipeline, Annotation annotation) throws IOException {
    for (String stage : documentLevelStages) {
      logger.debug("Annotation stage: {}", stage);
      try {
        (StanfordCoreNLP.getExistingAnnotator(stage)).annotate(annotation);
        if (stage.equals("parse")) {
          fixNullDependencyGraphs(annotation);
        }
      } catch (Exception e) {
        logger.warn("Error annotating stage: {}" + stage);
      }
    }
    
    logger.debug("Local processing annotation keys :: {}", annotation.keySet().toString());
    // Convert to an XML document.
    Document xmlDoc = this.stanfordToXML(pipeline, annotation);
    AgigaDocument agigaDoc = xmlToAgigaDoc(xmlDoc);

    logger.debug("agigaDoc has " + agigaDoc.getSents().size() + " sentences");
    logger.debug("annotation has " + annotation.get(SentencesAnnotation.class).size());
    logger.debug("annotation has " + annotation.get(SentencesAnnotation.class));

    return agigaDoc;
  }

  public AgigaDocument getAgigaDoc(Annotation annotation, boolean tokensOnly) throws IOException {
    logger.debug("Local processing annotation keys :: {}", annotation.keySet().toString());
    // Convert to an XML document.
    Document xmlDoc = this.stanfordToXML(pipeline, annotation, tokensOnly);
    AgigaPrefs prefs = new AgigaPrefs();
    if(tokensOnly) {
      prefs.setAll(false);
      prefs.setWord(true);
      prefs.setOffsets(true);
    } else {
      prefs.setAll(true);
    }
    AgigaDocument agigaDoc = xmlToAgigaDoc(xmlDoc, prefs);

    logger.debug("agigaDoc has " + agigaDoc.getSents().size() + " sentences");
    logger.debug("annotation has " + annotation.get(SentencesAnnotation.class).size());
    logger.debug("annotation has " + annotation.get(SentencesAnnotation.class));

    return agigaDoc;
  }

  public AgigaDocument getAgigaDocAllButCoref(Annotation annotation) throws IOException {
    logger.debug("Local processing annotation keys :: {}", annotation.keySet().toString());
    for (String stage : documentLevelStages) {
      logger.debug("Annotation stage: {}", stage);
      try {
        (StanfordCoreNLP.getExistingAnnotator(stage)).annotate(annotation);
        if (stage.equals("parse")) {
          fixNullDependencyGraphs(annotation);
        }
      } catch (Exception e) {
        logger.warn("Error annotating stage: {}" + stage);
      }
    }
    // Convert to an XML document.
    Document xmlDoc = this.stanfordToXML(pipeline, annotation);
    AgigaPrefs prefs = new AgigaPrefs();
    prefs.setAll(true);
    AgigaDocument agigaDoc = xmlToAgigaDoc(xmlDoc, prefs);

    logger.debug("agigaDoc has " + agigaDoc.getSents().size() + " sentences");
    logger.debug("annotation has " + annotation.get(SentencesAnnotation.class).size());
    logger.debug("annotation has " + annotation.get(SentencesAnnotation.class));

    return agigaDoc;
  }

  public AgigaDocument annotateCoref(StanfordCoreNLP pipeline, Annotation annotation) throws IOException {
    String stage = "dcoref";
    logger.debug("DEBUG: annotation stage = " + stage);
    fixNullDependencyGraphs(annotation);
    try {
      (StanfordCoreNLP.getExistingAnnotator(stage)).annotate(annotation);
    } catch (Exception e) {
      logger.error("Error annotating: {}", stage);
      logger.error(e.getMessage(), e);
    }
    
    logger.debug("annotation keys :: " + annotation.keySet().toString());
    // Convert to an XML document.
    Document xmlDoc = this.stanfordToXML(pipeline, annotation);
    AgigaPrefs agigaPrefs = new AgigaPrefs();
    agigaPrefs.setAll(false);
    agigaPrefs.setCoref(true);
    agigaPrefs.setWord(true);
    agigaPrefs.setOffsets(true);
    AgigaDocument agigaDoc = xmlToAgigaDoc(xmlDoc, agigaPrefs);
    logger.debug("agigaDoc has " + agigaDoc.getSents().size() + " sentences");
    logger.debug("annotation has " + annotation.get(SentencesAnnotation.class).size());
    logger.debug("annotation has " + annotation.get(SentencesAnnotation.class));

    return agigaDoc;
  }

  /**
   * sentences with no dependency structure have null values for the various dependency annotations. make sure these are empty dependencies instead to prevent
   * coref-resolution from dying
   **/
  public static void fixNullDependencyGraphs(Annotation anno) {
    for (CoreMap sent : anno.get(SentencesAnnotation.class)) {
      if (sent.get(CollapsedDependenciesAnnotation.class) == null) {
        sent.set(CollapsedDependenciesAnnotation.class, new SemanticGraph());
      }
    }
  }

  /**
   * This method assumes only one <DOC/> is contained in the xmlDoc.
   * This also sets AgigaPrefs.setAll(true)
   */
  private static AgigaDocument xmlToAgigaDoc(Document xmlDoc) throws UnsupportedEncodingException, IOException {
    AgigaPrefs prefs = new AgigaPrefs();
    prefs.setAll(true);
    return xmlToAgigaDoc(xmlDoc, prefs);
  }

  /** This method assumes only one <DOC/> is contained in the xmlDoc. */
  private static AgigaDocument xmlToAgigaDoc(Document xmlDoc, AgigaPrefs agigaPrefs) throws UnsupportedEncodingException, IOException {
    // Serialize to a byte array.
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Serializer ser = new Serializer(baos, "UTF-8");
    ser.setIndent(2);
    ser.setMaxLength(0);
    // The anno-pipeline used a customized version of the
    // nu.xom.Serializer that gave public access to otherwise protected
    // methods. Instead, we just write the entire document at once as above.
    ser.write(xmlDoc);
    ser.flush();

    BytesAgigaDocumentReader adr = new BytesAgigaDocumentReader(baos.toByteArray(), agigaPrefs);
    if (!adr.hasNext()) {
      throw new IllegalStateException("No documents found.");
    }
    AgigaDocument agigaDoc = adr.next();
    if (adr.hasNext()) {
      throw new IllegalStateException("Multiple documents found.");
    }
    return agigaDoc;
  }

  private static void fillInParseAnnotations(boolean verbose, CoreMap sentence, Tree tree) {
    ParserAnnotatorUtils.fillInParseAnnotations(verbose, true, gsf, sentence, tree);
  }

  /**
   * NOTICE: Copied and modified version from edu.jhu.annotation.GigawordAnnotator.
   *
   * Create the XML document, using the base StanfordCoreNLP default and adding custom dependency representations (to include root elements)
   *
   * @param anno
   *          Document to be output as XML
   * @throws IOException
   */
  public Document stanfordToXML(StanfordCoreNLP pipeline, Annotation anno) {
    return stanfordToXML(pipeline, anno, false);
  }

  /**
   * NOTICE: Copied and modified version from edu.jhu.annotation.GigawordAnnotator.
   * 
   * Create the XML document, using the base StanfordCoreNLP default and adding custom dependency representations (to include root elements)
   * 
   * @param anno
   *          Document to be output as XML
   * @throws IOException
   */
  public Document stanfordToXML(StanfordCoreNLP pipeline, Annotation anno, boolean tokensOnly) {
    Document xmlDoc = XMLOutputter.annotationToDoc(anno, pipeline);

    Element root = xmlDoc.getRootElement();
    Element docElem = (Element) root.getChild(0);

    // The document element will be a <document/> tag, but must be a <DOC/>.
    docElem.setLocalName("DOC");

    // Add empty id and type attributes to the <DOC>.
    docElem.addAttribute(new Attribute("id", Integer.toString(docCounter++)));
    docElem.addAttribute(new Attribute("type", "NONE"));

    // Add an empty id attribute to each sentence.
    Elements sents = docElem.getFirstChildElement("sentences").getChildElements("sentence");
    for (int i = 0; i < sents.size(); i++) {
      Element thisSent = sents.get(i);
      thisSent.addAttribute(new Attribute("id", Integer.toString(i)));
    }

    // rename coreference parent tag to "coreferences"
    Element corefElem = docElem.getFirstChildElement("coreference");
    // because StanfordCoreNLP.annotationToDoc() only appends the coref
    // element if it is nonempty (per Ben's request)
    if (corefElem == null) {
      Element corefInfo = new Element("coreferences", null);
      docElem.appendChild(corefInfo);
    } else {
      corefElem.setLocalName("coreferences");
    }

    if (do_deps) {
      logger.debug("Annotating dependencies");

      // add dependency annotations (need to do it this way because CoreNLP
      // does not include root annotation, and format is different from
      // AnnotatedGigaword)
      for (CoreMap sentence : anno.get(SentencesAnnotation.class)) {
        try {
          if(sentence.containsKey(TreeAnnotation.class)) {
            fillInParseAnnotations(false, sentence, sentence.get(TreeAnnotation.class));
          }
        } catch (Exception e) {
          logger.warn("In stanfordToXML. Error filling in parse annotation for sentence " + sentence);
        }

      }
      List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
      Elements sentElems = docElem.getFirstChildElement("sentences").getChildElements("sentence");
      for (int i = 0; i < sentElems.size(); i++) {
        Element thisSent = sentElems.get(i);
        Elements allDependencyParses = thisSent.getChildElements("dependencies");
        int numDParses = allDependencyParses.size();
        logger.debug("number of dep parses ::: " + numDParses);
        for (int j = 0; j < numDParses; j++) {
          Element depElem = allDependencyParses.get(j);
          Attribute att = depElem.getAttribute("type");
          String type = att.getValue();
          if (type == null)
            throw new RuntimeException("null type in dependency element");
          boolean atLeastOne = sentences.get(i).containsKey(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class) |
            sentences.get(i).containsKey(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class) |
            sentences.get(i).containsKey(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
          if(atLeastOne) {
            depElem.removeChildren();
            depElem.setLocalName(type);
            SemanticGraph semGraph;
            if (type.equals("basic-dependencies"))
              semGraph = sentences.get(i).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
            else if (type.equals("collapsed-dependencies"))
              semGraph = sentences.get(i).get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
            else if (type.equals("collapsed-ccprocessed-dependencies"))
              semGraph = sentences.get(i).get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
            else
              throw new RuntimeException("unknown dependency type " + type);
            addDependencyToXML(semGraph, depElem);
            depElem.removeAttribute(att);
          }
        }
      }
    }
    return xmlDoc;
  }

  /**
   * NOTICE: Copied and modified version from edu.jhu.annotation.GigawordAnnotator.
   * 
   * Create the XML document, using the base StanfordCoreNLP default and adding custom dependency representations (to include root elements)
   * 
   * @param anno
   *          Document to be output as XML
   * @throws IOException
   */
  public Document corefToXML(StanfordCoreNLP pipeline, Annotation anno) {
    Document xmlDoc = XMLOutputter.annotationToDoc(anno, pipeline);

    Element root = xmlDoc.getRootElement();
    Element docElem = (Element) root.getChild(0);

    // The document element will be a <document/> tag, but must be a <DOC/>.
    docElem.setLocalName("DOC");

    // Add empty id and type attributes to the <DOC>.
    docElem.addAttribute(new Attribute("id", Integer.toString(docCounter++)));
    docElem.addAttribute(new Attribute("type", "NONE"));

    // Add an empty id attribute to each sentence.
    Elements sents = docElem.getFirstChildElement("sentences").getChildElements("sentence");
    for (int i = 0; i < sents.size(); i++) {
      Element thisSent = sents.get(i);
      thisSent.addAttribute(new Attribute("id", Integer.toString(i)));
    }

    // rename coreference parent tag to "coreferences"
    Element corefElem = docElem.getFirstChildElement("coreference");
    // because StanfordCoreNLP.annotationToDoc() only appends the coref
    // element if it is nonempty (per Ben's request)
    if (corefElem == null) {
      Element corefInfo = new Element("coreferences", null);
      docElem.appendChild(corefInfo);
    } else {
      corefElem.setLocalName("coreferences");
    }

    for (CoreMap sentence : anno.get(SentencesAnnotation.class)) {
      try {
        fillInParseAnnotations(false, sentence, sentence.get(TreeAnnotation.class));
      } catch (Exception e) {
        logger.warn("In corefToXML. Error filling in parse annotation for sentence " + sentence);
      }
    }

    return xmlDoc;
  }

  /**
   * NOTICE: Copied from edu.jhu.annotation.GigawordAnnotator.
   * 
   * add dependency relations to the XML. adapted from StanfordCoreNLP to add root dependency and change format
   * 
   * @param semGraph
   *          the dependency graph
   * @param parentElem
   *          the element to attach dependency info to
   */
  public static void addDependencyToXML(SemanticGraph semGraph, Element parentElem) {
    if (semGraph != null && semGraph.edgeCount() > 0) {
      Element rootElem = new Element("dep");
      rootElem.addAttribute(new Attribute("type", "root"));
      Element rootGovElem = new Element("governor");
      rootGovElem.appendChild("0");
      rootElem.appendChild(rootGovElem);
      // need to surround this in a try/catch in case there is no
      // root in the dependency graph
      try {
        String rootIndex = Integer.toString(semGraph.getFirstRoot().get(IndexAnnotation.class));
        Element rootDepElem = new Element("dependent");
        rootDepElem.appendChild(rootIndex);
        rootElem.appendChild(rootDepElem);
        parentElem.appendChild(rootElem);
      } catch (Exception e) {
      }
      for (SemanticGraphEdge edge : semGraph.edgeListSorted()) {
        String rel = edge.getRelation().toString();
        rel = rel.replaceAll("\\s+", "");
        int source = edge.getSource().index();
        int target = edge.getTarget().index();

        Element depElem = new Element("dep");
        depElem.addAttribute(new Attribute("type", rel));

        Element govElem = new Element("governor");
        govElem.appendChild(Integer.toString(source));
        depElem.appendChild(govElem);

        Element dependElem = new Element("dependent");
        dependElem.appendChild(Integer.toString(target));
        depElem.appendChild(dependElem);

        parentElem.appendChild(depElem);
      }
    }
  }

  // return various annotators from the CoreNLP tools
  public Annotator nerAnnotator() {
    return StanfordCoreNLP.getExistingAnnotator("ner");
  }

  public Annotator dcorefAnnotator() {
    return StanfordCoreNLP.getExistingAnnotator("dcoref");
  }

}
