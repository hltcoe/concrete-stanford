package edu.jhu.hlt.concrete.util;

import edu.jhu.hlt.concrete.Communication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.TException;

public class ThriftIO {

    public static Communication readFile(String inputFile) throws IOException, TException {
        byte[] bytez = Files.readAllBytes(Paths.get(inputFile));
        TDeserializer deser = new TDeserializer(new TBinaryProtocol.Factory());
        Communication deserialized = new Communication();
        deser.deserialize(deserialized, bytez);
        return deserialized;
    }


    public static void writeFile(String outputFile, Communication communication) throws IOException, TException {
        byte[] bytez = new TSerializer(new TBinaryProtocol.Factory()).serialize(communication);
        Path path = (new File(outputFile)).toPath();
        Files.write(path, bytez, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

}

