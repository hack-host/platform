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
package com.quereo.quirrel
package typer

import org.specs2.mutable.Specification

object CriticalConditionFinderSpecs extends Specification
    with StubPhases
    with Compiler
    with CriticalConditionFinder {
  
  "critical condition finding" should {
    "detect critical conditions in a simple where" in {
      val tree @ Let(_, _, _, _, _) = compile("a('b) := 42 where 'b + 24 a")
      
      tree.criticalConditions must haveSize(1)
      tree.criticalConditions must haveKey("'b")
      
      tree.criticalConditions("'b") must haveSize(1)
      tree.criticalConditions("'b").head must beLike {
        case Add(_, TicVar(_, "'b"), NumLit(_, "24")) => ok
      }
    }
    
    "detect critical conditions in a nested where" in {
      val tree @ Let(_, _, _, _, _) = compile("a('b) := 42 where (12 where 'b + 24) a")
      
      tree.criticalConditions must haveSize(1)
      tree.criticalConditions must haveKey("'b")
      
      tree.criticalConditions("'b") must haveSize(1)
      tree.criticalConditions("'b").head must beLike {
        case Add(_, TicVar(_, "'b"), NumLit(_, "24")) => ok
      }
    }
    
    "merge critical conditions in a nested where" in {
      val tree @ Let(_, _, _, _, _) = compile("a('b) := 42 where (12 + 'b where 'b + 24) a")
      
      tree.criticalConditions must haveSize(1)
      tree.criticalConditions must haveKey("'b")
      
      val conditions = tree.criticalConditions("'b")
      conditions must haveSize(2)
      
      val sorted = conditions.toList sortWith { _.loc.toString < _.loc.toString }
      sorted(0) must beLike {
        case Add(_, TicVar(_, "'b"), NumLit(_, "24")) => ok
      }
      sorted(1) must beLike {
        case Operation(_, Add(_, NumLit(_, "12"), TicVar(_, "'b")), "where", Add(_, TicVar(_, "'b"), NumLit(_, "24"))) => ok
      }
    }
    
    "detect all critical conditions in a chain of wheres" in {
      val tree @ Let(_, _, _, _, _) = compile("a('b) := (42 where 12 + 'b) where 'b + 24 a")
      
      tree.criticalConditions must haveSize(1)
      tree.criticalConditions must haveKey("'b")
      
      val conditions = tree.criticalConditions("'b")
      conditions must haveSize(2)
      
      val sorted = conditions.toList sortWith { _.loc.toString < _.loc.toString }
      sorted(0) must beLike {
        case Add(_, TicVar(_, "'b"), NumLit(_, "24")) => ok
      }
      sorted(1) must beLike {
        case Add(_, NumLit(_, "12"), TicVar(_, "'b")) => ok
      }
    }
    
    "detect all critical conditions in a chain of lets" in {
      val input = """
          | histogram('a) :=
          |   foo' := 1 where 2 = 'a
          |   bar' := 3 where 4 = 'a
          |   foo' + bar'
          | 
          | histogram""".stripMargin
          
      val tree @ Let(_, _, _, _, _) = compile(input)
      
      tree.criticalConditions must haveSize(1)
      tree.criticalConditions must haveKey("'a")
      
      val conditions = tree.criticalConditions("'a")
      conditions must haveSize(2)
      
      val sorted = conditions.toList sortWith { _.loc.toString < _.loc.toString }
      sorted(0) must beLike {
        case Eq(_, NumLit(_, "2"), TicVar(_, "'a")) => ok
      }
      sorted(1) must beLike {
        case Eq(_, NumLit(_, "4"), TicVar(_, "'a")) => ok
      }
    }
    
    "detect critical condition hidden by dispatch" in {
      val tree @ Let(_, _, _, _, _) = compile("a('b) := h := 'b = 5 42 where h a")
      
      tree.criticalConditions must haveSize(1)
      tree.criticalConditions must haveKey("'b")
      
      val conditions = tree.criticalConditions("'b")
      conditions must haveSize(1)
      
      conditions.head must beLike {
        case Dispatch(_, "h", Vector()) => ok
      }
    }
    
    "remove extraneous conjunctions" in {
      val tree @ Let(_, _, _, _, _) = compile("a('b) := 42 where 'b = 2 & 4 = 5 a")
      
      tree.criticalConditions must haveSize(1)
      tree.criticalConditions must haveKey("'b")
      
      val conditions = tree.criticalConditions("'b")
      conditions must haveSize(1)
      
      conditions.head must beLike {
        case Eq(_, TicVar(_, "'b"), NumLit(_, "2")) => ok
      }
    }
    
    "split conjunctions" in {
      val tree @ Let(_, _, _, _, _) = compile("a('b) := 42 where 'b = 2 & 4 = 'b a")
      
      tree.criticalConditions must haveSize(1)
      tree.criticalConditions must haveKey("'b")
      
      val conditions = tree.criticalConditions("'b")
      conditions must haveSize(2)
      
      val sorted = conditions.toList sortWith { _.loc.toString < _.loc.toString }
      sorted(0) must beLike {
        case Eq(_, TicVar(_, "'b"), NumLit(_, "2")) => ok
      }
      sorted(1) must beLike {
        case Eq(_, NumLit(_, "4"), TicVar(_, "'b")) => ok
      }
    }
    
    "not split disjunctions" in {
      val tree @ Let(_, _, _, _, _) = compile("a('b) := 42 where 'b = 2 | 4 = 5 a")
      
      tree.criticalConditions must haveSize(1)
      tree.criticalConditions must haveKey("'b")
      
      val conditions = tree.criticalConditions("'b")
      conditions must haveSize(1)
      
      conditions.head must beLike {
        case Or(_, Eq(_, TicVar(_, "'b"), NumLit(_, "2")), Eq(_, NumLit(_, "4"), NumLit(_, "5"))) => ok
      }
    }
  }
}