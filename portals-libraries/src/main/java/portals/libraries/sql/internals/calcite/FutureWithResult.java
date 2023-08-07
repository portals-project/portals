package portals.libraries.sql.internals.calcite;

public class FutureWithResult {
    // portals.util.Future object
    public Object future;
    // Pointer to the result returned by subqueries, which is inside the future field
    public Object[] futureResult;

    public FutureWithResult(Object future, Object[] futureResult) {
        this.future = future;
        this.futureResult = futureResult;
    }
}
