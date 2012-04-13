package com.netflix.postreview.p4;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * General superclass for p4 command objects. Each subclass corresponds to a p4 command, knows how to invoke it,
 * and parse its output. This superclass helps with parsing of standard p4 ztag formatted output.
 */
public class P4Command {
    static Pattern ZTAG_LINE_PAT = Pattern.compile("... (\\w+) (.*)", Pattern.DOTALL);

    static Map<String, String> ztagMap(String ztagOutput) {
        String[] lines = ztagOutput.split("\\r?\\n|\\r");
        Map<String, String> map = new HashMap<String, String>();
        String tag = null;
        String value = null;
        for (String line : lines) {
            if (line.startsWith("...")) {
                if (tag != null) {
                    //System.out.println(" ztag parsed: " + tag + " : " + value);
                    map.put(tag, value);
                    tag = null;
                }
                Matcher m = ZTAG_LINE_PAT.matcher(line);
                if (m.matches()) {
                    tag = m.group(1);
                    value = m.group(2);
                }
            } else {
                value = value + "\n" + line;
            }
        }
        if (tag != null) {
            //System.out.println(" ztag parsed: " + tag + " : " + value);
            map.put(tag, value);
        }
        return map;
    }

    protected static int parseInt0(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

}
