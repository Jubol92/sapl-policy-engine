package io.sapl.grammar.sapl.impl;

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import org.junit.Test;

import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

public class ArraySlicingStepImplCustomTest {

	private final static EvaluationContext CTX = MockUtil.mockEvaluationContext();

	@Test
	public void slicingPropagatesErrors() {
		expressionErrors(CTX, "(1/0)[0:1]");
	}

	@Test
	public void applySlicingToNoArray() {
		expressionErrors(CTX, "\"abc\"[0:1]");
	}

	@Test
	public void defaultsToIdentity() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][:]";
		var expected = "[0,1,2,3,4,5,6,7,8,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void useCaseTestTwoNull() {
		var expression = "[1,2,3,4,5][2:]";
		var expected = "[3,4,5]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void negativeToTest() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][7:-1]";
		var expected = "[7,8]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayNodeNegativeFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][-3:9]";
		var expected = "[7,8]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayWithFromGreaterThanToReturnsEmptyArray() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][4:1]";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayNodeWithoutTo() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][7:]";
		var expected = "[7,8,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayNodeWithoutFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][:3]";
		var expected = "[0,1,2]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][-3:]";
		var expected = "[7,8,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStep() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][: :-1]";
		var expected = "[0,1,2,3,4,5,6,7,8,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][-2:6:-1]";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFromAndTo() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][-2:-5:-1]";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayWithNegativeStepAndToGreaterThanFrom() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][1:5:-1]";
		var expected = "[1,2,3,4]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingStepZeroErrors() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][1:5:0]";
		expressionErrors(CTX, expression);
	}

	@Test
	public void applySlicingToResultArray() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][3:6]";
		var expected = "[3,4,5]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayWithThreeStep() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][: :3]";
		var expected = "[0,3,6,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void applySlicingToArrayWithNegativeStep() {
		var expression = "[0,1,2,3,4,5,6,7,8,9][: :-3]";
		var expected = "[1,4,7]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterDefaultsToIdentity() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[:] : nil }";
		var expected = "[null,null,null,null,null,null,null,null,null,null]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterDefaultsToIdentityDescendStep() {
		var expression = "[[10,11,12,13,14],0,1,2,3,4,5,6,7,8,9] |- { @[:][-2:] : nil }";
		var expected = "[[10,11,12,null,null],0,1,2,3,4,5,6,7,8,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterErrorOnZeroStep() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[: :0] : nil }";
		expressionErrors(CTX, expression);
	}

	@Test
	public void filterEmptyArray() {
		var expression = "[] |- { @[:] : nil }";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterNegativeStepArray() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[: :-2] : nil }";
		var expected = "[null,1,null,3,null,5,null,7,null,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void filterNegativeTo() {
		var expression = "[0,1,2,3,4,5,6,7,8,9] |- { @[:-2] : nil }";
		var expected = "[null,null,null,null,null,null,null,null,8,9]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

}