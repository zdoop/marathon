package mesosphere.marathon
package api.akkahttp

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher.{ Matched, Unmatched }
import mesosphere.UnitTest
import mesosphere.marathon.state.PathId

class DirectivesTest extends UnitTest {
  import Directives._
  import PathId.StringPathId

  "AppPathId matcher" should {

    "properly parse and yield a path-id" in {
      // Note: when used, the preceeding slash SHOULD be consumed.
      AppPathId(Path("app-id")).shouldBe(
        Matched(Path.Empty, Tuple1("/app-id".toRootPath)))

      AppPathId(Path("path/to/thing")).shouldBe(
        Matched(Path.Empty, Tuple1("/path/to/thing".toRootPath)))

      AppPathId(Path("path/to////thing")).shouldBe(
        Matched(Path.Empty, Tuple1("/path/to/thing".toRootPath)))
    }

    "ignores the trailing slash when matching to the end" in {
      AppPathId(Path("path/to/thing/")).shouldBe(
        Matched(Path("/"), Tuple1("/path/to/thing".toRootPath)))
    }

    "properly ignore /tasks, /restart, /versions and /versions/XXX" in {
      AppPathId(Path("app-id/restart")).shouldBe(
        Matched(Path("/restart"), Tuple1("/app-id".toRootPath)))

      AppPathId(Path("app-id/tasks")).shouldBe(
        Matched(Path("/tasks"), Tuple1("/app-id".toRootPath)))

      AppPathId(Path("app-id/versions")).shouldBe(
        Matched(Path("/versions"), Tuple1("/app-id".toRootPath)))

      AppPathId(Path("app-id/versions/2017-05-01T00:00:00Z")).shouldBe(
        Matched(Path("/versions/2017-05-01T00:00:00Z"), Tuple1("/app-id".toRootPath)))
    }

    "consider unmatched if pathId is empty" in {
      AppPathId(Path("/")).shouldBe(
        Unmatched)
      AppPathId(Path("/")).shouldBe(
        Unmatched)
    }
  }
}
