package execResult;

import java.util.List;
import java.util.Map;

public class SingleExecutionResult extends ExecutionResult {
	private Map<String, List<Integer>> left;
	private Map<String, List<Integer>> right;
	public SingleExecutionResult(SdgConfigValues config, Map<String, List<Integer>> left, Map<String, List<Integer>> right) {
		super(config);
		this.left = left;
		this.right = right;
	}
	
}
