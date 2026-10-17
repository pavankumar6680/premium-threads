-- Context tags for the demo goal templates and compatibility rules for the demo catalog.

UPDATE goal_templates t
SET context_tag = v.tag
FROM (VALUES
    ('home-office', 'home-office'), ('gaming-setup', 'gaming'), ('home-gym', 'home-gym'),
    ('photo-studio', 'photography'), ('study-space', 'study'), ('podcast-studio', 'podcast'),
    ('smart-home', 'smart-home')
) AS v (slug, tag)
WHERE t.slug = v.slug;

INSERT INTO compatibility_rules (code, source_category_id, target_category_id, operator, source_attr, target_attr,
                                 rule_value, label, message)
SELECT v.code, s.id, t.id, v.operator, v.source_attr, v.target_attr, v.rule_value, v.label, v.message
FROM (VALUES
    ('MONITOR_PC_PORTS', 'monitors', 'desktops', 'LIST_INTERSECTS', 'inputs', 'video_outputs', NULL,
     'shares a video connection', 'The monitor and the PC have no video connection in common.'),
    ('MONITOR_LAPTOP_PORTS', 'monitors', 'laptops', 'LIST_INTERSECTS', 'inputs', 'video_outputs', NULL,
     'shares a video connection', 'The monitor and the laptop have no video connection in common.'),
    ('MONITOR_ARM_LOAD', 'monitors', 'monitor-arms', 'LTE', 'weight_kg', 'max_load_kg', NULL,
     'within the arm''s load limit', 'The monitor is heavier than the arm can carry.'),
    ('MONITOR_ARM_SIZE', 'monitors', 'monitor-arms', 'LTE', 'screen_size_in', 'max_screen_in', NULL,
     'within the arm''s screen size limit', 'The monitor is larger than the arm supports.'),
    ('MONITOR_ARM_VESA', 'monitors', 'monitor-arms', 'LIST_CONTAINS', 'vesa', 'vesa_patterns', NULL,
     'matching VESA mount', 'The arm does not fit the monitor''s VESA mount.'),
    ('CPU_SOCKET', 'cpus', 'motherboards', 'EQUALS', 'socket', 'socket', NULL,
     'same CPU socket', 'The processor and the motherboard use different sockets.'),
    ('LENS_MOUNT', 'lenses', 'cameras', 'EQUALS', 'mount', 'mount', NULL,
     'same lens mount', 'The lens does not fit the camera''s lens mount.'),
    ('CARD_SLOT', 'memory-cards', 'cameras', 'LIST_CONTAINS', 'card_format', 'card_slots', NULL,
     'fits a card slot', 'The camera has no slot for this memory card.'),
    ('TRIPOD_LOAD', 'cameras', 'tripods', 'LTE', 'weight_kg', 'max_load_kg', NULL,
     'within the tripod''s load limit', 'The camera is heavier than the tripod can hold.'),
    ('MIC_ARM_LOAD', 'microphones', 'mic-arms', 'LTE', 'weight_kg', 'max_load_kg', NULL,
     'within the arm''s load limit', 'The microphone is heavier than the boom arm can hold.'),
    ('MIC_NEEDS_INTERFACE', 'microphones', 'audio-interfaces', 'REQUIRES_CATEGORY_UNLESS', 'connection', NULL, 'USB',
     'connects through the audio interface', 'This XLR-only microphone needs an audio interface.'),
    ('LIGHTING_ECOSYSTEM', 'smart-lighting', 'smart-speakers', 'LIST_INTERSECTS', 'ecosystems', 'ecosystems', NULL,
     'same smart home ecosystem', 'The lights do not work with the speaker''s smart home ecosystem.'),
    ('PLUG_ECOSYSTEM', 'smart-plugs', 'smart-speakers', 'LIST_INTERSECTS', 'ecosystems', 'ecosystems', NULL,
     'same smart home ecosystem', 'The plugs do not work with the speaker''s smart home ecosystem.'),
    ('DOORBELL_ECOSYSTEM', 'video-doorbells', 'smart-speakers', 'LIST_INTERSECTS', 'ecosystems', 'ecosystems', NULL,
     'same smart home ecosystem', 'The doorbell does not work with the speaker''s smart home ecosystem.'),
    ('DESK_WIDTH', 'desks', NULL, 'LTE_SPACE_WIDTH', 'width_cm', NULL, NULL,
     'fits your available width', 'The desk is wider than your available space.'),
    ('DESK_DEPTH', 'desks', NULL, 'LTE_SPACE_DEPTH', 'depth_cm', NULL, NULL,
     'fits your available depth', 'The desk is deeper than your available space.'),
    ('CARDIO_WIDTH', 'cardio-machines', NULL, 'LTE_SPACE_WIDTH', 'footprint_width_cm', NULL, NULL,
     'fits your available width', 'The machine is wider than your available space.'),
    ('CARDIO_LENGTH', 'cardio-machines', NULL, 'LTE_SPACE_DEPTH', 'footprint_length_cm', NULL, NULL,
     'fits your available depth', 'The machine is longer than your available space.')
) AS v (code, source_category, target_category, operator, source_attr, target_attr, rule_value, label, message)
JOIN categories s ON s.slug = v.source_category
LEFT JOIN categories t ON t.slug = v.target_category;

DO $$
BEGIN
    IF (SELECT count(*) FROM compatibility_rules) <> 18 THEN
        RAISE EXCEPTION 'Compatibility rules reference unknown categories';
    END IF;
    IF EXISTS (SELECT 1 FROM goal_templates WHERE context_tag IS NULL) THEN
        RAISE EXCEPTION 'Every seeded goal template needs a context tag';
    END IF;
END $$;
