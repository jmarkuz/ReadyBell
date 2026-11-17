-- Staff usernames are unique within a restaurant, not across the platform: two restaurants can both have an "admin".
-- Sign-in now names the restaurant (its identifier) together with the username. Usernames are stored lower-case.
ALTER TABLE staff_account DROP CONSTRAINT uq_staff_account_username;
ALTER TABLE staff_account ADD CONSTRAINT uq_staff_account_tenant_username UNIQUE (tenant_id, username);
