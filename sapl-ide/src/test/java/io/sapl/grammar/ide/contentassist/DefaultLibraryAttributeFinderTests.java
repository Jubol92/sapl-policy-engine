/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.ide.contentassist;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.pip.TimePolicyInformationPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Tests regarding the auto completion of libraries and functions
 */
public class DefaultLibraryAttributeFinderTests {

	private static DefaultLibraryAttributeFinder attributeFinder;

	@BeforeAll
	public static void SetupLibraryAttributeFinder() throws InitializationException {
		AnnotationAttributeContext attributeContext = new AnnotationAttributeContext();
		attributeContext.loadPolicyInformationPoint(new TimePolicyInformationPoint(Clock.systemUTC()));

		AnnotationFunctionContext funtionContext = new AnnotationFunctionContext();
		funtionContext.loadLibrary(new FilterFunctionLibrary());
		funtionContext.loadLibrary(new StandardFunctionLibrary());
		funtionContext.loadLibrary(new TemporalFunctionLibrary());

		Map<String, JsonNode> environmentVariables = new HashMap<>();
		EvaluationContext evaluationContext = new EvaluationContext(attributeContext, funtionContext,
				environmentVariables);

		attributeFinder = new DefaultLibraryAttributeFinder(evaluationContext);
	}

	@ParameterizedTest
	@ValueSource(strings = { "time", "filter", "standard", "time" })
	public void getAvailableAttributes_WithEmptyString_ReturnsAllLibraries(String value) {
		Collection<String> availableAttributes = attributeFinder.getAvailableAttributes("");
		assertThat(availableAttributes.contains(value), is(true));
	}

	@ParameterizedTest
	@CsvSource({ "time, ti", "time, tim", "filter, filt", "standard, stand" })
	public void getAvailableAttributes_WithNeedle_ReturnsMatchingLibaries(ArgumentsAccessor arguments) {
		String needle = arguments.getString(1);
		String expectedLibrary = arguments.getString(0);

		Collection<String> availableAttributes = attributeFinder.getAvailableAttributes(needle);

		assertAll(() -> assertThat(availableAttributes.size(), is(1)),
				() -> assertThat(availableAttributes.contains(expectedLibrary), is(true)));
	}

	@ParameterizedTest
	@CsvSource({ "time., now" })
	public void getAvailableAttributes_WithLibraryQualifier_ReturnsAllFunctions(ArgumentsAccessor arguments) {
		String needle = arguments.getString(0);
		String expectedFunction = arguments.getString(1);

		Collection<String> availableAttributes = attributeFinder.getAvailableAttributes(needle);

		assertThat(availableAttributes.contains(expectedFunction), is(true));
	}

	@ParameterizedTest
	@CsvSource({ "time.now, now" })
	public void getAvailableAttributes_WithLibraryQualifierAndFunctionNeedle_ReturnsMatchingFunctions(
			ArgumentsAccessor arguments) {
		String needle = arguments.getString(0);
		String expectedFunction = arguments.getString(1);

		Collection<String> availableAttributes = attributeFinder.getAvailableAttributes(needle);

		assertThat(availableAttributes.contains(expectedFunction), is(true));
	}

}
