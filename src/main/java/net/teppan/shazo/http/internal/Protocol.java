package net.teppan.shazo.http.internal;

/**
 * Wire-protocol constants shared between the server-side handler and the
 * client-side adapter.
 */
public final class Protocol {

    /** Verify whether an entity exists. */
    public static final byte OP_CONTAINS = 1;
    /** Persist an entity. */
    public static final byte OP_STORE    = 2;
    /** Remove an entity. */
    public static final byte OP_DELETE   = 3;
    /** Retrieve a single entity. */
    public static final byte OP_RETRIEVE = 4;
    /** Retrieve a collection of entities. */
    public static final byte OP_CATALOG  = 5;

    /** The operation completed without error. */
    public static final byte STATUS_OK        = 0;
    /** The requested entity was not found (retrieve only). */
    public static final byte STATUS_NOT_FOUND = 1;
    /** The operation failed; payload is a UTF-8 error message. */
    public static final byte STATUS_EXCEPTION = 2;

    private Protocol() {}
}
