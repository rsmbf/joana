/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.ifc.sdg.core.violations;

import edu.kit.joana.util.Maybe;

/**
 * @author Martin Mohr
 */
public abstract class AbstractConflictLeak<T> implements IConflictLeak<T> {
	
	protected final ConflictEdge<T> confEdge;
	protected final String attackerLevel;
	protected final Maybe<T> trigger;
	
	public AbstractConflictLeak(ConflictEdge<T> confEdge, String attackerLevel) {
		this(confEdge, attackerLevel, Maybe.<T>nothing());
	}
	
	public AbstractConflictLeak(ConflictEdge<T> confEdge, String attackerLevel, Maybe<T> trigger) {
		this.confEdge = confEdge;
		this.attackerLevel = attackerLevel;
		this.trigger = trigger;
	}
	
	public ConflictEdge<T> getConflictEdge() {
		return confEdge;
	}
	
	public Maybe<T> getTrigger() {
		return trigger;
	}

}
