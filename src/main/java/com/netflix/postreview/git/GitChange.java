package com.netflix.postreview.git;

import com.atlassian.theplugin.commons.crucible.api.UploadItem;
import com.atlassian.theplugin.commons.crucible.api.model.BasicReview;
import com.netflix.postreview.Change;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an abstract change in Git to be reviewed in Crucible.
 */
public class GitChange implements Change {

    private final GitRunner git;
    private final String startChangeId;
    private final String endChangeId;
    private final GitMetadata metadata;

    public GitChange(GitRunner git, String startChangeId, String endChangeId) throws IOException {
        this.git = git;
        this.startChangeId = startChangeId != null ? startChangeId : "HEAD^";
        this.endChangeId = endChangeId != null ? endChangeId : "HEAD";
        metadata = GitMetadata.from(git, this.startChangeId, this.endChangeId);
    }

    @Override public String toString() { return metadata.toString(); }

    public boolean isSubmitted() { return false; }

    public String getId() {
        return metadata.hash; //startChangeId;
    }

    public String getDescription() {
        return metadata.comment;
    }

    public List<UploadItem> makeUploadItems(BasicReview review) throws IOException {
        List<String> files = git.execAndReadLines(new String[] {git.gitPath, "diff", "--name-only", startChangeId, endChangeId});
        List<UploadItem> items = new ArrayList<UploadItem>(files.size());
        for (String path : files) {
            items.add(new UploadItem(path, getBytes(startChangeId, path), getBytes(endChangeId, path))); //startChange
        }
        //return ImmutableList.copyOf(items);
        return Collections.unmodifiableList(items);
    }

    public String makePatch(BasicReview review) throws IOException {
        return null; // Hrm. Can we do this?
    }

    private byte[] getBytes(String change, String path) throws IOException {
        return git.execAndReadBytes(new String[] {git.gitPath, "show", change + ":" + path});
    }

}
