package portals.benchmark

/** the benchmark grid will span all combinations of the entries of the
  * parameter lists
  */
class BenchmarkGrid:
  // TODO: consider adding option to add a List of Lists of params, so that we can
  // have some form of nested grouping, allowing for grouping specific param combinations together.
  // e.g. see https://scikit-learn.org/stable/modules/generated/sklearn.model_selection.ParameterGrid.html

  private type ParamKeyValue = (String, String) // key value

  private var grid: List[List[ParamKeyValue]] = List.empty

  private def crossProduct(l: List[List[ParamKeyValue]]): Iterator[List[ParamKeyValue]] =
    l match
      case x :: xs => x.iterator.flatMap(i => crossProduct(xs).map(i :: _))
      case Nil => Iterator.single(List.empty[ParamKeyValue])

  def set(paramList: List[ParamKeyValue]): this.type =
    grid ::= paramList
    this

  def set(key: String, values: Any*): this.type =
    val paramList = values.map { x => (key, x.toString()) }.toList
    set(paramList)

  def setParam(key: String, value: Any): this.type =
    set(List((key, value.toString())))

  def iterator: Iterator[List[ParamKeyValue]] =
    crossProduct(grid.reverse)

  def configs: Iterator[BenchmarkConfig] =
    this.iterator.map { params => BenchmarkConfig().parseArgs(params.flatMap(List(_, _))) }
