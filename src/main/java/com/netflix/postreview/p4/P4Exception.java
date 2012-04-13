package com.netflix.postreview.p4;

/**
 * A general exception that may be thrown when communicating with Perforce.
 */
public class P4Exception extends Exception {

    public P4Exception(String msg) {
        super(msg);
    }

    public P4Exception(String msg, Throwable cause) {
        super(msg, cause);
    }
}
