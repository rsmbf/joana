/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.api.sdg;


/**
 * This class represents the node which corresponds to the return node of a call. It is
 * not to be confused with a CALL_RET node.
 * @author Martin Mohr
 */
public class SDGCallReturnNode implements SDGCallPart {

	private SDGCall owningCall;

	/**
	 * @param node
	 * @param owningMethod
	 */
	public SDGCallReturnNode(SDGCall owningCall) {
		this.owningCall = owningCall;
	}

	@Override
	public SDGCall getOwningCall() {
		return owningCall;
	}

	/* (non-Javadoc)
	 * @see edu.kit.joana.api.sdg.SDGProgramPart#acceptVisitor(edu.kit.joana.api.sdg.SDGProgramPartVisitor, java.lang.Object)
	 */
	@Override
	public <R, D> R acceptVisitor(SDGProgramPartVisitor<R, D> v, D data) {
		return v.visitCallReturnNode(this, data);
	}

	@Override
	public String toString() {
		return owningCall.toString() + "->ret";
	}

	/* (non-Javadoc)
	 * @see edu.kit.joana.api.sdg.SDGCallPart#acceptVisitor(edu.kit.joana.api.sdg.SDGCallPartVisitor)
	 */
	@Override
	public void acceptVisitor(SDGCallPartVisitor v) {
		v.visitReturnNode(this);
	}

	/* (non-Javadoc)
	 * @see edu.kit.joana.api.sdg.SDGProgramPart#getOwningMethod()
	 */
	@Override
	public SDGMethod getOwningMethod() {
		return owningCall.getOwningMethod();
	}

}
