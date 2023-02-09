package portals.examples.distributed.shoppingcart

import scala.annotation.experimental

import portals.*

@experimental
object ShoppingCartEvents:
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
    case AddToCart(user, _) => user.toLong
    case RemoveFromCart(user, _) => user.toLong
    case Checkout(user) => user.toLong

  def keyFrom(inventoryReq: InventoryReqs): Long = inventoryReq match
    case Get(item) => item.toLong
    case Put(item) => item.toLong

  def keyFrom(order: OrderOps): Long = order match
    case Order(user, _) => user.toLong
