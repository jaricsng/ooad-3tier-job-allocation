-- Run by `make db-up-mysql` (piped into the container as root) when joballoc_test does not exist yet.
-- The app database (joballoc) and user are created by the image from the MYSQL_* variables;
-- the integration tests need a second, disposable database the same user can rebuild.
create database if not exists joballoc_test;
grant all privileges on joballoc_test.* to 'joballoc'@'%';
