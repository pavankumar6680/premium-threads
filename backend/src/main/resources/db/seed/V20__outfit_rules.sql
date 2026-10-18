-- Outfit-matching rules for the fashion catalog. NULL categories mean "any category", so each rule is
-- checked between every two pieces of an outfit. The attributes are derived by the catalog for every product:
--   wardrobe  MEN / WOMEN (unisex products carry both)
--   covers    the part of the body a piece covers: upper, lower (a dress or kurta set covers both), outer
--   dress_code the formality (1 casual to 5 formal) of clothing; accessories have none, so a watch can dress
--             up a casual look without breaking the rule

INSERT INTO compatibility_rules (code, source_category_id, target_category_id, operator, source_attr, target_attr,
                                 rule_value, severity, label, message)
VALUES
    ('SAME_WARDROBE', NULL, NULL, 'LIST_INTERSECTS', 'wardrobe', 'wardrobe', NULL, 'BLOCK',
     'made for the same wardrobe',
     'This outfit mixes men''s and women''s pieces.'),
    ('DRESS_CODE', NULL, NULL, 'MAX_DIFFERENCE', 'dress_code', 'dress_code', '2', 'BLOCK',
     'a matching dress code',
     'One piece is much more formal than the other; they will look mismatched together.'),
    ('NO_DOUBLE_PIECES', NULL, NULL, 'LIST_DISJOINT', 'covers', 'covers', NULL, 'WARN',
     'no two pieces competing for the same spot',
     'Two pieces cover the same part of the outfit (for example a dress and a skirt).');
