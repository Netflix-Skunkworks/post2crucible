package com.netflix.postreview.p4;

import com.netflix.postreview.Runner;

import java.io.IOException;
import java.util.Map;

/**
 * Representation of basic p4 client info, retrieved using the 'p4 clients -e' command.
 *
 * @author cquinn
 */
public class Client extends P4Command {
    public final String name;
    public final String owner;
    public final String root;
    public final String host;

    public static Client invokeWith(Runner runner, String name) throws IOException {
        return fromZtag(runner.execAndReadString(commandFor(name)));
    }

    @Override public String toString() {
        return "Client " + name + " owner " + owner + " root " + root + " host " + host;
    }

    private Client(String name, String owner, String root, String host) {
        this.name = name;
        this.owner = owner;
        this.root = root;
        this.host = host;
    }

    private static String[] commandFor(String name) {
        return new String[] { "p4", "-ztag", "clients", "-e", name };
    }

    private static Client fromZtag(String ztag) {
        Map<String, String> zmap = ztagMap(ztag);
        if (zmap.size() == 0) {
            return null;
        }
        return new Client(zmap.get("client"), zmap.get("Owner"), zmap.get("Root"), zmap.get("Host"));
    }

}
