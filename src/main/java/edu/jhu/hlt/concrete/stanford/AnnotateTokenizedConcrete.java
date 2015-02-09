package edu.jhu.hlt.concrete.stanford;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
//import java.util.Properties;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import concrete.tools.AnnotationException;
import edu.jhu.agiga.AgigaDocument;
import edu.jhu.agiga.AgigaSentence;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TextSpan;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.agiga.AgigaAnnotationAdder;
import edu.jhu.hlt.concrete.communications.SuperCommunication;
import edu.jhu.hlt.concrete.serialization.CommunicationSerializer;
import edu.jhu.hlt.concrete.serialization.CompactCommunicationSerializer;
import edu.jhu.hlt.concrete.util.ConcreteException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

/**
 * Given tokenized Concrete as input, this class will annotate sentences with the Stanford NLP tools and add the annotations back in their Concrete
 * representations.<br>
 * <br>
 * This class assumes that the input has been tokenized using a PTB-like tokenization. There is a known bug in the Stanford library which will throw an
 * exception when trying to perform semantic head finding on after parsing the sentence "( CROSSTALK )". The error will not occur given the input
 * "-LRB- CROSSTALK -RRB-".
 * 
 * @author mgormley
 */
public class AnnotateTokenizedConcrete {

  private static final Logger log = LoggerFactory.getLogger(AnnotateTokenizedConcrete.class);

  private InMemoryAnnoPipeline pipeline;
  private String language;

  private final static String[] ChineseSectionName = new String[] {"</TURN>", "</HEADLINE>", "</TEXT>", "</POST>"};
  private static Set<String> ChineseSectionNameSet = new HashSet<String>(Arrays.asList(ChineseSectionName));

  public AnnotateTokenizedConcrete(String lang) {
    log.info("Loading models for Stanford tools");
    language = lang;
    pipeline = new InMemoryAnnoPipeline(lang);
  }

  /**
   * Annotates a Concrete {@link Communication} with the Stanford NLP tools.<br>
   * <br>
   * NOTE: Currently, this only supports per-sentence annotation. Coreference resolution is not performed.
   * 
   * @param comm
   *          The concrete communication.
   */
  public void annotateWithStanfordNlp(Communication comm) {
    for (Section cSection : comm.getSectionList()) {
      if (cSection.isSetLabel() && !ChineseSectionNameSet.contains(cSection.getLabel()) ) 
	      continue;
      Annotation sSectionAnno = getSectionAsAnnotation(cSection, comm);
      try {
        // Run the in-memory anno pipeline to (1) create Stanford objects,
        // (2) convert them to XML, and (3) read that XML into AGiga API objects.
        AgigaDocument aDoc = pipeline.annotate(sSectionAnno);
        // Convert the AgigaDocument with annotations for this section
        // to annotations on this section.
        String[] annotationList = {"pos", "cparse", "dparse"};
        AgigaAnnotationAdder aaa = new AgigaAnnotationAdder(language);
        aaa.addAgigaAnnosToSection(aDoc, cSection, annotationList);
      } catch (IOException e) {
        throw new RuntimeException(e);
      } catch (AnnotationException e) {
        throw new RuntimeException(e);
      } catch(Exception e) {
        log.error(e.toString());
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }
  }


  /**
   * Annotates a Concrete {@link Sentence} with the Stanford NLP tools.
   * 
   * @param cSent
   *          The concrete sentence.
   * @param comm
   *          The communication from which to extract the source text.
   */
  public void annotateWithStanfordNlp(Sentence cSent, Communication comm) {
    Annotation sSentAnno = getSentenceAsAnnotation(cSent, comm);
    try {
      // Run the in-memory anno pipeline to (1) create Stanford objects,
      // (2) convert them to XML, and (3) read that XML into AGiga API objects.
      AgigaDocument aDoc = pipeline.annotate(sSentAnno);
      if (aDoc.getSents().size() != 1) {
        throw new IllegalStateException("Multiple sentences in AgigaDoc which should contain only 1.");
      }
      AgigaSentence aSent = aDoc.getSents().get(0);
      // Convert the AgigaSentence with annotations for this sentence
      // to annotations on this sentence.
      String[] annotationList = {"pos", "cparse", "dparse"};
      AgigaAnnotationAdder aaa = new AgigaAnnotationAdder(language);
      aaa.addAgigaAnnosToConcreteSent(aSent, cSent, annotationList);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Converts a Concrete {@link Section} to a Stanford {@link Annotation}.
   * 
   * @param cSection
   *          The concrete section.
   * @param comm
   *          The communication from which to extract the source text.
   * @return The annotation representing the section.
   */
  private Annotation getSectionAsAnnotation(Section cSection, Communication comm) {
    //System.out.println("In getSectionAsAnnotation function!!!!!");
    List<Sentence> cSents = cSection.getSentenceList();
    return concreteSentListToAnnotation(cSents, comm);
  }

  /**
   * Converts a Concrete {@link Sentence} to a Stanford {@link Annotation}.
   * 
   * @param cSection
   *          The concrete sentence.
   * @param comm
   *          The communication from which to extract the source text.
   * @return The annotation representing the section.
   */
  private Annotation getSentenceAsAnnotation(Sentence cSent, Communication comm) {
    //System.out.println("In getSentenceAsAnnotation function!!!!!");
    List<Sentence> cSents = new ArrayList<>();
    cSents.add(cSent);
    return concreteSentListToAnnotation(cSents, comm);
  }

  /**
   * Converts a {@link List} of Concrete {@link Sentence} to a Stanford {@link Annotation}.
   * 
   * @param cSents
   *          The list of concrete sentences.
   * @param comm
   *          The communication from which to extract the source text.
   * @return The annotation representing the list of sentences.
   */
  private Annotation concreteSentListToAnnotation(List<Sentence> cSents, Communication comm) {
    //System.out.println("In concreteSentListToAnnotation function!!!!!");
    Annotation sSectionAnno = new Annotation(comm.getText());
    // Done by constructor: sectionAnno.set(CoreAnnotations.TextAnnotation, null);

    List<CoreLabel> sToks = new ArrayList<>();
    List<List<CoreLabel>> sSents = new ArrayList<>();
    for (Sentence cSent : cSents) {
      List<CoreLabel> sSent = concreteSentToCoreLabels(cSent, comm);
      sToks.addAll(sSent);
      sSents.add(sSent);
    }

    List<CoreMap> sentences = mimicWordsToSentsAnnotator(sSents, comm.getText());

    log.info("The tokenlist = "+ sToks);
    //log.info("The sentencelist = " + sentences);
    sSectionAnno.set(CoreAnnotations.TokensAnnotation.class, sToks);
    sSectionAnno.set(CoreAnnotations.SentencesAnnotation.class, sentences);
    return sSectionAnno;
  }

  /**
   * Converts a Concrete {@link Sentence} to a {@link List} of {@Link CoreLabel}s representing each token.
   * 
   * @param cSent
   *          The concrete sentence.
   * @param comm
   *          The communication from which to extract the source text.
   * @return The list of core labels.
   */
  private List<CoreLabel> concreteSentToCoreLabels(Sentence cSent, Communication comm) {
    CoreLabelTokenFactory coreLabelTokenFactory = new CoreLabelTokenFactory();
    List<CoreLabel> sSent = new ArrayList<>();
    Tokenization cToks = cSent.getTokenization();
    for (Token cTok : cToks.getTokenList().getTokenList()) {
      TextSpan cSpan = cTok.getTextSpan();
      String text = cTok.getText();
      int length = cSpan.getEnding() - cSpan.getStart();
      CoreLabel sTok = coreLabelTokenFactory.makeToken(text, comm.getText(), cSpan.getStart(), length);
      sSent.add(sTok);
    }
    if (log.isDebugEnabled()) {
      StringBuilder sb = new StringBuilder();
      for (CoreLabel sTok : sSent) {
        sb.append(sTok.word());
        sb.append(" ");
      }
      log.debug("Converted sentence: " + sb.toString());
    }
    return sSent;
  }

  /**
   * This method mimics the behavior of Stanford's WordsToSentencesAnnotator to create a List<CoreMap>s from a List<List<CoreLabel>>.
   */
  private List<CoreMap> mimicWordsToSentsAnnotator(List<List<CoreLabel>> sSents, String text) {
    int tokenOffset = 0;
    List<CoreMap> sentences = new ArrayList<CoreMap>();
    for (List<CoreLabel> sentenceTokens : sSents) {
      if (sentenceTokens.isEmpty()) {
        throw new RuntimeException("unexpected empty sentence: " + sentenceTokens);
      }

      // get the sentence text from the first and last character offsets
      int begin = sentenceTokens.get(0).get(CharacterOffsetBeginAnnotation.class);
      int last = sentenceTokens.size() - 1;
      int end = sentenceTokens.get(last).get(CharacterOffsetEndAnnotation.class);
      String sentenceText = "";
      if (language.equals("en")) {
	     sentenceText  = text.substring(begin, end);
      }
      else if (language.equals("cn")) { 
	     StringBuilder sb = new StringBuilder();
	     int cnt = 0;
	     for (CoreLabel token: sentenceTokens) {
		if (cnt != 0)
			sb.append(" ");
	     	sb.append(token.word());
		cnt ++ ;
	     }
	     sentenceText = sb.toString();
      }
      else {
	System.err.println("Do not support language "+language);
      	System.exit(1);
      }
      // create a sentence annotation with text and token offsets
      Annotation sentence = new Annotation(sentenceText);
      sentence.set(CharacterOffsetBeginAnnotation.class, begin);
      sentence.set(CharacterOffsetEndAnnotation.class, end);
      sentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);
      sentence.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffset);
      tokenOffset += sentenceTokens.size();
      sentence.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffset);

      // add the sentence to the list
      sentences.add(sentence);
    }
    return sentences;
  }

  
  public static FileSystem getNewZipFileSystem(Path zipFile) throws IOException {
    if (Files.exists(zipFile)) {
      Files.delete(zipFile);
    }
    URI uri = URI.create("jar:file:" + zipFile.toUri().getPath());
    Map<String, String> env = new HashMap<>();
    env.put("create", "true");
    return FileSystems.newFileSystem(uri, env);
  }

  
  public static void main(String[] args) throws IOException, TException, ConcreteException {
    Path inFile = Paths.get(args[0]);
    Path outFile = Paths.get(args[1]);

    CommunicationSerializer cs = new CompactCommunicationSerializer();
    AnnotateTokenizedConcrete annotator = new AnnotateTokenizedConcrete("cn");
    
    //StanfordCoreNLP cpipeline = annotator.makeChinesePipeline();
    try (FileSystem zipfs = getNewZipFileSystem(outFile)) {
      try (ZipFile zf = new ZipFile(inFile.toFile())) {
        Enumeration<? extends ZipEntry> e = zf.entries();
        while (e.hasMoreElements()) {
          ZipEntry ze = e.nextElement();
          log.info("Annotating communication: " + ze.getName());
          final Communication comm = cs.fromInputStream(zf.getInputStream(ze));
          annotator.annotateWithStanfordNlp(comm);
          new SuperCommunication(comm).writeToFile(zipfs.getPath(ze.getName()), true);
        }
      }
    }
  }

}
