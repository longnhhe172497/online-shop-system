-- Stream B: product reviews (UC-CUS-015) and Manager moderation (UC-MNG-005).
-- A Manager may answer a review publicly; the reply is shown under the review on the product page.
ALTER TABLE product_reviews
    ADD COLUMN manager_reply text,
    ADD COLUMN replied_by bigint REFERENCES users(id),
    ADD COLUMN replied_at timestamptz,
    ADD CONSTRAINT product_review_comment_length CHECK (comment IS NULL OR char_length(comment) <= 2000),
    ADD CONSTRAINT product_review_reply_length CHECK (manager_reply IS NULL OR char_length(manager_reply) <= 1000);

-- Product page: visible reviews of one product, newest first (joined through order_items.product_id).
CREATE INDEX order_items_product_idx ON order_items(product_id);
-- Manager list: filter by status, newest first.
CREATE INDEX product_reviews_status_created_idx ON product_reviews(status, created_at DESC);
