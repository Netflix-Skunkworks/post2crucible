package com.netflix.postreview.git;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Encapsulation of the parsed git log output for a range of changes
 */
public class GitMetadata {
    public final String author;
    public final String date;
    public final String hash;
    public final String comment;

    private GitMetadata(String author, String date, String hash, String comment) {
        this.author = author;
        this.date = date;
        this.hash = hash;
        this.comment = comment;
    }

    public static GitMetadata from(GitRunner git, String startChange, String endChange) throws IOException {
        List<String> lines = git.execAndReadLines(new String[] {git.gitPath, "log", startChange + ".." + endChange});

        StringBuilder author = new StringBuilder();
        StringBuilder date = new StringBuilder();
        StringBuilder hash = new StringBuilder();
        StringWriter comment = new StringWriter();

        PrintWriter out = new PrintWriter(comment);
        for (String line : lines) {
            if (!checkPrefix(author, line, "Author: ") && !checkPrefix(date, line, "Date: ") && !checkPrefix(hash, line, "commit ")) {
                out.println(line);
            }
        }
        out.flush();

        return new GitMetadata(author.toString(), date.toString(), hash.toString(), comment.toString());
    }

    private static boolean checkPrefix(StringBuilder str, String line, String prefix) {
        if (line.startsWith(prefix)) {
            String substring = line.substring(prefix.length());
            if (str.length() > 0) {
                str.append(", ");
            }
            str.append(substring);
            return true;
        }
        return false;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb.append("Author: ").append(author)
                .append("\nDate: ").append(date)
                .append("\ncommit: ").append(hash)
                .append("\n").append(comment).toString();
    }

}
