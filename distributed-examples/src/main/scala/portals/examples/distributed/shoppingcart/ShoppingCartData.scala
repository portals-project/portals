package portals.examples.distributed.shoppingcart

import scala.annotation.experimental
import scala.util.Random

@experimental
object ShoppingCartData:
  import ShoppingCartConfig.*
  import ShoppingCartEvents.*

  val rand = new scala.util.Random

  def cartOpsIter: Iterator[Iterator[CartOps]] =
    Iterator
      .fill(N_EVENTS)(math.abs(rand.nextInt() % 3) match
        case 0 => AddToCart(rand.nextInt(N_USERS), rand.nextInt(N_ITEMS))
        case 1 => RemoveFromCart(rand.nextInt(N_USERS), rand.nextInt(N_ITEMS))
        case 2 => Checkout(rand.nextInt(N_USERS))
      )
      .grouped(5)
      .map(_.iterator)

  def inveOpsIter: Iterator[Iterator[InventoryReqs]] =
    Iterator
      .fill(N_EVENTS)(Put(rand.nextInt(N_ITEMS)))
      .grouped(5)
      .map(_.iterator)
