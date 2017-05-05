package mesosphere.marathon
package api.akkahttp
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher.Matching
import akka.http.scaladsl.server.PathMatcher1
import akka.http.scaladsl.server.{ Directive1, Directives => AkkaDirectives, Rejection }
import com.wix.accord.{ Success, Failure, Validator }
import mesosphere.marathon.state.PathId
import scala.annotation.tailrec

/**
  * All Marathon Directives and Akka Directives
  *
  * These should be imported by the respective controllers
  */
object Directives extends AuthDirectives with LeaderDirectives with AkkaDirectives {
  /**
    * Special path matcher for matching AppIds
    *
    * Due to an unfortunate API design, we are unable to have our AppId's end with "tasks", "restart", "versions", or
    * "versions" / _. Since Akka HTTP does not support non-greedy matcher we need a special matcher which gathers the
    * segments and stops before such a pattern is detected.
    *
    * See the tests for more explicit description of the behavior
    */
  object AppPathId extends PathMatcher1[PathId] {
    import akka.http.scaladsl.server.PathMatcher._

    @tailrec final def iter(reversePieces: List[String], remaining: Path): Matching[Tuple1[PathId]] = remaining match {
      case rest @ (SlashOrEmpty() |
        Path.Slash(
          Path.Segment("tasks" | "restart", SlashOrEmpty()) |
          Path.Segment("versions", SlashOrEmpty() | Path.Slash(Path.Segment(_, SlashOrEmpty()))))) =>
        if (reversePieces.isEmpty)
          Unmatched
        else
          Matched(rest, Tuple1(PathId.sanitized(reversePieces.reverse, true)))
      case Path.Slash(rest) =>
        iter(reversePieces, rest)
      case Path.Segment(segment, rest) =>
        iter(segment :: reversePieces, rest)
    }

    override def apply(path: Path) = iter(Nil, path)
  }

  private object SlashOrEmpty {
    val slashEmpty = Path.Slash(Path.Empty)
    def unapply(p: Path): Boolean =
      (p == Path.Empty || p == slashEmpty)
  }

  /**
    * Validate the given resource using the implicit validator in scope; reject if invalid
    *
    * Ideally, we validate while unmarshalling; however, in the case of app updates, we need to apply validation after
    * applying some update operation.
    */
  def validated[T](resource: T)(implicit validator: Validator[T]): Directive1[T] = {
    validator(resource) match {
      case Success => provide(resource)
      case failure: Failure =>
        reject(EntityMarshallers.ValidationFailed(failure))
    }
  }

  def rejectingLeft[T](result: Directive1[Either[Rejection, T]]): Directive1[T] =
    result.flatMap {
      case Left(rej) => reject(rej)
      case Right(t) => provide(t)
    }
}
