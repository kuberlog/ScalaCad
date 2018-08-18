package net.joewing.scalacad.primitives

import net.joewing.scalacad.RenderedObject

case class Scale[D <: Dim](obj: Primitive[D], x: Double, y: Double, z: Double) extends Primitive[D] {
  implicit val dim: D = obj.dim
  lazy val render: RenderedObject = obj.render.map(_.scaled(x, y, z))
}

