package net.joewing.scalacad.primitives

import net.joewing.scalacad.Polygon

case class Translate[D <: Dim](obj: Primitive[D], x: Double = 0, y: Double = 0, z: Double = 0) extends Primitive[D] {
  def render: Seq[Polygon] = obj.render.map(_.moved(x, y, z))
}

