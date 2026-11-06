package edu.ooad.joballoc.persistence;

import edu.ooad.joballoc.persistence.dialect.DialectFactory;
import edu.ooad.joballoc.persistence.dialect.MySqlDialect;
import edu.ooad.joballoc.persistence.dialect.PostgresDialect;
import edu.ooad.joballoc.persistence.dialect.SqlDialect;
import edu.ooad.joballoc.persistence.orm.Migrator;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** STRATEGY + FACTORY for database products, and the portable migration runner (no database needed). */
public class DialectTest {

    @Test
    public void factoryPicksTheDialectFromTheUrl() {
        assertTrue(DialectFactory.forUrl("jdbc:postgresql://h:5432/db") instanceof PostgresDialect);
        assertTrue(DialectFactory.forUrl("jdbc:mysql://h:3306/db") instanceof MySqlDialect);
    }

    @Test
    public void unsupportedUrlFailsFastAndListsWhatIsSupported() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DialectFactory.forUrl("jdbc:oracle:thin:@h:1521/db"));
        assertTrue(e.getMessage().contains("jdbc:mysql://"));
        assertTrue(e.getMessage().contains("jdbc:postgresql://"));
        assertFalse(DialectFactory.isSupported(null));
    }

    @Test
    public void eachProductReportsDuplicateKeysItsOwnWay() {
        SqlDialect postgres = new PostgresDialect();
        SqlDialect mysql = new MySqlDialect();
        SQLException pgDuplicate = new SQLException("dup", "23505");
        SQLException mysqlDuplicate = new SQLException("Duplicate entry", "23000", 1062);
        SQLException mysqlForeignKey = new SQLException("FK fails", "23000", 1452);
        assertTrue(postgres.isUniqueViolation(pgDuplicate));
        assertFalse(postgres.isUniqueViolation(mysqlDuplicate));
        assertTrue(mysql.isUniqueViolation(mysqlDuplicate));
        assertFalse("same SQLSTATE 23000, but not a duplicate", mysql.isUniqueViolation(mysqlForeignKey));
    }

    @Test
    public void mySqlStoresInstantsInUtcAndKeepsMultiStatementsOff() {
        var p = new MySqlDialect().connectionProperties();
        assertEquals("UTC", p.getProperty("connectionTimeZone"));
        assertEquals("false", p.getProperty("allowMultiQueries"));
    }

    @Test
    public void everyProductHasEveryMigrationScript() throws IOException {
        for (SqlDialect d : List.of(new PostgresDialect(), new MySqlDialect())) {
            for (String script : Migrator.SCRIPTS) {
                String path = "/db/migration/" + d.migrationFolder() + "/" + script;
                try (InputStream in = DialectTest.class.getResourceAsStream(path)) {
                    assertNotNull("missing " + path, in);
                }
            }
        }
    }

    @Test
    public void scriptsAreSplitIntoSingleStatements() {
        String script = """
                -- comment line
                create table a (x int);

                insert into a values (1),
                    (2);
                create function f() returns trigger language plpgsql as $$
                begin
                    raise exception 'no; really';
                end;
                $$;
                insert into a values (3)
                """;
        List<String> statements = Migrator.statements(script);
        assertEquals(4, statements.size());
        assertEquals("create table a (x int)", statements.get(0));
        assertTrue(statements.get(2).contains("raise exception 'no; really';"));
        assertTrue(statements.get(2).endsWith("$$"));
        assertEquals("insert into a values (3)", statements.get(3));
    }

    @Test
    public void passwordHashesWithDollarSignsDoNotLookLikeDollarQuotes() {
        List<String> statements = Migrator.statements(
                "insert into u values ('pbkdf2$1$a$b');\ninsert into u values ('x$$y$$z');\nselect 1;");
        assertEquals(3, statements.size());
    }
}
