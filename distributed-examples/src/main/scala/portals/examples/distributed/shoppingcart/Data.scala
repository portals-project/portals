package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

@experimental
object Data:
  import ShoppingCartEvents.*

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
