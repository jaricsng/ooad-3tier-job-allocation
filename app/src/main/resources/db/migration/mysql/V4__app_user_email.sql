-- V4: email on app_user, so a single sign-on identity (Google, Microsoft, Apple) can be linked to an
-- account by its VERIFIED email. Expand-only: an older app version ignores the new column (safe rollback).
alter table app_user add email varchar(254);
create unique index ux_app_user_email on app_user (email);
-- Demo accounts get reserved example.com addresses (RFC 2606); real users get theirs from an admin.
update app_user set email = 'supervisor@example.com' where username = 'supervisor';
update app_user set email = 'viewer@example.com' where username = 'viewer';
