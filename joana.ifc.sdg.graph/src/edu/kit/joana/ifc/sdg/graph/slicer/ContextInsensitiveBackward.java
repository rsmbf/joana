/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.ifc.sdg.graph.slicer;

import java.util.Collection;
import java.util.Set;

import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGEdge;
import edu.kit.joana.ifc.sdg.graph.SDGNode;


/**
 * @author giffhorn
 *
 */
public class ContextInsensitiveBackward extends ContextInsensitiveSlicer {

    public ContextInsensitiveBackward(SDG g, Set<SDGEdge.Kind> omit) {
        super(g, omit);
    }

    public ContextInsensitiveBackward(SDG g) {
        super(g);
    }

    protected Collection<SDGEdge> edgesToTraverse(SDGNode node) {
        return this.g.incomingEdgesOf(node);
    }

    protected SDGNode reachedNode(SDGEdge edge) {
        return edge.getSource();
    }
}
