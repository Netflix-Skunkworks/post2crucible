package com.netflix.postreview;

import com.atlassian.connector.commons.api.ConnectionCfg;
import com.atlassian.theplugin.commons.crucible.api.UploadItem;
import com.atlassian.theplugin.commons.crucible.api.model.PermId;
import com.atlassian.theplugin.commons.crucible.api.rest.CrucibleSessionImpl;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiException;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiLoginException;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiMalformedUrlException;
import com.atlassian.theplugin.commons.remoteapi.rest.HttpSessionCallback;
import com.atlassian.theplugin.commons.util.Logger;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

/**
 * Subclass CrucibleSessionImpl to override a few methods to see if we can have better access to the rest
 * requests.
 */
class ExtendedCrucibleSession extends CrucibleSessionImpl {

    private Field authTokenField;

    public ExtendedCrucibleSession(ConnectionCfg serverData, HttpSessionCallback callback, Logger logger)
                throws RemoteApiMalformedUrlException {
        super(serverData, callback, logger);
        try {
            authTokenField = CrucibleSessionImpl.class.getDeclaredField("authToken");
            authTokenField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            System.out.println("authToken problem: " + e);
        }
        try {
            login();  // nothing really happens here, but loginCalled flag needs to get set
        } catch (RemoteApiLoginException e) {
            // doesn't really throe
        }
    }

    String getAuthToken() {
        try {
            return (String) authTokenField.get(this);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    void setAuthToken(String token) {
        try {
            authTokenField.set(this, token);
        } catch (IllegalAccessException e) {
            // authToken will remain unset
        }
    }

    public boolean isLoggedIn() throws RemoteApiLoginException {
        if (getAuthToken() == null && !super.isLoggedIn()) {
            return false;
        }
        //System.out.println("isLoggedIn? authToken: " + getAuthToken());
        return getAuthToken() != null;
    }

    @Override
    protected void adjustHttpHeader(HttpMethod method) {
        //method.addRequestHeader(new Header("Authorization", "Basic " + StringUtil.encode(getUsername() + ":" + getPassword())));
        if (getAuthToken() != null) {
            String qs = method.getQueryString();
            if (qs == null) qs = "";
            if (qs.length() > 0) qs = qs + "&";
            method.setQueryString(qs + "FEAUTH=" + getAuthToken());
        }
    }

    public void checkUser(String user) throws RemoteApiException {
        String requestUrl = getBaseUrl() + "/rest-service/users-v1/" + user; // USER_SERVICE;
        try {
            Document doc = retrieveGetResponse(requestUrl);

            XPath xpath = XPath.newInstance("/restUserProfileData/userData/userName");
            List<Element> elements = xpath.selectNodes(doc);
            if (elements == null || elements.isEmpty()) {
                throw new RemoteApiException(getBaseUrl() + ": Server returned malformed response, missing: /restUserProfileData/userData/userName");
            }
        } catch (IOException e) {
            throw new RemoteApiException(getBaseUrl() + ": " + e.getMessage(), e);
        } catch (JDOMException e) {
            throw new RemoteApiException(getBaseUrl() + ": Server returned malformed response", e);
        }
    }

    /**
     * Override addItemsToReview to differentiate the 3 cases of pairs present 1/1, 1/0, 0/1 (0/0
     * being invalid).
     */
    @Override
    public void addItemsToReview(PermId permId, Collection<UploadItem> uploadItems) throws RemoteApiException {
        final String REVIEW_SERVICE = "/rest-service/reviews-v1";
        final String ADD_FILE = "/addFile";
        try {
            String urlString = getBaseUrl() + REVIEW_SERVICE + "/" + permId.getId() + ADD_FILE;
            for (UploadItem uploadItem : uploadItems) {

                // Item add
                if (uploadItem.getOldContent() == null && uploadItem.getNewContent() != null) {
                    ByteArrayPartSource targetNewFile =
                            new ByteArrayPartSource(uploadItem.getFileName(), uploadItem.getNewContent());

                    Part[] parts = {
                        new FilePart("file", targetNewFile,
                                uploadItem.getNewContentType(), uploadItem.getNewCharset())};

                    retrievePostResponse(urlString, parts, true);

                // Item modify
                // TODO: use this approach for deletions too for now.
                } else if (true) { //uploadItem.getOldContent() != null && uploadItem.getNewContent() != null) {
                    ByteArrayPartSource targetNewFile =
                            new ByteArrayPartSource(uploadItem.getFileName(), uploadItem.getNewContent());
                    ByteArrayPartSource targetOldFile =
                            new ByteArrayPartSource(uploadItem.getFileName(), uploadItem.getOldContent());

                    Part[] parts = {
                            new FilePart("file", targetNewFile,
                                    uploadItem.getNewContentType(), uploadItem.getNewCharset()),
                            new FilePart("diffFile", targetOldFile,
                                    uploadItem.getOldContentType(), uploadItem.getOldCharset())};

                    retrievePostResponse(urlString, parts, true);

                // Item delete  
                // TODO: this rest combo doesn't work: crucible seems to need the "file" part...
                } else { // uploadItem.getOldContent() != null && uploadItem.getNewContent() == null
                    ByteArrayPartSource targetOldFile =
                            new ByteArrayPartSource(uploadItem.getFileName(), uploadItem.getOldContent());

                    Part[] parts = {
                            new FilePart("diffFile", targetOldFile,
                                    uploadItem.getOldContentType(), uploadItem.getOldCharset())};

                    retrievePostResponse(urlString, parts, true);
                }
            }
        } catch (JDOMException e) {
            throw new RemoteApiException(getBaseUrl() + ": Server returned malformed response", e);
        }
    }
}
