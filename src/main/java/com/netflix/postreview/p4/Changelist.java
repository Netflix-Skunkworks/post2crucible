package com.netflix.postreview.p4;

import com.netflix.postreview.Runner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Represents a changelist in Perforce as returned by the p4 describe command.
 */
public class Changelist extends P4Command {
    private static final Logger logger = Logger.getLogger("com.netflix.postreview");

    public final int changeNumber;
    public final String user;
    public final String client;
    public final Date time;
    public final Status status;
    public final String description;
    public final List<FileEntry> files;
    public final List<JobEntry> jobs;

    public static Changelist invokeWith(Runner runner, String cl) throws IOException {
        return fromZtag(runner.execAndReadString(commandFor(cl)));
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("change: " + changeNumber + "\n");
        sb.append("  by: " + user + "@" + client + "\n");
        sb.append("  on: " + time + "\n");
        sb.append("  status: " + status+ "\n");
        sb.append("  description: " + description + "\n");
        if (jobs.size() > 0) {
            sb.append("  jobs:\n");
            for (JobEntry job : jobs) {
                sb.append("    " + job + "\n");
            }
        }
        if (files.size() > 0) {
            sb.append("  files:\n");
            for (FileEntry file : files) {
                sb.append("    " + file + "\n");
            }
        }
        return sb.toString();
    }

    /**
     * The change description status enum.
     */
    public static enum Status {
        PENDING, SUBMITTED;

        public static Status fromString(String s) {
            return valueOf(s.toUpperCase(Locale.US));
        }

        @Override public String toString() {
            return super.toString().toLowerCase(Locale.US);
        }
    }

    /**
     * The change description file action enum.
     */
    public static enum Action {
        ADD(false, true),
        EDIT(true, true),
        DELETE(true, false),
        INTEGRATE(true, true),
        BRANCH(false, true), // In theory there's a depot file, but it's not available via the p4 describe that we use.
        PURGE(false, false),
        MOVE(false, false),
        MOVE_ADD(false, true) {
            public String toString() { return "move/add"; }
        },
        MOVE_DELETE(true, false) {
            public String toString() { return " move/delete"; }
        };

        public final boolean hasDepotFile;
        public final boolean hasLocalFile;

        Action(boolean hasDepotFile, boolean hasLocalFile) {
            this.hasDepotFile = hasDepotFile;
            this.hasLocalFile = hasLocalFile;
        }

        public static Action fromString(String a) {
            return valueOf(a.replace('/', '_').toUpperCase(Locale.US));
        }

        @Override public String toString() {
            return super.toString().toLowerCase(Locale.US);
        }
    }

    /**
     * The file content type.
     */
    public static enum FileType {
        TEXT(true), BINARY(false), SYMLINK(false), APPLE(false), RESOURCE(false),
        UNICODE(true), UTF16(true), UNKNOWN(false);

        public final boolean isText;

        FileType(boolean isText) {
            this.isText = isText;
        }

        public static FileType fromString(String t) {
            if (t.endsWith("text")) return TEXT;
            if (t.endsWith("binary") || t.endsWith("tempobj")) return BINARY;
            if (t.endsWith("symlink")) return SYMLINK;
            if (t.endsWith("apple")) return APPLE;
            if (t.endsWith("resource")) return RESOURCE;
            if (t.endsWith("unicode")) return UNICODE;
            if (t.endsWith("utf16")) return UTF16;
            return UNKNOWN;
        }

        @Override public String toString() {
            return super.toString().toLowerCase(Locale.US);
        }
    }

    /**
     * A single file entry in a changelist describe, contains a subset of what a direct fstat can return.
     */
    public static class FileEntry {
        public final String depotFile;
        public final int rev;
        public final Action action;
        public final FileType type;

        public FileEntry(String depotFile, String rev, String action, String type) {
            this.depotFile = depotFile;
            this.rev = parseInt0(rev);
            this.action = Action.fromString(action);
            this.type = FileType.fromString(type);
        }

        @Override public String toString() {
            return "" + action + " '" + depotFile + "' #" + rev + " <" + type + ">";
        }
    }

    /**
     * A job attached to a change.
     */
    public static class JobEntry {
        public final String job;
        public final String status;
        public final String description;

        public JobEntry(String job, String status, String description) {
            this.job = job;
            this.status = status;
            this.description = description;
        }

        @Override public String toString() {
            return "" + job + " " + status + " " + description;
        }
    }

    private Changelist(String cl, String user, String client, String time, String status, String desc, List<FileEntry> files, List<JobEntry> jobs) {
        this.changeNumber = parseInt0(cl);
        this.user = user;
        this.client = client;
        this.time = new Date(Long.parseLong(time) * 1000);
        this.status = Status.fromString(status);
        this.description = desc;
        this.files = Collections.unmodifiableList(files);
        this.jobs = Collections.unmodifiableList(jobs);
    }

    private static String[] commandFor(String cl) {
        return new String[] { "p4", "-ztag", "describe", cl };
    }

    private static Changelist fromZtag(String ztag) {
        Map<String, String> zmap = ztagMap(ztag);
        if (zmap.size() == 0) {
            return null;
        }

        String cl = zmap.get("change");
        String user = zmap.get("user");
        String client = zmap.get("client");
        String time = zmap.get("time");
        String desc = zmap.get("desc");
        String status = zmap.get("status");

        List<FileEntry> files = new ArrayList<FileEntry>();
        for (int i = 0; ; i++) {
            String depotFile = zmap.get("depotFile" + i);
            if (depotFile == null) break;
            String rev = zmap.get("rev" + i);
            String action = zmap.get("action" + i);
            String type = zmap.get("type" + i);
            files.add(new FileEntry(depotFile, rev, action, type));
        }

        // TODO: verify these fields if we really ever care about jobs
        List<JobEntry> jobs = new ArrayList<JobEntry>();
        for (int i = 0; ; i++) {
            String job = zmap.get("Job" + i);
            if (job == null) break;
            String js = zmap.get("Status" + i);
            //String ju = zm.get("User" + i);
            //String jdate = zm.get("Date" + i);
            String jd = zmap.get("Description" + i);
            jobs.add(new JobEntry(job, js, jd));
        }

        return new Changelist(cl, user, client, time, status, desc, files, jobs);
    }

}
