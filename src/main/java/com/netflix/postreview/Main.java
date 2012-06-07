package com.netflix.postreview;

import com.atlassian.theplugin.commons.crucible.api.UploadItem;
import com.atlassian.theplugin.commons.crucible.api.model.BasicReview;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiException;
import com.netflix.postreview.git.GitChange;
import com.netflix.postreview.git.GitRunner;
import com.netflix.postreview.p4.P4Change;
import com.netflix.postreview.p4.P4Runner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author cquinn
 */
public class Main {

    static class AppOptions {
        static Options initOptions() {
            return new Options()
                // Crucible connection options
                .addOption(OptionBuilder.withLongOpt("url").hasArg().withArgName("url")
                    .withDescription("Fisheye/Crucible base URL. (default: http://crucible.netflix.com)").create())
                .addOption(OptionBuilder.withLongOpt("user").hasArg().withArgName("name")
                    .withDescription("LDAP user. (default: $USER)").create('u'))
                .addOption(OptionBuilder.withLongOpt("passwd").hasArg().withArgName("passwd")
                    .withDescription("LDAP password.").create('p'))
                .addOption(OptionBuilder.withLongOpt("login")
                    .withDescription("Force (re)login, creating or refreshing the login token.").create())

                // Perforce connection & context options
                .addOption(OptionBuilder.withLongOpt("p4port").hasArg().withArgName("port")
                    .withDescription("Perforce server port. (default: perforce:1666)").create())
                .addOption(OptionBuilder.withLongOpt("p4passwd").hasArg().withArgName("passwd")
                    .withDescription("Perforce password. (default: $P4PASSWD if needed)").create())
                .addOption(OptionBuilder.withLongOpt("p4client").hasArg().withArgName("clientname")
                    .withDescription("Perforce client. (default: $P4CLIENT)").create())

                // Git connection & context options
                .addOption(OptionBuilder.withLongOpt("git").hasArg().withArgName("path")
                    .withDescription("Path to git executable. (default: /opt/local/bin/git)").create('g'))
                .addOption(OptionBuilder.withLongOpt("dir").hasArg().withArgName("path")
                    .withDescription("Project directory. (default: current working directory)").create('d'))

                 // Perforce & Git change / commit options
                .addOption(OptionBuilder.withLongOpt("change").hasArg().withArgName("id")
                    .withDescription("Pending P4/Git change (Git default: HEAD^) to review.").create('c'))
                .addOption(OptionBuilder.withLongOpt("endchange").hasArg().withArgName("id")
                    .withDescription("The ending Git commit (default: HEAD)").create('e'))

                // Code review options
                .addOption(OptionBuilder.withLongOpt("nothing")
                    .withDescription("Do not actually create or update the review.").create('n'))
                .addOption(OptionBuilder.withLongOpt("review").hasArg().withArgName("key")
                    .withDescription("Existing Crucible review to update. (default: find based on changeId in name)").create('r'))
                .addOption(OptionBuilder.withLongOpt("project").hasArg().withArgName("key")
                    .withDescription("Crucible project to associate review with. (default: 'CR')").create('j'))
                .addOption(OptionBuilder.withLongOpt("patch")
                    .withDescription("Use a universal diff patch upload instead of full file pairs.").create())

                .addOption(OptionBuilder.withLongOpt("open")
                    .withDescription("Open a browser to new review. (Mac only)").create('o'))

                .addOption(OptionBuilder.withLongOpt("new")
                    .withDescription("Force a new review to be created.").create());
        }
        static final Options options = initOptions();

        // Crucible connection options
        final String url;
        final String user;
        final String passwd;
        final boolean login;

        // Perforce connection & context options
        final String p4port;
        final String p4passwd;
        final String p4client;

        // Git connection & context options
        final String git;
        final File dir;

        // Change / commit options
        final String changeId;
        final String endChangeId;

        // action options
        final boolean nothing;
        final String reviewKey;
        final String project;
        final boolean patch;
        final boolean open;
        final boolean forceNewReview;

        public AppOptions(String[] args) throws ParseException, IOException {
            final CommandLine line = new GnuParser().parse(options, args);

            p4port = line.getOptionValue("p4port", "perforce:1666");
            p4client = line.getOptionValue("p4client", System.getenv("P4CLIENT"));
            p4passwd = line.getOptionValue("p4passwd", System.getenv("P4PASSWD"));

            git = line.hasOption("git") ? line.getOptionValue("git", "/opt/local/bin/git") : null;
            dir = new File(line.getOptionValue("dir", ".")).getCanonicalFile();

            url = line.getOptionValue("url", "http://crucible.netflix.com");
            user = line.getOptionValue("user", System.getenv("USER"));
            passwd = line.getOptionValue("passwd");
            login = line.hasOption("login");

            changeId = line.getOptionValue("change");
            endChangeId = line.getOptionValue("endchange", "HEAD");

            nothing = line.hasOption("nothing");
            reviewKey = line.getOptionValue("review");
            project = line.getOptionValue("project", "CR");
            patch = line.hasOption("patch");
            open = line.hasOption("open");
            forceNewReview = line.hasOption("new");
        }

        public static void printHelp() {
            HelpFormatter formatter = new HelpFormatter();
            formatter.setWidth(120);
            formatter.printHelp("post2crucible", options, true);
        }
    }

    public static void main(String[] args) {
        // Process Command Line
        AppOptions opts;
        try {
            opts = new AppOptions(args);
        }
        catch (Exception e) {
            System.out.println("\nFatal: Command line error: " + e.getMessage());
            AppOptions.printHelp();
            System.exit(1);
            return;
        }
        if (opts.git == null && (opts.p4client == null || opts.p4client.length() == 0)) {
            System.out.println("\nFatal: SCM information missing for either P4 or Git. One of these must be supplied:");
            System.out.println("  P4 client missing: it must be specified either by setting $P4CLIENT or passing --p4client.");
            System.out.println("  Git path/flag: --git [path to git executable]");
            System.exit(1);
            return;
        }
        if (opts.git == null && opts.changeId == null && !opts.login) {
            System.out.println("\nFatal: Command line error: at least one of --change or --login is required.");
            AppOptions.printHelp();
            System.exit(1);
            return;
        }

        // Login to Crucible
        Crucible cru = loginCrucible(opts);

        if (opts.login && opts.changeId == null) {
            System.exit(0);  // login above would be with force=true, so now just exit since nothing to do.
        }

        // Get the full change representation for Git/Perforce
        Change change;
        try {
            if (opts.git != null) {
                change = new GitChange(new GitRunner(opts.git, opts.dir), opts.changeId, opts.endChangeId);
            } else {
                change = new P4Change(new P4Runner(opts.p4port, opts.p4client, opts.user, opts.p4passwd), opts.changeId);
            }
            System.out.println(change);
        } catch (Exception e) {
            System.out.println("\nFatal: " + e.getMessage());
            System.exit(-1);
            return;
        }

        try {
            // Try to find an existing review to update, may be null if none found
            BasicReview review = locateReview(opts, cru, change.getId());

            if (change.isSubmitted()) {
                review = postSubmittedChange(change, opts, cru, review);
            } else { // Changelist.Status.PENDING
                // Create or update the review using patch file or file pair items.
                if (opts.patch)
                    review = postLocalChangePatch(change, opts, cru, review);
                else
                    review = postLocalChangeItems(change, opts, cru, review);
            }

            // Logout from Crucible, and display the result
            cru.logout();
            if (!opts.nothing) {
                finishReview(opts, review);
            }

        } catch (RemoteApiException e) {
            System.out.println("\nFatal: Crucible communication problem: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.out.println("\nFatal: " + e);
            System.exit(-1);
        }
    }

    private static Crucible loginCrucible(AppOptions opts) {
        System.out.println("Logging in to Crucible as " + opts.user + " @ " + opts.url);
        Crucible cru = new Crucible(opts.url);
        try {
            cru.login(opts.user, opts.passwd, opts.login);
        } catch (RemoteApiException e) {
            System.out.println("Fatal: Login problem: " + e.getMessage());
            System.exit(1);
        }
        return cru;
    }

    /**
     * Locates an existing review for the give changeId.
     */
    private static BasicReview locateReview(AppOptions opts, Crucible cru, String changeId) throws RemoteApiException {
        return opts.forceNewReview ? null : (opts.reviewKey != null ? cru.getReview(opts.reviewKey) : cru.findReview(changeId, opts.user));
    }

    private static BasicReview postLocalChangePatch(Change change, AppOptions opts, Crucible cru, BasicReview review) throws Exception {
        String patch = change.makePatch(review);

        // Create or update the review if not dry-run
        if (!opts.nothing) {
            if (review == null) {
                Review newReview = cru.newReviewRequest("depot", opts.project, opts.user, change.getId(), change.getDescription());
                // Create a new review from template
                return cru.createReviewWithPatch(newReview, patch);
            } else {
                cru.updateReviewWithPatch(review, "depot", patch);
            }
        } else {
            System.out.println("Doing nothing (--nothing), but would upload patch:");
            System.out.println(patch);
        }
        return review;
    }

    private static BasicReview postLocalChangeItems(Change change, AppOptions opts, Crucible cru, BasicReview review) throws Exception {
        // Generate the list of file items to review from the changelist, using the best form for create or update.
        List<UploadItem> items = change.makeUploadItems(review);

        // Create or update the review if not dry-run
        if (!opts.nothing) {
            if (review == null) {
                Review newReview = cru.newReviewRequest("depot", opts.project, opts.user, change.getId(), change.getDescription());
                review = cru.createReviewWithItems(newReview, items);
            } else {
                cru.updateReviewWithItems(review, items);
            }
        } else {
            System.out.println("Doing nothing (--nothing), but would upload " + items.size() + " file pairs.");
        }
        return review;
    }

    private static BasicReview postSubmittedChange(Change change, AppOptions opts, Crucible cru, BasicReview review) throws Exception {
        // Create the review if not dry-run
        if (!opts.nothing) {
            if (review == null) {
                Review newReview = cru.newReviewRequest("depot", opts.project, opts.user, change.getId(), change.getDescription());
                // Create a new review from template
                return cru.createReviewWithCl(newReview, change.getId());
            } else {
                //cru.updateReviewWithCl(review, "depot", opts.changeId);
                System.out.println("Change " + change.getId() + " already has open review " + review.getPermId());
            }
        } else {
            System.out.println("Doing nothing (--nothing), but would create review for change " + change.getId());
        }
        return review;
    }

    private static void finishReview(AppOptions opts, BasicReview review) {
        String reviewUrl = opts.url + "/cru/" + review.getPermId().getId();
        if (opts.open) {
            // Open a browser if requested.
            // TODO: This is for Mac, add support for Linux & Windows too.
            try {
                 new Runner().start(new String[] { "open", reviewUrl });
            } catch (IOException e) {
                System.out.println("\nFatal: Problem opening URL: " + e.getMessage());
                System.exit(1);
            }
        } else {
            System.out.println("Review URL: " + reviewUrl);
        }
    }

}
