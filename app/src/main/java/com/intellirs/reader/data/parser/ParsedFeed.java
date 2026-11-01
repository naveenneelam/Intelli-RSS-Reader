package com.intellirs.reader.data.parser;

import java.util.ArrayList;
import java.util.List;

/** Normalized feed metadata and items, independent of RSS/Atom/RDF syntax. */
public class ParsedFeed {
    public String title = "";
    public String description = "";
    public String siteUrl;
    public String feedUrl;
    public String feedType = "RSS";
    public String iconUrl;
    public String language;
    public final List<String> categories = new ArrayList<>();
    public final List<ParsedArticle> articles = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
}
