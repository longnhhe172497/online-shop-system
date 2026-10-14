-- Checkout charges a flat shipping fee per order (VND). Admin can change it in system settings.
INSERT INTO system_settings(setting_key,value_text,description) VALUES
    ('shipping_fee','30000','Flat shipping fee per order in VND');
