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
import java.util.Map;

/**
 * Representation of the 'p4 where' command and its results.
 *
 * @author cquinn
 */
public class Where extends P4Command {
    public final String depotPath;
    public final String clientPath;
    public final String localPath;

    public static Where invokeWith(Runner runner, String path) throws IOException {
        return fromZtag(runner.execAndReadString(commandFor(path)));
    }

    @Override public String toString() {
        return "'" + depotPath + "' '" + clientPath + "' '" + localPath + "'";
    }

    private Where(String depotPath, String clientPerforcePath, String clientLocalPath) {
        this.depotPath = depotPath;
        this.clientPath = clientPerforcePath;
        this.localPath = clientLocalPath;
    }

    private static String[] commandFor(String path) {
        return new String[] { "p4", "-ztag", "where", path };
    }

    private static Where fromZtag(String ztag) {
        Map<String, String> zmap = ztagMap(ztag);
        if (zmap.size() == 0) {
            return null;
        }
        return new Where(zmap.get("depotFile"), zmap.get("clientFile"), zmap.get("path"));
    }

}
