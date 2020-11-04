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
package io.sapl.grammar.sapl.impl;

import java.math.BigDecimal;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

public class UnaryMinusImplCustom extends UnaryMinusImpl {

	@Override
	public Flux<Val> evaluate(EvaluationContext ctx, Val relativeNode) {
		return getExpression().evaluate(ctx, relativeNode).flatMap(Val::toBigDecimal).map(BigDecimal::negate)
				.map(Val::of).distinctUntilChanged();
	}

}
