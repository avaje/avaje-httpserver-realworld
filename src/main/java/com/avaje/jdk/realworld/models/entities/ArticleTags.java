package com.avaje.jdk.realworld.models.entities;

import io.ebean.Model;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "realworld.article_tag")
public class ArticleTags extends Model {

  @EmbeddedId private ArticleTagId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "article_id", nullable = false, insertable = false, updatable = false)
  private final ArticleEntity article;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "tag_id", nullable = false, insertable = false, updatable = false)
  private final TagEntity tag;

  public ArticleTags(ArticleEntity article, TagEntity tag) {
    this.article = article;
    this.tag = tag;
  }

  public ArticleEntity article() {
    return article;
  }

  public TagEntity tag() {
    return tag;
  }

  public ArticleTagId id() {
    return id;
  }

  public void id(ArticleTagId id) {
    this.id = id;
  }

  @Embeddable
  public static record ArticleTagId(UUID articleId, UUID tagId) {}
}
