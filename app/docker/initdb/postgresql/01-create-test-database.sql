-- Run by `make db-up` (piped into the container with psql) when joballoc_test does not exist yet.
-- The app database (joballoc) and role are created by the image from the POSTGRES_* variables;
-- the integration tests need a second, disposable database.
create database joballoc_test owner joballoc;
