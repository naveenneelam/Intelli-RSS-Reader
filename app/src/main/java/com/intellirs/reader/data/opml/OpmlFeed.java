package com.intellirs.reader.data.opml;

import java.io.Serializable;

public class OpmlFeed implements Serializable {
    public String title = "";
    public String feedUrl = "";
    public String websiteUrl;
    public String categoryPath = "";
    public String feedType = "RSS";
    public boolean duplicate;
    public String error;
}
