package portals

import portals.*

private[portals] class TestSplitter(splitter: AtomicSplitter[_])(using rctx: TestRuntimeContext):
  def addOutput(path: String, filter: Any => Boolean): Unit = splitter.splitter.addOutput(path, filter)
  def removeOutput(path: String): Unit = splitter.splitter.removeOutput(path)
  def process(atom: TestAtom): List[TestAtom] =
    atom match
      case TestAtomBatch(p, l) =>
        val splatom = l
          .map(x =>
            x match
              case Event(key, t) => Splitter.Event(key, t)
              case Error(t) => Splitter.Error(t)
              case Atom => Splitter.Atom
              case Seal => Splitter.Seal
              case _ => ???
          )
          .asInstanceOf[List[Splitter.SplitterEvent[Any]]]
        splitter.asInstanceOf[AtomicSplitter[Any]].splitter.split(splatom) match
          case Nil => List.empty
          case list =>
            list.map { case (path, events) =>
              TestAtomBatch(
                path,
                events.map {
                  case Splitter.Event(key, t) => Event(key, t)
                  case Splitter.Error(t) => Error(t)
                  case Splitter.Atom => Atom
                  case Splitter.Seal => Seal
                }
              )
            }
      case _ => ??? // should not happen
