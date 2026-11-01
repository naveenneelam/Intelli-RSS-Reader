package com.intellirs.reader.data.model;

import androidx.room.Embedded;

import com.intellirs.reader.data.entity.DownloadQueueEntity;

public class DownloadItem {
    @Embedded
    public DownloadQueueEntity download;

    public String articleTitle;
    public String publisher;
    public String contentPath;
    public String audioPath;
}
