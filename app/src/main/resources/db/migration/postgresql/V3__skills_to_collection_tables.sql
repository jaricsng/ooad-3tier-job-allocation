-- V3: move skills out of PostgreSQL text[] arrays into collection tables (portable to every database).
-- V1 and V2 are never edited once applied; this script evolves the schema AND migrates the data.

create table worker_skill (
    worker_id  varchar(16) not null references worker (worker_id) on delete cascade,
    skill_code varchar(32) not null references skill (code),
    ordinal    int         not null,
    primary key (worker_id, skill_code)
);

create table job_skill (
    job_id     varchar(16) not null references job (job_id) on delete cascade,
    skill_code varchar(32) not null references skill (code),
    ordinal    int         not null,
    primary key (job_id, skill_code)
);
create index ix_job_skill_skill on job_skill (skill_code);

-- Copy the data, keeping each array's order in the ordinal column (0-based).
insert into worker_skill (worker_id, skill_code, ordinal)
select w.worker_id, s.code, s.position - 1
from worker w cross join lateral unnest(w.skills) with ordinality as s(code, position);

insert into job_skill (job_id, skill_code, ordinal)
select j.job_id, s.code, s.position - 1
from job j cross join lateral unnest(j.required_skills) with ordinality as s(code, position);

alter table worker drop column skills;
alter table job drop column required_skills;
