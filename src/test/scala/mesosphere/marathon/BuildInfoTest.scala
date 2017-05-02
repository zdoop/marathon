package mesosphere.marathon

import mesosphere.marathon.UnstableTest

import mesosphere.UnitTest

@UnstableTest
class BuildInfoTest extends UnitTest {

  "BuildInfo" should {
    "return a default versions" in {
      BuildInfo.scalaVersion should be("2.x.x")
      BuildInfo.version should be("1.5.0-SNAPSHOT")
    }

    "fail" in {
      false should be(true)
    }
  }
}
