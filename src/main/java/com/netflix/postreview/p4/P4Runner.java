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
    public P4Runner(String port, String client, String user, String passwd)  throws IOException, P4Exception {
        if (port != null) environment.put("P4PORT", port);
        if (client != null) environment.put("P4CLIENT", client);
        if (user != null) environment.put("P4USER", user);
        if (passwd != null) environment.put("P4PASSWD", passwd);

        // Retrieve the basic info on the client to make sure that all params are OK.
        Client c = Client.invokeWith(this, client);
        if (c == null) throw new P4Exception("Client not found: " + client, null);
    }

}
