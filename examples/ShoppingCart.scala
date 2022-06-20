import pods.workflows.*

/** Shopping Cart Example
  * 
  * This application implements a shopping cart using microservices. The 
  * shopping cart constist of one workflow (the `Cart`) for handling incoming 
  * requests: AddToCart(item); RemoveFromCart(Item); Checkout(). The `Inventory`
  * workflow manages the inventory, and replies to the requests: AddToInventory;
  * RemoveFromInventory. The last workflow manages the `Order`s and order 
  * history of the users.
 */
@main def ShoppingCart() = 
  val builder = Workflows.builder()


  // Shared definitions
  object Shared:
    case class User(id: String)
    case class Cart(items: Map[Item, Int])


    // Inventory
  object Inventory:
    import Shared.*
    import UserShoppingCart.* 

    case class Item(name: String, price: Double)

    sealed trait Command
    case class AddItem(item: Item) extends Command
    case class RemoveItem(item: Item) extends Command

    sealed trait Reply
    case object RemoveItemSuccess extends Reply
    case object RemoveItemFailure extends Reply

    // define the inventory behavior
    def inventory = Tasks.processor[Command] {
      // add item to the inventory
      case AddItem(item) =>
        ctx.log.debug("Adding item {} to inventory", item)
        ctx.state.get(item) match
          case Some(count) => ctx.state.put(item, count + 1)
          case None => ctx.state.put(item, 1)

      // remove an item from inventory and reply
      case RemoveItem(item, replyTo) =>
        ctx.log.debug("Removing item {} from inventory", item)
        ctx.state.get(item) match
          case Some(count) if count > 0 => 
            ctx.log.debug("Item {} is in inventory, removing it", item)
            ctx.state.put(item, count - 1)
            ctx.emit(RemoveItemSuccess)
          case _ => 
            ctx.log.debug("Item {} is not in inventory or out of stock, not removing it", item)
            ctx.emit(RemoveItemFailure)
    }

    def workflow: Workflow = 
      val builder = Workflows.builder()

      val _ = builder
        .sourceFromReplyStream[Command]("source") // create a source from a replyStream
        .task(inventory)
        .reply() // reply to the source
    
  end Inventory // object


  // Shopping cart
  object UserShoppingCart:
    import Shared.*
    import Inventory.*

    sealed trait Command(user: User)
    case class AddItem(user: User, item: Item) extends Command(user)
    case class ClearCart(user: User) extends Command(user)
    case class Checkout(user: User) extends Command(user)

    sealed trait Emits
    case class CheckoutSuccess(user: User, cart: Cart) extends Emits

    def cart = Tasks.processor[Command] {
      // try to add item to the user's cart
      case AddItem(user, item) => 
        ctx.log.debug("Adding item {} to cart of user {}", item, user)
        // We will ask the inventory to remove the item from the inventory.
        // alternative syntax, ask creates a ReplyStream event, : ctx.ask(RemoveItem(item))
        ctx.emit(RemoveItem(item))

      // FIXME: we need to somehow pertain the information about the user here for the reply
      // handle AddItemSuccess reply from the inventory
      case AddItemSuccess =>
        ctx.log.debug("Item {} added to cart of user {}", item, user)
        ctx.state.get(user) match
        case Some(usercart) => usercart.get(item) match
          case Some(count) => ctx.state.put(user, usercart + (item -> (count + 1)))
          case None => ctx.state.put(user, usercart + (item -> 1))
          
      // handle AddItemFailure reply from the inventory
      case AddItemFailure =>
        ctx.log.debug("Item {} not added to cart of user {}", item, user)

      // clear the cart, remove all items from user cart and add back to inventory
      case ClearCart(user) => 
        ctx.log.debug("Clearing cart of user {}", user)
        ctx.state.get(user) match
          case Some(usercart) =>
            usercart.foreach { case (item, count) =>
              ctx.log.debug("Adding {} items of item {} back to inventory", count, item)
              ctx.emit(inventory, AddItem(item)) // will complete because exactly once, we don't need to await
            }
            ctx.state.remove(user)
          case None => () // do nothing
        ctx.log.debug("Cart of user {} cleared", user)
      
      // checkout, emit checkout event and clear cart
      case Checkout(user) => ctx.state.get(user) match
          case Some(cart) =>
            ctx.log.debug("Checking out cart of user {}", user)
            ctx.state.remove(user)
            ctx.emit(CheckoutSuccess(user, cart))
          case None =>
            ctx.log.debug("Checking out cart of user {}", user)
            ctx.emit(CheckoutSuccess(user, map.empty))
    }

    def workflow: Workflow = 
      val builder = Workflows.builder().withName("UserShoppingCart")

      val source = builder
        .source[Command]("source")

      val replyPortal = builder
        .portal[Reply]("reply")

      val task = builder
        .merge(source, replyPortal)
        .task(cart) // task factory from TaskBehavior

      // split into requests and checkouts
      // FIXME: add split operator ;) to make more succint

      val requests = builder
        .from(task)
        .filter{
          case Inventory.Command => true
          case _ => false
        }

      val checkouts = builder
        .from(task)
        .filter{
          case CheckoutSuccess => true
          case _ => false
        }
      
      // create ReplyStream from requests
      val _ = builder
        .from(requests)
        .sinkReplyStream(portal)

      // create sink for checkout events
      val _ = builder
        .from(checkouts)
        .sink("checkout")
      
      // return built workflow
      builder.build() 

  end UserShoppingCart // object

  // orders
  object Orders:
    import Shared.*
    import Inventory.*
    import UserShoppingCart.*

    def orders = Tasks.processor[CheckoutSuccess] {
      case CheckoutSuccess(user, cart) =>
        ctx.log.debug("Registering successfull checkout for user and cart {} : {}", user, cart)
        ctx.state.get(user.id) match
          case Some(checkouts) =>
            ctx.state.put(user.id, checkouts + cart)
          case None =>
            ctx.state.put(user.id, Set(cart))
    }

    def workflow: Workflow = 
      val builder = Workflows
        .builder()
        .withName("Orders")

      val source = builder
        .source[CheckoutSuccess]("source")
        .task(orders)
        // no sink ;) 

  end Orders // object

  // shopping cart app
  object ShoppingCartApp:
    import Shared.*
    import UserShoppingCart.*
    import Inventory.*
    import Orders.*

    def launchAll(system: System): Unit =
      // launch the workflows
      val _ = system.launch(UserShoppingCart.workflow)
      val _ = system.launch(Inventory.workflow)
      val _ = system.launch(Orders.workflow)

      // connect the workflows
      val cartSource = system.registry.irefs("cart/source").resolve()
      val cartCheckouts = system.registry.orefs("cart/checkout").resolve()
      val cartRequests = system.registry.orefs("cart/request").resolve()
      val inventorySource = system.registry.irefs("inventory/source").resolve()
      val ordersSource = system.registry.irefs("orders/source").resolve()
      cartCheckouts.subscribe(ordersSource)      
      cartRequests.subscribe(inventorySource)

  end ShoppingCartApp // object


  // run app
  val system = Systems.local()
  ShoppingCartApp.launchAll(system)

end ShoppingCart // main