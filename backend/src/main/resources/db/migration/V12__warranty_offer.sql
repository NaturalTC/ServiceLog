-- When the owner offered a renewal or an extended warranty for a unit whose cover is
-- ending (S25). Cleared when the warranty end dates change, e.g. after a renewal.
ALTER TABLE equipment ADD COLUMN warranty_offer_made_on DATE;

-- For the "warranties ending soon" list
CREATE INDEX equipment_warranty_ends_idx ON equipment (warranty_ends_on) WHERE warranty_ends_on IS NOT NULL;
CREATE INDEX equipment_extended_ends_idx ON equipment (extended_warranty_ends_on)
    WHERE extended_warranty_ends_on IS NOT NULL;
