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
package io.sapl.pip;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import io.sapl.grammar.sapl.impl.Val;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@NoArgsConstructor
@PolicyInformationPoint(name = ClockPolicyInformationPoint.NAME, description = ClockPolicyInformationPoint.DESCRIPTION)
public class ClockPolicyInformationPoint {

	public static final String NAME = "clock";

	public static final String DESCRIPTION = "Policy Information Point and attributes for retrieving current date and time information";

	@Attribute(
			docs = "Returns the current date and time in the given time zone (e.g. 'UTC', 'ECT', 'Europe/Berlin', 'system') as an ISO-8601 string with time offset.")
	public Flux<Val> now(@Text Val value, Map<String, JsonNode> variables) throws AttributeException {
		try {
			final ZoneId zoneId = convertToZoneId(value.get());
			final OffsetDateTime now = Instant.now().atZone(zoneId).toOffsetDateTime();
			return Val.fluxOf(now.toString());
		}
		catch (Exception e) {
			throw new AttributeException("Exception while converting the given value to a ZoneId.", e);
		}
	}

	private ZoneId convertToZoneId(JsonNode value) {
		final String text = value.asText() == null ? "" : value.asText().trim();
		final String zoneIdStr = text.length() == 0 ? "system" : text;
		if ("system".equals(zoneIdStr)) {
			return ZoneId.systemDefault();
		}
		else if (ZoneId.SHORT_IDS.containsKey(zoneIdStr)) {
			return ZoneId.of(zoneIdStr, ZoneId.SHORT_IDS);
		}
		return ZoneId.of(zoneIdStr);
	}

	@Attribute(
			docs = "Emits every x seconds the current UTC date and time as an ISO-8601 string. x is the passed number value.")
	public Flux<Val> ticker(@Number Val value, Map<String, JsonNode> variables) throws AttributeException {
		try {
			return Flux.interval(Duration.ofSeconds(value.get().asLong())).map(i -> Val.of(Instant.now().toString()));
		}
		catch (Exception e) {
			throw new AttributeException("Exception while creating the next ticker value.", e);
		}
	}

}
