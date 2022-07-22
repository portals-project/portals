package portals

// TODO: implement pretty-printer for AST
object ASTPrinter:
  def println(ast: AST): Unit = pprint.pprintln(ast)

  def toString(ast: AST): String = pprint.apply(ast).plainText
