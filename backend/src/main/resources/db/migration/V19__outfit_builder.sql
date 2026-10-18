-- Build Your Outfit: who the outfit is for and in which sizes and colors, plus outfit-matching rules.

-- ---------------------------------------------------------------------------------------------
-- Goals describe the wearer: wardrobe, sizes per size scale, preferred colors
-- ---------------------------------------------------------------------------------------------
ALTER TABLE shopping_goals
    ADD COLUMN gender     VARCHAR(10),
    ADD COLUMN size_alpha VARCHAR(12),
    ADD COLUMN size_waist VARCHAR(12),
    ADD CONSTRAINT ck_shopping_goals_gender CHECK (gender IN ('MEN', 'WOMEN'));

CREATE TABLE goal_preferred_colors (
    goal_id UUID        NOT NULL REFERENCES shopping_goals (id) ON DELETE CASCADE,
    color   VARCHAR(40) NOT NULL,
    PRIMARY KEY (goal_id, color)
);

-- ---------------------------------------------------------------------------------------------
-- Compatibility rules can apply across every category ("any" = NULL category), and two operators are
-- added for outfits:
--   MAX_DIFFERENCE  |source number - target number| <= rule_value   (formality within 2 levels)
--   LIST_DISJOINT   source and target lists share no value           (two pieces covering the same body area)
-- ---------------------------------------------------------------------------------------------
ALTER TABLE compatibility_rules ALTER COLUMN source_category_id DROP NOT NULL;
ALTER TABLE compatibility_rules DROP CONSTRAINT compatibility_rules_operator_check;
ALTER TABLE compatibility_rules DROP CONSTRAINT ck_compatibility_rules_shape;
ALTER TABLE compatibility_rules ADD CONSTRAINT ck_compatibility_rules_operator CHECK (operator IN (
    'EQUALS', 'LIST_CONTAINS', 'LIST_INTERSECTS', 'LIST_DISJOINT', 'LTE', 'MAX_DIFFERENCE',
    'LTE_SPACE_WIDTH', 'LTE_SPACE_DEPTH', 'REQUIRES_CATEGORY_UNLESS'));
ALTER TABLE compatibility_rules ADD CONSTRAINT ck_compatibility_rules_shape CHECK (
    (operator IN ('EQUALS', 'LIST_CONTAINS', 'LIST_INTERSECTS', 'LIST_DISJOINT', 'LTE') AND target_attr IS NOT NULL)
    OR (operator = 'MAX_DIFFERENCE' AND target_attr IS NOT NULL AND rule_value ~ '^[0-9]+(\.[0-9]+)?$')
    OR (operator IN ('LTE_SPACE_WIDTH', 'LTE_SPACE_DEPTH') AND target_category_id IS NULL AND source_category_id IS NOT NULL)
    OR (operator = 'REQUIRES_CATEGORY_UNLESS' AND source_category_id IS NOT NULL AND target_category_id IS NOT NULL
        AND rule_value IS NOT NULL));
