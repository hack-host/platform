/*
 *  ____    ____    _____    ____    ___     ____ 
 * |  _ \  |  _ \  | ____|  / ___|  / _/    / ___|        Precog (R)
 * | |_) | | |_) | |  _|   | |     | |  /| | |  _         Advanced Analytics Engine for NoSQL Data
 * |  __/  |  _ <  | |___  | |___  |/ _| | | |_| |        Copyright (C) 2010 - 2013 SlamData, Inc.
 * |_|     |_| \_\ |_____|  \____|   /__/   \____|        All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the 
 * GNU Affero General Public License as published by the Free Software Foundation, either version 
 * 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See 
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this 
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.precog

import scalaz.Order
import scalaz.Monoid

import akka.dispatch.Future

import java.util.Comparator
import java.nio.ByteBuffer

import org.joda.time.Instant

import scala.collection.mutable
import scalaz.Bind

package object util {
  type RawBitSet = Array[Int]

  class Order2JComparator[A](order: Order[A]) {
    def toJavaComparator: Comparator[A] = new Comparator[A] {
      def compare(a1: A, a2: A) = {
        order.order(a1, a2).toInt
      }
    }
  }

  implicit def Order2JComparator[A](order: Order[A]): Order2JComparator[A] = new Order2JComparator(order)

  def using[A, B](a: A)(f: A => B)(implicit close: Close[A]): B = {
    val result = f(a)
    close.close(a)
    result
  }

  private val MAX_LONG = BigInt(Long.MaxValue)
  private val MIN_LONG = BigInt(Long.MinValue)
  
  @inline
  final def isValidLong(i: BigInt): Boolean = {
    MIN_LONG <= i && i <= MAX_LONG
  }

  final def flipBytes(buffer: ByteBuffer): Array[Byte] = {
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    buffer.flip()
    bytes
  }

  implicit def vectorMonoid[A]: Monoid[Vector[A]] = new Monoid[Vector[A]] {
    def zero: Vector[A] = Vector.empty[A]
    def append(v1: Vector[A], v2: => Vector[A]) = v1 ++ v2
  }

  implicit def bigDecimalMonoid: Monoid[BigDecimal] = new Monoid[BigDecimal] {
    def zero: BigDecimal = BigDecimal(0)
    def append(v1: BigDecimal, v2: => BigDecimal): BigDecimal = v1 + v2
  }

  final class LazyMap[A, B, C](source: Map[A, B], f: B => C) extends Map[A, C] {
    import scala.collection.JavaConverters._

    private val m: mutable.ConcurrentMap[A, C] = new java.util.concurrent.ConcurrentHashMap[A, C]().asScala

    def iterator: Iterator[(A, C)] = source.keysIterator map { a => (a, apply(a)) }

    def get(a: A): Option[C] = {
      m get a orElse (source get a map { b =>
        val c = f(b)
        m.putIfAbsent(a, c)
        c
      })
    }

    def + [C1 >: C](kv: (A, C1)): Map[A, C1] = iterator.toMap + kv
    def - (a: A): Map[A, C] = iterator.toMap - a
  }

  sealed trait LazyMapValues[A, B] {
    protected def source: Map[A, B]
    def lazyMapValues[C](f: B => C): Map[A, C] = new LazyMap[A, B, C](source, f)
  }

  implicit def lazyValueMapper[A, B](m: Map[A, B]) = new LazyMapValues[A, B] { val source = m }

  def arrayEq[@specialized A](a1: Array[A], a2: Array[A]): Boolean = {
    val len = a1.length
    if (len != a2.length) return false
    var i = 0
    while (i < len) {
      if (a1(i) != a2(i)) return false
      i += 1
    }
    true
  }

  def msTime[A](log: Long => Unit)(f : => A): A = {
    val start = System.currentTimeMillis
    val result = f
    log(System.currentTimeMillis - start)
    result
  }

  implicit val InstantOrdering: Ordering[Instant] = Ordering.Long.on[Instant](_.getMillis)

  implicit val FutureBind: Bind[Future] = new Bind[Future] {
    def map[A, B](fut: Future[A])(f: A => B) = fut.map(f)
    def bind[A, B](fut: Future[A])(f: A => Future[B]) = fut.flatMap(f)
  }
}

// vim: set ts=4 sw=4 et:
