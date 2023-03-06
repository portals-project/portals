package portals.api.builder

import portals.*
import portals.application.*

trait RegistryBuilder:
  def sequencers: Registry[ExtAtomicSequencerRef]
  def splitters: Registry[ExtAtomicSplitterRef]
  def streams: Registry[ExtAtomicStreamRef]
  def portals: Registry2[ExtAtomicPortalRef]
end RegistryBuilder

object RegistryBuilder:
  def apply()(using ApplicationBuilderContext): RegistryBuilder = new RegistryBuilderImpl()

class RegistryBuilderImpl(using bctx: ApplicationBuilderContext) extends RegistryBuilder:
  override def sequencers: Registry[ExtAtomicSequencerRef] = new SequencerRegistryImpl()
  override def splitters: Registry[ExtAtomicSplitterRef] = new SplitterRegistryImpl()
  override def streams: Registry[ExtAtomicStreamRef] = new StreamsRegistryImpl()
  override def portals: Registry2[ExtAtomicPortalRef] = new PortalsRegistryImpl()

trait Registry[A[_]]:
  def get[T](path: String): A[T]
end Registry

trait Registry2[A[_, _]]:
  def get[T, U](path: String): A[T, U]
end Registry2

class SequencerRegistryImpl(using bctx: ApplicationBuilderContext) extends Registry[ExtAtomicSequencerRef]:
  override def get[T](path: String): ExtAtomicSequencerRef[T] =
    val ref = ExtAtomicSequencerRef[T](path)
    bctx.addToContext(ref)
    ref

class SplitterRegistryImpl(using bctx: ApplicationBuilderContext) extends Registry[ExtAtomicSplitterRef]:
  override def get[T](path: String): ExtAtomicSplitterRef[T] =
    val ref = ExtAtomicSplitterRef[T](path)
    bctx.addToContext(ref)
    ref

class StreamsRegistryImpl(using bctx: ApplicationBuilderContext) extends Registry[ExtAtomicStreamRef]:
  override def get[T](path: String): ExtAtomicStreamRef[T] =
    val ref = ExtAtomicStreamRef[T](path)
    bctx.addToContext(ref)
    ref

class PortalsRegistryImpl(using bctx: ApplicationBuilderContext) extends Registry2[ExtAtomicPortalRef]:
  override def get[T, R](path: String): ExtAtomicPortalRef[T, R] =
    val ref = ExtAtomicPortalRef[T, R](path)
    bctx.addToContext(ref)
    ref
