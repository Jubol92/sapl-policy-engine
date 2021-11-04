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
package io.sapl.spring.method.reactive;

import static java.util.function.Predicate.not;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.ConstraintHandlerBundle;
import lombok.extern.slf4j.Slf4j;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

/**
 * The EnforceDropWhileDeniedPolicyEnforcementPoint implements continuous policy
 * enforcement on a Flux resource access point.
 *
 * After an initial PERMIT, the PEP subscribes to the resource access point and forwards
 * events downstream until a non-PERMIT decision from the PDP is received. Then, all
 * events are dropped until a new PERMIT signal arrives.
 *
 * Whenever a decision is received, the handling of obligations and advice are updated
 * accordingly.
 *
 * The PEP does not permit onErrorContinue() downstream.
 * 
 * @param <T> type of the Flux contents
 *  
 */
@Slf4j
public class EnforceDropWhileDeniedPolicyEnforcementPoint<T> extends Flux<T> {

	private Flux<AuthorizationDecision> decisions;

	private Flux<T> resourceAccessPoint;

	private ConstraintEnforcementService constraintsService;

	private EnforcementSink<T> sink;

	private Class<T> clazz;

	AtomicReference<Disposable> decisionsSubscription = new AtomicReference<Disposable>();

	AtomicReference<Disposable> dataSubscription = new AtomicReference<Disposable>();

	AtomicReference<AuthorizationDecision> latestDecision = new AtomicReference<AuthorizationDecision>();

	AtomicReference<ConstraintHandlerBundle<T>> constraintHandler = new AtomicReference<ConstraintHandlerBundle<T>>();

	AtomicBoolean stopped = new AtomicBoolean(false);

	private EnforceDropWhileDeniedPolicyEnforcementPoint(Flux<AuthorizationDecision> decisions,
			Flux<T> resourceAccessPoint, ConstraintEnforcementService constraintsService, Class<T> clazz) {
		this.decisions = decisions;
		this.resourceAccessPoint = resourceAccessPoint;
		this.constraintsService = constraintsService;
		this.clazz = clazz;
	}

	public static <V> Flux<V> of(Flux<AuthorizationDecision> decisions, Flux<V> resourceAccessPoint,
			ConstraintEnforcementService constraintsService, Class<V> clazz) {
		var pep = new EnforceDropWhileDeniedPolicyEnforcementPoint<V>(decisions, resourceAccessPoint,
				constraintsService, clazz);
		return pep.doOnTerminate(pep::handleOnTerminateConstraints)
				.doAfterTerminate(pep::handleAfterTerminateConstraints).doOnCancel(pep::handleCancel).onErrorStop();
	}

	@Override
	public void subscribe(CoreSubscriber<? super T> actual) {
		if (sink != null)
			throw new IllegalStateException("Operator may only be subscribed once.");
		ContextView context = actual.currentContext();
		sink = new EnforcementSink<T>();
		resourceAccessPoint = resourceAccessPoint.contextWrite(context);
		Flux.create(sink).subscribe(actual);
		decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
	}

	private void handleNextDecision(AuthorizationDecision decision) {
		var implicitDecision = decision;

		ConstraintHandlerBundle<T> newBundle;
		try {
			newBundle = constraintsService.bundleFor(decision, clazz);
			constraintHandler.set(newBundle);
		}
		catch (AccessDeniedException e) {
			// INDETERMINATE -> as long as we cannot handle the obligations of the current
			// decision, drop data
			constraintHandler.set(new ConstraintHandlerBundle<T>());
			implicitDecision = AuthorizationDecision.INDETERMINATE;
		}

		try {
			constraintHandler.get().handleOnDecisionConstraints();
		}
		catch (AccessDeniedException e) {
			implicitDecision = AuthorizationDecision.INDETERMINATE;
		}

		latestDecision.set(implicitDecision);

		if (implicitDecision.getResource().isPresent()) {
			try {
				sink.next(constraintsService.unmarshallResource(implicitDecision.getResource().get(), clazz));
			}
			catch (JsonProcessingException | IllegalArgumentException e) {
				log.warn("Cannot unmarshall resource from decision: " + implicitDecision, e);
			}
		}

		if (implicitDecision.getDecision() == Decision.PERMIT && dataSubscription.get() == null)
			dataSubscription.set(wrapResourceAccessPointAndSubcribe());
	}

	private Disposable wrapResourceAccessPointAndSubcribe() {
		return resourceAccessPoint.doOnError(this::handleError).doOnRequest(this::handleRequest)
				.doOnSubscribe(this::handleSubscribe).doOnNext(this::handleNext).doOnComplete(this::handleComplete)
				.subscribe();
	}

	private void handleSubscribe(Subscription s) {
		try {
			constraintHandler.get().handleOnSubscribeConstraints(s);
		}
		catch (Throwable t) {
			handleNextDecision(AuthorizationDecision.INDETERMINATE);
		}
	}

	private void handleNext(T value) {
		// the following guard clause makes sure that the constraint handlers do not get
		// called after downstream consumers cancelled. If the RAP is not consisting of
		// delayed elements, but something like Flux.just(1,2,3) the handler would be
		// called for 2 and 3, even if there was a take(1) applied downstream.
		if (stopped.get())
			return;

		var decision = latestDecision.get();

		if (decision.getDecision() != Decision.PERMIT)
			return;

		// drop elements while the last decision replaced data with resource
		if (decision.getResource().isPresent())
			return;

		try {
			var transformedValue = constraintHandler.get().handleAllOnNextConstraints(value);
			sink.next(transformedValue);
		}
		catch (Throwable t) {
			// NOOP drop only the element with the failed obligation
			// doing handleNextDecision(AuthorizationDecision.DENY); would drop all
			// subsequent messages, even if the constraint handler would succeed on then.
		}
	}

	private void handleRequest(Long value) {
		try {
			constraintHandler.get().handleOnRequestConstraints(value);
		}
		catch (Throwable t) {
			handleNextDecision(AuthorizationDecision.INDETERMINATE);
		}
	}

	private void handleOnTerminateConstraints() {
		constraintHandler.get().handleOnTerminateConstraints();
	}

	private void handleAfterTerminateConstraints() {
		constraintHandler.get().handleAfterTerminateConstraints();
	}

	private void handleComplete() {
		if (stopped.get())
			return;
		try {
			constraintHandler.get().handleOnCompleteConstraints();
		}
		catch (Throwable t) {
			// NOOP stream is finished nothing more to protect.
		}
		sink.complete();
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleCancel() {
		try {
			constraintHandler.get().handleOnCancelConstraints();
		}
		catch (Throwable t) {
			// NOOP
		}
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleError(Throwable error) {
		try {
			sink.error(constraintHandler.get().handleAllOnErrorConstraints(error));
		}
		catch (Throwable t) {
			sink.error(t);
			handleNextDecision(AuthorizationDecision.INDETERMINATE);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private void disposeDecisionsAndResourceAccessPoint() {
		stopped.set(true);
		disposeUndisposedIfPresent(decisionsSubscription);
		disposeUndisposedIfPresent(dataSubscription);
	}

	private void disposeUndisposedIfPresent(AtomicReference<Disposable> atomicDisposable) {
		Optional.ofNullable(atomicDisposable.get()).filter(not(Disposable::isDisposed)).ifPresent(Disposable::dispose);
	}

}
