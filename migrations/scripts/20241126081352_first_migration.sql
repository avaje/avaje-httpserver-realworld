-- // First migration.
CREATE EXTENSION citext;

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
    password_hash text not null,
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
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    slug text not null unique,
    title text not null,
    description text not null default '',
    body text not null default '',
    deleted boolean not null default false
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
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    user_id uuid not null references realworld.user(id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
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
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    to_user_id uuid not null references realworld.user(id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
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
                                   on update restrict
                                   on delete restrict,
    tag_id uuid not null references realworld.tag(id)
        on update restrict
        on delete restrict
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
                    on update restrict
                    on delete restrict,
    user_id uuid not null references realworld.user(id)
                    on update restrict
                    on delete restrict,
    body text not null default '',
    deleted boolean not null default false
);

CREATE TRIGGER set_realworld_comment_updated_at
    BEFORE UPDATE ON realworld.comment
    FOR EACH ROW
EXECUTE PROCEDURE realworld.set_current_timestamp_updated_at();

create table realworld.api_key(
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    user_id uuid not null references realworld.user(id)
                    on update restrict
                    on delete restrict,
    value text not null unique,
    invalidated_at timestamptz
);

CREATE TRIGGER set_realworld_api_key_updated_at
    BEFORE UPDATE ON realworld.api_key
    FOR EACH ROW
EXECUTE PROCEDURE realworld.set_current_timestamp_updated_at();

create index on realworld.api_key using btree(value);

-- //@UNDO
DROP SCHEMA realworld cascade;

DROP EXTENSION citext;