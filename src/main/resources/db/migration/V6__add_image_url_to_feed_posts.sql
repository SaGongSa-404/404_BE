ALTER TABLE feed_posts
    ADD COLUMN IF NOT EXISTS image_url varchar(500);