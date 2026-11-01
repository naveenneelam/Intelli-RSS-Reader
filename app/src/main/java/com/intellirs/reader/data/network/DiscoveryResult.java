package com.intellirs.reader.data.network;

import java.util.ArrayList;
import java.util.List;

public class DiscoveryResult {
    public final List<FeedCandidate> candidates = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public String inspectedUrl;
}
