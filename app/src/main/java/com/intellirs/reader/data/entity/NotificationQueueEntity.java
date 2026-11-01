package com.intellirs.reader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Notification decision audit log. Deliberately denormalized so history survives article
 * cleanup. The explanation in decisionReason is shown to the user.
 */
@Entity(tableName = "notification_queue",
        indices = {
                @Index("articleId"),
                @Index("feedId"),
                @Index("notificationStatus"),
                @Index("createdAt"),
                @Index("postedAt"),
                @Index("contentHash"),
                @Index("groupKey")
        })
public class NotificationQueueEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long articleId;

    public long feedId;

    @NonNull
    public String articleTitle = "";

    @NonNull
    public String publisher = "";

    @NonNull
    public String contentHash = "";

    public boolean eligibility;

    @NonNull
    public String decisionReason = "";

    @NonNull
    public NotificationStatus notificationStatus = NotificationStatus.SKIPPED;

    @NonNull
    public String groupKey = "";

    @NonNull
    public String channelId = "";

    public long createdAt = System.currentTimeMillis();

    public long postedAt;
}
