package com.wakaleo.schemaspy;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

public class PGSQLTest {
    /**
     * Test resources.
     */
    @Rule
    public TestResources resources = new TestResources();

    /**
     * test rule.
     */
    @Rule
    public MojoRule rule = new MojoRule();

    @Rule
    public TestName name = new TestName();

    @Test
    public void testPGSQLConfiguration() throws Exception {
        Logger.getLogger("global").info("Starting: " + name.getMethodName());
        File projectCopy = this.resources.getBasedir("unit");
        File testPom = new File(projectCopy,"pgsql-testcontainers-plugin-config.xml");
        assumeNotNull("POM file should not be null.", testPom);
        assumeTrue("POM file should exist as file.",
                testPom.exists() && testPom.isFile());

        SchemaSpyReport mojo = new SchemaSpyReport();
        mojo = (SchemaSpyReport) this.rule.configureMojo(mojo, rule.extractPluginConfiguration("schemaspy-maven-plugin", testPom));
        mojo.executeReport(Locale.getDefault());

        // check if the reports generated
        File generatedFile = new File("./target/reports/pgsql-test/schemaspy/index.html");
        Logger.getLogger("global").info("generatedFile = " + generatedFile.getAbsolutePath());
        assertTrue(generatedFile.exists());
    }
}
