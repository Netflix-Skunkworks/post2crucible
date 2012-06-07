package com.netflix.postreview.p4;

import com.atlassian.theplugin.commons.crucible.api.UploadItem;
import com.atlassian.theplugin.commons.crucible.api.model.BasicReview;
import com.netflix.postreview.Change;
import com.netflix.postreview.Runner;
import difflib.DiffUtils;
import difflib.Patch;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a pending or submitted change in P4 to be reviewed in Crucible.
 */
public class P4Change implements Change {
    P4Runner p4;
    Changelist changelist;

    public P4Change(P4Runner p4, String changeId) throws IOException {
        this.p4 = p4;
        changelist = Changelist.invokeWith(p4, changeId);
        if (changelist == null) throw new IOException("Changelist not found: " + changeId);
    }

    @Override public String toString() {
        return changelist != null ? changelist.toString() : "change: INVALID\n";
    }

    public boolean isSubmitted() { return changelist.status == Changelist.Status.SUBMITTED; }

    public String getId() {
        return Integer.toString(changelist.changeNumber);
    }

    public String getDescription() {
        return changelist.description;
    }

    /**
     * Scans a given CL and returns a list of Crucible UploadItem objects. Each of these are a pair of
     * depot and local (original/revised) files, including full content.
     */
    public List<UploadItem> makeUploadItems(BasicReview review) throws IOException {
        // Analyze each entry in the changelist to determine its location and
        // action: ADD/EDIT/DELETE/MOVE_x, producing a crucible UploadItem for each.
        List<UploadItem> items = new ArrayList<UploadItem>();
        for (Changelist.FileEntry fe : changelist.files) {
            Fstat fs = Fstat.invokeWith(p4, fe.depotFile);
            System.out.println("    => " + fs);
            if (fe.action.hasDepotFile || fe.action.hasLocalFile) {
                if (true || review == null) {
                    byte[] depotBytes = fe.action.hasDepotFile
                            ? DepotFile.readBytes(p4, fs.depotPath, fe.rev)
                            : new byte[0];
                    byte[] localBytes = fe.action.hasLocalFile
                            ? Runner.readFileBytes(fs.localPath)
                            : new byte[0];
                    items.add(new UploadItem(fs.relativePath(), depotBytes, localBytes));
                } else {
                    // TODO: This kinda works, but really need to know when local is really different from last file content
                    /*
                    Set<CrucibleFileInfo> cruFiles = review.getFiles();
                    for (CrucibleFileInfo cfi : cruFiles) {
                        //if (fs.depotPath.equals())
                        System.out.println(" cfi:" + cfi);
                    }*/
                    // See : byte[] getFileContent(String contentUrl)
                    byte[] localBytes = fe.action.hasLocalFile
                            ? Runner.readFileBytes(fs.localPath)
                            : new byte[0];
                    items.add(new UploadItem(fs.relativePath(), null, localBytes));
                }
            }
        }
        return items;
    }

    /**
     * Scans a given CL and returns a single big udiff patch string that represents the delta from the original
     * depot versions to the current local versions. The patch file is in a format digestible by Crucible.
     */
    public String makePatch(BasicReview review) throws IOException {
        List<String> patchLines = new ArrayList<String>();
        for (Changelist.FileEntry fe : changelist.files) {
            Fstat fs = Fstat.invokeWith(p4, fe.depotFile);
            if (!fe.type.isText) {
                System.out.println("    => " + fs + " (SKIPPING)");
                continue;
            }
            System.out.println("    => " + fs);
            if (fe.action.hasDepotFile || fe.action.hasLocalFile) {
                List<String> diffLines;
                if (!fe.action.hasDepotFile && fe.action.hasLocalFile) {
                    diffLines = diffLinesForAdd(p4, fe, fs);
                } else if (fe.action.hasDepotFile && fe.action.hasLocalFile) {
                    diffLines = diffLinesForChange(p4, fe, fs);
                } else {
                    diffLines = diffLinesForDelete(p4, fe, fs);
                }
                //for (String dl : diffLines) { System.out.println(dl); }
                patchLines.add("Index: " + fs.relativePath());
                patchLines.add("===================================================================");
                patchLines.addAll(diffLines);
            }
        }
        return StringUtils.join(patchLines, '\n');
    }

    private static List<String> diffLinesForAdd(P4Runner p4, Changelist.FileEntry fe, Fstat fs) throws IOException {
        List<String> diffLines = new ArrayList<String>();
        diffLines.add("--- " +  fs.relativePath() + "\t(added)");
        diffLines.add("+++ " + fs.relativePath() + "\t(added)");
        List<String> localLines = Runner.readFileLines(fs.localPath);
        String header = "@@ -0,0 +1," + localLines.size() + " @@";
        diffLines.add(header);
        for (String ll : localLines) {
            diffLines.add("+" + ll);
        }
        return diffLines;
    }

    private static List<String> diffLinesForChange(P4Runner p4, Changelist.FileEntry fe, Fstat fs) throws IOException {
        List<String> depotLines = DepotFile.readLines(p4, fs.depotPath, fe.rev);
        List<String> localLines = Runner.readFileLines(fs.localPath);
        Patch patch = DiffUtils.diff(depotLines, localLines);
        String depotTag = fs.relativePath() + "\t" + fs.change;
        String localTag = fs.relativePath() + "\t" + "\t(modified)";
        return DiffUtils.generateUnifiedDiff(depotTag, localTag, depotLines, patch, 99999); // big number for full context
    }

    private static List<String> diffLinesForDelete(P4Runner p4, Changelist.FileEntry fe, Fstat fs) throws IOException {
        List<String> diffLines = new ArrayList<String>();
        diffLines.add("--- " + fs.relativePath() + "\t" + fs.change);
        diffLines.add("+++ " + fs.relativePath() + "\t" + fs.change);
        List<String> depotLines = DepotFile.readLines(p4, fs.depotPath, fe.rev);
        String header = "@@ -1," + depotLines.size() + " +0,0 @@";
        diffLines.add(header);
        for (String dl : depotLines) {
            diffLines.add("-" + dl);
        }
        return diffLines;
    }

}
