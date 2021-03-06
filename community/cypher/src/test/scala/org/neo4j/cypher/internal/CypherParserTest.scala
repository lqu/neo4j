/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal

import org.junit.Assert._
import org.neo4j.graphdb.Direction
import org.scalatest.junit.JUnitSuite
import org.junit.Test
import org.junit.Ignore
import org.scalatest.Assertions
import org.hamcrest.CoreMatchers.equalTo
import org.neo4j.cypher._
import org.neo4j.cypher.CypherVersion._
import org.neo4j.cypher.internal.parser.{ParsedVarLengthRelation, ParsedEntity, ParsedRelation}
import org.neo4j.cypher.internal.commands._
import org.neo4j.cypher.internal.commands.expressions._
import org.neo4j.cypher.internal.commands.values.{UnresolvedLabel, TokenType, KeyToken}
import org.neo4j.cypher.internal.commands.values.TokenType.PropertyKey
import org.neo4j.cypher.internal.helpers.LabelSupport
import org.neo4j.cypher.internal.mutation._

class CypherParserTest extends JUnitSuite with Assertions {
  @Test def shouldParseEasiestPossibleQuery() {
    test("start s = NODE(1) return s",
      Query.
        start(NodeById("s", 1)).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def should_return_string_literal() {
    test("start s = node(1) return \"apa\"",
      Query.
        start(NodeById("s", 1)).
        returns(ReturnItem(Literal("apa"), "\"apa\"")))
  }

  @Test def should_return_string_literal_with_escaped_sequence_in() {
    test("start s = node(1) return \"a\\tp\\\"a\"",
      Query.
        start(NodeById("s", 1)).
        returns(ReturnItem(Literal("a\tp\"a"), "\"a\\tp\\\"a\"")))
  }

  @Test def allTheNodes() {
    test("start s = NODE(*) return s",
      Query.
        start(AllNodes("s")).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def allTheRels() {
    test("start r = relationship(*) return r",
      Query.
        start(AllRelationships("r")).
        returns(ReturnItem(Identifier("r"), "r")))
  }

  @Test def shouldHandleAliasingOfColumnNames() {
    test("start s = NODE(1) return s as somethingElse",
      Query.
        start(NodeById("s", 1)).
        returns(ReturnItem(Identifier("s"), "somethingElse", true)))
  }

  @Test def sourceIsAnIndex() {
    test(
      """start a = node:index(key = "value") return a""",
      Query.
        start(NodeByIndex("a", "index", Literal("key"), Literal("value"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def sourceIsAnNonParsedIndexQuery() {
    test(
      """start a = node:index("key:value") return a""",
      Query.
        start(NodeByIndexQuery("a", "index", Literal("key:value"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldParseEasiestPossibleRelationshipQuery() {
    test(
      "start s = relationship(1) return s",
      Query.
        start(RelationshipById("s", 1)).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def shouldParseEasiestPossibleRelationshipQueryShort() {
    test(
      "start s = rel(1) return s",
      Query.
        start(RelationshipById("s", 1)).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def sourceIsARelationshipIndex() {
    test(
      """start a = rel:index(key = "value") return a""",
      Query.
        start(RelationshipByIndex("a", "index", Literal("key"), Literal("value"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def keywordsShouldBeCaseInsensitive() {
    test(
      "START s = NODE(1) RETURN s",
      Query.
        start(NodeById("s", 1)).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def shouldParseMultipleNodes() {
    test(
      "start s = NODE(1,2,3) return s",
      Query.
        start(NodeById("s", 1, 2, 3)).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def shouldParseMultipleInputs() {
    test(
      "start a = node(1), b = NODE(2) return a,b",
      Query.
        start(NodeById("a", 1), NodeById("b", 2)).
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b")))
  }

  @Test def shouldFilterOnProp() {
    test(
      "start a = NODE(1) where a.name = \"andres\" return a",
      Query.
        start(NodeById("a", 1)).
        where(Equals(Property(Identifier("a"), PropertyKey("name")), Literal("andres"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldReturnLiterals() {
    test(
      "start a = NODE(1) return 12",
      Query.
        start(NodeById("a", 1)).
        returns(ReturnItem(Literal(12), "12")))
  }

  @Test def shouldReturnAdditions() {
    test(
      "start a = NODE(1) return 12+2",
      Query.
        start(NodeById("a", 1)).
        returns(ReturnItem(Add(Literal(12), Literal(2)), "12+2")))
  }

  @Test def arithmeticsPrecedence() {
    test(
      "start a = NODE(1) return 12/4*3-2*4",
      Query.
        start(NodeById("a", 1)).
        returns(ReturnItem(
        Subtract(
          Multiply(
            Divide(
              Literal(12),
              Literal(4)),
            Literal(3)),
          Multiply(
            Literal(2),
            Literal(4)))
        , "12/4*3-2*4")))
  }

  @Test def shouldFilterOnPropWithDecimals() {
    test(
      "start a = node(1) where a.extractReturnItems = 3.1415 return a",
      Query.
        start(NodeById("a", 1)).
        where(Equals(Property(Identifier("a"), PropertyKey("extractReturnItems")), Literal(3.1415))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldHandleNot() {
    test(
      "start a = node(1) where not(a.name = \"andres\") return a",
      Query.
        start(NodeById("a", 1)).
        where(Not(Equals(Property(Identifier("a"), PropertyKey("name")), Literal("andres")))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldHandleNotEqualTo() {
    test(
      "start a = node(1) where a.name <> \"andres\" return a",
      Query.
        start(NodeById("a", 1)).
        where(Not(Equals(Property(Identifier("a"), PropertyKey("name")), Literal("andres")))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldHandleLessThan() {
    test(
      "start a = node(1) where a.name < \"andres\" return a",
      Query.
        start(NodeById("a", 1)).
        where(LessThan(Property(Identifier("a"), PropertyKey("name")), Literal("andres"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldHandleGreaterThan() {
    test(
      "start a = node(1) where a.name > \"andres\" return a",
      Query.
        start(NodeById("a", 1)).
        where(GreaterThan(Property(Identifier("a"), PropertyKey("name")), Literal("andres"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldHandleLessThanOrEqual() {
    test(
      "start a = node(1) where a.name <= \"andres\" return a",
      Query.
        start(NodeById("a", 1)).
        where(LessThanOrEqual(Property(Identifier("a"), PropertyKey("name")), Literal("andres"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }


  @Test def shouldHandleRegularComparison() {
    test(
      "start a = node(1) where \"Andres\" =~ 'And.*' return a",
      Query.
        start(NodeById("a", 1)).
        where(LiteralRegularExpression(Literal("Andres"), Literal("And.*"))).
        returns(ReturnItem(Identifier("a"), "a"))
    )
  }


  @Test def shouldHandleMultipleRegularComparison() {
    test(
      """start a = node(1) where a.name =~ 'And.*' AnD a.name =~ 'And.*' return a""",
      Query.
        start(NodeById("a", 1)).
        where(And(LiteralRegularExpression(Property(Identifier("a"), PropertyKey("name")), Literal("And.*")), LiteralRegularExpression(Property(Identifier("a"), PropertyKey("name")), Literal("And.*")))).
        returns(ReturnItem(Identifier("a"), "a"))
    )
  }

  @Test def shouldHandleEscapedRegexs() {
    test(
      """start a = node(1) where a.name =~ 'And\\/.*' return a""",
      Query.
        start(NodeById("a", 1)).
        where(LiteralRegularExpression(Property(Identifier("a"), PropertyKey("name")), Literal("And\\/.*"))).
        returns(ReturnItem(Identifier("a"), "a"))
    )
  }

  @Test def shouldHandleGreaterThanOrEqual() {
    test(
      "start a = node(1) where a.name >= \"andres\" return a",
      Query.
        start(NodeById("a", 1)).
        where(GreaterThanOrEqual(Property(Identifier("a"), PropertyKey("name")), Literal("andres"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def booleanLiteralsOld() {
    test(vPre2_0,
      "start a = node(1) where true = false return a",
      Query.
        start(NodeById("a", 1)).
        where(Equals(Literal(true), Literal(false))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def booleanLiterals() {
    test(vFrom2_0,
      "start a = node(1) where true = false return a",
      Query.
        start(NodeById("a", 1)).
        where(Equals(True(), Not(True()))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldFilterOnNumericProp() {
    test(
      "start a = NODE(1) where 35 = a.age return a",
      Query.
        start(NodeById("a", 1)).
        where(Equals(Literal(35), Property(Identifier("a"), PropertyKey("age")))).
        returns(ReturnItem(Identifier("a"), "a")))
  }


  @Test def shouldHandleNegativeLiteralsAsExpected() {
    test(
      "start a = NODE(1) where -35 = a.age AND a.age > -1.2 return a",
      Query.
        start(NodeById("a", 1)).
        where(And(
        Equals(Literal(-35), Property(Identifier("a"), PropertyKey("age"))),
        GreaterThan(Property(Identifier("a"), PropertyKey("age")), Literal(-1.2)))
      ).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldCreateNotEqualsQuery() {
    test(
      "start a = NODE(1) where 35 <> a.age return a",
      Query.
        start(NodeById("a", 1)).
        where(Not(Equals(Literal(35), Property(Identifier("a"), PropertyKey("age"))))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def multipleFilters() {
    test(
      "start a = NODE(1) where a.name = \"andres\" or a.name = \"mattias\" return a",
      Query.
        start(NodeById("a", 1)).
        where(Or(
        Equals(Property(Identifier("a"), PropertyKey("name")), Literal("andres")),
        Equals(Property(Identifier("a"), PropertyKey("name")), Literal("mattias")))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldCreateXorQuery() {
    test(vFrom2_0,
      "start a = NODE(1) where a.name = 'andres' xor a.name = 'mattias' return a",
      Query.
        start(NodeById("a", 1)).
        where(Xor(
        Equals(Property(Identifier("a"), PropertyKey("name")), Literal("andres")),
        Equals(Property(Identifier("a"), PropertyKey("name")), Literal("mattias")))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def relatedTo() {
    val string = "start a = NODE(1) match a -[:KNOWS]-> (b) return a, b"
    def query(DIFFERENCE: String): Query = {
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", DIFFERENCE, Seq("KNOWS"), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"))
    }

    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED26" -> vFrom2_0
    )
  }

  @Test def relatedToWithoutRelType() {
    val string = "start a = NODE(1) match a --> (b) return a, b"
    def query(relName: String): Query = {
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", relName, Seq(), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"))
    }

    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED26" -> vFrom2_0)
  }

  @Test def relatedToWithoutRelTypeButWithRelVariable() {
    test(
      "start a = NODE(1) match a-[r]->b return r",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "r", Seq(), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("r"), "r")))
  }

  @Test def relatedToTheOtherWay() {
    val string = "start a = NODE(1) match a <-[:KNOWS]- (b) return a, b"
    def query(DIFFERENCE: String): Query = {
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("b", "a", DIFFERENCE, Seq("KNOWS"), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"))
    }

    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED26" -> vFrom2_0)
  }

  @Test def twoDoubleOptionalWithFourHalfs() {
    test(v2_0,
      "START a=node(1), b=node(2) MATCH a-[r1?]->X<-[r2?]-b, a<-[r3?]-Z-[r4?]->b return r1,r2,r3,r4 order by id(r1),id(r2),id(r3),id(r4)",
      Query.
        start(NodeById("a", 1), NodeById("b", 2)).
        matches(
            RelatedTo(SingleNode("a"), SingleOptionalNode("X"), "r1", Seq(), Direction.OUTGOING, true),
            RelatedTo(SingleNode("b"), SingleOptionalNode("X"), "r2", Seq(), Direction.OUTGOING, true),
            RelatedTo(SingleOptionalNode("Z"), SingleNode("a"), "r3", Seq(), Direction.OUTGOING, true),
            RelatedTo(SingleOptionalNode("Z"), SingleNode("b"), "r4", Seq(), Direction.OUTGOING, true)
        ).orderBy(
            SortItem(IdFunction(Identifier("r1")), true),
            SortItem(IdFunction(Identifier("r2")), true),
            SortItem(IdFunction(Identifier("r3")), true),
            SortItem(IdFunction(Identifier("r4")), true)
        ).returns(
            ReturnItem(Identifier("r1"), "r1"),
            ReturnItem(Identifier("r2"), "r2"),
            ReturnItem(Identifier("r3"), "r3"),
            ReturnItem(Identifier("r4"), "r4")
        )
    )
  }

  @Test def twoDoubleOptionalWithFourHalfs1_9() {
    test(v1_9,
      "START a=node(1), b=node(2) MATCH a-[r1?]->X<-[r2?]-b, a<-[r3?]-Z-[r4?]->b return r1,r2,r3,r4 order by id(r1),id(r2),id(r3),id(r4)",
      Query.
        start(NodeById("a", 1), NodeById("b", 2)).
        matches(
            RelatedTo.optional("a", "X", "r1", Seq(), Direction.OUTGOING),
            RelatedTo.optional("b", "X", "r2", Seq(), Direction.OUTGOING),
            RelatedTo.optional("Z", "a", "r3", Seq(), Direction.OUTGOING),
            RelatedTo.optional("Z", "b", "r4", Seq(), Direction.OUTGOING)
        ).orderBy(
            SortItem(IdFunction(Identifier("r1")), true),
            SortItem(IdFunction(Identifier("r2")), true),
            SortItem(IdFunction(Identifier("r3")), true),
            SortItem(IdFunction(Identifier("r4")), true)
        ).returns(
            ReturnItem(Identifier("r1"), "r1"),
            ReturnItem(Identifier("r2"), "r2"),
            ReturnItem(Identifier("r3"), "r3"),
            ReturnItem(Identifier("r4"), "r4")
        )
    )
  }

  @Test def shouldOutputVariables() {
    test(
      "start a = NODE(1) return a.name",
      Query.
        start(NodeById("a", 1)).
        returns(ReturnItem(Property(Identifier("a"), PropertyKey("name")), "a.name")))
  }

  @Test def shouldReadPropertiesOnExpressions() {
    test(vFrom2_0,
      "start a = NODE(1) return (a).name",
      Query.
        start(NodeById("a", 1)).
        returns(ReturnItem(Property(Identifier("a"), PropertyKey("name")), "(a).name")))
  }

  @Test def shouldHandleAndPredicates() {
    test(
      "start a = NODE(1) where a.name = \"andres\" and a.lastname = \"taylor\" return a.name",
      Query.
        start(NodeById("a", 1)).
        where(And(
        Equals(Property(Identifier("a"), PropertyKey("name")), Literal("andres")),
        Equals(Property(Identifier("a"), PropertyKey("lastname")), Literal("taylor")))).
        returns(ReturnItem(Property(Identifier("a"), PropertyKey("name")), "a.name")))
  }

  @Test def relatedToWithRelationOutput() {
    test(
      "start a = NODE(1) match a -[rel:KNOWS]-> (b) return rel",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "rel", Seq("KNOWS"), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("rel"), "rel")))
  }


  @Test def relatedToWithoutEndName() {
    val string = "start a = NODE(1) match a -[r:MARRIED]-> () return a"
    def query(DIFFERENCE: String): Query = {
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", DIFFERENCE, "r", Seq("MARRIED"), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("a"), "a"))
    }

    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED42" -> vFrom2_0)
  }

  @Test def relatedInTwoSteps() {
    val string = "start a = NODE(1) match a -[:KNOWS]-> b -[:FRIEND]-> (c) return c"
    def query(DIFFERENCE: (String, String)): Query = {
      Query.
        start(NodeById("a", 1)).
        matches(
        RelatedTo("a", "b", DIFFERENCE._1, Seq("KNOWS"), Direction.OUTGOING),
        RelatedTo("b", "c", DIFFERENCE._2, Seq("FRIEND"), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("c"), "c"))
    }

    testVariants(string, query,
      ("  UNNAMED5", "  UNNAMED6") -> vPre2_0,
      ("  UNNAMED26", "  UNNAMED40") -> vFrom2_0
    )
  }

  @Test def djangoRelationshipType() {
    test(
      "start a = NODE(1) match a -[r:`<<KNOWS>>`]-> b return b",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "r", Seq("<<KNOWS>>"), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def countTheNumberOfHitsOld() {
    test(vPre2_0,
      "start a = NODE(1) match a --> b return a, b, count(*)",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "  UNNAMED3", Seq(), Direction.OUTGOING)).
        aggregation(CountStar()).
        columns("a", "b", "count(*)").
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"), ReturnItem(CountStar(), "count(*)")))
  }

  @Test def countTheNumberOfHits() {
    test(vFrom2_0,
      "start a = NODE(1) match a --> b return a, b, count(*)",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "  UNNAMED26", Seq(), Direction.OUTGOING)).
        aggregation(CountStar()).
        columns("a", "b", "count(*)").
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"), ReturnItem(CountStar(), "count(*)")))
  }

  @Test def countStar() {
    test(
      "start a = NODE(1) return count(*) order by count(*)",
      Query.
        start(NodeById("a", 1)).
        aggregation(CountStar()).
        columns("count(*)").
        orderBy(SortItem(CountStar(), true)).
        returns(ReturnItem(CountStar(), "count(*)")))
  }

  @Test def distinct() {
    test(
      "start a = NODE(1) match a -[r]-> b return distinct a, b",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "r", Seq(), Direction.OUTGOING)).
        aggregation().
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b")))
  }

  @Test def sumTheAgesOfPeople() {
    test(
      "start a = NODE(1) match a -[r]-> b return a, b, sum(a.age)",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "r", Seq(), Direction.OUTGOING)).
        aggregation(Sum(Property(Identifier("a"), PropertyKey("age")))).
        columns("a", "b", "sum(a.age)").
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"), ReturnItem(Sum(Property(Identifier("a"), PropertyKey("age"))), "sum(a.age)")))
  }

  @Test def avgTheAgesOfPeopleOld() {
    test(vPre2_0,
      "start a = NODE(1) match a --> b return a, b, avg(a.age)",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "  UNNAMED3", Seq(), Direction.OUTGOING)).
        aggregation(Avg(Property(Identifier("a"), PropertyKey("age")))).
        columns("a", "b", "avg(a.age)").
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"), ReturnItem(Avg(Property(Identifier("a"), PropertyKey("age"))), "avg(a.age)")))
  }

  @Test def avgTheAgesOfPeople() {
    test(vFrom2_0,
      "start a = NODE(1) match a --> b return a, b, avg(a.age)",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "  UNNAMED26", Seq(), Direction.OUTGOING)).
        aggregation(Avg(Property(Identifier("a"), PropertyKey("age")))).
        columns("a", "b", "avg(a.age)").
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"), ReturnItem(Avg(Property(Identifier("a"), PropertyKey("age"))), "avg(a.age)")))
  }

  @Test def minTheAgesOfPeople() {
    val string = "start a = NODE(1) match (a) --> b return a, b, min(a.age)"

    def query(DIFFERENCE: String): Query =
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", DIFFERENCE, Seq(), Direction.OUTGOING)).
        aggregation(Min(Property(Identifier("a"), PropertyKey("age")))).
        columns("a", "b", "min(a.age)").
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"), ReturnItem(Min(Property(Identifier("a"), PropertyKey("age"))), "min(a.age)"))

    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED28"-> vFrom2_0
    )
  }

  @Test def maxTheAgesOfPeople() {
    val query = "start a = NODE(1) match a --> b return a, b, max(a.age)"

    test(vFrom2_0,
      query,
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "  UNNAMED26", Seq(), Direction.OUTGOING)).
        aggregation(Max((Property(Identifier("a"), PropertyKey("age"))))).
        columns("a", "b", "max(a.age)").
        returns(
        ReturnItem(Identifier("a"), "a"),
        ReturnItem(Identifier("b"), "b"),
        ReturnItem(Max((Property(Identifier("a"), PropertyKey("age")))), "max(a.age)")
      ))

    test(vPre2_0,
      query,
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "  UNNAMED3", Seq(), Direction.OUTGOING)).
        aggregation(Max((Property(Identifier("a"), PropertyKey("age"))))).
        columns("a", "b", "max(a.age)").
        returns(
        ReturnItem(Identifier("a"), "a"),
        ReturnItem(Identifier("b"), "b"),
        ReturnItem(Max((Property(Identifier("a"), PropertyKey("age")))), "max(a.age)")
      ))
  }

  @Test def singleColumnSorting() {
    test(
      "start a = NODE(1) return a order by a.name",
      Query.
        start(NodeById("a", 1)).
        orderBy(SortItem(Property(Identifier("a"), PropertyKey("name")), ascending = true)).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def sortOnAggregatedColumn() {
    test(
      "start a = NODE(1) return a order by avg(a.name)",
      Query.
        start(NodeById("a", 1)).
        orderBy(SortItem(Avg(Property(Identifier("a"), PropertyKey("name"))), ascending = true)).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def sortOnAliasedAggregatedColumn() {
    test(
      "start n = node(0) match (n)-[r:KNOWS]-(c) return n, count(c) as cnt order by cnt",
      Query.
        start(NodeById("n", 0)).
        matches(RelatedTo("c", "n", "r", Seq("KNOWS"), Direction.BOTH)).
        orderBy(SortItem(Count(Identifier("c")), true)).
        aggregation(Count(Identifier("c"))).
        returns(ReturnItem(Identifier("n"), "n"), ReturnItem(Count(Identifier("c")), "cnt", true)))
  }

  @Test def shouldHandleTwoSortColumns() {
    test(
      "start a = NODE(1) return a order by a.name, a.age",
      Query.
        start(NodeById("a", 1)).
        orderBy(
        SortItem(Property(Identifier("a"), PropertyKey("name")), ascending = true),
        SortItem(Property(Identifier("a"), PropertyKey("age")), ascending = true)).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldHandleTwoSortColumnsAscending() {
    test(
      "start a = NODE(1) return a order by a.name ASCENDING, a.age ASC",
      Query.
        start(NodeById("a", 1)).
        orderBy(
        SortItem(Property(Identifier("a"), PropertyKey("name")), ascending = true),
        SortItem(Property(Identifier("a"), PropertyKey("age")), ascending = true)).
        returns(ReturnItem(Identifier("a"), "a")))

  }

  @Test def orderByDescending() {
    test(
      "start a = NODE(1) return a order by a.name DESCENDING",
      Query.
        start(NodeById("a", 1)).
        orderBy(SortItem(Property(Identifier("a"), PropertyKey("name")), ascending = false)).
        returns(ReturnItem(Identifier("a"), "a")))

  }

  @Test def orderByDesc() {
    test(
      "start a = NODE(1) return a order by a.name desc",
      Query.
        start(NodeById("a", 1)).
        orderBy(SortItem(Property(Identifier("a"), PropertyKey("name")), ascending = false)).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def nullableProperty() {
    test(vPre2_0,
      "start a = NODE(1) return a.name?",
      Query.
        start(NodeById("a", 1)).
        returns(ReturnItem(Nullable(Property(Identifier("a"), PropertyKey("name"))), "a.name?")))
  }

  @Test def nestedBooleanOperatorsAndParentesis() {
    test(
      """start n = NODE(1,2,3) where (n.animal = "monkey" and n.food = "banana") or (n.animal = "cow" and n
      .food="grass") return n""",
      Query.
        start(NodeById("n", 1, 2, 3)).
        where(Or(
        And(
          Equals(Property(Identifier("n"), PropertyKey("animal")), Literal("monkey")),
          Equals(Property(Identifier("n"), PropertyKey("food")), Literal("banana"))),
        And(
          Equals(Property(Identifier("n"), PropertyKey("animal")), Literal("cow")),
          Equals(Property(Identifier("n"), PropertyKey("food")), Literal("grass"))))).
        returns(ReturnItem(Identifier("n"), "n")))
  }

  @Test def nestedBooleanOperatorsAndParentesisXor() {
    test(vFrom2_0,
      """start n = NODE(1,2,3) where (n.animal = "monkey" and n.food = "banana") xor (n.animal = "cow" and n
      .food="grass") return n""",
      Query.
        start(NodeById("n", 1, 2, 3)).
        where(Xor(
        And(
          Equals(Property(Identifier("n"), PropertyKey("animal")), Literal("monkey")),
          Equals(Property(Identifier("n"), PropertyKey("food")), Literal("banana"))),
        And(
          Equals(Property(Identifier("n"), PropertyKey("animal")), Literal("cow")),
          Equals(Property(Identifier("n"), PropertyKey("food")), Literal("grass"))))).
        returns(ReturnItem(Identifier("n"), "n")))
  }

  @Test def limit5() {
    test(
      "start n=NODE(1) return n limit 5",
      Query.
        start(NodeById("n", 1)).
        limit(5).
        returns(ReturnItem(Identifier("n"), "n")))
  }

  @Test def skip5() {
    test(
      "start n=NODE(1) return n skip 5",
      Query.
        start(NodeById("n", 1)).
        skip(5).
        returns(ReturnItem(Identifier("n"), "n")))
  }

  @Test def skip5limit5() {
    test(
      "start n=NODE(1) return n skip 5 limit 5",
      Query.
        start(NodeById("n", 1)).
        limit(5).
        skip(5).
        returns(ReturnItem(Identifier("n"), "n")))
  }

  @Test def relationshipType() {
    test(
      "start n=NODE(1) match n-[r]->(x) where type(r) = \"something\" return r",
      Query.
        start(NodeById("n", 1)).
        matches(RelatedTo("n", "x", "r", Seq(), Direction.OUTGOING)).
        where(Equals(RelationshipTypeFunction(Identifier("r")), Literal("something"))).
        returns(ReturnItem(Identifier("r"), "r")))
  }

  @Test def pathLength() {
    test(
      "start n=NODE(1) match p=(n-[r]->x) where LENGTH(p) = 10 return p",
      Query.
        start(NodeById("n", 1)).
        matches(RelatedTo("n", "x", "r", Seq(), Direction.OUTGOING)).
        namedPaths(NamedPath("p", ParsedRelation("r", "n", "x", Seq.empty, Direction.OUTGOING))).
        where(Equals(LengthFunction(Identifier("p")), Literal(10.0))).
        returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def relationshipTypeOut() {
    test(
      "start n=NODE(1) match n-[r]->(x) return TYPE(r)",

      Query.
        start(NodeById("n", 1)).
        matches(RelatedTo("n", "x", "r", Seq(), Direction.OUTGOING)).
        returns(ReturnItem(RelationshipTypeFunction(Identifier("r")), "TYPE(r)")))
  }


  @Test def shouldBeAbleToParseCoalesce() {
    test(
      "start n=NODE(1) match n-[r]->(x) return COALESCE(r.name,x.name)",
      Query.
        start(NodeById("n", 1)).
        matches(RelatedTo("n", "x", "r", Seq(), Direction.OUTGOING)).
        returns(ReturnItem(CoalesceFunction(Property(Identifier("r"), PropertyKey("name")), Property(Identifier("x"), PropertyKey("name"))), "COALESCE(r.name,x.name)")))
  }

  @Test def relationshipsFromPathOutput() {
    test(
      "start n=NODE(1) match p=n-[r]->x return RELATIONSHIPS(p)",

      Query.
        start(NodeById("n", 1)).
        matches(RelatedTo("n", "x", "r", Seq(), Direction.OUTGOING)).
        namedPaths(NamedPath("p", ParsedRelation("r", "n", "x", Seq.empty, Direction.OUTGOING))).
        returns(ReturnItem(RelationshipFunction(Identifier("p")), "RELATIONSHIPS(p)")))
  }

  @Test def makeDirectionOutgoing() {
    test("START a=node(1) match b<-[r]-a return b",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("a", "b", "r", Seq(), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def keepDirectionForNamedPaths() {
    test("START a=node(1) match p=b<-[r]-a return p",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo("b", "a", "r", Seq(), Direction.INCOMING)).
        namedPaths(NamedPath("p", ParsedRelation("r", "b", "a", Seq(), Direction.INCOMING))).
        returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def relationshipsFromPathInWhere() {
    test(
      "start n=NODE(1) match p=n-[r]->x where length(rels(p))=1 return p",

      Query.
        start(NodeById("n", 1)).
        matches(RelatedTo("n", "x", "r", Seq(), Direction.OUTGOING)).
        namedPaths(NamedPath("p", ParsedRelation("r", "n", "x", Seq.empty, Direction.OUTGOING))).
        where(Equals(LengthFunction(RelationshipFunction(Identifier("p"))), Literal(1))).
        returns (ReturnItem(Identifier("p"), "p")))
  }

  @Test def countNonNullValues() {
    test(
      "start a = NODE(1) return a, count(a)",
      Query.
        start(NodeById("a", 1)).
        aggregation(Count(Identifier("a"))).
        columns("a", "count(a)").
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Count(Identifier("a")), "count(a)")))
  }

  @Test def shouldHandleIdBothInReturnAndWhere() {
    test(
      "start a = NODE(1) where id(a) = 0 return ID(a)",
      Query.
        start(NodeById("a", 1)).
        where(Equals(IdFunction(Identifier("a")), Literal(0)))
        returns (ReturnItem(IdFunction(Identifier("a")), "ID(a)")))
  }

  @Test def shouldBeAbleToHandleStringLiteralsWithApostrophe() {
    test(
      "start a = node:index(key = 'value') return a",
      Query.
        start(NodeByIndex("a", "index", Literal("key"), Literal("value"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldHandleQuotationsInsideApostrophes() {
    test(
      "start a = node:index(key = 'val\"ue') return a",
      Query.
        start(NodeByIndex("a", "index", Literal("key"), Literal("val\"ue"))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def simplePathExample() {
    val string = "start a = node(0) match p = a-->b return a"
    def query(relName: String): Query =
      Query.
      start(NodeById("a", 0)).
      matches(RelatedTo("a", "b", relName, Seq(), Direction.OUTGOING)).
      namedPaths(NamedPath("p", ParsedRelation(relName, "a", "b", Seq.empty, Direction.OUTGOING))).
      returns(ReturnItem(Identifier("a"), "a"))

    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED29" -> vFrom2_0)
  }

  @Test def threeStepsPath() {
    test(
      "start a = node(0) match p = ( a-[r1]->b-[r2]->c ) return a",
      Query.
        start(NodeById("a", 0)).
        matches(
          RelatedTo("a", "b", "r1", Seq(), Direction.OUTGOING),
          RelatedTo("b", "c", "r2", Seq(), Direction.OUTGOING)).
        namedPaths(NamedPath("p",
          ParsedRelation("r1", "a", "b", Seq.empty, Direction.OUTGOING),
          ParsedRelation("r2", "b", "c", Seq.empty, Direction.OUTGOING))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def pathsShouldBePossibleWithoutParenthesis() {
    test(
      "start a = node(0) match p = a-[r]->b return a",
      Query.
        start(NodeById("a", 0)).
        matches(RelatedTo("a", "b", "r", Seq(), Direction.OUTGOING)).
        namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq.empty, Direction.OUTGOING))).
        returns (ReturnItem(Identifier("a"), "a")))
  }

  @Test def variableLengthPath() {
    val string = "start a=node(0) match a -[:knows*1..3]-> x return x"
    def query(pathName: String): Query = {
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo(pathName, "a", "x", Some(1), Some(3), "knows", Direction.OUTGOING)).
        returns(ReturnItem(Identifier("x"), "x"))
    }

    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED24" -> vFrom2_0)
  }

  @Test def variableLengthPathWithRelsIterable() {
    val string = "start a=node(0) match a -[r:knows*1..3]-> x return length(r)"
    def query(pathName: String): Query = {
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo(pathName, "a", "x", Some(1), Some(3), "knows", Direction.OUTGOING, Some("r"))).
        returns(ReturnItem(LengthFunction(Identifier("r")), "length(r)"))
    }

    testVariants(string, query,
    "  UNNAMED3" -> vPre2_0,
    "  UNNAMED24" -> vFrom2_0)
  }

  @Test def fixedVarLengthPath() {
    val string = "start a=node(0) match a -[*3]-> x return x"

    def queryWith(DIFFERENCE: String): Query = {
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo(DIFFERENCE, SingleNode("a"), SingleNode("x"), Some(3), Some(3), Seq(),
        Direction.OUTGOING, None, optional = false)).
        returns(ReturnItem(Identifier("x"), "x"))
    }

    test(vPre2_0, string, queryWith("  UNNAMED3"))

    test(vFrom2_0, string, queryWith("  UNNAMED24"))
  }

  @Test def variableLengthPathWithoutMinDepth() {
    val string = "start a=node(0) match a -[:knows*..3]-> x return x"
    def query(pathName: String): Query =
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo(pathName, "a", "x", None, Some(3), "knows", Direction.OUTGOING)).
        returns(ReturnItem(Identifier("x"), "x"))
    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED24" -> vFrom2_0)
  }

  @Test def variableLengthPathWithRelationshipIdentifier() {
    val string = "start a=node(0) match a -[r:knows*2..]-> x return x"
    def query(pathName: String): Query =
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo(pathName, "a", "x", Some(2), None, "knows", Direction.OUTGOING, Some("r"))).
        returns(ReturnItem(Identifier("x"), "x"))
    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED24" -> vFrom2_0)
  }

  @Test def variableLengthPathWithoutMaxDepth() {
    val string = "start a=node(0) match a -[:knows*2..]-> x return x"
    def query(pathName: String): Query =
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo(pathName, "a", "x", Some(2), None, "knows", Direction.OUTGOING)).
        returns(ReturnItem(Identifier("x"), "x"))

    testVariants(string, query,
      "  UNNAMED3" -> vPre2_0,
      "  UNNAMED24" -> vFrom2_0)
  }

  @Test def unboundVariableLengthPath_Old() {
    test(vPre2_0, "start a=node(0) match a -[:knows*]-> x return x",
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo("  UNNAMED3", "a", "x", None, None, "knows", Direction.OUTGOING)).
        returns(ReturnItem(Identifier("x"), "x"))
    )
  }

  @Test def unboundVariableLengthPath() {
    test(vFrom2_0, "start a=node(0) match a -[:knows*]-> x return x",
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo("  UNNAMED24", "a", "x", None, None, "knows", Direction.OUTGOING)).
        returns(ReturnItem(Identifier("x"), "x"))
    )
  }

  @Test def optionalRelationship1_9() {
    test(v1_9, "start a = node(1) match a -[?]-> (b) return b",
      Query.
      start(NodeById("a", 1)).
      matches(RelatedTo(SingleNode("a"), SingleNode("b"), "  UNNAMED3", Seq(), Direction.OUTGOING, true)).
      returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def optionalRelationship() {
    test(vFrom2_0, "start a = node(1) match a -[?]-> (b) return b",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("a"), SingleOptionalNode("b"), "  UNNAMED26", Seq(), Direction.OUTGOING, true)).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def questionMarkOperator() {
    test(vPre2_0,
      "start a = node(1) where a.prop? = 42 return a",
      Query.
        start(NodeById("a", 1)).
        where(NullablePredicate(Equals(Nullable(Property(Identifier("a"), PropertyKey("prop"))), Literal(42.0)), Seq((Nullable(Property(Identifier("a"), PropertyKey("prop"))), true)))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def exclamationMarkOperator() {
    test(vPre2_0,
      "start a = node(1) where a.prop! = 42 return a",
      Query.
        start(NodeById("a", 1)).
        where(NullablePredicate(Equals(Nullable(Property(Identifier("a"), PropertyKey("prop"))), Literal(42)), Seq((Nullable(Property(Identifier("a"), PropertyKey("prop"))), false)))).
        returns(ReturnItem(Identifier("a"), "a")))
  }

  @Test def optionalTypedRelationship1_9() {
    test(v1_9, "start a = node(1) match a -[?:KNOWS]-> (b) return b",
      Query.
      start(NodeById("a", 1)).
      matches(RelatedTo.optional("a", "b", "  UNNAMED3", Seq("KNOWS"), Direction.OUTGOING)).
      returns(ReturnItem(Identifier("b"), "b"))
    )
  }

  @Test def optionalTypedRelationship() {
    test(vFrom2_0, "start a = node(1) match a -[?:KNOWS]-> (b) return b",
      Query.
      start(NodeById("a", 1)).
      matches(RelatedTo(SingleNode("a"), SingleOptionalNode("b"), "  UNNAMED26", Seq("KNOWS"), Direction.OUTGOING, true)).
      returns(ReturnItem(Identifier("b"), "b"))
    )
  }

  @Test def optionalTypedAndNamedRelationship1_9() {
    test(v1_9,
      "start a = node(1) match a -[r?:KNOWS]-> (b) return b",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo.optional("a", "b", "r", Seq("KNOWS"), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def optionalTypedAndNamedRelationship() {
    test(vFrom2_0,
      "start a = node(1) match a -[r?:KNOWS]-> (b) return b",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("a"), SingleOptionalNode("b"), "r", Seq("KNOWS"), Direction.OUTGOING, optional = true)).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def optionalNamedRelationship1_9() {
    test(v1_9,
      "start a = node(1) match a -[r?]-> (b) return b",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo.optional("a", "b", "r", Seq(), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def optionalNamedRelationship() {
    test(vFrom2_0,
      "start a = node(1) match a -[r?]-> (b) return b",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("a"), SingleOptionalNode("b"), "r", Seq(), Direction.OUTGOING, optional = true)).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def testAllIterablePredicate() {
    test(
      """start a = node(1) match p=(a-[r]->b) where all(x in NODES(p) WHERE x.name = "Andres") return b""",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("a"), SingleNode("b"), "r", Seq(), Direction.OUTGOING, false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq(), Direction.OUTGOING))).
        where(AllInCollection(NodesFunction(Identifier("p")), "x", Equals(Property(Identifier("x"), PropertyKey("name")), Literal("Andres")))).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def testAnyIterablePredicate() {
    test(
      """start a = node(1) match p=(a-[r]->b) where any(x in NODES(p) WHERE x.name = "Andres") return b""",
      Query.
        start(NodeById("a", 1)).
        where(SingleInCollection(NodesFunction(Identifier("p")), "x", Equals(Property(Identifier("x"), PropertyKey("name")), Literal("Andres")))).
        matches(RelatedTo(SingleNode("a"), SingleNode("b"), "r", Seq(), Direction.OUTGOING, false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq(), Direction.OUTGOING))).
        where(AnyInCollection(NodesFunction(Identifier("p")), "x", Equals(Property(Identifier("x"), PropertyKey("name")), Literal("Andres")))).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def testNoneIterablePredicate() {
    test(
      """start a = node(1) match p=(a-[r]->b) where none(x in NODES(p) WHERE x.name = "Andres") return b""",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("a"), SingleNode("b"), "r", Seq(), Direction.OUTGOING, false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq(), Direction.OUTGOING))).
        where(NoneInCollection(NodesFunction(Identifier("p")), "x", Equals(Property(Identifier("x"), PropertyKey("name")), Literal("Andres")))).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def testSingleIterablePredicate() {
    test(
      """start a = node(1) match p=(a-[r]->b) where single(x in NODES(p) WHERE x.name = "Andres") return b""",
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("a"), SingleNode("b"), "r", Seq(), Direction.OUTGOING, false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq(), Direction.OUTGOING))).
        where(SingleInCollection(NodesFunction(Identifier("p")), "x", Equals(Property(Identifier("x"), PropertyKey("name")), Literal("Andres")))).
        returns(ReturnItem(Identifier("b"), "b")))
  }

  @Test def testParamAsStartNode() {
    test(
      """start pA = node({a}) return pA""",
      Query.
        start(NodeById("pA", ParameterExpression("a"))).
        returns(ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testParamAsStartRel() {
    test(
      """start pA = relationship({a}) return pA""",
      Query.
        start(RelationshipById("pA", ParameterExpression("a"))).
        returns(ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testNumericParamNameAsStartNode() {
    test(
      """start pA = node({0}) return pA""",
      Query.
        start(NodeById("pA", ParameterExpression("0"))).
        returns(ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testParamForWhereLiteral() {
    test(
      """start pA = node(1) where pA.name = {name} return pA""",
      Query.
        start(NodeById("pA", 1)).
        where(Equals(Property(Identifier("pA"), PropertyKey("name")), ParameterExpression("name")))
        returns (ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testParamForIndexKey() {
    test(vPre2_0,
      """start pA = node:idx({key} = "Value") return pA""",
      Query.
        start(NodeByIndex("pA", "idx", ParameterExpression("key"), Literal("Value"))).
        returns(ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testParamForIndexValue() {
    test(
      """start pA = node:idx(key = {Value}) return pA""",
      Query.
        start(NodeByIndex("pA", "idx", Literal("key"), ParameterExpression("Value"))).
        returns(ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testParamForIndexQuery() {
    test(
      """start pA = node:idx({query}) return pA""",
      Query.
        start(NodeByIndexQuery("pA", "idx", ParameterExpression("query"))).
        returns(ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testParamForSkip() {
    test(
      """start pA = node(0) return pA skip {skipper}""",
      Query.
        start(NodeById("pA", 0)).
        skip("skipper")
        returns (ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testParamForLimit() {
    test(
      """start pA = node(0) return pA limit {stop}""",
      Query.
        start(NodeById("pA", 0)).
        limit("stop")
        returns (ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testParamForLimitAndSkip() {
    test(
      """start pA = node(0) return pA skip {skipper} limit {stop}""",
      Query.
        start(NodeById("pA", 0)).
        skip("skipper")
        limit ("stop")
        returns (ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testQuotedParams() {
    test(
      """start pA = node({`id`}) where pA.name =~ {`regex`} return pA skip {`ski``pper`} limit {`stop`}""",
      Query.
        start(NodeById("pA", ParameterExpression("id"))).
        where(RegularExpression(Property(Identifier("pA"), PropertyKey("name")), ParameterExpression("regex")))
        skip("ski`pper")
        limit ("stop")
        returns (ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testParamForRegex() {
    test(
      """start pA = node(0) where pA.name =~ {regex} return pA""",
      Query.
        start(NodeById("pA", 0)).
        where(RegularExpression(Property(Identifier("pA"), PropertyKey("name")), ParameterExpression("regex")))
        returns (ReturnItem(Identifier("pA"), "pA")))
  }

  @Test def testShortestPathWithMaxDepth() {
    test(
      """start a=node(0), b=node(1) match p = shortestPath( a-[*..6]->b ) return p""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        matches(ShortestPath("p", SingleNode("a"), SingleNode("b"), Seq(), Direction.OUTGOING, Some(6), false, true, None)).
        returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def testShortestPathWithType() {
    test(
      """start a=node(0), b=node(1) match p = shortestPath( a-[:KNOWS*..6]->b ) return p""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        matches(ShortestPath("p", SingleNode("a"), SingleNode("b"), Seq("KNOWS"), Direction.OUTGOING, Some(6), false, true, None)).
        returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def testAllShortestPathsWithType() {
    test(
      """start a=node(0), b=node(1) match p = allShortestPaths( a-[:KNOWS*..6]->b ) return p""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        matches(ShortestPath("p", SingleNode("a"), SingleNode("b"), Seq("KNOWS"), Direction.OUTGOING, Some(6), false, false, None)).
        returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def testShortestPathWithoutStart() {
    test(vFrom2_0,
      """match p = shortestPath( a-[*..3]->b ) WHERE a.name = 'John' AND b.name = 'Sarah' return p""",
      Query.
        matches(ShortestPath("p", SingleNode("a"), SingleNode("b"), Seq(), Direction.OUTGOING, Some(3), false, true, None)).
        where(And(
          Equals(Property(Identifier("a"), PropertyKey("name")), Literal("John")),
          Equals(Property(Identifier("b"), PropertyKey("name")), Literal("Sarah"))))
        returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def testShortestPathExpression() {
    test(vFrom2_0,
      """start a=node(0), b=node(1) return shortestPath(a-[:KNOWS*..3]->b) AS path""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        returns(ReturnItem(ShortestPathExpression(
          ShortestPath("  UNNAMED34", SingleNode("a"), SingleNode("b"), Seq("KNOWS"), Direction.OUTGOING, Some(3), false, true, None)),
          "path", true)))
  }

  @Test def testForNull() {
    test(
      """start a=node(0) where a is null return a""",
      Query.
        start(NodeById("a", 0)).
        where(IsNull(Identifier("a")))
        returns (ReturnItem(Identifier("a"), "a")))
  }

  @Test def testForNotNull() {
    test(
      """start a=node(0) where a is not null return a""",
      Query.
        start(NodeById("a", 0)).
        where(Not(IsNull(Identifier("a"))))
        returns (ReturnItem(Identifier("a"), "a")))
  }

  @Test def testCountDistinct() {
    test(
      """start a=node(0) return count(distinct a)""",
      Query.
        start(NodeById("a", 0)).
        aggregation(Distinct(Count(Identifier("a")), Identifier("a"))).
        columns("count(distinct a)")
        returns (ReturnItem(Distinct(Count(Identifier("a")), Identifier("a")), "count(distinct a)")))
  }


  @Test def supportedHasRelationshipInTheWhereClause() {
    test(vPre2_0,
      """start a=node(0), b=node(1) where a-->b return a""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        where(NonEmpty(PathExpression(Seq(RelatedTo(SingleNode("a"), SingleNode("b"), "  UNNAMED39", Seq(), Direction.OUTGOING, optional = false))))).
        returns (ReturnItem(Identifier("a"), "a")))
  }

  @Test def supportsHasRelationshipInTheWhereClause() {
    test(vFrom2_0,
      """start a=node(0), b=node(1) where a-->b return a""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        where(PatternPredicate(Seq(RelatedTo(SingleNode("a"), SingleNode("b"), "  UNNAMED34", Seq(), Direction.OUTGOING, optional = false)))).
        returns (ReturnItem(Identifier("a"), "a")))
  }

  @Test def supportedNotHasRelationshipInTheWhereClause() {
    test(vPre2_0,
      """start a=node(0), b=node(1) where not(a-->()) return a""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        where(Not(NonEmpty(PathExpression(Seq(RelatedTo(SingleNode("a"), SingleNode("  UNNAMED143"), "  UNNAMED144", Seq(), Direction.OUTGOING, optional = false)))))).
        returns (ReturnItem(Identifier("a"), "a")))
  }

  @Test def supportsNotHasRelationshipInTheWhereClause() {
    test(vFrom2_0,
      """start a=node(0), b=node(1) where not(a-->()) return a""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        where(Not(PatternPredicate(Seq(RelatedTo(SingleNode("a"), SingleNode("  UNNAMED42"), "  UNNAMED38", Seq(), Direction.OUTGOING, optional = false))))).
        returns (ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldHandleLFAsWhiteSpace() {
    test(
      "start\na=node(0)\nwhere\na.prop=12\nreturn\na",
      Query.
        start(NodeById("a", 0)).
        where(Equals(Property(Identifier("a"), PropertyKey("prop")), Literal(12)))
        returns (ReturnItem(Identifier("a"), "a")))
  }

  @Test def shouldHandleUpperCaseDistinct() {
    test("start s = NODE(1) return DISTINCT s",
      Query.
        start(NodeById("s", 1)).
        aggregation().
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def shouldParseMathFunctions() {
    test("start s = NODE(0) return 5 % 4, abs(-1), round(3.1415), 2 ^ 8, sqrt(16), sign(1)",
      Query.
        start(NodeById("s", 0)).
        returns(
        ReturnItem(Modulo(Literal(5), Literal(4)), "5 % 4"),
        ReturnItem(AbsFunction(Literal(-1)), "abs(-1)"),
        ReturnItem(RoundFunction(Literal(3.1415)), "round(3.1415)"),
        ReturnItem(Pow(Literal(2), Literal(8)), "2 ^ 8"),
        ReturnItem(SqrtFunction(Literal(16)), "sqrt(16)"),
        ReturnItem(SignFunction(Literal(1)), "sign(1)")
      )
    )
  }

  @Test def shouldAllowCommentAtEnd() {
    test("start s = NODE(1) return s // COMMENT",
      Query.
        start(NodeById("s", 1)).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def shouldAllowCommentAlone() {
    test("""start s = NODE(1) return s
    // COMMENT""",
      Query.
        start(NodeById("s", 1)).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def shouldAllowCommentsInsideStrings() {
    test("start s = NODE(1) where s.apa = '//NOT A COMMENT' return s",
      Query.
        start(NodeById("s", 1)).
        where(Equals(Property(Identifier("s"), PropertyKey("apa")), Literal("//NOT A COMMENT")))
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def shouldHandleCommentsFollowedByWhiteSpace() {
    test("""start s = NODE(1)
    //I can haz more comment?
    return s""",
      Query.
        start(NodeById("s", 1)).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def first_last_and_rest() {
    test("start x = NODE(1) match p=x-[r]->z return head(nodes(p)), last(nodes(p)), tail(nodes(p))",
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "r", Seq.empty, Direction.OUTGOING, optional = false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "x", "z", Seq.empty, Direction.OUTGOING))).
        returns(
        ReturnItem(HeadFunction(NodesFunction(Identifier("p"))), "head(nodes(p))"),
        ReturnItem(LastFunction(NodesFunction(Identifier("p"))), "last(nodes(p))"),
        ReturnItem(TailFunction(NodesFunction(Identifier("p"))), "tail(nodes(p))")
      ))
  }

  @Test def filter() {
    test("start x = NODE(1) match p=x-[r]->z return filter(x in p WHERE x.prop = 123)",
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "r", Seq.empty, Direction.OUTGOING, optional = false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "x", "z", Seq.empty, Direction.OUTGOING))).
        returns(
        ReturnItem(FilterFunction(Identifier("p"), "x", Equals(Property(Identifier("x"), PropertyKey("prop")), Literal(123))), "filter(x in p WHERE x.prop = 123)")
      ))

    test(vFrom2_0, "start x = NODE(1) match p=x-[r]->z return [x in p WHERE x.prop = 123]",
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "r", Seq(), Direction.OUTGOING, false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "x", "z", Seq.empty, Direction.OUTGOING))).
        returns(
        ReturnItem(FilterFunction(Identifier("p"), "x", Equals(Property(Identifier("x"), PropertyKey("prop")), Literal(123))), "[x in p WHERE x.prop = 123]")
      ))
  }

  @Test def filterWithColon() {
    test(vAll diff List(v2_0), "start x = NODE(1) match p=x-[r]->z return filter(x in p : x.prop = 123)",
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "r", Seq.empty, Direction.OUTGOING, optional = false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "x", "z", Seq.empty, Direction.OUTGOING))).
        returns(
        ReturnItem(FilterFunction(Identifier("p"), "x", Equals(Property(Identifier("x"), PropertyKey("prop")), Literal(123))), "filter(x in p : x.prop = 123)")
      ))
  }

  @Test def extract() {
    test(vFrom2_0, "start x = NODE(1) match p=x-[r]->z return [x in p | x.prop]",
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "r", Seq(), Direction.OUTGOING, false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "x", "z", Seq.empty, Direction.OUTGOING))).
        returns(
        ReturnItem(ExtractFunction(Identifier("p"), "x", Property(Identifier("x"), PropertyKey("prop"))), "[x in p | x.prop]")
      ))
  }

  @Test def listComprehension() {
    test(vFrom2_0, "start x = NODE(1) match p=x-[r]->z return [x in p WHERE x.prop > 123 | x.prop]",
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "r", Seq(), Direction.OUTGOING, false)).
        namedPaths(NamedPath("p", ParsedRelation("r", "x", "z", Seq.empty, Direction.OUTGOING))).
        returns(
        ReturnItem(ExtractFunction(
          FilterFunction(Identifier("p"), "x", GreaterThan(Property(Identifier("x"), PropertyKey("prop")), Literal(123))),
          "x",
          Property(Identifier("x"), PropertyKey("prop"))
        ), "[x in p WHERE x.prop > 123 | x.prop]")
      ))
  }

  @Test def collection_literal() {
    test("start x = NODE(1) return ['a','b','c']",
      Query.
        start(NodeById("x", 1)).
        returns(ReturnItem(Collection(Literal("a"), Literal("b"), Literal("c")), "['a','b','c']")
      ))
  }

  @Test def collection_literal2() {
    test("start x = NODE(1) return []",
      Query.
        start(NodeById("x", 1)).
        returns(ReturnItem(Collection(), "[]")
      ))
  }

  @Test def collection_literal3() {
    test("start x = NODE(1) return [1,2,3]",
      Query.
        start(NodeById("x", 1)).
        returns(ReturnItem(Collection(Literal(1), Literal(2), Literal(3)), "[1,2,3]")
      ))
  }

  @Test def collection_literal4() {
    test("start x = NODE(1) return ['a',2]",
      Query.
        start(NodeById("x", 1)).
        returns(ReturnItem(Collection(Literal("a"), Literal(2)), "['a',2]")
      ))
  }

  @Test def in_with_collection_literal() {
    test("start x = NODE(1) where x.prop in ['a','b'] return x",
      Query.
        start(NodeById("x", 1)).
        where(AnyInCollection(Collection(Literal("a"), Literal("b")), "-_-INNER-_-", Equals(Property(Identifier("x"), PropertyKey("prop")), Identifier("-_-INNER-_-")))).
        returns(ReturnItem(Identifier("x"), "x"))
    )
  }

  @Test def multiple_relationship_type_in_matchOld() {
    val query = {
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "  UNNAMED3", Seq("REL1", "REL2", "REL3"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("x"), "x"))
    }

    test(vPre2_0, "start x = NODE(1) match x-[:REL1|REL2|REL3]->z return x", query)
  }

  @Test def multiple_relationship_type_in_match() {
    val query = {
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "  UNNAMED25", Seq("REL1", "REL2", "REL3"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("x"), "x"))
    }

    test(vFrom2_0, "start x = NODE(1) match x-[:REL1|:REL2|:REL3]->z return x", query)
  }


  @Test def multiple_relationship_type_in_varlength_relOld() {
    val q=
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "  UNNAMED3", Seq("REL1", "REL2", "REL3"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("x"), "x"))

    test(vPre2_0, "start x = NODE(1) match x-[:REL1|REL2|REL3]->z return x", q)
  }

  @Test def multiple_relationship_type_in_varlength_rel() {
    val q=
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "  UNNAMED25", Seq("REL1", "REL2", "REL3"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("x"), "x"))

    test(vFrom2_0, "start x = NODE(1) match x-[:REL1|:REL2|:REL3]->z return x", q)
  }

  @Test def multiple_relationship_type_in_shortest_pathOld() {
    def q =
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "  UNNAMED3", Seq("REL1", "REL2", "REL3"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("x"), "x"))

    test(vPre2_0, "start x = NODE(1) match x-[:REL1|REL2|REL3]->z return x", q)
  }

  @Test def multiple_relationship_type_in_shortest_path() {
    def q =
      Query.
        start(NodeById("x", 1)).
        matches(RelatedTo(SingleNode("x"), SingleNode("z"), "  UNNAMED25", Seq("REL1", "REL2", "REL3"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("x"), "x"))

    test(vFrom2_0, "start x = NODE(1) match x-[:REL1|:REL2|:REL3]->z return x", q)
  }

  @Test def multiple_relationship_type_in_relationship_predicate_back_in_the_day() {
    test(vPre2_0,
      """start a=node(0), b=node(1) where a-[:KNOWS|BLOCKS]-b return a""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        where(NonEmpty(PathExpression(Seq(RelatedTo(SingleNode("a"), SingleNode("b"), "  UNNAMED39", Seq("KNOWS","BLOCKS"), Direction.BOTH, optional = false)))))
        returns (ReturnItem(Identifier("a"), "a")))
  }

  @Test def multiple_relationship_type_in_relationship_predicate() {
    test(vFrom2_0,
      """start a=node(0), b=node(1) where a-[:KNOWS|:BLOCKS]-b return a""",
      Query.
        start(NodeById("a", 0), NodeById("b", 1)).
        where(PatternPredicate(Seq(RelatedTo(SingleNode("a"), SingleNode("b"), "  UNNAMED34", Seq("KNOWS","BLOCKS"), Direction.BOTH, optional = false))))
        returns (ReturnItem(Identifier("a"), "a")))
  }


  @Test def first_parsed_pipe_query() {
    val secondQ = Query.
      start().
      where(Equals(Property(Identifier("x"), PropertyKey("foo")), Literal(42))).
      returns(ReturnItem(Identifier("x"), "x"))

    val q = Query.
      start(NodeById("x", 1)).
      tail(secondQ).
      returns(ReturnItem(Identifier("x"), "x"))


    test("START x = node(1) WITH x WHERE x.foo = 42 RETURN x", q)
  }

  @Test def read_first_and_update_next() {
    val string = "start a = node(1) with a create (b {age : a.age * 2}) return b"

    def query(bare: Boolean) = {
      val secondQ = Query.
        start(CreateNodeStartItem(CreateNode("b", Map("age" -> Multiply(Property(Identifier("a"), PropertyKey("age")), Literal(2.0))), Seq.empty, bare))).
        returns(ReturnItem(Identifier("b"), "b"))

      Query.
        start(NodeById("a", 1)).
        tail(secondQ).
        returns(ReturnItem(Identifier("a"), "a"))
    }

    testVariants(string, query,
      true->vPre2_0,
      false->vFrom2_0)
  }

  @Test def variable_length_path_with_collection_for_relationships1_9() {
    test(vPre2_0,
      "start a=node(0) match a -[r?*1..3]-> x return x",
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo("  UNNAMED3", SingleNode("a"), SingleNode("x"), Some(1), Some(3), Seq(), Direction.OUTGOING, Some("r"), optional = true)).
        returns(ReturnItem(Identifier("x"), "x")))
  }

  @Test def variable_length_path_with_collection_for_relationships() {
    test(vFrom2_0,
      "start a=node(0) match a -[r?*1..3]-> x return x",
      Query.
        start(NodeById("a", 0)).
        matches(VarLengthRelatedTo("  UNNAMED24", SingleNode("a"), SingleOptionalNode("x"), Some(1), Some(3), Seq(), Direction.OUTGOING, Some("r"), optional = true)).
        returns(ReturnItem(Identifier("x"), "x")))
  }

  @Test def binary_precedence() {
    test(vFrom2_0, """start n=node(0) where n.a = 'x' and n.b = 'x' xor n.c = 'x' or n.d = 'x' return n""",
      Query.
        start(NodeById("n", 0)).
        where(
        Or(
          Xor(
            And(
              Equals(Property(Identifier("n"), PropertyKey("a")), Literal("x")),
              Equals(Property(Identifier("n"), PropertyKey("b")), Literal("x"))
            ),
            Equals(Property(Identifier("n"), PropertyKey("c")), Literal("x"))
          ),
          Equals(Property(Identifier("n"), PropertyKey("d")), Literal("x"))
        )
      ).returns(ReturnItem(Identifier("n"), "n"))
    )
  }

  @Test def create_node() {
    test("create a",
      Query.
        start(CreateNodeStartItem(CreateNode("a", Map(), Seq.empty))).
        returns()
    )
  }

  @Test def create_node_from_param() {
    test(vFrom2_0, "create ({param})",
      Query.
        start(CreateNodeStartItem(CreateNode("  UNNAMED8", Map("*" -> ParameterExpression("param")), Seq.empty, false))).
        returns()
    )
  }

  @Test def create_node_with_a_property() {
    val string = "create (a {name : 'Andres'})"
    def query(bare: Boolean): Query =
      Query.
        start(CreateNodeStartItem(CreateNode("a", Map("name" -> Literal("Andres")), Seq.empty, bare))).
        returns()

    testVariants(string, query,
      true -> vPre2_0,
      false -> vFrom2_0)
  }

  @Test def create_node_with_a_property2O() {
    val string = "create a={name : 'Andres'}"
    def query(bare: Boolean): Query =
      Query.
        start(CreateNodeStartItem(CreateNode("a", Map("name" -> Literal("Andres")), Seq.empty, bare))).
        returns()
    testVariants(string, query,
      true -> vPre2_0)
  }

  @Test def create_node_with_a_property_and_return_it() {
    val string = "create (a {name : 'Andres'}) return a"
    def query(bare: Boolean): Query =
      Query.
        start(CreateNodeStartItem(CreateNode("a", Map("name" -> Literal("Andres")), Seq.empty, bare))).
        returns(ReturnItem(Identifier("a"), "a"))

    testVariants(string, query,
      true -> vPre2_0,
      false -> vFrom2_0)
  }

  @Test def create_node_from_map_expressionOld() {
    test(vPre2_0, "create (a {param})",
      Query.
        start(CreateNodeStartItem(CreateNode("a", Map("*" -> ParameterExpression("param")), Seq.empty))).
        returns()
    )
  }

  @Test def create_node_from_map_expression() {
    test(vFrom2_0, "create (a {param})",
      Query.
        start(CreateNodeStartItem(CreateNode("a", Map("*" -> ParameterExpression("param")), Seq.empty, false))).
        returns()
    )
  }

  @Test def create_node_with_a_label() {
    test(vFrom2_0, "create (a:FOO)",
      Query.
        start(CreateNodeStartItem(CreateNode("a", Map(), LabelSupport.labelCollection("FOO"), false))).
        returns()
    )
  }

  @Test def create_node_with_multiple_labels() {
    test(vFrom2_0, "create (a:FOO:BAR)",
      Query.
        start(CreateNodeStartItem(CreateNode("a", Map(), LabelSupport.labelCollection("FOO", "BAR"), false))).
        returns()
    )
  }

  @Test def create_node_with_multiple_labels_with_spaces() {
    test(vFrom2_0, "create (a :FOO :BAR)",
      Query.
        start(CreateNodeStartItem(CreateNode("a", Map(), LabelSupport.labelCollection("FOO", "BAR"), false))).
        returns()
    )
  }

  @Test def create_nodes_with_labels_and_a_rel() {
    test(vFrom2_0, "CREATE (n:Person:Husband)-[:FOO]->(x:Person)",
      Query.
        start(CreateRelationshipStartItem(CreateRelationship("  UNNAMED25",
        RelationshipEndpoint(Identifier("n"),Map(), LabelSupport.labelCollection("Person", "Husband"), false),
        RelationshipEndpoint(Identifier("x"),Map(), LabelSupport.labelCollection("Person"), false), "FOO", Map()))).
        returns()
    )
  }

  @Test def start_with_two_nodes_and_create_relationship() {
    val secondQ = Query.
      start(CreateRelationshipStartItem(CreateRelationship("r",
        RelationshipEndpoint(Identifier("a"), Map(), Seq.empty, true),
        RelationshipEndpoint(Identifier("b"),Map(), Seq.empty, true), "REL", Map()))).
      returns()

    val q = Query.
      start(NodeById("a", 0), NodeById("b", 1)).
      tail(secondQ).
      returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"))

    test("start a=node(0), b=node(1) with a,b create a-[r:REL]->b", q)
  }

  @Test def start_with_two_nodes_and_create_relationship_make_outgoing() {
    val secondQ = Query.
      start(CreateRelationshipStartItem(CreateRelationship("r",
        RelationshipEndpoint(Identifier("b"), Map(), Seq.empty, true),
        RelationshipEndpoint(Identifier("a"),Map(), Seq.empty, true), "REL", Map()))).
      returns()

    val q = Query.
      start(NodeById("a", 0), NodeById("b", 1)).
      tail(secondQ).
      returns(AllIdentifiers())

    test("start a=node(0), b=node(1) create a<-[r:REL]-b", q)
  }

  @Test def start_with_two_nodes_and_create_relationship_make_outgoing_named() {
    val secondQ = Query.
      start(CreateRelationshipStartItem(CreateRelationship("r",
        RelationshipEndpoint(Identifier("b"), Map(), Seq.empty, true),
        RelationshipEndpoint(Identifier("a"),Map(), Seq.empty, true), "REL", Map()))).
      namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq("REL"), Direction.INCOMING))).
      returns(ReturnItem(Identifier("p"), "p"))

    val q = Query.
      start(NodeById("a", 0), NodeById("b", 1)).
      tail(secondQ).
      returns(AllIdentifiers())

    test("start a=node(0), b=node(1) create p=a<-[r:REL]-b return p", q)
  }

  @Test def create_relationship_with_properties() {
    val secondQ = Query.
      start(CreateRelationshipStartItem(CreateRelationship("r",
      RelationshipEndpoint(Identifier("a"),Map(),Seq.empty, true),
      RelationshipEndpoint(Identifier("b"),Map(),Seq.empty, true), "REL", Map("why" -> Literal(42), "foo" -> Literal("bar"))))).
      returns()

    val q = Query.
      start(NodeById("a", 0), NodeById("b", 1)).
      tail(secondQ).
      returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"))


    test("start a=node(0), b=node(1) with a,b create a-[r:REL {why : 42, foo : 'bar'}]->b", q)
  }

  @Test def create_relationship_without_identifierOld() {
    test(vPre2_0, "create (a {a})-[:REL]->(b {b})",
      Query.
        start(CreateRelationshipStartItem(CreateRelationship("  UNNAMED1",
          RelationshipEndpoint(Identifier("a"), Map("*" -> ParameterExpression("a")),Seq.empty, true),
          RelationshipEndpoint(Identifier("b"), Map("*" -> ParameterExpression("b")),Seq.empty, true),
          "REL", Map()))).
        returns())
  }

  @Test def create_relationship_without_identifier() {
    test(vFrom2_0, "create (a {a})-[:REL]->(b {b})",
      Query.
        start(CreateRelationshipStartItem(CreateRelationship("  UNNAMED14",
          RelationshipEndpoint(Identifier("a"), Map("*" -> ParameterExpression("a")),Seq.empty, false),
          RelationshipEndpoint(Identifier("b"), Map("*" -> ParameterExpression("b")),Seq.empty, false),
          "REL", Map()))).
        returns())
  }

  @Test def create_relationship_with_properties_from_map_old() {
    test(vPre2_0, "create (a {a})-[:REL {param}]->(b {b})",
      Query.
        start(CreateRelationshipStartItem(CreateRelationship("  UNNAMED1",
          RelationshipEndpoint(Identifier("a"), Map("*" -> ParameterExpression("a")),Seq.empty, true),
          RelationshipEndpoint(Identifier("b"), Map("*" -> ParameterExpression("b")),Seq.empty, true),
          "REL", Map("*" -> ParameterExpression("param"))))).
        returns())
  }

  @Test def create_relationship_with_properties_from_map() {
    test(vFrom2_0, "create (a {a})-[:REL {param}]->(b {b})",
      Query.
        start(CreateRelationshipStartItem(CreateRelationship("  UNNAMED14",
          RelationshipEndpoint(Identifier("a"), Map("*" -> ParameterExpression("a")), Seq.empty, false),
          RelationshipEndpoint(Identifier("b"), Map("*" -> ParameterExpression("b")), Seq.empty, false),
          "REL", Map("*" -> ParameterExpression("param"))))).
        returns())
  }

  @Test def create_relationship_without_identifier2Old() {
    test(vPre2_0, "create (a {a})-[:REL]->(b {b})",
      Query.
        start(CreateRelationshipStartItem(CreateRelationship("  UNNAMED1",
          RelationshipEndpoint(Identifier("a"), Map("*" -> ParameterExpression("a")), Seq.empty, true),
          RelationshipEndpoint(Identifier("b"), Map("*" -> ParameterExpression("b")), Seq.empty, true),
          "REL", Map()))).
        returns())
  }

  @Test def create_relationship_without_identifier2() {
    test(vFrom2_0, "create (a {a})-[:REL]->(b {b})",
      Query.
        start(CreateRelationshipStartItem(CreateRelationship("  UNNAMED14",
          RelationshipEndpoint(Identifier("a"), Map("*" -> ParameterExpression("a")), Seq.empty, false),
          RelationshipEndpoint(Identifier("b"), Map("*" -> ParameterExpression("b")), Seq.empty, false),
          "REL", Map()))).
        returns())
  }

  @Test def delete_node() {
    val secondQ = Query.
      updates(DeleteEntityAction(Identifier("a"))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(ReturnItem(Identifier("a"), "a"))

    test("start a=node(0) with a delete a", q)
  }

  @Test def simple_delete_node() {
    val secondQ = Query.
      updates(DeleteEntityAction(Identifier("a"))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(AllIdentifiers())

    test("start a=node(0) delete a", q)
  }

  @Test def delete_rel() {
    val secondQ = Query.
      updates(DeleteEntityAction(Identifier("r"))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      matches(RelatedTo("a", "b", "r", "REL", Direction.OUTGOING)).
      tail(secondQ).
      returns(AllIdentifiers())

    test("start a=node(0) match (a)-[r:REL]->(b) delete r", q)
  }

  @Test def delete_path() {
    val secondQ = Query.
      updates(DeleteEntityAction(Identifier("p"))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      matches(RelatedTo("a", "b", "r", "REL", Direction.OUTGOING)).
      namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq("REL"), Direction.OUTGOING))).
      tail(secondQ).
      returns(AllIdentifiers())

    test("start a=node(0) match p=(a)-[r:REL]->(b) delete p", q)
  }

  @Test def set_property_on_node() {
    val secondQ = Query.
      updates(PropertySetAction(Property(Identifier("a"), PropertyKey("hello")), Literal("world"))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(ReturnItem(Identifier("a"), "a"))

    test("start a=node(0) with a set a.hello = 'world'", q)
  }

  @Test def set_property_on_node_from_expression() {
    val secondQ = Query.
      updates(PropertySetAction(Property(Identifier("a"), PropertyKey("hello")), Literal("world"))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(ReturnItem(Identifier("a"), "a"))

    test(vFrom2_0, "start a=node(0) with a set (a).hello = 'world'", q)
  }

  @Test def set_multiple_properties_on_node() {
    val secondQ = Query.
      updates(
        PropertySetAction(Property(Identifier("a"), PropertyKey("hello")), Literal("world")),
        PropertySetAction(Property(Identifier("a"), PropertyKey("foo")), Literal("bar"))
      ).returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(ReturnItem(Identifier("a"), "a"))

    test("start a=node(0) with a set a.hello = 'world', a.foo = 'bar'", q)
  }

  @Test def update_property_with_expression() {
    val secondQ = Query.
      updates(PropertySetAction(Property(Identifier("a"), PropertyKey("salary")), Multiply(Property(Identifier("a"), PropertyKey("salary")), Literal(2.0)))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(ReturnItem(Identifier("a"), "a"))

    test("start a=node(0) with a set a.salary = a.salary * 2 ", q)
  }

  @Test def delete_property_old() {
    val secondQ = Query.
      updates(DeletePropertyAction(Identifier("a"), PropertyKey("salary"))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(AllIdentifiers())

    test(v1_9, "start a=node(0) delete a.salary", q)
  }

  @Test def remove_property() {
    val secondQ = Query.
      updates(DeletePropertyAction(Identifier("a"), PropertyKey("salary"))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(AllIdentifiers())

    test(vFrom2_0, "start a=node(0) remove a.salary", q)
  }

  @Test def foreach_on_pathOld() {
    val secondQ = Query.
      updates(ForeachAction(NodesFunction(Identifier("p")), "n", Seq(PropertySetAction(Property(Identifier("n"), PropertyKey("touched")), Literal(true))))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      matches(RelatedTo("a", "b", "r", "REL", Direction.OUTGOING)).
      namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq("REL"), Direction.OUTGOING))).
      tail(secondQ).
      returns(ReturnItem(Identifier("p"), "p"))

    test(vPre2_0, "start a=node(0) match p = a-[r:REL]->b with p foreach(n in nodes(p) | set n.touched = true ) ", q)
  }

  @Test def foreach_on_pathOld_with_colon() {
    val secondQ = Query.
      updates(ForeachAction(NodesFunction(Identifier("p")), "n", Seq(PropertySetAction(Property(Identifier("n"), PropertyKey("touched")), Literal(true))))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      matches(RelatedTo("a", "b", "r", "REL", Direction.OUTGOING)).
      namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq("REL"), Direction.OUTGOING))).
      tail(secondQ).
      returns(ReturnItem(Identifier("p"), "p"))

    test(vPre2_0, "start a=node(0) match p = a-[r:REL]->b with p foreach(n in nodes(p) : set n.touched = true ) ", q)
  }

  @Test def foreach_on_path() {
    val secondQ = Query.
      updates(ForeachAction(NodesFunction(Identifier("p")), "n", Seq(PropertySetAction(Property(Identifier("n"), PropertyKey("touched")), True())))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      matches(RelatedTo("a", "b", "r", "REL", Direction.OUTGOING)).
      namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq("REL"), Direction.OUTGOING))).
      tail(secondQ).
      returns(ReturnItem(Identifier("p"), "p"))

    test(vFrom2_0, "start a=node(0) match p = a-[r:REL]->b with p foreach(n in nodes(p) | set n.touched = true ) ", q)
  }

  @Test def foreach_on_path_with_multiple_updates() {
    val secondQ = Query.
      updates(ForeachAction(Collection(Literal(1), Literal(2), Literal(3)), "n", Seq(
      CreateRelationship("r1", RelationshipEndpoint("x"), RelationshipEndpoint("z"), "HAS", Map.empty),
      CreateRelationship("r2", RelationshipEndpoint("x"), RelationshipEndpoint("z2"), "HAS", Map.empty)
    ))).
      returns()

    val q = Query.
      matches(SingleNode("n")).
      tail(secondQ).
      returns(AllIdentifiers())

    test(vFrom2_0, "match n foreach(n in [1,2,3] | create (x)-[r1:HAS]->(z) create (x)-[r2:HAS]->(z2) )", q)
  }

  @Test def simple_read_first_and_update_next() {
    val string = "start a = node(1) create (b {age : a.age * 2}) return b"
    def query(bare: Boolean): Query = {
      val secondQ = Query.
        start(CreateNodeStartItem(CreateNode("b", Map("age" -> Multiply(Property(Identifier("a"), PropertyKey("age")), Literal(2.0))), Seq.empty, bare))).
        returns(ReturnItem(Identifier("b"), "b"))

      Query.
        start(NodeById("a", 1)).
        tail(secondQ).
        returns(AllIdentifiers())
    }
    testVariants(string, query,
      true -> vPre2_0,
      false -> vFrom2_0)
  }

  @Test def simple_start_with_two_nodes_and_create_relationship() {
    val secondQ = Query.
      start(CreateRelationshipStartItem(CreateRelationship("r",
        RelationshipEndpoint(Identifier("a"), Map(), Seq.empty, bare = true),
        RelationshipEndpoint(Identifier("b"), Map(), Seq.empty, bare = true), "REL", Map()))).
      returns()

    val q = Query.
      start(NodeById("a", 0), NodeById("b", 1)).
      tail(secondQ).
      returns(AllIdentifiers())


    test("start a=node(0), b=node(1) create a-[r:REL]->b", q)
  }

  @Test def simple_create_relationship_with_properties() {
    val secondQ = Query.
      start(CreateRelationshipStartItem(CreateRelationship("r",
        RelationshipEndpoint(Identifier("b"), Map(), Seq.empty, true),
        RelationshipEndpoint(Identifier("a"), Map(), Seq.empty, true), "REL",
      Map("why" -> Literal(42), "foo" -> Literal("bar"))
    ))).
      returns()

    val q = Query.
      start(NodeById("a", 0), NodeById("b", 1)).
      tail(secondQ).
      returns(AllIdentifiers())


    test("start a=node(0), b=node(1) create a<-[r:REL {why : 42, foo : 'bar'}]-b", q)
  }

  @Test def simple_set_property_on_node() {
    val secondQ = Query.
      updates(PropertySetAction(Property(Identifier("a"), PropertyKey("hello")), Literal("world"))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(AllIdentifiers())

    test("start a=node(0) set a.hello = 'world'", q)
  }

  @Test def simple_update_property_with_expression() {
    val secondQ = Query.
      updates(PropertySetAction(Property(Identifier("a"), PropertyKey("salary")), Multiply(Property(Identifier("a"),PropertyKey( "salary")), Literal(2.0)))).
      returns()

    val q = Query.
      start(NodeById("a", 0)).
      tail(secondQ).
      returns(AllIdentifiers())

    test("start a=node(0) set a.salary = a.salary * 2 ", q)
  }

  @Test def simple_foreach_on_path() {
    val string = "start a=node(0) match p = a-[r:REL]->b foreach(n in nodes(p) | set n.touched = true ) "

    def query(literal: Expression): Query = {
      val secondQ = Query.
        updates(ForeachAction(NodesFunction(Identifier("p")), "n", Seq(PropertySetAction(Property(Identifier("n"), PropertyKey("touched")), literal)))).
        returns()

      Query.
        start(NodeById("a", 0)).
        matches(RelatedTo("a", "b", "r", "REL", Direction.OUTGOING)).
        namedPaths(NamedPath("p", ParsedRelation("r", "a", "b", Seq("REL"), Direction.OUTGOING))).
        tail(secondQ).
        returns(AllIdentifiers())
    }

    testVariants(string, query,
      Literal(true) -> vPre2_0,
      True() -> vFrom2_0)
  }

  @Test def returnAll() {
    test("start s = NODE(1) return *",
      Query.
        start(NodeById("s", 1)).
        returns(AllIdentifiers()))
  }

  @Test def single_create_unique() {
    val string = "start a = node(1), b=node(2) create unique a-[:reltype]->b"
    def query(relName: String): Query = {
      val secondQ = Query.
        unique(UniqueLink("a", "b", relName, "reltype", Direction.OUTGOING)).
        returns()

      Query.
        start(NodeById("a", 1), NodeById("b", 2)).
        tail(secondQ).
        returns(AllIdentifiers())
    }

    testVariants(string, query,
      "  UNNAMED1" -> vPre2_0,
      "  UNNAMED44" -> vFrom2_0)
  }

  @Test def single_create_unique_with_rel() {
    val secondQ = Query.
      unique(UniqueLink("a", "b", "r", "reltype", Direction.OUTGOING)).
      returns()

    val q = Query.
      start(NodeById("a", 1), NodeById("b", 2)).
      tail(secondQ).
      returns(AllIdentifiers())
    test("start a = node(1), b=node(2) create unique a-[r:reltype]->b", q)
  }

  @Test def single_relate_with_empty_parenthesis() {
    val string = "start a = node(1), b=node(2) create unique a-[:reltype]->()"

    def query(DIFFERENCE: (String, String)): Query = {
    val secondQ = Query.
      unique(UniqueLink("a", DIFFERENCE._1, DIFFERENCE._2, "reltype", Direction.OUTGOING)).
      returns()

    Query.
      start(NodeById("a", 1), NodeById("b", 2)).
      tail(secondQ).
      returns(AllIdentifiers())
    }

    testVariants(string, query,
      ("  UNNAMED1", "  UNNAMED2") -> vPre2_0,
      ("  UNNAMED58", "  UNNAMED44") -> vFrom2_0)
  }

  @Test def create_unique_with_two_patterns() {
    val string = "start a = node(1) create unique a-[:X]->b<-[:X]-c"
    def query(DIFFERENCE: (String, String)): Query = {
      val secondQ = Query.
        unique(
        UniqueLink("a", "b", DIFFERENCE._1, "X", Direction.OUTGOING),
        UniqueLink("c", "b", DIFFERENCE._2, "X", Direction.OUTGOING)).
        returns()

      Query.
        start(NodeById("a", 1)).
        tail(secondQ).
        returns(AllIdentifiers())
    }

    testVariants(string, query,
      ("  UNNAMED1", "  UNNAMED2") -> vPre2_0,
      ("  UNNAMED33", "  UNNAMED41") -> vFrom2_0)
  }

  @Test def relate_with_initial_values_for_node() {
    val string = "start a = node(1) create unique a-[:X]->(b {name:'Andres'})"
    def query(DIFFERENCE: String) = {
      val secondQ = Query.
        unique(
        UniqueLink(
          NamedExpectation("a", bare = true),
          NamedExpectation("b", Map[String, Expression]("name" -> Literal("Andres")), bare = false),
          NamedExpectation(DIFFERENCE, bare = true), "X", Direction.OUTGOING)).
        returns()

      Query.
        start(NodeById("a", 1)).
        tail(secondQ).
        returns(AllIdentifiers())
    }

    testVariants(string, query,
      "  UNNAMED1" -> vPre2_0,
      "  UNNAMED33" -> vFrom2_0)
  }

  @Test def create_unique_with_initial_values_for_rel() {
    val string = "start a = node(1) create unique a-[:X {name:'Andres'}]->b"
    def query(DIFFERENCE: String) = {
      val secondQ = Query.
        unique(
        UniqueLink(
          NamedExpectation("a", bare = true),
          NamedExpectation("b", bare = true),
          NamedExpectation(DIFFERENCE, Map[String, Expression]("name" -> Literal("Andres")), bare = false), "X", Direction.OUTGOING)).
        returns()

      Query.
        start(NodeById("a", 1)).
        tail(secondQ).
        returns(AllIdentifiers())
    }
    testVariants(string, query,
      "  UNNAMED1" -> vPre2_0,
      "  UNNAMED33" -> vFrom2_0)
  }

  @Test def foreach_with_literal_collectionOld() {
    val q2 = Query.updates(
      ForeachAction(Collection(Literal(1.0), Literal(2.0), Literal(3.0)), "x", Seq(CreateNode("a", Map("number" -> Identifier("x")), Seq.empty)))
    ).returns()

    test(vPre2_0,
      "create root foreach(x in [1,2,3] | create (a {number:x}))",
      Query.
        start(CreateNodeStartItem(CreateNode("root", Map.empty, Seq.empty))).
        tail(q2).
        returns(AllIdentifiers())
    )
  }

  @Test def foreach_with_literal_collection() {
    test(vFrom2_0,
      "create root foreach(x in [1,2,3] | create (a {number:x}))",
      Query.
        start(CreateNodeStartItem(CreateNode("root", Map.empty, Seq.empty))).
        updates(ForeachAction(Collection(Literal(1.0), Literal(2.0), Literal(3.0)), "x", Seq(CreateNode("a", Map("number" -> Identifier("x")), Seq.empty, false)))).
        returns()
    )
  }

  @Test def string_literals_should_not_be_mistaken_for_identifiers() {
    val string = "create (tag1 {name:'tag2'}), (tag2 {name:'tag1'})"
    def query(bare: Boolean): Query = {
      Query.
        start(
        CreateNodeStartItem(CreateNode("tag1", Map("name" -> Literal("tag2")), Seq.empty, bare)),
        CreateNodeStartItem(CreateNode("tag2", Map("name" -> Literal("tag1")), Seq.empty, bare))
      ).returns()
    }
    testVariants(string, query,
      true -> vPre2_0,
      false -> vFrom2_0)
  }

  @Test def relate_with_two_rels_to_same_node() {
    val returns = Query.
      start(CreateUniqueStartItem(CreateUniqueAction(
      UniqueLink("root", "x", "r1", "X", Direction.OUTGOING),
      UniqueLink("root", "x", "r2", "Y", Direction.OUTGOING))))
      .returns(ReturnItem(Identifier("x"), "x"))

    val q = Query.start(NodeById("root", 0)).tail(returns).returns(AllIdentifiers())

    test(vFrom2_0,
        "start root=node(0) create unique x<-[r1:X]-root-[r2:Y]->x return x", q)
  }

  @Test def optional_shortest_path() {
    test(
      """start a  = node(1), x = node(2,3)
         match p = shortestPath(a -[?*]-> x)
         return *""",
      Query.
        start(NodeById("a", 1),NodeById("x", 2,3)).
        matches(ShortestPath("p", SingleNode("a"), SingleNode("x"), Seq(), Direction.OUTGOING, None, optional = true, single = true, relIterator = None)).
        returns(AllIdentifiers())
    )
  }

  @Test def return_paths_back_in_the_day() {
    test(vPre2_0, "start a  = node(1) return a-->()",
      Query.
        start(NodeById("a", 1)).
        returns(ReturnItem(PathExpression(Seq(RelatedTo(SingleNode("a"), SingleNode("  UNNAMED1"), "  UNNAMED2", Seq(), Direction.OUTGOING, optional = false))), "a-->()"))
    )
  }

  @Test def return_paths() {
    test(vFrom2_0, "start a  = node(1) return a-->()",
      Query.
        start(NodeById("a", 1)).
        returns(ReturnItem(PatternPredicate(Seq(RelatedTo(SingleNode("a"), SingleNode("  UNNAMED31"), "  UNNAMED27", Seq(), Direction.OUTGOING, optional = false))), "a-->()"))
    )
  }

  @Test def not_with_parenthesis() {
    test("start a  = node(1) where not(1=2) or 2=3 return a",
      Query.
        start(NodeById("a", 1)).
        where(Or(Not(Equals(Literal(1), Literal(2))), Equals(Literal(2), Literal(3)))).
        returns(ReturnItem(Identifier("a"), "a"))
    )
  }

  @Test def not_without_parenthesis() {
    test(vFrom2_0, "start a = node(1) where not true or false return a",
      Query.
        start(NodeById("a", 1)).
        where(Or(Not(True()), Not(True()))).
        returns(ReturnItem(Identifier("a"), "a"))
    )
  }

  @Test def not_with_pattern() {
    test(vFrom2_0, "MATCH (admin) WHERE NOT (admin)-[:MEMBER_OF]->() RETURN admin",
      Query.
        matches(SingleNode("admin")).
        where(Not(PatternPredicate(Seq(RelatedTo(SingleNode("admin"), SingleNode("  UNNAMED47"), "  UNNAMED31", Seq("MEMBER_OF"), Direction.OUTGOING, optional = false))))).
        returns(ReturnItem(Identifier("admin"), "admin")))

    test(vFrom2_0, "MATCH (admin) WHERE NOT ((admin)-[:MEMBER_OF]->()) RETURN admin",
      Query.
        matches(SingleNode("admin")).
        where(Not(PatternPredicate(Seq(RelatedTo(SingleNode("admin"), SingleNode("  UNNAMED48"), "  UNNAMED32", Seq("MEMBER_OF"), Direction.OUTGOING, optional = false))))).
        returns(ReturnItem(Identifier("admin"), "admin")))
  }

  @Test def full_path_in_create() {
    def query(DIFFERENCE: String) = {
      val secondQ = Query.
        start(
        CreateRelationshipStartItem(CreateRelationship("r1",
          RelationshipEndpoint(Identifier("a"), Map(), Seq.empty, true),
          RelationshipEndpoint(Identifier(DIFFERENCE), Map(), Seq.empty, true), "KNOWS", Map())),
        CreateRelationshipStartItem(CreateRelationship("r2",
          RelationshipEndpoint(Identifier(DIFFERENCE), Map(), Seq.empty, true),
          RelationshipEndpoint(Identifier("b"), Map(), Seq.empty, true), "LOVES", Map()))).
        returns()

      Query.
        start(NodeById("a", 1), NodeById("b", 2)).
        tail(secondQ).
        returns(AllIdentifiers())
    }

    val string = "start a=node(1), b=node(2) create a-[r1:KNOWS]->()-[r2:LOVES]->b"

    test(vFrom2_0, string, query("  UNNAMED49"))
    test(vPre2_0, string, query("  UNNAMED1"))
  }


  @Test def create_and_assign_to_path_identifierOld() {
    test(vPre2_0,
      "create p = a-[r:KNOWS]->() return p",
      Query.
      start(CreateRelationshipStartItem(CreateRelationship("r",
        RelationshipEndpoint(Identifier("a"), Map(), Seq.empty, true),
        RelationshipEndpoint(Identifier("  UNNAMED1"), Map(), Seq.empty, true), "KNOWS", Map()))).
        namedPaths(NamedPath("p", ParsedRelation("r", "a", "  UNNAMED1", Seq("KNOWS"), Direction.OUTGOING))).
      returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def create_and_assign_to_path_identifier() {
    test(vFrom2_0,
      "create p = a-[r:KNOWS]->() return p",
      Query.
        start(CreateRelationshipStartItem(CreateRelationship("r",
        RelationshipEndpoint(Identifier("a"), Map(), Seq.empty, true),
        RelationshipEndpoint(Identifier("  UNNAMED25"), Map(), Seq.empty, true), "KNOWS", Map()))).
        namedPaths(NamedPath("p", ParsedRelation("r", "a", "  UNNAMED25", Seq("KNOWS"), Direction.OUTGOING))).
        returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def undirected_relationship_1_9() {
    val string = "create (a {name:'A'})-[:KNOWS]-(b {name:'B'})"
    def query = Query.
      start(CreateRelationshipStartItem(CreateRelationship("  UNNAMED1",
      RelationshipEndpoint(Identifier("a"), Map("name" -> Literal("A")), Seq.empty, true),
      RelationshipEndpoint(Identifier("b"), Map("name" -> Literal("B")), Seq.empty, true), "KNOWS", Map()))).
      returns()

    test(v1_9, string, query)
  }

  @Test def relate_and_assign_to_path_identifier() {
    val string = "start a=node(0) create unique p = a-[r:KNOWS]->() return p"
    def query(DIFFERENCE: String) = {
      val q2 = Query.
        start(CreateUniqueStartItem(CreateUniqueAction(UniqueLink("a", DIFFERENCE, "r", "KNOWS", Direction.OUTGOING)))).
        namedPaths(NamedPath("p", ParsedRelation("r", "a", DIFFERENCE, Seq("KNOWS"), Direction.OUTGOING))).
        returns(ReturnItem(Identifier("p"), "p"))

      Query.
        start(NodeById("a", 0)).
        tail(q2).
        returns(AllIdentifiers())
    }

    testVariants(string, query,
    "  UNNAMED1" -> vPre2_0,
    "  UNNAMED48"-> vFrom2_0)
  }

  @Test def use_predicate_as_expression() {
    test("start n=node(0) return id(n) = 0, n is null",
      Query.
        start(NodeById("n", 0)).
        returns(
        ReturnItem(Equals(IdFunction(Identifier("n")), Literal(0)), "id(n) = 0"),
        ReturnItem(IsNull(Identifier("n")), "n is null")
      ))
  }

  @Test def create_unique_should_support_parameter_maps() {
    val start = NamedExpectation("n", true)
    val rel = NamedExpectation("  UNNAMED31", true)
    val end = NamedExpectation("  UNNAMED41", Map("*" -> ParameterExpression("param")), Seq.empty, false)

    val secondQ = Query.
                  unique(UniqueLink(start, end, rel, "foo", Direction.OUTGOING)).
                  returns(AllIdentifiers())

    test(vFrom2_0,
        "START n=node(0) CREATE UNIQUE n-[:foo]->({param}) RETURN *",
                 Query.
                 start(NodeById("n", 0)).
                 tail(secondQ).
                 returns(AllIdentifiers()))
  }

  @Test def create_unique_should_support_parameter_maps_1_9() {
    val start = NamedExpectation("n", bare = true)
    val rel = NamedExpectation("  UNNAMED2", bare = true)
    val end = new NamedExpectation("  UNNAMED1", ParameterExpression("param"), Map.empty, Seq.empty, true)

    val secondQ = Query.
      unique(UniqueLink(start, end, rel, "foo", Direction.OUTGOING)).
      returns(AllIdentifiers())

    test(v1_9,
      "START n=node(0) CREATE UNIQUE n-[:foo]->({param}) RETURN *",
      Query.
        start(NodeById("n", 0)).
        tail(secondQ).
        returns(AllIdentifiers()))
  }


  @Test def with_limit() {
    test("start n=node(0,1,2) with n limit 2 where ID(n) = 1 return n",
      Query.
        start(NodeById("n", 0, 1, 2)).
        limit(2).
        tail(Query.
          start().
          where(Equals(IdFunction(Identifier("n")), Literal(1))).
          returns(ReturnItem(Identifier("n"), "n"))
        ).
        returns(
        ReturnItem(Identifier("n"), "n")
      ))
  }

  @Test def with_sort_limit() {
    test("start n=node(0,1,2) with n order by ID(n) desc limit 2 where ID(n) = 1 return n",
      Query.
        start(NodeById("n", 0, 1, 2)).
        orderBy(SortItem(IdFunction(Identifier("n")), false)).
        limit(2).
        tail(Query.
          start().
          where(Equals(IdFunction(Identifier("n")), Literal(1))).
          returns(ReturnItem(Identifier("n"), "n"))
        ).
        returns(
        ReturnItem(Identifier("n"), "n")
      ))
  }

  @Test def set_to_param() {
    val q2 = Query.
      start().
      updates(MapPropertySetAction(Identifier("n"), ParameterExpression("prop"))).
      returns()

    test("start n=node(0) set n = {prop}",
      Query.
        start(NodeById("n", 0)).
        tail(q2).
        returns(AllIdentifiers()))
  }

  @Test def set_to_map() {
    val q2 = Query.
      start().
      updates(MapPropertySetAction(Identifier("n"), LiteralMap(Map("key" -> Literal("value"), "foo" -> Literal(1))))).
      returns()

    test(vFrom2_0, "start n=node(0) set n = {key: 'value', foo: 1}",
      Query.
        start(NodeById("n", 0)).
        tail(q2).
        returns(AllIdentifiers()))
  }

  @Test def add_label() {
    val q2 = Query.
      start().
      updates(LabelAction(Identifier("n"), LabelSetOp, List(KeyToken.Unresolved("LabelName", TokenType.Label)))).
      returns()

    test(vFrom2_0, "START n=node(0) set n:LabelName",
      Query.
        start(NodeById("n", 0)).
        tail(q2).
        returns(AllIdentifiers())
    )
  }

  @Test def add_short_label() {
    val q2 = Query.
      start().
      updates(LabelAction(Identifier("n"), LabelSetOp, List(KeyToken.Unresolved("LabelName", TokenType.Label)))).
      returns()

    test(vFrom2_0, "START n=node(0) SET n:LabelName",
      Query.
        start(NodeById("n", 0)).
        tail(q2).
        returns(AllIdentifiers())
    )
  }

  @Test def add_multiple_labels() {
    val coll = LabelSupport.labelCollection("LabelName2", "LabelName3")
    val q2   = Query.
      start().
      updates(LabelAction(Identifier("n"), LabelSetOp, coll)).
      returns()

    test(vFrom2_0, "START n=node(0) set n :LabelName2 :LabelName3",
      Query.
        start(NodeById("n", 0)).
        tail(q2).
        returns(AllIdentifiers())
    )
  }

  @Test def add_multiple_short_labels() {
    val coll = LabelSupport.labelCollection("LabelName2", "LabelName3")
    val q2   = Query.
      start().
      updates(LabelAction(Identifier("n"), LabelSetOp, coll)).
      returns()

    test(vFrom2_0, "START n=node(0) set n:LabelName2:LabelName3",
      Query.
        start(NodeById("n", 0)).
        tail(q2).
        returns(AllIdentifiers())
    )
  }

  @Test def add_multiple_short_labels2() {
    val coll = LabelSupport.labelCollection("LabelName2", "LabelName3")
    val q2   = Query.
      start().
      updates(LabelAction(Identifier("n"), LabelSetOp, coll)).
      returns()

    test(vFrom2_0, "START n=node(0) SET n :LabelName2 :LabelName3",
      Query.
        start(NodeById("n", 0)).
        tail(q2).
        returns(AllIdentifiers())
    )
  }

  @Test def remove_label() {
    val q2 = Query.
      start().
      updates(LabelAction(Identifier("n"), LabelRemoveOp, LabelSupport.labelCollection("LabelName"))).
      returns()

    test(vFrom2_0, "START n=node(0) REMOVE n:LabelName",
      Query.
        start(NodeById("n", 0)).
        tail(q2).
        returns(AllIdentifiers())
    )
  }

  @Test def remove_multiple_labels() {
    val coll = LabelSupport.labelCollection("LabelName2", "LabelName3")
    val q2   = Query.
      start().
      updates(LabelAction(Identifier("n"), LabelRemoveOp, coll)).
      returns()

    test(vFrom2_0, "START n=node(0) REMOVE n:LabelName2:LabelName3",
      Query.
        start(NodeById("n", 0)).
        tail(q2).
        returns(AllIdentifiers())
    )
  }

  @Test def filter_by_label_in_where() {
    test(vFrom2_0, "START n=node(0) WHERE (n):Foo RETURN n",
      Query.
        start(NodeById("n", 0)).
        where(HasLabel(Identifier("n"), KeyToken.Unresolved("Foo", TokenType.Label))).
        returns(ReturnItem(Identifier("n"), "n"))
    )
  }

  @Test def filter_by_label_in_where_with_expression() {
    test(vFrom2_0, "START n=node(0) WHERE (n):Foo RETURN n",
      Query.
        start(NodeById("n", 0)).
        where(HasLabel(Identifier("n"), KeyToken.Unresolved("Foo", TokenType.Label))).
        returns(ReturnItem(Identifier("n"), "n"))
    )
  }

  @Test def filter_by_labels_in_where() {
    test(vFrom2_0, "START n=node(0) WHERE n:Foo:Bar RETURN n",
      Query.
        start(NodeById("n", 0)).
        where(And(HasLabel(Identifier("n"), KeyToken.Unresolved("Foo", TokenType.Label)), HasLabel(Identifier("n"), KeyToken.Unresolved("Bar", TokenType.Label)))).
        returns(ReturnItem(Identifier("n"), "n"))
    )
  }

  @Test(expected = classOf[SyntaxException]) def create_no_index_without_properties() {
    test(vFrom2_0, "create index on :MyLabel",
      CreateIndex("MyLabel", Seq()))
  }

  @Test def create_index_on_single_property() {
    test(vFrom2_0, "create index on :MyLabel(prop1)",
      CreateIndex("MyLabel", Seq("prop1")))
  }

  @Test(expected = classOf[SyntaxException]) def create_index_on_multiple_properties() {
    test(vFrom2_0,
      "create index on :MyLabel(prop1, prop2)",
      CreateIndex("MyLabel", Seq("prop1", "prop2")))
  }

  @Test def match_left_with_single_label() {
    val query    = "start a = NODE(1) match (a:foo) -[r:MARRIED]-> () return a"
    val expected =
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("a", Seq(UnresolvedLabel("foo"))), SingleNode("  UNNAMED48"), "r", Seq("MARRIED"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("a"), "a"))

    test(vFrom2_0, query, expected)
  }

  @Test def match_left_with_multiple_labels() {
    val query    = "start a = NODE(1) match (a:foo:bar) -[r:MARRIED]-> () return a"
    val expected =
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("a", Seq(UnresolvedLabel("foo"), UnresolvedLabel("bar"))), SingleNode("  UNNAMED52"), "r", Seq("MARRIED"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("a"), "a"))

    test(vFrom2_0, query, expected)
  }

  @Test def match_right_with_multiple_labels() {
    val query    = "start a = NODE(1) match () -[r:MARRIED]-> (a:foo:bar) return a"
    val expected =
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("  UNNAMED25"), SingleNode("a", Seq(UnresolvedLabel("foo"), UnresolvedLabel("bar"))), "r", Seq("MARRIED"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("a"), "a"))

    test(vFrom2_0, query, expected)
  }

  @Test def match_both_with_labels() {
    val query    = "start a = NODE(1) match (b:foo) -[r:MARRIED]-> (a:bar) return a"
    val expected =
      Query.
        start(NodeById("a", 1)).
        matches(RelatedTo(SingleNode("b", Seq(UnresolvedLabel("foo"))), SingleNode("a", Seq(UnresolvedLabel("bar"))), "r", Seq("MARRIED"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("a"), "a"))

    test(vFrom2_0, query, expected)
  }

  @Ignore("slow test") @Test def multi_thread_parsing() {
    val q = """start root=node(0) return x"""
    val parser = CypherParser()

    val runners = (1 to 10).toList.map(x => {
      run(() => parser.parse(q))
    })

    val threads = runners.map(new Thread(_))
    threads.foreach(_.start())
    threads.foreach(_.join())

    runners.foreach(_.report())
  }

  @Test def union_ftw() {
    val q1 = Query.
      start(NodeById("s", 1)).
      returns(ReturnItem(Identifier("s"), "s"))
    val q2 = Query.
      start(NodeById("t", 1)).
      returns(ReturnItem(Identifier("t"), "t"))
    val q3 = Query.
      start(NodeById("u", 1)).
      returns(ReturnItem(Identifier("u"), "u"))

    test(vFrom2_0,
      "start s = NODE(1) return s UNION all start t = NODE(1) return t UNION all start u = NODE(1) return u",
      Union(Seq(q1, q2, q3), QueryString.empty, distinct = false))
  }

  @Test def union_distinct() {
    val q = Query.
      start(NodeById("s", 1)).
      returns(ReturnItem(Identifier("s"), "s"))

    test(vFrom2_0,
      "start s = NODE(1) return s UNION start s = NODE(1) return s UNION start s = NODE(1) return s",
      Union(Seq(q, q, q), QueryString.empty, distinct = true))
  }

  @Test def keywords_in_reltype_and_label() {
    test(vFrom2_0, "START n=node(0) MATCH (n:On)-[:WHERE]->() RETURN n",
      Query.
        start(NodeById("n", 0)).
        matches(RelatedTo(SingleNode("n", Seq(UnresolvedLabel("On"))), SingleNode("  UNNAMED40"), "  UNNAMED28", Seq("WHERE"), Direction.OUTGOING, false)).
        returns(ReturnItem(Identifier("n"), "n"))
    )
  }

  @Test def remove_index_on_single_property() {
    test(vFrom2_0, "drop index on :MyLabel(prop1)",
      DropIndex("MyLabel", Seq("prop1")))
  }

  @Test def simple_query_with_index_hint() {
    test(vFrom2_0, "match (n:Person)-->() using index n:Person(name) where n.name = 'Andres' return n",
      Query.matches(RelatedTo(SingleNode("n", Seq(UnresolvedLabel("Person"))), SingleNode("  UNNAMED20"), "  UNNAMED16", Seq(), Direction.OUTGOING, optional = false)).
        where(Equals(Property(Identifier("n"), PropertyKey("name")), Literal("Andres"))).
        using(SchemaIndex("n", "Person", "name", AnyIndex, None)).
        returns(ReturnItem(Identifier("n"), "n", renamed = false)))
  }

  @Test def single_node_match_pattern() {
    test("start s = node(*) match s return s",
      Query.
        start(AllNodes("s")).
        matches(SingleNode("s")).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def awesome_single_labeled_node_match_pattern() {
    test(vFrom2_0, "match (s:nostart) return s",
      Query.
        matches(SingleNode("s", Seq(UnresolvedLabel("nostart")))).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def single_node_match_pattern_path() {
    test("start s = node(*) match p = s return s",
      Query.
        start(AllNodes("s")).
        matches(SingleNode("s")).
        namedPaths(NamedPath("p", ParsedEntity("s"))).
        returns(ReturnItem(Identifier("s"), "s")))
  }

  @Test def label_scan_hint() {
    test(vFrom2_0, "match (p:Person) using scan p:Person return p",
      Query.
        matches(SingleNode("p", Seq(UnresolvedLabel("Person")))).
        using(NodeByLabel("p", "Person")).
        returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def varlength_named_path() {
    test(vFrom2_0, "start n=node(1) match p=n-[:KNOWS*..2]->x return p",
      Query.
        start(NodeById("n", 1)).
        matches(VarLengthRelatedTo("  UNNAMED25", SingleNode("n"), SingleNode("x"), None, Some(2), Seq("KNOWS"), Direction.OUTGOING, None, false)).
        namedPaths(NamedPath("p", ParsedVarLengthRelation("  UNNAMED25", Map.empty, ParsedEntity("n"), ParsedEntity("x"), Seq("KNOWS"), Direction.OUTGOING, false, None, Some(2), None))).
        returns(ReturnItem(Identifier("p"), "p")))
  }

  @Test def reduce_function() {
    val collection = Collection(Literal(1), Literal(2), Literal(3))
    val expression = Add(Identifier("acc"), Identifier("x"))
    test(vFrom2_0, "start n=node(1) return reduce(acc = 0, x in [1,2,3] | acc + x)",
      Query.
        start(NodeById("n", 1)).
        returns(ReturnItem(ReduceFunction(collection, "x", expression, "acc", Literal(0)), "reduce(acc = 0, x in [1,2,3] | acc + x)")))
  }

  @Test def start_and_endNode() {
    test(vFrom2_0, "start r=rel(1) return startNode(r), endNode(r)",
      Query.
        start(RelationshipById("r", 1)).
        returns(
        ReturnItem(RelationshipEndPoints(Identifier("r"), start = true), "startNode(r)"),
        ReturnItem(RelationshipEndPoints(Identifier("r"), start = false), "endNode(r)")))
  }

  @Test def mathy_aggregation_expressions() {
    val property = Property(Identifier("n"), PropertyKey("property"))
    val percentileCont = PercentileCont(property, Literal(0.4))
    val percentileDisc = PercentileDisc(property, Literal(0.5))
    val stdev = Stdev(property)
    val stdevP = StdevP(property)
    test(vFrom2_0, "match n return percentileCont(n.property, 0.4), percentileDisc(n.property, 0.5), stdev(n.property), stdevp(n.property)",
      Query.
        matches(SingleNode("n")).
        aggregation(percentileCont, percentileDisc, stdev, stdevP).
        returns(
        ReturnItem(percentileCont, "percentileCont(n.property, 0.4)"),
        ReturnItem(percentileDisc, "percentileDisc(n.property, 0.5)"),
        ReturnItem(stdev, "stdev(n.property)"),
        ReturnItem(stdevP, "stdevp(n.property)")))
  }

  @Test def escaped_identifier() {
    test(vFrom2_0, "match `Unusual identifier` return `Unusual identifier`.propertyName",
      Query.
        matches(SingleNode("Unusual identifier")).
        returns(
        ReturnItem(Property(Identifier("Unusual identifier"), PropertyKey("propertyName")), "`Unusual identifier`.propertyName")))
  }

  @Test def aliased_column_does_not_keep_escape_symbols() {
    test(vFrom2_0, "match a return a as `Escaped alias`",
      Query.
        matches(SingleNode("a")).
        returns(
        ReturnItem(Identifier("a"), "Escaped alias", renamed = true)))
  }

  @Test def create_with_labels_and_props_with_parens() {
    test(vFrom2_0, "CREATE (node :FOO:BAR {name: 'Stefan'})",
      Query.
        start(CreateNodeStartItem(CreateNode("node", Map("name"->Literal("Stefan")),
                                             LabelSupport.labelCollection("FOO", "BAR"), bare = false))).
        returns())
  }

  @Test def constraint_creation() {
    test(vFrom2_0, "CREATE CONSTRAINT ON (id:Label) ASSERT id.property IS UNIQUE",
      CreateUniqueConstraint("id", "Label", "id", "property")
    )
  }

  @Test def named_path_with_variable_length_path_and_named_relationships_collection() {
    test(vFrom2_0, "match p = (a)-[r*]->(b) return p",
    Query.
      matches(VarLengthRelatedTo("  UNNAMED13", SingleNode("a"), SingleNode("b"), None, None, Seq.empty, Direction.OUTGOING, Some("r"), optional = false)).
      namedPaths(NamedPath("p", ParsedVarLengthRelation("  UNNAMED13", Map.empty, ParsedEntity("a"), ParsedEntity("b"), Seq.empty, Direction.OUTGOING, optional = false, None, None, Some("r")))).
      returns(ReturnItem(Identifier("p"), "p"))
    )
  }

  @Test def variable_length_relationship_with_rel_collection() {
    test(vFrom2_0, "MATCH (a)-[rels*]->(b) WHERE ALL(r in rels WHERE r.prop = 42) RETURN rels",
      Query.
        matches(VarLengthRelatedTo("  UNNAMED9", SingleNode("a"), SingleNode("b"), None, None, Seq.empty, Direction.OUTGOING, Some("rels"), optional = false)).
        where(AllInCollection(Identifier("rels"), "r", Equals(Property(Identifier("r"), PropertyKey("prop")), Literal(42)))).
        returns(ReturnItem(Identifier("rels"), "rels"))
    )
  }

  @Test def simple_case_statement() {
    test(vFrom2_0, "MATCH (a) RETURN CASE a.prop WHEN 1 THEN 'hello' ELSE 'goodbye' END AS result",
      Query.
        matches(SingleNode("a")).
        returns(
        ReturnItem(SimpleCase(Property(Identifier("a"), PropertyKey("prop")), Seq(
          (Literal(1), Literal("hello"))
        ), Some(Literal("goodbye"))), "result", true)))
  }

  @Test def generic_case_statement() {
    test(vFrom2_0, "MATCH (a) RETURN CASE WHEN a.prop = 1 THEN 'hello' ELSE 'goodbye' END AS result",
      Query.
        matches(SingleNode("a")).
        returns(
        ReturnItem(GenericCase(Seq(
          (Equals(Property(Identifier("a"), PropertyKey("prop")), Literal(1)), Literal("hello"))
        ), Some(Literal("goodbye"))), "result", true)))
  }

  @Test def shouldGroupCreateAndCreateUpdate() {
    val thirdQ = Query.
      start(CreateUniqueStartItem(CreateUniqueAction(UniqueLink("wife", "friendOfFriend", "  UNNAMED128", "KNOWS", Direction.BOTH)))).
      namedPaths(NamedPath("p3", ParsedRelation("  UNNAMED128", "wife", "friendOfFriend", Seq("KNOWS"), Direction.BOTH))).
      returns(ReturnItem(Identifier("p1"), "p1"), ReturnItem(Identifier("p2"), "p2"), ReturnItem(Identifier("p3"), "p3"))

    val secondQ = Query.
      start(CreateRelationshipStartItem(CreateRelationship("  UNNAMED65",
        RelationshipEndpoint(Identifier("me"),Map.empty, Seq.empty, true),
        RelationshipEndpoint(Identifier("wife"), Map("name" -> Literal("Gunhild")), Seq.empty, false),
        "MARRIED_TO", Map()))).
      namedPaths(NamedPath("p2", new ParsedRelation("  UNNAMED65", Map(),
        ParsedEntity("me"),
        ParsedEntity("wife", Identifier("wife"), Map("name" -> Literal("Gunhild")), Seq.empty, false),
        Seq("MARRIED_TO"), Direction.OUTGOING, false))).
      tail(thirdQ).
      returns(AllIdentifiers())

    val query = Query.start(NodeById("me", 0)).
      matches(VarLengthRelatedTo("  UNNAMED30", SingleNode("me"), SingleNode("friendOfFriend"), Some(2), Some(2), Seq.empty, Direction.BOTH, None, false)).
      namedPaths(NamedPath("p1", ParsedVarLengthRelation("  UNNAMED30", Map.empty, ParsedEntity("me"), ParsedEntity("friendOfFriend"), Seq.empty, Direction.BOTH, false, Some(2), Some(2), None))).
      tail(secondQ).
      returns(AllIdentifiers())

    val string = """START me=node(0) MATCH p1 = me-[*2]-friendOfFriend CREATE p2 = me-[:MARRIED_TO]->(wife {name:"Gunhild"}) CREATE UNIQUE p3 = wife-[:KNOWS]-friendOfFriend RETURN p1,p2,p3"""

    test(vFrom2_0, string, query)
  }

  @Test def return_only_query_with_literal_map() {
    test(vFrom2_0, "RETURN { key: 'value' }",
      Query.
        matches().
        returns(
        ReturnItem(LiteralMap(Map("key"->Literal("value"))), "{ key: 'value' }")))
  }

  @Test def long_match_chain() {
    test(vFrom2_0, "match (a)<-[r1:REL1]-(b)<-[r2:REL2]-(c) return a, b, c",
      Query.
        matches(
        RelatedTo("b", "a", "r1", Seq("REL1"), Direction.OUTGOING),
        RelatedTo("c", "b", "r2", Seq("REL2"), Direction.OUTGOING)).
        returns(ReturnItem(Identifier("a"), "a"), ReturnItem(Identifier("b"), "b"), ReturnItem(Identifier("c"), "c")))
  }

  @Test def long_create_chain() {
    test(vFrom2_0, "create (a)<-[r1:REL1]-(b)<-[r2:REL2]-(c)",
      Query.
        start(
        CreateRelationshipStartItem(CreateRelationship("r1",
          RelationshipEndpoint(Identifier("b"), Map.empty, Seq.empty, true),
          RelationshipEndpoint(Identifier("a"), Map.empty, Seq.empty, true),
          "REL1", Map())),
        CreateRelationshipStartItem(CreateRelationship("r2",
          RelationshipEndpoint(Identifier("c"), Map.empty, Seq.empty, true),
          RelationshipEndpoint(Identifier("b"), Map.empty, Seq.empty, true),
          "REL2", Map()))).
        returns())
  }

  @Test def test_literal_numbers() {
    test(vFrom2_0, "RETURN 0.5, .5, 50",
      Query.
        matches().
        returns(ReturnItem(Literal(0.5), "0.5"), ReturnItem(Literal(0.5), ".5"), ReturnItem(Literal(50), "50")))
  }

  private def run(f: () => Unit) =
    new Runnable() {
      var error: Option[Throwable] = None

      def run() {
        try {
          (1 until 500).foreach(x => f())
        } catch {
          case e: Throwable => error = Some(e)
        }
      }

      def report() {
        error.foreach(e => throw e)
      }
    }

  private val vAll = List(v1_9, v2_0)
  private val vPre2_0 = List(v1_9)
  private val vFrom2_0 = vAll diff vPre2_0

  private def testVariants[A](query: String, queryProducer: A => AbstractQuery, variants: (A, Seq[CypherVersion])*) {
    for ((input, versions) <- variants) {
      test(versions, query, queryProducer(input))
    }
  }

  private def test(query: String, expectedQuery: AbstractQuery) {
    test(vAll, query, expectedQuery)
  }

  private def test(version: CypherVersion, query: String, expectedQuery: AbstractQuery) {
    test(Seq(version), query, expectedQuery)
  }

  private def test(versions: Seq[CypherVersion], query: String, expectedQuery: AbstractQuery) {
    for (version <- versions) {
      val maybeVersion = version match {
        case `vDefault` => None
        case _          => Some(version)
      }
      testQuery(maybeVersion, query, expectedQuery)
    }
  }

  private def testQuery(version: Option[CypherVersion], query: String, expectedQuery: AbstractQuery) {
    val parser = CypherParser()

    val (qWithVer, message) = version match {
      case None    => (query, "Using the default parser")
      case Some(v) => (s"cypher ${v.name} " + query, "Using parser version " + v.name)
    }

    val ast = parser.parse(qWithVer)
    try {
      assertThat(message, ast, equalTo(expectedQuery))
    } catch {
      case x: AssertionError => throw new AssertionError(x.getMessage.replace("WrappedArray", "List"))
    }
  }
}
