-- org 1 owned by user 100 (Admin), used by permission tests
INSERT INTO organization.organizations (id, name, description, address, city, region, country, phone, email, logo_url, status, plan, max_members, created_at, updated_at)
VALUES (1, 'Seed Org', null, null, null, null, null, null, null, null, 'Active', 'Free', 1, NOW(), NOW());

INSERT INTO organization.organization_members (id, organization_id, user_id, role, status, joined_at)
VALUES (1, 1, 100, 'Admin', 'Active', NOW());
