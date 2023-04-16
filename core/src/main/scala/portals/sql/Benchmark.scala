package portals.sql

import cask.*
import portals.api.dsl.DSL.PortalsApp
import portals.api.dsl.*
import portals.api.dsl.DSL.*

object Server extends MainRoutes {

  @get("/")
  def hello() = {
    "hello"
  }

  @post("/query")
  def query(request: Request) = {
    val queryText = request.text()
    println("body: " + request.text())

  }

  // TODO: init query wf and data wf
  initialize()

  sealed class SQLQueryEvent
  case class SelectOp(tableName: String, key: Int) extends SQLQueryEvent
  case class InsertOp(tableName: String, data: List[Any], key: Int) extends SQLQueryEvent
  case class SQLQueryResult(msg: String, result: Array[Object])

  def initApp() = {
    val app = PortalsApp("app") {
      val portal = Portal[SQLQueryEvent, SQLQueryResult](
        "bookPortal",
        qEvent =>
          qEvent match
            case SelectOp(tableName, key) =>
              key
            case InsertOp(tableName, data, key) =>
              key
            case _ => 0
      )
      
      
    }
  }
}
