-- Demo catalog. All brands and product names are fictional; specs and prices are realistic but invented.
-- Kept in db/seed so a deployment can exclude it (spring.flyway.locations=classpath:db/migration).

-- ---------------------------------------------------------------------------------------------
-- Categories: departments and leaf categories
-- ---------------------------------------------------------------------------------------------
INSERT INTO categories (slug, name, description, sort_order) VALUES
    ('home-office', 'Home Office & Furniture', 'Desks, chairs, lighting and ergonomic accessories.', 1),
    ('computers', 'Computers & Accessories', 'Monitors, PCs, laptops, components and peripherals.', 2),
    ('audio', 'Audio & Podcasting', 'Headphones, microphones and studio gear.', 3),
    ('photography', 'Photography & Video', 'Cameras, lenses, lighting and studio equipment.', 4),
    ('fitness', 'Fitness', 'Strength, cardio and recovery equipment for home gyms.', 5),
    ('smart-home', 'Smart Home', 'Speakers, lighting, plugs and security that work together.', 6);

INSERT INTO categories (parent_id, slug, name, description, sort_order)
SELECT p.id, v.slug, v.name, v.description, v.sort_order
FROM (VALUES
    ('home-office', 'desks', 'Desks', 'Writing, corner and standing desks.', 1),
    ('home-office', 'chairs', 'Chairs', 'Ergonomic office and gaming chairs.', 2),
    ('home-office', 'desk-lamps', 'Desk Lamps', 'Task lamps, light bars and key lights.', 3),
    ('home-office', 'monitor-arms', 'Monitor Arms', 'Single, dual and heavy-duty monitor arms.', 4),
    ('computers', 'monitors', 'Monitors', 'Office, creator and gaming monitors.', 1),
    ('computers', 'keyboards', 'Keyboards', 'Wireless, ergonomic and mechanical keyboards.', 2),
    ('computers', 'mice', 'Mice', 'Productivity, ergonomic and gaming mice.', 3),
    ('computers', 'webcams', 'Webcams', 'Webcams for calls and streaming.', 4),
    ('computers', 'desktops', 'Desktop PCs', 'Mini PCs, creator and gaming towers.', 5),
    ('computers', 'laptops', 'Laptops', 'Student, productivity, creator and gaming laptops.', 6),
    ('computers', 'cpus', 'Processors', 'Desktop processors.', 7),
    ('computers', 'motherboards', 'Motherboards', 'Desktop motherboards.', 8),
    ('audio', 'headphones', 'Headphones & Headsets', 'Everyday, studio and gaming headphones.', 1),
    ('audio', 'microphones', 'Microphones', 'USB and XLR microphones.', 2),
    ('audio', 'audio-interfaces', 'Audio Interfaces', 'Interfaces and podcast mixers.', 3),
    ('audio', 'mic-arms', 'Mic Arms', 'Boom arms for microphones.', 4),
    ('audio', 'acoustic-panels', 'Acoustic Treatment', 'Foam, fabric panels and bass traps.', 5),
    ('photography', 'cameras', 'Cameras', 'Mirrorless cameras.', 1),
    ('photography', 'lenses', 'Lenses', 'Prime and zoom lenses.', 2),
    ('photography', 'tripods', 'Tripods', 'Photo and video tripods.', 3),
    ('photography', 'studio-lighting', 'Studio Lighting', 'Ring lights, softboxes and LED panels.', 4),
    ('photography', 'backdrops', 'Backdrops', 'Muslin, paper and green screens.', 5),
    ('photography', 'memory-cards', 'Memory Cards', 'SD and CFexpress cards.', 6),
    ('fitness', 'dumbbells', 'Dumbbells', 'Fixed and adjustable dumbbells.', 1),
    ('fitness', 'kettlebells', 'Kettlebells', 'Cast iron, competition and adjustable kettlebells.', 2),
    ('fitness', 'benches', 'Benches', 'Flat and adjustable weight benches.', 3),
    ('fitness', 'exercise-mats', 'Mats & Flooring', 'Exercise mats and gym flooring.', 4),
    ('fitness', 'resistance-bands', 'Resistance Bands', 'Loop, tube and power bands.', 5),
    ('fitness', 'cardio-machines', 'Cardio Machines', 'Walking pads, treadmills, bikes and rowers.', 6),
    ('smart-home', 'smart-speakers', 'Smart Speakers & Hubs', 'Voice assistants and smart home hubs.', 1),
    ('smart-home', 'smart-lighting', 'Smart Lighting', 'Bulbs, strips and lamps.', 2),
    ('smart-home', 'smart-plugs', 'Smart Plugs', 'Plugs with scheduling and energy monitoring.', 3),
    ('smart-home', 'video-doorbells', 'Video Doorbells', 'Battery and wired video doorbells.', 4)
) AS v (parent, slug, name, description, sort_order)
JOIN categories p ON p.slug = v.parent;

-- ---------------------------------------------------------------------------------------------
-- Attribute definitions per leaf category
-- ---------------------------------------------------------------------------------------------
INSERT INTO attribute_definitions (category_id, key, label, data_type, unit, sort_order)
SELECT c.id, v.key, v.label, v.data_type, v.unit, v.sort_order
FROM (VALUES
    ('desks', 'width_cm', 'Width', 'NUMBER', 'cm', 1),
    ('desks', 'depth_cm', 'Depth', 'NUMBER', 'cm', 2),
    ('desks', 'height_adjustable', 'Height adjustable', 'BOOL', NULL, 3),
    ('desks', 'max_load_kg', 'Max load', 'NUMBER', 'kg', 4),
    ('desks', 'material', 'Top material', 'TEXT', NULL, 5),
    ('chairs', 'style', 'Style', 'TEXT', NULL, 1),
    ('chairs', 'lumbar_support', 'Lumbar support', 'BOOL', NULL, 2),
    ('chairs', 'adjustable_armrests', 'Adjustable armrests', 'BOOL', NULL, 3),
    ('chairs', 'max_weight_kg', 'Max user weight', 'NUMBER', 'kg', 4),
    ('chairs', 'material', 'Material', 'TEXT', NULL, 5),
    ('desk-lamps', 'brightness_lm', 'Brightness', 'NUMBER', 'lm', 1),
    ('desk-lamps', 'color_temp_adjustable', 'Adjustable color temperature', 'BOOL', NULL, 2),
    ('desk-lamps', 'power', 'Power', 'TEXT', NULL, 3),
    ('monitor-arms', 'max_load_kg', 'Max load per arm', 'NUMBER', 'kg', 1),
    ('monitor-arms', 'vesa_patterns', 'VESA patterns', 'LIST', NULL, 2),
    ('monitor-arms', 'max_screen_in', 'Max screen size', 'NUMBER', 'in', 3),
    ('monitor-arms', 'monitors_supported', 'Monitors supported', 'NUMBER', NULL, 4),
    ('monitors', 'screen_size_in', 'Screen size', 'NUMBER', 'in', 1),
    ('monitors', 'resolution', 'Resolution', 'TEXT', NULL, 2),
    ('monitors', 'refresh_hz', 'Refresh rate', 'NUMBER', 'Hz', 3),
    ('monitors', 'panel', 'Panel type', 'TEXT', NULL, 4),
    ('monitors', 'inputs', 'Inputs', 'LIST', NULL, 5),
    ('monitors', 'vesa', 'VESA mount', 'TEXT', NULL, 6),
    ('monitors', 'weight_kg', 'Weight', 'NUMBER', 'kg', 7),
    ('keyboards', 'layout', 'Layout', 'TEXT', NULL, 1),
    ('keyboards', 'switch_type', 'Switch type', 'TEXT', NULL, 2),
    ('keyboards', 'connection', 'Connection', 'LIST', NULL, 3),
    ('keyboards', 'backlight', 'Backlight', 'BOOL', NULL, 4),
    ('mice', 'connection', 'Connection', 'LIST', NULL, 1),
    ('mice', 'dpi_max', 'Max DPI', 'NUMBER', NULL, 2),
    ('mice', 'ergonomic', 'Ergonomic', 'BOOL', NULL, 3),
    ('mice', 'weight_g', 'Weight', 'NUMBER', 'g', 4),
    ('webcams', 'resolution', 'Resolution', 'TEXT', NULL, 1),
    ('webcams', 'fps', 'Frame rate', 'NUMBER', 'fps', 2),
    ('webcams', 'connection', 'Connection', 'TEXT', NULL, 3),
    ('webcams', 'autofocus', 'Autofocus', 'BOOL', NULL, 4),
    ('desktops', 'cpu', 'Processor', 'TEXT', NULL, 1),
    ('desktops', 'gpu', 'Graphics', 'TEXT', NULL, 2),
    ('desktops', 'ram_gb', 'Memory', 'NUMBER', 'GB', 3),
    ('desktops', 'storage_gb', 'Storage', 'NUMBER', 'GB', 4),
    ('desktops', 'video_outputs', 'Video outputs', 'LIST', NULL, 5),
    ('laptops', 'screen_size_in', 'Screen size', 'NUMBER', 'in', 1),
    ('laptops', 'ram_gb', 'Memory', 'NUMBER', 'GB', 2),
    ('laptops', 'storage_gb', 'Storage', 'NUMBER', 'GB', 3),
    ('laptops', 'gpu', 'Graphics', 'TEXT', NULL, 4),
    ('laptops', 'video_outputs', 'Video outputs', 'LIST', NULL, 5),
    ('laptops', 'weight_kg', 'Weight', 'NUMBER', 'kg', 6),
    ('cpus', 'socket', 'Socket', 'TEXT', NULL, 1),
    ('cpus', 'cores', 'Cores', 'NUMBER', NULL, 2),
    ('cpus', 'base_clock_ghz', 'Base clock', 'NUMBER', 'GHz', 3),
    ('cpus', 'tdp_w', 'TDP', 'NUMBER', 'W', 4),
    ('cpus', 'integrated_graphics', 'Integrated graphics', 'BOOL', NULL, 5),
    ('motherboards', 'socket', 'Socket', 'TEXT', NULL, 1),
    ('motherboards', 'form_factor', 'Form factor', 'TEXT', NULL, 2),
    ('motherboards', 'memory_type', 'Memory type', 'TEXT', NULL, 3),
    ('motherboards', 'wifi', 'Wi-Fi', 'BOOL', NULL, 4),
    ('headphones', 'style', 'Type', 'TEXT', NULL, 1),
    ('headphones', 'wireless', 'Wireless', 'BOOL', NULL, 2),
    ('headphones', 'microphone', 'Microphone', 'BOOL', NULL, 3),
    ('headphones', 'noise_cancelling', 'Active noise cancelling', 'BOOL', NULL, 4),
    ('headphones', 'connection', 'Connection', 'LIST', NULL, 5),
    ('microphones', 'connection', 'Connection', 'LIST', NULL, 1),
    ('microphones', 'polar_pattern', 'Polar pattern', 'TEXT', NULL, 2),
    ('microphones', 'mic_type', 'Capsule type', 'TEXT', NULL, 3),
    ('microphones', 'requires_phantom_power', 'Requires phantom power', 'BOOL', NULL, 4),
    ('microphones', 'weight_kg', 'Weight', 'NUMBER', 'kg', 5),
    ('audio-interfaces', 'xlr_inputs', 'XLR inputs', 'NUMBER', NULL, 1),
    ('audio-interfaces', 'phantom_power', 'Phantom power', 'BOOL', NULL, 2),
    ('audio-interfaces', 'connection', 'Connection', 'TEXT', NULL, 3),
    ('mic-arms', 'max_load_kg', 'Max load', 'NUMBER', 'kg', 1),
    ('mic-arms', 'mount', 'Mount', 'TEXT', NULL, 2),
    ('acoustic-panels', 'panel_count', 'Panels', 'NUMBER', NULL, 1),
    ('acoustic-panels', 'size_cm', 'Panel size', 'TEXT', 'cm', 2),
    ('acoustic-panels', 'nrc', 'NRC rating', 'NUMBER', NULL, 3),
    ('cameras', 'mount', 'Lens mount', 'TEXT', NULL, 1),
    ('cameras', 'sensor', 'Sensor', 'TEXT', NULL, 2),
    ('cameras', 'megapixels', 'Resolution', 'NUMBER', 'MP', 3),
    ('cameras', 'video', 'Max video', 'TEXT', NULL, 4),
    ('cameras', 'weight_kg', 'Weight', 'NUMBER', 'kg', 5),
    ('cameras', 'card_slots', 'Card slots', 'LIST', NULL, 6),
    ('lenses', 'mount', 'Lens mount', 'TEXT', NULL, 1),
    ('lenses', 'focal_length', 'Focal length', 'TEXT', NULL, 2),
    ('lenses', 'max_aperture', 'Max aperture', 'TEXT', NULL, 3),
    ('lenses', 'lens_type', 'Lens type', 'TEXT', NULL, 4),
    ('lenses', 'weight_kg', 'Weight', 'NUMBER', 'kg', 5),
    ('tripods', 'max_load_kg', 'Max load', 'NUMBER', 'kg', 1),
    ('tripods', 'max_height_cm', 'Max height', 'NUMBER', 'cm', 2),
    ('tripods', 'weight_kg', 'Weight', 'NUMBER', 'kg', 3),
    ('tripods', 'head', 'Head', 'TEXT', NULL, 4),
    ('studio-lighting', 'light_type', 'Light type', 'TEXT', NULL, 1),
    ('studio-lighting', 'power_w', 'Power', 'NUMBER', 'W', 2),
    ('studio-lighting', 'color_temp_range', 'Color temperature', 'TEXT', NULL, 3),
    ('studio-lighting', 'kit_pieces', 'Lights in kit', 'NUMBER', NULL, 4),
    ('backdrops', 'width_cm', 'Width', 'NUMBER', 'cm', 1),
    ('backdrops', 'material', 'Material', 'TEXT', NULL, 2),
    ('backdrops', 'color', 'Color', 'TEXT', NULL, 3),
    ('memory-cards', 'capacity_gb', 'Capacity', 'NUMBER', 'GB', 1),
    ('memory-cards', 'card_format', 'Format', 'TEXT', NULL, 2),
    ('memory-cards', 'speed_class', 'Speed class', 'TEXT', NULL, 3),
    ('dumbbells', 'dumbbell_type', 'Type', 'TEXT', NULL, 1),
    ('dumbbells', 'max_weight_kg', 'Max weight per dumbbell', 'NUMBER', 'kg', 2),
    ('dumbbells', 'pair', 'Sold as pair', 'BOOL', NULL, 3),
    ('kettlebells', 'weight_kg', 'Weight', 'NUMBER', 'kg', 1),
    ('kettlebells', 'material', 'Material', 'TEXT', NULL, 2),
    ('benches', 'adjustable', 'Adjustable', 'BOOL', NULL, 1),
    ('benches', 'max_load_kg', 'Max load', 'NUMBER', 'kg', 2),
    ('benches', 'foldable', 'Foldable', 'BOOL', NULL, 3),
    ('benches', 'length_cm', 'Length', 'NUMBER', 'cm', 4),
    ('exercise-mats', 'thickness_mm', 'Thickness', 'NUMBER', 'mm', 1),
    ('exercise-mats', 'length_cm', 'Length', 'NUMBER', 'cm', 2),
    ('exercise-mats', 'material', 'Material', 'TEXT', NULL, 3),
    ('resistance-bands', 'pieces', 'Pieces', 'NUMBER', NULL, 1),
    ('resistance-bands', 'max_resistance_kg', 'Max resistance', 'NUMBER', 'kg', 2),
    ('cardio-machines', 'machine_type', 'Type', 'TEXT', NULL, 1),
    ('cardio-machines', 'foldable', 'Foldable', 'BOOL', NULL, 2),
    ('cardio-machines', 'max_user_kg', 'Max user weight', 'NUMBER', 'kg', 3),
    ('cardio-machines', 'footprint_length_cm', 'Footprint length', 'NUMBER', 'cm', 4),
    ('cardio-machines', 'footprint_width_cm', 'Footprint width', 'NUMBER', 'cm', 5),
    ('smart-speakers', 'ecosystems', 'Ecosystems', 'LIST', NULL, 1),
    ('smart-speakers', 'hub_protocols', 'Built-in hub', 'LIST', NULL, 2),
    ('smart-speakers', 'screen', 'Screen', 'BOOL', NULL, 3),
    ('smart-lighting', 'ecosystems', 'Ecosystems', 'LIST', NULL, 1),
    ('smart-lighting', 'protocols', 'Protocols', 'LIST', NULL, 2),
    ('smart-lighting', 'pack_size', 'Pack size', 'NUMBER', NULL, 3),
    ('smart-lighting', 'color', 'Color', 'BOOL', NULL, 4),
    ('smart-lighting', 'requires_hub', 'Requires hub', 'BOOL', NULL, 5),
    ('smart-plugs', 'ecosystems', 'Ecosystems', 'LIST', NULL, 1),
    ('smart-plugs', 'protocols', 'Protocols', 'LIST', NULL, 2),
    ('smart-plugs', 'energy_monitoring', 'Energy monitoring', 'BOOL', NULL, 3),
    ('smart-plugs', 'pack_size', 'Pack size', 'NUMBER', NULL, 4),
    ('video-doorbells', 'ecosystems', 'Ecosystems', 'LIST', NULL, 1),
    ('video-doorbells', 'resolution', 'Resolution', 'TEXT', NULL, 2),
    ('video-doorbells', 'power', 'Power', 'TEXT', NULL, 3),
    ('video-doorbells', 'subscription_required', 'Subscription required', 'BOOL', NULL, 4)
) AS v (cat, key, label, data_type, unit, sort_order)
JOIN categories c ON c.slug = v.cat;

-- ---------------------------------------------------------------------------------------------
-- Products (staged, then expanded into products / inventory / tags / attributes)
-- ---------------------------------------------------------------------------------------------
CREATE TEMP TABLE seed_products (
    sku TEXT, cat TEXT, name TEXT, brand TEXT, price NUMERIC, rating NUMERIC, reviews INT, tier INT,
    stock INT, tags TEXT[], attrs JSONB, descr TEXT
) ON COMMIT DROP;

INSERT INTO seed_products VALUES
-- Desks
('DSK-001','desks','Northdesk Compact Writing Desk 100','Northdesk',129.00,4.2,860,1,40,'{study,home-office,compact}','{"width_cm":100,"depth_cm":50,"height_adjustable":false,"max_load_kg":50,"material":"Laminate"}','A compact laminate writing desk with a cable grommet, sized for small rooms and dorms.'),
('DSK-002','desks','Oakline Solid Oak Desk 140','Oakline',549.00,4.7,312,3,12,'{home-office}','{"width_cm":140,"depth_cm":70,"height_adjustable":false,"max_load_kg":80,"material":"Solid oak"}','Solid oak top on a powder-coated steel frame, built to last for decades.'),
('DSK-003','desks','Ergolift FlexiStand Desk 120','Ergolift',329.00,4.5,2140,2,30,'{home-office,ergonomic,study}','{"width_cm":120,"depth_cm":60,"height_adjustable":true,"max_load_kg":70,"material":"Laminate"}','Single-motor sit-stand desk with three memory presets and a 72 to 120 cm height range.'),
('DSK-004','desks','Ergolift ProStand Dual-Motor Desk 160','Ergolift',649.00,4.8,1580,3,18,'{home-office,ergonomic,gaming}','{"width_cm":160,"depth_cm":80,"height_adjustable":true,"max_load_kg":125,"material":"Bamboo"}','Dual-motor standing desk with anti-collision sensing, whisper-quiet motors and a bamboo top.'),
('DSK-005','desks','Northdesk Corner Desk L140','Northdesk',219.00,4.3,640,2,22,'{home-office,gaming}','{"width_cm":140,"depth_cm":120,"height_adjustable":false,"max_load_kg":60,"material":"Laminate"}','L-shaped corner desk with room for dual monitors and a separate writing surface.'),
('DSK-006','desks','Posturo Gaming Desk 140 RGB','Posturo',279.00,4.4,990,2,25,'{gaming}','{"width_cm":140,"depth_cm":65,"height_adjustable":false,"max_load_kg":70,"material":"Carbon-fiber texture"}','Carbon-texture gaming desk with RGB edge lighting, a cup holder and a headphone hook.'),
-- Chairs
('CHR-001','chairs','Sittwell Basic Task Chair','Sittwell',99.00,4.0,1720,1,60,'{study,home-office}','{"style":"office","lumbar_support":false,"adjustable_armrests":false,"max_weight_kg":100,"material":"Mesh"}','Lightweight mesh task chair with height adjustment and smooth-rolling casters.'),
('CHR-002','chairs','Sittwell Ergo Mesh Chair','Sittwell',249.00,4.5,2380,2,35,'{home-office,ergonomic,study}','{"style":"office","lumbar_support":true,"adjustable_armrests":true,"max_weight_kg":120,"material":"Mesh"}','Breathable mesh chair with adjustable lumbar support, 3D armrests and synchronized tilt.'),
('CHR-003','chairs','Posturo Executive Ergonomic Chair','Posturo',589.00,4.8,940,3,15,'{home-office,ergonomic}','{"style":"office","lumbar_support":true,"adjustable_armrests":true,"max_weight_kg":150,"material":"Mesh and aluminum"}','Fully adjustable ergonomic chair with dynamic lumbar support, seat depth adjustment and a 12-year warranty.'),
('CHR-004','chairs','Posturo Racer Gaming Chair','Posturo',199.00,4.2,3100,1,40,'{gaming}','{"style":"gaming","lumbar_support":true,"adjustable_armrests":true,"max_weight_kg":120,"material":"PU leather"}','Racing-style gaming chair with lumbar and neck pillows and 155 degree recline.'),
('CHR-005','chairs','Posturo Elite Gaming Chair','Posturo',429.00,4.7,1210,3,20,'{gaming,ergonomic}','{"style":"gaming","lumbar_support":true,"adjustable_armrests":true,"max_weight_kg":150,"material":"Fabric"}','Breathable fabric gaming chair with a magnetic head pillow, 4D armrests and integrated lumbar support.'),
('CHR-006','chairs','Oakline Study Chair','Oakline',139.00,4.3,610,2,30,'{study}','{"style":"office","lumbar_support":true,"adjustable_armrests":false,"max_weight_kg":110,"material":"Fabric"}','Compact upholstered study chair with a built-in lumbar curve, ideal for smaller desks.'),
-- Desk lamps
('LMP-001','desk-lamps','Lumora Clip LED Lamp','Lumora',29.00,4.1,2200,1,80,'{study,compact}','{"brightness_lm":400,"color_temp_adjustable":false,"power":"USB"}','Clip-on USB LED lamp with a flexible gooseneck that fits any shelf or desk edge.'),
('LMP-002','desk-lamps','Lumora Architect Desk Lamp','Lumora',79.00,4.5,930,2,45,'{home-office,study}','{"brightness_lm":800,"color_temp_adjustable":true,"power":"AC"}','Swing-arm LED desk lamp with adjustable color temperature and stepless dimming.'),
('LMP-003','desk-lamps','Brightnest Monitor Light Bar','Brightnest',59.00,4.6,3400,2,50,'{home-office,gaming}','{"brightness_lm":500,"color_temp_adjustable":true,"power":"USB"}','Asymmetric monitor light bar that lights your desk without glare on the screen.'),
('LMP-004','desk-lamps','Brightnest Pro Key Light','Brightnest',169.00,4.7,720,3,20,'{home-office,streaming,podcast}','{"brightness_lm":2500,"color_temp_adjustable":true,"power":"AC"}','App-controlled LED panel light for video calls and streaming, with a sturdy desk clamp.'),
-- Monitor arms
('ARM-001','monitor-arms','Mountix Single Monitor Arm','Mountix',39.00,4.3,1830,1,60,'{home-office,ergonomic}','{"max_load_kg":8,"vesa_patterns":["75x75","100x100"],"max_screen_in":32,"monitors_supported":1}','Single monitor arm with tilt, swivel and rotation that frees up desk space.'),
('ARM-002','monitor-arms','Mountix Gas Spring Monitor Arm','Mountix',89.00,4.6,1520,2,40,'{home-office,ergonomic}','{"max_load_kg":9,"vesa_patterns":["75x75","100x100"],"max_screen_in":34,"monitors_supported":1}','Gas spring arm for effortless one-hand positioning, with built-in cable management.'),
('ARM-003','monitor-arms','Armada Dual Monitor Arm','Armada',129.00,4.5,980,2,25,'{home-office,gaming}','{"max_load_kg":9,"vesa_patterns":["75x75","100x100"],"max_screen_in":32,"monitors_supported":2}','Dual gas spring arms with independent adjustment for two monitors side by side.'),
('ARM-004','monitor-arms','Armada Heavy Duty Monitor Arm','Armada',179.00,4.7,410,3,15,'{home-office,gaming}','{"max_load_kg":20,"vesa_patterns":["100x100","200x200"],"max_screen_in":49,"monitors_supported":1}','Heavy-duty arm for ultrawide and large monitors up to 20 kg.'),
-- Monitors
('MON-001','monitors','Vantix 24 FHD Monitor','Vantix',129.00,4.3,4200,1,70,'{home-office,study}','{"screen_size_in":24,"resolution":"1920x1080","refresh_hz":75,"panel":"IPS","inputs":["HDMI"],"vesa":"100x100","weight_kg":3.2}','24-inch Full HD IPS monitor with slim bezels and a flicker-free backlight.'),
('MON-002','monitors','Vantix 27 QHD Monitor','Vantix',249.00,4.5,2900,2,45,'{home-office}','{"screen_size_in":27,"resolution":"2560x1440","refresh_hz":75,"panel":"IPS","inputs":["HDMI","DisplayPort"],"vesa":"100x100","weight_kg":4.6}','27-inch QHD IPS monitor with crisp text and wide color for productivity.'),
('MON-003','monitors','Vantix 27 4K USB-C Monitor','Vantix',449.00,4.7,1340,3,25,'{home-office,photography}','{"screen_size_in":27,"resolution":"3840x2160","refresh_hz":60,"panel":"IPS","inputs":["HDMI","DisplayPort","USB-C"],"vesa":"100x100","weight_kg":5.8}','4K IPS monitor with 90 W USB-C charging and factory color calibration.'),
('MON-004','monitors','Pixelon 24 Gaming Monitor 165Hz','Pixelon',179.00,4.4,3800,1,50,'{gaming}','{"screen_size_in":24,"resolution":"1920x1080","refresh_hz":165,"panel":"IPS","inputs":["HDMI","DisplayPort"],"vesa":"100x100","weight_kg":3.6}','Fast 165 Hz IPS gaming monitor with 1 ms response time and adaptive sync.'),
('MON-005','monitors','Pixelon 27 QHD Gaming Monitor 170Hz','Pixelon',329.00,4.6,2100,2,35,'{gaming}','{"screen_size_in":27,"resolution":"2560x1440","refresh_hz":170,"panel":"IPS","inputs":["HDMI","DisplayPort"],"vesa":"100x100","weight_kg":5.1}','27-inch QHD gaming monitor at 170 Hz with HDR support.'),
('MON-006','monitors','Pixelon 34 Ultrawide OLED Monitor','Pixelon',899.00,4.8,640,3,4,'{gaming,home-office}','{"screen_size_in":34,"resolution":"3440x1440","refresh_hz":175,"panel":"OLED","inputs":["HDMI","DisplayPort","USB-C"],"vesa":"100x100","weight_kg":7.9}','34-inch curved OLED ultrawide with true blacks and near-instant response.'),
('MON-007','monitors','Clarion 32 4K Creator Monitor','Clarion',699.00,4.6,380,3,12,'{photography,home-office}','{"screen_size_in":32,"resolution":"3840x2160","refresh_hz":60,"panel":"IPS","inputs":["HDMI","DisplayPort","USB-C"],"vesa":"100x100","weight_kg":8.4}','32-inch 4K creator monitor covering 99 percent DCI-P3, with hardware calibration.'),
-- Keyboards
('KBD-001','keyboards','Glide Slim Wireless Keyboard','Glide',39.00,4.2,2600,1,80,'{home-office,study,wireless}','{"layout":"Full size","switch_type":"Membrane","connection":["Bluetooth","2.4GHz"],"backlight":false}','Quiet low-profile wireless keyboard that pairs with up to three devices.'),
('KBD-002','keyboards','Glide Ergo Split Keyboard','Glide',119.00,4.5,1150,2,30,'{home-office,ergonomic,wireless}','{"layout":"Full size","switch_type":"Membrane","connection":["Bluetooth","2.4GHz"],"backlight":true}','Split ergonomic keyboard with a cushioned palm rest and adjustable tenting.'),
('KBD-003','keyboards','Keyforge TKL Mechanical Keyboard','Keyforge',89.00,4.6,3100,2,45,'{gaming}','{"layout":"TKL","switch_type":"Mechanical","connection":["USB"],"backlight":true}','Tenkeyless mechanical keyboard with hot-swap switches and per-key RGB.'),
('KBD-004','keyboards','Keyforge 75 Wireless Mechanical Keyboard','Keyforge',169.00,4.7,1720,3,25,'{gaming,home-office,wireless}','{"layout":"75%","switch_type":"Mechanical","connection":["USB","Bluetooth","2.4GHz"],"backlight":true}','Gasket-mounted 75 percent keyboard with tri-mode wireless and pre-lubed switches.'),
('KBD-005','keyboards','Tactiq Pro Gaming Keyboard','Tactiq',199.00,4.6,890,3,20,'{gaming}','{"layout":"Full size","switch_type":"Optical","connection":["USB"],"backlight":true}','Optical-switch gaming keyboard with a media dial and a magnetic wrist rest.'),
-- Mice
('MSE-001','mice','Glide Basic Wireless Mouse','Glide',19.00,4.1,5100,1,120,'{study,home-office,wireless}','{"connection":["2.4GHz"],"dpi_max":1600,"ergonomic":false,"weight_g":85}','Compact wireless mouse with up to 18 months of battery life.'),
('MSE-002','mice','Glide Vertical Ergo Mouse','Glide',59.00,4.4,2300,2,40,'{home-office,ergonomic,wireless}','{"connection":["Bluetooth","2.4GHz"],"dpi_max":4000,"ergonomic":true,"weight_g":125}','Vertical mouse that keeps your wrist in a natural handshake position.'),
('MSE-003','mice','Glide MX Productivity Mouse','Glide',99.00,4.8,4100,3,35,'{home-office,wireless}','{"connection":["Bluetooth","2.4GHz"],"dpi_max":8000,"ergonomic":true,"weight_g":141}','Sculpted productivity mouse with a hyper-fast scroll wheel and multi-device switching.'),
('MSE-004','mice','Tactiq Lightweight Gaming Mouse','Tactiq',49.00,4.5,3600,2,60,'{gaming}','{"connection":["USB"],"dpi_max":26000,"ergonomic":false,"weight_g":59}','59 g gaming mouse with a flawless 26K sensor and a flexible paracord-style cable.'),
('MSE-005','mice','Tactiq Pro Wireless Gaming Mouse','Tactiq',129.00,4.7,1900,3,30,'{gaming,wireless}','{"connection":["USB","2.4GHz"],"dpi_max":30000,"ergonomic":false,"weight_g":63}','Pro-grade wireless gaming mouse with 70-hour battery life and high polling rate.'),
-- Webcams
('CAM-001','webcams','Visio 1080p Webcam','Visio',39.00,4.1,3900,1,70,'{home-office,study}','{"resolution":"1080p","fps":30,"connection":"USB-A","autofocus":false}','Plug-and-play 1080p webcam with a built-in privacy shutter.'),
('CAM-002','webcams','Visio 1080p60 Autofocus Webcam','Visio',79.00,4.4,2100,2,40,'{home-office,streaming}','{"resolution":"1080p","fps":60,"connection":"USB-A","autofocus":true}','Smooth 60 fps webcam with autofocus and automatic light correction.'),
('CAM-003','webcams','Streamly 4K Pro Webcam','Streamly',169.00,4.6,980,3,20,'{streaming,podcast,home-office}','{"resolution":"4K","fps":30,"connection":"USB-C","autofocus":true}','4K webcam with a large sensor, HDR and automatic framing.'),
('CAM-004','webcams','Streamly StreamCam 60','Streamly',119.00,4.5,1300,2,25,'{streaming,gaming}','{"resolution":"1080p","fps":60,"connection":"USB-C","autofocus":true}','Streaming webcam with vertical video mode and smart exposure.'),
-- Desktop PCs
('PC-001','desktops','Novabyte Office Mini PC','Novabyte',449.00,4.3,820,1,25,'{home-office,study,compact}','{"cpu":"Intara Core 5","gpu":"Integrated","ram_gb":16,"storage_gb":512,"video_outputs":["HDMI","DisplayPort"]}','Palm-sized mini PC for office work, with dual 4K display support.'),
('PC-002','desktops','Novabyte Creator Tower','Novabyte',1299.00,4.6,310,2,12,'{home-office,photography}','{"cpu":"Zentra 7 9700","gpu":"12 GB discrete","ram_gb":32,"storage_gb":2000,"video_outputs":["HDMI","DisplayPort"]}','Quiet creator workstation for photo editing, video and 3D work.'),
('PC-003','desktops','Corvex Gaming Tower 1080p','Corvex',999.00,4.5,1450,1,15,'{gaming}','{"cpu":"Zentra 5 9600","gpu":"8 GB discrete","ram_gb":16,"storage_gb":1000,"video_outputs":["HDMI","DisplayPort"]}','Ready-to-play gaming PC tuned for high frame rates at 1080p.'),
('PC-004','desktops','Corvex Gaming Tower 1440p','Corvex',1599.00,4.7,870,2,10,'{gaming,streaming}','{"cpu":"Zentra 7 9800","gpu":"12 GB discrete","ram_gb":32,"storage_gb":2000,"video_outputs":["HDMI","DisplayPort"]}','High-refresh 1440p gaming and streaming PC with tempered glass and liquid cooling.'),
('PC-005','desktops','Corvex Gaming Tower 4K Ultra','Corvex',2799.00,4.8,290,3,0,'{gaming,streaming}','{"cpu":"Intara Core 9","gpu":"16 GB discrete","ram_gb":64,"storage_gb":4000,"video_outputs":["HDMI","DisplayPort","USB-C"]}','Flagship 4K gaming PC with top-tier graphics and 64 GB of memory.'),
-- Laptops
('LTP-001','laptops','Novabyte Air 14 Student Laptop','Novabyte',599.00,4.3,1900,1,30,'{study,portable}','{"screen_size_in":14,"ram_gb":8,"storage_gb":256,"gpu":"Integrated","video_outputs":["HDMI","USB-C"],"weight_kg":1.3}','Light 14-inch laptop with all-day battery life for classes and coursework.'),
('LTP-002','laptops','Novabyte Book 15 Pro Laptop','Novabyte',1099.00,4.6,1150,2,20,'{home-office,study}','{"screen_size_in":15.6,"ram_gb":16,"storage_gb":1000,"gpu":"Integrated","video_outputs":["HDMI","USB-C"],"weight_kg":1.7}','15.6-inch productivity laptop with a 2.8K display and fast USB-C docking.'),
('LTP-003','laptops','Corvex Gaming Laptop 15','Corvex',1299.00,4.5,980,2,14,'{gaming,portable}','{"screen_size_in":15.6,"ram_gb":16,"storage_gb":1000,"gpu":"8 GB discrete","video_outputs":["HDMI","USB-C"],"weight_kg":2.3}','Portable gaming laptop with a 165 Hz display and an advanced cooling system.'),
('LTP-004','laptops','Corvex Blade 16 Gaming Laptop','Corvex',2299.00,4.7,410,3,8,'{gaming,streaming}','{"screen_size_in":16,"ram_gb":32,"storage_gb":2000,"gpu":"12 GB discrete","video_outputs":["HDMI","USB-C"],"weight_kg":2.4}','Premium 16-inch gaming laptop with an aluminum chassis and a QHD 240 Hz display.'),
('LTP-005','laptops','Novabyte Studio 16 Creator Laptop','Novabyte',1899.00,4.7,360,3,9,'{photography,home-office}','{"screen_size_in":16,"ram_gb":32,"storage_gb":2000,"gpu":"8 GB discrete","video_outputs":["HDMI","USB-C"],"weight_kg":2.0}','Color-accurate OLED creator laptop for photo and video editing on the go.'),
-- Processors
('CPU-001','cpus','Zentra 5 9600 Processor','Zentra',229.00,4.7,2100,1,30,'{gaming}','{"socket":"AM5","cores":6,"base_clock_ghz":3.9,"tdp_w":65,"integrated_graphics":true}','Efficient 6-core desktop processor with excellent gaming performance per dollar.'),
('CPU-002','cpus','Zentra 7 9800 Processor','Zentra',449.00,4.9,1800,3,3,'{gaming,streaming}','{"socket":"AM5","cores":8,"base_clock_ghz":4.2,"tdp_w":120,"integrated_graphics":true}','8-core gaming flagship with a large on-die cache for top frame rates.'),
('CPU-003','cpus','Intara Core 5 245 Processor','Intara',299.00,4.5,950,2,22,'{gaming,home-office}','{"socket":"LGA1851","cores":14,"base_clock_ghz":4.2,"tdp_w":125,"integrated_graphics":true}','14-core hybrid processor balanced for gaming and multitasking.'),
('CPU-004','cpus','Intara Core 7 14700 Processor','Intara',349.00,4.6,1400,2,20,'{gaming,streaming}','{"socket":"LGA1700","cores":20,"base_clock_ghz":3.4,"tdp_w":125,"integrated_graphics":true}','20-core processor for gaming while streaming and content creation.'),
-- Motherboards
('MB-001','motherboards','Boardwell A6M Wi-Fi Motherboard','Boardwell',159.00,4.5,1200,1,25,'{gaming}','{"socket":"AM5","form_factor":"mATX","memory_type":"DDR5","wifi":true}','Compact micro-ATX board with DDR5 support, Wi-Fi and a PCIe 4.0 graphics slot.'),
('MB-002','motherboards','Archon X8 ATX Motherboard','Archon',289.00,4.7,640,3,12,'{gaming}','{"socket":"AM5","form_factor":"ATX","memory_type":"DDR5","wifi":true}','Enthusiast ATX board with robust power delivery and PCIe 5.0 storage.'),
('MB-003','motherboards','Boardwell Z8 ATX Motherboard','Boardwell',259.00,4.5,520,2,15,'{gaming}','{"socket":"LGA1851","form_factor":"ATX","memory_type":"DDR5","wifi":true}','Feature-rich ATX board with Wi-Fi 7, four M.2 slots and DDR5 support.'),
('MB-004','motherboards','Archon B7M Motherboard','Archon',139.00,4.3,880,1,20,'{gaming}','{"socket":"LGA1700","form_factor":"mATX","memory_type":"DDR4","wifi":false}','Value micro-ATX board that pairs with affordable DDR4 memory.'),
-- Headphones
('HP-001','headphones','Auralis Everyday Headphones','Auralis',49.00,4.2,3800,1,70,'{study,home-office}','{"style":"Over-ear","wireless":true,"microphone":true,"noise_cancelling":false,"connection":["Bluetooth","3.5mm"]}','Comfortable wireless headphones with 40-hour battery and a built-in mic.'),
('HP-002','headphones','Auralis Quiet ANC Headphones','Auralis',249.00,4.7,5200,3,30,'{home-office,study,wireless}','{"style":"Over-ear","wireless":true,"microphone":true,"noise_cancelling":true,"connection":["Bluetooth","USB"]}','Industry-leading noise cancelling headphones for focused work and travel.'),
('HP-003','headphones','Sonaro Studio Monitor Headphones','Sonaro',99.00,4.6,2900,2,35,'{podcast,streaming}','{"style":"Over-ear","wireless":false,"microphone":false,"noise_cancelling":false,"connection":["3.5mm"]}','Closed-back studio headphones with a flat response for monitoring and editing.'),
('HP-004','headphones','Sonaro Pro Reference Headphones','Sonaro',299.00,4.8,780,3,12,'{podcast}','{"style":"Over-ear","wireless":false,"microphone":false,"noise_cancelling":false,"connection":["3.5mm"]}','Reference-grade studio headphones with detachable cables and velour pads.'),
('HP-005','headphones','Tactiq Gaming Headset','Tactiq',69.00,4.3,4100,1,55,'{gaming}','{"style":"Over-ear","wireless":false,"microphone":true,"noise_cancelling":false,"connection":["USB","3.5mm"]}','Lightweight gaming headset with virtual surround and a flip-to-mute mic.'),
('HP-006','headphones','Tactiq Wireless Pro Headset','Tactiq',179.00,4.6,1600,3,25,'{gaming,wireless,streaming}','{"style":"Over-ear","wireless":true,"microphone":true,"noise_cancelling":true,"connection":["USB","2.4GHz","Bluetooth"]}','Low-latency wireless gaming headset with noise cancelling and a broadcast-grade mic.'),
-- Microphones
('MIC-001','microphones','Vocalis USB Podcast Mic','Vocalis',59.00,4.3,3300,1,50,'{podcast,streaming,beginner}','{"connection":["USB"],"polar_pattern":"Cardioid","mic_type":"Condenser","requires_phantom_power":false,"weight_kg":0.35}','Plug-and-play USB condenser mic with a desk stand and headphone monitoring.'),
('MIC-002','microphones','Vocalis Dynamic USB/XLR Mic','Vocalis',99.00,4.6,2700,2,40,'{podcast,streaming}','{"connection":["USB","XLR"],"polar_pattern":"Cardioid","mic_type":"Dynamic","requires_phantom_power":false,"weight_kg":0.55}','Dynamic mic with both USB and XLR outputs, so it grows with your setup.'),
('MIC-003','microphones','Sonaro Broadcast Dynamic Mic','Sonaro',349.00,4.8,1100,3,15,'{podcast,pro}','{"connection":["XLR"],"polar_pattern":"Cardioid","mic_type":"Dynamic","requires_phantom_power":false,"weight_kg":1.1}','Broadcast-standard dynamic mic with warm, focused vocals and excellent noise rejection.'),
('MIC-004','microphones','Sonaro Studio Condenser Mic','Sonaro',199.00,4.6,860,2,18,'{podcast}','{"connection":["XLR"],"polar_pattern":"Cardioid","mic_type":"Condenser","requires_phantom_power":true,"weight_kg":0.6}','Large-diaphragm studio condenser mic for detailed, airy vocal recordings.'),
('MIC-005','microphones','Vocalis Streamer Mic RGB','Vocalis',129.00,4.4,1400,2,30,'{streaming,gaming}','{"connection":["USB"],"polar_pattern":"Supercardioid","mic_type":"Condenser","requires_phantom_power":false,"weight_kg":0.45}','Streaming mic with tap-to-mute, RGB lighting and built-in pop filter.'),
-- Audio interfaces
('AIF-001','audio-interfaces','Wavecraft Solo Audio Interface','Wavecraft',119.00,4.5,2400,1,35,'{podcast,beginner}','{"xlr_inputs":1,"phantom_power":true,"connection":"USB-C"}','One-input USB-C audio interface with a clean preamp and phantom power.'),
('AIF-002','audio-interfaces','Wavecraft Duo Audio Interface','Wavecraft',189.00,4.7,1900,2,25,'{podcast}','{"xlr_inputs":2,"phantom_power":true,"connection":"USB-C"}','Two-input interface for recording a host and guest side by side.'),
('AIF-003','audio-interfaces','Wavecraft Podcast Mixer 4','Wavecraft',349.00,4.6,520,3,12,'{podcast,pro}','{"xlr_inputs":4,"phantom_power":true,"connection":"USB-C"}','Podcast production console with four mic inputs, faders and sound pads.'),
-- Mic arms
('MAR-001','mic-arms','Mountix Basic Boom Arm','Mountix',29.00,4.0,2800,1,60,'{podcast,streaming}','{"max_load_kg":1.0,"mount":"Clamp"}','Spring-loaded boom arm with a desk clamp for light microphones.'),
('MAR-002','mic-arms','Mountix Low-Profile Boom Arm','Mountix',99.00,4.7,1500,2,30,'{podcast,streaming}','{"max_load_kg":1.5,"mount":"Clamp"}','Smooth low-profile boom arm with internal cable routing.'),
('MAR-003','mic-arms','Armada Heavy Broadcast Arm','Armada',149.00,4.6,430,3,15,'{podcast,pro}','{"max_load_kg":2.0,"mount":"Clamp or grommet"}','Heavy-duty broadcast arm for large microphones, silent in every position.'),
-- Acoustic treatment
('ACP-001','acoustic-panels','Wavecraft Foam Panels 12-Pack','Wavecraft',39.00,4.0,2100,1,50,'{podcast,streaming}','{"panel_count":12,"size_cm":"30x30","nrc":0.6}','Acoustic foam tiles that tame echo in small recording spaces.'),
('ACP-002','acoustic-panels','Wavecraft Fabric Panels 6-Pack','Wavecraft',149.00,4.6,640,2,20,'{podcast}','{"panel_count":6,"size_cm":"60x60","nrc":0.85}','Fabric-wrapped absorption panels that look great on camera.'),
('ACP-003','acoustic-panels','Wavecraft Pro Bass Traps 4-Pack','Wavecraft',199.00,4.7,310,3,12,'{podcast,pro}','{"panel_count":4,"size_cm":"60x30","nrc":0.95}','Corner bass traps that control low-frequency buildup.'),
-- Cameras
('CMR-001','cameras','Fotonix FX-30 Mirrorless Camera','Fotonix',699.00,4.5,1400,1,15,'{photography,beginner}','{"mount":"FX","sensor":"APS-C","megapixels":24,"video":"4K30","weight_kg":0.45,"card_slots":["SD"]}','Beginner-friendly mirrorless camera with fast autofocus and a flip screen.'),
('CMR-002','cameras','Fotonix FX-50 Mirrorless Camera','Fotonix',1299.00,4.7,820,2,10,'{photography,streaming}','{"mount":"FX","sensor":"APS-C","megapixels":33,"video":"4K60","weight_kg":0.51,"card_slots":["SD"]}','Hybrid photo and video camera with in-body stabilization and 4K60 recording.'),
('CMR-003','cameras','Fotonix FX-9 Full-Frame Camera','Fotonix',2499.00,4.8,410,3,6,'{photography,pro}','{"mount":"FX","sensor":"Full-frame","megapixels":45,"video":"4K120","weight_kg":0.72,"card_slots":["SD","CFexpress B"]}','High-resolution full-frame camera with dual card slots for professional work.'),
('CMR-004','cameras','Optima OM-1 Mirrorless Camera','Optima',899.00,4.5,690,2,12,'{photography}','{"mount":"OM","sensor":"APS-C","megapixels":26,"video":"4K60","weight_kg":0.48,"card_slots":["SD"]}','Compact weather-sealed mirrorless camera with classic dials.'),
('CMR-005','cameras','Optima OM-R Full-Frame Camera','Optima',2199.00,4.7,300,3,7,'{photography,pro}','{"mount":"OM","sensor":"Full-frame","megapixels":40,"video":"4K120","weight_kg":0.68,"card_slots":["CFexpress B"]}','Full-frame camera with blazing burst speed and pro video features.'),
-- Lenses
('LNS-001','lenses','Fotonix FX 50mm f/1.8 Prime Lens','Fotonix',249.00,4.7,1900,1,25,'{photography,beginner}','{"mount":"FX","focal_length":"50mm","max_aperture":"f/1.8","lens_type":"Prime","weight_kg":0.19}','Light, sharp standard prime for portraits and everyday shooting.'),
('LNS-002','lenses','Fotonix FX 24-70mm f/2.8 Zoom Lens','Fotonix',1599.00,4.8,520,3,8,'{photography,pro}','{"mount":"FX","focal_length":"24-70mm","max_aperture":"f/2.8","lens_type":"Zoom","weight_kg":0.88}','Professional standard zoom with constant f/2.8 aperture.'),
('LNS-003','lenses','Optima OM 35mm f/1.4 Prime Lens','Optima',599.00,4.6,410,2,10,'{photography}','{"mount":"OM","focal_length":"35mm","max_aperture":"f/1.4","lens_type":"Prime","weight_kg":0.36}','Bright wide-angle prime for street, travel and low light.'),
('LNS-004','lenses','Optima OM 70-200mm f/4 Zoom Lens','Optima',1299.00,4.7,290,3,6,'{photography,pro}','{"mount":"OM","focal_length":"70-200mm","max_aperture":"f/4","lens_type":"Zoom","weight_kg":0.79}','Telephoto zoom for sports, wildlife and compressed portraits.'),
('LNS-005','lenses','Lenscraft 18-50mm f/2.8 Lens for FX','Lenscraft',449.00,4.5,870,2,14,'{photography,streaming}','{"mount":"FX","focal_length":"18-50mm","max_aperture":"f/2.8","lens_type":"Zoom","weight_kg":0.29}','Compact fast zoom, a popular choice for vlogging and streaming.'),
('LNS-006','lenses','Lenscraft 85mm f/1.8 Lens for OM','Lenscraft',399.00,4.5,360,2,10,'{photography}','{"mount":"OM","focal_length":"85mm","max_aperture":"f/1.8","lens_type":"Prime","weight_kg":0.41}','Portrait prime with creamy background blur.'),
-- Tripods
('TRP-001','tripods','Steadyline Travel Tripod','Steadyline',69.00,4.2,2300,1,40,'{photography,portable,beginner}','{"max_load_kg":3,"max_height_cm":150,"weight_kg":1.1,"head":"Ball"}','Compact travel tripod that folds to 40 cm.'),
('TRP-002','tripods','Steadyline Pro Aluminum Tripod','Steadyline',179.00,4.6,980,2,20,'{photography,streaming}','{"max_load_kg":8,"max_height_cm":170,"weight_kg":2.0,"head":"Ball"}','Sturdy aluminum tripod with a quick-release ball head.'),
('TRP-003','tripods','Tripodia Carbon Fiber Tripod','Tripodia',399.00,4.8,410,3,10,'{photography,pro,portable}','{"max_load_kg":12,"max_height_cm":165,"weight_kg":1.4,"head":"Ball"}','Ultra-rigid carbon fiber tripod for demanding professional work.'),
('TRP-004','tripods','Tripodia Video Tripod with Fluid Head','Tripodia',289.00,4.6,260,2,8,'{photography,streaming}','{"max_load_kg":6,"max_height_cm":180,"weight_kg":3.2,"head":"Fluid"}','Video tripod with a smooth fluid head for pans and tilts.'),
-- Studio lighting
('SLT-001','studio-lighting','Glowlab Ring Light 18 inch','Glowlab',79.00,4.3,3100,1,40,'{photography,streaming,beginner}','{"light_type":"Ring","power_w":55,"color_temp_range":"3200-5600K","kit_pieces":1}','Dimmable 18-inch ring light with stand and phone holder.'),
('SLT-002','studio-lighting','Glowlab Softbox Kit 2-Light','Glowlab',139.00,4.4,1500,2,25,'{photography,podcast}','{"light_type":"Softbox","power_w":170,"color_temp_range":"5500K","kit_pieces":2}','Two-light softbox kit for soft, flattering portrait light.'),
('SLT-003','studio-lighting','Studiopro LED Panel Kit 3-Light','Studiopro',299.00,4.6,720,2,15,'{photography,streaming,podcast}','{"light_type":"LED panel","power_w":180,"color_temp_range":"2700-6500K","kit_pieces":3}','Bi-color LED panel kit for three-point lighting.'),
('SLT-004','studio-lighting','Studiopro COB Light 300W','Studiopro',549.00,4.8,310,3,2,'{photography,pro}','{"light_type":"COB","power_w":300,"color_temp_range":"2700-6500K","kit_pieces":1}','Powerful bi-color COB light with a Bowens mount for modifiers.'),
-- Backdrops
('BKD-001','backdrops','Canvasly Muslin Backdrop Kit','Canvasly',79.00,4.2,980,1,25,'{photography}','{"width_cm":200,"material":"Muslin","color":"Black, white, green"}','Three muslin backdrops with an adjustable support stand.'),
('BKD-002','backdrops','Canvasly Seamless Paper Roll','Canvasly',69.00,4.6,640,2,30,'{photography}','{"width_cm":272,"material":"Paper","color":"Gray"}','Seamless paper backdrop roll for clean studio portraits.'),
('BKD-003','backdrops','Canvasly Collapsible Green Screen','Canvasly',159.00,4.5,1100,2,18,'{streaming,photography}','{"width_cm":148,"material":"Polyester","color":"Green"}','Pull-up green screen that sets up in seconds and stores flat.'),
-- Memory cards
('MEM-001','memory-cards','Datavolt SD Card 128GB V30','Datavolt',24.00,4.6,5400,1,120,'{photography}','{"capacity_gb":128,"card_format":"SD","speed_class":"V30"}','Reliable SD card fast enough for 4K video.'),
('MEM-002','memory-cards','Datavolt SD Card 256GB V90','Datavolt',129.00,4.8,1300,3,40,'{photography,pro}','{"capacity_gb":256,"card_format":"SD","speed_class":"V90"}','Professional V90 SD card for high-bitrate video and long bursts.'),
('MEM-003','memory-cards','Datavolt CFexpress Type B 256GB','Datavolt',199.00,4.7,480,3,20,'{photography,pro}','{"capacity_gb":256,"card_format":"CFexpress B","speed_class":"VPG400"}','CFexpress Type B card for professional cameras and raw video.'),
('MEM-004','memory-cards','Datavolt SD Card 64GB V30','Datavolt',14.00,4.5,3900,1,150,'{photography,beginner}','{"capacity_gb":64,"card_format":"SD","speed_class":"V30"}','Affordable SD card for photos and 4K clips.'),
-- Dumbbells
('DBL-001','dumbbells','IronCore Neoprene Dumbbell Set','IronCore',49.00,4.4,2600,1,50,'{home-gym,beginner}','{"dumbbell_type":"Fixed","max_weight_kg":5,"pair":true}','Three pairs of neoprene dumbbells (1, 3 and 5 kg) with a storage stand.'),
('DBL-002','dumbbells','IronCore Adjustable Dumbbells 24kg','IronCore',299.00,4.7,3400,2,20,'{home-gym,compact}','{"dumbbell_type":"Adjustable","max_weight_kg":24,"pair":true}','Dial-adjustable dumbbells that replace 15 pairs, from 2 to 24 kg.'),
('DBL-003','dumbbells','IronCore Adjustable Dumbbells 40kg','IronCore',499.00,4.8,980,3,12,'{home-gym}','{"dumbbell_type":"Adjustable","max_weight_kg":40,"pair":true}','Heavy-duty adjustable dumbbells up to 40 kg with a metal handle.'),
('DBL-004','dumbbells','FlexFit Hex Dumbbell Pair 10kg','FlexFit',59.00,4.5,1700,1,40,'{home-gym}','{"dumbbell_type":"Fixed","max_weight_kg":10,"pair":true}','Rubber-coated hex dumbbells that do not roll away.'),
-- Kettlebells
('KTB-001','kettlebells','IronCore Cast Iron Kettlebell 8kg','IronCore',35.00,4.6,2100,1,40,'{home-gym,beginner}','{"weight_kg":8,"material":"Cast iron"}','Single-piece cast iron kettlebell with a wide, smooth handle.'),
('KTB-002','kettlebells','IronCore Cast Iron Kettlebell 16kg','IronCore',59.00,4.7,1800,2,35,'{home-gym}','{"weight_kg":16,"material":"Cast iron"}','16 kg cast iron kettlebell for swings, cleans and presses.'),
('KTB-003','kettlebells','FlexFit Adjustable Kettlebell 18kg','FlexFit',149.00,4.5,720,2,15,'{home-gym,compact}','{"weight_kg":18,"material":"Steel"}','Adjustable kettlebell from 5 to 18 kg in one compact unit.'),
('KTB-004','kettlebells','FlexFit Competition Kettlebell 24kg','FlexFit',109.00,4.8,410,3,12,'{home-gym}','{"weight_kg":24,"material":"Steel"}','Competition-standard steel kettlebell with consistent size across weights.'),
-- Benches
('BNC-001','benches','FlexFit Flat Bench','FlexFit',79.00,4.3,1500,1,30,'{home-gym,beginner}','{"adjustable":false,"max_load_kg":250,"foldable":false,"length_cm":110}','Stable flat bench for presses, rows and step-ups.'),
('BNC-002','benches','FlexFit Adjustable FID Bench','FlexFit',179.00,4.6,2200,2,20,'{home-gym}','{"adjustable":true,"max_load_kg":300,"foldable":true,"length_cm":130}','Flat, incline and decline bench with seven back positions.'),
('BNC-003','benches','IronCore Pro Utility Bench','IronCore',349.00,4.8,640,3,10,'{home-gym}','{"adjustable":true,"max_load_kg":450,"foldable":false,"length_cm":140}','Commercial-grade adjustable bench with a minimal pad gap.'),
('BNC-004','benches','FlexFit Folding Compact Bench','FlexFit',129.00,4.4,980,2,25,'{home-gym,compact}','{"adjustable":true,"max_load_kg":220,"foldable":true,"length_cm":115}','Space-saving folding bench that stores upright.'),
-- Mats & flooring
('MAT-001','exercise-mats','FlexFit Yoga Mat 6mm','FlexFit',29.00,4.5,4800,1,80,'{home-gym,beginner}','{"thickness_mm":6,"length_cm":183,"material":"TPE"}','Non-slip TPE yoga mat with alignment lines and a carry strap.'),
('MAT-002','exercise-mats','FlexFit Large Exercise Mat','FlexFit',79.00,4.6,1300,2,30,'{home-gym}','{"thickness_mm":8,"length_cm":213,"material":"PVC foam"}','Extra-large mat for full-body workouts and stretching.'),
('MAT-003','exercise-mats','IronCore Interlocking Gym Floor Tiles','IronCore',119.00,4.7,960,2,25,'{home-gym}','{"thickness_mm":20,"length_cm":240,"material":"EVA foam"}','Six interlocking foam tiles that protect floors under heavy equipment.'),
-- Resistance bands
('RBD-001','resistance-bands','FlexFit Loop Bands 5-Pack','FlexFit',19.00,4.4,6100,1,100,'{home-gym,beginner,portable}','{"pieces":5,"max_resistance_kg":20}','Five latex loop bands from extra light to extra heavy.'),
('RBD-002','resistance-bands','FlexFit Tube Band Kit','FlexFit',39.00,4.5,2900,2,60,'{home-gym,portable}','{"pieces":11,"max_resistance_kg":68}','Stackable tube bands with handles, ankle straps and a door anchor.'),
('RBD-003','resistance-bands','IronCore Power Bands Set','IronCore',69.00,4.7,820,3,30,'{home-gym}','{"pieces":4,"max_resistance_kg":80}','Heavy power bands for assisted pull-ups and strength work.'),
-- Cardio machines
('CRD-001','cardio-machines','Stride Walking Pad','Stride',299.00,4.3,2100,1,15,'{home-gym,home-office,compact}','{"machine_type":"Walking pad","foldable":true,"max_user_kg":110,"footprint_length_cm":140,"footprint_width_cm":55}','Under-desk walking pad up to 6 km/h that pairs well with a standing desk.'),
('CRD-002','cardio-machines','Pulsefit Magnetic Exercise Bike','Pulsefit',399.00,4.5,1300,2,12,'{home-gym,quiet}','{"machine_type":"Bike","foldable":false,"max_user_kg":136,"footprint_length_cm":100,"footprint_width_cm":55}','Quiet magnetic-resistance bike with 32 levels and a tablet holder.'),
('CRD-003','cardio-machines','Stride Folding Treadmill','Stride',799.00,4.6,640,2,8,'{home-gym}','{"machine_type":"Treadmill","foldable":true,"max_user_kg":136,"footprint_length_cm":180,"footprint_width_cm":75}','Folding treadmill up to 16 km/h with auto-incline.'),
('CRD-004','cardio-machines','Pulsefit Smart Rower','Pulsefit',1199.00,4.8,310,3,6,'{home-gym}','{"machine_type":"Rower","foldable":true,"max_user_kg":150,"footprint_length_cm":240,"footprint_width_cm":55}','Smart rowing machine with water-like resistance and guided workouts.'),
-- Smart speakers & hubs
('SSP-001','smart-speakers','Homely Mini Smart Speaker','Homely',49.00,4.4,8800,1,80,'{smart-home,beginner}','{"ecosystems":["Alexa"],"hub_protocols":["Matter"],"screen":false}','Compact smart speaker for voice control, music and routines.'),
('SSP-002','smart-speakers','Homely Speaker with Zigbee Hub','Homely',99.00,4.6,3100,2,40,'{smart-home}','{"ecosystems":["Alexa"],"hub_protocols":["Zigbee","Matter","Thread"],"screen":false}','Room-filling smart speaker with a built-in Zigbee, Thread and Matter hub.'),
('SSP-003','smart-speakers','Nestora Display Hub 8','Nestora',129.00,4.5,2400,2,30,'{smart-home}','{"ecosystems":["Google Home"],"hub_protocols":["Matter","Thread"],"screen":true}','8-inch smart display to control your home, view cameras and make video calls.'),
('SSP-004','smart-speakers','Nestora Home Speaker Max','Nestora',299.00,4.7,820,3,15,'{smart-home}','{"ecosystems":["Apple Home"],"hub_protocols":["Matter","Thread"],"screen":false}','Premium smart speaker with spatial audio that doubles as a home hub.'),
-- Smart lighting
('SLG-001','smart-lighting','Lumio Wi-Fi Smart Bulb 4-Pack','Lumio',39.00,4.2,5200,1,60,'{smart-home,beginner}','{"ecosystems":["Alexa","Google Home"],"protocols":["Wi-Fi"],"pack_size":4,"color":false,"requires_hub":false}','Dimmable white smart bulbs that connect straight to Wi-Fi.'),
('SLG-002','smart-lighting','Lumio Color Bulb Starter Kit','Lumio',179.00,4.7,2800,3,25,'{smart-home}','{"ecosystems":["Alexa","Google Home","Apple Home"],"protocols":["Zigbee"],"pack_size":3,"color":true,"requires_hub":true}','Three full-color Zigbee bulbs plus a bridge for rock-solid reliability.'),
('SLG-003','smart-lighting','Lumio Matter Color Bulb 2-Pack','Lumio',49.00,4.4,1100,2,50,'{smart-home}','{"ecosystems":["Alexa","Google Home","Apple Home"],"protocols":["Matter","Thread"],"pack_size":2,"color":true,"requires_hub":false}','Matter-over-Thread color bulbs that work with every major ecosystem.'),
('SLG-004','smart-lighting','Lumio LED Light Strip 5m','Lumio',69.00,4.5,2300,2,40,'{smart-home,gaming,streaming}','{"ecosystems":["Alexa","Google Home"],"protocols":["Wi-Fi"],"pack_size":1,"color":true,"requires_hub":false}','5 m color light strip with music sync for desks and TVs.'),
('SLG-005','smart-lighting','Brightnest Smart Floor Lamp','Brightnest',129.00,4.5,640,2,20,'{smart-home,home-office}','{"ecosystems":["Alexa","Google Home","Apple Home"],"protocols":["Matter","Wi-Fi"],"pack_size":1,"color":true,"requires_hub":false}','Minimal smart floor lamp with tunable white and color modes.'),
-- Smart plugs
('SPL-001','smart-plugs','Homely Smart Plug 2-Pack','Homely',24.00,4.4,7200,1,90,'{smart-home,beginner}','{"ecosystems":["Alexa","Google Home"],"protocols":["Wi-Fi"],"energy_monitoring":false,"pack_size":2}','Mini Wi-Fi smart plugs for schedules and voice control.'),
('SPL-002','smart-plugs','Homely Energy Smart Plug 4-Pack','Homely',59.00,4.6,2100,2,40,'{smart-home}','{"ecosystems":["Alexa","Google Home","Apple Home"],"protocols":["Matter","Wi-Fi"],"energy_monitoring":true,"pack_size":4}','Matter smart plugs with real-time energy monitoring.'),
('SPL-003','smart-plugs','Lumio Zigbee Smart Plug 2-Pack','Lumio',39.00,4.5,860,2,35,'{smart-home}','{"ecosystems":["Alexa","Google Home"],"protocols":["Zigbee"],"energy_monitoring":true,"pack_size":2}','Zigbee smart plugs that also extend your Zigbee mesh.'),
-- Video doorbells
('VDB-001','video-doorbells','Doorly Battery Video Doorbell','Doorly',99.00,4.2,4300,1,30,'{smart-home}','{"ecosystems":["Alexa"],"resolution":"1080p","power":"Battery","subscription_required":true}','Wire-free 1080p video doorbell with motion alerts.'),
('VDB-002','video-doorbells','Doorly Wired Video Doorbell Pro','Doorly',179.00,4.6,2100,2,20,'{smart-home}','{"ecosystems":["Alexa","Google Home"],"resolution":"2K","power":"Wired","subscription_required":false}','2K wired doorbell with head-to-toe view and local storage.'),
('VDB-003','video-doorbells','Nestora Video Doorbell Plus','Nestora',229.00,4.5,1200,3,12,'{smart-home}','{"ecosystems":["Google Home","Apple Home"],"resolution":"2K","power":"Wired","subscription_required":false}','Smart doorbell with package detection and familiar face alerts.');

-- Fail fast on typos in the seed data.
DO $$
DECLARE
    problems TEXT;
BEGIN
    SELECT string_agg(sku || ' -> ' || cat, ', ') INTO problems
    FROM seed_products s
    WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.slug = s.cat AND c.parent_id IS NOT NULL);
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'Seed products reference unknown leaf categories: %', problems;
    END IF;

    SELECT string_agg(s.sku || '.' || a.key, ', ') INTO problems
    FROM seed_products s
    JOIN categories c ON c.slug = s.cat
    CROSS JOIN LATERAL jsonb_object_keys(s.attrs) AS a (key)
    WHERE NOT EXISTS (SELECT 1 FROM attribute_definitions ad WHERE ad.category_id = c.id AND ad.key = a.key);
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION 'Seed attributes without a definition: %', problems;
    END IF;
END $$;

INSERT INTO products (category_id, sku, slug, name, brand, description, price, rating_avg, rating_count, quality_tier)
SELECT c.id, s.sku, trim(BOTH '-' FROM lower(regexp_replace(s.name, '[^A-Za-z0-9]+', '-', 'g'))),
       s.name, s.brand, s.descr, s.price, s.rating, s.reviews, s.tier
FROM seed_products s
JOIN categories c ON c.slug = s.cat;

INSERT INTO inventory (product_id, quantity_on_hand)
SELECT p.id, s.stock
FROM seed_products s
JOIN products p ON p.sku = s.sku;

INSERT INTO product_tags (product_id, tag)
SELECT p.id, t.tag
FROM seed_products s
JOIN products p ON p.sku = s.sku
CROSS JOIN LATERAL unnest(s.tags) AS t (tag);

-- JSON scalars become one row; JSON arrays (LIST attributes) become one row per element.
INSERT INTO product_attributes (product_id, attribute_id, value_text, value_number, value_bool)
SELECT p.id,
       ad.id,
       CASE WHEN ad.data_type IN ('TEXT', 'LIST') THEN e.val #>> '{}' END,
       CASE WHEN ad.data_type = 'NUMBER' THEN (e.val #>> '{}')::NUMERIC END,
       CASE WHEN ad.data_type = 'BOOL' THEN (e.val #>> '{}')::BOOLEAN END
FROM seed_products s
JOIN products p ON p.sku = s.sku
CROSS JOIN LATERAL jsonb_each(s.attrs) AS a (key, value)
CROSS JOIN LATERAL jsonb_array_elements(
    CASE WHEN jsonb_typeof(a.value) = 'array' THEN a.value ELSE jsonb_build_array(a.value) END) AS e (val)
JOIN attribute_definitions ad ON ad.category_id = p.category_id AND ad.key = a.key;
