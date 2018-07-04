package net.joewing.scalacad.primitives

import net.joewing.scalacad.{Polygon, Vertex}

case class LinearExtrude(
  obj: Primitive[TwoDimensional],
  length: Double,
  rotation: Double = 0.0,
  slices: Int = 1
) extends Primitive[ThreeDimensional] {

  private def includeSide(base: Seq[Polygon])(side: (Vertex, Vertex)): Boolean = {
    val (a, b) = side
    val count = base.flatMap(_.edges).count { case (othera, otherb) =>
      (othera.approxEqual(a) && otherb.approxEqual(b)) || (othera.approxEqual(b) && otherb.approxEqual(a))
    }
    count == 1
  }

  private def segments(base: Seq[Polygon]): Seq[(Vertex, Vertex)] = {
    val vertices = base.flatMap(_.vertices)
    val pairs = vertices.zip(vertices.last +: vertices)
    pairs.filter(includeSide(base))
  }

  def render: Seq[Polygon] = {
    val base = obj.render

    def positionVertex(i: Int, v: Vertex): Vertex = {
      val angle = i * rotation
      Vertex(
        v.x1 * math.cos(angle) - v.x2 * math.sin(angle),
        v.x1 * math.sin(angle) + v.x2 * math.cos(angle),
        i * length / slices
      )
    }

    val perimeter = segments(base)
    val polygons = Vector.range(0, slices).foldLeft(Seq.empty[Polygon]) { (prev, i) =>
      prev ++ perimeter.flatMap { case (base1, base2) =>
        val b1 = positionVertex(i, base1)
        val b2 = positionVertex(i, base2)
        val t1 = positionVertex(i + 1, base1)
        val t2 = positionVertex(i + 1, base2)
        if (rotation == 0.0) {
          Seq(Polygon(Seq(b1, b2, t2, t1)))
        } else {
          Seq(
            Polygon(Seq(b1, b2, t1)),
            Polygon(Seq(b2, t2, t1))
          )
        }
      }
    }
    val top = base.map { polygon =>
      Polygon(polygon.vertices.map(v => positionVertex(slices, v)).reverse)
    }
    base ++ polygons ++ top
  }
}
