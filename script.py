def readRealtimeProcOutput(proc):
	print "Entering readRealTimeProcOutput"
	import sys
	for c in iter(lambda: proc.stdout.read(1), ''):
		sys.stdout.write(c)
		sys.stdout.flush()
	print "Leaving readRealTimeProcOutput"

def runSubProcess(cmd, report_file):
	import time
	import threading
	import os
	import signal

	#print cmd
	proc = PopenBash(cmd)

	t = threading.Thread(target=readRealtimeProcOutput, args = [proc])
	t.daemon = True
	t.start()
	start_time = time.time()
	timeout = 86400 #seconds of a day
	sleep_time = 5 #5 seconds
	seconds_passed = time.time() - start_time
	remaining_time = timeout - seconds_passed
	while proc.poll() is None and remaining_time > 0: # Monitor process
		time.sleep(sleep_time) # Wait a little
		seconds_passed = time.time() - start_time
		remaining_time = timeout - seconds_passed
		if(seconds_passed > 250):
			sleep_time = min(seconds_passed / 50, remaining_time)
		#print seconds_passed
	if(remaining_time <= 0):
		print "Timeout..."
		#print "Identified timeout after: " + str(time.time() - start_time) 
		os.killpg(proc.pid, signal.SIGINT)	
		t.join()
		proc.stdout.close()
		with open(report_file, 'a') as f:
			writeNewLine(f, "")
			writeNewLine(f, "TIMEOUT...")
		returnCode = -1
	else:
		returnCode = proc.returncode
	#proc.communicate()[0]
	print "Java Return Code: " +str(returnCode)

#def runSubProcess(cmd, report_file):
#	import subprocess
#	import sys
#	proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
#	lines = ""
#	makeFiledirs(report_file)
#	with open(report_file, 'w') as f:
#		for line in iter(proc.stdout.readline, ''):
#			lines += line
#			sys.stdout.write(line)
#			f.write(line)
#	return lines

def PopenBash(cmd):
	import subprocess
	import os
	return subprocess.Popen(["/bin/bash","-c", cmd], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, preexec_fn=os.setsid)

def runBuild(buildCmd, report_file):
	import sys
	print "Running build..."
	makeFiledirs(report_file)
	with open(report_file, 'w') as f:
		writeNewLine(f, "Build command used: "+buildCmd)
	proc = PopenBash(buildCmd)
	#lines = ""
	with open(report_file, 'a') as f:
		for c in iter(lambda: proc.stdout.read(1), ''):
			f.write(c)
			#lines += c
	proc.communicate()[0]
	returnCode = proc.returncode
	print "Build Return Code: " +str(returnCode)
	return returnCode == 0
	#return lines

#def checkBuildResult(unf_lines):
#	lines = unf_lines.split("\n")
#	i = len(lines) - 1
#	buildSuc = ["BUILD SUCCESSFUL", "BUILD SUCCESS", "[INFO] BUILD SUCCESS"]
#	buildFail = ["BUILD FAILED", "BUILD FAILURE", "[INFO] BUILD FAILURE"]
#	buildRes = buildSuc + buildFail
#	while i >= 0 and (lines[i] not in buildRes) :
#		i -= 1
#	return i >= 0 and lines[i] in buildSuc

def makeFiledirs(filename):
	import os
	import os.path
	dir = os.path.dirname(filename)
	makedirs(dir)

def readLines(path):
	fil = open(path)
	return fil.read().splitlines()

def writeNewLine(file, content):
	file.write(content + "\n")

def makedirs(dir):
	import os
	import os.path
	if not os.path.exists(dir):
		os.makedirs(dir)

def build(REV_GIT_PATH, REV_REPORTS_PATH, filePref):
	import os.path
	ant = REV_GIT_PATH + "/build.xml"
	gradlew = REV_GIT_PATH + "/gradlew"
	gradle = REV_GIT_PATH + "/build.gradle"
	maven = REV_GIT_PATH + "/pom.xml"
	built = False
	hasGradlew = os.path.exists(gradlew)
	hasGradle = os.path.exists(gradle)
	hasAnt = os.path.exists(ant)
	hasMvn = os.path.exists(maven)
	lastBuildRun = "-"
	if((not built) and hasGradlew):
		print "Run Gradlew build..."
		built = runBuild("chmod +x " + gradlew + " && "+gradlew + " build -p " +REV_GIT_PATH+ " -x test", REV_REPORTS_PATH + "/" + filePref + "build_gradlew.txt")
		lastBuildRun = "Gradle"
		#buildLines = runBuild("chmod +x " + gradlew + " && "+gradlew + " build -p " +REV_GIT_PATH+ " -x test", REV_REPORTS_PATH + "/build_gradlew.txt") 
		#built = checkBuildResult(buildLines)	

	if((not built) and hasGradle):
		print "Run Gradle build..."
		built = runBuild("gradle build -p " +REV_GIT_PATH+ " -x test", REV_REPORTS_PATH + "/" + filePref + "build_gradle.txt")
		lastBuildRun = "Gradle"
		#buildLines = runBuild("gradle build -p " +REV_GIT_PATH+ " -x test", REV_REPORTS_PATH + "/build_gradle.txt") 
		#built = checkBuildResult(buildLines)
	
	if((not built) and hasAnt):
		print "Run Ant build..."
		#built = runBuild("ant build -buildfile "+ REV_GIT_PATH + "/build.xml", REV_REPORTS_PATH + "/build_ant.txt")
		built = runBuild("ant -buildfile "+ REV_GIT_PATH + "/build.xml", REV_REPORTS_PATH + "/" + filePref + "build_ant.txt")
		lastBuildRun = "Ant"
		#buildLines = runBuild("ant build -buildfile "+ REV_GIT_PATH + "/build.xml", REV_REPORTS_PATH + "/build_ant.txt") 
		#built = checkBuildResult(buildLines)

	if((not built) and hasMvn):
		print "Run Maven build..."
		built = runBuild("mvn compile -f "+ REV_GIT_PATH + "/pom.xml", REV_REPORTS_PATH + "/" + filePref + "build_mvn.txt")
		lastBuildRun = "Maven"
		#buildLines = runBuild("mvn compile -f "+ REV_GIT_PATH + "/pom.xml", REV_REPORTS_PATH + "/build_mvn.txt") 
		#built = checkBuildResult(buildLines)	

	if(not(built)):
		lastBuildRun = "-"
	
	return str(built) + "; " + str(hasGradlew or hasGradle) + "; " + str(hasAnt) + "; " + str(hasMvn) + "; " + lastBuildRun

def exceptionToStr(ignoreExceptions):
	if ignoreExceptions == "true":
		return "noExcep"
	else:
		return "excep"

def run_joana(REV_GIT_PATH, REV_REPORTS_PATH, REV_SDGS_PATH, revContribs, heapStr, libPaths):
	print "Running Joana..."
	import sys
	import os.path
	baseCmd = "nohup java " + heapStr + " -jar joana_inv.jar \"" + REV_GIT_PATH + "\" \""+ REV_REPORTS_PATH + "\" \"" + REV_SDGS_PATH + "\""
	baseCmd += " \'" +revContribs + "\'"
	baseCmd += " \"" +libPaths + "\""
	#print baseCmd
	ignoreExceptions=["true", "false"]
	initialExceptionMsg = "ignoreExceptions="
	initialPrecisionMsg = "initialPrecision="
	precisions = ["TYPE_BASED", "INSTANCE_BASED","OBJECT_SENSITIVE", "N1_OBJECT_SENSITIVE", 
		"UNLIMITED_OBJECT_SENSITIVE", "N1_CALL_STACK", "N2_CALL_STACK", "N3_CALL_STACK"]
	precisionsIds = xrange(8)#[0,1,2,3,4,5,6,7]
	if(os.path.exists(REV_REPORTS_PATH + "/executionSummary.csv")):
		open(REV_REPORTS_PATH + "/executionSummary.csv","w").close()
	for ignoreException in ignoreExceptions:
		cmde = baseCmd + " \"" + initialExceptionMsg + ignoreException + "\""
		print "Ignore Exceptions: "+str(ignoreException)
		for i in precisionsIds:
			cmd = cmde + " \""	+ initialPrecisionMsg + str(i) + "\"" 
			print "Precision: "+precisions[i]
			sys.stdout.flush()			
			sysout_path = REV_REPORTS_PATH + "/" + precisions[i] + "_" +exceptionToStr(ignoreException) + "_sysout.txt"
			if(os.path.exists(sysout_path)):
				open(sysout_path, "w").close()
			else:
				makeFiledirs(sysout_path)
			runSubProcess(cmd + " > "+sysout_path, sysout_path)
			sys.stdout.flush()

def getRevContribs(contribs, rev):
	revContribs=[]
	for contrib in contribs:
		splittedContrib = contrib.split("; ")
		if len(splittedContrib) > 1:
			currentRev = splittedContrib[1]
			if currentRev == rev:
				revContribs.append(contrib)
	return '\n'.join(revContribs)

def checkIfIsInYearRange(yearRange, revHasContrib, revContribs):
	isInYearRange = len(yearRange) != 2 or (yearRange[0] == "" and yearRange[1] == "")
	if(revHasContrib and (not(isInYearRange))):
			startYear = yearRange[0]
			endYear = yearRange[1]
			fullDate = revContribs.split("\n")[0].split("; ")[2]
			strLen = len(fullDate)
			yearStr = fullDate[(strLen - 4):strLen]
			year = int(yearStr)
			isInYearRange = ((startYear == "" or year >= int(startYear)) and (endYear == "" or year <= int(endYear)))
	return isInYearRange

def getHeapComplement(path):
	if path[:27] == "/home/local/CIN/rsmbf/rsmbf":
		comp = "-Xms80g -Xmx120g"#"-Xms128g -Xmx192g"
	else:
		comp = "-Xms1g -Xmx2g" # "-Xms1g -Xmx2g" #"-Xms4m -Xmx8m"
	return comp

def runJoanaForSpecificRevs():
	import os
	currDir = os.getcwd()
	CA_PATH = currDir + "/conflicts_analyzer"
	heapStr = getHeapComplement(currDir)
	DOWNLOAD_PATH = CA_PATH + "/downloads"
	REPORTS_PATH = CA_PATH + "/reports"
	SDGS_PATH = CA_PATH + "/sdgs"
	revList = readLines(CA_PATH + "/revList")
	for revLine in revList:
		revLineSplitted = revLine.split(",")
		project = revLineSplitted[0].strip()
		PROJECT_REPORTS_PATH = REPORTS_PATH + "/" + project
		PROJECT_SDGS_PATH = SDGS_PATH + "/" + project
		PROJECT_PATH = DOWNLOAD_PATH + "/" +project
		revBaseStr = "rev"
		revStr = revLineSplitted[1].strip()
		rev = revBaseStr + "_" + revStr
		splittedRev = revStr.split("_")
		left = splittedRev[0].strip()
		right = splittedRev[1].strip()
		inner_rev = revBaseStr + "_" + left + "-" + right
		ES_MC_PATH = PROJECT_PATH + "/editsamemc_revisions"
		REV_GIT_PATH = ES_MC_PATH + "/" + rev + "/" + inner_rev + "/git"
		print REV_GIT_PATH
		project_contribs = readLines(PROJECT_REPORTS_PATH + "/editSameMCcontribs.csv")
		revContribs = getRevContribs(project_contribs, inner_rev)
		#print revContribs
		REV_REPORTS_PATH = PROJECT_REPORTS_PATH + "/" + rev
		REV_SDGS_PATH = ""#PROJECT_SDGS_PATH + "/" + rev
		libStr = ""
		if(len(revLineSplitted) >= 3):
			libStr = revLineSplitted[2].strip()
		run_joana(REV_GIT_PATH, REV_REPORTS_PATH, REV_SDGS_PATH, revContribs, heapStr, libStr)

def main():
	build_all = True
	build_rev_merged = True
	build_rev_ss = True
	import os
	import os.path
	currDir = os.getcwd()
	CA_PATH = currDir + "/conflicts_analyzer"
	heapStr = getHeapComplement(currDir)
	DOWNLOAD_PATH = CA_PATH + "/downloads"
	REPORTS_PATH = CA_PATH + "/reports"
	SDGS_PATH = CA_PATH + "/sdgs"
	projectList = readLines(CA_PATH + "/projectsList")
	yearRangeFil = CA_PATH + "/yearRange"
	yearRangeFilExists = os.path.exists(yearRangeFil)
	yearRange = ["",""]
	if(yearRangeFilExists):
		yearLines = readLines(yearRangeFil)
		if len(yearLines) > 0:
			yearRangeStr = yearLines[0]
			yearRange = yearRangeStr.split("-")
	for project in projectList: 
	   project_name = project.split("/")[1]
	   PROJECT_PATH = DOWNLOAD_PATH + "/" +project_name
	   projectExists = os.path.exists(PROJECT_PATH)
	   print PROJECT_PATH + " ProjectExists: " +str(projectExists)
	   if projectExists:
		   PROJECT_REPORTS_PATH = REPORTS_PATH + "/" + project_name
		   PROJECT_SDGS_PATH = SDGS_PATH + "/" + project_name
		   ES_MC_PATH = PROJECT_PATH + "/editsamemc_revisions"
		   projectHasEditSameMC = os.path.exists(ES_MC_PATH)
		   print ES_MC_PATH + " ProjectHasEditSameMC: "+str(projectHasEditSameMC)
		   if projectHasEditSameMC:
			   revs = [name for name in os.listdir(ES_MC_PATH)
			            if os.path.isdir(os.path.join(ES_MC_PATH, name))]
			   revsSize = len(revs)
			   if revsSize > 0:
			   		if build_rev_ss: 
				   		buildSummaryPath = PROJECT_REPORTS_PATH + "/buildSummary.csv"
				   		makeFiledirs(buildSummaryPath)
				   		buildSummary = open(buildSummaryPath, "w", 0)
				   		writeNewLine(buildSummary, "Rev; Built; Gradle; Ant; Mvn; Built with")
				   	if build_rev_merged:
				   		buildSummaryPathMerge = PROJECT_REPORTS_PATH + "/buildSummaryMerge.csv"
				   		makeFiledirs(buildSummaryPathMerge)
				   		buildSummaryMerge = open(buildSummaryPathMerge, "w", 0)
				   		writeNewLine(buildSummaryMerge, "Rev; Built; Gradle; Ant; Mvn; Built with")
			   		project_contribs = readLines(PROJECT_REPORTS_PATH + "/editSameMCcontribs.csv")
			   for rev in revs:
			   		splittedRev = rev.split("_")
			   		left = splittedRev[1]
			   		right = splittedRev[2]
			   		inner_rev = splittedRev[0] + "_" + left + "-" + right
			   		REV_GIT_PATH = ES_MC_PATH + "/" + rev + "/" + inner_rev + "/git"
			   		print REV_GIT_PATH
			   		revContribs = getRevContribs(project_contribs, inner_rev)
			   		print "Contrib: " +revContribs
			   		revHasContrib = not(revContribs == '')
			   		print "Rev has contrib: "+str(revHasContrib)
			   		isInYearRange = checkIfIsInYearRange(yearRange, revHasContrib, revContribs)
			   		print "Is in year range: "+str(isInYearRange)
			   		shouldRunJoana = revHasContrib and isInYearRange
			   		print "Should Run Joana: "+str(shouldRunJoana)
			   		if (build_all or shouldRunJoana):		
			   			REV_REPORTS_PATH = PROJECT_REPORTS_PATH + "/" + rev
			   			built = not(build_rev_ss)
			   			if build_rev_ss:
			   				buildRes = build(REV_GIT_PATH, REV_REPORTS_PATH, "")
			   				built = buildRes.split(";")[0] == "True"
			   				writeNewLine(buildSummary, rev + "; "+buildRes)			   		
			   				print "Build Result: "+str(built)
			   			if build_rev_merged:
			   				REV_GITM_PATH = ES_MC_PATH + "/" + rev + "/rev_merged_git/git"
			   				buildResM = build(REV_GITM_PATH, REV_REPORTS_PATH, "merge_")
			   				builtM = buildResM.split(";")[0] == "True"
			   				writeNewLine(buildSummaryMerge, rev + "; "+buildResM)			   		
			   				print "Build Result merge: "+str(builtM)
			   			if built and shouldRunJoana:
				   			REV_SDGS_PATH = ""#PROJECT_SDGS_PATH + "/" + rev
				   			#run_joana(REV_GIT_PATH, REV_REPORTS_PATH, REV_SDGS_PATH, revContribs, heapStr, "")

#main()
runJoanaForSpecificRevs()
