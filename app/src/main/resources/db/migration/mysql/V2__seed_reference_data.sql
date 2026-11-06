-- V2: reference data (skills, workers and their skills) and the two demo users.
-- Demo passwords: supervisor / Supervisor#2026 and viewer / Viewer#2026. Change them outside a classroom.

insert into skill (code, name) values
    ('LINUX',    'Linux administration'),
    ('DBA',      'Database administration'),
    ('SECURITY', 'Security testing'),
    ('GO',       'Go development'),
    ('JAVA',     'Java development'),
    ('NETWORK',  'Networking');

insert into worker (worker_id, name, daily_capacity_hours) values
    ('W-01', 'Alice Tan',    8),
    ('W-02', 'Ben Lim',      8),
    ('W-03', 'Chitra Nair',  6),
    ('W-04', 'Daniel Wong',  8),
    ('W-05', 'Erin Koh',     4);

insert into worker_skill (worker_id, skill_code, ordinal) values
    ('W-01', 'LINUX', 0), ('W-01', 'GO', 1),
    ('W-02', 'LINUX', 0),
    ('W-03', 'DBA', 0), ('W-03', 'LINUX', 1),
    ('W-04', 'SECURITY', 0), ('W-04', 'NETWORK', 1),
    ('W-05', 'JAVA', 0), ('W-05', 'GO', 1), ('W-05', 'SECURITY', 2);


insert into app_user (username, password_hash, role, display_name) values
    ('supervisor', 'pbkdf2-sha256$210000$EdDNmcc88PVS+AB12Fp/Jw==$uMB4wtot5P+d8DT9psabn79uXq3bP+tiikQ4cjlq7Bo=', 'SUPERVISOR', 'Sam Supervisor'),
    ('viewer',     'pbkdf2-sha256$210000$ZZN//SNMsmar3N8sbWf/1w==$Z9EkP+vU0bgIaLU9PntNSZrGTgyV0KmgGIFeGBlMPts=', 'VIEWER',     'Vera Viewer');
