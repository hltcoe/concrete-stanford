package edu.jhu.hlt.concrete.stanford;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.jhu.agiga.*;
import edu.jhu.hlt.concrete.*;
import edu.jhu.hlt.concrete.util.ThriftIO;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

import org.apache.thrift.TException;

public class StanfordAgigaPipe {
    static final String usage = "You must specify an input path: java edu.jhu.hlt.concrete.stanford.StanfordAgigaPipe --input path/to/input/file --output path/to/output/file\n"
        + "  Optional arguments: \n"
        + "       --annotate-sections <comma-separated-list of type names> (default: PASSAGE)\n"
        + "       --debug\n\t\tto print debugging messages (default: false)\n";
	
    private boolean debug = false;
    private int sentenceCount = 1; // for flat files, no document structure

    private boolean aggregateSectionsByFirst = false;
    private boolean tokenize = true;
    private boolean parse = false;

    private String inputFile = null;
    private String outputFile = null;
    private InMemoryAnnoPipeline pipeline;

    private Set<SectionKind> annotateNames;
    
    private int charOffset = 0;
    private int globalTokenOffset = 0;

    public static void main(String[] args) throws TException, IOException{
        StanfordAgigaPipe sap = new StanfordAgigaPipe(args);
        sap.go();
    }

    public StanfordAgigaPipe(String[] args) {
        annotateNames = new HashSet<SectionKind>();
        parseArgs(args);
        if(inputFile == null || outputFile==null){
            System.err.println(usage);
            System.exit(1);
        }
        pipeline = new InMemoryAnnoPipeline();
    }

    public void parseArgs(String[] args){
        int i = 0;
        boolean userAddedAnnotations = false;
        try {
            while (i < args.length) {
                if(args[i].equals("--annotate-sections")){
                    String[] toanno =args[++i].split(",");
                    for(String t : toanno) annotateNames.add(SectionKind.valueOf(t));
                    userAddedAnnotations = true;
                }
                // if(args[i].equals("--aggregate-by-first-section-number"))
                //     aggregateSectionsByFirst = args[++i].equals("t");
                else if (args[i].equals("--debug")) debug = true;
                else if (args[i].equals("--input")) inputFile = args[++i];
                else if (args[i].equals("--output")) outputFile = args[++i];
                else{
                    System.err.println("Invalid option: " + args[i]);
                    System.err.println(usage);
                    System.exit(1);
                } 
                i++;
            }
        } catch (Exception e) {
            System.err.println(usage);
            System.exit(1);
        }
        if(!userAddedAnnotations) annotateNames.add(SectionKind.PASSAGE);
    }

    public void go() throws TException, IOException{        
        Communication communication = ThriftIO.readFile(inputFile);
        runPipelineOnCommunicationSectionsAndSentences(communication);
        if(debug) System.err.println(communication);
        writeCommunication(communication);
    }

    /**
     * Construct a dummy Annotation object that will serve as an aggregator.
     * The properties SentencesAnnotation.class and TokensAnnotation.class are 
     * initialized with lists of CoreMap and CoreLabel objects, respectively.
     */
    private Annotation getSeededDocumentAnnotation(){
        Annotation documentAnnotation = new Annotation((String)null);
        documentAnnotation.set(SentencesAnnotation.class, new ArrayList<CoreMap>());
        documentAnnotation.set(TokensAnnotation.class, new ArrayList<CoreLabel>());
        return documentAnnotation;
    }

    /**
     * This steps through the given communication. For each section segmentation, it will
     * go through each of the sections, first doing what localized processing it can (i.e., 
     * all but coref resolution), and then doing the global processing (coref). 
     */
    public void runPipelineOnCommunicationSectionsAndSentences(Communication comm) {
        if (!comm.isSetText())
            throw new IllegalArgumentException("Expecting Communication Text.");
        if (comm.getSectionSegmentations().size() == 0)
            throw new IllegalArgumentException("Expecting Communication SectionSegmentations.");

        //if called multiple times, reset the sentence count
        sentenceCount = 1;
		
        String commText = comm.getText();
        List<Annotation> finishedAnnotations = new ArrayList<Annotation>();

        for(SectionSegmentation sectionSegmentation : comm.getSectionSegmentations()){
            //TODO: get section and sentence segmentation info from metadata
            List<Section> sections = sectionSegmentation.getSectionList();
            List<String> sectionUUIDs = new ArrayList<String>();
            List<Integer> numberOfSentences = new ArrayList<Integer>();
            List<Tokenization> tokenizations = new ArrayList<Tokenization>();
            Annotation documentAnnotation = getSeededDocumentAnnotation();
            String sectionSegmentationUUID = sectionSegmentation.getUuid();
            for (Section section : sections) {
                TextSpan sts= section.getTextSpan();
                // 1) First *perform* the tokenization & sentence splits
                //    Note we do this first, even before checking the content-type
                String sectionText = commText.substring(sts.getStart(), sts.getEnding());
                Annotation a = pipeline.splitAndTokenizeText(sectionText);
                if((section.isSetKind() && !annotateNames.contains(section.getKind()) ) || 
                   section.getSentenceSegmentation().size() == 0) {
                    //We MUST update the character offset
                    charOffset += sectionText.length();
                    //NOTE: It's possible we want to account for sentences in non-contentful sections
                    //If that's the case, then we need to update the globalToken and sentence offset
                    //variables correctly.
                    if(a == null) continue;

                    // List<CoreLabel> sentTokens = a.get(TokensAnnotation.class);
                    // int tokenEnd = tokenOffset + sentTokens.size();
                    // sentAnno.set(SentenceIndexAnnotation.class, sentIndex);
                    // sentIndex++;
                    // sentenceCount++;
                    // tokenOffset = tokenEnd;
                    // document.set(TokensAnnotation.class, docTokens);

                    continue;
                }
                sectionUUIDs.add(section.getUuid());
                // 2) Second, perform the other localized processing
                processSection(section, a, documentAnnotation, tokenizations);
            }
            // 3) Third, do coref; cross-reference against sectionUUIDs
        }
    }

    /**
     * On a given annotation object representing a single section, run 
     * <ul>
     * <li> part-of-speech tagging, </li>
     * <li> lemmatization, </li>
     * <li> constituency and dependency parsing, and </li>
     * <li> named entity recognition.</li>
     * </ul>
     * Note that corefence resolution is done only once all contentful
     * sections have been properly annotated.
     *
     */
    public AgigaDocument annotate(Annotation annotation) {
        try {
            return pipeline.annotate(annotation);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Ensures that there is actually something to process for 
     * the current section. Note, this will not halt computation: 
     * it will only produce a warning message.
     */
    private void validateSectionBuffer(List<CoreMap> sectionBuffer){
        //first cat all CoreMap objects in sectionBuffer into one
        if(sectionBuffer==null || sectionBuffer.size()==0){
            System.err.println("no sentences found on this invocation");
        }
        if(debug){
            System.err.println("CALL TO PROCESS");
            System.err.println("\tsectionBuffer.size = " + sectionBuffer.size());
        }
    }
	
    /**
     * Convert tokenized sentences (<code>sentAnno</code>) into a document Annotation.<br/>
     *
     * @param sentences
     * @return
     */
    public void sentencesToSection(CoreMap sentAnno, Annotation document) {
        if (sentAnno == null) {
            System.err.println("encountering null sentAnno");
            return;
        }
        String docText = null;

        List<CoreMap> docSents = document.get(SentencesAnnotation.class);
        docSents.add(sentAnno);
        document.set(SentencesAnnotation.class, docSents);
        
        List<CoreLabel> docTokens = document.get(TokensAnnotation.class);
        System.err.println("converting list of CoreMap sentences to Annotations, starting at token offset " + globalTokenOffset);
         
        List<CoreLabel> sentTokens = sentAnno.get(TokensAnnotation.class);
        docTokens.addAll(sentTokens);
        int tokenEnd = globalTokenOffset + sentTokens.size();
        sentAnno.set(TokenBeginAnnotation.class, globalTokenOffset);
        sentAnno.set(TokenEndAnnotation.class, tokenEnd);
        sentAnno.set(SentenceIndexAnnotation.class, sentenceCount++);
        globalTokenOffset = tokenEnd;
        document.set(TokensAnnotation.class, docTokens);
        
        for (CoreLabel token : docTokens) {
            //note that character offsets are global
            String tokenText = token.get(TextAnnotation.class);
            token.set(CharacterOffsetBeginAnnotation.class, charOffset);
            charOffset += tokenText.length();
            token.set(CharacterOffsetEndAnnotation.class, charOffset);
            charOffset++; // Skip space
        }
        List<CoreLabel> sentenceTokens = sentAnno.get(TokensAnnotation.class);
        sentAnno.set(CharacterOffsetBeginAnnotation.class,
                     sentenceTokens.get(0).get(CharacterOffsetBeginAnnotation.class));
        sentAnno.set(CharacterOffsetEndAnnotation.class,
                     sentenceTokens.get(sentenceTokens.size() - 1).get(CharacterOffsetEndAnnotation.class));

    }

    /**
     * Given a particular section ({@code section}) from a communication,
     * further locally process {@code sentenceSplitText}; add those new 
     * annotations to an aggregating {@code docAnnotation} to use for 
     * later global processing.
     */
    public void processSection(Section section,
                               Annotation sentenceSplitText,
                               Annotation docAnnotation,
                               List<Tokenization> tokenizations){
        ////proposed
        // validateSectionBuffer(sectionBuffer);
        // Annotation annotation = sentencesToSection(sectionBuffer);
        // annotation = annotate(annotation);
        // pushAnnotationsToSection(annotation, section);

        sentencesToSection(sentenceSplitText, docAnnotation);
        AgigaDocument agigaDoc = annotate(sentenceSplitText);
        AgigaConcreteAnnotator agigaToConcrete = new AgigaConcreteAnnotator(debug);
        agigaToConcrete.convertSection(section, agigaDoc, tokenizations);
    }
	
    /* This method contains code for transforming lists of Stanford's CoreLabels into 
     * Concrete tokenizations.  We might want to use it if we get rid of agiga. 
     */
    // private Tokenization coreLabelsToTokenization(List<CoreLabel> coreLabels) {		
    // 	List<Token> tokens = new ArrayList<Token>();
    // 	List<TaggedToken> lemmas = new ArrayList<TaggedToken>();
    // 	List<TaggedToken> nerTags = new ArrayList<TaggedToken>();
    // 	List<TaggedToken> posTags = new ArrayList<TaggedToken>();
		
    // 	int tokenId = 0;
    // 	for (CoreLabel coreLabel : coreLabels) {
    // 	    if (coreLabel.lemma() != null) {
    // 		lemmas.add(
    // 			   TaggedToken.newBuilder()
    // 			   .setTag(coreLabel.lemma())
    // 			   .setTokenId(tokenId)
    // 			   .build()
    // 			   );
    // 	    }
			
    // 	    if (coreLabel.ner() != null) {
    // 		nerTags.add(
    // 			    TaggedToken.newBuilder()
    // 			    .setTag(coreLabel.ner())
    // 			    .setTokenId(tokenId)
    // 			    .build()
    // 			    );					
    // 	    }
			
    // 	    if (coreLabel.tag() != null) {
    // 		posTags.add(
    // 			    TaggedToken.newBuilder()
    // 			    .setTag(coreLabel.tag())
    // 			    .setTokenId(tokenId)
    // 			    .build()
    // 			    );						
    // 	    }
			
    // 	    tokens.add(
    // 		       Token.newBuilder()
    // 		       .setTokenId(tokenId)
    // 		       .setTextSpan(
    // 				    TextSpan.newBuilder()
    // 				    .setStart(coreLabel.beginPosition())
    // 				    .setEnd(coreLabel.endPosition())
    // 				    .build()
    // 				    )
    // 		       .setText(coreLabel.value())
    // 		       .build()
    // 		       );
			
    // 	    tokenId++;
    // 	}
		
    // 	Tokenization tokenization = Tokenization.newBuilder()
    // 	    .setUuid(IdUtil.generateUUID())
    // 	    .setKind(Kind.TOKEN_LIST)
    // 	    .addPosTags(
    // 			TokenTagging.newBuilder()
    // 			.setUuid(IdUtil.generateUUID())
    // 			.addAllTaggedToken(posTags)
    // 			.build()
    // 			)
    // 	    .addNerTags(
    // 			TokenTagging.newBuilder()
    // 			.setUuid(IdUtil.generateUUID())
    // 			.addAllTaggedToken(nerTags)
    // 			.build()
    // 			)
    // 	    .addLemmas(
    // 		       TokenTagging.newBuilder()
    // 		       .setUuid(IdUtil.generateUUID())
    // 		       .addAllTaggedToken(lemmas)
    // 		       .build()
    // 		       )
    // 	    .addAllToken(tokens)
    // 	    .build();
		
    // 	return tokenization;
    // }



    //TODO: NOT YET IMPLEMENTED
    public void writeCommunication(Communication communication){
        throw new RuntimeException("Not yet implemented :( ");
    }


    /**
     * convert a tree t to its token representation
     * 
     * @param t
     * @return
     * @throws IOException
     */
    protected String getText(Tree t) throws IOException {
        StringBuffer sb = new StringBuffer();
        if (t == null) {
            return null;
        }
        for (Tree tt : t.getLeaves()) {
            sb.append(tt.value());
            sb.append(" ");
        }
        return sb.toString().trim();
    }
}
