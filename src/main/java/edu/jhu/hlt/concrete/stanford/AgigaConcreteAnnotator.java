package edu.jhu.hlt.concrete.stanford;

import edu.jhu.hlt.concrete.*;
import edu.jhu.hlt.concrete.util.*;
import edu.jhu.hlt.concrete.agiga.AgigaConverter;
import edu.jhu.agiga.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * given a Communication (with Sections and Sentences added)
 * and Stanford's annotations via an AgigaDocument,
 * add these annotations and return a new Communication
 */
public class AgigaConcreteAnnotator {

    private boolean debug = false;
    private long timestamp;

    public AgigaConcreteAnnotator(){
    }
    public AgigaConcreteAnnotator(boolean debug){
        this.debug=debug;
    }

    private AnnotationMetadata metadata() {
        return new AnnotationMetadata().setTool("anno-pipeline-v2").setTimestamp(timestamp);
    }
		
    //private Communication comm;
    private String sectionSegmentationId;
    private List<String> sectionIds;
    private AgigaDocument agigaDoc;
    private int agigaSentPtr = -1;
    private int sectionPtr = -1;
    // need to reference this in building corefs
    private List<Tokenization> tokenizations;

    public synchronized void convertCommunication(Communication comm,
                                                  String sectionSegmentationId,
                                                  String sectionId, // relevant sections (look inside for #sentences)
                                                  AgigaDocument agigaDoc) {

        if(sectionIds.size() == 0) {
            System.err.println("WARNING: calling annotate with no sections specified!");
        }
        if(debug){
            System.err.println("[AgigaConcreteAnnotator debug]");
            System.err.println("sectionSegmentationId = " + sectionSegmentationId);
        }
        this.timestamp = Calendar.getInstance().getTimeInMillis() / 1000;
        this.sectionSegmentationId = sectionSegmentationId;
        this.sectionIds = sectionIds;
        this.agigaDoc = agigaDoc;
        this.agigaSentPtr = 0;
        this.sectionPtr = 0;
        this.tokenizations = new ArrayList<Tokenization>();
        flushCommunication(comm, false);
    }

    public synchronized void convertSection(Section section, AgigaDocument agigaDoc, List<Tokenization> tokenizations) {
        this.timestamp = Calendar.getInstance().getTimeInMillis() / 1000;
        addSentenceSegmentation(section, agigaDoc, tokenizations);
    }
	
    // get appropriate section segmentation, and change it
    private void flushCommunication(Communication in, boolean transferCorefs) {
        SectionSegmentation newSS = null;
        int remove = -1;
        int n = in.getSectionSegmentationsSize();
        for(int i=0; i<n; i++) {
            SectionSegmentation ss = in.getSectionSegmentations().get(i);
            if(ss.getUuid().equals(this.sectionSegmentationId)) {
                remove = i;
                flushSections(ss);
            }
            break;
        }
        if(remove < 0) throw new RuntimeException("couldn't find SectionSegmentation with String=" + this.sectionSegmentationId);
        if(this.tokenizations.size() != this.agigaDoc.getSents().size()) {
            throw new RuntimeException("#agigaSents=" + agigaDoc.getSents().size() + ", #tokenizations=" + tokenizations.size());
        }
    }

    public void addCorefs(Communication in, List<Tokenization> tokenizations){
        EntityMentionSet ems = new EntityMentionSet().setUuid(UUIDGenerator.make()).setMetadata(metadata());
        List<Entity> elist = new LinkedList<Entity>();
        for(AgigaCoref coref : this.agigaDoc.getCorefs()) {
            Entity e = AgigaConverter.convertCoref(ems, coref, this.agigaDoc, tokenizations);
            elist.add(e);
        }
        in.addToEntityMentionSets(ems);
        in.addToEntitySets(new EntitySet().setUuid(UUIDGenerator.make()).setMetadata(metadata()).setEntityList(elist));
    }

    // given a particular section segmentation, add information to appropriate sections
    private void flushSections(SectionSegmentation in) {
        int n = in.getSectionListSize();
        assert n > 0 : "n="+n;

        // add them from source
        String target = this.sectionIds.get(this.sectionPtr);
        if(debug)
            System.err.println("[f2] target=" + target);
        for(Section section : in.getSectionList()) {
            if(debug)
                System.err.printf("sectionPtr=%d sect.uuid=%s\n", sectionPtr, section.getUuid());
            if(section.getUuid().equals(target)) {
                addSentenceSegmentation(section);
                this.sectionPtr++;
                if(debug) {
                    target = this.sectionPtr < this.sectionIds.size()
                        ? this.sectionIds.get(this.sectionPtr)
                        : null;
                    System.err.println("[f2] target=" + target);
                }
            }
        }
        if(this.sectionPtr != this.sectionIds.size())
            throw new RuntimeException(String.format("found %d of %d sections", this.sectionPtr, this.sectionIds.size()));
    }

    // add SentenceSegmentation to the section
    private void addSentenceSegmentation(Section in, AgigaDocument ad, List<Tokenization> tokenizations) {
        if(debug)
            System.err.println("f3");
        //create a sentence segmentation
        SentenceSegmentation ss = new SentenceSegmentation().setUuid(UUIDGenerator.make()).setMetadata(metadata());
        addSentences(ss, ad, tokenizations);
        in.addToSentenceSegmentation(ss);
    }
    private void addSentenceSegmentation(Section in) {
        addSentenceSegmentation(in, this.agigaDoc, this.tokenizations);
    }

    // add all Sentences
    private void addSentences(SentenceSegmentation in, AgigaDocument ad, List<Tokenization> tokenizations) {
        if(debug) System.err.println("f4");
        int n = ad.getSents().size();
        int[] arr = new int[]{0};
        assert n > 0 : "n=" + n;
        for(int i = 0; i < n; i++) in.addToSentenceList(createTokenization(ad, arr, tokenizations));
    }
    private void addSentences(SentenceSegmentation in) {
        addSentences(in, this.agigaDoc, this.tokenizations);
    }


    // add a Tokenization
    private Sentence createTokenization(AgigaDocument ad, int[] sentPtr, List<Tokenization> tokenizations) {
        if(debug) System.err.println("f5");
        AgigaSentence asent = ad.getSents().get(sentPtr[0]++);
        // tokenization has all the annotations
        Tokenization tok = AgigaConverter.convertTokenization(asent);	
        tokenizations.add(tok);
        Sentence newS = new Sentence().setUuid(UUIDGenerator.make());
        newS.addToTokenizationList(tok);
        return newS;
    }
    private Sentence createTokenization(){
        return createTokenization(this.agigaDoc, new int[]{this.agigaSentPtr}, this.tokenizations );
    }
}

