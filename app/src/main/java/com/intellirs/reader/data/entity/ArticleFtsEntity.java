package com.intellirs.reader.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Fts4;
import androidx.room.PrimaryKey;

/**
 * SQLite FTS4 index. It stores searchable text only; full HTML and binary content remain
 * in app-private files. Entries are maintained by ArticleRepository.
 */
@Fts4
@Entity(tableName = "article_fts")
public class ArticleFtsEntity {
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    public long rowId;

    public String title = "";

    public String description = "";

    @ColumnInfo(name = "body_text")
    public String bodyText = "";

    public String publisher = "";

    public String author = "";

    public String tags = "";

    public String notes = "";
}
