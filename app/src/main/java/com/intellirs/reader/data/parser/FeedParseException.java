package com.intellirs.reader.data.parser;

/** A feed could not be parsed as RSS 2.0, RSS 1.0/RDF or Atom. */
public class FeedParseException extends Exception {
    public FeedParseException(String message) {
        super(message);
    }

    public FeedParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
