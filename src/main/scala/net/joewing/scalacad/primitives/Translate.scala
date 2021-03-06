package net.joewing.scalacad.primitives

import net.joewing.scalacad.{RenderedObject, Vertex}

import scala.concurrent.{ExecutionContext, Future}

final case class Translate[D <: Dim](
  obj: Primitive[D],
  x: Double = 0,
  y: Double = 0,
  z: Double = 0
) extends Primitive[D] {

  val dim: D = obj.dim

  protected def render(implicit ec: ExecutionContext): Future[RenderedObject] = {
    obj.renderedFuture.map(_.map(_.moved(x, y, z)))
  }

  override def transformed(f: Primitive[D] => Primitive[D]): Primitive[D] =
    obj.transformed(o => f(Translate(o, x, y, z)))

  override def extruded(
    f: Primitive[TwoDimensional] => Primitive[ThreeDimensional]
  ): Primitive[ThreeDimensional] = obj.extruded(o => f(Translate(o, x, y, z)))

  lazy val minBound: Vertex = obj.minBound.moved(x, y, z)
  lazy val maxBound: Vertex = obj.maxBound.moved(x, y, z)
}

