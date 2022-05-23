/** Social Media Example
  *
  * The social media example shows how we can implement a blogging platform
  * where users can post a blog post and then like each others blog posts.
  *
  * To keep things simple there are only two types of events:
  *   - AddPost(user: User, title: String, text: String)
  *   - LikePost(user: User, post: Post)
  */
import pods.workflows.*

object SocialMedia:
  import pods.DSL.* // get nice methods

  /** Some User */
  case class User(id: Int, name: String)

  /** Events that are ingested by the social media */
  sealed trait Event
  case class AddPost(user: User, title: String, text: String) extends Event
  case class LikePost(user: User, id: Int) extends Event
  
  /** Updates to the social media */
  sealed trait Update extends Event
  case class RemoveUser(user: User) extends Update

  /** Behavior for adding posts to the system */
  def postsBehavior =
    Tasks.keyby[AddPost](_.user) {
      Tasks.processor[AddPost, Nothing] { 
        case AddPost(user, title, text) =>
          val id = scala.util.Random.nextInt()
          ctx.state.set(id, (title, text))
      }
    }

  /** Behavior for liking posts */
  def likesBehavior =
    Tasks.keyby[LikePost](_.user) {
      Tasks.processor[LikePost, Nothing] { 
        case LikePost(user, id) =>
          ctx.state.set(id, true) // we need some way to create a reverse index for this to be useful
      }
    }

  def updateBehaviorWrapper = ctx ?=> event => wrapped => event match
    case RemoveUser(user) =>
      ctx.state.remove(user.id)
    case _ =>
      wrapped(event)

  /** Workflow Factory */
  def apply() = Workflows.builder()
    .source[AddPost]()
    .task(postsBehavior)
    .source[LikePost]()
    .task(likesBehavior)
    .allWithWrapper(updateBehaviorWrapper)
    .build()
