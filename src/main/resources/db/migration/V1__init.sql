CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE post (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200)  NOT NULL,
    content    VARCHAR(4000) NOT NULL,
    author_id  BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_post_created_at ON post (created_at DESC);
