package net.joewing.scalacad

import net.joewing.scalacad.primitives.{Dim, LinearExtrude, ThreeDimensional, TwoDimensional}

import scala.concurrent.{ExecutionContext, Future}

sealed trait RenderedObject extends Product with Serializable {
  implicit val dim: Dim

  val minBound: Vertex
  val maxBound: Vertex

  def facets: IndexedSeq[Facet]
  def treeFuture(implicit ec: ExecutionContext): Future[BSPTree]

  def invert: RenderedObject

  def merge(other: RenderedObject)(implicit ec: ExecutionContext): Future[RenderedObject]

  private def overlaps(right: RenderedObject): Boolean = {
    val (mina, maxa) = (minBound, maxBound)
    val (minb, maxb) = (right.minBound, right.maxBound)
    !(maxa.x < minb.x || maxa.y < minb.y || maxa.z < minb.z || mina.x > maxb.x || mina.y > maxb.y || mina.z > maxb.z)
  }

  final def union(other: RenderedObject)(implicit ec: ExecutionContext): Future[RenderedObject] = {
    if (overlaps(other)) {
      val leftFuture = treeFuture
      val rightFuture = other.treeFuture
      for {
        left <- leftFuture
        right <- rightFuture
        leftClipped <- left.clip(right)
        rightClipped <- right.clip(leftClipped)
        invertClipped <- rightClipped.inverted.clip(leftClipped)
        merged <- leftClipped.merge(invertClipped.inverted)
      } yield BSPTreeRenderedObject(merged)
    } else merge(other)
  }

  final def intersect(other: RenderedObject)(implicit ec: ExecutionContext): Future[RenderedObject] = {
    invert.union(other.invert).map(_.invert)
  }

  final def minus(other: RenderedObject)(implicit ec: ExecutionContext): Future[RenderedObject] = {
    invert.union(other).map(_.invert)
  }

  final def map(f: Facet => Facet): FacetRenderedObject = RenderedObject.fromFacets(facets.map(f))

  final def filter(f: Facet => Boolean): FacetRenderedObject = RenderedObject.fromFacets(facets.filter(f))

  final def filterNot(f: Facet => Boolean): FacetRenderedObject = RenderedObject.fromFacets(facets.filterNot(f))
}

final case class FacetRenderedObject(dim: Dim, facets: IndexedSeq[Facet]) extends RenderedObject {

  lazy val minBound: Vertex = facets.foldLeft(Vertex.max) { (v, f) => f.minBound.min(v) }
  lazy val maxBound: Vertex = facets.foldLeft(Vertex.min) { (v, f) => f.maxBound.max(v) }

  def treeFuture(implicit ec: ExecutionContext): Future[BSPTree] = {
    require(dim == Dim.three, s"cannot convert 2d object to BSPTree")
    BSPTree(facets.map(f => Polygon3d(f.vertices)))
  }

  def invert: RenderedObject = FacetRenderedObject(dim, facets.map(_.flip))

  def merge(other: RenderedObject)(implicit ec: ExecutionContext): Future[RenderedObject] = {
    other match {
      case BSPTreeRenderedObject(otherTree)    =>
        for {
          tree <- treeFuture
          merged <- tree.merge(otherTree)
        } yield BSPTreeRenderedObject(merged)
      case FacetRenderedObject(_, otherFacets) => Future {
        FacetRenderedObject(dim, facets ++ otherFacets)
      }
    }
  }
}

final case class BSPTreeRenderedObject(tree: BSPTree) extends RenderedObject {
  val dim: Dim = Dim.three

  lazy val vertices: Seq[Vertex] = tree.allPolygons.flatMap(_.vertices)
  lazy val minBound: Vertex = vertices.foldLeft(Vertex.max) { (v, p) => p.min(v) }
  lazy val maxBound: Vertex = vertices.foldLeft(Vertex.min) { (v, p) => p.max(v) }

  def facets: IndexedSeq[Facet] = {
    val polygons = tree.allPolygons
    val vertices = polygons.par.flatMap(_.vertices).seq.distinct
    val octree = Octree(vertices)
    polygons.par.flatMap { p =>
      Facet.fromVertices(p.vertices).flatMap(f => RenderedObject.insertPoints(f, octree))
    }.seq.toIndexedSeq
  }

  def treeFuture(implicit ec: ExecutionContext): Future[BSPTree] = Future.successful(tree)

  def invert: RenderedObject = BSPTreeRenderedObject(tree.inverted)

  def merge(other: RenderedObject)(implicit ec: ExecutionContext): Future[RenderedObject] = for {
    otherTree <- other.treeFuture
    merged <- tree.merge(otherTree)
  } yield BSPTreeRenderedObject(merged)
}

object RenderedObject {

  def fromFacets(facets: IndexedSeq[Facet])(implicit dim: Dim): FacetRenderedObject = {
    FacetRenderedObject(dim, facets)
  }

  def fromVertices(vertices: Seq[Vertex])(implicit dim: Dim): FacetRenderedObject = {
    fromFacets(Facet.fromVertices(vertices))
  }

  // Insert a point to the facet by splitting it.
  // Note that this assumes all inserted points are on an edge of the facet.
  def insertPoint(facet: Facet, p: Vertex): Seq[Facet] = {
    if (p.between(facet.v1, facet.v2)) {
      Vector(Facet(facet.v1, p, facet.v3), Facet(p, facet.v2, facet.v3))
    } else if (p.between(facet.v2, facet.v3)) {
      Vector(Facet(facet.v1, facet.v2, p), Facet(p, facet.v3, facet.v1))
    } else if (p.between(facet.v3, facet.v1)) {
      Vector(Facet(facet.v1, facet.v2, p), Facet(facet.v2, facet.v3, p))
    } else {
      Vector(facet)
    }
  }

  // Insert points from the Octree into the facet by splitting it.
  def insertPoints(facet: Facet, octree: Octree): Seq[Facet] = {
    val d = Vertex(Vertex.epsilon, Vertex.epsilon, Vertex.epsilon)
    octree.contained(facet.minBound - d, facet.maxBound + d).foldLeft(Vector(facet)) { (oldFacets, point) =>
      oldFacets.flatMap { f => insertPoint(f, point).filter(validFacet) }
    }
  }

  // Check if a facet is valid (has non-zero area).
  def validFacet(f: Facet): Boolean = {
    !f.v1.approxEqual(f.v2) && !f.v1.approxEqual(f.v3) && !f.v2.approxEqual(f.v3)
  }
}
