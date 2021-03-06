package net.joewing.scalacad.primitives

import org.scalatest.{FunSpec, Matchers}

class UnionSpec extends FunSpec with Matchers {
  describe("3d") {
    it("should render something") {
      val o1 = Cube(10, 10, 10)
      val o2 = Translate(Cube(1, 1, 1), 1, 1, 1)
      Union.union(o1, o2).rendered.facets should not be empty
    }
  }
}
