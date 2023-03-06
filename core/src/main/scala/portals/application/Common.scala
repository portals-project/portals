package portals.application

object Common:
  object Types:
    type Path = String
    type Filter[T] = T => Boolean
