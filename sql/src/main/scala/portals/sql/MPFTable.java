package portals.sql;

import com.google.common.collect.ImmutableSet;
import org.apache.calcite.DataContext;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.logical.LogicalTableModify;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.*;
import org.apache.calcite.schema.ModifiableTable;
import org.apache.calcite.schema.ProjectableFilterableTable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Schemas;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.Sarg;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

public class MPFTable extends AbstractTable implements ModifiableTable, ProjectableFilterableTable, Calcite.DeletableTable {

    private String tableName;
    private MyList<Object[]> data;
    private final RelDataType rowType;
    private int pkIndex = 0;
    private List<RexLiteral> pkPredicates = new ArrayList<>();
    private Function<Object, FutureWithResult> getFutureByRowKeyFunc;
    private Calcite calcite;

    MPFTable(String tableName, RelDataType rowType, MyList<Object[]> data, int pkIndex, Calcite calcite) {
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

        collectPKPredicates(filters);

        if (pkPredicates.isEmpty()) {
            throw new RuntimeException("no pk filter");
        }

        for (RexLiteral pkPredicate : pkPredicates) {
            // ask, return future
            FutureWithResult futureWithResult = getFutureByRowKeyFunc.apply(pkPredicate.getValue());
            calcite.futures.add(futureWithResult);
            calcite.tableFutures.putIfAbsent(tableName, new ArrayList<>());
            calcite.tableFutures.get(tableName).add(futureWithResult);
        }

        // tell outside that they can get these futures and call awaitAll
        try {
//            System.out.println("put futureReadyCond " + tableName);
            calcite.futureReadyCond.put(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            // wait for awaitAll callback to be called, so the asking is actually executed
            int awaitForFutureResult = calcite.awaitForFuturesCond.take();
            if (awaitForFutureResult == -1) {
//                throw new RuntimeException("awaitForFutureResult == -1");
                throw new ExecutionAbortException("");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        System.out.println("return tableScan " + tableName);

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
//                            System.out.println("moveNext " + tableName + " " + row + " " + Arrays.toString(current));
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

    private void collectPKPredicates(List<RexNode> targetList) {
        this.pkPredicates.clear();

        targetList.forEach(filter -> {
            // is already a pk= / pk in
            List<RexLiteral> pkPredicates = tryCollectPKPredicate((RexCall) filter);
            if (pkPredicates != null) {
                this.pkPredicates.addAll(pkPredicates);
                return;
            }

            // else need to search all its children
            searchFilter((RexCall) filter);
        });
    }

    private Set<SqlOperator> leafOperators() {
        return ImmutableSet.of(SqlStdOperatorTable.EQUALS, SqlStdOperatorTable.AND, SqlStdOperatorTable.OR, SqlStdOperatorTable.SEARCH);
    }

    // pass in a rexCall, remove all its pk predicates, return the rest
    private void searchFilter(RexCall rexCall) {
        for (RexNode operand : rexCall.operands) {
            if (operand instanceof RexCall) {
                RexCall operandCall = (RexCall) operand;
                List<RexLiteral> pkPredicates = tryCollectPKPredicate(operandCall);
                if (pkPredicates != null) {
                    this.pkPredicates.addAll(pkPredicates);
                } else {
                    // not equal pk predicate, but may be tree leaf
                    if (!leafOperators().contains(operandCall.op)) {
                        searchFilter(operandCall);
                    }
                }
            }
        }
    }

    private List<RexLiteral> tryCollectPKPredicate(RexCall rexCall) {
        if (rexCall.op == SqlStdOperatorTable.EQUALS) {
            if (rexCall.operands.get(0) instanceof RexInputRef) {
                RexInputRef ref = (RexInputRef) rexCall.operands.get(0);
                if (ref.getIndex() == pkIndex) {
                    return Collections.singletonList((RexLiteral) rexCall.operands.get(1));
                }
            } else if (rexCall.operands.get(0) instanceof RexCall && ((RexCall) rexCall.operands.get(0)).op == SqlStdOperatorTable.CAST) {
                RexCall cast = (RexCall) rexCall.operands.get(0);
                if (cast.operands.get(0) instanceof RexInputRef) {
                    RexInputRef ref = (RexInputRef) cast.operands.get(0);
                    if (ref.getIndex() == pkIndex) {
                        return Collections.singletonList((RexLiteral) rexCall.operands.get(1));
                    }
                }
            }
        } else if (rexCall.op == SqlStdOperatorTable.OR) {
            List<RexLiteral> ans = new ArrayList<>();
            for (RexNode operand : rexCall.operands) {
                if (operand instanceof RexCall) {
                    List<RexLiteral> literal = tryCollectPKPredicate((RexCall) operand);
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
                Sarg sarg = (Sarg) ((RexLiteral) rexCall.operands.get(1)).getValue();
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
    }

}
