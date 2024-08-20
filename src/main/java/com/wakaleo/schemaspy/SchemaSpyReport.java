package com.wakaleo.schemaspy;

import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.schemaspy.LayoutFolder;
import org.schemaspy.SchemaAnalyzer;
import org.schemaspy.cli.CommandLineArgumentParser;
import org.schemaspy.cli.CommandLineArguments;
import org.schemaspy.input.dbms.ConnectionConfig;
import org.schemaspy.input.dbms.service.DatabaseServiceFactory;
import org.schemaspy.input.dbms.service.SqlService;
import org.schemaspy.output.OutputProducer;
import org.schemaspy.output.xml.dom.XmlProducerUsingDOM;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The SchemaSpy Maven plugin report.
 *
 * @author John Smart
 *           The SchemaSpy Maven plugin This plugin is designed to generate
 *           SchemaSpy reports for a Maven web site.
 *
 *           SchemaSpy (http://schemaspy.sourceforge.net) does not need to be
 *           installed and accessible on your machine. However, SchemaSpy also
 *           needs the Graphviz tool (http://www.graphviz.org/) in order to
 *           generate graphical representations of the table/view relationships,
 *           so this needs to be installed on your machine.
 *
 *           The schemaspy goal invokes the SchemaSpy command-line tool.
 *           SchemaSpy generates a graphical and HTML report describing a given
 *           relational database.
 *
 */
@Mojo(name = "schemaspy", defaultPhase = LifecyclePhase.SITE)
public class SchemaSpyReport extends AbstractMavenReport {

    /**
     * The output directory for the intermediate report.
     *
     */
    @Parameter(property = "targetDirectory", defaultValue = "${project.build.directory}")
    private File targetDirectory;

    /**
     * The output directory where the final HTML reports will be generated. Note
     * that the reports are always generated in a directory called "schemaspy".
     * The output directory refers to the directory in which the "schemaspy"
     * will be generated.
     *
     */
    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/site")
    private String outputDirectory;

    /**
     * Site rendering component for generating the HTML report.
     */
    @Component
    private Renderer siteRenderer;

    /**
     * The Maven project object.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;


    /**
     * The name of the database being analysed.
     */
    @Parameter (property = "database", required = true)
    private String database;

    /**
     * The host address of the database being analysed.
     */
    @Parameter(property = "host")
    private String host;

    /**
     * The port, required by some drivers.
     */
    @Parameter(property = "port")
    private String port;

//    /**
//     * The JDBC URL to be used to connect to the database. Rather than defining
//     * the database and host names and letting SchemaSpy build the URL, you can
//     * alternatively specify the complete JDBC URL using this parameter. If this
//     * parameter is defined, it will override the host address (which, as a
//     * result, is not needed). Note that you still need to specify the database
//     * type, since SchemaSpy uses its own database properties file for extra
//     * information about each database.
//     *
//     * @todo Would it be possible to guess the database type from the form of
//     *       the URL?
//     */
//    @Parameter
//    private String jdbcUrl;

    @Parameter (property = "postgresDdlScript")
    private String postgresDdlScript;

    @Parameter (property = "postgresImage")
    private String postgresImage;
    /**
     * The type of database being analysed - defaults to ora.
     */
    @Parameter (property = "databaseType")
    private String databaseType;

    /**
     * Connect to the database with this user id.
     */
    @Parameter(property = "user")
    private String user;

    /**
     * Database schema to use - defaults to the specified user.
     */
    @Parameter(property = "schema")
    private String schema;

    /**
     * Database password to use - defaults to none.
     */
    @Parameter(property = "password")
    private String password;

    /**
     * If specified, SchemaSpy will look for JDBC drivers on this path, rather
     * than using the application classpath. Useful if your database has a
     * non-O/S driver not bundled with the plugin.
     */
    @Parameter(property = "pathToDrivers")
    private String pathToDrivers;

    /**
     * Schema description. Displays the specified textual description on summary
     * pages. If your description includes an equals sign then escape it with a
     * backslash. NOTE: This field doesn't seem to be used by SchemaSpy in the
     * current version.
     */
    @Parameter(property = "schemaDescription")
    private String schemaDescription;

    /**
     * Only include matching tables/views. This is a regular expression that's
     * used to determine which tables/views to include. For example: -i
     * "(.*book.*)|(library.*)" includes only those tables/views with 'book' in
     * their names or that start with 'library'. You might want to use
     * "description" with this option to describe the subset of tables.
     */
    @Parameter(property = "includeTableNamesRegex")
    private String includeTableNamesRegex;

    /**
     * Exclude matching columns from relationship analysis to simplify the
     * generated graphs. This is a regular expression that's used to determine
     * which columns to exclude. It must match table name, followed by a dot,
     * followed by column name. For example: -x "(book.isbn)|(borrower.address)"
     * Note that each column name regular expression must be surround by ()'s
     * and separated from other column names by a |.
     */
    @Parameter(property = "excludeColumnNamesRegex")
    private String excludeColumnNamesRegex;

    /**
     * Allow HTML In Comments. Any HTML embedded in comments normally gets
     * encoded so that it's rendered as text. This option allows it to be
     * rendered as HTML.
     */
    @Parameter(property = "allowHtmlInComments")
    private Boolean allowHtmlInComments;

    /**
     * Comments Initially Displayed. Column comments are normally hidden by
     * default. This option displays them by default.
     *
     * @deprecated this seems to no longer be a supported option in SchemaSpy
     */
    @Deprecated
    @Parameter(property = "commentsInitiallyDisplayed")
    private Boolean commentsInitiallyDisplayed;

    /**
     * Don't include implied foreign key relationships in the generated table
     * details.
     */
    @Parameter(property = "noImplied")
    private Boolean noImplied;

    /**
     * Only generate files needed for insertion/deletion of data (e.g. for
     * scripts).
     */
    @Parameter(property = "noHtml")
    private Boolean noHtml;

    /**
     * Detail of execution logging.
     */
    @Parameter(property = "logLevel")
    private String logLevel;

    /**
     * Some databases, like Derby, will crash if you use the old driver object
     * to establish a connection (eg "connection = driver.connect(...)"). In
     * this case, set useDriverManager to true to use the
     * DriverManager.getConnection() method instead (eg "connection =
     * java.sql.DriverManager.getConnection(...)"). Other databases (eg MySQL)
     * seem to only work with the first method, so don't use this parameter
     * unless you have to.
     */
    @Parameter(property = "useDriverManager")
    private Boolean useDriverManager;

    /**
     * The CSS Stylesheet. Allows you to override the default SchemaSpyCSS
     * stylesheet.
     */
    @Parameter(property = "cssStylesheet")
    private String cssStylesheet;

    /**
     * Single Sign-On. Don't require a user to be specified with -user to
     * simplify configuration when running in a single sign-on environment.
     */
    @Parameter(property = "singleSignOn")
    private Boolean singleSignOn;

    /**
     * Generate lower-quality diagrams. Various installations of Graphviz (depending on OS
     * and/or version) will default to generating either higher or lower quality images.
     * That is, some might not have the "lower quality" libraries and others might not have
     * the "higher quality" libraries.
     */
    @Parameter(property = "lowQuality")
    private Boolean lowQuality;

    /**
     * Generate higher-quality diagrams. Various installations of Graphviz (depending on OS
     * and/or version) will default to generating either higher or lower quality images.
     * That is, some might not have the "lower quality" libraries and others might not have
     * the "higher quality" libraries.
     */
    @Parameter(property = "highQuality")
    private Boolean highQuality;

    /**
     * Evaluate all schemas in a database. Generates a high-level index of the schemas
     * evaluated and allows for traversal of cross-schema foreign key relationships.
     * Use with -schemaSpec "schemaRegularExpression" to narrow-down the schemas to include.
     */
    @Parameter(property = "showAllSchemas")
    private Boolean showAllSchemas;

    /**
     * Evaluate specified schemas.
     * Similar to -showAllSchemas, but explicitly specifies which schema to evaluate without
     * interrogating the database's metadata. Can be used with databases like MySQL where a
     * database isn't composed of multiple schemas.
     */
    @Parameter(property = "schemas")
    private String schemas;

    /**
     * No schema required for this database (e.g. derby).
     */
    @Parameter(property = "noSchema")
    private Boolean noSchema;

    /**
     * Don't query or display row counts.
     */
    @Parameter(property = "noRows")
    private Boolean noRows;

    /**
     * Don't query or display row counts.
     */
    @Parameter(property = "noViews")
    private Boolean noViews;

    /**
     * Specifies additional properties to be used when connecting to the database.
     * Specify the entries directly, escaping the ='s with \= and separating each key\=value
     * pair with a ;.
     * 
     * May also be a file name, useful for hiding connection properties from public logs. 
     */
    @Parameter(property = "connprops")
    private String connprops;

    /**
     * Don't generate ads in reports.
     *
     * @deprecated will be removed
     */
    @Deprecated
    @Parameter(property = "noAds", defaultValue = "true")
    private Boolean noAds;

    /**
     * Don't generate sourceforge logos in reports.
     *
     * @deprecated will be removed
     */
    @Deprecated
    @Parameter(property = "noLogo", defaultValue = "true")
    private Boolean noLogo;

    @Parameter(property = "catalog", defaultValue = "%")
    private String catalog;

    @Parameter(property = "vizjs", defaultValue = "true")
    protected boolean vizjs = false;

    /**
     * Whether to create the report only on the execution root of a multi-module project.
     *
     * @since 5.0.4
     */
    @Parameter(property = "runOnExecutionRoot", defaultValue = "false")
    protected boolean runOnExecutionRoot = false;

    /**
     * The SchemaSpy analyser that generates the actual report.
     * Can be overridden for testing purposes.
     */
    private MavenSchemaAnalyzer analyzer;

    protected void setSchemaAnalyzer(final MavenSchemaAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
    * Convenience method used to build the schemaspy command line parameters.
    *
    * @param argList the current list of schemaspy parameter options.
    * @param parameter a new parameter to add
    * @param value the value for this parameter
    */
    private void addToArguments(
        final List<String> argList, final String parameter, final Boolean value) {
        if (value != null && value) {
            argList.add(parameter);
            argList.add(String.valueOf(true));
        }
    }

    /**
    * Convenience method used to build the schemaspy command line parameters.
    *
    * @param argList the current list of schemaspy parameter options.
    * @param parameter a new parameter to add
    * @param value the value for this parameter
    */
    private void addFlagToArguments(
        final List<String> argList, final String parameter, final Boolean value) {
        if (value != null && value) {
            argList.add(parameter);
        }
    }

    /**
     * Convenience method used to build the schemaspy command line parameters.
     *
     * @param argList
     *            the current list of schemaspy parameter options.
     * @param parameter
     *            a new parameter to add
     * @param value
     *            the value for this parameter
     */
    private void addToArguments(final List<String> argList, final String parameter, final String value) {
        if (value != null) {
            argList.add(parameter);
            argList.add(value);
        }
    }

    /**
     * Generate the Schemaspy report.
     *
     * @throws MavenReportException
     *             if schemaspy crashes
     * @param locale
     *            the language of the report - currently ignored.
     */
    @Override
    protected void executeReport(final Locale locale) throws MavenReportException {

        //
        // targetDirectory should be set by the maven framework. This is
        // jusr for unit testing purposes
        //
        if (targetDirectory == null) {
            targetDirectory = new File("target");
        }

        targetDirectory.mkdirs();
        File siteDir = new File(targetDirectory, "site");
        siteDir.mkdirs();
        File outputDir = null;
        if (outputDirectory == null) {
            outputDir = new File(siteDir, "schemaspy");
            outputDir.mkdirs();
            outputDirectory = outputDir.getAbsolutePath();
        } else {
            outputDir = new File(new File(outputDirectory), "schemaspy");
            outputDir.mkdirs();
        }
        String schemaSpyDirectory = outputDir.getAbsolutePath();

        List<String> argList = new ArrayList<>();

//        if ((jdbcUrl != null) && (databaseType == null)) {
//            databaseType = JDBCHelper.extractDatabaseType(jdbcUrl);
//        }
        addToArguments(argList, "-dp", pathToDrivers);
        addToArguments(argList, "-db", database);
        addToArguments(argList, "-host", host);
        addToArguments(argList, "-port", port);
        addToArguments(argList, "-t", databaseType);
        addToArguments(argList, "-u", user);
        addToArguments(argList, "-p", password);
        addToArguments(argList, "-s", schema);
        addToArguments(argList, "-o", schemaSpyDirectory);
        addToArguments(argList, "-desc", schemaDescription);
        addToArguments(argList, "-i", includeTableNamesRegex);
        addToArguments(argList, "-x", excludeColumnNamesRegex);
        addFlagToArguments(argList, "-ahic", allowHtmlInComments);
        addFlagToArguments(argList, "-noimplied", noImplied);
        addFlagToArguments(argList, "-nohtml", noHtml);
        addToArguments(argList, "-loglevel", logLevel);
        addFlagToArguments(argList, "-norows", noRows);
        addFlagToArguments(argList, "-noviews", noViews);
        addFlagToArguments(argList, "-noschema", noSchema);
        addFlagToArguments(argList, "-all", showAllSchemas);
        addToArguments(argList, "-schemas", schemas);

        addToArguments(argList, "-useDriverManager", useDriverManager);
        addToArguments(argList, "-css", cssStylesheet);
        addFlagToArguments(argList, "-sso", singleSignOn);
        addFlagToArguments(argList, "-lq", lowQuality);
        addFlagToArguments(argList, "-hq", highQuality);
        addToArguments(argList, "-connprops", connprops);
        addFlagToArguments(argList, "-cid", commentsInitiallyDisplayed);
        addFlagToArguments(argList, "-noads", noAds);
        addFlagToArguments(argList, "-nologo", noLogo);
        addToArguments(argList, "-cat", catalog);
        addFlagToArguments(argList, "-vizjs", vizjs);
//        addToArguments(argList, "-jdbcUrl", jdbcUrl);

        try {
            if(postgresDdlScript!=null && !postgresDdlScript.trim().isEmpty()){
                if(database==null){
                    database = "postgres";
                    addToArguments(argList, "-db", database);
                }
                if(postgresImage==null || postgresImage.isEmpty()){
                    postgresImage = "postgresql:9.6.8";
                }
                String jdbcUrl = "jdbc:tc:" + postgresImage +
                        ":///"+database+"?TC_TMPFS=/testtmpfs:rw&TC_INITSCRIPT=" + postgresDdlScript;
                try (Connection connection = DriverManager.getConnection(jdbcUrl)){
                    ResultSet resultSet = connection.createStatement().executeQuery("select 1");
                    while (resultSet.next()){
                        System.out.println(resultSet.getString(1));
                    }
                    if(databaseType==null){
                        databaseType = "pgsql";
                        addToArguments(argList, "-t", databaseType);
                    }
                    if(user==null){
                        user="test";
                        addToArguments(argList, "-u", user);
                    }
                    if(password==null){
                        password="test";
                        addToArguments(argList, "-p", password);
                    }
                    analyze(argList, connection);
                }
            } else {
                analyze(argList);
            }
        } catch (Exception e) {
            throw new MavenReportException(e.getMessage(), e);
        }
    }

    private static void analyze(List<String> argList, Connection connection) throws SQLException, IOException {
        String[] args = argList.toArray(new String[0]);
        CommandLineArgumentParser parser = new CommandLineArgumentParser(new CommandLineArguments(), null);
        CommandLineArguments cliArgs = parser.parse(args);
        SqlService sqlService = new SqlService(){
            @Override
            public DatabaseMetaData connect(ConnectionConfig connectionConfig) throws IOException, SQLException {
                return connect(connection);
            }
        };
        DatabaseServiceFactory databaseServiceFactory = new DatabaseServiceFactory(sqlService);
        OutputProducer outputProducer = new XmlProducerUsingDOM();
        LayoutFolder layoutFolder = new LayoutFolder(SchemaAnalyzer.class.getClassLoader());
        SchemaAnalyzer analyzer = new SchemaAnalyzer(sqlService, databaseServiceFactory, cliArgs, outputProducer, layoutFolder);
        analyzer.analyze();
    }

    private void analyze(List<String> argList) throws SQLException, IOException {
        if (analyzer == null) {
            analyzer = new MavenSchemaAnalyzer();
            analyzer.applyConfiguration(argList);
        }
        analyzer.analyze();
    }

    @Override
    public boolean canGenerateReport() {
        return !runOnExecutionRoot || project.isExecutionRoot();
    }

    @Override
    protected String getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    protected MavenProject getProject() {
        return project;
    }

    @Override
    protected Renderer getSiteRenderer() {
        return siteRenderer;
    }

    @Override
    public String getDescription(final Locale locale) {
        return "SchemaSpy database documentation";
    }

    @Override
    public String getName(final Locale locale) {
        return "SchemaSpy";
    }

    @Override
    public String getOutputName() {
        return "schemaspy/index";
    }

    /**
     * Always return {@code true} as we're using the report generated by SchemaSpy
     * rather than creating our own report.
     *
     * @return {@code true}
     */
    @Override
    public boolean isExternalReport() {
        return true;
    }
}
