/*
 * Copyright 2019 Roessingh Research and Development.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package nl.rrd.wool.model.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.rrd.wool.exception.LineNumberParseException;
import nl.rrd.wool.expressions.EvaluationException;
import nl.rrd.wool.expressions.Expression;
import nl.rrd.wool.expressions.Value;
import nl.rrd.wool.expressions.types.AssignExpression;
import nl.rrd.wool.model.WoolNodeBody;
import nl.rrd.wool.parser.WoolBodyParser;
import nl.rrd.wool.parser.WoolBodyToken;
import nl.rrd.wool.parser.WoolNodeState;
import nl.rrd.wool.utils.CurrentIterator;

/**
 * This class models the &lt;&lt;if ...&gt;&gt; command in Wool. It can be part
 * of a {@link WoolNodeBody WoolNodeBody} (not inside a reply).
 * 
 * @author Dennis Hofs (RRD)
 */
public class WoolIfCommand extends WoolExpressionCommand {
	private List<Clause> ifClauses = new ArrayList<>();
	private WoolNodeBody elseClause = null;

	/**
	 * Returns the if clauses. They should be processed from first to last.
	 * There should be at least one clause. That is the "if" clause. Any
	 * subsequent clauses are "elseif" clauses.
	 * 
	 * @return the if clauses
	 */
	public List<Clause> getIfClauses() {
		return ifClauses;
	}

	/**
	 * Sets the if clauses. They should be processed from first to last. There
	 * should be at least one clause. That is the "if" clause. Any subsequent
	 * clauses are "elseif" clauses.
	 * 
	 * @param ifClauses the if clauses
	 */
	public void setIfClauses(List<Clause> ifClauses) {
		this.ifClauses = ifClauses;
	}
	
	/**
	 * Adds an if clause. The clauses should be processed from first to last.
	 * There should be at least one clause.That is the "if" clause. Any
	 * subsequent clauses are "elseif" clauses.
	 * 
	 * @param ifClause the if clause
	 */
	public void addIfClause(Clause ifClause) {
		ifClauses.add(ifClause);
	}

	/**
	 * Returns the else clause. If there is no else clause, then this method
	 * returns null (default).
	 * 
	 * @return the else clause or null
	 */
	public WoolNodeBody getElseClause() {
		return elseClause;
	}

	/**
	 * Sets the else clause. If there is no else clause, this can be set to
	 * null (default).
	 * 
	 * @param elseClause the else clause or null
	 */
	public void setElseClause(WoolNodeBody elseClause) {
		this.elseClause = elseClause;
	}
	
	@Override
	public void getReadVariableNames(Set<String> varNames) {
		for (Clause clause : ifClauses) {
			varNames.addAll(clause.expression.getVariableNames());
			clause.statement.getReadVariableNames(varNames);
		}
		if (elseClause != null)
			elseClause.getReadVariableNames(varNames);
	}

	@Override
	public void executeBodyCommand(Map<String, Object> variables,
			WoolNodeBody processedBody) throws EvaluationException {
		for (Clause clause : ifClauses) {
			Value clauseEval = clause.expression.evaluate(variables);
			if (clauseEval.asBoolean()) {
				clause.statement.execute(variables, processedBody);
				return;
			}
		}
		if (elseClause != null)
			elseClause.execute(variables, processedBody);
	}

	@Override
	public String toString() {
		String newline = System.getProperty("line.separator");
		Clause clause = ifClauses.get(0);
		StringBuilder result = new StringBuilder(
				"<<if " + clause.expression + ">>" + newline);
		result.append(clause.statement + newline);
		for (int i = 1; i < ifClauses.size(); i++) {
			clause = ifClauses.get(i);
			result.append("<<elseif " + clause.expression + ">>" + newline);
			result.append(clause.statement + newline);
		}
		if (elseClause != null) {
			result.append("<<else>>" + newline);
			result.append(elseClause + newline);
		}
		result.append("<<endif>>");
		return result.toString();
	}

	public static WoolIfCommand parse(WoolBodyToken cmdStartToken,
			CurrentIterator<WoolBodyToken> tokens, WoolNodeState nodeState)
			throws LineNumberParseException {
		WoolIfCommand command = new WoolIfCommand();
		ReadContentResult content = readCommandContent(cmdStartToken, tokens);
		ParseContentResult parsedIf = parseCommandContentExpression(
				cmdStartToken, content, "if");
		checkNoAssignment(cmdStartToken, parsedIf.name, parsedIf.expression);
		while (true) {
			WoolBodyParser bodyParser = new WoolBodyParser(nodeState);
			WoolBodyParser.ParseUntilIfClauseResult bodyParse =
					bodyParser.parseUntilIfClause(tokens, Arrays.asList(
					"action", "if", "set"));
			if (bodyParse.ifClauseStartToken == null) {
				throw new LineNumberParseException(
						"Command \"if\" not terminated",
						cmdStartToken.getLineNum(), cmdStartToken.getColNum());
			}
			if (parsedIf.name.equals("if") || parsedIf.name.equals("elseif")) {
				command.addIfClause(new Clause(parsedIf.expression,
						bodyParse.body));
			} else {
				command.setElseClause(bodyParse.body);
			}
			WoolBodyToken clauseStartToken = bodyParse.ifClauseStartToken;
			String clauseName = bodyParse.ifClauseName;
			content = readCommandContent(clauseStartToken, tokens);
			switch (clauseName) {
			case "elseif":
				if (command.elseClause != null) {
					throw new LineNumberParseException(
							"Found \"elseif\" after \"else\"",
							clauseStartToken.getLineNum(),
							clauseStartToken.getColNum());
				}
				parsedIf = parseCommandContentExpression(clauseStartToken,
						content, clauseName);
				checkNoAssignment(clauseStartToken, parsedIf.name,
						parsedIf.expression);
				break;
			case "else":
				if (command.elseClause != null) {
					throw new LineNumberParseException(
							"Found more than one \"else\"",
							clauseStartToken.getLineNum(),
							clauseStartToken.getColNum());
				}
				parsedIf = parseCommandContentName(clauseStartToken, content,
						clauseName);
				break;
			case "endif":
				parseCommandContentName(clauseStartToken, content, clauseName);
				return command;
			}
		}
	}
	
	private static void checkNoAssignment(WoolBodyToken cmdStartToken,
			String name, Expression expression)
			throws LineNumberParseException {
		List<Expression> list = new ArrayList<>();
		list.add(expression);
		list.addAll(expression.getDescendants());
		for (Expression expr : list) {
			if (expr instanceof AssignExpression) {
				throw new LineNumberParseException(String.format(
						"Found assignment expression in \"%s\" command", name),
						cmdStartToken.getLineNum(), cmdStartToken.getColNum());
			}
		}
	}

	/**
	 * This class models a clause of an if statement. That is the "if" clause
	 * or an "elseif" clause.
	 */
	public static class Clause {
		private Expression expression;
		private WoolNodeBody statement;

		/**
		 * Constructs a new if clause.
		 * 
		 * @param expression the if expression that should be evaluated as a
		 * boolean
		 * @param statement the statement that should be output if the
		 * expression evaluates to true
		 */
		public Clause(Expression expression, WoolNodeBody statement) {
			this.expression = expression;
			this.statement = statement;
		}

		/**
		 * Returns the if expression that should be evaluated as a boolean.
		 * 
		 * @return the if expression
		 */
		public Expression getExpression() {
			return expression;
		}

		/**
		 * Sets the if expression that should be evaluated as a boolean.
		 * 
		 * @param expression the if expression
		 */
		public void setExpression(Expression expression) {
			this.expression = expression;
		}

		/**
		 * Returns the statement that should be output if the expression
		 * evaluates to true.
		 * 
		 * @return the statement
		 */
		public WoolNodeBody getStatement() {
			return statement;
		}

		/**
		 * Sets the statement that should be output if the expression evaluates
		 * to true.
		 * 
		 * @param statement the statement
		 */
		public void setStatement(WoolNodeBody statement) {
			this.statement = statement;
		}
	}
}