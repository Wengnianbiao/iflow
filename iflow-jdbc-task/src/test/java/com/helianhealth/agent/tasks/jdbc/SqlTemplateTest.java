package com.helianhealth.agent.tasks.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlTemplateTest {

    @Test
    void bindsScalarsWithoutInterpolatingTheirText() {
        String hostile = "A001' OR 1=1 --";
        SqlTemplate.Bound bound = SqlTemplate.bind(
                "SELECT * FROM t WHERE barcode=$.barcode AND age>$.minAge AND active=$.active",
                JdbcVendor.MYSQL, Map.of("barcode", hostile, "minAge", 18, "active", true));
        assertEquals("SELECT * FROM t WHERE barcode=:_p0 AND age>:_p1 AND active=:_p2", bound.sql());
        assertEquals(Map.of("_p0", hostile, "_p1", 18, "_p2", true), bound.parameters());
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN $.codes", "IN ($.codes)", "NOT IN $.codes", "NOT IN ($.codes)",
            "in $.codes", "IN($.codes)"})
    void expandsListsWithExactlyOnePairOfParentheses(String predicate) {
        SqlTemplate.Bound bound = SqlTemplate.bind(
                "SELECT * FROM t WHERE code " + predicate, JdbcVendor.MYSQL,
                Map.of("codes", List.of("A001", "A002")));
        assertEquals(List.of("A001", "A002"), bound.parameters().get("_p0"));
        String expanded = NamedParameterUtils.substituteNamedParameters(
                NamedParameterUtils.parseSqlStatement(bound.sql()), new MapSqlParameterSource(bound.parameters()));
        assertTrue(expanded.endsWith("(?, ?)"), expanded);
        assertFalse(expanded.contains("(("), expanded);
        assertEquals(predicate.startsWith("NOT") ? "SELECT * FROM t WHERE code NOT IN (?, ?)"
                : predicate.startsWith("in") ? "SELECT * FROM t WHERE code in (?, ?)"
                : predicate.startsWith("IN(") ? "SELECT * FROM t WHERE code IN(?, ?)"
                : "SELECT * FROM t WHERE code IN (?, ?)", expanded);
    }

    @Test
    void commentsDoNotHideInButIdentifiersAndLiteralsCannotActAsInKeyword() {
        SqlTemplate.Bound bound = SqlTemplate.bind(
                "SELECT within, 'IN', \"IN\", $.id FROM t WHERE id NOT in /* $.ignored */\n $.ids",
                JdbcVendor.POSTGRES, Map.of("id", 1, "ids", List.of(2, 3)));
        assertEquals("SELECT within, 'IN', \"IN\", :_p0 FROM t WHERE id NOT in /* $.ignored */\n (:_p1)", bound.sql());
        assertEquals("SELECT within :_p0, 'IN' :_p1, \"IN\" :_p2",
                SqlTemplate.bind("SELECT within $.id, 'IN' $.id, \"IN\" $.id",
                        JdbcVendor.POSTGRES, Map.of("id", 1)).sql());
    }

    @Test
    void resolvesNestedFieldsArrayIndexesAndQuotedKeys() {
        SqlTemplate.Bound bound = SqlTemplate.bind(
                "SELECT $.patient.id, $.items[0].code, $['a-b'], $[\"a.b\"], $.患者.条码",
                JdbcVendor.MYSQL, Map.of("patient", Map.of("id", 7),
                        "items", List.of(Map.of("code", "A001")), "a-b", 8, "a.b", 9,
                        "患者", Map.of("条码", "C")));
        assertEquals(Map.of("_p0", 7, "_p1", "A001", "_p2", 8, "_p3", 9, "_p4", "C"), bound.parameters());
        assertEquals(Map.of("_p0", 7), SqlTemplate.bind("SELECT $[0].id", JdbcVendor.SQLSERVER,
                List.of(Map.of("id", 7))).parameters());
    }

    @Test
    void preservesSqlOperatorsAndRepeatedReferences() {
        SqlTemplate.Bound bound = SqlTemplate.bind(
                "SELECT $.amount-1,$.amount+2,$.amount/2,$.amount*2,$.amount::text",
                JdbcVendor.POSTGRES, Map.of("amount", 7));
        assertEquals("SELECT :_p0-1,:_p1+2,:_p2/2,:_p3*2,:_p4::text", bound.sql());
        assertEquals(5, bound.parameters().size());
        assertTrue(bound.parameters().values().stream().allMatch(Integer.valueOf(7)::equals));
    }

    @Test
    void literalJsonPathsQuotedIdentifiersAndCommentsStayUntouched() {
        String sql = "SELECT JSON_EXTRACT(doc, '$.barcode'), '%$.keyword%', \"$.column\", \u0060$.name\u0060"
                + " FROM t /* $.unused */ WHERE id=$.id -- $.unused\n# $.unused";
        SqlTemplate.Bound bound = SqlTemplate.bind(sql, JdbcVendor.MYSQL, Map.of("id", 7));
        assertEquals(sql.replace("id=$.id", "id=:_p0"), bound.sql());
        assertEquals(Map.of("_p0", 7), bound.parameters());
    }

    @Test
    void preservesPostgresQuotesEscapesOperatorsAndNestedComments() {
        for (JdbcVendor vendor : List.of(JdbcVendor.POSTGRES, JdbcVendor.OPENGAUSS, JdbcVendor.KINGBASE)) {
            String sql = "SELECT $$ $.raw it's raw $$, $tag$ $.raw $tag$, E'a\\'$.raw', 'a''$.raw',"
                    + " doc #> '{a,b}', $.id::text /* outer /* nested */ $.raw */";
            SqlTemplate.Bound bound = SqlTemplate.bind(sql, vendor, Map.of("id", 7));
            assertEquals(sql.replace("$.id::", ":_p0::"), bound.sql());
            assertEquals(Map.of("_p0", 7), bound.parameters());
        }
    }

    @Test
    void preservesOracleAlternativeQuotesAndDollarIdentifiers() {
        String sql = "SELECT q'[it's $.raw]', Q'!$.raw!', V$SESSION.id, foo$.bar, $.id FROM dual";
        assertEquals(sql.replace("$.id FROM", ":_p0 FROM"),
                SqlTemplate.bind(sql, JdbcVendor.ORACLE, Map.of("id", 7)).sql());
    }

    @Test
    void preservesSqlServerBracketIdentifiers() {
        String sql = "SELECT [$.column], [a]]$.column], $.id FROM t";
        assertEquals(sql.replace("$.id FROM", ":_p0 FROM"),
                SqlTemplate.bind(sql, JdbcVendor.SQLSERVER, Map.of("id", 7)).sql());
    }

    @Test
    void mysqlEscapesAndDoubleMinusArithmeticAreRecognised() {
        String sql = "SELECT 'a\\'$.raw', $.id--1 -- $.ignored\rWHERE id=$.id";
        assertEquals("SELECT 'a\\'$.raw', :_p0--1 -- $.ignored\rWHERE id=:_p1",
                SqlTemplate.bind(sql, JdbcVendor.MYSQL, Map.of("id", 7)).sql());
    }

    @Test
    void missingLeafAndExplicitNullBindNull() {
        SqlTemplate.Bound bound = SqlTemplate.bind(
                "SELECT $.absent, $.values[1]", JdbcVendor.MYSQL,
                Map.of("values", Arrays.asList("present", null)));
        assertEquals(2, bound.parameters().size());
        assertNull(bound.parameters().get("_p0"));
        assertNull(bound.parameters().get("_p1"));
    }

    @Test
    void scalarInReceivesParenthesesAndLikeUsesOrdinaryBinding() {
        assertEquals("SELECT * FROM t WHERE id IN (:_p0) AND name LIKE CONCAT('%', :_p1, '%')",
                SqlTemplate.bind("SELECT * FROM t WHERE id IN $.id AND name LIKE CONCAT('%', $.name, '%')",
                        JdbcVendor.MYSQL, Map.of("id", 7, "name", "test")).sql());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$.", "$..id", "$.*", "$[*]", "$[?(@.id)]", "$.items[-1]",
            "$.items[0:2]", "$.items[0,1]", "$.items[", "$['key'", "$.items[0]code", "$.items[9999999999999]", "$.items.length()"})
    void rejectsMalformedAndIndefinitePaths(String path) {
        assertThrows(IllegalArgumentException.class,
                () -> SqlTemplate.bind("SELECT " + path, JdbcVendor.MYSQL, Map.of()));
    }

    @Test
    void rejectsEmptyArraysStructuredValuesAndInvalidParents() {
        for (Object value : List.of(List.of(), List.of(Map.of("id", 1)), List.of(List.of(1)), Map.of("id", 1))) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> SqlTemplate.bind("SELECT * FROM t WHERE id IN $.value", JdbcVendor.MYSQL,
                            Map.of("value", value)));
            assertTrue(error.getMessage().contains("$.value"));
        }
        for (String path : List.of("$.value.id", "$.value[0]", "$.missing.id")) {
            assertThrows(IllegalArgumentException.class,
                    () -> SqlTemplate.bind("SELECT " + path, JdbcVendor.MYSQL, Map.of("value", 7)));
        }
    }

    @Test
    void payloadIsRequiredOnlyWhenReadingAPathAndStringsAreNotParsedAsJson() {
        assertEquals("SELECT 1", SqlTemplate.bind("SELECT 1", JdbcVendor.MYSQL, null).sql());
        assertThrows(IllegalArgumentException.class, () -> SqlTemplate.bind("SELECT $.id", JdbcVendor.MYSQL, null));
        assertThrows(IllegalArgumentException.class,
                () -> SqlTemplate.bind("SELECT $.id", JdbcVendor.MYSQL, "{\"id\":7}"));
    }

    @Test
    void doesNotInterpretOldMarkers() {
        SqlTemplate.Bound bound = SqlTemplate.bind("SELECT #{id}, '##{id}'", JdbcVendor.POSTGRES, Map.of("id", 7));
        assertEquals("SELECT #{id}, '##{id}'", bound.sql());
        assertTrue(bound.parameters().isEmpty());
    }

    @Test
    void rejectsUnterminatedSqlStructures() {
        for (String sql : List.of("SELECT 'raw", "SELECT /* raw", "SELECT $tag$ raw")) {
            assertThrows(IllegalArgumentException.class, () -> SqlTemplate.bind(sql, JdbcVendor.POSTGRES, null));
        }
    }
}
