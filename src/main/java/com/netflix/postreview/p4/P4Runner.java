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
package com.netflix.postreview.p4;

import com.netflix.postreview.Runner;

import java.io.IOException;

/**
 * Variant of Runner tailored for invoking p4
 */
public class P4Runner extends Runner {
    /**
     * Requires environment variables (P4USER, P4CLIENT, P4PORT, P4PASSWD)
     */
    public P4Runner(String port, String client, String user, String passwd) throws IOException {
        if (port != null) environment.put("P4PORT", port);
        if (client != null) environment.put("P4CLIENT", client);
        if (user != null) environment.put("P4USER", user);
        if (passwd != null) environment.put("P4PASSWD", passwd);

        // Retrieve the basic info on the client to make sure that all params are OK.
        Client c = Client.invokeWith(this, client);
        if (c == null) throw new IOException("Client not found: " + client);
    }

}
