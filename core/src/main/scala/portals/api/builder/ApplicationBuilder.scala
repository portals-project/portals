package portals

/** Application Builder. */
trait ApplicationBuilder:
  def build(): Application

  def registry: RegistryBuilder

  def workflows[T, U](name: String = null): WorkflowBuilder[T, U]

  def splitters: SplitterBuilder

  def splitters(name: String = null): SplitterBuilder

  def generators: GeneratorBuilder

  def generators(name: String = null): GeneratorBuilder

  def sequencers: SequencerBuilder

  def sequencers(name: String = null): SequencerBuilder

  def connections: ConnectionBuilder

  def connections(name: String = null): ConnectionBuilder

  def portals: PortalBuilder

  def portals(name: String = null): PortalBuilder
end ApplicationBuilder // trait
