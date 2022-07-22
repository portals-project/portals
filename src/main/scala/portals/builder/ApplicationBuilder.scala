package portals

/** Application Builder. */
trait ApplicationBuilder:
  def build(): Application

  def registry: RegistryBuilder

  def workflows[T, U](name: String): WorkflowBuilder[T, U]

  def generators: GeneratorBuilder

  def sequencers: SequencerBuilder

  def connections: ConnectionBuilder

  def portals: PortalBuilder
end ApplicationBuilder // trait
