package com.netflix.postreview.p4;

import com.netflix.postreview.Runner;

import java.io.IOException;
import java.util.Map;

/**
 * Representation of the 'p4 info' command and its results.
 *
 * @author cquinn
 */
public class Info extends P4Command {
    public final String clientName;
    public final String clientRoot;

    public static Info invokeWith(Runner runner) throws IOException {
        return fromZtag(runner.execAndReadString(command()));
    }

    @Override public String toString() {
        return "Client: " + clientName + " Root: " + clientRoot;
    }

    private Info(String clientName, String clientRoot) {
        this.clientName = clientName;
        this.clientRoot = clientRoot;
    }

    private static String[] command() {
        return new String[] { "p4", "-ztag", "info" };
    }

    private static Info fromZtag(String ztag) {
        Map<String, String> zmap = ztagMap(ztag);
        return new Info(zmap.get("clientName"), zmap.get("clientRoot"));
    }

}
