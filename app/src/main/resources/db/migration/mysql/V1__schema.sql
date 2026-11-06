-- V1: core schema for the Workload Job Allocation app (MySQL 8.0.16+; tested with 8.0 and 8.4 LTS).
-- Same tables and rules as the PostgreSQL schema after its V3, written in MySQL syntax.
-- Differences are marked "MySQL:".

create table skill (
    code  varchar(32)  primary key,
    name  varchar(100) not null
);

create table worker (
    worker_id            varchar(16)  primary key,
    name                 varchar(100) not null,
    daily_capacity_hours int          not null check (daily_capacity_hours between 1 and 24),
    version              bigint       not null default 0
);

create table worker_skill (
    worker_id  varchar(16) not null,
    skill_code varchar(32) not null,
    ordinal    int         not null,
    primary key (worker_id, skill_code),
    constraint fk_worker_skill_worker foreign key (worker_id) references worker (worker_id) on delete cascade,
    constraint fk_worker_skill_skill foreign key (skill_code) references skill (code)
);

create table job (
    job_id          varchar(16)  primary key,
    title           varchar(200) not null,
    effort_hours    int          not null check (effort_hours between 1 and 24),
    work_date       date         not null,
    due_date        date         not null,
    priority        varchar(16)  not null check (priority in ('LOW','MEDIUM','HIGH','CRITICAL')),
    status          varchar(16)  not null check (status in ('PENDING','ALLOCATED','IN_PROGRESS','DONE','CANCELLED')),
    version         bigint       not null default 0
);
-- MySQL: no partial indexes, so index (work_date, status) instead of "where status = 'PENDING'".
create index ix_job_pending on job (work_date, status);

create table job_skill (
    job_id     varchar(16) not null,
    skill_code varchar(32) not null,
    ordinal    int         not null,
    primary key (job_id, skill_code),
    constraint fk_job_skill_job foreign key (job_id) references job (job_id) on delete cascade,
    constraint fk_job_skill_skill foreign key (skill_code) references skill (code)
);
create index ix_job_skill_skill on job_skill (skill_code);

create table allocation (
    allocation_id varchar(40) primary key,
    job_id        varchar(16) not null,
    worker_id     varchar(16) not null,
    work_date     date        not null,
    hours         int         not null check (hours between 1 and 24),
    allocated_by  varchar(64) not null,
    -- MySQL: datetime(6) holds the UTC instant (the dialect sets the session time zone to UTC).
    allocated_at  datetime(6) not null,
    status        varchar(16) not null check (status in ('ACTIVE','RELEASED','COMPLETED')),
    version       bigint      not null default 0,
    -- MySQL: no partial unique index. This generated column is the job id while ACTIVE and NULL
    -- otherwise; a unique index allows many NULLs, so "one ACTIVE allocation per job" still holds.
    active_job_id varchar(16) generated always as (case when status = 'ACTIVE' then job_id end) stored,
    constraint fk_allocation_job foreign key (job_id) references job (job_id),
    constraint fk_allocation_worker foreign key (worker_id) references worker (worker_id)
);
create unique index ux_allocation_one_active_per_job on allocation (active_job_id);
create index ix_allocation_worker_date on allocation (worker_id, work_date, status);

create table app_user (
    username      varchar(64)  primary key,
    password_hash varchar(200) not null,
    role          varchar(16)  not null check (role in ('VIEWER','SUPERVISOR')),
    display_name  varchar(100) not null
);

create table audit_event (
    event_id    varchar(40)  primary key,
    event_type  varchar(40)  not null,
    actor       varchar(64)  not null,
    entity_id   varchar(40)  not null,
    detail      varchar(500),
    occurred_at datetime(6)  not null
);
create index ix_audit_event_time on audit_event (occurred_at desc);

-- Append-only audit trail: any UPDATE or DELETE is rejected by the database itself.
-- MySQL: one trigger per event, and SIGNAL instead of RAISE EXCEPTION.
create trigger trg_audit_event_no_update before update on audit_event for each row
    signal sqlstate '45000' set message_text = 'audit_event is append-only (UPDATE rejected)';
create trigger trg_audit_event_no_delete before delete on audit_event for each row
    signal sqlstate '45000' set message_text = 'audit_event is append-only (DELETE rejected)';
