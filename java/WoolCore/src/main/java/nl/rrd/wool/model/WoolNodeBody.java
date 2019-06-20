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

package nl.rrd.wool.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.rrd.wool.expressions.EvaluationException;
import nl.rrd.wool.model.command.WoolActionCommand;
import nl.rrd.wool.model.command.WoolCommand;
import nl.rrd.wool.model.command.WoolIfCommand;
import nl.rrd.wool.model.command.WoolInputCommand;
import nl.rrd.wool.model.command.WoolSetCommand;
import nl.rrd.wool.model.nodepointer.WoolNodePointer;

/**
 * A node body can occur in three different contexts inside a {@link WoolNode
 * WoolNode}.
 * 
 * <p><ul>
 * <li>Directly in the node. In this case it specifies the agent statement with
 * possible commands and user replies.</li>
 * <li>As part of a clause in a {@link WoolIfCommand WoolIfCommand}. The content
 * is the same as directly in the node. The only difference is that it is
 * performed conditionally.</li>
 * <li>As part of a {@link WoolReply WoolReply}. In this case it specifies the
 * user statement with possible commands, but no replies. Note that the UI shows
 * these statements as options immediately along with the agent statement. This
 * {@link WoolNodeBody WoolNodeBody} does not contain commands that are to be
 * performed when the reply is chosen. Such commands are specified separately in
 * a {@link WoolReply WoolReply}.</li>
 * </ul></p>
 * 
 * <p>The body contains a statement as a list of segments where each segment is
 * one of:</p>
 * 
 * <p><ul>
 * <li>{@link WoolNodeBody.TextSegment TextSegment}: a {@link WoolVariableString
 * WoolVariableString} with text and variables</li>
 * <li>{@link WoolNodeBody.CommandSegment CommandSegment}: a command (see
 * below)</li>
 * </ul></p>
 * 
 * <p>The segments are always normalized so that subsequent text segments are
 * automatically merged into one.</p>
 * 
 * <p>The type of commands depend on the context. Directly in the node or in a
 * {@link WoolIfCommand WoolIfCommand}, it can be:</p>
 * 
 * <p><ul>
 * <li>{@link WoolActionCommand WoolActionCommand}: Actions to perform along
 * with the agent's text statement.</li>
 * <li>{@link WoolIfCommand WoolIfCommand}: Contains clauses, each with a {@link
 * WoolNodeBody WoolNodeBody} specifying conditional statements, replies and
 * commands.</li>
 * <li>{@link WoolSetCommand WoolSetCommand}: Sets a variable value.</li>
 * </ul></p>
 * 
 * <p>As part of a reply (remember the earlier remarks about commands in a
 * reply), it can be:</p>
 * 
 * <p><ul>
 * <li>{@link WoolInputCommand WoolInputCommand}: Allow user to provide input
 * other than just clicking the reply option.</li>
 * </ul></p>
 * 
 * @author Dennis Hofs (RRD)
 */
public class WoolNodeBody {
	private List<Segment> segments = new ArrayList<>();
	private List<WoolReply> replies = new ArrayList<>();
	
	/**
	 * Returns the segments as an unmodifiable list.
	 * 
	 * @return the segments as an unmodifiable list
	 */
	public List<Segment> getSegments() {
		return Collections.unmodifiableList(segments);
	}

	public void addSegment(Segment segment) {
		Segment lastSegment = null;
		if (!segments.isEmpty())
			lastSegment = segments.get(segments.size() - 1);
		if (lastSegment instanceof TextSegment &&
				segment instanceof TextSegment) {
			TextSegment lastTextSegment = (TextSegment)lastSegment;
			TextSegment textSegment = (TextSegment)segment;
			WoolVariableString text = new WoolVariableString();
			text.addSegments(lastTextSegment.text.getSegments());
			text.addSegments(textSegment.text.getSegments());
			TextSegment mergedSegment = new TextSegment(text);
			segments.remove(segments.size() - 1);
			segments.add(mergedSegment);
		} else {
			segments.add(segment);
		}
	}

	public List<WoolReply> getReplies() {
		return replies;
	}
	
	public WoolReply getReplyById(int replyId) {
		for (WoolReply reply : replies) {
			if (reply.getReplyId() == replyId)
				return reply;
		}
		return null;
	}

	public void addReply(WoolReply reply) {
		replies.add(reply);
	}
	
	/**
	 * Retrieves all variable names that are read in this body and adds them to
	 * the specified set.
	 * 
	 * @param varNames the set to which the variable names are added
	 */
	public void getReadVariableNames(Set<String> varNames) {
		for (Segment segment : segments) {
			segment.getReadVariableNames(varNames);
		}
		for (WoolReply reply : replies) {
			reply.getReadVariableNames(varNames);
		}
	}
	
	/**
	 * Executes the statement in this body with respect to the specified
	 * variable map. It executes commands and resolves variables. Any resulting
	 * body content that should be sent to the client, is added to
	 * "processedBody". This content can be text or client commands, with all
	 * variables resolved.
	 * 
	 * @param variables the variable map
	 * @param processedBody the processed body
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public void execute(Map<String,Object> variables,
			WoolNodeBody processedBody) throws EvaluationException {
		for (Segment segment : segments) {
			if (segment instanceof TextSegment) {
				executeTextSegment((TextSegment)segment, variables,
						processedBody);
			} else {
				executeCommandSegment((CommandSegment)segment, variables,
						processedBody);
			}
		}
		for (WoolReply reply : replies) {
			processedBody.addReply(reply.execute(variables));
		}
	}
	
	private void executeTextSegment(TextSegment segment,
			Map<String,Object> variables, WoolNodeBody processedBody) {
		TextSegment processedText = new TextSegment(
				segment.text.execute(variables));
		processedBody.addSegment(processedText);
	}
	
	private void executeCommandSegment(CommandSegment segment,
			Map<String,Object> variables, WoolNodeBody processedBody)
			throws EvaluationException {
		segment.command.executeBodyCommand(variables, processedBody);
	}
	
	public List<WoolNodePointer> getNodePointers() {
		List<WoolNodePointer> result = new ArrayList<>();
		for (WoolReply reply : replies) {
			result.add(reply.getNodePointer());
		}
		return result;
	}

	public void trimWhitespace() {
		removeLeadingWhitespace();
		removeTrailingWhitespace();
	}

	public void removeLeadingWhitespace() {
		while (!segments.isEmpty()) {
			Segment segment = segments.get(0);
			if (!(segment instanceof TextSegment))
				return;
			TextSegment textSegment = (TextSegment)segment;
			WoolVariableString text = textSegment.getText();
			text.removeLeadingWhitespace();
			if (!text.getSegments().isEmpty())
				return;
			segments.remove(0);
		}
	}

	public void removeTrailingWhitespace() {
		while (!segments.isEmpty()) {
			Segment segment = segments.get(segments.size() - 1);
			if (!(segment instanceof TextSegment))
				return;
			TextSegment textSegment = (TextSegment)segment;
			WoolVariableString text = textSegment.getText();
			text.removeTrailingWhitespace();
			if (!text.getSegments().isEmpty())
				return;
			segments.remove(segments.size() - 1);
		}
	}
	
	@Override
	public String toString() {
		String newline = System.getProperty("line.separator");
		StringBuilder builder = new StringBuilder();
		for (Segment segment : segments) {
			builder.append(segment.toString());
		}
		for (WoolReply reply : replies) {
			builder.append(newline);
			builder.append(reply);
		}
		return builder.toString();
	}

	public static abstract class Segment {

		/**
		 * Retrieves all variable names that are read in this segment and adds
		 * them to the specified set.
		 * 
		 * @param varNames the set to which the variable names are added
		 */
		public abstract void getReadVariableNames(Set<String> varNames);
	}
	
	public static class TextSegment extends Segment {
		private WoolVariableString text;
		
		public TextSegment(WoolVariableString text) {
			this.text = text;
		}

		public WoolVariableString getText() {
			return text;
		}

		public void setText(WoolVariableString text) {
			this.text = text;
		}
		
		@Override
		public void getReadVariableNames(Set<String> varNames) {
			text.getReadVariableNames(varNames);
		}

		@Override
		public String toString() {
			return text.toString();
		}
	}
	
	public static class CommandSegment extends Segment {
		private WoolCommand command;
		
		public CommandSegment(WoolCommand command) {
			this.command = command;
		}

		public WoolCommand getCommand() {
			return command;
		}
		
		@Override
		public void getReadVariableNames(Set<String> varNames) {
			command.getReadVariableNames(varNames);
		}
		
		@Override
		public String toString() {
			return command.toString();
		}
	}
}