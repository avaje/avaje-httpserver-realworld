-- // First migration.
CREATE EXTENSION citext;
CREATE EXTENSION pgcrypto;

CREATE SCHEMA realworld;


CREATE FUNCTION realworld.set_current_timestamp_updated_at()
    RETURNS TRIGGER AS $$
DECLARE
_new record;
BEGIN
  _new := NEW;
  _new."updated_at" = NOW();
RETURN _new;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE realworld.user (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    username text not null unique,
    password_hash bytea not null,
    email citext not null unique,
    bio text not null default '',
    image text,
    unique (username, email)
);

CREATE TRIGGER set_realworld_user_updated_at
    BEFORE UPDATE ON realworld.user
    FOR EACH ROW
    EXECUTE PROCEDURE realworld.set_current_timestamp_updated_at();

CREATE TABLE realworld.article (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    user_id uuid not null references realworld.user (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    slug text not null unique,
    title text not null,
    description text not null default '',
    body text not null default ''
);

create index on realworld.article using btree(user_id);

CREATE TRIGGER set_realworld_article_updated_at
    BEFORE UPDATE ON realworld.article
    FOR EACH ROW
    EXECUTE PROCEDURE realworld.set_current_timestamp_updated_at();

create table realworld.favorite (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    article_id uuid not null references realworld.article(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    user_id uuid not null references realworld.user(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    unique (article_id, user_id)
);


create index on realworld.favorite using btree(article_id);
create index on realworld.favorite using btree(user_id);

CREATE TRIGGER set_realworld_favorite_updated_at
    BEFORE UPDATE ON realworld.favorite
    FOR EACH ROW
EXECUTE PROCEDURE realworld.set_current_timestamp_updated_at();

create table realworld.follow (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    from_user_id uuid not null references realworld.user(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    to_user_id uuid not null references realworld.user(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    UNIQUE (from_user_id, to_user_id)
);

create index on realworld.follow using btree(from_user_id);
create index on realworld.follow using btree(to_user_id);

CREATE TRIGGER set_realworld_follow_updated_at
    BEFORE UPDATE ON realworld.follow
    FOR EACH ROW
EXECUTE PROCEDURE realworld.set_current_timestamp_updated_at();


create table realworld.tag (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    name text not null unique
);

CREATE TRIGGER set_realworld_tag_updated_at
    BEFORE UPDATE ON realworld.tag
    FOR EACH ROW
EXECUTE PROCEDURE realworld.set_current_timestamp_updated_at();

create table realworld.article_tag (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    article_id uuid not null references realworld.article(id)
                                   on update CASCADE
                                   on delete CASCADE,
    tag_id uuid not null references realworld.tag(id)
        on update CASCADE
        on delete CASCADE
);


create index on realworld.article_tag using btree(article_id);
create index on realworld.article_tag using btree(tag_id);

CREATE TRIGGER set_realworld_article_tag_updated_at
    BEFORE UPDATE ON realworld.article_tag
    FOR EACH ROW
EXECUTE PROCEDURE realworld.set_current_timestamp_updated_at();

create table realworld.comment(
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    article_id uuid not null references realworld.article(id)
                    on update CASCADE
                    on delete CASCADE,
    user_id uuid not null references realworld.user(id)
                    on update CASCADE
                    on delete CASCADE,
    body text not null default '',
    deleted boolean not null default false
);

CREATE TRIGGER set_realworld_comment_updated_at
    BEFORE UPDATE ON realworld.comment
    FOR EACH ROW
EXECUTE PROCEDURE realworld.set_current_timestamp_updated_at();

-- //@UNDO
-- DROP SCHEMA realworld cascade;
-- DROP EXTENSION citext;
-- DROP EXTENSION pgcrypto;
