package com.netflix.postreview.p4;

import com.netflix.postreview.Runner;

/**
 * Variant of Runner tailored for invoking p4
 */
public class P4Runner extends Runner {
    /**
     * Requires environment variables (P4USER, P4CLIENT, P4PORT, etc)
     */
    public P4Runner(String port, String client, String user, String passwd) {
        if (port != null) environment.put("P4PORT", port);
        if (client != null) environment.put("P4CLIENT", client);
        if (user != null) environment.put("P4USER", user);
        if (passwd != null) environment.put("P4PASSWD", passwd);
    }

}
