package io.sapl.grammar.sapl.impl;

import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.reflections.Reflections;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Evaluable;
import io.sapl.interpreter.EvaluationContext;

@RunWith(Parameterized.class)
public class EvaluableEvaluateNullCheckTest {

	private final static EvaluationContext CTX = mock(EvaluationContext.class);

	private Evaluable evaluable;

	public EvaluableEvaluateNullCheckTest(Evaluable evaluable) {
		this.evaluable = evaluable;
	}

	@Parameters
	public static Collection<Object> data() throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		var reflections = new Reflections("io.sapl.grammar.sapl.impl");
		var classes = reflections.getSubTypesOf(Evaluable.class);
		List<Object> instances = new ArrayList<Object>(classes.size());
		for (var clazz : classes) {
			if (clazz.getSimpleName().endsWith("ImplCustom")
					&& !clazz.getSimpleName().equals("BasicExpressionImplCustom")) {
				instances.add(clazz.getDeclaredConstructor().newInstance());
			}
		}
		return (List<Object>) instances;
	}

	@Test(expected = NullPointerException.class)
	public void nullEvaluationContext() {
		evaluable.evaluate(null, Val.UNDEFINED);
	}

	@Test(expected = NullPointerException.class)
	public void nullullRelativeNode() {
		evaluable.evaluate(CTX, null);
	}
}