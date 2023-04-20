package portals.sql

object Book extends DBSerializable[Book]:
  def fromObjectArray(arr: List[Any]): Book =
    Book(arr(0).asInstanceOf[Integer], arr(1).asInstanceOf[String], arr(2).asInstanceOf[Integer], arr(3).asInstanceOf[Integer])

  def toObjectArray(book: Book): Array[Object] =
    Array[Object](book.id.asInstanceOf[Object], book.title.asInstanceOf[Object], book.year.asInstanceOf[Object], book.author.asInstanceOf[Object])

case class Book(id: Integer, title: String, year: Integer, author: Integer) {
  override def toString: String = s"Book($id, $title, $year, $author)"
}

object Author extends DBSerializable[Author]:
  def fromObjectArray(arr: List[Any]): Author =
    Author(arr(0).asInstanceOf[Integer], arr(1).asInstanceOf[String], arr(2).asInstanceOf[String])

  def toObjectArray(author: Author): Array[Object] =
    Array[Object](author.id.asInstanceOf[Object], author.fname.asInstanceOf[Object], author.lname.asInstanceOf[Object])

case class Author(id: Integer, fname: String, lname: String) {
    override def toString: String = s"Author($id, $fname, $lname)"
}
