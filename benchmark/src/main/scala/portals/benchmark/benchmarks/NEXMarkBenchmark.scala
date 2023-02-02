package portals.benchmark.benchmarks

import portals.*
import portals.benchmark.*
import portals.benchmark.systems.*
import portals.benchmark.BenchmarkUtils.*
import portals.DSL.*

object NEXMarkBenchmarkUtil:
  import org.apache.beam.sdk.nexmark.*
  import org.apache.beam.sdk.nexmark.model.*
  import org.apache.beam.sdk.nexmark.sources.generator.*
  import org.apache.beam.sdk.values.*

  // use `portals.Generator` here instead of `Generator` to avoid name conflicts
  def NEXMarkGenerator(nAtomSize: Int, nEvents: Int): portals.Generator[TimestampedValue[Event]] =
    // TODO: consider setting the query in the config, and consider splitting the sources
    val nexmarkConfig = NexmarkConfiguration.DEFAULT
    val config = new GeneratorConfig(nexmarkConfig, 0, 0, nEvents, 0)
    portals.Generators.fromIteratorOfIterators(
      new Iterator[TimestampedValue[Event]] {
        val _generator = new Generator(config)
        def hasNext = _generator.hasNext()
        def next() = _generator.next()
      }.grouped(nAtomSize).map(_.iterator)
    )

  object Query1:
    import org.apache.beam.sdk.nexmark.queries.sql.SqlQuery1.*
    import org.apache.beam.sdk.nexmark.queries.Query1.*

    val name: String = "Query1"

    private val _doltoeur = DolToEur()
    private def doltoeur(dollar: Long): Long = _doltoeur(dollar)

    def apply(
        stream: AtomicStreamRef[TimestampedValue[Event]],
        builder: ApplicationBuilder,
        completer: CountingCompletionWatcher,
    ): Workflow[TimestampedValue[Event], TimestampedValue[Bid]] =
      builder
        .workflows[TimestampedValue[Event], TimestampedValue[Bid]]("query1")
        .source(stream)
        .filter { case x if x.getValue().bid != null => true; case _ => false }
        .map[TimestampedValue[Bid]] { x =>
          val bid = x.getValue().bid
          bid.price = doltoeur(bid.price)
          TimestampedValue.of(bid, x.getTimestamp())
        }
        .withOnAtomComplete { completer.complete() }
        .sink()
        .freeze()

  object Query2:
    import org.apache.beam.sdk.nexmark.queries.sql.SqlQuery2.*
    import org.apache.beam.sdk.nexmark.queries.Query2.*

    val name: String = "Query2"

    def apply(skipFactor: Int)(
        stream: AtomicStreamRef[TimestampedValue[Event]],
        builder: ApplicationBuilder,
        completer: CountingCompletionWatcher,
    ): Workflow[TimestampedValue[Event], TimestampedValue[Bid]] =
      builder
        .workflows[TimestampedValue[Event], TimestampedValue[Bid]]("query1")
        .source(stream)
        .filter { case x if x.getValue().bid != null => true; case _ => false }
        .filter { _.getValue().bid.auction % skipFactor == 0 }
        .withOnAtomComplete { completer.complete() }
        .map { _.asInstanceOf[TimestampedValue[Bid]] }
        .sink()
        .freeze()

  object Query3:
    import org.apache.beam.sdk.nexmark.queries.sql.SqlQuery3.*
    import org.apache.beam.sdk.nexmark.queries.Query3.*

    val name: String = "Query3"

    case class ResultType(pname: String, pcity: String, pstate: String, aid: Long)

    def apply(
        stream: AtomicStreamRef[TimestampedValue[Event]],
        builder: ApplicationBuilder,
        completer: CountingCompletionWatcher,
    ): Workflow[TimestampedValue[Event], ResultType] =
      val source = builder
        .workflows[TimestampedValue[Event], ResultType]("workflow")
        .source(stream)

      val persons = source
        .filter { case x if x.getValue().newPerson != null => true; case _ => false }
        .filter { x =>
          val person = x.getValue().newPerson
          person.state == "OR" || person.state == "ID" || person.state == "CA"
        }
        .key { x => x.getValue().newPerson.id.toInt }

      val auctions = source
        .filter { case x if x.getValue().newAuction != null => true; case _ => false }
        .filter { x =>
          val auction = x.getValue().newAuction
          auction.category == 10
        }
        .key { x => x.getValue().newAuction.seller.toInt }

      auctions
        .union(persons)
        .init[ResultType] {
          val auctions = PerKeyState[Set[Auction]]("auctions", Set.empty)
          val persons = PerKeyState[Set[Person]]("persons", Set.empty)

          TaskBuilder.processor {
            case x if x.getValue().newPerson != null =>
              val person = x.getValue().newPerson
              persons.set(persons.get() + person)
              auctions.get().foreach { auction =>
                if person.id == auction.seller then
                  ctx.emit(ResultType(person.name, person.city, person.state, auction.id))
              }

            case x if x.getValue().newAuction != null =>
              val auction = x.getValue().newAuction
              auctions.set(auctions.get() + auction)
              persons.get().foreach { person =>
                if person.id == auction.seller then
                  ctx.emit(ResultType(person.name, person.city, person.state, auction.id))
              }
          }
        }
        .withOnAtomComplete { completer.complete() }
        .sink()
        .freeze()

  object Query4:
    import org.apache.beam.sdk.nexmark.queries.Query4
    import org.apache.beam.sdk.transforms.windowing.BoundedWindow
    import org.joda.time.Instant

    val name: String = "Query4"

    case class ResultType(cid: Long, avgcaprice: Double)
    case class IntermediateType(cid: Long, price: Long)

    def apply(
        stream: AtomicStreamRef[TimestampedValue[Event]],
        builder: ApplicationBuilder,
        completer: CountingCompletionWatcher,
    ): Workflow[TimestampedValue[Event], ResultType] =
      builder
        .workflows[TimestampedValue[Event], ResultType]("query4")
        .source(stream)
        .filter {
          case x if x.getValue().newAuction != null => true
          case x if x.getValue().bid != null => true
          case _ => false
        }
        // winning bid per closed auction
        .key {
          case x if x.getValue().newAuction != null =>
            x.getValue().newAuction.id.toInt
          case x if x.getValue().bid != null =>
            x.getValue().bid.auction.toInt
          case _ => ???
        }
        .init[IntermediateType] {
          val auctionIdToAuction = PerTaskState[Map[Long, Auction]]("auctionIdToAuction", Map.empty)
          val auctionIdToBid = PerTaskState[Map[Long, Bid]]("auctionIdToBid", Map.empty)
          val highestTimeStamp = PerTaskState[Instant]("highestTimeStamp", BoundedWindow.TIMESTAMP_MIN_VALUE)

          TaskBuilder
            .processor[TimestampedValue[Event], IntermediateType] { x =>
              x match
                case x if x.getValue().newAuction != null =>
                  val _auction = x.getValue().newAuction
                  auctionIdToAuction.set(auctionIdToAuction.get() + (_auction.id -> _auction))
                case x if x.getValue().bid != null =>
                  val _bid = x.getValue().bid
                  auctionIdToBid.set(auctionIdToBid.get() + (_bid.auction -> _bid))

              if x.getTimestamp().isAfter(highestTimeStamp.get()) then highestTimeStamp.set(x.getTimestamp())
            }
            .withOnAtomComplete {
              val highestBidsAuctions = auctionIdToAuction.get().filter { x =>
                x._2.expires.isBefore(highestTimeStamp.get())
              }
              val highestBids = auctionIdToBid.get().filter { x => highestBidsAuctions.contains(x._1) }
              highestBids.foreach { bid =>
                ctx.emit(IntermediateType(highestBidsAuctions(bid._1).category, bid._2.price))
              }
              auctionIdToAuction.set(auctionIdToAuction.get() -- highestBidsAuctions.keys)
              auctionIdToBid.set(auctionIdToBid.get() -- highestBids.keys)
            }
        }
        // average winning bids over auction category
        .key { case IntermediateType(cid, price) =>
          cid.toInt
        }
        .init {
          val total = PerKeyState[Long]("total", 0)
          val num = PerKeyState[Long]("num", 0)

          TaskBuilder.processor { case IntermediateType(cid, price) =>
            val _total = total.get()
            val _num = num.get()
            val newTotal = _total + price
            val newNum = _num + 1
            total.set(newTotal)
            num.set(newNum)
            ctx.emit(ResultType(cid, newTotal.toDouble / newNum.toDouble))
          }
        }
        .withOnAtomComplete { completer.complete() }
        .sink()
        .freeze()

object NEXMarkBenchmark extends Benchmark:

  private val config = BenchmarkConfig()
    .setRequired("--nEvents") // 1024 * 1024
    .setRequired("--nAtomSize") // 1024
    .setRequired("--sQuery") // "Query1"
    .setRequired("--sSystem") // "async"

  override val name = "NEXMarkBenchmark"

  override def initialize(args: List[String]): Unit = config.parseArgs(args)

  override def cleanupOneIteration(): Unit = ()

  override def runOneIteration(): Unit =
    val nEvents = config.getInt("--nEvents")
    val nAtomSize = config.getInt("--nAtomSize")
    val sQuery = config.get("--sQuery")
    val sSystem = config.get("--sSystem")

    val query = sQuery match
      case "Query1" => NEXMarkBenchmarkUtil.Query1.apply
      case "Query2" => NEXMarkBenchmarkUtil.Query2(128).apply
      case "Query3" => NEXMarkBenchmarkUtil.Query3.apply
      case "Query4" => NEXMarkBenchmarkUtil.Query4.apply
      case _ => throw new IllegalArgumentException("Selected query does not exist: " + sQuery)

    val completer = CountingCompletionWatcher(nEvents / nAtomSize) // number of atoms :)

    val builder = ApplicationBuilders.application("runOneIteration")

    val generator = builder.generators.generator(NEXMarkBenchmarkUtil.NEXMarkGenerator(nAtomSize, nEvents))

    val queryWorkflow = query(generator.stream, builder, completer)

    val system = sSystem match
      case "async" => Systems.parallel()
      case "noGuarantees" => Systems.asyncLocalNoGuarantees()
      case "microBatching" => Systems.asyncLocalMicroBatching()
      case "sync" => Systems.test()
      case _ => ???

    val app = builder.build()

    system.launch(app)

    if sSystem == "sync" then system.asInstanceOf[TestSystem].stepUntilComplete()

    completer.waitForCompletion()

    system.shutdown()
