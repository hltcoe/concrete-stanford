package edu.jhu.hlt.concrete.util;

import edu.jhu.hlt.concrete.Communication;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.TException;

public class ThriftIO {

    public static Communication readFile(String inputFile) throws IOException, TException {
        byte[] bytez = Files.readAllBytes(Paths.get(inputFile));
        return readFile(bytez);
    }

    public static Communication readFile(InputStream input) throws IOException, TException   {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((bytesRead = input.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return readFile(baos.toByteArray());
    }

    public static Communication readFile(byte[] bytez) throws IOException, TException {
        TDeserializer deser = new TDeserializer(new TBinaryProtocol.Factory());
        Communication deserialized = new Communication();
        deser.deserialize(deserialized, bytez);
        return deserialized;
    }

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
        for(Communication communication : communications){
            ZipEntry ze = new ZipEntry(communication.getId());
            zout.putNextEntry(ze);
            byte[] bytez = new TSerializer(new TBinaryProtocol.Factory()).serialize(communication);
            zout.write(bytez);
            zout.closeEntry();
        }
        zout.close();
        //Path path = (new File(outputFile)).toPath();
        //Files.write(path, bytez, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }


}

