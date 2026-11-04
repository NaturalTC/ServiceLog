-- Where each service address is on the map, looked up from the street address (free US
-- Census geocoder) so the phone can tell which customer's house it's at. Empty until the
-- lookup has run; geocoded_at is set even when the address couldn't be found, so it isn't
-- retried every minute, and cleared again when the address changes.
ALTER TABLE property
    ADD COLUMN latitude double precision,
    ADD COLUMN longitude double precision,
    ADD COLUMN geocoded_at timestamptz;
