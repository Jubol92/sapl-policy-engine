/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.combinators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuples;

@Slf4j
public abstract class AbstractEagerCombinator implements DocumentsCombinator, PolicyCombinator {

	@Override
	public Flux<AuthorizationDecision> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments,
			boolean errorsInTarget, AuthorizationSubscription authzSubscription, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables) {
		log.debug("|-- Combining matching documents");

		final VariableContext variableCtx;
		try {
			variableCtx = new VariableContext(authzSubscription, systemVariables);
		} catch (PolicyEvaluationException e) {
			// Error -> default to INDETERMINATE
			log.debug("| |-- Error with context initialization. Cannot evaluate policies. Default to {}. {}",
					AuthorizationDecision.INDETERMINATE, e.getMessage());
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}

		var evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variableCtx);

		final List<Flux<AuthorizationDecision>> authzDecisionFluxes = new ArrayList<>(matchingSaplDocuments.size());
		for (SAPL document : matchingSaplDocuments) {
			log.debug("| |-- Evaluate: {} ({})", document.getPolicyElement().getSaplName(),
					document.getPolicyElement().getClass().getName());
			// do not first check match again. directly evaluate the rules
			authzDecisionFluxes.add(document.evaluate(evaluationCtx));
		}
		if (matchingSaplDocuments == null || matchingSaplDocuments.isEmpty()) {
			return Flux.just(combineDecisions(new AuthorizationDecision[0], errorsInTarget));
		}
		return Flux.combineLatest(authzDecisionFluxes, decisions -> combineDecisions(decisions, errorsInTarget));
	}

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		return Flux.fromIterable(policies)
				.concatMap(policy -> policy.matches(ctx).map(match -> Tuples.of(match, policy)))
				.reduce(Tuples.of(Boolean.FALSE, new ArrayList<Policy>(policies.size())), (state, match) -> {
					var newState = new ArrayList<>(state.getT2());
					if (match.getT1().isBoolean() && match.getT1().getBoolean()) {
						newState.add(match.getT2());
					}
					return Tuples.of(state.getT1() || match.getT1().isError(), newState);
				}).flux().concatMap(matching -> doCombine(matching.getT2(), matching.getT1(), ctx));
	}

	private Flux<AuthorizationDecision> doCombine(List<Policy> matchingPolicies, boolean errorsInTarget,
			EvaluationContext ctx) {
		log.debug("| |-- Combining {} policies", matchingPolicies.size());
		final List<Flux<AuthorizationDecision>> authzDecisionFluxes = new ArrayList<>(matchingPolicies.size());
		for (Policy policy : matchingPolicies) {
			authzDecisionFluxes.add(policy.evaluate(ctx));
		}
		if (matchingPolicies == null || matchingPolicies.isEmpty()) {
			return Flux.just(combineDecisions(new AuthorizationDecision[0], errorsInTarget));
		}
		return Flux.combineLatest(authzDecisionFluxes, decisions -> combineDecisions(decisions, errorsInTarget));
	}

	protected abstract AuthorizationDecision combineDecisions(Object[] decisions, boolean errorsInTarget);
}