package portals.benchmark

class BenchmarkConfig:
  private var config = Map.empty[String, String]
  private var required = Set.empty[String]

  def args: List[String] = config.flatMap { case (k, v) => List(k.toString(), v.toString()) }.toList

  // check if required config parameters are set
  private def checkRequired(): Boolean =
    required.forall(config.contains)

  // internal recursive method
  private def _parseArgs(args: List[String]): Unit = args match
    case c :: value :: tail =>
      config += (c -> value)
      _parseArgs(tail)
    case Nil => ()
    case _ => ???

  def parseArgs(args: List[String]): this.type =
    _parseArgs(args)
    if !checkRequired() then
      val missing = required.diff(config.keys.toSet)
      throw new IllegalArgumentException("Missing required arguments for BenchmarkConfig: " + missing)
    this

  def set(key: String, value: Any): this.type =
    config += (key -> value.toString)
    this

  def setRequired(key: String): this.type =
    required += key
    this

  def get(key: String): String = config(key)

  def getInt(key: String): Int = config(key).toInt
