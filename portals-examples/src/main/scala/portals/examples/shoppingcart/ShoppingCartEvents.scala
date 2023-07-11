package portals.examples.shoppingcart

import scala.annotation.experimental

object ShoppingCartEvents:
  ////////////////////////////////////////////////////////////////////////////
  // Types
  ////////////////////////////////////////////////////////////////////////////
  type User = Int
  type Item = Int
  type Count = Int

  ////////////////////////////////////////////////////////////////////////////
  // Cart
  ////////////////////////////////////////////////////////////////////////////
  sealed trait CartOps
  case class AddToCart(user: User, item: Item) extends CartOps
  case class RemoveFromCart(user: User, item: Item) extends CartOps
  case class Checkout(user: User) extends CartOps

  case class CartState(items: List[Item]):
    def add(item: Item): CartState = CartState(item :: items)
    def remove(item: Item): CartState = CartState(items.diff(List(item)))
  object CartState { def zero: CartState = CartState(List.empty) }

  ////////////////////////////////////////////////////////////////////////////
  // Inventory
  ////////////////////////////////////////////////////////////////////////////
  sealed trait InventoryReqs
  case class Get(item: Item) extends InventoryReqs
  case class Put(item: Item) extends InventoryReqs
  sealed trait InventoryReps
  case class GetReply(item: Item, success: Boolean) extends InventoryReps
  case class PutReply(item: Item, success: Boolean) extends InventoryReps

  ////////////////////////////////////////////////////////////////////////////
  // Orders
  ////////////////////////////////////////////////////////////////////////////
  sealed trait OrderOps
  case class Order(user: User, order: CartState) extends OrderOps

  ////////////////////////////////////////////////////////////////////////////
  // Analytics
  ////////////////////////////////////////////////////////////////////////////
  sealed trait AnalyticsReqs
  case object Top100 extends AnalyticsReqs
  sealed trait AnalyticsReps
  case class Top100Reply(items: List[Item]) extends AnalyticsReps

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

  def keyFrom(item: Item): Long =
    item.toLong

  def keyFrom(analyticsReq: AnalyticsReqs): Long =
    0L // all analytics requests go to the same key, can be optimized
