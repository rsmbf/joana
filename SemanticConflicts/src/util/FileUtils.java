package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
	public static String[] getAllJarFiles(String baseFolderStr, String pathsStr)
	{
		List<String> jars = new ArrayList<String>();
		List<String> jarNames = new ArrayList<String>();
		List<String> jarsToEvaluate = new ArrayList<String>();
		String[] paths = pathsStr.split(System.getProperty("path.separator"));
		String[] jarPaths = getAllJarFilesStr(paths).split(System.getProperty("path.separator"));
		
		for(int i = 0; i < jarPaths.length; i++)
		{
			if(new File(jarPaths[i]).getParent().contains(baseFolderStr))
			{
				jars.add(jarPaths[i]);
				jarNames.add(new File(jarPaths[i]).getName());
			}else{
				jarsToEvaluate.add(jarPaths[i]);
			}
		}
		for(String jarPath : jarsToEvaluate)
		{
			String jarName = new File(jarPath).getName();
			if(!jarNames.contains(jarName))
			{
				jars.add(jarPath);
				jarNames.add(jarName);
			}
		}

		return jars.toArray(new String[0]);
	}

	public static String getAllJarFilesStr(String... paths) {
		StringBuilder sb = new StringBuilder();
		for (String path : paths) {
			if (path.endsWith("*")) {
				path = path.substring(0, path.length() - 1);
			}
			File pathFile = new File(path);
			sb.append(getAllJarFiles(pathFile));
			/*
			if(pathFile.exists())
			{
				if(pathFile.isDirectory())
				{
					for (File file : pathFile.listFiles()) {
						if (file.isFile() && file.getName().endsWith(".jar")) {
							/*
							sb.append(path);
							if(!path.endsWith(File.separator))
							{
								sb.append(File.separator);
							}
							sb.append(file.getName());
							* /
							sb.append(file.getAbsolutePath());
							sb.append(System.getProperty("path.separator"));
						}
					}
				}else if(pathFile.isFile() && pathFile.getName().endsWith(".jar")){
					sb.append(path);
					sb.append(System.getProperty("path.separator"));
				}
			}
			*/
		}

		return sb.toString();
	}

	private static String getAllJarFiles(File pathFile) {
		StringBuilder sb = new StringBuilder();
		if(pathFile.exists())
		{
			if(pathFile.isDirectory())
			{
				for (File file : pathFile.listFiles()) {
					sb.append(getAllJarFiles(file));
				}
			}else if(pathFile.isFile() && pathFile.getName().endsWith(".jar")){
				sb.append(pathFile.getAbsolutePath());
				sb.append(System.getProperty("path.separator"));
			}
		}
		return sb.toString();
	}

	public static void writeNewLine(String path, String line) throws IOException
	{
		writeNewLine(path, line, true);
	}

	public static void writeNewLine(String path, String line, boolean append) throws IOException
	{
		BufferedWriter bw = new BufferedWriter(new FileWriter(path, append));
		bw.write(line + "\n");
		bw.close();
		System.out.println(line);
	}
	
	public static void write(String path, String line) throws IOException
	{
		BufferedWriter bw = new BufferedWriter(new FileWriter(path, true));
		bw.write(line);
		bw.close();
		System.out.print(line);
	}

	public static void createFile(String newClassPath) throws IOException {
		File file = new File(newClassPath);
		if(file.exists())
		{
			file.delete();
		}
		
		if (!file.exists()) {
			mkdirs(file);
			file.createNewFile();
		}
	}

	public static void mkdirs(File file) {
		File parent = file.getParentFile();
		if(!parent.exists()){
			parent.mkdirs();
		}
	}
	
	public static String readFirstLine(String path) throws IOException
	{
		List<String> lines = readNLines(path, 1);
		return lines.size() > 0 ? lines.get(0) : null;
	}
	
	public static List<String> readNLines(String path, int number) throws IOException
	{
		List<String> lines = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line;
		while((line = br.readLine()) != null && number > 0)
		{
			lines.add(line);
			number--;
		}
		br.close();
		return lines;
	}

	public static void printFileContent(String path) throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line;
		while((line = br.readLine()) != null)
		{
			System.out.println(line);
		}
		br.close();
	}
	
	public static List<String> getFileLines(String path) throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line;
		List<String> lines = new ArrayList<String>();
		while((line = br.readLine()) != null)
		{
			lines.add(line);
		}
		br.close();
		return lines;
	}
	
	public static void main(String[] args) throws IOException {
		String[] res = getAllJarFiles("/Users/Roberto/Documents/UFPE/Msc/Projeto/conflicts_analyzer/downloads/mongo-java-driver/editsamemc_revisions/rev_8e5de_c4707/rev_8e5de-c4707/git/target/dependency",
				"/Users/Roberto/Documents/UFPE/Msc/Projeto/conflicts_analyzer/downloads/mongo-java-driver/editsamemc_revisions/rev_8e5de_c4707/rev_8e5de-c4707/git/target/dependency:"
				+ "/Users/Roberto/Documents/UFPE/Msc/Projeto/conflicts_analyzer/downloads/mongo-java-driver/editsamemc_revisions/rev_8e5de_c4707/rev_8e5de-c4707/git/lib");
		for(String str : res)
			System.out.println(str);
		//System.out.println(readFirstLine("/Users/Roberto/Desktop/empty.txt"));
		//FileUtils.writeNewLine("/Users/Roberto/Desktop/empty.txt", "d");
		/*
		String projectPath = "/Users/Roberto/Documents/UFPE/Msc/Projeto/conflicts_analyzer/downloads/druid/editsamemc_revisions/";
		String left_id = "07131";
		String right_id = "21613";
		projectPath += "rev_"+left_id+"_"+right_id+"/rev_"+left_id+"-"+right_id;
		projectPath += "/git";
		String libs = projectPath + "/merger/target/dependency/*";
		libs += ":"+projectPath + "/common/target/dependency/*";
		libs += ":"+projectPath + "/index-common/target/dependency/*";
		libs += ":"+projectPath + "/client/target/dependency/*";
		libs += ":"+projectPath + "/server/target/dependency/*";
		libs += ":"+projectPath + "/indexer/target/dependency/*";
		libs += ":"+projectPath + "/realtime/target/dependency/*";
		for(String str : getAllJarFiles(projectPath + "/merger/target/dependency",libs))
		{
			System.out.println(str);
		}
		*/
	}
}
