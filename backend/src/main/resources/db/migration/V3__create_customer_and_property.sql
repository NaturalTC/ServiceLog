-- A customer can have several properties (home, rental, a parent's house).
-- Equipment will belong to a property, not directly to a customer.
CREATE TABLE customer (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    -- Stored normalized as +1XXXXXXXXXX; the app formats it for display
    phone      VARCHAR(20)  NOT NULL,
    email      VARCHAR(254),
    notes      VARCHAR(2000),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE property (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    street      VARCHAR(200) NOT NULL,
    unit        VARCHAR(50),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(2)   NOT NULL,
    zip         VARCHAR(10)  NOT NULL,
    notes       VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX property_customer_idx ON property (customer_id);
