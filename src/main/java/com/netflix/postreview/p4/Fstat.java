package com.netflix.postreview.p4;

import com.netflix.postreview.Runner;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Representation of the 'p4 fstat' command and its results. This is a very detailed dump of everything p4
 * knows about the depot and local file.
 *
 * @author cquinn
 */
public class Fstat extends P4Command {
    public final String depotPath;
    public final String localPath;
    public final Changelist.Action action;
    public final int change;
    public final Date time;
    public final Changelist.FileType type;

    public static Fstat invokeWith(Runner runner, String path) throws IOException {
        return fromZtag(runner.execAndReadString(commandFor(path)));
    }

    @Override public String toString() {
        return "'" + depotPath + "' '" + localPath + "' " + action + " @" + change + " <" + type + ">";
    }

    static Pattern DEPOT_PATH_PAT = Pattern.compile("//(\\w+)/(.+)", Pattern.DOTALL);

    public String relativePath() {
        Matcher m = DEPOT_PATH_PAT.matcher(depotPath);
        return m.matches() ? m.group(2) : "???";
    }

    private Fstat(String depotPath, String localPath, String action, String change, String time, String type) {
        this.depotPath = depotPath;
        this.localPath = localPath;
        this.action = Changelist.Action.fromString(action);
        this.change = parseInt0(change);
        this.time = new Date(Long.parseLong(time) * 1000);
        this.type = Changelist.FileType.fromString(type);
    }

    private static String[] commandFor(String path) {
        return new String[] { "p4", "-ztag", "fstat", path };
    }

    private static Fstat fromZtag(String ztag) {
        Map<String, String> zmap = ztagMap(ztag);
        if (zmap.size() == 0) {
            return null;
        }
        String action = zmap.containsKey("action") ? zmap.get("action") : zmap.get("headAction");
        String type = zmap.containsKey("type") ? zmap.get("type") : zmap.get("headType");
        String change = zmap.containsKey("headChange") ? zmap.get("headChange") : zmap.get("change");
        //"haveRev"
        String time = zmap.containsKey("headTime") ? zmap.get("headTime") : "0";
        return new Fstat(zmap.get("depotFile"), zmap.get("clientFile"), action, change, time, type);
    }

}
