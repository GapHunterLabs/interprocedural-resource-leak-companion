package dev.gaphunter.interproceduralresourceleakcompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InterproceduralResourceLeakInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(InterproceduralResourceLeakInspection::class.java)
    }

    fun `test a connection delegated to a helper that never closes it is flagged`() {
        myFixture.configureByText(
            "Repo1.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class Repo1 {
                void run() throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    process(conn);
                }

                void process(Connection c) throws Exception {
                    c.createStatement();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("CWE-772") == true })
    }

    fun `test a connection delegated to a helper that closes it on every path is not flagged`() {
        myFixture.configureByText(
            "Repo2.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class Repo2 {
                void run() throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    processAndClose(conn);
                }

                void processAndClose(Connection c) throws Exception {
                    c.createStatement();
                    c.close();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-772") == true })
    }

    fun `test a connection delegated to a non-closing helper but closed by the caller afterward is not flagged`() {
        myFixture.configureByText(
            "Repo3.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class Repo3 {
                void run() throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    process(conn);
                    conn.close();
                }

                void process(Connection c) throws Exception {
                    c.createStatement();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-772") == true })
    }

    fun `test a connection never delegated to any helper is out of this plugin's scope`() {
        myFixture.configureByText(
            "Repo4.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class Repo4 {
                void run() throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    conn.createStatement();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-772") == true })
    }

    fun `test a two-hop chain where the deepest helper closes it propagates through a middle helper via the fixed point`() {
        myFixture.configureByText(
            "Repo5.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class Repo5 {
                void run() throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    middle(conn);
                }

                void middle(Connection c) throws Exception {
                    deepest(c);
                }

                void deepest(Connection c) throws Exception {
                    c.createStatement();
                    c.close();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-772") == true })
    }
}
