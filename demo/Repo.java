import java.sql.Connection;
import java.sql.DriverManager;

class Repo {
    void run() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:x");
        process(conn);
    }

    void process(Connection c) throws Exception {
        c.createStatement();
    }
}
