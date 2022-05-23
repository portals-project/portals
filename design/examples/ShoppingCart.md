# Shopping Cart

```scala
/** Shopping cart example
 * 
 *  The shopping cart example consists of a workflow that has two tasks, one
 *  shopping cart task that tracks the users shopping carts, and one inventory
 *  task that tracks the stock.
*/

import serializably.workflows.DSL.ctx

/** first we define the inventory task behavior */
object Inventory:
  import UserShoppingCart.* 

  case class Item(name: String, price: Double)

  sealed trait Command
  case class AddItem(item: Item) extends Command
  case class RemoveItem(item: Item, replyTo: IRef[]) extends Command

  sealed trait Reply
  case object RemoveItemSuccess extends Reply
  case object RemoveItemFailure extends Reply

  val inventory = Tasks.processor {
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
          replyTo ! RemoveItemSuccess
        case _ => 
          ctx.log.debug("Item {} is not in inventory or out of stock, not removing it", item)
          replyTo ! RemoveItemFailure
  }

/** next, we define the shopping cart behavior */
object UserShoppingCart:
  import Inventory.*
  
  case class User(id: String)
  case class Cart(items: Map[Item, Int])

  sealed trait Command(user: User)
  case class AddItem(user: User, item: Item) extends Command(user)
  case class ClearCart(user: User) extends Command(user)
  case class Checkout(user: User) extends Command(user)

  sealed trait Emits
  case class CheckoutSuccess(user: User, cart: Cart) extends Emits

  val cartFactory = inventory => Tasks.processor {
    case AddItem(user, item) =>
      ctx.log.debug("Adding item {} to cart of user {}", item, user)
      val future = ctx.ask(inventory, ref => RemoveItem(item, ref))
      ctx.await(future) {
        // if item was removed successfully from inventory we can add it to cart
        case AddItemSuccess =>
          ctx.log.debug("Item {} added to cart of user {}", item, user)
          ctx.state.get(user) match
            case Some(usercart) => usercart.get(item) match
              case Some(count) => ctx.state.put(user, usercart + (item -> (count + 1)))
              case None => ctx.state.put(user, usercart + (item -> 1))
        
        // otherwise we have to abort
        case AddItemFailure =>
          ctx.log.debug("Item {} not added to cart of user {}", item, user)
      }

    // clear the cart, remove all items from user cart and add back to inventory
    case ClearCart(user) =>
      ctx.log.debug("Clearing cart of user {}", user)
      ctx.state.get(user) match
        case Some(usercart) =>
          usercart.foreach { case (item, count) =>
            ctx.log.debug("Adding {} items of item {} back to inventory", count, item)
            ctx.ask(inventory, AddItem(item)) // will complete because exactly once, we don't need to await
          }
          ctx.state.remove(user)
      ctx.log.debug("Cart of user {} cleared", user)
    
    // checkout, emit checkout event and clear cart
    case Checkout(user) =>
      ctx.set.get(user) match
        case Some(cart) =>
          ctx.log.debug("Checking out cart of user {}", user)
          ctx.set.remove(user)
          ctx.emit(CheckoutSuccess(user, cart))
        case None =>
          ctx.log.debug("Checking out cart of user {}", user)
          ctx.emit(CheckoutSuccess(user, map.empty))
  }

val builder = Workflows.builder()

val inventory = builder
  .source[Inventory.Command]()
  .task(Inventory.inventory)

val cart = builder
  .source[Cart.Command]()
  .task(Cart.cartFactory(inventory))

builder.build.execute()
```