# Third-Party Notices

The application bundles these components in `lib/` (pinned by SHA-256 in `lib.sha256`) and `src/main/resources/static/`. Each keeps its own licence.

| Component | Version | Used for | Licence | Source |
|---|---|---|---|---|
| PostgreSQL JDBC Driver (pgjdbc) | 42.7.14 | Runtime: database driver | BSD-2-Clause | https://github.com/pgjdbc/pgjdbc |
| MySQL Connector/J | 26.10.0 | Runtime: database driver when `DB_URL` is `jdbc:mysql://` (verified against Maven Central's SHA-1; pinned by SHA-256 in `lib.sha256`) | GPL-2.0-only WITH Universal-FOSS-exception-1.0 | https://github.com/mysql/mysql-connector-j |
| Jackson Core / Databind / Annotations | 2.22.4 (annotations 2.22) | Runtime: JSON for the REST API | Apache-2.0 | https://github.com/FasterXML/jackson |
| Jackson Datatype JSR-310 | 2.22.4 | Runtime: java.time in JSON | Apache-2.0 | https://github.com/FasterXML/jackson-modules-java8 |
| JUnit | 4.13.2 | Tests only (not in the packaged jar's class path) | EPL-1.0 | https://github.com/junit-team/junit4 |
| Hamcrest Core | 1.3 | Tests only | BSD-3-Clause | https://github.com/hamcrest/JavaHamcrest |
| Bootstrap (CSS only) | 5.3.8 | Runtime: UI styling, served from `/static/bootstrap.min.css` (pinned by SHA-256 in `StaticAssetsTest`) | MIT | https://github.com/twbs/bootstrap |
| Playwright Test (npm, `e2e/`) | 1.49.1 | Browser tests only, not distributed | Apache-2.0 | https://github.com/microsoft/playwright |

Bootstrap is vendored in `src/main/resources/static/`; its MIT copyright header is kept at the top of the file. No Bootstrap JavaScript is shipped, because the CSP forbids scripts.
Apache-2.0, MIT and BSD licences require keeping copyright notices when redistributing; the jars contain their original notices.
EPL-1.0 (JUnit) is used only at test time and is not shipped with the application.

**MySQL Connector/J** is GPLv2 with Oracle's Universal FOSS Exception 1.0 (full text in the jar's `LICENSE`). The exception lets it be combined with software under an OSI-approved licence such as this project's MIT licence. Distributing it inside a closed-source product would need legal review or a commercial licence; alternatively use MariaDB Connector/J (LGPL-2.1). It is only loaded when `DB_URL` starts with `jdbc:mysql://`. Not legal advice.
