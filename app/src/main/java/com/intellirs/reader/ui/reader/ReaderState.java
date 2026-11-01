package com.intellirs.reader.ui.reader;

import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleNoteEntity;
import com.intellirs.reader.data.entity.ArticleTagEntity;
import com.intellirs.reader.data.entity.ArticleVersionEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.model.ArticleSource;

import java.util.ArrayList;
import java.util.List;

public class ReaderState {
    public ArticleEntity article;
    public FeedEntity feed;
    public String categoryName = "";
    public List<ArticleSource> sources = new ArrayList<>();
    public List<ArticleTagEntity> tags = new ArrayList<>();
    public ArticleNoteEntity note;
    public List<ArticleVersionEntity> versions = new ArrayList<>();
    public List<ArticleEntity> related = new ArrayList<>();
    public String html = "";
    public String baseUrl;
    public boolean fallbackContent;
    public float savedProgress;
    public long olderArticleId = -1L;
    public long newerArticleId = -1L;
    public String error;
}
