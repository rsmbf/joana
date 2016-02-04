package main;

import java.util.Collection;

import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.core.SecurityNode;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import gnu.trove.map.TObjectIntMap;

public class ViolationResult {
	private Collection<? extends IViolation<SecurityNode>> result;
	private TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart;
	
	public ViolationResult(Collection<? extends IViolation<SecurityNode>> result, TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart)
	{
		this.result = result;
		this.resultByProgramPart = resultByProgramPart;
	}

	public Collection<? extends IViolation<SecurityNode>> getResult() {
		return result;
	}

	public TObjectIntMap<IViolation<SDGProgramPart>> getResultByProgramPart() {
		return resultByProgramPart;
	}
}
