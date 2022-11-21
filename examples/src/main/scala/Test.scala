class A(val x: Int)

class B(using a: A):
  def x: Int = a.x

@main def testZ(): Unit =
  val a1 = A(1)
  val a2 = A(2)

  given A = a1
  // given A = a2
  val b1 = B()

  {
    println(b1.x)
    
    given A = a2
    
    println(b1.x)
  }