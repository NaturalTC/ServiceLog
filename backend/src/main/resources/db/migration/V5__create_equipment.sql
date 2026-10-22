-- Equipment installed at a property. Service records (S5) will hang off equipment.
CREATE TABLE equipment (
    id            BIGSERIAL PRIMARY KEY,
    -- RESTRICT: a property with equipment can't be deleted by accident
    property_id   BIGINT       NOT NULL REFERENCES property (id) ON DELETE RESTRICT,
    type          VARCHAR(30)  NOT NULL,
    brand         VARCHAR(100),
    model         VARCHAR(100),
    serial_number VARCHAR(100),
    install_date  DATE,
    notes         VARCHAR(2000),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT equipment_type_check CHECK (type IN (
        'OIL_BURNER', 'BOILER', 'FURNACE', 'CENTRAL_AC', 'HEAT_PUMP',
        'MINI_SPLIT_OUTDOOR', 'MINI_SPLIT_INDOOR', 'WATER_HEATER', 'OTHER'))
);

CREATE INDEX equipment_property_idx ON equipment (property_id);

-- Serial numbers are optional, but each one may be recorded only once (ignoring case)
CREATE UNIQUE INDEX equipment_serial_unique_idx ON equipment (lower(serial_number))
    WHERE serial_number IS NOT NULL;
