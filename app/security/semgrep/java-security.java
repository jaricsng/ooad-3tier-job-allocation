// Rule tests for java-security.yml (run: make sast-test). "ruleid" lines must match, "ok" lines must not.
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Random;

class RuleTests {
    void sql(Connection con, Statement st, String name) throws Exception {
        // ruleid: jdbc-sql-built-by-concatenation
        st.executeQuery("select * from job where title = '" + name + "'");
        // ruleid: jdbc-sql-built-by-concatenation
        con.prepareStatement("delete from job where job_id = " + name);
        // ok: jdbc-sql-built-by-concatenation
        con.prepareStatement("select * from job where title = ?");
        // ok: jdbc-sql-built-by-concatenation
        st.execute("create table t ("
                + "id int primary key)");
    }

    void hashes() throws Exception {
        // ruleid: weak-hash-algorithm
        MessageDigest.getInstance("MD5");
        // ruleid: weak-hash-algorithm
        MessageDigest.getInstance("SHA-1");
        // ok: weak-hash-algorithm
        MessageDigest.getInstance("SHA-256");
    }

    void random() {
        // ruleid: insecure-random-for-security
        int n = new Random().nextInt();
        // ok: insecure-random-for-security
        byte[] salt = new java.security.SecureRandom().generateSeed(16);
    }

    void commands() throws Exception {
        // ruleid: os-command-execution
        Runtime.getRuntime().exec("ls");
    }

    void deserialize(java.io.InputStream in) throws Exception {
        // ruleid: unsafe-java-deserialization
        Object o = new java.io.ObjectInputStream(in).readObject();
    }

    // ruleid: jdbc-url-disables-tls
    String url = "jdbc:mysql://db:3306/app?useSSL=false";
    // ok: jdbc-url-disables-tls
    String safe = "jdbc:postgresql://db:5432/app?sslmode=verify-full";

    // ruleid: hard-coded-secret
    String dbPassword = "s3cr3t-value";
    // ok: hard-coded-secret
    String passwordLabel = "";

    void trace(Exception e) {
        // ruleid: stack-trace-to-console
        e.printStackTrace();
    }
}
