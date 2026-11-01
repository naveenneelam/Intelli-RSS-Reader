package com.intellirs.reader.domain.notification;

public class NotificationDecision {
    public final boolean eligible;
    public final String reason;
    public final String channelId;

    public NotificationDecision(boolean eligible, String reason, String channelId) {
        this.eligible = eligible;
        this.reason = reason;
        this.channelId = channelId;
    }
}
