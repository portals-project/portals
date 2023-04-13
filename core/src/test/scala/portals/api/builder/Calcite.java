package portals.api.builder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.plan.*;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.logical.*;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.*;
import org.apache.calcite.runtime.Bindable;
import org.apache.calcite.schema.ModifiableTable;
import org.apache.calcite.schema.ProjectableFilterableTable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Schemas;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.type.BasicSqlType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlOperatorTables;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.sql2rel.StandardConvertletTable;
import org.apache.calcite.util.Sarg;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


/**
 * An end to end example from an SQL query to a plan in Bindable convention.
 */
public class Calcite {
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
        // portals: awaitAll(futures) { fill in the data + cond.signal() + awaitForResultCond.await() }
        // calcite: finish the query, fill the output + awaitForResultCond.signal()
        calcite.executeSQL("INSERT INTO Book (id, title, \"year\", author) VALUES (6, 'The Lord of the Rings', 1954, 1)",
//        calcite.executeSQL("SELECT * FROM Book WHERE \"year\" > 1829 AND id IN (1, 2, 3)",
                futureReadyCond, awaitForFutureCond, awaitForFinishCond, new LinkedBlockingQueue<>(), futures, result);
        // signal at awaitAll when data is ready
        futureReadyCond.take();
        for (FutureWithResult future : futures) {
            future.futureResult = new Object[]{1, "Les Miserables", 1862, 0};
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
        // portals: awaitAll(futures) { fill in the data + cond.signal() + awaitForResultCond.await() }
        // calcite: finish the query, fill the output + awaitForResultCond.signal()
        calcite.executeSQL("SELECT * FROM Book WHERE \"year\" > 1829 AND id IN (1, 2, 3)",
                futureReadyCond, awaitForFutureCond, awaitForFinishCond, new LinkedBlockingQueue<>(), futures, result);

        futureReadyCond.take();
        // at awaitAll
        for (FutureWithResult future : futures) {
            future.futureResult = new Object[]{1, "Les Miserables", 1862, 0};
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
    private ThreadPoolExecutor executor;
    private Map<String, MPFTable> registeredTable = new HashMap<>();

    public MPFTable getTable(String tableName) {
        return registeredTable.get(tableName);
    }

    public Calcite() {
        // Instantiate a type factory for creating types (e.g., VARCHAR, NUMERIC, etc.)
        typeFactory = new JavaTypeFactoryImpl();
        // Create the root schema describing the data model
        schema = CalciteSchema.createRootSchema(true);
        // Instantiate task executor
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    //    public QTable(String tableName, List<SqlTypeName> columnTypes, List<String> columnNames, int pkIndex, Consumer<Object> queryRow, Consumer<Object[]> insertRow) {
    public void registerTable(String tableName, List<SqlTypeName> columnTypes, List<String> columnNames,
                              int pkIndex) {
        RelDataTypeFactory.Builder tableType = new RelDataTypeFactory.Builder(typeFactory);
        for (int i = 0; i < columnNames.size(); i++) {
            tableType.add(columnNames.get(i), columnTypes.get(i));
        }

        // Initialize table
        if (schema.getTable(tableName, false) == null)
            MPFTable = new MPFTable(tableName, tableType.build(), new MyList<>(this), pkIndex, this);
        registeredTable.put(tableName, MPFTable);
        schema.add(tableName, MPFTable);
    }

    public boolean printPlan = true;
    public boolean debug = false;
    // TODO: can be removed
    MPFTable MPFTable;
    BlockingQueue<Integer> futureReadyCond;
    BlockingQueue<Integer> awaitForFuturesCond;
    BlockingQueue<Integer> awaitForSQLCompletionCond;
    BlockingQueue<Integer> tableOpCntCond;

    List<FutureWithResult> futures; // for outside usage only
    Map<String, List<FutureWithResult>> tableFutures = new HashMap<>();

    List<Object[]> result;

    public void executeSQL(String sql, BlockingQueue<Integer> futureReadyCond, BlockingQueue<Integer> awaitForFutureCond,
                           BlockingQueue<Integer> awaitForSQLCompletionCond,
                           BlockingQueue<Integer> tableScanCntCond,
                           List<FutureWithResult> futures,
                           List<Object[]> result) throws InterruptedException {
        this.futureReadyCond = futureReadyCond;
        this.awaitForFuturesCond = awaitForFutureCond;
        this.awaitForSQLCompletionCond = awaitForSQLCompletionCond;
        this.tableOpCntCond = tableScanCntCond;
        this.futures = futures;
        this.result = result;
        Runnable runnable = () -> {
            try {
                executeSQL0(sql);
            } catch (SqlParseException | InterruptedException e) {
                e.printStackTrace();
            }
        };
        executor.execute(runnable);
//        (new ArrayList<>()).stream().collect(Collectors.toList())
    }

    private void executeSQL0(String sql) throws SqlParseException, InterruptedException {
        System.out.println("====== Execute SQL: " + sql + " ======");

        // Parse the query into an AST
        SqlParser parser = SqlParser.create(sql);
        SqlNode sqlNode = parser.parseQuery();

        // Configure and instantiate validator
        Properties props = new Properties();
        props.setProperty(CalciteConnectionProperty.CASE_SENSITIVE.camelName(), "false");
        CalciteConnectionConfig config = new CalciteConnectionConfigImpl(props);
        CalciteCatalogReader catalogReader = new CalciteCatalogReader(schema,
                Collections.singletonList(""),
                typeFactory, config);

        // NOTE: 新增代码
        // 从 org.apache.calcite.prepare.CalcitePrepareImpl.createSqlValidator 抄来的
        final SqlOperatorTable opTab0 = SqlStdOperatorTable.instance();
        final List<SqlOperatorTable> list = new ArrayList<>();
        list.add(opTab0);
        list.add(catalogReader);
        final SqlOperatorTable opTab = SqlOperatorTables.chain(list);

        // NOTE：这里的 newValidator param1 不能用默认的 SqlStdOperatorTable.instance()，其中只有自带的函数
        SqlValidator validator = SqlValidatorUtil.newValidator(opTab,
                catalogReader, typeFactory,
                SqlValidator.Config.DEFAULT);

        // Validate the initial AST
        SqlNode validNode = validator.validate(sqlNode);

        // Configure and instantiate the converter of the AST to Logical plan (requires opt cluster)
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
        tableOpCntCond.put(getTableOpCnt(logPlan));

        // Display the logical plan
        if (printPlan) {
            System.out.println(
                    RelOptUtil.dumpPlan("[Logical plan]", logPlan, SqlExplainFormat.TEXT,
                            SqlExplainLevel.EXPPLAN_ATTRIBUTES));
        }

        if (logPlan instanceof LogicalTableModify) {
            LogicalTableModify modify = (LogicalTableModify) logPlan;
            if (modify.getOperation() == TableModify.Operation.DELETE) {
                LogicalProject project = (LogicalProject) modify.getInput();
                LogicalFilter filter = (LogicalFilter) project.getInput();
                LogicalTableScan scan = (LogicalTableScan) filter.getInput();
                String tableName = scan.getTable().getQualifiedName().get(0);
                RexCall cond = (RexCall) filter.getCondition();
                if (cond.op != SqlStdOperatorTable.EQUALS) {
                    throw new RuntimeException("Unsupported condition: " + cond.op);
                }
                // TODO: AND()
                RexInputRef left = (RexInputRef) cond.operands.get(0);
                RexLiteral right = (RexLiteral) cond.operands.get(1);
                // TODO: key will be on the right, call delete manually (delete(tableName, key))
            } else if (modify.getOperation() == TableModify.Operation.INSERT) {
//                (RexInputRef) cond.operands.get(0);
                enumerableConventionPlanExecution(logPlan, cluster);
            } else if (modify.getOperation() == TableModify.Operation.UPDATE) {
                throw new RuntimeException("Unsupported operation: " + modify.getOperation());
            }
        } else {
            bindableConventionPlanExecution(logPlan, cluster);
        }
    }

    private int getTableOpCnt(RelNode logPlan) {
        if (logPlan instanceof LogicalTableScan || logPlan instanceof LogicalValues) {
            return 1;
        } else {
            int cnt = 0;
            for (RelNode input : logPlan.getInputs()) {
                cnt += getTableOpCnt(input);
            }
            return cnt;
        }
    }

    /**
     * output:
     * x:1844
     * [4, The three Musketeers, 3688]
     * x:1884
     * [5, The Count of Monte Cristo, 3768]
     *
     * @param logPlan
     * @param cluster
     */
    private void enumerableConventionPlanExecution(RelNode logPlan, RelOptCluster cluster) throws InterruptedException {
        RelOptPlanner planner = cluster.getPlanner();
        planner.addRule(EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE);
        planner.addRule(CoreRules.PROJECT_TO_CALC);
//        planner.addRule(CoreRules.FILTER_SCAN);
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

        // Start the optimization process to obtain the most efficient physical plan based on the
        // provided rule set.
        EnumerableRel phyPlan = (EnumerableRel) planner.findBestExp();

        // Display the physical plan
        if (printPlan) {
            System.out.println(
                    RelOptUtil.dumpPlan("[Physical plan]", phyPlan, SqlExplainFormat.TEXT,
                            SqlExplainLevel.NON_COST_ATTRIBUTES));
        }

        // Obtain the executable plan
        Bindable executablePlan = EnumerableInterpretable.toBindable(
                new HashMap<>(),
                null,
                phyPlan,
                EnumerableRel.Prefer.ARRAY);
        // Run the executable plan using a context simply providing access to the schema
        for (Object row : executablePlan.bind(new SchemaOnlyDataContext(schema))) {
//            if (row instanceof Object[]) {
//                System.out.println(Arrays.toString((Object[]) row));
//            } else {
//                System.out.println(row);
//            }
            result.add(new Object[]{row});
        }
        awaitForSQLCompletionCond.put(1);

        init();
    }

    private void bindableConventionPlanExecution(RelNode logPlan, RelOptCluster cluster) throws InterruptedException {
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
        // Start the optimization process to obtain the most efficient physical plan based on the
        // provided rule set.
        BindableRel phyPlan = (BindableRel) planner.findBestExp();

        // Display the physical plan
        if (printPlan) {
            System.out.println(
                    RelOptUtil.dumpPlan("[Physical plan]", phyPlan, SqlExplainFormat.TEXT,
                            SqlExplainLevel.NON_COST_ATTRIBUTES));
        }

        // Run the executable plan using a context simply providing access to the schema
        for (Object[] row : phyPlan.bind(new SchemaOnlyDataContext(schema))) {
//            System.out.println(Arrays.toString(row));
            result.add(row);
        }
        awaitForSQLCompletionCond.put(1);

        init();
    }

    private void init() {
        futures = new ArrayList<>();
        tableFutures = new HashMap<>();
    }

    private static RelOptCluster newCluster(RelDataTypeFactory factory) {
        RelOptPlanner planner = new VolcanoPlanner();
        planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
        return RelOptCluster.create(planner, new RexBuilder(factory));
    }

    private static final RelOptTable.ViewExpander NOOP_EXPANDER = (rowType, queryString, schemaPath
            , viewPath) -> null;

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

    interface DeletableTable {
        void delete(List<Object[]> keys);
    }

    // TODO: declare table (+register)


    class MPFTable extends AbstractTable implements ModifiableTable, ProjectableFilterableTable, DeletableTable {

        private String tableName;
        private MyList<Object[]> data;
        private final RelDataType rowType;
        private int pkIndex = 0;
        private List<RexLiteral> pkPredicates = new ArrayList<>();
        private Function<Object, FutureWithResult> getFutureByRowKeyFunc;
        private Calcite calcite;

        public MPFTable(String tableName, RelDataType rowType, MyList<Object[]> data, int pkIndex, Calcite calcite) {
            this.tableName = tableName;
            this.data = data;
            this.rowType = rowType;
            this.pkIndex = pkIndex;
            this.calcite = calcite;
        }

        public void setInsertRow(Function<Object[], FutureWithResult> insertRow) {
            this.data.insertRow = insertRow;
        }

        public void setGetFutureByRowKeyFunc(Function<Object, FutureWithResult> getFutureByRowKeyFunc) {
            this.getFutureByRowKeyFunc = getFutureByRowKeyFunc;
        }

        @Override
        public void delete(List<Object[]> keys) {

        }

        @Override
        public Enumerable<Object[]> scan(DataContext root, List<RexNode> filters, int[] projects) {
            // TODO: filter
            if (calcite.debug) System.out.println("scan");

            List<RexNode> newFilters = copyAndRemovePKPredicate(filters);
            filters.clear();
            filters.addAll(newFilters);

            for (RexLiteral pkPredicate : pkPredicates) {
                // ask, return future
                FutureWithResult futureWithResult = getFutureByRowKeyFunc.apply(pkPredicate.getValue());
                calcite.futures.add(futureWithResult);
                calcite.tableFutures.putIfAbsent(tableName, new ArrayList<>());
                calcite.tableFutures.get(tableName).add(futureWithResult);
            }

            // tell outside that they can get these futures and call awaitAll
            try {
                System.out.println("put futureReadyCond " + tableName);
                calcite.futureReadyCond.put(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                // wait for awaitAll callback to be called, so the asking is actually executed
                calcite.awaitForFuturesCond.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("return tableScan " + tableName);

            return new AbstractEnumerable<Object[]>() {
                public Enumerator<Object[]> enumerator() {
                    return new Enumerator<Object[]>() {
                        int row = -1;
                        Object[] current;

                        public Object[] current() {
                            return current;
                        }

                        public boolean moveNext() {
                            while (++row < pkPredicates.size()) {
                                // await
//                                Object[] current = data.get(row);
                                Object[] current = calcite.tableFutures.get(tableName).get(row).futureResult;
                                System.out.println("moveNext " + tableName + " " + row + " " + Arrays.toString(current));
                                if (current == null) {
                                    continue;
                                }
                                if (projects == null) {
                                    this.current = current;
                                } else {
                                    Object[] newCurrent = new Object[projects.length];
                                    for (int i = 0; i < projects.length; i++) {
                                        newCurrent[i] = current[projects[i]];
                                    }
                                    this.current = newCurrent;
                                }
                                return true;
                            }
                            return false;
                        }

                        public void reset() {
                            row = -1;
                        }

                        public void close() {
                        }
                    };
                }
            };
        }

        @Override
        public Collection getModifiableCollection() {
            return data;
        }

        @Override
        public TableModify toModificationRel(RelOptCluster cluster, RelOptTable table, Prepare.CatalogReader catalogReader, RelNode child, TableModify.Operation operation, List<String> updateColumnList, List<RexNode> sourceExpressionList, boolean flattened) {
            return LogicalTableModify.create(table, catalogReader, child, operation,
                    updateColumnList, sourceExpressionList, flattened);
        }

        public RelDataType getRowType(RelDataTypeFactory relDataTypeFactory) {
            return rowType;
        }

        // should not be called
        @Override
        public <T> Queryable<T> asQueryable(QueryProvider queryProvider, SchemaPlus schema, String tableName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Type getElementType() {
            return Object[].class;
        }

        @Override
        public Expression getExpression(SchemaPlus schema, String tableName, Class clazz) {
            return Schemas.tableExpression(schema, getElementType(), tableName, clazz);
        }

        private List<RexNode> copyAndRemovePKPredicate(List<RexNode> targetList) {
            if (targetList.isEmpty()) {
                return new ArrayList<>();
            }

            this.pkPredicates.clear();

            assert targetList.size() == 1;
            RexCall target = (RexCall) targetList.get(0);
            List<RexLiteral> pkPredicates = isPKPredicate(target);
            if (pkPredicates != null) {
                this.pkPredicates.addAll(pkPredicates);
                return new ArrayList<>();
            }

            return Collections.singletonList(target.clone(target.getType(), removePkPredicate(target.operands)));
        }

        private Set<SqlOperator> leafOperators() {
            return ImmutableSet.of(SqlStdOperatorTable.EQUALS, SqlStdOperatorTable.AND, SqlStdOperatorTable.OR);
        }

        private ImmutableList<RexNode> removePkPredicate(ImmutableList<RexNode> operands) {
            List<RexNode> ans = new ArrayList<>();
            for (RexNode operand : operands) {
                if (operand instanceof RexCall) {
                    RexCall operandCall = (RexCall) operand;
                    List<RexLiteral> pkPredicates = isPKPredicate(operandCall);
                    if (pkPredicates != null) {
                        this.pkPredicates.addAll(pkPredicates);
                    } else {
                        // not equal pk predicate, but may be tree leaf
                        if (leafOperators().contains(operandCall.op)) {
                            ans.add(operand);
                        } else {
                            ans.add(operandCall.clone(operandCall.getType(), removePkPredicate(operandCall.operands)));
                        }
                    }
                } else {
                    // TODO: when will this happen?
                    ans.add(operand);
                }
            }
            return ImmutableList.copyOf(ans);
        }

        private List<RexLiteral> isPKPredicate(RexCall rexCall) {
            if (rexCall.op == SqlStdOperatorTable.EQUALS) {
                if (rexCall.operands.get(0) instanceof RexInputRef) {
                    RexInputRef ref = (RexInputRef) rexCall.operands.get(0);
                    if (ref.getIndex() == pkIndex) {
                        return Collections.singletonList((RexLiteral) rexCall.operands.get(1));
                    }
                }
            } else if (rexCall.op == SqlStdOperatorTable.OR) {
                List<RexLiteral> ans = new ArrayList<>();
                for (RexNode operand : rexCall.operands) {
                    if (operand instanceof RexCall) {
                        List<RexLiteral> literal = isPKPredicate((RexCall) operand);
                        if (literal == null) {
                            return null;
                        }
                        assert literal.size() == 1 : "literal.size() == 1";
                        ans.add(literal.get(0));
                    }
                }
                return ans;
            } else if (rexCall.op == SqlStdOperatorTable.SEARCH) {
                List<RexLiteral> ans = new ArrayList<>();
                RexInputRef ref = (RexInputRef) rexCall.operands.get(0); // check pk
                if (ref.getIndex() == pkIndex) {
                    Sarg sarg = (Sarg)((RexLiteral) rexCall.operands.get(1)).getValue();
                    sarg.rangeSet.asRanges().forEach(range -> {
//                        String x = range.toString().split();
                        String intStr = range.toString().split("\\.\\.")[0].replace("[", "");
                        ans.add(intToRexLiteral(Integer.parseInt(intStr)));
                    });
                }
                return ans;
            }
            return null;
        }

        private RexLiteral intToRexLiteral(int i) {
            RexBuilder builder = new RexBuilder(new JavaTypeFactoryImpl());
            return builder.makeExactLiteral(BigDecimal.valueOf(i));
//            return RexLiteral.fromJdbcString(typeFactory., SqlTypeName.DECIMAL, String.valueOf(i));
//            return null;
        }

//        private List<RexLiteral> convertSargToRexLiteral(Sarg sarg) {
//            List<RexLiteral> ans = new ArrayList<>();
//            for (int i = 0; i < 100; i++) {
//                if (sarg.rangeSet.contains(i)) {
//                    ans.add(RexLiteral.intValue())
//                }
//            }
//        }
    }
}

class FutureWithResult {
    Object future;
    Object[] futureResult;

    public FutureWithResult(Object future, Object[] futureResult) {
        this.future = future;
        this.futureResult = futureResult;
    }
}

class MyList<T> extends ArrayList<T> {
    public MyList(Collection<? extends T> c) {
        super(c);
    }

    private Calcite calcite;
    public Function<Object[], FutureWithResult> insertRow;

    public MyList(Calcite calcite) {
        this.calcite = calcite;
    }

    @Override
    public boolean add(T t) {

        FutureWithResult futureWithResult = insertRow.apply((Object[]) t);
        calcite.futures.add(futureWithResult);

        // tell outside that they can get these futures and call awaitAll
        try {
            System.out.println("put futureReadyCond");
            calcite.futureReadyCond.put(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            // wait for awaitAll callback to be called, so the asking is actually executed
            calcite.awaitForFuturesCond.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // future is complete, we can return
        if (calcite.debug) System.out.println("add " + t);
        // but we intercept and add the result
        return super.add(t); // comment this will cause the "row affected" to be 0
//        return true;
    }
}
