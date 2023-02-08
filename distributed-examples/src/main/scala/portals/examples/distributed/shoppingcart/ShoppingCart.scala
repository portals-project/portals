package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

import portals.*

@experimental
object ShoppingCartTasks:
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  import ShoppingCart.Events.*

  def cart(
      portal: AtomicPortalRef[InventoryReqs, InventoryReps]
  ): GenericTask[CartOps, OrderOps, InventoryReqs, InventoryReps] =
    TaskBuilder
      .init[CartOps, OrderOps] {
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
                      if success then
                        state.set(state.get().add(item))
                        ctx.log.info(s"User $user added $item to cart")
                      else ctx.log.info(s"User $user tried to add $item to cart, but it was not in inventory")
                    case _ => ctx.log.info("Unexpected response"); ???
                }
              case RemoveFromCart(user, item) =>
                portal.ask(Put(item))
                ctx.log.info(s"User $user removed $item from cart")
              case Checkout(user) =>
                val cart = state.get()
                ctx.emit(Order(user, cart))
                state.del()
                ctx.log.info(s"Checking out $user with cart $cart")
          }
          .asInstanceOf[GenericTask[
            CartOps,
            OrderOps,
            Nothing,
            Nothing
          ]] // TODO: make it so that we don't have to do this :((
      }
      .asInstanceOf[GenericTask[
        CartOps,
        OrderOps,
        InventoryReqs,
        InventoryReps
      ]] // TODO: make it so that we don't have to do this :((

  def inventory(
      portal: AtomicPortalRef[InventoryReqs, InventoryReps]
  ): GenericTask[InventoryReqs, Nothing, InventoryReqs, InventoryReps] =
    TaskBuilder
      .portal(portal)
      .replier[InventoryReqs, Nothing] {
        case Get(item) =>
          val state = PerKeyState[Int]("state", 0)
          ctx.log.info(s"Taking $item from inventory")
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

  def orders(): GenericTask[OrderOps, Nothing, Nothing, Nothing] =
    TaskBuilder.flatMap[OrderOps, Nothing] { event =>
      ctx.log.info(s"Ordering $event")
      List.empty
    }

////////////////////////////////////////////////////////////////////////////////
// shopping cart app
////////////////////////////////////////////////////////////////////////////////
@experimental
object ShoppingCart:
  import portals.DSL.*
  import portals.DSL.BuilderDSL.*
  import portals.DSL.ExperimentalDSL.*

  //////////////////////////////////////////////////////////////////////////////
  // Events
  //////////////////////////////////////////////////////////////////////////////
  object Events:
    ////////////////////////////////////////////////////////////////////////////
    // Cart
    ////////////////////////////////////////////////////////////////////////////
    sealed trait CartOps
    @experimental case class AddToCart(user: Int, item: Int) extends CartOps
    @experimental case class RemoveFromCart(user: Int, item: Int) extends CartOps
    @experimental case class Checkout(user: Int) extends CartOps

    @experimental case class CartState(items: List[Int]):
      def add(item: Int): CartState = CartState(item :: items)
      def remove(item: Int): CartState = CartState(items.diff(List(item)))
    object CartState { def zero: CartState = CartState(List.empty) }

    ////////////////////////////////////////////////////////////////////////////
    // Inventory
    ////////////////////////////////////////////////////////////////////////////
    sealed trait InventoryReqs
    @experimental case class Get(item: Int) extends InventoryReqs
    @experimental case class Put(item: Int) extends InventoryReqs
    sealed trait InventoryReps
    @experimental case class GetReply(item: Int, success: Boolean) extends InventoryReps
    @experimental case class PutReply(item: Int, success: Boolean) extends InventoryReps

    ////////////////////////////////////////////////////////////////////////////
    // Orders
    ////////////////////////////////////////////////////////////////////////////
    sealed trait OrderOps
    @experimental case class Order(user: Int, order: CartState) extends OrderOps

    ////////////////////////////////////////////////////////////////////////////
    // Key
    ////////////////////////////////////////////////////////////////////////////
    def keyFrom(cartOp: CartOps): Long = cartOp match
      case AddToCart(user, _) => user
      case RemoveFromCart(user, _) => user
      case Checkout(user) => user

    def keyFrom(inventoryReq: InventoryReqs): Long = inventoryReq match
      case Get(item) => item
      case Put(item) => item

  import Events.*

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

    val portal = Portal[InventoryReqs, InventoryReps]("inventory", { x => keyFrom(x) })

    val cart = Workflows[CartOps, OrderOps]("cart")
      .source(cartops.stream)
      .key(keyFrom(_))
      .task(ShoppingCartTasks.cart(portal))
      .withName("cart")
      .sink()
      .freeze()

    val inventory = Workflows[InventoryReqs, Nothing]("inventory")
      .source(inventoryops.stream)
      .key(keyFrom(_))
      .task(ShoppingCartTasks.inventory(portal))
      .withName("inventory")
      .sink()
      .freeze()

    val orders = Workflows[OrderOps, Nothing]("orders")
      .source(cart.stream)
      .task(ShoppingCartTasks.orders())
      .withName("orders")
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
