-- Phase 5: customer-editable bundles. Recommended bundles stay immutable; "customize" creates a DRAFT copy
-- owned by the customer, which can be edited and then SAVED.

ALTER TABLE bundles DROP CONSTRAINT bundles_status_check;
ALTER TABLE bundles ADD CONSTRAINT ck_bundles_status CHECK (status IN ('RECOMMENDED', 'DRAFT', 'SAVED'));

-- provenance: the recommended bundle a draft was customized from
ALTER TABLE bundles ADD COLUMN source_bundle_id UUID REFERENCES bundles (id) ON DELETE SET NULL;

CREATE INDEX idx_bundles_user_updated ON bundles (user_id, updated_at DESC);
