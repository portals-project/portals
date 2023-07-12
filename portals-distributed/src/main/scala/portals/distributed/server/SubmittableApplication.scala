package portals.distributed.server

import portals.application.Application

/** Submittable portals application. Extend this trait from an object in order
  * to submit an application to the server.
  *
  * @see
  *   [[portals.distributed.examples.HelloWorld]]
  */
trait SubmittableApplication:
  def apply(): Application
