package com.netflix.postreview.p4;

import com.netflix.postreview.Runner;

import java.io.IOException;
import java.util.List;

/**
 * Simple wrapper for getting the contents of a depot file using "p4 print".
 */
public class DepotFile {

    public static byte[] readBytes(Runner r, String path, int rev) throws IOException {
        return r.execAndReadBytes(getPrintCmd(path, rev));
    }

    public static String readString(Runner r, String path, int rev) throws IOException {
        return r.execAndReadString(getPrintCmd(path, rev));
    }

    public static List<String> readLines(Runner r, String path, int rev) throws IOException {
        return r.execAndReadLines(getPrintCmd(path, rev));
    }

    private static String[] getPrintCmd(String filename, int rev) {
        String fileRev = filename + "#" + rev;
        return new String[] {"p4", "print", "-q", fileRev};
    }
}
