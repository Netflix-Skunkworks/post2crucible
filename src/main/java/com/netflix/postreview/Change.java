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
