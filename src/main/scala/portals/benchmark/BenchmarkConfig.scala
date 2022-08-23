package portals.benchmark

class BenchmarkConfig:
  private var config = Map.empty[String, String]

  def args: List[String] = config.flatMap { case (k, v) => List(k.toString(), v.toString()) }.toList

  def parseArgs(args: List[String]): Unit = args match
    case c :: value :: tail =>
      config += (c -> value)
      parseArgs(tail)
    case Nil => ()
    case _ => ???

  def set(key: String, value: Any): this.type =
    config += (key -> value.toString)
    this

  def get(key: String): String = config(key)

  def getInt(key: String): Int = config(key).toInt
