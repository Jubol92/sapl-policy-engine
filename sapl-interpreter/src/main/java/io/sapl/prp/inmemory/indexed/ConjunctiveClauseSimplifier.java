package io.sapl.prp.inmemory.indexed;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class ConjunctiveClauseSimplifier implements Simplifier<ConjunctiveClause> {

	@Override
	public ConjunctiveClause reduce(final ConjunctiveClause obj) {
		List<Literal> literals = obj.getLiterals();
		if (literals.size() > 1) {
			List<Literal> result = new ArrayList<>(literals);
			reduceConstants(result);
			reduceFormula(result);
			return new ConjunctiveClause(result);
		}
		return obj;
	}

	private static void reduceConstants(final List<Literal> data) {
		ListIterator<Literal> iter = data.listIterator();
		while (iter.hasNext() && data.size() > 1) {
			Literal literal = iter.next();
			if (literal.isImmutable()) {
				if (!literal.evaluate()) {
					data.clear();
					data.add(literal);
					return;
				}
				else {
					iter.remove();
				}
			}
		}
	}

	private static void reduceFormula(final List<Literal> data) {
		ListIterator<Literal> pointer = data.listIterator();
		while (pointer.hasNext()) {
			Literal lhs = pointer.next();
			if (lhs != null && reduceFormulaStep(data, pointer, lhs)) {
				break;
			}
		}
		data.removeIf(Objects::isNull);
	}

	private static boolean reduceFormulaStep(final List<Literal> data,
			final ListIterator<Literal> pointer, final Literal value) {
		ListIterator<Literal> forward = data.listIterator(pointer.nextIndex());
		while (forward.hasNext()) {
			Literal rhs = forward.next();
			if (rhs == null) {
				continue;
			}
			if (value.sharesBool(rhs)) {
				if (value.sharesNegation(rhs)) {
					forward.set(null);
				}
				else {
					data.clear();
					data.add(new Literal(new Bool(false)));
					return true;
				}
			}
		}
		return false;
	}

}
