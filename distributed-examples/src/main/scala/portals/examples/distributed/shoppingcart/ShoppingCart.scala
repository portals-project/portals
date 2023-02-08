package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

import portals.*

////////////////////////////////////////////////////////////////////////////////
// shopping cart app
////////////////////////////////////////////////////////////////////////////////
@experimental
object ShoppingCart:
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  // operations
  // cart
  sealed trait CartOps:
    def key: Long
  @experimental case class AddToCart(user: Int, item: Int) extends CartOps:
    override def key: Long = user
  @experimental case class RemoveFromCart(user: Int, item: Int) extends CartOps:
    override def key: Long = user
  @experimental case class Checkout(user: Int) extends CartOps:
    override def key: Long = user

  // cart state
  @experimental case class CartState(items: List[Int]):
    def add(item: Int): CartState = CartState(item :: items)
    def remove(item: Int): CartState = CartState(items.diff(List(item)))
  object CartState { def zero: CartState = CartState(List.empty) }

  // inventory
  sealed trait InventoryReqs:
    def key: Long
  @experimental case class Get(item: Int) extends InventoryReqs:
    def key: Long = item
  @experimental case class Put(item: Int) extends InventoryReqs:
    def key: Long = item
  sealed trait InventoryReps
  @experimental case class GetReply(item: Int, success: Boolean) extends InventoryReps
  @experimental case class PutReply(item: Int, success: Boolean) extends InventoryReps

  // orders
  sealed trait OrderOps
  @experimental case class Order(user: Int, order: CartState) extends OrderOps

  val cartopsList: List[List[CartOps]] =
    List(
      AddToCart(0, 1),
      AddToCart(1, 2),
      AddToCart(0, 3),
      AddToCart(1, 2),
      RemoveFromCart(0, 1),
      Checkout(0),
      Checkout(1),
    ).grouped(1).map(_.toList).toList

  val inventoryopsList: List[InventoryReqs] =
    List(
      Put(1),
      Put(2),
      Put(3),
      Put(1),
      Put(2),
      Put(3),
    )

  def app: Application = PortalsApp("shopping-cart") {
    val inventoryops = Generators.fromList(inventoryopsList)
    val cartops = Generators.fromListOfLists(cartopsList)

    val portal = Portal[InventoryReqs, InventoryReps]("inventory", { x => x.key })
    // val portal = Portal[InventoryReqs, InventoryReps]("inventory")

    val cart = Workflows[CartOps, OrderOps]("cart")
      .source(cartops.stream)
      .key(_.key)
      .init[OrderOps] {
        val state = PerKeyState[CartState]("state", CartState.zero)
        TaskBuilder
          .portal(portal)
          .asker[CartOps, OrderOps] { event =>
            event match
              case AddToCart(user, item) =>
                val resp = portal.ask(Get(item))
                resp.await {
                  resp.value.get match
                    case GetReply(item, success) =>
                      if success then // TODO: state set
                        state.set(state.get().add(item))
                        ctx.log.info(s"Item $item added to cart")
                      else ctx.log.info(s"Could not add $item to cart")
                    case _ =>
                      ctx.log.info("Unexpected response")
                      ???
                }
              case RemoveFromCart(user, item) =>
                portal.ask(Put(item))
                ctx.log.info(s"Removed $item from cart")
              case Checkout(user) =>
                val cart = state.get()
                ctx.emit(Order(user, cart))
                state.del()
                ctx.log.info(s"Checking out $user with cart $cart")
          }
          .asInstanceOf[GenericTask[CartOps, OrderOps, Nothing, Nothing]]

        // .asInstanceOf // TODO: make it so that we don't have to do this :((
      }
      .withName("cart")
      .sink()
      .freeze()

    val inventory = Workflows[InventoryReqs, Nothing]("inventory")
      .source(inventoryops.stream)
      // .logger()
      .key(_.key)
      .portal(portal)
      .replier[Nothing] {
        case Get(item) =>
          ctx.log.info(s"Taking $item from inventory")
          val state = PerKeyState[Int]("state", 0)
          if state.get() > 0 then state.set(state.get() - 1) else ???
        case Put(item) =>
          ctx.log.info(s"Putting $item in inventory")
          val state = PerKeyState[Int]("state", 0)
          state.set(state.get() + 1)
      } {
        case Get(item) =>
          ctx.log.info(s"Checking if $item is in inventory")
          val state = PerKeyState[Int]("state", 0)
          if state.get() > 0 then
            reply(GetReply(item, true))
            state.set(state.get() - 1)
          else reply(GetReply(item, false))
        case Put(item) =>
          ctx.log.info(s"Putting $item in inventory")
          val state = PerKeyState[Int]("state", 0)
          state.set(state.get() + 1)
          reply(PutReply(item, true))
      }
      .withName("inventory")
      .sink()
      .freeze()

    val orders = Workflows[OrderOps, Nothing]("orders")
      .source(cart.stream)
      // .logger()
      .map { x =>
        ctx.log.info(s"Ordering $x")
      }
      .withName("orders")
      .empty[Nothing]()
      .sink()
      .freeze()
  }
  end app // def

@experimental
object ShoppingCartMain extends App:
  val app = ShoppingCart.app

  // ASTPrinter.println(app)
  val system = Systems.test(2)

  val _ = system.launch(app)

  system.stepUntilComplete()

  system.shutdown()
