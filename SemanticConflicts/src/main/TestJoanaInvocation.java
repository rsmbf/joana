package main;

import java.io.IOException;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

public class TestJoanaInvocation {
	public static void main(String[] args) throws ClassHierarchyException, ClassNotFoundException, IOException, UnsoundGraphException, CancelException {
		JoanaInvocation.main(new String[]{
				//"/Users/Roberto/Documents/UFPE/Msc/Projeto/conflicts_analyzer/TestFlows/","1"
				"/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/", "1"
				//"/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/rsmbf/","0"
		});
	}
}
