/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
/*
 * SAPLTest generated by Xtext
 */
package io.sapl.test.grammar.validation;

import io.sapl.test.grammar.sapltest.Duration;
import io.sapl.test.grammar.sapltest.Multiple;
import java.time.format.DateTimeParseException;
import org.eclipse.xtext.validation.Check;

/**
 * This class contains custom validation rules.
 *
 * See
 * https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
public class SAPLTestValidator extends AbstractSAPLTestValidator {
    protected static final String MSG_INVALID_JAVA_DURATION = "Duration is not a valid Java Duration";

    protected static final String MSG_INVALID_MULTIPLE_AMOUNT = "Amount needs to be a natural number larger than 1";

    /**
     * Duration string needs to represent a valid Java Duration
     *
     * @param duration a duration string
     */
    @Check
    public void durationNeedsToBeAValidJavaDuration(final Duration duration) {
        try {
            java.time.Duration.parse(duration.getDuration());
        } catch (DateTimeParseException e) {
            error(MSG_INVALID_JAVA_DURATION, duration, null);
        }
    }

    /**
     * Multiple amount needs to be a natural number larger than 1
     *
     * @param multiple a multiple instance
     */
    @Check
    public void multipleAmountNeedsToBeNaturalNumberLargerThanOne(final Multiple multiple) {
        var isValid = true;
        try {
            if (multiple.getAmount().intValueExact() < 2) {
                isValid = false;
            }
        } catch (ArithmeticException exception) {
            isValid = false;
        }
        if (!isValid) {
            error(MSG_INVALID_MULTIPLE_AMOUNT, multiple, null);
        }
    }
}