package nl.rrd.wool.execution;

import java.util.List;
import java.util.Map;

import nl.rrd.wool.execution.WoolVariableStore.VariableSource;
import nl.rrd.wool.expressions.EvaluationException;
import nl.rrd.wool.expressions.Value;
import nl.rrd.wool.model.WoolDialogue;
import nl.rrd.wool.model.WoolNode;
import nl.rrd.wool.model.WoolNodeBody;
import nl.rrd.wool.model.WoolReply;
import nl.rrd.wool.model.command.WoolCommand;
import nl.rrd.wool.model.command.WoolInputCommand;
import nl.rrd.wool.model.command.WoolSetCommand;
import nl.rrd.wool.model.nodepointer.WoolNodePointer;
import nl.rrd.wool.model.nodepointer.WoolNodePointerInternal;

/**
 * An {@link ActiveWoolDialogue} is a wrapper around a {@link WoolDialogue}, which contains
 * a static definition of a dialogue (referred to as the {@code dialogueDefinition}). 
 * The {@link ActiveWoolDialogue} also contains utility functions to keep track of the state during 
 * "execution" of the dialogue.
 * 
 * @author Harm op den Akker
 * @author Tessa Beinema
 */
public class ActiveWoolDialogue {
	
	private WoolDialogue dialogueDefinition;
	private WoolNode currentNode;
	private DialogueState dialogueState;
	private WoolVariableStore woolVariableStore;
		
	// ----------- Constructors:
	
	public ActiveWoolDialogue(WoolDialogue dialogueDefinition) {
		this.dialogueDefinition = dialogueDefinition;
		this.dialogueState = DialogueState.INACTIVE;
	}
	
	// ---------- Getters:
	
	public WoolDialogue getDialogueDefinition() {
		return dialogueDefinition;
	}
	
	public WoolNode getCurrentNode() {
		return currentNode;
	}
	
	public DialogueState getDialogueState() {
		return dialogueState;
	}
	
	/**
	 * Returns the {@link WoolVariableStore} associated with this {@link ActiveWoolDialogue}.
	 * @return the {@link WoolVariableStore} associated with this {@link ActiveWoolDialogue}.
	 */
	public WoolVariableStore getWoolVariableStore() {
		return woolVariableStore;
	}
	
	// ---------- Setters:
	
	public void setCurrentNode(WoolNode currentNode) {
		this.currentNode = currentNode;
	}
	
	public void setDialogueState(DialogueState dialogueState) {
		this.dialogueState = dialogueState;
	}
	
	/**
	 * Sets the {@link WoolVariableStore} used to store/retrieve parameters for this {@link ActiveWoolDialogue}.
	 * @param woolVariableStore the {@link WoolVariableStore} used to store/retrieve parameters for this {@link ActiveWoolDialogue}.
	 */
	public void setWoolVariableStore(WoolVariableStore woolVariableStore) {
		this.woolVariableStore = woolVariableStore;
	}
	
	// ---------- Convenience:
	
	/**
	 * Returns the name of this {@link ActiveWoolDialogue} as defined in the associated {@link WoolDialogue}.
	 * @return the name of this {@link ActiveWoolDialogue} as defined in the associated {@link WoolDialogue}.
	 */
	public String getDialogueName() {
		return dialogueDefinition.getDialogueName();
	}
	
	// ---------- Functions:
	
	/**
	 * "Starts" this {@link ActiveWoolDialogue}, returning the start node and updating
	 * its internal state.
	 * @return the initial {@link WoolNode}.
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public WoolNode startDialogue() throws EvaluationException {
		this.dialogueState = DialogueState.ACTIVE;
		WoolNode nextNode = dialogueDefinition.getStartNode();
		this.currentNode = nextNode;
		if(this.currentNode.getBody().getReplies().size() == 0) {
			this.dialogueState = DialogueState.FINISHED;
		}
		return executeWoolNode(nextNode);
	}
	
	/**
	 * "Starts" this {@link ActiveWoolDialogue} at the provided {@WoolNode}, returning that first node
	 * and updating the dialogue's internal state.
	 * @return the first {@link WoolNode}.
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public WoolNode startDialogue(String nodeId) throws EvaluationException {
		this.dialogueState = DialogueState.ACTIVE;
		WoolNode nextNode = dialogueDefinition.getNodeById(nodeId);
		this.currentNode = nextNode;
		if(this.currentNode.getBody().getReplies().size() == 0) {
			this.dialogueState = DialogueState.FINISHED;
		}
		return executeWoolNode(nextNode);
	}
	
	/**
	 * Retrieves the next dialogue and node Ids based on the provided reply id.
	 * @param replyId
	 * @return WoolNodePointer nodePointer to next (dialogue and) node. 
	 */
	public WoolNodePointer processReplyAndGetNodePointer(int replyId)
			throws EvaluationException {
		WoolReply selectedWoolReply = currentNode.getBody().getReplyById(
				replyId);
		Map<String,Object> variableMap = woolVariableStore.getModifiableMap(
				VariableSource.CORE);
		for (WoolCommand command : selectedWoolReply.getCommands()) {
			if (command instanceof WoolSetCommand) {
				WoolSetCommand setCommand = (WoolSetCommand)command;
				setCommand.getExpression().evaluate(variableMap);
			}
		}
		return selectedWoolReply.getNodePointer();
	}
	
	/**
	 * Takes the selected reply and selects the next {@link WoolNode} based on the replies in the current {@link WoolNode}. 
	 * If there is a next node, then returns the executed version of that next {@link WoolNode} which results from a call to the {@link #executeWoolNode(WoolNode)} function. 
	 * @param selectedWoolReply
	 * @return the next {@link WoolNode} that follows on the selected reply.  
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	public WoolNode progressDialogue(WoolNodePointerInternal nodePointer)
			throws EvaluationException {
		WoolNode nextNode = null;
		nextNode = dialogueDefinition.getNodeById(nodePointer.getNodeId());
		if(nextNode != null) {
			this.currentNode = nextNode;
			if(this.currentNode.getBody().getReplies().size() == 0) {
				this.dialogueState = DialogueState.FINISHED;
			}
			this.currentNode = executeWoolNode(nextNode);
			return currentNode;
		} else {
			return null;
		}
	}
	
	/**
	 * The user's client returned the given {@code replyId} - what was the statement that was
	 * uttered by the user?
	 * @param replyId
	 * @return 
	 */
	public String getUserStatementFromReplyId(int replyId) {
		WoolReply selectedReply = currentNode.getBody().getReplyById(replyId);
		if (selectedReply.getStatement() == null)
			return "AUTOFORWARD";
		StringBuilder result = new StringBuilder();
		List<WoolNodeBody.Segment> segments = selectedReply.getStatement()
				.getSegments();
		for (WoolNodeBody.Segment segment : segments) {
			if (segment instanceof WoolNodeBody.TextSegment) {
				WoolNodeBody.TextSegment textSegment =
						(WoolNodeBody.TextSegment)segment;
				result.append(textSegment.getText().evaluate(null));
			} else {
				WoolNodeBody.CommandSegment cmdSegment =
						(WoolNodeBody.CommandSegment)segment;
				// a reply statement can only contain an "input" command
				WoolInputCommand command =
						(WoolInputCommand)cmdSegment.getCommand();
				Value value = new Value(woolVariableStore.getValue(
						command.getVariableName()));
				result.append(value.toString());
			}
		}
		return result.toString();
	}
	
	/**
	 * Executes the WoolNode (i.e. evaluates the command statements and returns a flattened 1-level of statements node).
	 * @param WoolNode a node to execute
	 * @return WoolNode an executed WoolNode (i.e. all ifs and sets etc. are set and evaluated and removed accordingly) 
	 * @throws EvaluationException if an expression cannot be evaluated
	 */
	private WoolNode executeWoolNode(WoolNode woolNode)
			throws EvaluationException {
		WoolNode processedNode = new WoolNode();
		processedNode.setHeader(woolNode.getHeader());
		WoolNodeBody processedBody = new WoolNodeBody();
		Map<String,Object> variables = woolVariableStore.getModifiableMap(
				VariableSource.CORE);
		woolNode.getBody().execute(variables, processedBody);
		processedNode.setBody(processedBody);
		return processedNode;
	}	
}
