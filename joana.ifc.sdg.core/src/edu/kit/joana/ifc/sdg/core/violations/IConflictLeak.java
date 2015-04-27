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
 * A conflict leak is a violation where information flows through a conflict, i.e.
 * indefinite program behaviour. This conflict is possibly influenced by a source.
 * @author Martin Mohr
 */
public interface IConflictLeak<T> extends IViolation<T> {
	
	/**
	 * @return the trigger of the conflict or {@link Maybe#nothing} if there is no source is available
	 */
	Maybe<T> getTrigger();
	
	/**
	 * @return The conflict leading to this conflict leak
	 */
	ConflictEdge<T> getConflictEdge();
	
	/**
	 * @return the security level which an attacker must have to observe the conflict
	 */
	String getAttackerLevel();
}
