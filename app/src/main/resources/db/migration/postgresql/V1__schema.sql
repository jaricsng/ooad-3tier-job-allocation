-- V1: core schema for the Workload Job Allocation app (PostgreSQL 14+)

create table skill (
    code  varchar(32)  primary key,
    name  varchar(100) not null
);

create table worker (
    worker_id            varchar(16)  primary key,
    name                 varchar(100) not null,
    daily_capacity_hours int          not null check (daily_capacity_hours between 1 and 24),
    skills               text[]       not null default '{}',
    version              bigint       not null default 0
);

create table job (
    job_id          varchar(16)  primary key,
    title           varchar(200) not null,
    required_skills text[]       not null check (cardinality(required_skills) > 0),
    effort_hours    int          not null check (effort_hours between 1 and 24),
    work_date       date         not null,
    due_date        date         not null,
    priority        varchar(16)  not null check (priority in ('LOW','MEDIUM','HIGH','CRITICAL')),
    status          varchar(16)  not null check (status in ('PENDING','ALLOCATED','IN_PROGRESS','DONE','CANCELLED')),
    version         bigint       not null default 0
);
create index ix_job_pending on job (work_date) where status = 'PENDING';

create table allocation (
    allocation_id varchar(40) primary key,
    job_id        varchar(16) not null references job (job_id),
    worker_id     varchar(16) not null references worker (worker_id),
    work_date     date        not null,
    hours         int         not null check (hours between 1 and 24),
    allocated_by  varchar(64) not null,
    allocated_at  timestamptz not null,
    status        varchar(16) not null check (status in ('ACTIVE','RELEASED','COMPLETED')),
    version       bigint      not null default 0
);
-- A job can have at most one ACTIVE allocation: the database backs up the domain rule.
create unique index ux_allocation_one_active_per_job on allocation (job_id) where status = 'ACTIVE';
create index ix_allocation_worker_date on allocation (worker_id, work_date) where status = 'ACTIVE';

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
    occurred_at timestamptz  not null
);
create index ix_audit_event_time on audit_event (occurred_at desc);

-- Append-only audit trail: any UPDATE or DELETE is rejected by the database itself.
create function audit_event_is_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'audit_event is append-only (% rejected)', tg_op;
end;
$$;
create trigger trg_audit_event_append_only
    before update or delete on audit_event
    for each row execute function audit_event_is_append_only();
