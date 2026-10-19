-- V15: role-based access control. Every existing and newly registered user is
-- a plain USER; ADMIN is only ever granted by creating the account at startup
-- from ADMIN_EMAIL/ADMIN_PASSWORD (see AdminBootstrap) or by an explicit
-- UPDATE -- never through the public registration endpoint.

ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
