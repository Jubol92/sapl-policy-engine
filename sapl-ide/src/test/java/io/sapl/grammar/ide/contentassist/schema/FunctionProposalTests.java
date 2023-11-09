/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.grammar.ide.contentassist.schema;

import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

import io.sapl.grammar.ide.contentassist.CompletionTests;

class FunctionProposalTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_function_without_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where var foo = schemaTest.person();
                    schemaTe""";

            String cursor = "foo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.person().name", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.person();
                    tes""";

            String cursor = "tes";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("test.person().name", "test.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_variable_assigned_function_without_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where var foo = schemaTest.dog();
                    fo""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo.race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_variable_assigned_function_with_alias_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.dog();
                    fo""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo.race");
                assertProposalsSimple(expected, completionList);
                var unwanted = List.of("foo.name");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = schemaTest""";

            String cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.person().name", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_multiple_parameters() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = schemaTest""";

            String cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.dog()", "schemaTest.dog().race", "schemaTest.food(String species)",
                        "schemaTest.person()", "schemaTest.person().name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_does_not_exist() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = schemaTest.cat""";

            String cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.dog()", "schemaTest.dog().race", "schemaTest.food(String species)",
                        "schemaTest.person()", "schemaTest.person().name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_exists() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = schemaTest.dog""";

            String cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.dog()", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_without_assignment() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    schemaTest""";

            String cursor = "schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.dog()", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_not_suggest_out_of_scope() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    foo
                    var foo = schemaTest.dog""";

            String cursor = "foo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var unwanted = List.of("schemaTest.dog()", "schemaTest.dog().race");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }
}