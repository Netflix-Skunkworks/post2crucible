package com.netflix.postreview;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * General purpose command runner, handles process creation, environment and reading process output.
 */
public class Runner {
    private static final Logger logger = Logger.getLogger("com.netflix.postreview");

    protected Map<String, String> environment = new HashMap<String, String>();
    protected File workingDir;

    public byte[] execAndReadBytes(String[] args) throws IOException {
        Process process = start(args);
        byte[] bytes = readAllBytes(new BufferedInputStream(process.getInputStream()));
        close(process);
        return bytes;
    }

    public String execAndReadString(String[] args) throws IOException {
        Process process = start(args);
        String string = readAllString(new BufferedReader(new InputStreamReader(process.getInputStream())));
        close(process);
        return string;
    }

    public List<String> execAndReadLines(String[] args) throws IOException {
        Process process = start(args);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        List<String> lines = readAllLines(reader);
        close(process);
        return lines;
    }

    private static void logArgs(String[] args) {
        StringBuilder debug = new StringBuilder();
        for (String arg : args) {
            debug.append(arg + " ");
        }
        //logger.info("Executing: " + debug);
        System.out.println("Executing: " + debug);
    }

    public Process start(String[] args) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.environment().putAll(environment);
        if (workingDir != null) builder.directory(workingDir);
        builder.redirectErrorStream(true);
        logArgs(args);
        try {
            return builder.start();
        } catch (IOException e) {
            throw new IOException("Failed to invoke: " + args[0], e);
        }
    }

    public static void close(Process process) {
        try {
            process.getInputStream().close();
        } catch (IOException e) { }
        try {
            process.getOutputStream().close();
        } catch (IOException e) { }
        process.destroy();
    }

    // InputStream and Reader reading

    public static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int ch;
        while ((ch = is.read()) != -1) {
            bos.write(ch);
        }
        is.close();
        return bos.toByteArray();
    }

    public static String readAllString(BufferedReader br) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = br.read()) != -1) {
            sb.append((char) ch);
        }
        br.close();
        return sb.toString();
    }

    public static List<String> readAllLines(BufferedReader br) throws IOException {
        List<String> lines = new ArrayList<String>();
        String line;
        while ((line = br.readLine()) != null) {
            lines.add(line);
        }
        br.close();
        return lines;
    }

    // Local file reading

    public static byte[] readFileBytes(String filename) throws IOException {
        return readAllBytes(new BufferedInputStream(new FileInputStream(filename)));
    }

    public static String readFileString(String filename) throws IOException {
        return readAllString(new BufferedReader(new FileReader(filename)));
    }

    public static List<String> readFileLines(String filename) throws IOException {
        return readAllLines(new BufferedReader(new FileReader(filename)));
    }

}
