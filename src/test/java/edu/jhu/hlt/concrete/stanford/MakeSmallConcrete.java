package edu.jhu.hlt.concrete.stanford;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

//public class MakeSmallConcrete {
//
//  public static void main(String[] args) throws Exception {
//
//    if (args.length != 2) {
//      System.err.println("Usage: MakeSmallConcrete <path/to/input/file> <path/to/output/file>");
//      System.exit(1);
//    }
//
//    File input = new File(args[0]);
//    ProtocolBufferWriter output = new ProtocolBufferWriter(args[1]);
//    parseFile(input, output);
//    output.close();
//  }
//
//  private static void parseFile(File inputFile, ProtocolBufferWriter output) throws IOException {
//    String docId = "testDocId";
//    UUID uuid = IdUtil.generateUUID();
//    CommunicationGUID guid = ProtoFactory.generateCommGuid("stanford-test-file", "0");
//    Communication.Builder communication = Communication.newBuilder().setUuid(uuid).setGuid(guid).setKind(Communication.Kind.NEWS);
//
//    AnnotationMetadata.Builder metadata = AnnotationMetadata.newBuilder();
//    metadata.setTool("Concrete-Stanford Tester 1.0");
//    metadata.setTimestamp(inputFile.lastModified());
//
//    // Write out the tokenization for the file
//    SectionSegmentation.Builder sectionSegmentation = communication.addSectionSegmentationBuilder().setUuid(IdUtil.generateUUID());
//    sectionSegmentation.setMetadata(metadata);
//
//    Section.Builder section = sectionSegmentation.addSectionBuilder().setUuid(IdUtil.generateUUID());
//    section.setKind(Section.Kind.PASSAGE);
//    SentenceSegmentation.Builder sentenceSegmentation = section.addSentenceSegmentationBuilder().setUuid(IdUtil.generateUUID());
//    sentenceSegmentation.setMetadata(metadata);
//
//    BufferedReader br = new BufferedReader(new FileReader(inputFile));
//
//    String currline = null;
//    ArrayList<String> lines = new ArrayList<String>();
//    StringBuilder sb = new StringBuilder();
//    while ((currline = br.readLine()) != null) {
//      lines.add(currline);
//      sb.append(currline);
//    }
//    // duplicate it for later on...
//    communication.setText(sb.toString() + sb.toString());
//    int curr_position = 0;
//    for (String line : lines) {
//      Concrete.Sentence.Builder sentenceBuilder = sentenceSegmentation.addSentenceBuilder().setUuid(IdUtil.generateUUID());
//      Concrete.TextSpan.Builder textSpan = Concrete.TextSpan.newBuilder().setStart(curr_position);
//      curr_position += line.length();
//      textSpan.setEnd(curr_position);
//      sentenceBuilder.setTextSpan(textSpan);
//    }
//
//    // now write a duplicate message, where every two sections are grouped together
//    int sectnum = 0;
//    int linesInSect = 0;
//    for (String line : lines) {
//      Section.Builder sect = sectionSegmentation.addSectionBuilder().setUuid(IdUtil.generateUUID());
//      sect.setKind(Section.Kind.PASSAGE);
//      sect.addNumber(sectnum).addNumber(linesInSect++);
//      SentenceSegmentation.Builder sentSeg = sect.addSentenceSegmentationBuilder().setUuid(IdUtil.generateUUID());
//      sentSeg.setMetadata(metadata);
//
//      Concrete.Sentence.Builder sentBuilder = sentSeg.addSentenceBuilder().setUuid(IdUtil.generateUUID());
//      Concrete.TextSpan.Builder textSpan = Concrete.TextSpan.newBuilder().setStart(curr_position);
//      curr_position += line.length();
//      textSpan.setEnd(curr_position);
//      sentBuilder.setTextSpan(textSpan);
//      if (linesInSect == 2) {
//        sectnum++;
//      }
//    }
//    Communication comm = communication.build();
//    output.write(comm);
//  }
//}
