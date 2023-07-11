package portals.examples.shoppingcart

import scala.annotation.experimental
import scala.util.Random

import portals.application.generator.Generators

object ShoppingCartData:
  import ShoppingCartConfig.*
  import ShoppingCartEvents.*

  val rand = new scala.util.Random

  private def cartOpsIter: Iterator[Iterator[CartOps]] =
    Iterator
      // -- info: to make the iterator infinite, use `Iterator.continually` instead --
      .fill(N_EVENTS)(rand.nextInt(3) match
        case 0 => AddToCart(rand.nextInt(N_USERS), rand.nextInt(N_ITEMS))
        case 1 => RemoveFromCart(rand.nextInt(N_USERS), rand.nextInt(N_ITEMS))
        case 2 => Checkout(rand.nextInt(N_USERS))
      )
      .grouped(5)
      .map(_.iterator)

  def cartOpsGenerator =
    ThrottledGenerator(
      Generators.fromIteratorOfIterators(cartOpsIter),
      128,
    )

  private def inventoryOpsIter: Iterator[Iterator[InventoryReqs]] =
    Iterator
      .fill(N_EVENTS)(Put(rand.nextInt(N_ITEMS)))
      .grouped(5)
      .map(_.iterator)

  def inventoryOpsGenerator =
    ThrottledGenerator(
      Generators.fromIteratorOfIterators(inventoryOpsIter),
      128,
    )
