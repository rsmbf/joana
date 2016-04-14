package main;

import java.util.HashMap;
import java.util.Map;

import edu.kit.joana.api.sdg.SDGCallPart;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.core.violations.IIllegalFlow;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import gnu.trove.map.TObjectIntMap;

public class BothAffect {
	private static SDGProgramPart getSink(SDGProgramPart sink)
	{
		SDGProgramPart filteredSink;
		if(sink instanceof SDGCallPart){
			filteredSink = ((SDGCallPart) sink).getOwningCall();
		}else{
			filteredSink = sink;
		}
		return filteredSink;
	}
		
	private static Map<SDGProgramPart, InterferencePoint> getAllInterferencesBySink(Map<SDGProgramPart, Integer> parts_map,
			TObjectIntMap<IViolation<SDGProgramPart>> leftViosByProgramPart,
			TObjectIntMap<IViolation<SDGProgramPart>> rightViosByProgramPart)
			{
		Map<SDGProgramPart, InterferencePoint> interferencePoints = new HashMap<SDGProgramPart, InterferencePoint>();
		for(IViolation<SDGProgramPart> leftVio : leftViosByProgramPart.keySet())
		{
			IIllegalFlow<SDGProgramPart> leftFlow = (IIllegalFlow) leftVio;

			InterferencePoint interferencePoint;
			SDGProgramPart sink = leftFlow.getSink();
			if(interferencePoints.containsKey(sink))
			{
				interferencePoint = interferencePoints.get(sink);
			}else{
				interferencePoint = new InterferencePoint(sink, parts_map);
				interferencePoints.put(sink, interferencePoint);
			}
			interferencePoint.addSourceLeft(leftFlow.getSource());
		}

		for(IViolation<SDGProgramPart> rightVio : rightViosByProgramPart.keySet())
		{
			IIllegalFlow<SDGProgramPart> rightFlow = (IIllegalFlow) rightVio;
			InterferencePoint interferencePoint;
			SDGProgramPart sink = rightFlow.getSink();
			if(interferencePoints.containsKey(sink))
			{
				interferencePoint = interferencePoints.get(sink);
			}else{
				interferencePoint = new InterferencePoint(sink, parts_map);
				interferencePoints.put(sink, interferencePoint);
			}
			interferencePoint.addSourceRight(rightFlow.getSource());
		}
		return interferencePoints;
			}
	
	private static Map<Integer, LineInterferencesPoints> getAllInterferencesByLine(Map<SDGProgramPart, Integer> parts_map,
			TObjectIntMap<IViolation<SDGProgramPart>> leftViosByProgramPart,
			TObjectIntMap<IViolation<SDGProgramPart>> rightViosByProgramPart)
	{
		Map<SDGProgramPart, InterferencePoint> interferencePointsBySink = getAllInterferencesBySink(parts_map, leftViosByProgramPart, rightViosByProgramPart);
		Map<Integer, LineInterferencesPoints> interferencePointsByLine = new HashMap<Integer, LineInterferencesPoints>();
		for(SDGProgramPart sink : interferencePointsBySink.keySet())
		{
			LineInterferencesPoints lineInterferencePoint;
			int sinkLine = parts_map.get(getSink(sink));
			if(interferencePointsByLine.containsKey(sinkLine))
			{
				lineInterferencePoint = interferencePointsByLine.get(sinkLine);
			}else{
				lineInterferencePoint = new LineInterferencesPoints();
				interferencePointsByLine.put(sinkLine, lineInterferencePoint);
			}
			lineInterferencePoint.addNewInterference(sink, interferencePointsBySink.get(sink).getSourcesLeftMap(), interferencePointsBySink.get(sink).getSourcesRightMap());
		}
		return interferencePointsByLine;
	}
	
	public static Map<Integer, LineInterferencesPoints> getInterferencesByLine(Map<SDGProgramPart, Integer> parts_map,
			TObjectIntMap<IViolation<SDGProgramPart>> leftViosByProgramPart,
			TObjectIntMap<IViolation<SDGProgramPart>> rightViosByProgramPart){
		Map<Integer, LineInterferencesPoints> allInterferencesByLine = getAllInterferencesByLine(parts_map, leftViosByProgramPart, rightViosByProgramPart);
		Map<Integer, LineInterferencesPoints> interferencesByLine = new HashMap<Integer, LineInterferencesPoints>();
		for(Integer line : allInterferencesByLine.keySet())
		{
			if(!allInterferencesByLine.get(line).getSourcesLeftMap().isEmpty() && !allInterferencesByLine.get(line).getSourcesRightMap().isEmpty())
			{
				interferencesByLine.put(line, allInterferencesByLine.get(line));
			}
		}
		return interferencesByLine;
	}

}
