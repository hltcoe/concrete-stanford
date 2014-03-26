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

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.SectionSegmentation;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.SectionKind;
import edu.jhu.hlt.concrete.SentenceSegmentation;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.util.Serialization;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;


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

    private Set<String> annotateNames;
    
    private int charOffset = 0;
    private int tokenOffset = 0;
    private int wordOffset = 0;

    public static void main(String[] args) throws IOException{
        StanfordAgigaPipe sap = new StanfordAgigaPipe(args);
        sap.go();
    }

    public StanfordAgigaPipe(String[] args) {
        annotateNames = new HashSet<String>();
        parseArgs(args);
        if(inputFile == null || outputFile==null){
            System.err.println(usage);
            System.exit(1);
        }
        // try {
        //     //TODO
        //     tbr = new ProtocolBufferReader(inputFile, Concrete.Communication.class);
        // } catch(Exception e){
        //     System.err.println("Trouble reading in theory file " + inputFile);
        //     System.err.println(e.getMessage());
        //     System.exit(1);
        // }
        // try {
        //     //TODO
        //     tbw = new ProtocolBufferWriter(outputFile);
        // } catch(Exception e){
        //     System.err.println("Trouble opening new output Thrift file " + outputFile);
        //     System.err.println(e.getMessage());
        //     System.exit(1);
        // } 
        pipeline = new InMemoryAnnoPipeline();
    }

    public void parseArgs(String[] args){
        int i = 0;
        boolean userAddedAnnotations = false;
        try {
            while (i < args.length) {
                if(args[i].equals("--annotate-sections")){
                    String toanno =args[++i].split(",");
                    for(String t : toanno) annotateNames.add(t);
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
        if(!userAddedAnnotations) annotateNames.add("PASSAGE");
    }

    public void go() throws IOException{
        Communication communication = Serialization.fromBytes(Files.readAllBytes(Paths.get(inputFile)));
        runPipelineOnCommunicationSectionsAndSentences(comm);
        if(debug) System.err.println(comm);
        writeCommunication(comm);
    }

    //TODO: keep track of all coref-able sections
    private void runPipelineOnCommunicationSectionsAndSentences(Communication comm) {
        if (!comm.isSetText())
            throw new IllegalArgumentException("Expecting Communication Text.");
        if (comm.getSectionSegmentationCount() == 0)
            throw new IllegalArgumentException("Expecting Communication SectionSegmentations.");
		
        String commText = comm.getText();
        List<Annotation> finishedAnnotations = new ArrayList<Annotation>();
        sentenceCount = 1;
        int[] numberOfSentences = new int[];
        for(SectionSegmentation sectionSegmentation : comm.getSectionSegmentations()){
            //TODO: get section and sentence segmentation info from metadata
            List<Section> sections = sectionSegmentation.getSectionList();
            List<UUID> sectionUUIDs = new ArrayList<UUID>();
            UUID sectionSegmentationUUID = sectionSegmentation.getUuid();
            for (Section section : sections) {
                List<CoreMap> sectionBuffer = new ArrayList<CoreMap>();
                if ((section.isSetKind() && !annotateNames.contains(section.getKind()))
                    || section.getSentenceSegmentation().size() == 0) continue;
                sectionUUIDs.add(section.getUuid());
                TextSpan sts= section.getTextSpan();
                // 1) First *perform* the tokenization & sentence splits
                //    and add those results to the buffer
                sectionBuffer.add(pipeline.annotateSentence(commText.substr(sts.getStart(),
                                                                            sts.getEnd())));
                if(sectionBuffer.size() > 0)
                    process(section, sectionSegmentationUUID, sectionUUIDs, numberOfSentences, sectionBuffer);
            }
        }
        //Finally, run coref on all corefable sections
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
     * the current section.
     */
    private void validateSectionBuffer(List<CoreMap> sectionBuffer){
        //first cat all CoreMap objects in sectionBuffer into one
        if(sectionBuffer==null || sectionBuffer.size()==0){
            System.err.println("For communication " + commToAnnotate +", no sentences found on this invocation");
        }
        if(debug){
            System.err.println("CALL TO PROCESS");
            System.err.println("\tsectionBuffer.size = " + sectionBuffer.size());
        }
    }
	
    /**
     * Convert a list of tokenized sentences into a document Annotation.<br/>
     * If given no sentences, returns null.
     *
     * (Originally from anno-pipeline)
     * 
     * @param sentences
     * @return
     */
    public Annotation sentencesToSection(List<CoreMap> sentences) {
        if (sentences.size() == 0) {
            if (debug)
                System.err.println("0 sentences");
            return null;
        }
        String docText = null;
        Annotation document = new Annotation(docText);
        document.set(SentencesAnnotation.class, sentences);
        List<CoreLabel> docTokens = new ArrayList<CoreLabel>();
        int sentIndex = sentenceCount;
        System.err.println("converting list of CoreMap sentences to Annotations, starting at token offset " + tokenBegin);
        for (CoreMap sentAnno : sentences) {
            if (sentAnno == null) {
                System.err.println("encountering null sentAnno");
                continue;
            }
            List<CoreLabel> sentTokens = sentAnno.get(TokensAnnotation.class);
            docTokens.addAll(sentTokens);
            int tokenEnd = tokenOffset + sentTokens.size();
            sentAnno.set(TokenBeginAnnotation.class, tokenOffset);
            sentAnno.set(TokenEndAnnotation.class, tokenEnd);
            sentAnno.set(SentenceIndexAnnotation.class, sentIndex);
            sentIndex++;
            sentenceCount++;
            tokenOffset = tokenEnd;
        }
        document.set(TokensAnnotation.class, docTokens);
        for (CoreLabel token : docTokens) {
            String tokenText = token.get(TextAnnotation.class);
            token.set(CharacterOffsetBeginAnnotation.class, charOffset);
            charOffset += tokenText.length();
            token.set(CharacterOffsetEndAnnotation.class, charOffset);
            charOffset++; // Skip space
        }
        for (CoreMap sentenceAnnotation : sentences) {
            if (sentenceAnnotation == null) {
                continue;
            }
            List<CoreLabel> sentenceTokens = sentenceAnnotation.get(TokensAnnotation.class);
            sentenceAnnotation.set(CharacterOffsetBeginAnnotation.class,
                                   sentenceTokens.get(0).get(CharacterOffsetBeginAnnotation.class));
            sentenceAnnotation.set(CharacterOffsetEndAnnotation.class,
                                   sentenceTokens.get(sentenceTokens.size() - 1).get(CharacterOffsetEndAnnotation.class));
        }
        return document;
    }

    /**
     * WARNING: This has the side effects of clearing sectionUUIDs and sectionBuffer.
     * These two clears are imperative to this working correctly.
     */
    //Add all the annotations within the section buffer to section
    public void process(Section section,
                        UUID sectionSegmentationUUID,
                        List<UUID> sectionUUIDs,
                        int[] numberOfSentences,
                        List<CoreMap> sectionBuffer) {
        ////proposed
        // validateSectionBuffer(sectionBuffer);
        // Annotation annotation = sentencesToSection(sectionBuffer);
        // annotation = annotate(annotation);
        // pushAnnotationsToSection(annotation, section);

        validateSectionBuffer(sectionBuffer);
        Annotation annotation = sentencesToSection(sectionBuffer);
        AgigaDocument agigaDoc = annotate(annotation);
        AgigaConcreteAnnotator t = new AgigaConcreteAnnotator(debug);
        t.annotate(commToAnnotate, sectionSegmentationUUID, sectionUUIDs, numberOfSentences, agigaDoc);
        //FINALLY: clear the  lists
        sectionBuffer.clear(); 
        //sectionUUIDs.clear();
        //return newcomm;
    }


    private void pushAnnotationsToSection(Annotation annotation, Section section){
        
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
