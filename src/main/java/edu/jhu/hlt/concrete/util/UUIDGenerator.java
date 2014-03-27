package edu.jhu.hlt.concrete.util;

import java.util.UUID;

public class UUIDGenerator {
    public static String make(){
        return UUID.randomUUID().toString();
    }
}
