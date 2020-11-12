/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.reimpl.prp.index.canonical;

import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.improved.CTuple;
import io.sapl.prp.inmemory.indexed.improved.Predicate;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


//@Slf4j
@UtilityClass
public class CanonicalIndexAlgorithm {

    public Mono<PolicyRetrievalResult> match(final FunctionContext functionCtx, final VariableContext variableCtx,
                                             CanonicalIndexDataContainer dataContainer) {
        Bitmask clauseCandidatesMask = new Bitmask();
        clauseCandidatesMask.set(0, dataContainer.getNumberOfLiteralsInConjunction().length);

        int[] trueLiteralsOfConjunction = new int[dataContainer.getNumberOfLiteralsInConjunction().length];
        int[] eliminatedFormulasWithConjunction = new int[dataContainer.getNumberOfLiteralsInConjunction().length];

        Mono<PolicyRetrievalResult> resultMono = Flux.fromIterable(dataContainer.getPredicateOrder())
                .filter(predicate -> isReferenced(predicate, clauseCandidatesMask))
                .concatMap(predicate -> predicate.evaluate(functionCtx, variableCtx)
                        .map(evaluationResult ->
                                eliminateCandidatesAndReturnSatisfied(clauseCandidatesMask, predicate, evaluationResult,
                                        trueLiteralsOfConjunction, eliminatedFormulasWithConjunction,
                                        dataContainer))
                        .doOnError(__ ->
                                removeCandidatesRelatedToPredicate(predicate, clauseCandidatesMask))
                ).reduce(new Bitmask(), (b2, b1) -> orBitMask(b1, b2))
                .map(satisfied -> fetchFormulas(satisfied, dataContainer.getRelatedFormulas()))
                .map(formulas -> fetchPolicies(formulas, dataContainer.getFormulaToDocuments()))
                .map(policies -> new PolicyRetrievalResult(policies, false))
                //if any error occurs in the chain, return an empty result with the error flag set to true
                .onErrorReturn(new PolicyRetrievalResult(Collections.emptyList(), true));


        return resultMono;
    }

    Bitmask orBitMask(@NonNull Bitmask b1, @NonNull Bitmask b2) {
        Bitmask result = new Bitmask(b1);
        result.or(b2);
        return result;
    }

    private Bitmask eliminateCandidatesAndReturnSatisfied(Bitmask clauseCandidatesMask, Predicate predicate,
                                                          Boolean evaluationResult, int[] trueLiteralsOfConjunction,
                                                          int[] eliminatedFormulasWithConjunction,
                                                          CanonicalIndexDataContainer dataContainer) {

        Bitmask satisfiableCandidates = findSatisfiableCandidates(clauseCandidatesMask, predicate,
                evaluationResult, trueLiteralsOfConjunction,
                dataContainer.getNumberOfLiteralsInConjunction());

        Bitmask unsatisfiableCandidates =
                findUnsatisfiableCandidates(clauseCandidatesMask, predicate, evaluationResult);

        Bitmask orphanedCandidates = findOrphanedCandidates(clauseCandidatesMask,
                satisfiableCandidates, eliminatedFormulasWithConjunction,
                dataContainer.getConjunctionsInFormulasReferencingConjunction(),
                dataContainer.getNumberOfFormulasWithConjunction());

        eliminateCandidates(clauseCandidatesMask, unsatisfiableCandidates, satisfiableCandidates,
                orphanedCandidates);

        return satisfiableCandidates;
    }


    void removeCandidatesRelatedToPredicate(final Predicate predicate, Bitmask candidates) {
        // Bitmask affectedCandidates = findRelatedCandidates(predicate);
        Bitmask affectedCandidates = predicate.getConjunctions();
        candidates.andNot(affectedCandidates);
    }


    Bitmask findOrphanedCandidates(final Bitmask candidates, final Bitmask satisfiableCandidates,
                                   int[] eliminatedFormulasWithConjunction,
                                   Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction,
                                   int[] numberOfFormulasWithConjunction) {
        Bitmask result = new Bitmask();

        satisfiableCandidates.forEachSetBit(index -> {
            Set<CTuple> cTuples = conjunctionsInFormulasReferencingConjunction.get(index);
            for (CTuple cTuple : cTuples) {
                if (!candidates.isSet(cTuple.getCI()))
                    continue;
                eliminatedFormulasWithConjunction[cTuple.getCI()] += cTuple.getN();

                // if all formular of conjunction have been eliminated
                if (eliminatedFormulasWithConjunction[cTuple.getCI()] ==
                        numberOfFormulasWithConjunction[cTuple.getCI()]) {
                    result.set(cTuple.getCI());
                }
            }
        });

        return result;
    }

    void eliminateCandidates(final Bitmask candidates, final Bitmask unsatisfiableCandidates,
                             final Bitmask satisfiableCandidates, final Bitmask orphanedCandidates) {
        candidates.andNot(unsatisfiableCandidates);
        candidates.andNot(satisfiableCandidates);
        candidates.andNot(orphanedCandidates);
    }

    Set<DisjunctiveFormula> fetchFormulas(final Bitmask satisfiableCandidates,
                                          List<Set<DisjunctiveFormula>> relatedFormulas) {
        final Set<DisjunctiveFormula> result = new HashSet<>();
        satisfiableCandidates.forEachSetBit(index -> result.addAll(relatedFormulas.get(index)));
        return result;
    }

    Bitmask findSatisfiableCandidates(final Bitmask candidates, final Predicate predicate,
                                      final boolean evaluationResult, final int[] trueLiteralsOfConjunction,
                                      int[] numberOfLiteralsInConjunction) {
        Bitmask result = new Bitmask();
        // calling method with negated evaluation result will return satisfied clauses
        Bitmask satisfiableCandidates = findUnsatisfiableCandidates(candidates, predicate, !evaluationResult);

        satisfiableCandidates.forEachSetBit(index -> {
            // increment number of true literals
            trueLiteralsOfConjunction[index] += 1;
            // if all literals in conjunction are true, add conjunction to result
            if (trueLiteralsOfConjunction[index] == numberOfLiteralsInConjunction[index])
                result.set(index);
        });

        return result;
    }

    boolean isReferenced(final Predicate predicate, final Bitmask candidates) {
        return predicate.getConjunctions().intersects(candidates);
    }

    private Set<SAPL> fetchPolicies(final Set<DisjunctiveFormula> formulas,
                                    Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments) {
        return formulas.parallelStream()
                .map(formulaToDocuments::get)
                .flatMap(Collection::parallelStream)
                .collect(Collectors.toSet());
    }

    Bitmask findUnsatisfiableCandidates(final Bitmask candidates, final Predicate predicate,
                                        final boolean predicateEvaluationResult) {
        Bitmask result = new Bitmask(candidates);
        if (predicateEvaluationResult) {
            result.and(predicate.getFalseForTruePredicate());
        } else {
            result.and(predicate.getFalseForFalsePredicate());
        }
        return result;
    }

}
