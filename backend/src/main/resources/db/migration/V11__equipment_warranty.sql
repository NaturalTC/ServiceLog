-- Warranty details on installed equipment (S24). Every unit the owner installs has to be
-- registered with the manufacturer for its warranty, and extended warranties are sold
-- through a dealer program, so both end dates are kept.
ALTER TABLE equipment
    ADD COLUMN warranty_registered         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN warranty_registered_on      DATE,
    ADD COLUMN warranty_ends_on            DATE,
    ADD COLUMN extended_warranty_ends_on   DATE,
    -- A registration date only makes sense on a registered unit
    ADD CONSTRAINT equipment_registration_date_check
        CHECK (warranty_registered OR warranty_registered_on IS NULL);

-- For the "not registered yet" list
CREATE INDEX equipment_unregistered_idx ON equipment (install_date)
    WHERE NOT warranty_registered AND install_date IS NOT NULL;
