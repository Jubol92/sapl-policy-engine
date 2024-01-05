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
package io.sapl.springdatar2dbc.sapl.queries.enforcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatar2dbc.database.MethodInvocationForTesting;
import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.Role;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatar2dbc.sapl.QueryManipulationExecutor;
import io.sapl.springdatar2dbc.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatar2dbc.sapl.handlers.R2dbcQueryManipulationObligationProvider;
import io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("rawtypes")
class R2dbcAnnotationQueryManipulationEnforcementPointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode           OBLIGATIONS;
    private static JsonNode           R2DBC_QUERY_MANIPULATION;
    private static JsonNode           CONDITION;
    private static JsonNode           CONDITION_WITH_CONJUNCTION;
    private static JsonNode           R2DBC_QUERY_MANIPULATION_WITH_CONJUCTION;

    final Person       malinda       = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);
    final Flux<Person> malindaAsFlux = Flux.just(malinda);

    EmbeddedPolicyDecisionPoint pdpMock = mock(EmbeddedPolicyDecisionPoint.class);

    R2dbcEntityTemplate       r2dbcEntityTemplateMock = mock(R2dbcEntityTemplate.class, Answers.RETURNS_DEEP_STUBS);
    BeanFactory               beanFactoryMock         = mock(BeanFactory.class);
    @SuppressWarnings("unchecked")
    Flux<Map<String, Object>> fluxMap                 = mock(Flux.class);;

    MockedStatic<ConstraintHandlerUtils>           constraintHandlerUtilsMock;
    MockedStatic<QueryAnnotationParameterResolver> queryAnnotationParameterResolverMockedStatic;

    @BeforeAll
    public static void setUp() throws JsonProcessingException {
        OBLIGATIONS                              = MAPPER.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"condition\":\"firstname IN('Aaron', 'Cathrin')\"},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        R2DBC_QUERY_MANIPULATION                 = MAPPER
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"condition\":\"firstname IN('Aaron', 'Cathrin')\"}");
        CONDITION                                = R2DBC_QUERY_MANIPULATION.get("condition");
        R2DBC_QUERY_MANIPULATION_WITH_CONJUCTION = MAPPER.readTree(
                "{\"type\":\"r2dbcQueryManipulation\",\"condition\":\"AND firstname IN('Aaron', 'Cathrin')\"}");
        CONDITION_WITH_CONJUNCTION               = R2DBC_QUERY_MANIPULATION_WITH_CONJUCTION.get("condition");
    }

    @BeforeEach
    public void initBeforeEach() {
        constraintHandlerUtilsMock                   = mockStatic(ConstraintHandlerUtils.class);
        queryAnnotationParameterResolverMockedStatic = mockStatic(QueryAnnotationParameterResolver.class);
    }

    @AfterEach
    public void cleanUp() {
        constraintHandlerUtilsMock.close();
        queryAnnotationParameterResolverMockedStatic.close();
    }

    @Test
    void when_thereAreConditionsInTheDecision_then_enforce() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllByAgeAfter",
                            new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of(30)), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)))
                            .thenReturn("SELECT * FROM testUser WHERE age > 30");
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(OBLIGATIONS);

                    var enforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                    when(dataManipulationHandler.manipulate(any(JsonNode.class)))
                            .thenReturn(obligations -> malindaAsFlux);
                    when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> malinda);

                    var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(r2dbcQueryManipulationObligationProviderMock.isResponsible(any(JsonNode.class)))
                            .thenReturn(Boolean.TRUE);
                    when(r2dbcQueryManipulationObligationProviderMock.getObligation(any(JsonNode.class)))
                            .thenReturn(R2DBC_QUERY_MANIPULATION);
                    when(r2dbcQueryManipulationObligationProviderMock.getCondition(any(JsonNode.class)))
                            .thenReturn(CONDITION);

                    var queryManipulationExecutor = queryManipulationExecutorMockedConstruction.constructed().get(0);
                    when(queryManipulationExecutor.execute(anyString(), eq(Person.class))).thenReturn(fluxMap);

                    var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                    // THEN
                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(1)).getObligation(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(1))
                            .getCondition(R2DBC_QUERY_MANIPULATION);
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                    queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)),
                            times(1));
                }
            }
        }
    }

    @Test
    void when_thereAreConditionsInTheDecisionButWithAnAndConjunction_then_enforce() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {
                    // GIVEN
                    var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                            new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(30, '2')), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    queryAnnotationParameterResolverMockedStatic
                            .when(() -> QueryAnnotationParameterResolver
                                    .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                            any(Object[].class)))
                            .thenReturn("SELECT * FROM testUser WHERE age = 30 AND id = '2'");
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(OBLIGATIONS);

                    var enforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                    when(dataManipulationHandler.manipulate(OBLIGATIONS)).thenReturn(obligations -> Flux.just(malinda));
                    when(dataManipulationHandler.toDomainObject()).thenCallRealMethod();

                    var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(r2dbcQueryManipulationObligationProviderMock.isResponsible(OBLIGATIONS))
                            .thenReturn(Boolean.TRUE);
                    when(r2dbcQueryManipulationObligationProviderMock.getObligation(OBLIGATIONS))
                            .thenReturn(R2DBC_QUERY_MANIPULATION_WITH_CONJUCTION);
                    when(r2dbcQueryManipulationObligationProviderMock
                            .getCondition(R2DBC_QUERY_MANIPULATION_WITH_CONJUCTION))
                            .thenReturn(CONDITION_WITH_CONJUNCTION);

                    var queryManipulationExecutor = queryManipulationExecutorMockedConstruction.constructed().get(0);
                    when(queryManipulationExecutor.execute(anyString(), eq(Person.class))).thenReturn(fluxMap);

                    var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                    // THEN
                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(1)).getObligation(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(1))
                            .getCondition(R2DBC_QUERY_MANIPULATION_WITH_CONJUCTION);
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                    queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)),
                            times(1));
                }
            }
        }
    }

    @Test
    void when_decisionIsNotPermit_then_throwAccessDeniedException() {
        // GIVEN
        var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(30, '2')), null);
        var authSub                   = AuthorizationSubscription.of("subject", "denyTest", "resource", "environment");
        var enforcementData           = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                beanFactoryMock, Person.class, pdpMock, authSub);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));

        var mongoAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                enforcementData);
        var accessDeniedException                            = mongoAnnotationQueryManipulationEnforcementPoint
                .enforce();

        // THEN
        StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();

        constraintHandlerUtilsMock.verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)),
                times(1));
        constraintHandlerUtilsMock.verify(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)),
                times(0));
    }

    @Test
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalse_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                            new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(30, '2')), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var expectedQuery             = "SELECT * FROM testUser WHERE age = 30 AND id = '2'";

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)))
                            .thenReturn(expectedQuery);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(OBLIGATIONS);

                    var enforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    when(dataManipulationHandler.constructed().get(0).manipulate(OBLIGATIONS))
                            .thenReturn(obligations -> malindaAsFlux);
                    when(dataManipulationHandler.constructed().get(0).toDomainObject())
                            .thenReturn(obligations -> malinda);

                    var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(r2dbcQueryManipulationObligationProviderMock.isResponsible(OBLIGATIONS))
                            .thenReturn(Boolean.FALSE);

                    var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                    // THEN
                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verifyNoMoreInteractions(r2dbcEntityTemplateMock.getDatabaseClient());
                    verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(0)).getObligation(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(0))
                            .getCondition(R2DBC_QUERY_MANIPULATION);
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                    queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)),
                            times(1));
                }
            }
        }
    }

    @Test
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {
                    // GIVEN
                    var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findUserTest",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")),
                            Mono.just(malinda));
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var expectedQuery             = "SELECT * FROM testUser WHERE age = 30 AND id = '2'";

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)))
                            .thenReturn(expectedQuery);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(OBLIGATIONS);

                    var enforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    when(dataManipulationHandler.constructed().get(0).manipulate(OBLIGATIONS))
                            .thenReturn(obligations -> malindaAsFlux);
                    when(dataManipulationHandler.constructed().get(0).toDomainObject())
                            .thenReturn(obligations -> malinda);

                    var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(r2dbcQueryManipulationObligationProviderMock.isResponsible(OBLIGATIONS))
                            .thenReturn(Boolean.FALSE);

                    var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                    // THEN
                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(0)).getObligation(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(0))
                            .getCondition(R2DBC_QUERY_MANIPULATION);
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                    queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)),
                            times(1));
                }
            }
        }
    }

    @Test
    void when_mongoQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_throwThrowable() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findUserTest",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")),
                            new Throwable());
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");

                    var expectedQuery = "SELECT * FROM testUser WHERE age = 30 AND id = '2'";

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)))
                            .thenReturn(expectedQuery);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(OBLIGATIONS);

                    var enforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    when(dataManipulationHandler.constructed().get(0).manipulate(OBLIGATIONS))
                            .thenReturn(obligations -> malindaAsFlux);
                    when(dataManipulationHandler.constructed().get(0).toDomainObject())
                            .thenReturn(obligations -> malinda);

                    var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(r2dbcQueryManipulationObligationProviderMock.isResponsible(OBLIGATIONS))
                            .thenReturn(Boolean.FALSE);

                    var throwableException = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                    // THEN
                    StepVerifier.create(throwableException).expectError(Throwable.class).verify();

                    verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(0)).getObligation(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(0))
                            .getCondition(R2DBC_QUERY_MANIPULATION);
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                    queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)),
                            times(1));
                }
            }
        }
    }

    @Test
    void when_thereAreConditionsInTheDecisionButNoWhereClauseInOriginalQuery_then_enforce() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<QueryManipulationExecutor> queryManipulationExecutorMockedConstruction = Mockito
                        .mockConstruction(QueryManipulationExecutor.class)) {

                    // GIVEN
                    var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                            new ArrayList<>(), new ArrayList<>(), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)))
                            .thenReturn("SELECT * FROM testUser");
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(OBLIGATIONS);

                    var enforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                            beanFactoryMock, Person.class, pdpMock, authSub);

                    var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                    when(dataManipulationHandler.manipulate(any(JsonNode.class)))
                            .thenReturn(obligations -> malindaAsFlux);
                    when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> malinda);

                    var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    when(r2dbcQueryManipulationObligationProviderMock.isResponsible(any(JsonNode.class)))
                            .thenReturn(Boolean.TRUE);
                    when(r2dbcQueryManipulationObligationProviderMock.getObligation(any(JsonNode.class)))
                            .thenReturn(R2DBC_QUERY_MANIPULATION);
                    when(r2dbcQueryManipulationObligationProviderMock.getCondition(any(JsonNode.class)))
                            .thenReturn(CONDITION);

                    var queryManipulationExecutor = queryManipulationExecutorMockedConstruction.constructed().get(0);
                    when(queryManipulationExecutor.execute(anyString(), eq(Person.class))).thenReturn(fluxMap);

                    var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                    // THEN
                    StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                    verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(1)).getObligation(OBLIGATIONS);
                    verify(r2dbcQueryManipulationObligationProviderMock, times(1))
                            .getCondition(R2DBC_QUERY_MANIPULATION);
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                    queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                            .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class),
                                    any(Object[].class)),
                            times(1));
                }
            }
        }
    }
}