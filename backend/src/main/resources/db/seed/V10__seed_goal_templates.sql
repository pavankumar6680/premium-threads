-- Goal templates for the demo catalog. Category references are resolved by slug.

INSERT INTO goal_templates (slug, title, tagline, description, default_budget, sort_order) VALUES
    ('home-office', 'Professional home office', 'Everything for productive, comfortable remote work',
     'A complete work-from-home setup: an ergonomic seat and desk, a sharp monitor, quality input devices and good lighting for long days and video calls.',
     1200.00, 1),
    ('gaming-setup', 'Gaming setup', 'A high-refresh battle station, from PC to headset',
     'A gaming PC with a fast monitor, responsive peripherals, a supportive chair and a headset, balanced so no single part holds the rest back.',
     2500.00, 2),
    ('home-gym', 'Home gym', 'Strength training at home, sized for your space',
     'Free weights, a bench and protective flooring for full-body strength training, with optional cardio and conditioning gear.',
     800.00, 3),
    ('photo-studio', 'Photography studio', 'Camera, glass, light and support that work together',
     'A mirrorless camera with a compatible lens, lighting, a tripod that can hold it and the right memory card, plus a backdrop for clean shots.',
     3000.00, 4),
    ('study-space', 'Student study space', 'A focused, affordable place to study',
     'A compact desk, a comfortable chair and good task lighting, with optional tech for coursework and online classes.',
     600.00, 5),
    ('podcast-studio', 'Podcast studio', 'Broadcast-quality voice from your spare room',
     'A microphone with an arm that can hold it, monitoring headphones, an interface when the mic needs one, and acoustic treatment for a clean sound.',
     900.00, 6),
    ('smart-home', 'Smart home starter', 'Voice control, lighting and automation that play nicely together',
     'A hub-capable smart speaker with lighting and plugs from the same ecosystem, and optionally a video doorbell.',
     400.00, 7);

INSERT INTO goal_template_items (template_id, category_id, required, selected_by_default, quantity, note, sort_order)
SELECT t.id, c.id, v.required, v.selected, v.quantity, v.note, v.sort_order
FROM (VALUES
    ('home-office', 'desks', TRUE, TRUE, 1, 'Sized for your space; standing desks help long days', 1),
    ('home-office', 'chairs', TRUE, TRUE, 1, 'Lumbar support matters most for all-day comfort', 2),
    ('home-office', 'monitors', TRUE, TRUE, 1, 'QHD or 4K for crisp text and more screen space', 3),
    ('home-office', 'keyboards', TRUE, TRUE, 1, NULL, 4),
    ('home-office', 'mice', TRUE, TRUE, 1, NULL, 5),
    ('home-office', 'desk-lamps', FALSE, TRUE, 1, 'Good light reduces eye strain', 6),
    ('home-office', 'webcams', FALSE, TRUE, 1, 'Look sharp on video calls', 7),
    ('home-office', 'monitor-arms', FALSE, FALSE, 1, 'Frees desk space; must carry your monitor', 8),
    ('home-office', 'headphones', FALSE, FALSE, 1, 'Noise cancelling helps you focus', 9),
    ('gaming-setup', 'desktops', TRUE, TRUE, 1, 'The engine of the setup', 1),
    ('gaming-setup', 'monitors', TRUE, TRUE, 1, 'High refresh rate for smooth motion', 2),
    ('gaming-setup', 'keyboards', TRUE, TRUE, 1, NULL, 3),
    ('gaming-setup', 'mice', TRUE, TRUE, 1, NULL, 4),
    ('gaming-setup', 'headphones', TRUE, TRUE, 1, 'A mic is needed for team chat', 5),
    ('gaming-setup', 'chairs', TRUE, TRUE, 1, NULL, 6),
    ('gaming-setup', 'desks', FALSE, TRUE, 1, NULL, 7),
    ('gaming-setup', 'webcams', FALSE, FALSE, 1, 'For streaming your gameplay', 8),
    ('gaming-setup', 'smart-lighting', FALSE, FALSE, 1, 'Ambient light strips', 9),
    ('home-gym', 'dumbbells', TRUE, TRUE, 1, 'Adjustable dumbbells save the most space', 1),
    ('home-gym', 'benches', TRUE, TRUE, 1, NULL, 2),
    ('home-gym', 'exercise-mats', TRUE, TRUE, 1, 'Protects floors and joints', 3),
    ('home-gym', 'kettlebells', FALSE, TRUE, 1, NULL, 4),
    ('home-gym', 'resistance-bands', FALSE, TRUE, 1, NULL, 5),
    ('home-gym', 'cardio-machines', FALSE, FALSE, 1, 'Check the footprint fits your room', 6),
    ('photo-studio', 'cameras', TRUE, TRUE, 1, 'Sets the lens mount for the rest of the kit', 1),
    ('photo-studio', 'lenses', TRUE, TRUE, 1, 'Must match the camera mount', 2),
    ('photo-studio', 'tripods', TRUE, TRUE, 1, 'Must support the camera weight', 3),
    ('photo-studio', 'studio-lighting', TRUE, TRUE, 1, NULL, 4),
    ('photo-studio', 'memory-cards', TRUE, TRUE, 1, 'Must fit a camera card slot', 5),
    ('photo-studio', 'backdrops', FALSE, TRUE, 1, NULL, 6),
    ('photo-studio', 'monitors', FALSE, FALSE, 1, 'A color-accurate screen for editing', 7),
    ('study-space', 'desks', TRUE, TRUE, 1, 'Compact desks fit dorms and bedrooms', 1),
    ('study-space', 'chairs', TRUE, TRUE, 1, NULL, 2),
    ('study-space', 'desk-lamps', TRUE, TRUE, 1, NULL, 3),
    ('study-space', 'laptops', FALSE, FALSE, 1, 'For coursework and online classes', 4),
    ('study-space', 'headphones', FALSE, TRUE, 1, NULL, 5),
    ('study-space', 'monitors', FALSE, FALSE, 1, NULL, 6),
    ('study-space', 'keyboards', FALSE, FALSE, 1, NULL, 7),
    ('study-space', 'mice', FALSE, FALSE, 1, NULL, 8),
    ('podcast-studio', 'microphones', TRUE, TRUE, 1, 'The single biggest factor in sound quality', 1),
    ('podcast-studio', 'headphones', TRUE, TRUE, 1, 'Closed-back models stop bleed into the mic', 2),
    ('podcast-studio', 'mic-arms', TRUE, TRUE, 1, 'Must carry the microphone weight', 3),
    ('podcast-studio', 'audio-interfaces', FALSE, TRUE, 1, 'Required for XLR-only microphones', 4),
    ('podcast-studio', 'acoustic-panels', FALSE, TRUE, 1, NULL, 5),
    ('podcast-studio', 'webcams', FALSE, FALSE, 1, 'For video podcasts', 6),
    ('podcast-studio', 'desk-lamps', FALSE, FALSE, 1, 'A key light for video', 7),
    ('smart-home', 'smart-speakers', TRUE, TRUE, 1, 'Pick your ecosystem first', 1),
    ('smart-home', 'smart-lighting', TRUE, TRUE, 1, 'Must work with your speaker ecosystem', 2),
    ('smart-home', 'smart-plugs', FALSE, TRUE, 1, NULL, 3),
    ('smart-home', 'video-doorbells', FALSE, FALSE, 1, NULL, 4)
) AS v (template, category, required, selected, quantity, note, sort_order)
JOIN goal_templates t ON t.slug = v.template
JOIN categories c ON c.slug = v.category;

INSERT INTO goal_template_usage_options (template_id, tag, label, sort_order)
SELECT t.id, v.tag, v.label, v.sort_order
FROM (VALUES
    ('home-office', 'ergonomic', 'Ergonomic comfort', 1),
    ('home-office', 'streaming', 'Frequent video calls', 2),
    ('home-office', 'compact', 'Small space', 3),
    ('home-office', 'wireless', 'Wireless, clutter-free desk', 4),
    ('gaming-setup', 'gaming', 'Competitive gaming', 1),
    ('gaming-setup', 'streaming', 'Streaming my games', 2),
    ('gaming-setup', 'wireless', 'Wireless peripherals', 3),
    ('gaming-setup', 'ergonomic', 'Long sessions in comfort', 4),
    ('home-gym', 'beginner', 'New to training', 1),
    ('home-gym', 'compact', 'Small space', 2),
    ('home-gym', 'quiet', 'Quiet (apartment friendly)', 3),
    ('photo-studio', 'beginner', 'Learning photography', 1),
    ('photo-studio', 'pro', 'Professional work', 2),
    ('photo-studio', 'streaming', 'Video and streaming', 3),
    ('photo-studio', 'portable', 'Shooting on location', 4),
    ('study-space', 'compact', 'Small room or dorm', 1),
    ('study-space', 'study', 'Long study sessions', 2),
    ('study-space', 'portable', 'Studying on the go', 3),
    ('podcast-studio', 'beginner', 'First podcast', 1),
    ('podcast-studio', 'pro', 'Broadcast quality', 2),
    ('podcast-studio', 'streaming', 'Video podcast or streaming', 3),
    ('smart-home', 'beginner', 'Simple setup', 1)
) AS v (template, tag, label, sort_order)
JOIN goal_templates t ON t.slug = v.template;

INSERT INTO goal_template_keywords (template_id, keyword)
SELECT t.id, v.keyword
FROM (VALUES
    ('home-office', 'home office'), ('home-office', 'work from home'), ('home-office', 'wfh'),
    ('home-office', 'remote work'), ('home-office', 'office'), ('home-office', 'workspace'),
    ('home-office', 'work setup'),
    ('gaming-setup', 'gaming'), ('gaming-setup', 'gamer'), ('gaming-setup', 'battle station'),
    ('gaming-setup', 'battlestation'), ('gaming-setup', 'esports'), ('gaming-setup', 'games'),
    ('home-gym', 'gym'), ('home-gym', 'workout'), ('home-gym', 'fitness'), ('home-gym', 'strength'),
    ('home-gym', 'lifting'), ('home-gym', 'exercise'), ('home-gym', 'training'),
    ('photo-studio', 'photography'), ('photo-studio', 'photo'), ('photo-studio', 'photo studio'),
    ('photo-studio', 'photographer'), ('photo-studio', 'portrait'), ('photo-studio', 'shoot'),
    ('study-space', 'study'), ('study-space', 'student'), ('study-space', 'dorm'),
    ('study-space', 'homework'), ('study-space', 'college'), ('study-space', 'school'),
    ('podcast-studio', 'podcast'), ('podcast-studio', 'podcasting'), ('podcast-studio', 'recording'),
    ('podcast-studio', 'voice over'), ('podcast-studio', 'voiceover'), ('podcast-studio', 'broadcast'),
    ('smart-home', 'smart home'), ('smart-home', 'home automation'), ('smart-home', 'alexa'),
    ('smart-home', 'google home'), ('smart-home', 'apple home'), ('smart-home', 'automation')
) AS v (template, keyword)
JOIN goal_templates t ON t.slug = v.template;

DO $$
BEGIN
    IF (SELECT count(*) FROM goal_template_items) <> 50 THEN
        RAISE EXCEPTION 'Goal template items reference unknown categories (expected 50 rows, got %)',
            (SELECT count(*) FROM goal_template_items);
    END IF;
END $$;
