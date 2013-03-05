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
package com.netflix.postreview;

import com.atlassian.connector.commons.api.ConnectionCfg;
import com.atlassian.theplugin.commons.cfg.CrucibleServerCfg;
import com.atlassian.theplugin.commons.cfg.ServerCfg;
import com.atlassian.theplugin.commons.cfg.ServerIdImpl;
import com.atlassian.theplugin.commons.crucible.api.UploadItem;
import com.atlassian.theplugin.commons.crucible.api.model.BasicReview;
import com.atlassian.theplugin.commons.crucible.api.model.CustomFilterBean;
import com.atlassian.theplugin.commons.crucible.api.model.PatchAnchorDataBean;
import com.atlassian.theplugin.commons.crucible.api.model.PermId;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.State;
import com.atlassian.theplugin.commons.crucible.api.model.User;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiException;
import com.atlassian.theplugin.commons.util.Logger;
import com.atlassian.theplugin.commons.util.LoggerImpl;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crucible client wrapper class. Handles all access to a Crucible server given its base URL.
 *
 * @author cquinn
 */
public class Crucible {

    static class NullLogger extends LoggerImpl {
        public void log(int level, String msg, Throwable t) { }
    }

    //private static final Logger logger = Logger.getLogger("com.netflix.postreview");
    private static final Logger logger = new NullLogger();
    static {
        // Keep LoggerImpl quiet, and older code that uses getInstance, happy.
        LoggerImpl.setInstance(logger);
    }

    final String baseUrl;
    ExtendedCrucibleSession session;

    /**
     * Constructs a new Crucible client instance ready to be connected to the server using login().
     */
    public Crucible(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private ConnectionCfg createServerData(ServerCfg serverCfg) {
        return new ConnectionCfg(serverCfg.getServerId().getId(), serverCfg.getUrl(),
                serverCfg.getUsername(), serverCfg.getPassword());
    }

    private ExtendedCrucibleSession createCrucibleSession(String url, String username, String password)
            throws RemoteApiException {
        final CrucibleServerCfg serverCfg =
                new CrucibleServerCfg("crucibleservercfg", new ServerIdImpl());
        serverCfg.setUrl(url);
        serverCfg.setUsername(username);
        serverCfg.setPassword(password);
        serverCfg.setFisheyeInstance(true);
        return new ExtendedCrucibleSession(createServerData(serverCfg), new ClientFactory(), logger);
    }

    private static String tokenFile = System.getProperty("user.home") + "/.post2crucible.token";

    private String loadAuthToken() {
        try {
            String authToken = Runner.readFileString(tokenFile).trim();
            //System.out.println("Loaded authToken: " + authToken);
            return authToken;
        } catch (IOException e) {
            // OK, no token file
            return null;
        }
    }

    private void saveAuthToken(String authToken) {
        try {
            Writer fw = new FileWriter(tokenFile);
            fw.write(authToken);
            fw.close();
            //System.out.println("Saved authToken: " + authToken);
        } catch (IOException e) {
            // Can't save the token, but not much we can do about it, so just carry on.
        }
    }

    private static String promptPassword() {
        java.io.Console console = System.console();
        if (console != null) {
            char[] passwd = console.readPassword("%s", "Crucible Password: ");
            return new String(passwd);
        }
        return null;
    }


    /**
     * Creates a new Crucible session and logs in the given user with the given password.
     */
    public void login(String user, String passwd, boolean forceLogin) throws RemoteApiException {

        // Get the session object created and ready to log in
        session = createCrucibleSession(baseUrl, user, passwd);

        // if a password was provided, just use it to log in
        if (passwd != null) {
            session.isLoggedIn();  // trigger login, get token, throw an exception if failure
            // If that went OK, then write the auth token for later use
            if (session.getAuthToken() != null) {
                saveAuthToken(session.getAuthToken());
                return;
            }
        }

        // No password, see if we have an auth token saved
        String authToken = forceLogin ? null : loadAuthToken();

        // Have an auth toke to try. Need to poke the server to see if it is ok
        if (authToken != null) {
            session = createCrucibleSession(baseUrl, user, "");  // need non-null password to proceed
            session.setAuthToken(authToken);
            try {
                // Pre-validate token by poking an exiting CR to work around: http://jira.atlassian.com/browse/CRUC-1452
                session.checkUser(user);
                return;  // loaded auth token looks good, all done here.
            } catch (RemoteApiException e) {
                e.printStackTrace();  // Auth problem? Or just bad approach?
                // token is bad, don't use below
            }
        }

        // No auth token, no password given, need to prompt for real password and try again
        passwd = promptPassword();
        session = createCrucibleSession(baseUrl, user, passwd);
        session.isLoggedIn();  // trigger login, get token, throw an exception if failure
        if (session.getAuthToken() != null) {
            saveAuthToken(session.getAuthToken());
            return;  // new login has created a token, good to go.
        }
    }

    private static final int MAX_DESCRIPTION = 120;

    /** Returns the first non-blank line of the description, trimmed to 120 chars max. */
    private static String nameFromDescription(String description) {
        if (description != null) {
            for (String line : description.split("\n")) {
                String tl = line.trim();
                if (tl.length() > 0) {
                    return tl.length() <= MAX_DESCRIPTION ? tl : tl.substring(0, MAX_DESCRIPTION);
                }
            }
        }
        return null;
    }

    /**
     * Returns a new (full) Review instance given the depot, project key, user name, change ID and description text.
     */
    public Review newReviewRequest(String depot, String project, String user, String changeId, String descr) {
        Review review = new Review(baseUrl, project, new User(user, null), new User(user, null));  // user is author & moderator
        review.setRepoName(depot);
        String name = nameFromDescription(descr);
        if (name != null) {
            name += " @" + changeId;
        } else {
            name = "Review of pending change @" + changeId;
        }
        review.setName(name.length() <= MAX_DESCRIPTION ? name : name.substring(0, MAX_DESCRIPTION));
        review.setCreator(new User(user, null));  // seems to be required
        review.setDescription(descr);
        review.setAllowReviewerToJoin(true);
        return review;
    }

    // Locate a change in a title with a @ prefix, then either 3-10 P4 CL digits, or 8-40 Git SHA-1 hex chars
    private static final Pattern CHANGEID_PAT = Pattern.compile(".*@([\\d]{3,10}|[0-9a-fA-F]{8,40}).*");  // Could also check the depot name here.

    /**
     * Returns the changeId as a String extracted from a given title string, null when none found.
     */
    public static String changeIdFromTitle(String title) {
        Matcher m = CHANGEID_PAT.matcher(title.trim());
        return m.matches() ? m.group(1) : null;
    }

    /**
     * Finds the best review as a BasicReview for a given changeId and author.
     */
    public BasicReview findReview(String changeId, String author) {
        try {
            CustomFilterBean customFilter = new CustomFilterBean();
            customFilter.setAuthor(author);
            customFilter.setState(new State[] {State.DRAFT, State.REVIEW});
            List<BasicReview> reviews = session.getReviewsForCustomFilter(customFilter);
            System.out.println("Scanning " + reviews.size() + " reviews for a review of change " + changeId);
            for (BasicReview r : reviews) {
                System.out.println("    (" + r.getPermId().getId() + " '" + r.getName() + "')");
                String cid = changeIdFromTitle(r.getName());
                if (cid != null && (cid.startsWith(changeId) || changeId.startsWith(cid))) {
                    System.out.println("Found review: " + r.getPermId().getId() + " '" + r.getName() + "'");
                    return r;
                }
            }
            return null;
        } catch (RemoteApiException e) {
            System.out.println("Find review problem: " + e);
            logger.log(LoggerImpl.LOG_ERR, null, e);
        }
        return null;
    }

    /**
     * Retrieves a (full) Review object given a BasicReview.
     */
    public Review getReview(BasicReview br) throws RemoteApiException {
        return getReview(br.getPermId());
    }

    /**
     * Retrieves a (full) Review object given a rev key string.
     */
    public Review getReview(String revKey) throws RemoteApiException {
        return getReview(new PermId(revKey));
    }

    /**
     * Retrieves a (full) Review object given a permId.
     */
    public Review getReview(PermId permId) throws RemoteApiException {
        try {
            System.out.println("Retrieving review for update: " + permId);
            return session.getReview(permId);
        } catch (RemoteApiException e) {
            System.out.println("Review not found: " + permId);
            logger.log(LoggerImpl.LOG_ERR, null, e);
            throw e;
        }
    }

    /**
     * Adds upload items to an existing retrieved review.
     */
    public void updateReviewWithItems(BasicReview review, Collection<UploadItem> items) throws RemoteApiException {
        session.addItemsToReview(review.getPermId(), items);
        System.out.println("Updated review: " + review.getPermId().getId() + " '" + review.getName() + "'");
    }

    /**
     * Adds a patch to an existing retrieved review.
     */
    public void updateReviewWithPatch(BasicReview review, String depot, String patch) throws RemoteApiException {
        session.addPatchToReview(review.getPermId(), "depot", patch);
        System.out.println("Updated review: " + review.getPermId().getId() + " '" + review.getName() + "'");
    }

    /**
     * TODO: see also session method:
     * Review addFileRevisionsToReview(PermId permId, String repository, List<PathAndRevision> revisions)
     */

    /**
     * Creates a brand-new review given a new review instance and a list of update items.
     */
    public BasicReview createReviewWithItems(Review review, Collection<UploadItem> items) throws RemoteApiException {
        BasicReview response = session.createReviewFromUpload(review, items);
        System.out.println("New review: " + response.getPermId().getId() + " '" + response.getName() + "'");
        return response;
        //TODO: see:
        //  session.
        //    public BasicReview createReviewFromPatch(Review review, String patch)
    }

    /**
     * Creates a brand-new review given a new review instance and a patch.
     */
    public BasicReview createReviewWithPatch(Review review, String patch) throws RemoteApiException {
        BasicReview response = session.createReviewFromPatch(review, patch, new PatchAnchorDataBean(review.getRepoName(), "/", ""));
        System.out.println("New review: " + response.getPermId().getId() + " '" + response.getName() + "'");
        return response;
    }

    /**
     * Creates a brand-new review given a new review instance and a single submitted CL.
     */
    public BasicReview createReviewWithCl(Review review, String changeId) throws RemoteApiException {
        BasicReview response = session.createReviewFromRevision(review, Arrays.asList(changeId));
        System.out.println("New review: " + response.getPermId().getId() + " '" + response.getName() + "'");
        return response;
    }


    /**
     * Logout from the server and terminate the session.
     */
    public void logout() throws RemoteApiException {
        session.logout();
        session = null;
    }

}
