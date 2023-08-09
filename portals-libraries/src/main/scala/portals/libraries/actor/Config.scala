package portals.libraries.actor

import scala.annotation.implicitNotFound

class Config[T <: Tuple](hmap: Config.HMap[T]):
  import Config.HMap.Types.*

  // hack to get the Config type, used to set a type `ActorConfig` as a default
  // value based on this type: `ActorConfig = ActorConfig.default._type`
  type _type = Config[T]

  def set[K <: Singleton, V](key: K, value: V)(using
      ev: CanSet[T, K]
  ): Config[Set[T, K, V]] =
    Config(hmap.set(key, value)(using ev))

  def replace[K <: Singleton, V](key: K, value: V)(using
      ev: CanReplace[T, K, V]
  ): Config[Replace[T, K, V]] =
    Config(hmap.replace(key, value)(using ev))

  def get[K <: Singleton](key: K)(using
      ev: CanGet[T, K]
  ): Get[T, K] = hmap.get(key)

end Config

object Config:
  val empty = Config(HMap.empty)

  private[Config] class HMap[T <: Tuple](map: Map[Any, Any]):
    import HMap.Types.*

    def set[K <: Singleton, V](key: K, value: V)(using
        @implicitNotFound(CanSet.MSG) ev: CanSet[T, K]
    ): HMap[Set[T, K, V]] =
      new HMap(map + (key -> value))

    def get[K <: Singleton](key: K)(using
        @implicitNotFound(CanGet.MSG) ev: CanGet[T, K]
    ): Get[T, K] =
      map(key).asInstanceOf[Get[T, K]]

    def replace[K <: Singleton, V](key: K, value: V)(using
        @implicitNotFound(CanReplace.MSG) ev: CanReplace[T, K, V]
    ): HMap[Replace[T, K, V]] =
      new HMap(map + (key -> value))

  end HMap

  private[Config] object HMap:
    def empty: HMap[EmptyTuple] = new HMap(Map.empty)

    object Types:
      type Contains[T <: Tuple, K] = T match
        case (K, _) *: t => true
        case _ *: t => Contains[t, K]
        case _ => false

      type ContainsKV[T <: Tuple, K, V] = T match
        case (K, V) *: t => true
        case _ *: t => ContainsKV[t, K, V]
        case _ => false

      type CanGet[T <: Tuple, K] = true =:= Contains[T, K]
      object CanGet { inline val MSG = "Can't get key ${K} from HMap, no such key found" }

      type CanSet[T <: Tuple, K] = false =:= Contains[T, K]
      object CanSet { inline val MSG = "Can't set key ${K} in HMap, key already exists" }

      type CanReplace[T <: Tuple, K, V] = true =:= ContainsKV[T, K, V]
      object CanReplace { inline val MSG = "Can't replace key ${K} and value ${V} in HMap, this pair does not exist" }

      type Get[T <: Tuple, K] = T match
        case (K, v) *: t => v
        case _ *: t => Get[t, K]

      type Set[T <: Tuple, K, V] = (K, V) *: T

      type Replace[T <: Tuple, K, V] = T

    end Types
  end HMap
end Config
