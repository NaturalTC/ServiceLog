-- The first lookups after V15 hit a moment when the map service answered "no match" for
-- addresses it does know. Clear those so they're looked up again; from now on a "not found"
-- is retried after a few days rather than kept for good.
UPDATE property SET geocoded_at = NULL WHERE latitude IS NULL;
