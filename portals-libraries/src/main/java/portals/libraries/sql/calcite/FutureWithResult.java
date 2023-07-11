package portals.libraries.sql.calcite;

public class FutureWithResult {
    public Object future;
    public Object[] futureResult;

    public FutureWithResult(Object future, Object[] futureResult) {
        this.future = future;
        this.futureResult = futureResult;
    }
}
