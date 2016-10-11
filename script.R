root <- "~/Documents/UFPE/Msc/Projeto/projects/rsmbf/conflicts_analyzer"
server_reports <- paste(root, "/server_reports/19-09", sep="") #""#"/server_reports/1"
rDir <- paste(server_reports, "/R", sep="")
plots <- paste(rDir, "/plots",sep="")
result_data <- paste(server_reports, "/ResultData", sep="")
reports <- paste(server_reports, "/reports", sep="")
projects <- scan(file = paste(server_reports, "/projectsList", sep=""), what = "character")
precisions <- c("TYPE_BASED", "INSTANCE_BASED","N1_OBJECT_SENSITIVE", "OBJECT_SENSITIVE", 
                "UNLIMITED_OBJECT_SENSITIVE", "N1_CALL_STACK", "N2_CALL_STACK", "N3_CALL_STACK")
phase2Precisions <- c("TYPE_BASED", "INSTANCE_BASED","N1_OBJECT_SENSITIVE", "OBJECT_SENSITIVE", 
                      "N1_CALL_STACK", "N2_CALL_STACK", "N3_CALL_STACK")
exceptions <- c("Yes", "No")
phase2Exceptions <- c("No")
labelsList <- list(CGNodes=c("CG Nodes"), CGEdges=c("CG Edges"), 
                   SDGNodes=c("SDG Nodes"), SDGEdges=c("SDG Edges"),
                   LineVios=c("Line Violations", "Line Vios"), SdgCreated=c("Sdg Created (%)"))
total_projects <- length(projects)
skipPhase1 <- TRUE
skipPhase2 <- FALSE

getPrettyPrecision <- function(prec){
  prettyNames=c("TYPE_BASED"="Type_Based", "INSTANCE_BASED" = "Instance_Based","OBJECT_SENSITIVE" = "Object_Sensitive", 
                "N1_OBJECT_SENSITIVE" = "N1_Object_Sensitive", 
                "UNLIMITED_OBJECT_SENSITIVE" = "Unlimited_Object_Sensitive", 
                "N1_CALL_STACK" = "N1_Call_Stack", 
                "N2_CALL_STACK" = "N2_Call_Stack",
                "N3_CALL_STACK"="N3_Call_Stack")
  return(prettyNames[[prec]])
}

appendToList <- function(list, el){
  len <- length(list) + 1
  list[[len]] <- el
  return(list)
}

insertLineToDf <- function(df, elems){
  line <- nrow(df) + 1
  for(i in getPositiveRange(length(elems)))
  {
    df[line,i] <- elems[i]  
  }
  return(df)
}

strToBoolean <- function(str)
{
  return(str == "Yes")
}

strsToBoolean <- function(str, str2){
  ret <- NA 
  if(!is.na(str) && !is.na(str2))
  {
    ret <- strToBoolean(str) || strToBoolean(str2)
  }
  return(ret)
}

strToNumeric <- function(str)
{
  ret <- NA
  if(str == "Yes")
  {
    ret <- 1
  }else if(str == "No")
  {
    ret <- 0
  }
  return(ret)
}

strsToNumeric <- function(str, str2)
{
  ret <- NA
  if(!is.na(str) && !is.na(str2))
  {
    ret <- strToNumeric(str) + strToNumeric(str2)
  }
  return(ret)
}

mkdirs <- function(dir)
{
  if(!file.exists(dir))
  {
    dir.create(dir, recursive=TRUE)
  }
}

getNRow <- function(df)
{
  ret <- 0
  if(!is.null(df))
  {
    ret <- nrow(df)
  }
  return(ret)
}

getPositiveRange <- function(size)
{
  range <- list()
  if(size > 0){
    range <- 1:size
  }
  return(range)
}

filterDesiredConfigs <- function(desiredExceptions, desiredPrecisions, frame)
{
  filtFrame <- data.frame(frame[0,], stringsAsFactors=FALSE)
  filtRows <- 0
  for(row in getPositiveRange(getNRow(frame))){
    if(frame[row,'Precision'] %in% desiredPrecisions && frame[row,'Exception'] %in% desiredExceptions)
    {
      filtRows <- filtRows + 1
      filtFrame[filtRows,] <- frame[row,]
    }
  }
  return(filtFrame)
}

filterConfigsWithoutNa <- function(toEvaluate, critList, frame, numElems)
{
  redFrame <- data.frame(frame[critList])
  elems <- getNRow(unique(redFrame))
  filtFrame <- frame[0,]
  rowNum <- 1
  for(i in getPositiveRange(elems)){
    start <- ((i - 1) * numElems) + 1
    sum <- numElems - 1
    end <- start + sum
    rows <- frame[start:end,]
    reducedList <- rows[[toEvaluate]]
    if(all(!is.na(reducedList)))
    {
      filtEnd <- rowNum + sum
      filtFrame[rowNum:filtEnd,] <- rows
      rowNum <- filtEnd + 1
    }
  }
  
  return(filtFrame)
}

calculateAllStatistics <- function(filteredRevDfsList, filteredMethodDfsList)
{
  revsStats <- c()
  for(toEvaluate in toEvaluateList)
  {
    filteredRevDfs <- filteredRevDfsList[[toEvaluate]]
    revsStats[[toEvaluate]] <- calculateExceptionsStatistics(toEvaluate, filteredRevDfs)
  }
  filteredMethodsDfs <- filteredMethodDfsList[['LineVios']]
  methStats <- calculateExceptionsStatistics('LineVios', filteredMethodsDfs)
  return(list("Rev"=revsStats, "Method"=c("LineVios"=methStats)))
}

calculateExceptionsStatistics <- function(toEvaluate,filteredDf){
  splittedFiltDf <- split(filteredDf, filteredDf$Exception)
  splittedYesFiltDf <- splittedFiltDf$Yes
  splittedNoFiltDf <- splittedFiltDf$No
  yesStatistics <- calculateStatistics(splittedYesFiltDf[[toEvaluate]])
  noStatistics <- calculateStatistics(splittedNoFiltDf[[toEvaluate]])
  return(list('Yes'=yesStatistics, 'No'=noStatistics))
}

calculateStatistics <- function(group)
{
  mean <- mean(group)
  median <- median(group)
  sd <- sd(group)
  return(list('mean'=mean, 'median'=median, 'sd'=sd))
}

generatePrecisionsBarplot <- function(list, plotType, toEvaluate, excepFileName, namesList, exception, phase="1")
{
  excepsTitlePart <- c(Yes="with", No="without")
  dir <- paste(plots, "/phase", phase, "/barplots/precisions/",toEvaluate, "/", sep="")
  mkdirs(dir)
  jpeg(paste(dir, plotType, "_PrecisionsPlot_", toEvaluate, "_", excepFileName,".jpg", sep = ""))
  par(mar = c(10,5.2,2,0.7) + 0.1,las=2)
  barplot(list, names.arg = namesList,
          cex.names=0.9,col=c("darkblue"), main=paste("Number of creations for SDGs ",excepsTitlePart[[exception]]," exceptions (",plotType,")",sep=""))
  mtext("Pointer Analysis",side=1, cex=1.4, font=2, line=8, las=0)
  mtext("SDG Creations",side=2, cex=1.4, font=2,line=3, las=0)
  dev.off()
}

generateGroupedPrecisionsBarplot <- function(frame, colType, elemList, plotType, toEvaluate, excepFileName, namesList, exception, phase = "1")
{
  excepsTitlePart <- c(Yes="with", No="without")
  m <- createMatrix(frame, colType, elemList)
  dir <- paste(plots, "/phase", phase, "/barplots/precisions/",toEvaluate, "/", sep="")
  mkdirs(dir)
  jpeg(paste(dir, plotType, "_PrecisionsPlot_", toEvaluate, "_", excepFileName,".jpg", sep = ""))
  par(mar = c(10,5.2,2,0.7) + 0.1,las=2)
  barplot(m, names.arg = namesList, beside=TRUE,
          cex.names=0.9,ylim=c(0,100),col=c("green","red"), main=paste("IF occurrence for SDGs ",excepsTitlePart[[exception]]," exceptions ","(",plotType,")",sep=""))
  legend("bottomleft",legend=c("Flow","No flow"),inset=c(-0.17,-0.45),xpd=TRUE,fill=c("green","red"))
  mtext("Pointer Analysis",side=1, cex=1.4, font=2, line=8, las=0)
  mtext("IF occurrence (%)",side=2, cex=1.4, font=2,line=3, las=0)
  dev.off()
}

generateExceptionsBarplot <- function(frame, colType, elemList, plotType, toEvaluate, prec, phase="1")
{
  m <- createMatrix(frame, colType, elemList)
  dir <- paste(plots, "/phase", phase, "/barplots/exceptions/", toEvaluate, "/", sep="")
  mkdirs(dir)
  jpeg(paste(dir, plotType, "_ExceptionsPlot_", toEvaluate, "_", prec,".jpg", sep = ""))
  par(mar=c(5,5,5,1))
  barplot(m, names.arg = elemList, xlab="Exception", ylab="IF occurrence (%)", beside=TRUE,
          cex.names=1.5, cex.lab=1.5, cex.axis=1.2, ylim=c(0,100),col=c("green","red"), main=paste("IF occurrence for ",getPrettyPrecision(prec)," SDGs ","(",plotType,")",sep=""))
  legend("bottomleft",legend=c("Flow","No flow"),inset=c(-0.16,-0.19),xpd=TRUE,fill=c("green","red"))
  dev.off()
}

generatePrecisionsBoxplot <- function(namesList, labelsList, plotType, exception, toEvaluate, form, excepFileName, yLim=c(), phase="1")
{
  excepsTitlePart <- c(Yes="with", No="without")
  baseExcepsTitle <- c("for SDGs", "exceptions", paste("(",plotType,")",sep=""))
  logscales <- c("CGNodes"="y", "CGEdges"="y", "SDGNodes"="y", "SDGEdges"="y", "LineVios"="", "SdgCreated"="")
  dir <- paste(plots, "/phase", phase,"/boxplots/precisions/", toEvaluate, "/", sep="")
  mkdirs(dir)
  comp <- ""
  if(!is.null(yLim))
  {
    comp <- paste("_yLim", toString(yLim[2]), sep="")
  }
  jpeg(paste(dir, plotType, "_PrecisionsPlot_", toEvaluate, "_", excepFileName, comp,".jpg", sep = ""))
  title <- paste(labelsList[[toEvaluate]][length(labelsList[[toEvaluate]])], 
                 baseExcepsTitle[1], excepsTitlePart[[exception]], baseExcepsTitle[2], 
                 baseExcepsTitle[3],sep=" ")
  boxplot(form, las=2, par(mar = c(9.5,5.2,2,0.7) + 0.1, cex.axis=0.9), 
          names=namesList, main=title, col="lightblue",log=logscales[[toEvaluate]], ylim=yLim)
  mtext("Pointer Analysis",side=1, cex=1.4, font=2, line=8)
  mtext(labelsList[[toEvaluate]][1],side=2, cex=1.4, font=2,line=3.8)
  dev.off()
}

generateExceptionsBoxplot <- function(labelsList, plotType, precision, toEvaluate, form, yLim=c(), phase="1")
{
  logscales <- c("CGNodes"="y", "CGEdges"="y", "SDGNodes"="y", "SDGEdges"="y", "LineVios"="")
  dir <- paste(plots, "/phase", phase, "/boxplots/exceptions/", toEvaluate, "/", sep="")
  mkdirs(dir)
  yLimComp <- ""
  if(!is.null(yLim))
  {
    yLimComp <- paste("_yLim",toString(yLim[2]),sep="")
  }
  jpeg(paste(dir, plotType, "_ExceptionsPlot_", toEvaluate, "_", precision, yLimComp, ".jpg", sep = ""))
  baseTitle <- c("for", "SDGs")
  title0 <- paste(labelsList[[toEvaluate]][length(labelsList[[toEvaluate]])], baseTitle[1], getPrettyPrecision(precision),
                  baseTitle[2], sep=" ")
  title <- paste(title0, " (", plotType,")",sep="")
  boxplot(form, xlab="Exception",ylab=labelsList[[toEvaluate]][1], main=title, 
          par(mar=c(5,5,5,1.5),cex.lab=1.5, cex.axis=1.5),col="lightblue",log=logscales[[toEvaluate]], ylim=yLim)
  dev.off()
}

createMatrix <- function(frame, colType, elemList)
{
  m <- matrix(nrow=2, ncol=length(elemList))
  getIfcNum <- function(tab, elem){
    if(elem %in% names(tab))
    {
      return(tab[[elem]])
    }else{
      return(0)
    }
  }
  for(i in getPositiveRange(length(elemList)))
  {
    elem <- elemList[i]
    specificList <- split(frame, frame[[colType]])[[elem]]
    tab <- table(specificList$HasIfc)
    hasIfc <- getIfcNum(tab, 'TRUE')
    noIfc <- getIfcNum(tab, 'FALSE')
    total <- hasIfc + noIfc
    if(total > 0){
      m[1,i] <- 100 * (hasIfc / total)
      m[2,i] <- 100 * (noIfc / total)
    }
  }
  return(m)
}

generatePrecisionPlots <- function(methodDf, revDf, toEvaluateList, labelsList, desiredExceptions=exceptions, desiredPrecisions=precisions, phase="1")
{
  revDfSplittedByExceptions <- split(revDf, revDf$Exception)
  methodDfSplittedByExceptions <- split(methodDf, methodDf$Exception)
  excepsFileName <- c(Yes="excep",No="noExcep")  
  getNamesList <- function(precs)
  {
    precsMappings <- c("TYPE_BASED" = "TYPE_BASED", "INSTANCE_BASED" = "INSTANCE_BAS",
                       "OBJECT_SENSITIVE" = "OBJ_SENSITIVE", "N1_OBJECT_SENSITIVE" = "N1_OBJ_SENS", 
                       "UNLIMITED_OBJECT_SENSITIVE" =  "UNL_OBJ_SENS", "N1_CALL_STACK" = "N1_CALL_STACK",
                       "N2_CALL_STACK" = "N2_CALL_STACK", "N3_CALL_STACK" = "N3_CALL_STACK")
    finalList <- c()
    i <- 0
    for(prec in precs){
      i <- i + 1
      finalList[i] <- precsMappings[prec]
    }
    return(finalList)
  }
  namesList <- getNamesList(desiredPrecisions)
  #namesList <- c("TYPE_BASED", "INSTANCE_BAS", "OBJ_SENSITIVE", "N1_OBJ_SENS", 
  #               "UNL_OBJ_SENS", "N1_CALL_STACK", "N2_CALL_STACK", "N3_CALL_STACK")
  sdgCreatedDf <- getSdgCreatedDf(revDf)
  sdgCreatedByProjDf <- getSdgCreatedByProj(sdgCreatedDf, desiredExceptions, desiredPrecisions)
  sdgSucCreatedDf <- split(sdgCreatedDf, sdgCreatedDf$Created)[['TRUE']]
  splittedSdgCreatedProjByExcep <- split(sdgCreatedByProjDf, sdgCreatedByProjDf$Exception)
  splittedSdgCreateByExcep <- split(sdgSucCreatedDf, sdgSucCreatedDf$Exception)
  revsHeader <- c("Project", "Rev")
  for(exception in desiredExceptions)
  {
    excepFileName <- excepsFileName[[exception]]
    
    sdgCreatedProjDfExcep <- splittedSdgCreatedProjByExcep[[exception]]
    if(!is.null(sdgCreatedProjDfExcep))
    {
      generatePrecisionsBoxplot(namesList,labelsList, "Projs", exception, "SdgCreated", 
                                sdgCreatedProjDfExcep$Rate ~ factor(sdgCreatedProjDfExcep$Precision, desiredPrecisions), excepFileName, phase=phase)
    }
    sdgCreatedDfExcep <- splittedSdgCreateByExcep[[exception]]
    if(!is.null(sdgCreatedDfExcep)){
      creationsDf <- setNames(aggregate(sdgCreatedDfExcep$Created ~ factor(sdgCreatedDfExcep$Precision, desiredPrecisions), FUN=length), c("Precision", "SdgCreations"))  
      generatePrecisionsBarplot(creationsDf$SdgCreations,"Revs", "SdgCreations", excepFileName, namesList, exception, phase=phase)
    }
    revDfException <- revDfSplittedByExceptions[[exception]]
    if(!is.null(revDfException))
    {
      toEvaluate <- "HasIfc"
      revDfExceptionFilt <- filterConfigsWithoutNa(toEvaluate, revsHeader, revDfException, length(desiredPrecisions))
      generateGroupedPrecisionsBarplot(revDfExceptionFilt, "Precision", desiredPrecisions,"Revs", toEvaluate, excepFileName, namesList, exception, phase=phase)
      revPrecisionsFactor <- factor(revDfExceptionFilt$Precision, desiredPrecisions)  
      for(toEvaluate in toEvaluateList)
      {
        revDfExceptionFilt <- filterConfigsWithoutNa(toEvaluate, revsHeader, revDfException, length(desiredPrecisions))
        revPrecisionsFactor <- factor(revDfExceptionFilt$Precision, desiredPrecisions)
        generatePrecisionsBoxplot(namesList,labelsList, "Revs", exception, toEvaluate, revDfExceptionFilt[[toEvaluate]] ~ revPrecisionsFactor, excepFileName, phase=phase)
        if(toEvaluate == "LineVios")
        {
          generatePrecisionsBoxplot(namesList,labelsList, "Revs", exception, toEvaluate, revDfExceptionFilt[[toEvaluate]] ~ revPrecisionsFactor, excepFileName, c(0,30), phase)
          generatePrecisionsBoxplot(namesList,labelsList, "Revs", exception, toEvaluate, revDfExceptionFilt[[toEvaluate]] ~ revPrecisionsFactor, excepFileName, c(0,25), phase)
          generatePrecisionsBoxplot(namesList,labelsList, "Revs", exception, toEvaluate, revDfExceptionFilt[[toEvaluate]] ~ revPrecisionsFactor, excepFileName, c(0,20), phase)
          generatePrecisionsBoxplot(namesList,labelsList, "Revs", exception, toEvaluate, revDfExceptionFilt[[toEvaluate]] ~ revPrecisionsFactor, excepFileName, c(0,15), phase)
        }
      }     
    }
    
    methodDfException <- methodDfSplittedByExceptions[[exception]]
    if(!is.null(methodDfException))
    {
      toEvaluate <- "HasIfc"
      methodDfExceptionFilt <- filterConfigsWithoutNa(toEvaluate, c("Project", "Rev", "Method"), methodDfException, length(desiredPrecisions))
      generateGroupedPrecisionsBarplot(methodDfExceptionFilt, "Precision", desiredPrecisions, "Methods", toEvaluate, excepFileName, namesList, exception, phase=phase)
      methPrecisionsFactor <- factor(methodDfExceptionFilt$Precision, desiredPrecisions)
      generatePrecisionsBoxplot(namesList,labelsList, "Methods", exception, "LineVios", methodDfExceptionFilt$LineVios ~ methPrecisionsFactor, excepFileName, phase=phase)  
      generatePrecisionsBoxplot(namesList,labelsList, "Methods", exception, "LineVios", methodDfExceptionFilt$LineVios ~ methPrecisionsFactor, excepFileName, c(0,30), phase)  
      generatePrecisionsBoxplot(namesList,labelsList, "Methods", exception, "LineVios", methodDfExceptionFilt$LineVios ~ methPrecisionsFactor, excepFileName, c(0,25), phase)  
      generatePrecisionsBoxplot(namesList,labelsList, "Methods", exception, "LineVios", methodDfExceptionFilt$LineVios ~ methPrecisionsFactor, excepFileName, c(0,20), phase)  
      generatePrecisionsBoxplot(namesList,labelsList, "Methods", exception, "LineVios", methodDfExceptionFilt$LineVios ~ methPrecisionsFactor, excepFileName, c(0,15), phase)  
    }
  }
}

generateExceptionPlots <- function(methodDf, revDf, toEvaluateList, labelsList)
{
  revDfSplittedByPrecisions <- split(revDf, revDf$Precision)
  methodDfSplittedByPrecisions <- split(methodDf, methodDf$Precision)
  for(precision in precisions)
  {
    revDfPrecision <- revDfSplittedByPrecisions[[precision]]
    toEvaluate <- "HasIfc"
    revDfPrecisionFilt <- filterConfigsWithoutNa(toEvaluate, c("Project", "Rev"), revDfPrecision, length(exceptions))
    generateExceptionsBarplot(revDfPrecisionFilt, "Exception", exceptions, "Revs", toEvaluate, precision)
    for(toEvaluate in toEvaluateList)
    {
      revDfPrecisionFilt <- filterConfigsWithoutNa(toEvaluate, c("Project", "Rev"), revDfPrecision, length(exceptions))
      generateExceptionsBoxplot(labelsList, "Revs", precision, toEvaluate, revDfPrecisionFilt[[toEvaluate]] ~ factor(revDfPrecisionFilt$Exception, exceptions))
      if(toEvaluate == 'LineVios')
      {
        generateExceptionsBoxplot(labelsList, "Revs", precision, toEvaluate, revDfPrecisionFilt[[toEvaluate]] ~ factor(revDfPrecisionFilt$Exception, exceptions), c(0,30))
        generateExceptionsBoxplot(labelsList, "Revs", precision, toEvaluate, revDfPrecisionFilt[[toEvaluate]] ~ factor(revDfPrecisionFilt$Exception, exceptions), c(0,25))
        generateExceptionsBoxplot(labelsList, "Revs", precision, toEvaluate, revDfPrecisionFilt[[toEvaluate]] ~ factor(revDfPrecisionFilt$Exception, exceptions), c(0,20))
        generateExceptionsBoxplot(labelsList, "Revs", precision, toEvaluate, revDfPrecisionFilt[[toEvaluate]] ~ factor(revDfPrecisionFilt$Exception, exceptions), c(0,15))
      }
    }
    methodDfPrecision <- methodDfSplittedByPrecisions[[precision]]
    methodDfPrecisionFilt <- filterConfigsWithoutNa("HasIfc", c("Project", "Rev", "Method"), methodDfPrecision, length(exceptions))
    generateExceptionsBarplot(methodDfPrecisionFilt, "Exception", exceptions,"Methods", "HasIfc", precision)
    methodDfPrecisionFilt <- filterConfigsWithoutNa("LineVios", c("Project", "Rev", "Method"), methodDfPrecision, length(exceptions))
    generateExceptionsBoxplot(labelsList, "Methods", precision, "LineVios", methodDfPrecisionFilt$LineVios ~ factor(methodDfPrecisionFilt$Exception, exceptions))
    generateExceptionsBoxplot(labelsList, "Methods", precision, "LineVios", methodDfPrecisionFilt$LineVios ~ factor(methodDfPrecisionFilt$Exception, exceptions),c(0,30))
    generateExceptionsBoxplot(labelsList, "Methods", precision, "LineVios", methodDfPrecisionFilt$LineVios ~ factor(methodDfPrecisionFilt$Exception, exceptions),c(0,25))
    generateExceptionsBoxplot(labelsList, "Methods", precision, "LineVios", methodDfPrecisionFilt$LineVios ~ factor(methodDfPrecisionFilt$Exception, exceptions),c(0,20))
    generateExceptionsBoxplot(labelsList, "Methods", precision, "LineVios", methodDfPrecisionFilt$LineVios ~ factor(methodDfPrecisionFilt$Exception, exceptions),c(0,15))
  }
}
toEvaluateList <- c("CGNodes", "CGEdges", "SDGNodes", "SDGEdges", "LineVios")
generateExecutionPlots <- function(methodDf, revDf)
{
  if(nrow(revDf) > 0){
    generatePrecisionPlots(methodDf, revDf, toEvaluateList, labelsList)
    generateExceptionPlots(methodDf, revDf, toEvaluateList, labelsList)
  }
}

calculateViosDiffList <- function(rowDetList, colDetList){
  diffsList <- c(both="", onlyRow ="", onlyCol="")
  rowDetViosList <- splitDetVios(rowDetList)
  colDetViosList <- splitDetVios(colDetList)
  if(length(rowDetViosList) > 0)
  {
    for(str in rowDetViosList)
    {
      if(str %in% colDetViosList)
      {
        diffsList[['both']] <- ifelse(diffsList[['both']] == "", str, paste(diffsList[['both']], str, sep=";"))
      }else{
        diffsList[['onlyRow']] <- ifelse(diffsList[['onlyRow']] == "", str, paste(diffsList[['onlyRow']], str, sep=";"))
      }
    }
  }
  
  if(length(colDetViosList) > 0)
  {
    for(str in colDetViosList)
    {
      if(!(str %in% rowDetViosList))
      {
        diffsList[['onlyCol']] <- ifelse(diffsList[['onlyCol']] == "", str, paste(diffsList[['onlyCol']], str, sep=";"))
      }
    }
  }
  return(diffsList)
}

splitDetVios <- function(detVios){
  str <- toString(detVios)
  substr <- substr(str, 2, nchar(str) - 1)
  expr <- "([a-z]|[A-Z])[^\\(\\)]*\\([^\\(\\)]*\\) \\(line [0-9]+\\) -> [^\\(\\)]*\\([^\\(\\)]*\\) \\(line [0-9]+\\)"
  m <- gregexpr(expr, substr, perl=TRUE)
  return(regmatches(substr, m)[[1]])
}

createPrecByPrecTable <- function(precisions){
  lenPrec <- length(precisions)
  m <- matrix(nrow=lenPrec, ncol=lenPrec)
  rownames(m) <- precisions
  colnames(m) <- precisions
  return(m)
}

generateSummaryBoxplot <- function(values, name, ylabel, title)
{
  dir <- paste(plots, "/summary/boxplots/", sep="")
  mkdirs(dir)
  jpeg(paste(dir, name,".jpg", sep = ""))
  par(mar = c(2,5,5,1.5), cex.lab=1.5, cex.axis=1.5)
  boxplot(values, ylab=ylabel, main=title, col="lightblue")
  dev.off()
}

generateMethodsPerProjectBoxplot <- function(evalMethodsPerProject)
{
  dir <- paste(plots, "/summary/boxplots/", sep="")
  mkdirs(dir)
  jpeg(paste(dir, "Methods_Per_Project",".jpg", sep = ""))
  par(mar = c(5,5,5,1.5), cex.lab=1.5, cex.axis=1.5)
  boxplot(evalMethodsPerProject$median, evalMethodsPerProject$sum, ylab="Methods",
          xlab="Project level statistic",
          main="Methods per project", col="lightblue", names=c("Median", "Sum"))
  dev.off()
}

getSdgCreatedDf <- function(revDf)
{
  df <- data.frame(Project=character(), Rev=character(), Precision=character(), Exception=character(), HasSourceAndSink=logical(), Created=logical(), BothEdited=logical(),stringsAsFactors=FALSE)
  for(i in getPositiveRange(nrow(revDf)))
  {
    row <- revDf[i,]
    df[i,1] <- row$Project
    df[i,2] <- row$Rev
    df[i,3] <- row$Precision
    df[i,4] <- row$Exception
    df[i,5] <- row$HasSourceAndSink
    df[i,6] <- !is.na(row$SDGNodes) && !is.na(row$SDGEdges)
  }
  return(df)
}

getSdgCreatedByProj <- function(df, exceptions=exceptions, precisions=precisions)
{
  projDf <- data.frame(Project=character(), Precision=character(), Exception=character(), Rate=numeric(), stringsAsFactors=FALSE)
  splittedDfByProj <- split(df, df$Project)
  for(i in getPositiveRange(length(unique(df$Project))))
  {
    proj <- unique(df$Project)[i]
    projLines <- splittedDfByProj[[proj]]
    splittedProjLines <- NULL
    if(!is.null(projLines))
    {
      splittedProjLines <- split(projLines, list(projLines$Precision, projLines$Exception))
    }
    
    for(exception in exceptions)
    {
      for(precision in precisions)
      {
        precExcepLines <- NULL
        if(!is.null(splittedProjLines))
        {
          precExcepLines <- splittedProjLines[[paste(precision, exception, sep=".")]]
          splitBySdgCreated <- split(precExcepLines, precExcepLines$Created)
          createdSdg <- getNRow(splitBySdgCreated[['TRUE']])
          rate <- 100 * (createdSdg / (createdSdg + getNRow(splitBySdgCreated[['FALSE']])))
          newLineNum <- nrow(projDf) + 1
          projDf[newLineNum, 1] <- proj
          projDf[newLineNum, 2] <- precision
          projDf[newLineNum, 3] <- exception
          projDf[newLineNum, 4] <- rate
        }  
      }
    }
  }
  return(projDf)
}

getFilteredConfigsWithoutNa <- function(df, uniqueCrit, toEvaluateList, numberOfConfigs = 16)
{
  filteredDfsList <- c()
  for(toEvaluate in toEvaluateList)
  {
    filteredDfsList[[toEvaluate]] <- filterConfigsWithoutNa(toEvaluate, uniqueCrit, df, numberOfConfigs)
  }
  return(filteredDfsList)
}

doStatisticTests <- function(filteredRevDfsList, filteredMethodDfsList)
{
  library('coin')
  #library('exactRankTests')
  toEvaluateResList <- c() 
  for(toEvaluate in toEvaluateList)
  {
    filteredRevDf <- filteredRevDfsList[[toEvaluate]]
    toEvaluateResList[[toEvaluate]] <- doStatisticTestException(toEvaluate,filteredRevDf)
  }
  filteredMethodDf <- filteredMethodDfsList[['LineVios']]
  methodResList <- list('LineVios'=doStatisticTestException('LineVios', filteredMethodDf))
  return(list('Rev'=toEvaluateResList, 'Method'=methodResList))
}

doStatisticTestException <- function(toEvaluate, filteredDf)
{
  res <- c()
  splittedFiltDf <- split(filteredDf, filteredDf$Exception)
  splittedYesFiltDf <- splittedFiltDf$Yes
  splittedNoFiltDf <- splittedFiltDf$No
  toEvaluateYes <- splittedYesFiltDf[[toEvaluate]]
  toEvaluateNo <- splittedNoFiltDf[[toEvaluate]]
  hasDiff <- Reduce('|', (toEvaluateYes - toEvaluateNo) != 0)
  if(!is.null(hasDiff) && hasDiff)
  {
    shapTestYes <- shapiro.test(toEvaluateYes)
    shapTestNo <- shapiro.test(toEvaluateNo)
    shapTestRes <- c('Yes'=shapTestYes, 'No'=shapTestNo)
    
    wilcoxTest <- wilcox.test(toEvaluateYes, toEvaluateNo, paired = TRUE, exact=FALSE, alternative="greater",conf.level=0.95)  
    #wilcoxExact <- wilcox.exact(toEvaluateYes, toEvaluateNo, paired = TRUE, exact = TRUE, alternative="greater",conf.level=0.95)
    wilcoxSignTest <- wilcoxsign_test(toEvaluateYes ~ toEvaluateNo, zero.method="Wilcoxon", dist="exact", alternative="greater",conf.level=0.95)
    wilcoxSignTestPratt <- wilcoxsign_test(toEvaluateYes ~ toEvaluateNo, dist="exact", alternative="greater",conf.level=0.95)
    wilcoxTestRes <- c('WilcoxTest'=wilcoxTest, 'WilcoxSignTest'=wilcoxSignTest, 'WilcoxSignTestPratt'=wilcoxSignTestPratt)
    res <- list('HasDiff'=hasDiff, 'Normality'=shapTestRes, 'Th'=wilcoxTestRes)
  }else{
    res <- list('HasDiff'=hasDiff)
  }
  return(res)
}

generateDataSummaryPlots <- function(evalRevDf, evalMethodDf, builtRevDf, totalRevDf)
{
  projsWithMerge <- totalRevDf[!is.na(totalRevDf$Revs) & totalRevDf$Revs > 0,]
  projESMCRate <- data.frame(project=character(), rate=numeric(), stringsAsFactors=FALSE)
  evalRevsPerProject <- data.frame(project=character(), revs=numeric(), stringsAsFactors=FALSE)
  evalMethodsPerRev <- data.frame(project=character(), rev=character(), methods=numeric(), stringsAsFactors=FALSE)
  evalMethodsPerProject <- data.frame(project=character(),median=numeric(), sum=numeric(), stringsAsFactors=FALSE)
  builtProjectsRates <- data.frame(project=character(), rate=numeric(), stringsAsFactors=FALSE)
  evalProjects <- unique(evalRevDf$Project) 
  evalRevsSplittedByProject <- split(evalRevDf, evalRevDf$Project)
  evalMethodsSplittedByProject <- split(evalMethodDf, evalMethodDf$Project)
  for(i in getPositiveRange(nrow(projsWithMerge)))
  {
    proj <- projsWithMerge[i,1]
    editsamemc_revs <- nrow(builtRevDf[builtRevDf$Project == proj,])
    projESMCRate[i,1] <- proj
    projESMCRate[i,2] <- 100 * (editsamemc_revs / projsWithMerge[i,2])
  }
  for(i in getPositiveRange(length(evalProjects)))
  {
    proj <- evalProjects[i]
    evalRevsPerProject[i,1] <- proj
    revs <- evalRevsSplittedByProject[[proj]]
    evalRevsPerProject[i,2] <- nrow(revs)
    projectMethods <- evalMethodsSplittedByProject[[proj]]
    splittedRevs <- split(projectMethods, projectMethods$Rev)
    methods <- c()
    for(rev in revs[,2])
    {
      revMethods <- nrow(splittedRevs[[rev]])
      methods <- appendToList(methods, revMethods)
      evalMethodsRow <- nrow(evalMethodsPerRev) + 1
      evalMethodsPerRev[evalMethodsRow, 1] <- proj
      evalMethodsPerRev[evalMethodsRow, 2] <- rev
      evalMethodsPerRev[evalMethodsRow, 3] <- revMethods
    }
    numRevs <- length(revs[,2])
    evalMethodsPerProject[i,1] <- proj
    evalMethodsPerProject[i,2] <- numRevs * median(methods)
    evalMethodsPerProject[i,3] <- sum(methods)
  }
  builtProjects <- unique(builtRevDf$Project)
  splittedBuiltProjects <- split(builtRevDf, builtRevDf$Project)
  
  for(i in getPositiveRange(length(builtProjects)))
  {
    builtProject <- builtProjects[i]
    builtProjectsLines <- splittedBuiltProjects[[builtProject]]
    splittedBuiltRevDf <- split(builtProjectsLines, builtProjectsLines$Built)
    builtProjectsRates[i, 1] <- builtProject
    successCases <- getNRow(splittedBuiltRevDf[['TRUE']])
    builtProjectsRates[i, 2] <-  100 * (successCases / (successCases + getNRow(splittedBuiltRevDf[['FALSE']])))
  }
  generateSummaryBoxplot(evalRevsPerProject$revs, "Revs_Per_Project", "Revisions", "Evaluated revisions per project")
  generateSummaryBoxplot(evalMethodsPerRev$methods, "Methods_Per_Rev", "Methods", "Evaluated methods per revision")
  generateSummaryBoxplot(builtProjectsRates$rate, "Projects_Build_Rates","Build success (%)", "Build success per project")
  #generateSummaryBoxplot(projESMCRate$rate, "Projects_ESMC_Rates","Edit Same Mc (%)", "Edit Same MC per project")
  generateMethodsPerProjectBoxplot(evalMethodsPerProject)
}

printDataSummary <- function(revDf, methodDf, evalRevDf, evalMethodDf, builtRevDf, totalRevsDf, editSameMcRevsDf, revFiltDf, methodFiltDf){
  mkdirs(rDir)
  summaryFil <- paste(rDir, "summary.txt", sep="/")
  if(!file.exists(summaryFil))
  {
    file.create(summaryFil)
  }
  projsWithMerge <- totalRevsDf[!is.na(totalRevsDf$Revs) & totalRevsDf$Revs > 0,]
  projsWithConfESMC <- totalRevsDf[!is.na(totalRevsDf$Revs) & totalRevsDf$Revs > 0 & !is.na(totalRevsDf$EditSameMcConflicts) & 
                                     totalRevsDf$EditSameMcConflicts > 0,]
  projsWithESMC <- length(unique(editSameMcRevsDf$Project))
  esMcConfRevs <- sum(projsWithConfESMC$EditSameMcConflicts)
  esMcRevs <- getNRow(editSameMcRevsDf)
  buildRunProjects <- length(unique(builtRevDf$Project)) 
  splittedBuiltRevDf <- split(builtRevDf, builtRevDf$Built)
  successBuiltRevDf <- splittedBuiltRevDf[['TRUE']]
  buildSuccessProjects <- length(unique(successBuiltRevDf$Project))
  sdgCreatedDf <- getSdgCreatedDf(revDf)
  filtSdgCreatedDf <- getSdgCreatedDf(revFiltDf)
  sdgSuccessCreatedDf <- split(sdgCreatedDf, sdgCreatedDf$Created)[['TRUE']]
  filtSdgSuccessCreatedDf <- split(filtSdgCreatedDf, filtSdgCreatedDf$Created)[['TRUE']]
  sdgCreatedProjs <- length(unique(sdgSuccessCreatedDf$Project))
  buildRunRevs <- nrow(builtRevDf)
  buildSuccessRevs <- getNRow(successBuiltRevDf)
  buildFailedRevs <- getNRow(splittedBuiltRevDf[['FALSE']])
  buildResultSum <- buildSuccessRevs + buildFailedRevs
  buildSuccessRate <- ifelse(buildResultSum > 0, paste(100 * (buildSuccessRevs / buildResultSum),"%",sep=""), "-")
  sdgCreatedRevs <- unique(sdgSuccessCreatedDf[c("Project", "Rev")])
  numSdgCreatedRevs <- getNRow(sdgCreatedRevs)
  hasGradleRevs <- split(builtRevDf, builtRevDf$HasGradle)[['TRUE']]
  hasAntRevs <- split(builtRevDf, builtRevDf$HasAnt)[['TRUE']]
  hasMvnRevs <- split(builtRevDf, builtRevDf$HasMvn)[['TRUE']]
  
  revsWithGradle <- getNRow(hasGradleRevs)
  revsWithAnt <- getNRow(hasAntRevs)
  revsWithMvn <- getNRow(hasMvnRevs)
  
  projsWithGradle <- length(unique(hasGradleRevs$Project))
  projsWithAnt <- length(unique(hasAntRevs$Project))
  projsWithMvn <- length(unique(hasMvnRevs$Project))
  
  moreThanOneBuild <- builtRevDf[(builtRevDf$HasGradle == TRUE & builtRevDf$HasAnt == TRUE) | 
                                   (builtRevDf$HasGradle == TRUE & builtRevDf$HasMvn == TRUE) | 
                                   (builtRevDf$HasAnt == TRUE & builtRevDf$HasMvn == TRUE),]
  projsWithMoreThanOneBuild <- length(unique(moreThanOneBuild$Project))
  
  evalRevs <- nrow(evalRevDf)
  evalMethods <- nrow(evalMethodDf)
  revsSplitByHasSrcAndSink <- split(revDf, revDf$HasSourceAndSink)
  revsWithSrcAndSink <- revsSplitByHasSrcAndSink[['Yes']]
  revsWithoutSrcAndSink <- revsSplitByHasSrcAndSink[['No']]
  numRevSrcAndSink <- getNRow(unique(revsWithSrcAndSink[c("Project","Rev")]))
  numRevWithoutSrcAndSink <- getNRow(unique(revsWithoutSrcAndSink[c("Project","Rev")]))
  methodsSplitByHasSrcAndSink <- split(methodDf, methodDf$HasSourceAndSink)
  methodsWithSrcAndSink <- methodsSplitByHasSrcAndSink[['Yes']]
  unMethodsWithSrcAndSink <- unique(methodsWithSrcAndSink[c("Project","Rev","Method")])
  methodsWithoutSrcAndSink <- methodsSplitByHasSrcAndSink[['No']]
  unMethodsWithoutSrcAndSink <- unique(methodsWithoutSrcAndSink[c("Project","Rev","Method")])
  numMethodSrcAndSink <- getNRow(unMethodsWithSrcAndSink)
  numMethodWithoutSrcAndSink <- getNRow(setdiff(unMethodsWithoutSrcAndSink,unMethodsWithSrcAndSink))
  #print(unMethodsWithSrcAndSink)
  #print(unMethodsWithoutSrcAndSink)
  methodsWithBoth <- 0
  if(!is.null(unMethodsWithoutSrcAndSink))
  {
    for(r in getPositiveRange(nrow(unMethodsWithoutSrcAndSink)))
    {
      proj <- unMethodsWithoutSrcAndSink[r,1]
      rev <- unMethodsWithoutSrcAndSink[r,2]
      method <- unMethodsWithoutSrcAndSink[r,3]
      lines <- which(methodDf$Project == proj & methodDf$Rev == rev & methodDf$Method == method)
      if(Reduce('|',methodDf[lines, ]$HasLeftAndRight))
      {
        methodsWithBoth <- methodsWithBoth + 1
      }
    }
  }
  splittedBuiltWith <- split(builtRevDf, builtRevDf$BuiltWith)
  builtWithGradle <- splittedBuiltWith[['Gradle']]
  builtWithAnt <- splittedBuiltWith[['Ant']]
  builtWithMvn <- splittedBuiltWith[['Maven']]
  projsWithMoreThanOneBuildUsed <- 0
  if(!is.null(successBuiltRevDf))
  {
    builtRevDfSplittedByProj <- split(successBuiltRevDf, successBuiltRevDf$Project)
    for(proj in unique(successBuiltRevDf$Project))
    {
      buildSystems <- length(unique(builtRevDfSplittedByProj[[proj]]$BuiltWith))
      if(buildSystems > 1)
      {
        projsWithMoreThanOneBuildUsed <- projsWithMoreThanOneBuildUsed + 1
      }
    }
  }
  numConfigs <- length(precisions) * length(exceptions)
  numFiltConfigs <- length(phase2Precisions) * length(phase2Exceptions)
  numMethods <- nrow(methodDf) / numConfigs
  allNotNaDf <- data.frame(Project=character(),Rev=character(),Method=character(),stringsAsFactors=FALSE)
  filtNotNaDf <- data.frame(Project=character(),Rev=character(),Method=character(),stringsAsFactors=FALSE)
  someNotNaDf <-data.frame(Project=character(),Rev=character(),Method=character(),stringsAsFactors=FALSE)
  numNonNaLineVios <- 0
  numAllNonNaLineVios <- 0
  numFiltNonNaLineVios <- 0
  for(i in getPositiveRange(numMethods))
  {
    offset <- (numConfigs * (i - 1))
    start <- 1 + offset
    end <- start + numConfigs - 1
    if(!(all(is.na(methodDf$LineVios[start:end])))){
      numNonNaLineVios <- numNonNaLineVios + 1
      someNotNaDf[numNonNaLineVios,] <- methodDf[start, 1:3]
    }
    if(all(!is.na(methodDf$LineVios[start:end])))
    {
      numAllNonNaLineVios <- numAllNonNaLineVios + 1
      allNotNaDf[numAllNonNaLineVios,] <- methodDf[start,1:3]
    }  
    offset2 <- (numFiltConfigs * (i - 1))
    start2 <- 1 + offset2
    end2 <- start2 + numFiltConfigs - 1
    if(all(!is.na(methodFiltDf$LineVios[start2:end2])))
    {
      numFiltNonNaLineVios <- numFiltNonNaLineVios + 1
      filtNotNaDf[numFiltNonNaLineVios,] <- methodFiltDf[start2,1:3]
    } 
  }
  
  #print(someNotNaDf)
  #print(allNotNaDf)
  mergeRunRevs <- sum(projsWithMerge$Revs)
  sdgSucCreationsForAll <- function(sdgSuccessCreatedDf, numOfConfigs)
  {
    sdgSuccessCreations <- data.frame(Project=character(), Rev=character(),Creations=numeric(), stringsAsFactors=FALSE)
    if(!is.null(sdgSuccessCreatedDf))
    {
      sdgSuccessCreations <- setNames(aggregate(sdgSuccessCreatedDf$Created, by = list(sdgSuccessCreatedDf$Project, sdgSuccessCreatedDf$Rev), FUN=length), c("Project", "Rev", "Creations"))
    }
    return(getNRow(sdgSuccessCreations[sdgSuccessCreations$Creations == numOfConfigs,]))
  }
  
  revsWithSdgCreatedForAll <- sdgSucCreationsForAll(sdgSuccessCreatedDf, numConfigs)
  revsWithSdgCreatedForAllFilt <- sdgSucCreationsForAll(filtSdgSuccessCreatedDf, numFiltConfigs)
  print(unique(allNotNaDf[c("Project", "Rev")]))
  lines <- c(
    paste("Total Projects:",total_projects),
    paste("Projects with merge executed:",getNRow(projsWithMerge)),
    paste("Projects with revs with edit same mc merge conflicts:", getNRow(projsWithConfESMC)),
    paste("Projects with revs with edit same mc and no merge conflicts:",projsWithESMC),
    paste("Projects with build executed:", buildRunProjects),
    paste("Projects with build success revs:", buildSuccessProjects),
    paste("Projects with Gradle:", projsWithGradle),
    paste("Projects with Ant:", projsWithAnt),
    paste("Projects with Mvn:", projsWithMvn),
    paste("Projects with revs successfully built using Gradle:", length(unique(builtWithGradle$Project))),
    paste("Projects with revs successfully built using Ant:", length(unique(builtWithAnt$Project))),
    paste("Projects with revs successfully built using Maven:", length(unique(builtWithMvn$Project))),
    paste("Projects with different build systems:", projsWithMoreThanOneBuild),
    paste("Projects built with different build systems:",projsWithMoreThanOneBuildUsed),
    paste("Projects with Sdg Created for at least one config:", sdgCreatedProjs),
    paste("Project with LineVios calculated for at least one config:",length(unique(someNotNaDf$Project))),
    paste("Project with LineVios calculated for all phase 2 configs:",length(unique(filtNotNaDf$Project))),
    paste("Project with LineVios calculated for all configs:",length(unique(allNotNaDf$Project))),
    paste("Revs with merge executed:", mergeRunRevs),
    paste("Revs with non java conflicts only:", sum(projsWithMerge$ConflictsNonJava)),
    paste("Revs with java conflicts:", sum(projsWithMerge$ConflictsJava)),
    paste("Revs with edit same mc conflicts:",esMcConfRevs),
    paste("Revs with edit same mc and no conflicts:", esMcRevs),
    paste("Revs with build executed:", buildRunRevs),
    paste("Revs with build success:", buildSuccessRevs),
    paste("Revs with build failure:", buildFailedRevs),
    paste("Revs build success rate: ", buildSuccessRate,sep=""),
    paste("Revs with Gradle:", revsWithGradle),
    paste("Revs successfully built with Gradle:", getNRow(builtWithGradle)),
    paste("Revs with Ant:", revsWithAnt),
    paste("Revs successfully built with Ant:", getNRow(builtWithAnt)),
    paste("Revs with Maven:", revsWithMvn),
    paste("Revs successfully built with Maven:", getNRow(builtWithMvn)),
    paste("Revs evaluated by Joana:", evalRevs),
    paste("Revs with Sdg Created for at least one config: ", numSdgCreatedRevs),
    paste("Revs with Sdg Created for all phase 2 configs: ", revsWithSdgCreatedForAllFilt),
    paste("Revs with Sdg Created for all configs: ", revsWithSdgCreatedForAll),
    paste("Revs containing methods with source and sink:", numRevSrcAndSink),
    paste("Revs not containing methods with source and sink:", numRevWithoutSrcAndSink),
    paste("Revs with Line Vios calculated for at least one config:",getNRow(unique(someNotNaDf[c("Project", "Rev")]))),
    paste("Revs with Line Vios calculated for all phase 2 configs:",getNRow(unique(filtNotNaDf[c("Project", "Rev")]))),
    paste("Revs with Line Vios calculated for all configs:",getNRow(unique(allNotNaDf[c("Project", "Rev")]))),
    paste("Methods evaluated by Joana:", evalMethods),
    paste("Methods with source and sink:", numMethodSrcAndSink),
    paste("Methods without source or sink:",numMethodWithoutSrcAndSink),
    paste("Methods without source or sink with lines contribution from left and right:",methodsWithBoth),
    paste("Methods with LineVios calculated for at least one config:", numNonNaLineVios),
    paste("Methods with LineVios calculated for all phase 2 configs:", numFiltNonNaLineVios),
    paste("Methods with LineVios calculated for all configs:", numAllNonNaLineVios)
  )
  for(line in lines)
  {
    print(line)  
  }
  fileConn <- file(summaryFil)
  writeLines(lines, fileConn)
  close(fileConn)
  #print(paste("Revs edit same mc rate: ", 100 * (buildRunRevs / mergeRunRevs),"%",sep=""))
}

revs_by_project <- c()
methods_by_projectRev <- c()
methods_projectRev_by_excep <- c()
simDetViosTablePerMethodExcep <- c()
diffDetViosTablePerMethodExcep <- c()
sumDetViosTable <- c()
numDetViosTable <- c()
numDetViosTableFilt <- c()
csvs <- c()
methodsInfoDf <- data.frame(Project=character(), Index=numeric(), Rev=character(), Method=character(), NumOfLines=numeric(), stringsAsFactors=FALSE)
editSameMcRevsDf <- data.frame(Project=character(), Rev=character(), stringsAsFactors=FALSE)
revDf <- data.frame(Project=character(), Rev=character(), Precision=character(), Exception=character(),
                    SDGNodes=numeric(),SDGEdges=numeric(), CGNodes=numeric(), CGEdges=numeric(), HasSourceAndSink=character(), LineVios=numeric(), HasIfc=logical(), IFCs=numeric(), stringsAsFactors=FALSE)
methodDf <- data.frame(Project=character(), Rev=character(), Method=character(), Precision=character(), Exception=character(),
                       HasSourceAndSink=character(),LineVios=numeric(), HasIfc=logical(), 
                       IFCs=numeric(),Left=character(), Right=character(), HasLeftAndRight=logical(), stringsAsFactors=FALSE)
evalRevDf <- data.frame(Project=character(),Rev=character(), stringsAsFactors=FALSE)
evalMethDf <- data.frame(Project=character(),Rev=character(), Method=character(), stringsAsFactors=FALSE)
builtRevDf <- data.frame(Project=character(), Rev=character(), Built=logical(), 
                         HasGradle=logical(), HasAnt=logical(),HasMvn=logical(), BuiltWith=character(),stringsAsFactors=FALSE)
totalRevsDf <- data.frame(Project=character(), Revs=numeric(), ConflictsNonJava=numeric(), ConflictsJava=numeric(), EditSameMcConflicts=numeric(), stringsAsFactors=FALSE)
sumDetViosTable$Yes$both <- createPrecByPrecTable(precisions)
sumDetViosTable$Yes$diff <- createPrecByPrecTable(precisions)
sumDetViosTable$No$both <- createPrecByPrecTable(precisions)
sumDetViosTable$No$diff <- createPrecByPrecTable(precisions)
numDetViosTable$Yes$both <- createPrecByPrecTable(precisions)
numDetViosTable$Yes$diff <- createPrecByPrecTable(precisions)
numDetViosTable$No$both <- createPrecByPrecTable(precisions)
numDetViosTable$No$diff <- createPrecByPrecTable(precisions)
numDetViosTableFilt$No$both <- createPrecByPrecTable(phase2Precisions)
numDetViosTableFilt$No$diff <- createPrecByPrecTable(phase2Precisions)
buildStrToBool <- function(str){return(str=="True")}
for(p in getPositiveRange(length(projects))){
  project <- projects[p]
  project_name <- strsplit(project, "/")[[1]][2]
  revs_by_project[[project_name]] <- list()
  print(project_name)
  projectReport <- paste(result_data, project_name, "ProjectReport.csv",sep="/")
  totalRevsDf[p,1] <- project_name
  if(file.exists(projectReport))
  {
    projRepCsv <- read.csv(projectReport, header=TRUE, sep=",", strip.white=TRUE, stringsAsFactors=FALSE)
    totalRevsDf[p,2] <- projRepCsv[["Merge_Scenarios"]]
    totalRevsDf[p,3] <- projRepCsv[["Conflicting_Scenarios_Non_Java_Only"]]
    totalRevsDf[p,4] <- projRepCsv[["Conflicting_Scenarios_Java"]]
    confEsMC <- projRepCsv[['Conflicting_Scenarios_Java_EditSameMC']]
    if(!is.null(confEsMC))
    {
      totalRevsDf[p,5] <- confEsMC  
    }
  }
  
  project_reports_dir <- paste(reports, project_name,sep="/")
  editSameMc <- paste(project_reports_dir, "editSameMCcontribs.csv",sep="/")
  if(file.exists(editSameMc))
  {
    editSameMcCsv <- read.csv(editSameMc, header = TRUE, sep=";", strip.white=TRUE, stringsAsFactors=FALSE)
    for(row in getPositiveRange(nrow(editSameMcCsv)))
    {
      methodInfoDfNewLen <- nrow(methodsInfoDf) + 1
      fullRow <- editSameMcCsv[row,]
      methodsInfoDf[methodInfoDfNewLen,] <- c(project_name, fullRow$Index, fullRow$Revision, fullRow$Signature, fullRow[['Number.of.Lines']])
    }
    
    filRevs <- unique(editSameMcCsv$Revision)
    for(rev in filRevs)
    {
      editSameMcRevsDf[nrow(editSameMcRevsDf) + 1,] <- c(project_name, rev) 
    }
  }
  buildSummary <- paste(project_reports_dir, "buildSummary.csv",sep="/")
  buildCsv <- NULL 
  if(file.exists(buildSummary) && file.info(buildSummary)$size > 0)
  {
    buildCsv <- read.csv(buildSummary, header = TRUE, sep=";", strip.white=TRUE, na.string=c("-"), stringsAsFactors=FALSE)
    for(i in getPositiveRange(nrow(buildCsv)))
    {
      line <- c(project_name, buildCsv$Rev[i],
                buildStrToBool(buildCsv$Built[i]),
                buildStrToBool(buildCsv$Gradle[i]),
                buildStrToBool(buildCsv$Ant[i]), 
                buildStrToBool(buildCsv$Mvn[i]),
                buildCsv[['Built.with']][i])
      builtRevDf <- insertLineToDf(builtRevDf, line)
    }
  }  
  for(rev in list.dirs(project_reports_dir, recursive=FALSE)){
    print(paste("       ",rev))
    rev_name <- basename(rev)
    execSummary <- paste(rev, "executionSummary.csv", sep="/")
    if(file.exists(execSummary) && file.info(execSummary)$size > 0)
    {
      evalRevDf <- insertLineToDf(evalRevDf, c(project_name,rev_name))
      csv <- read.csv(execSummary, header = TRUE, sep=";", strip.white=TRUE, na.string=c("-"))
      csvs[[project_name]][[rev_name]] <- csv
      i <- length(revs_by_project[[project_name]]) + 1
      revs_by_project[[project_name]][[i]] <- rev_name
      
      resultsByMethod <- split(csv, csv$Method)
      methods_by_projectRev[[project_name]][[rev_name]] <- resultsByMethod
      methods = levels(csv$Method)
      numMethods = length(methods)
      first_method <- methods[1]
      for(method in methods)
      {
        evalMethDf <- insertLineToDf(evalMethDf, c(project_name, rev_name, method))
        methods_projectRev_by_excep[[project_name]][[rev_name]][[method]] <- split(resultsByMethod[[method]], resultsByMethod[[method]]$Exceptions)
        print(paste("                 ",method))
        for(exception in exceptions)
        {
          #print(paste("Exception:",  exception))
          
          simM <- createPrecByPrecTable(precisions)
          diffM <- createPrecByPrecTable(precisions)
          
          for(col in getPositiveRange(length(precisions)))
          {
            precision <- precisions[col]
            excStr <- toString(exception)
            if(method == first_method)
            {
              revDfNewLen <- nrow(revDf) + 1
              revDf[revDfNewLen, 1] <- project_name
              revDf[revDfNewLen, 2] <- rev_name
              revDf[revDfNewLen, 3] <- precision
              revDf[revDfNewLen, 4] <- excStr              
            }else{
              revDfNewLen <- which(revDf$Project == project_name & revDf$Rev == rev_name &
                                     revDf$Precision == precision & revDf$Exception == excStr)[1]
            }
            methodDfNewLen <- nrow(methodDf) + 1
            methodDf[methodDfNewLen, 1] <- project_name
            methodDf[methodDfNewLen, 2] <- rev_name
            methodDf[methodDfNewLen, 3] <- method
            methodDf[methodDfNewLen, 4] <- precisions[col]
            methodDf[methodDfNewLen, 5] <- toString(exception)
            detailedPrec <- NULL
            if(!is.null(methods_projectRev_by_excep[[project_name]][[rev_name]][[method]][[exception]])){
              splittedPrecs <- split(methods_projectRev_by_excep[[project_name]][[rev_name]][[method]][[exception]], 
                                     methods_projectRev_by_excep[[project_name]][[rev_name]][[method]][[exception]]$Precision) 
              detailedPrec <- splittedPrecs[[precisions[col]]]
            }
            
            if(!is.null(detailedPrec))
            {
              #splittedDetVios <- detailedPrec$DetailedLineVios
              #print(detailedPrec)
              strHasSrcAndSink <- toString(detailedPrec$HasSourcedAndSink)
              if(method == first_method)
              {              
                #print(detailedPrec)
                revDf[revDfNewLen, 5] <- detailedPrec$SdgNodes
                revDf[revDfNewLen, 6] <- detailedPrec$SdgEdges
                revDf[revDfNewLen, 7] <- detailedPrec$CgNodes
                revDf[revDfNewLen, 8] <- detailedPrec$CgEdges
              }
              currHasSrcAndSink <- revDf[revDfNewLen, 9]
              if(!is.na(strHasSrcAndSink) && 
                   (is.na(currHasSrcAndSink) || 
                      (toString(currHasSrcAndSink) != "Yes" && strHasSrcAndSink == "Yes"))){
                revDf[revDfNewLen, 9] <- strHasSrcAndSink
                revDf[revDfNewLen, 10] <- detailedPrec$LineVios
                revDf[revDfNewLen, 11] <- strsToBoolean(detailedPrec$HasLeftToRightVio, detailedPrec$HasRightToLeftVio)
                revDf[revDfNewLen, 12] <- strsToNumeric(detailedPrec$HasLeftToRightVio, detailedPrec$HasRightToLeftVio)
              }
              methodDf[methodDfNewLen, 6] <- strHasSrcAndSink
              methodDf[methodDfNewLen, 7] <- detailedPrec$LineVios
              methodDf[methodDfNewLen, 8] <- strsToBoolean(detailedPrec$HasLeftToRightVio, detailedPrec$HasRightToLeftVio)
              methodDf[methodDfNewLen, 9] <- strsToNumeric(detailedPrec$HasLeftToRightVio, detailedPrec$HasRightToLeftVio)
              left <- toString(detailedPrec$Left)
              right <- toString(detailedPrec$Right)
              methodDf[methodDfNewLen, 10] <- left
              methodDf[methodDfNewLen, 11] <- right
              
              if(!is.na(left) && !is.na(right))
              {
                getElems <- function(strList){
                  ret <- 0
                  if(strList != "[]"){
                    ret <- length(strsplit(strList,",")[[1]])
                  }
                  return(ret)
                }
                methodDf[methodDfNewLen, 12] <- getElems(left) > 0 && getElems(right) > 0
              }
              phase1Cond <- length(splittedPrecs) == length(precisions) 
              splittedPrecsNames <- names(splittedPrecs)
              phase2CondPart1 <- all(phase2Precisions %in% splittedPrecsNames) && 
                (exception %in% phase2Exceptions) && (precision %in% phase2Precisions)
              if(col < length(precisions) && (phase1Cond || phase2CondPart1)){ #&& length(splittedPrecs) == length(precisions)){
                #colDetVios <- splittedDetVios[[precisions[col]]]
                colDetVios <- detailedPrec$DetailedLineVios
                for(row in ((col + 1):length(precisions))){
                  #print(paste("Row:",row))splittesss
                  rowPrecision <- precisions[row]
                  if(rowPrecision %in% splittedPrecsNames){
                    rowDetVios <- splittedPrecs[[precisions[row]]]$DetailedLineVios              
                    if(!is.na(rowDetVios) && !is.na(colDetVios))
                    {          
                      viosDiffsList <- calculateViosDiffList(rowDetVios, colDetVios)
                      #print(viosDiffsList) 
                      phase2Cond <- phase2CondPart1 && (rowPrecision %in% phase2Precisions)
                      if(phase1Cond || phase2Cond)
                      {
                        onlyColLen <- length(strsplit(viosDiffsList["onlyCol"],";")[[1]])
                        onlyRowLen <- length(strsplit(viosDiffsList["onlyRow"],";")[[1]])
                        
                        if(phase1Cond)
                        {
                          simM[row, col] <- viosDiffsList["both"]
                          simM[col, row] <- viosDiffsList["both"]
                          diffM[row, col] <- viosDiffsList["onlyCol"]
                          diffM[col, row] <- viosDiffsList["onlyRow"]
                          bothLen <- length(strsplit(viosDiffsList["both"],";")[[1]])             
                          
                          newLen <- ifelse(is.na(sumDetViosTable[[exception]]$both[row, col]), bothLen,
                                           sumDetViosTable[[exception]]$both[row, col] + bothLen)
                          sumDetViosTable[[exception]]$both[row, col] <- newLen
                          sumDetViosTable[[exception]]$both[col, row] <- newLen
                          
                          newLen <- ifelse(is.na(sumDetViosTable[[exception]]$diff[row, col]), onlyColLen,
                                           sumDetViosTable[[exception]]$diff[row, col] + onlyColLen)
                          sumDetViosTable[[exception]]$diff[row, col] <- newLen
                          
                          newLen <- ifelse(is.na(sumDetViosTable[[exception]]$diff[col, row]), onlyRowLen,
                                           sumDetViosTable[[exception]]$diff[col, row] + onlyRowLen)
                          sumDetViosTable[[exception]]$diff[col, row] <- newLen
                          if(onlyColLen == 0 && onlyRowLen == 0)
                          {
                            numDetViosTable[[exception]]$both[row, col] <- ifelse(is.na(numDetViosTable[[exception]]$both[row, col]), 1, 
                                                                                  numDetViosTable[[exception]]$both[row, col] + 1)
                            numDetViosTable[[exception]]$both[col, row] <- ifelse(is.na(numDetViosTable[[exception]]$both[col, row]), 1, 
                                                                                  numDetViosTable[[exception]]$both[col, row] + 1)
                          }
                          if(onlyColLen > 0)
                          {
                            numDetViosTable[[exception]]$diff[row, col] <- ifelse(is.na(numDetViosTable[[exception]]$diff[row,col]), 1, 
                                                                                  numDetViosTable[[exception]]$diff[row, col] + 1)
                          }
                          if(onlyRowLen > 0)
                          {
                            numDetViosTable[[exception]]$diff[col, row] <- ifelse(is.na(numDetViosTable[[exception]]$diff[col, row]), 1, 
                                                                                  numDetViosTable[[exception]]$diff[col, row] + 1)
                          }
                        }
                        if(phase2Cond)
                        {
                          phase2Row <- which(rowPrecision == phase2Precisions)[[1]]
                          phase2Col <- which(precision == phase2Precisions)[[1]]
                          if(onlyColLen == 0 && onlyRowLen == 0)
                          {
                            numDetViosTableFilt[[exception]]$both[phase2Row, phase2Col] <- ifelse(is.na(numDetViosTableFilt[[exception]]$both[phase2Row, phase2Col]), 1, 
                                                                                                  numDetViosTableFilt[[exception]]$both[phase2Row, phase2Col] + 1)
                            numDetViosTableFilt[[exception]]$both[phase2Col, phase2Row] <- ifelse(is.na(numDetViosTableFilt[[exception]]$both[phase2Col, phase2Row]), 1, 
                                                                                                  numDetViosTableFilt[[exception]]$both[phase2Col, phase2Row] + 1)
                          }
                          if(onlyColLen > 0)
                          {
                            numDetViosTableFilt[[exception]]$diff[phase2Row, phase2Col] <- ifelse(is.na(numDetViosTableFilt[[exception]]$diff[phase2Row, phase2Col]), 1, 
                                                                                                  numDetViosTableFilt[[exception]]$diff[phase2Row, phase2Col] + 1)
                          }
                          if(onlyRowLen > 0)
                          {
                            numDetViosTableFilt[[exception]]$diff[phase2Col, phase2Row] <- ifelse(is.na(numDetViosTableFilt[[exception]]$diff[phase2Col, phase2Row]), 1, 
                                                                                                  numDetViosTableFilt[[exception]]$diff[phase2Col, phase2Row] + 1)
                          }
                        }
                      }
                    }
                  }
                }
              }
            }                      
          }
          simDetViosTablePerMethodExcep[[project_name]][[rev_name]][[method]][[exception]] <- simM
          diffDetViosTablePerMethodExcep[[project_name]][[rev_name]][[method]][[exception]] <- diffM
        }
      }
      #       print("BEGIN")
      #       print(project_name)
      #       print(rev_name)
      #       aux <- which(methodDf$Project == project_name & methodDf$Rev == rev_name)
      #       print(aux)
      #       aux2 <- which(revDf$Project == project_name & revDf$Rev == rev_name)
      #       print(aux2)
      #       for(exception in exceptions)
      #       {
      #         for(precision in precisions)
      #         {
      #           revLine <- which(revDf$Project == project_name & revDf$Rev == rev_name & 
      #                              revDf$Precision == precision & revDf$Exception == exception)
      #           methodLines <- which(methodDf$Project == project_name & methodDf$Rev == rev_name & 
      #                                  methodDf$Precision == precision & methodDf$Exception == exception & !is.na(methodDf$LineVios))
      #           print(revDf[revLine,])
      #           print("MethodLines")
      #           print(methodLines)
      #           methodLines <- methodDf[methodLines,]
      #           print(methodLines$LineVios)
      #         }
      #       }
      #       
      #       
      #       print("END")
    }
  }
}
if(skipPhase1)
{
  print("Skipping phase 1!")
}else{
  print("Phase 1...")
  if(nrow(revDf) > 0){
    generateExecutionPlots(methodDf, revDf)    
    filteredRevDfsList <- getFilteredConfigsWithoutNa(revDf, c("Project", "Rev"), toEvaluateList)
    filteredMethodDfsList <- getFilteredConfigsWithoutNa(methodDf, c("Project", "Rev", "Method"), c("LineVios"))
    statisticTests <- doStatisticTests(filteredRevDfsList, filteredMethodDfsList)
    stats <- calculateAllStatistics(filteredRevDfsList, filteredMethodDfsList)
  }else{
    print("No Revs evaluated!")
  }
}

if(skipPhase2)
{
  print("Skipping phase 2!")
}else{
  print("Phase 2...")
  if(nrow(revDf) > 0)
  {
    filtRevDf <- filterDesiredConfigs(phase2Exceptions, phase2Precisions, revDf)
    filtMethDf <- filterDesiredConfigs(phase2Exceptions, phase2Precisions, methodDf)
    generatePrecisionPlots(filtMethDf, filtRevDf, toEvaluateList,labelsList, phase2Exceptions, phase2Precisions, "2") 
  }
}
if(nrow(evalRevDf) > 0)
{
  generateDataSummaryPlots(evalRevDf, evalMethDf, builtRevDf, totalRevsDf) 
  if(!exists("filtRevDf"))
  {
    filtRevDf <- NULL
    filtMethDf <- NULL
  }
  printDataSummary(revDf, methodDf, evalRevDf, evalMethDf, builtRevDf, totalRevsDf, editSameMcRevsDf, filtRevDf, filtMethDf)
}
