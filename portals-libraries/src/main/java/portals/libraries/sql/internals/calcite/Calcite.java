package portals.libraries.sql.internals.calcite;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableInterpretable;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.config.CalciteConnectionConfigImpl;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.interpreter.BindableConvention;
import org.apache.calcite.interpreter.BindableRel;
import org.apache.calcite.interpreter.Bindables;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.plan.*;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.logical.*;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.*;
import org.apache.calcite.runtime.Bindable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlOperatorTables;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.sql2rel.StandardConvertletTable;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Most content of this class are similar to this
 *   https://github.com/zabetak/calcite/blob/demo-january-2021/core/src/test/java/org/apache/calcite/examples/foodmart/java/EndToEndExampleBindable.java
 * Some noticable points for this class:
 *   The `registerTable` function needs to be called by the user to create a table,
 *   This is where we register our MPFTable (Modifiable-Projectable-Filterable Table) to Calcite Schema
 *   Calcite will call the add/scan function for TableStore/MPFTable, in these two functions we need to
 *   send out subqueries by calling callbacks given from Portals
 */
public class Calcite {

    // An example about how to use this class
    public static void main(String[] args) throws SqlParseException, InterruptedException {
        Calcite calcite = new Calcite();
        calcite.registerTable("Book",
                ImmutableList.of(SqlTypeName.INTEGER, SqlTypeName.VARCHAR, SqlTypeName.INTEGER, SqlTypeName.INTEGER),
                ImmutableList.of("id", "title", "year", "author"), 0);
        // asking
        calcite.getTable("Book").setGetFutureByRowKeyFunc(o -> new FutureWithResult(null, null));
        calcite.getTable("Book").setInsertRow(objects -> {
            System.out.println("insert " + Arrays.toString(objects));
            return new FutureWithResult(null, null);
        });

        BlockingQueue<Integer> futureReadyCond = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> awaitForFutureCond = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> awaitForFinishCond = new LinkedBlockingQueue<>();
        List<Object[]> result = new ArrayList<>();
        List<FutureWithResult> futures = new ArrayList<>();
        // calcite: return all asking + cond.await()
        // portals: awaitAll(futures) { fill in the data + cond.signal() +
        // awaitForResultCond.await() }
        // calcite: finish the query, fill the output + awaitForResultCond.signal()
        calcite.executeSQL(
                "INSERT INTO Book (id, title, \"year\", author) VALUES (6, 'The Lord of the Rings', 1954, 1)",
                // calcite.executeSQL("SELECT * FROM Book WHERE \"year\" > 1829 AND id IN (1, 2,
                // 3)",
                futureReadyCond, awaitForFutureCond, awaitForFinishCond, new LinkedBlockingQueue<>(), futures, result);
        // signal at awaitAll when data is ready
        futureReadyCond.take();
        for (FutureWithResult future : futures) {
            future.futureResult = new Object[] { 1, "Les Miserables", 1862, 0 };
        }
        awaitForFutureCond.put(1);
        awaitForFinishCond.take();

        for (Object[] row : result) {
            System.out.println(Arrays.toString(row));
        }

        // ======================

        futureReadyCond = new LinkedBlockingQueue<>();
        awaitForFutureCond = new LinkedBlockingQueue<>();
        awaitForFinishCond = new LinkedBlockingQueue<>();
        result = new ArrayList<>();
        futures = new ArrayList<>();
        // calcite: return all asking + cond.await()
        // portals: awaitAll(futures) { fill in the data + cond.signal() +
        // awaitForResultCond.await() }
        // calcite: finish the query, fill the output + awaitForResultCond.signal()
        calcite.executeSQL("SELECT * FROM Book WHERE \"year\" > 1829 AND id IN (1, 2, 3)",
                futureReadyCond, awaitForFutureCond, awaitForFinishCond, new LinkedBlockingQueue<>(), futures, result);

        futureReadyCond.take();
        // at awaitAll
        for (FutureWithResult future : futures) {
            future.futureResult = new Object[] { 1, "Les Miserables", 1862, 0 };
        }
        awaitForFutureCond.put(1);
        awaitForFinishCond.take();
        // awaitAll end

        for (Object[] row : result) {
            System.out.println(Arrays.toString(row));
        }
    }

    private CalciteSchema schema;
    private RelDataTypeFactory typeFactory;
    private static ThreadPoolExecutor executor = new ThreadPoolExecutor(100, 100, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());
    // private ThreadPoolExecutor executor;
    private Map<String, MPFTable> registeredTable = new HashMap<>();

    public MPFTable getTable(String tableName) {
        return registeredTable.get(tableName);
    }

    public Calcite() {
        // Instantiate a type factory for creating types (e.g., VARCHAR, NUMERIC, etc.)
        typeFactory = new JavaTypeFactoryImpl();
        // Create the root schema describing the data model
        schema = CalciteSchema.createRootSchema(true);
    }

    // Register table schema to Calcite
    public void registerTable(String tableName, List<SqlTypeName> columnTypes, List<String> columnNames,
            int pkIndex) {
        RelDataTypeFactory.Builder tableType = new RelDataTypeFactory.Builder(typeFactory);
        for (int i = 0; i < columnNames.size(); i++) {
            tableType.add(columnNames.get(i), columnTypes.get(i));
        }

        // Initialize table
        //
        if (schema.getTable(tableName, false) == null) {
            MPFTable table = new MPFTable(tableName, tableType.build(), new TableStore<>(this), pkIndex, this);
            registeredTable.put(tableName, table);
            schema.add(tableName, table);
        }
    }

    public boolean printPlan = true;
    public boolean debug = false;
    BlockingQueue<Integer> futureReadyCond;
    BlockingQueue<Integer> awaitForFutureCond;
    BlockingQueue<Integer> awaitForSQLCompletionCond;
    BlockingQueue<Integer> tableOpCntCond;

    List<FutureWithResult> futures; // for outside usage only
    Map<String, List<FutureWithResult>> tableFutures = new HashMap<>();

    List<Object[]> result;

    /**
     *
     * @param sql: The SQL query in String
     * @param futureReadyCond:
     *   Used by Calcite to notify portals that all sub-queries for
     *   one table are sent
     * @param awaitForFutureCond:
     *   Used by Portals to notify Calcite that all sub-queries
     *   are returned
     * @param awaitForSQLCompletionCond:
     *   Used by Calcite to notify portals that the SQL
     *   execution is finished
     * @param tableScanCntCond:
     *   Used by Calcite to tell Portals how many tables are involved
     *   in this query
     * @param futures:
     *   A container for holding the Futures objects
     * @param result
     *   A container for holding the final query result
     */
    public void executeSQL(String sql, BlockingQueue<Integer> futureReadyCond,
            BlockingQueue<Integer> awaitForFutureCond,
            BlockingQueue<Integer> awaitForSQLCompletionCond,
            BlockingQueue<Integer> tableScanCntCond,
            List<FutureWithResult> futures,
            List<Object[]> result) throws InterruptedException {
        this.futureReadyCond = futureReadyCond;
        this.awaitForFutureCond = awaitForFutureCond;
        this.awaitForSQLCompletionCond = awaitForSQLCompletionCond;
        this.tableOpCntCond = tableScanCntCond;
        this.futures = futures;
        this.result = result;
        Runnable runnable = () -> {
            try {
                executeSQL0(sql);
            } catch (SqlParseException | InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionAbortException e) {
                // System.out.printf("Execution for [%s] aborted\n", sql);
            }
        };
        executor.execute(runnable);
        // (new ArrayList<>()).stream().collect(Collectors.toList())
    }

    private void executeSQL0(String sql) throws SqlParseException, InterruptedException {
        // System.out.println("====== Execute SQL: " + sql + " ======");

        // Parse the query into an AST
        long parsingStartTime = System.nanoTime();

        SqlParser parser = SqlParser.create(sql);
        SqlNode sqlNode = parser.parseQuery();

        long parsingEndTime = System.nanoTime();
        CalciteStat.recordParsing(parsingEndTime - parsingStartTime);

        // Configure and instantiate validator
        Properties props = new Properties();
        props.setProperty(CalciteConnectionProperty.CASE_SENSITIVE.camelName(), "false");
        CalciteConnectionConfig config = new CalciteConnectionConfigImpl(props);
        CalciteCatalogReader catalogReader = new CalciteCatalogReader(schema,
                Collections.singletonList(""),
                typeFactory, config);

        final SqlOperatorTable opTab0 = SqlStdOperatorTable.instance();
        final List<SqlOperatorTable> list = new ArrayList<>();
        list.add(opTab0);
        list.add(catalogReader);
        final SqlOperatorTable opTab = SqlOperatorTables.chain(list);

        SqlValidator validator = SqlValidatorUtil.newValidator(opTab,
                catalogReader, typeFactory,
                SqlValidator.Config.DEFAULT);

        // Validate the initial AST
        SqlNode validNode = validator.validate(sqlNode);

        long validationEndTime = System.nanoTime();
        CalciteStat.recordValidation(validationEndTime - parsingEndTime);

        // Configure and instantiate the converter of the AST to Logical plan (requires
        // opt cluster)
        RelOptCluster cluster = newCluster(typeFactory);
        SqlToRelConverter relConverter = new SqlToRelConverter(
                NOOP_EXPANDER,
                validator,
                catalogReader,
                cluster,
                StandardConvertletTable.INSTANCE,
                SqlToRelConverter.config());

        // Convert the valid AST into a logical plan
        RelNode logPlan = relConverter.convertQuery(validNode, false, true).rel;

        // report tableScanCnt
        int cnt = getTableOpCnt(logPlan);
        tableOpCntCond.put(cnt);

        // Display the logical plan
        if (printPlan) {
            System.out.println(
                    RelOptUtil.dumpPlan("[Logical plan]", logPlan, SqlExplainFormat.TEXT,
                            SqlExplainLevel.EXPPLAN_ATTRIBUTES));
        }

        long planingEndTime = 0;

        // Using different convention for modification and selection queries
        // is a hack to to make sure Calcite will call MPFTable's scan and TableStore's
        // add method when select/insert
        if (logPlan instanceof LogicalTableModify) {
            LogicalTableModify modify = (LogicalTableModify) logPlan;
            // INSERT is the only supported modification operation by Calcite
            if (modify.getOperation() == TableModify.Operation.INSERT) {
                Bindable executablePlan = enumerableConventionPlanExecution(logPlan, cluster);
                planingEndTime = System.nanoTime();
                CalciteStat.recordPlanning(planingEndTime - validationEndTime);

                // Run the executable plan using a context simply providing access to the schema
                for (Object row : executablePlan.bind(new SchemaOnlyDataContext(schema))) {
                    result.add(new Object[] { row });
                }
                awaitForSQLCompletionCond.put(1);
            }
        } else {
            BindableRel phyPlan = bindableConventionPlanExecution(logPlan, cluster);
            planingEndTime = System.nanoTime();
            CalciteStat.recordPlanning(planingEndTime - validationEndTime);

            // Run the executable plan using a context simply providing access to the schema
            for (Object[] row : phyPlan.bind(new SchemaOnlyDataContext(schema))) {
                result.add(row);
            }
            awaitForSQLCompletionCond.put(1);
        }

        CalciteStat.recordExecution(System.nanoTime() - planingEndTime);

        // re-initiate state after execution
        initState();
    }

    private int getTableOpCnt(RelNode logPlan) {
        if (logPlan instanceof LogicalTableModify) {
            return ((LogicalValues) logPlan.getInput(0)).tuples.size();
        } else if (logPlan instanceof LogicalTableScan || logPlan instanceof LogicalValues) {
            return 1;
        } else {
            int cnt = 0;
            for (RelNode input : logPlan.getInputs()) {
                cnt += getTableOpCnt(input);
            }
            return cnt;
        }
    }

    private Bindable enumerableConventionPlanExecution(RelNode logPlan, RelOptCluster cluster)
            throws InterruptedException {
        RelOptPlanner planner = cluster.getPlanner();
        planner.addRule(EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE);
        planner.addRule(CoreRules.PROJECT_TO_CALC);
        // planner.addRule(CoreRules.FILTER_SCAN);
        planner.addRule(CoreRules.FILTER_TO_CALC);
        planner.addRule(EnumerableRules.ENUMERABLE_CALC_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_JOIN_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_SORT_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_LIMIT_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_AGGREGATE_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_VALUES_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_UNION_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_MINUS_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_INTERSECT_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_MATCH_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_WINDOW_RULE);
        planner.addRule(EnumerableRules.ENUMERABLE_TABLE_MODIFICATION_RULE);

        // Define the type of the output plan (in this case we want a physical plan in
        // EnumerableContention)
        logPlan = planner.changeTraits(logPlan,
                cluster.traitSet().replace(EnumerableConvention.INSTANCE));
        planner.setRoot(logPlan);

        // Start the optimization process to obtain the most efficient physical plan
        // based on the
        // provided rule set.
        EnumerableRel phyPlan = (EnumerableRel) planner.findBestExp();

        // Display the physical plan
        if (printPlan) {
            System.out.println(
                    RelOptUtil.dumpPlan("[Physical plan]", phyPlan, SqlExplainFormat.TEXT,
                            SqlExplainLevel.NON_COST_ATTRIBUTES));
        }

        // Obtain the executable plan
        return EnumerableInterpretable.toBindable(
                new HashMap<>(),
                null,
                phyPlan,
                EnumerableRel.Prefer.ARRAY);
    }

    private BindableRel bindableConventionPlanExecution(RelNode logPlan, RelOptCluster cluster)
            throws InterruptedException {
        // Initialize optimizer/planner with the necessary rules
        RelOptPlanner planner = cluster.getPlanner();
        planner.addRule(CoreRules.FILTER_SCAN);
        planner.addRule(CoreRules.FILTER_INTO_JOIN);
        planner.addRule(Bindables.BINDABLE_TABLE_SCAN_RULE);
        planner.addRule(Bindables.BINDABLE_FILTER_RULE);
        planner.addRule(Bindables.BINDABLE_JOIN_RULE);
        planner.addRule(Bindables.BINDABLE_PROJECT_RULE);
        planner.addRule(Bindables.BINDABLE_SORT_RULE);
        planner.addRule(Bindables.BINDABLE_SORT_RULE);

        // Define the type of the output plan (in this case we want a physical plan in
        // BindableConvention)
        logPlan = planner.changeTraits(logPlan,
                cluster.traitSet().replace(BindableConvention.INSTANCE));
        planner.setRoot(logPlan);
        // Start the optimization process to obtain the most efficient physical plan
        // based on the
        // provided rule set.
        BindableRel phyPlan = (BindableRel) planner.findBestExp();

        // Display the physical plan
        if (printPlan) {
            System.out.println(
                    RelOptUtil.dumpPlan("[Physical plan]", phyPlan, SqlExplainFormat.TEXT,
                            SqlExplainLevel.NON_COST_ATTRIBUTES));
        }

        return phyPlan;
    }

    private void initState() {
        futures = new ArrayList<>();
        tableFutures = new HashMap<>();
    }

    private static RelOptCluster newCluster(RelDataTypeFactory factory) {
        RelOptPlanner planner = new VolcanoPlanner();
        planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
        return RelOptCluster.create(planner, new RexBuilder(factory));
    }

    private static final RelOptTable.ViewExpander NOOP_EXPANDER = (rowType, queryString, schemaPath, viewPath) -> null;

    /**
     * A simple data context only with schema information.
     */
    private static final class SchemaOnlyDataContext implements DataContext {
        private final SchemaPlus schema;

        SchemaOnlyDataContext(CalciteSchema calciteSchema) {
            this.schema = calciteSchema.plus();
        }

        @Override
        public SchemaPlus getRootSchema() {
            return schema;
        }

        @Override
        public JavaTypeFactory getTypeFactory() {
            return new JavaTypeFactoryImpl();
        }

        @Override
        public QueryProvider getQueryProvider() {
            return null;
        }

        @Override
        public Object get(final String name) {
            return null;
        }
    }
}

// We store table rows as Object[] here, the add function is overridden
// to call subqueries
class TableStore<T> extends ArrayList<T> {
    public TableStore(Collection<? extends T> c) {
        super(c);
    }

    private Calcite calcite;
    public Function<Object[], FutureWithResult> insertRow;

    public TableStore(Calcite calcite) {
        this.calcite = calcite;
    }

    @Override
    public boolean add(T t) {

        FutureWithResult futureWithResult = insertRow.apply((Object[]) t);
        CalciteStat.recordMessage();
        calcite.futures.add(futureWithResult);

        // tell portals that all sub-queries are sent
        // and all corresponding futures are there
        try {
            calcite.futureReadyCond.put(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // if this is the last TableScan node being executed as leaf nodes,
        // return from this function means data for all leaf nodes are ready
        // and the real query execution will start, we want to block here until
        // portals says we may continue
        try {
            // wait for awaitAll callback to be called, so the asking is actually executed
            calcite.awaitForFutureCond.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (calcite.debug)
            System.out.println("add " + t);

        return true;
    }
}

class ExecutionAbortException extends RuntimeException {
    public ExecutionAbortException(String message) {
        super(message);
    }
}
