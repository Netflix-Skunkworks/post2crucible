package com.netflix.postreview;

import com.atlassian.theplugin.commons.crucible.api.UploadItem;
import com.atlassian.theplugin.commons.crucible.api.model.BasicReview;

import java.io.IOException;
import java.util.List;

/**
 * Abstract representation of an SCM change that will be posted to Crucible.
 */
public interface Change {

    String getId();

    boolean isSubmitted();

    String getDescription();

    List<UploadItem> makeUploadItems(BasicReview review) throws IOException;

    String makePatch(BasicReview review) throws IOException;
}
