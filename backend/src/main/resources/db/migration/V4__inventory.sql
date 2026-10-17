-- Inventory module. Stock is decremented with a conditional UPDATE (quantity_on_hand >= requested),
-- so concurrent checkouts can never oversell; the CHECK constraint is a second line of defense.

CREATE TABLE inventory (
    product_id       BIGINT PRIMARY KEY REFERENCES products (id) ON DELETE CASCADE,
    quantity_on_hand INT         NOT NULL CHECK (quantity_on_hand >= 0),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
