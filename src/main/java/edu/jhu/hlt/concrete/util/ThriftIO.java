package edu.jhu.hlt.concrete.util;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;

import edu.jhu.hlt.concrete.Communication;

public class ThriftIO {

  public static void writeFile(String outputFile, Communication communication) throws IOException, TException {
    Path path = Paths.get(outputFile);
    writeFile(path, communication);
  }

  public static void writeFile(Path path, Communication communication) throws TException, IOException {
    byte[] bytez = new TSerializer(new TBinaryProtocol.Factory()).serialize(communication);
    Files.write(path, bytez, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
  }

  public static void writeFile(String outputFile, List<Communication> communications) throws IOException, TException {
    FileOutputStream fout = new FileOutputStream(outputFile);
    BufferedOutputStream bos = new BufferedOutputStream(fout);
    ZipOutputStream zout = new ZipOutputStream(bos);
    for (Communication communication : communications) {
      ZipEntry ze = new ZipEntry(communication.getId());
      zout.putNextEntry(ze);
      byte[] bytez = new TSerializer(new TBinaryProtocol.Factory()).serialize(communication);
      zout.write(bytez);
      zout.closeEntry();
    }
    zout.close();
    // Path path = (new File(outputFile)).toPath();
    // Files.write(path, bytez, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
  }

}
