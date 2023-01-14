/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.ide;

import org.eclipse.xtext.testing.AbstractLanguageServerTest;

/**
 * This class derives from the xtext test class to define a test environment for sapl
 * policies
 */
public class AbstractSaplLanguageServerTest extends AbstractLanguageServerTest {

	public AbstractSaplLanguageServerTest() {
		super("sapl");
	}

}