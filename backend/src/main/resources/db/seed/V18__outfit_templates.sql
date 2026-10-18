-- Build Your Outfit templates: one per occasion and wardrobe (men / women). Items are outfit slots
-- filled from the matching categories; context_tag is the occasion products are tagged with.

INSERT INTO goal_templates (slug, title, tagline, description, default_budget, sort_order, context_tag) VALUES
    ('weekend-casual-men', 'Weekend casual (men)', 'Easy layers for brunch, errands and day trips',
     'A relaxed tee and jeans with an optional jacket and a couple of accessories to finish it off.', 200.00, 1, 'weekend'),
    ('weekend-casual-women', 'Weekend casual (women)', 'Relaxed, put-together looks for your days off',
     'A comfortable top and jeans with an optional jacket, a bag and sunglasses.', 200.00, 2, 'weekend'),
    ('office-men', 'Office ready (men)', 'Sharp, comfortable pieces for the working week',
     'A shirt and tailored trousers with an optional knit layer, a belt and a watch.', 300.00, 3, 'office'),
    ('office-women', 'Office ready (women)', 'Polished workwear that still feels like you',
     'A blouse and tailored trousers with an optional blazer, a work bag and a watch.', 300.00, 4, 'office'),
    ('party-men', 'Party night (men)', 'Dressed up for a night out',
     'A sharp shirt and dark jeans with an optional jacket and a statement watch.', 250.00, 5, 'party'),
    ('party-women', 'Party night (women)', 'A standout look for celebrations',
     'A party dress with an optional jacket, a small bag and jewelry.', 250.00, 6, 'party'),
    ('date-night-men', 'Date night (men)', 'Relaxed but considered',
     'A smart shirt and slim jeans or trousers, with a jacket and a watch if you like.', 220.00, 7, 'date-night'),
    ('date-night-women', 'Date night (women)', 'Effortless and elegant',
     'A dress or a top with a skirt, finished with jewelry and a small bag.', 220.00, 8, 'date-night'),
    ('festive-men', 'Festive & wedding (men)', 'Traditional wear for weddings and festivals',
     'A kurta set or bandhgala with a watch and leather accessories.', 250.00, 9, 'festive'),
    ('festive-women', 'Festive & wedding (women)', 'Celebration-ready ethnic wear',
     'A kurta set or anarkali with jewelry, a bag and an optional scarf.', 250.00, 10, 'festive'),
    ('streetwear-men', 'Streetwear (men)', 'Oversized layers and sporty details',
     'A hoodie and jeans or joggers with a cap, a backpack and shades.', 200.00, 11, 'weekend'),
    ('streetwear-women', 'Streetwear (women)', 'Comfortable, confident street style',
     'A hoodie with jeans, plus a cap and a backpack.', 200.00, 12, 'weekend'),
    ('travel-men', 'Travel (men)', 'Comfortable layers that pack well',
     'A tee and easy trousers with a light jacket, a backpack and sunglasses.', 250.00, 13, 'travel'),
    ('travel-women', 'Travel (women)', 'Easy, comfortable and ready to go',
     'A breathable top and relaxed trousers with a layer, a backpack and a scarf.', 250.00, 14, 'travel'),
    ('workout-men', 'Workout (men)', 'Training gear that keeps up',
     'A training tee and joggers with a hoodie, a cap and a gym backpack.', 150.00, 15, 'workout'),
    ('workout-women', 'Workout (women)', 'Move freely, look good doing it',
     'A training top and joggers with a hoodie, a cap and a backpack.', 150.00, 16, 'workout');

INSERT INTO goal_template_items (template_id, category_id, required, selected_by_default, quantity, note, sort_order)
SELECT t.id, c.id, v.required, v.selected, 1, v.note, v.sort_order
FROM (VALUES
    ('weekend-casual-men', 'men-tshirts', TRUE, TRUE, 'The base of the look', 1),
    ('weekend-casual-men', 'men-jeans', TRUE, TRUE, NULL, 2),
    ('weekend-casual-men', 'men-jackets', FALSE, TRUE, 'A layer for cooler evenings', 3),
    ('weekend-casual-men', 'sunglasses', FALSE, TRUE, NULL, 4),
    ('weekend-casual-men', 'caps', FALSE, FALSE, NULL, 5),
    ('weekend-casual-men', 'watches', FALSE, FALSE, NULL, 6),
    ('weekend-casual-women', 'women-tops', TRUE, TRUE, 'The base of the look', 1),
    ('weekend-casual-women', 'women-jeans', TRUE, TRUE, NULL, 2),
    ('weekend-casual-women', 'women-jackets', FALSE, TRUE, 'A layer for cooler evenings', 3),
    ('weekend-casual-women', 'handbags', FALSE, TRUE, NULL, 4),
    ('weekend-casual-women', 'sunglasses', FALSE, FALSE, NULL, 5),
    ('weekend-casual-women', 'jewelry', FALSE, FALSE, NULL, 6),
    ('office-men', 'men-shirts', TRUE, TRUE, 'Crisp and well fitting', 1),
    ('office-men', 'men-trousers', TRUE, TRUE, NULL, 2),
    ('office-men', 'men-sweaters', FALSE, TRUE, 'A knit layer for air-conditioned offices', 3),
    ('office-men', 'belts', FALSE, TRUE, NULL, 4),
    ('office-men', 'watches', FALSE, TRUE, NULL, 5),
    ('office-men', 'wallets', FALSE, FALSE, NULL, 6),
    ('office-women', 'women-tops', TRUE, TRUE, 'A blouse or fine knit', 1),
    ('office-women', 'women-trousers', TRUE, TRUE, NULL, 2),
    ('office-women', 'women-jackets', FALSE, TRUE, 'A blazer sharpens the look', 3),
    ('office-women', 'handbags', FALSE, TRUE, 'Room for a laptop helps', 4),
    ('office-women', 'watches', FALSE, FALSE, NULL, 5),
    ('office-women', 'jewelry', FALSE, FALSE, NULL, 6),
    ('party-men', 'men-shirts', TRUE, TRUE, NULL, 1),
    ('party-men', 'men-jeans', TRUE, TRUE, 'Dark washes dress up best', 2),
    ('party-men', 'men-jackets', FALSE, TRUE, NULL, 3),
    ('party-men', 'watches', FALSE, TRUE, NULL, 4),
    ('party-men', 'belts', FALSE, FALSE, NULL, 5),
    ('party-women', 'women-dresses', TRUE, TRUE, 'The statement piece', 1),
    ('party-women', 'women-jackets', FALSE, FALSE, NULL, 2),
    ('party-women', 'handbags', FALSE, TRUE, 'A small bag keeps the look light', 3),
    ('party-women', 'jewelry', FALSE, TRUE, NULL, 4),
    ('party-women', 'watches', FALSE, FALSE, NULL, 5),
    ('date-night-men', 'men-shirts', TRUE, TRUE, NULL, 1),
    ('date-night-men', 'men-jeans', TRUE, TRUE, NULL, 2),
    ('date-night-men', 'men-jackets', FALSE, FALSE, NULL, 3),
    ('date-night-men', 'watches', FALSE, TRUE, NULL, 4),
    ('date-night-women', 'women-dresses', TRUE, TRUE, NULL, 1),
    ('date-night-women', 'jewelry', FALSE, TRUE, NULL, 2),
    ('date-night-women', 'handbags', FALSE, TRUE, NULL, 3),
    ('date-night-women', 'women-jackets', FALSE, FALSE, NULL, 4),
    ('festive-men', 'men-ethnic-wear', TRUE, TRUE, 'Kurta set or bandhgala', 1),
    ('festive-men', 'watches', FALSE, TRUE, NULL, 2),
    ('festive-men', 'wallets', FALSE, FALSE, NULL, 3),
    ('festive-women', 'women-ethnic-wear', TRUE, TRUE, 'Kurta set or anarkali', 1),
    ('festive-women', 'jewelry', FALSE, TRUE, NULL, 2),
    ('festive-women', 'handbags', FALSE, TRUE, NULL, 3),
    ('festive-women', 'scarves', FALSE, FALSE, NULL, 4),
    ('streetwear-men', 'men-hoodies', TRUE, TRUE, 'Oversized fits work best', 1),
    ('streetwear-men', 'men-jeans', TRUE, TRUE, NULL, 2),
    ('streetwear-men', 'caps', FALSE, TRUE, NULL, 3),
    ('streetwear-men', 'backpacks', FALSE, TRUE, NULL, 4),
    ('streetwear-men', 'sunglasses', FALSE, FALSE, NULL, 5),
    ('streetwear-women', 'women-hoodies', TRUE, TRUE, NULL, 1),
    ('streetwear-women', 'women-jeans', TRUE, TRUE, NULL, 2),
    ('streetwear-women', 'caps', FALSE, TRUE, NULL, 3),
    ('streetwear-women', 'backpacks', FALSE, FALSE, NULL, 4),
    ('travel-men', 'men-tshirts', TRUE, TRUE, NULL, 1),
    ('travel-men', 'men-trousers', TRUE, TRUE, 'Stretch and comfort for long journeys', 2),
    ('travel-men', 'men-jackets', FALSE, TRUE, NULL, 3),
    ('travel-men', 'backpacks', FALSE, TRUE, NULL, 4),
    ('travel-men', 'sunglasses', FALSE, FALSE, NULL, 5),
    ('travel-women', 'women-tops', TRUE, TRUE, NULL, 1),
    ('travel-women', 'women-trousers', TRUE, TRUE, NULL, 2),
    ('travel-women', 'women-hoodies', FALSE, TRUE, 'A cozy layer for flights', 3),
    ('travel-women', 'backpacks', FALSE, TRUE, NULL, 4),
    ('travel-women', 'scarves', FALSE, FALSE, NULL, 5),
    ('workout-men', 'men-tshirts', TRUE, TRUE, 'Quick-drying fabrics', 1),
    ('workout-men', 'men-trousers', TRUE, TRUE, 'Joggers or track pants', 2),
    ('workout-men', 'men-hoodies', FALSE, TRUE, NULL, 3),
    ('workout-men', 'caps', FALSE, FALSE, NULL, 4),
    ('workout-men', 'backpacks', FALSE, FALSE, NULL, 5),
    ('workout-women', 'women-tops', TRUE, TRUE, NULL, 1),
    ('workout-women', 'women-trousers', TRUE, TRUE, NULL, 2),
    ('workout-women', 'women-hoodies', FALSE, TRUE, NULL, 3),
    ('workout-women', 'caps', FALSE, FALSE, NULL, 4),
    ('workout-women', 'backpacks', FALSE, FALSE, NULL, 5)
) AS v(template, category, required, selected, note, sort_order)
JOIN goal_templates t ON t.slug = v.template
JOIN categories c ON c.slug = v.category;

-- The wizard's "how will you wear it?" choices map to style tags on products
INSERT INTO goal_template_usage_options (template_id, tag, label, sort_order)
SELECT t.id, v.tag, v.label, v.sort_order
FROM goal_templates t
CROSS JOIN (VALUES
    ('casual', 'Casual', 1),
    ('streetwear', 'Streetwear', 2),
    ('minimalist', 'Minimalist', 3),
    ('formal', 'Formal', 4),
    ('party-wear', 'Party wear', 5)
) AS v(tag, label, sort_order)
WHERE t.active AND t.slug IN ('weekend-casual-men', 'weekend-casual-women', 'office-men', 'office-women',
    'party-men', 'party-women', 'date-night-men', 'date-night-women', 'festive-men', 'festive-women',
    'streetwear-men', 'streetwear-women', 'travel-men', 'travel-women', 'workout-men', 'workout-women');

-- Phrases that identify the occasion (and wardrobe) in a free-text request
INSERT INTO goal_template_keywords (template_id, keyword)
SELECT t.id, k.keyword
FROM goal_templates t
JOIN (VALUES
    ('weekend-casual', 'weekend'), ('weekend-casual', 'casual'), ('weekend-casual', 'brunch'), ('weekend-casual', 'day out'),
    ('office', 'office'), ('office', 'work'), ('office', 'interview'), ('office', 'meeting'), ('office', 'business casual'),
    ('party', 'party'), ('party', 'night out'), ('party', 'club'), ('party', 'birthday'), ('party', 'cocktail'),
    ('date-night', 'date'), ('date-night', 'dinner'), ('date-night', 'anniversary'),
    ('festive', 'wedding'), ('festive', 'festive'), ('festive', 'diwali'), ('festive', 'eid'), ('festive', 'sangeet'),
    ('festive', 'ethnic'), ('festive', 'traditional'),
    ('streetwear', 'streetwear'), ('streetwear', 'street style'), ('streetwear', 'oversized'), ('streetwear', 'hoodie'),
    ('travel', 'travel'), ('travel', 'trip'), ('travel', 'vacation'), ('travel', 'holiday'), ('travel', 'flight'),
    ('workout', 'workout'), ('workout', 'gym'), ('workout', 'training'), ('workout', 'running'), ('workout', 'yoga')
) AS k(prefix, keyword) ON t.slug LIKE k.prefix || '-%'
UNION
SELECT t.id, g.keyword
FROM goal_templates t
JOIN (VALUES
    ('-men', 'men'), ('-men', 'man'), ('-men', 'mens'), ('-men', 'guy'), ('-men', 'him'), ('-men', 'boyfriend'),
    ('-men', 'husband'),
    ('-women', 'women'), ('-women', 'woman'), ('-women', 'womens'), ('-women', 'girl'), ('-women', 'her'),
    ('-women', 'girlfriend'), ('-women', 'wife'), ('-women', 'ladies')
) AS g(suffix, keyword) ON t.slug LIKE '%' || g.suffix
WHERE t.slug LIKE '%-men' OR t.slug LIKE '%-women';
