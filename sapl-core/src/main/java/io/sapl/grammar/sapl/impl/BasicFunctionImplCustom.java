/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Implements the evaluation of functions.
 *
 * Basic returns BasicExpression: {BasicGroup} '(' expression=Expression ')' steps+=Step*
 * | {BasicValue} value=Value steps+=Step* | {BasicFunction} fsteps+=ID ('.' fsteps+=ID)*
 * arguments=Arguments steps+=Step* | {BasicIdentifier} identifier=ID steps+=Step* |
 * BasicRelative;
 */
public class BasicFunctionImplCustom extends BasicFunctionImpl {

	private static final String UNDEFINED_PARAMETER_VALUE_HANDED_TO_FUNCTION_CALL = "undefined parameter value handed to function call";

	@Override
	public Flux<Optional<JsonNode>> evaluate(EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		if (getArguments() != null && !getArguments().getArgs().isEmpty()) {
			// first create a List of FLuxes containing the Fluxes of argument assignments
			// by subscribing to the individual expressions.
			final List<Flux<Optional<JsonNode>>> arguments = new ArrayList<>(
					getArguments().getArgs().size());
			for (Expression argument : getArguments().getArgs()) {
				arguments.add(argument.evaluate(ctx, isBody, relativeNode));
			}
			// evaluate function for append the function evaluation to each value
			// assignment
			// of the parameters
			return Flux.combineLatest(arguments, Function.identity())
					.flatMap(parameters -> evaluateFunction(parameters, ctx))
					.map(funResult -> evaluateStepsFilterSubtemplate(funResult,
							getSteps(), ctx, isBody, relativeNode))
					.flatMap(Function.identity());
		}
		else {
			// No need to subscribe to parameters. Just evaluate and apply steps.
			return evaluateFunction(null, ctx)
					.map(funResult -> evaluateStepsFilterSubtemplate(funResult,
							getSteps(), ctx, isBody, relativeNode))
					.flatMap(Function.identity());
		}
	}

	private String functionName(EvaluationContext ctx) {
		String joinedSteps = String.join(".", getFsteps());
		return ctx.getImports().getOrDefault(joinedSteps, joinedSteps);
	}

	@SuppressWarnings("unchecked")
	private Flux<Optional<JsonNode>> evaluateFunction(Object[] parameters,
			EvaluationContext ctx) {
		final ArrayNode argumentsArray = JSON.arrayNode();
		try {
			if (parameters != null) {
				for (Object paramNode : parameters) {
					// TODO: consider to adjust function library interfaces to:
					// - accept fluxes
					// - accept undefined
					// - return Mono/Flux
					// Functions currently still operate in the 1.0.0 engine mindset.
					// But:
					// - functions are always local (never remote)
					// - they never produce a flux of results by their own, only if their
					// arguments
					// are fluxes
					// - therefore they can be used in target expressions, where fluxes of
					// results
					// don't make sense
					// (and never occur because attribute finders, the only source of
					// result fluxes,
					// are not allowed
					// in target expressions)
					// That's why the function library interface was intentionally left
					// non reactive
					argumentsArray.add(((Optional<JsonNode>) paramNode)
							.orElseThrow(() -> new FunctionException(
									UNDEFINED_PARAMETER_VALUE_HANDED_TO_FUNCTION_CALL)));
				}
			}
			return Flux.just(
					ctx.getFunctionCtx().evaluate(functionName(ctx), argumentsArray));
		}
		catch (FunctionException e) {
			return Flux.error(new PolicyEvaluationException(e));
		}
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + (getArguments() == null ? 0 : getArguments().hash(imports));
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		hash = 37 * hash + (getFilter() == null ? 0 : getFilter().hash(imports));
		String identifier = String.join(".", getFsteps());
		if (imports != null) {
			String imp = imports.get(identifier);
			if (imp != null) {
				identifier = imp;
			}
		}
		hash = 37 * hash + Objects.hashCode(identifier);
		for (Step step : getSteps()) {
			hash = 37 * hash + (step == null ? 0 : step.hash(imports));
		}
		hash = 37 * hash
				+ (getSubtemplate() == null ? 0 : getSubtemplate().hash(imports));
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports,
			Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final BasicFunctionImplCustom otherImpl = (BasicFunctionImplCustom) other;
		if (getArguments() == null ? getArguments() != otherImpl.getArguments()
				: !getArguments().isEqualTo(otherImpl.getArguments(), otherImports,
						imports)) {
			return false;
		}
		if (getFilter() == null ? getFilter() != otherImpl.getFilter()
				: !getFilter().isEqualTo(otherImpl.getFilter(), otherImports, imports)) {
			return false;
		}
		if (getFsteps() == null != (otherImpl.getFsteps() == null)) {
			return false;
		}
		String lhIdentifier = String.join(".", getFsteps());
		if (imports != null) {
			String imp = imports.get(lhIdentifier);
			if (imp != null) {
				lhIdentifier = imp;
			}
		}
		String rhIdentifier = String.join(".", otherImpl.getFsteps());
		if (otherImports != null) {
			String imp = otherImports.get(rhIdentifier);
			if (imp != null) {
				rhIdentifier = otherImports.get(rhIdentifier);
			}
		}
		if (!Objects.equals(lhIdentifier, rhIdentifier)) {
			return false;
		}
		if (getSubtemplate() == null ? getSubtemplate() != otherImpl.getSubtemplate()
				: !getSubtemplate().isEqualTo(otherImpl.getSubtemplate(), otherImports,
						imports)) {
			return false;
		}
		if (getSteps().size() != otherImpl.getSteps().size()) {
			return false;
		}
		ListIterator<Step> left = getSteps().listIterator();
		ListIterator<Step> right = otherImpl.getSteps().listIterator();
		while (left.hasNext()) {
			Step lhStep = left.next();
			Step rhStep = right.next();
			if (!lhStep.isEqualTo(rhStep, otherImports, imports)) {
				return false;
			}
		}
		return true;
	}

}
