/**
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
