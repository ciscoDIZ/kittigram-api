INSERT INTO organization.organizations (id, name, description, address, city, region, country, phone, email, logo_url, status, plan, max_members, created_at, updated_at)
VALUES (1, 'Protectora Demo', 'Protectora de animales de prueba', 'Calle Mayor 1', 'Sevilla', 'Andalucía', 'España', '600000001', 'demo@protectora.org', null, 'Active', 'Free', 1, NOW(), NOW());

INSERT INTO organization.organization_members (id, organization_id, user_id, role, status, joined_at)
VALUES (1, 1, 1, 'Admin', 'Active', NOW());
