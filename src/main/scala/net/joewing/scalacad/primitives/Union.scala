package net.joewing.scalacad.primitives

import net.joewing.scalacad._

final case class Union[D <: Dim](a: Primitive[D], b: Primitive[D]) extends Primitive[D] {
  lazy val render: Surface = {
    val left = a.render.tree
    val right = b.render.tree
    val leftClipped = left.clip(right)
    val rightClipped = right.clip(leftClipped).inverted.clip(leftClipped).inverted
    BSPTreeSurface(leftClipped.merge(rightClipped))
  }
}

