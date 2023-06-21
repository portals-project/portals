package portals.runtime.interpreter.processors

import portals.runtime.BatchedEvents.*

enum StepperType:
  case Processing
  case Generating

private[portals] sealed trait Stepper:
  val tpe: StepperType

private[portals] trait ProcessingStepper extends Stepper:
  override val tpe = StepperType.Processing
  def step(atom: EventBatch): List[EventBatch]

private[portals] trait GeneratingStepper extends Stepper:
  override val tpe = StepperType.Generating
  def step(): List[EventBatch]
