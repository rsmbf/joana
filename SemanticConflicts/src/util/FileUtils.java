package util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileUtils {
	public static String getAllJarFiles(String... paths) {
	    StringBuilder sb = new StringBuilder();
	    for (String path : paths) {
	        if (path.endsWith("*")) {
	            path = path.substring(0, path.length() - 1);
	            File pathFile = new File(path);
	            if(pathFile.exists() && pathFile.isDirectory())
	            {
	            	 for (File file : pathFile.listFiles()) {
	 	                if (file.isFile() && file.getName().endsWith(".jar")) {
	 	                    sb.append(path);
	 	                    sb.append(file.getName());
	 	                    sb.append(System.getProperty("path.separator"));
	 	                }
	 	            }
	            }
	           
	        } else {
	            sb.append(path);
	            sb.append(System.getProperty("path.separator"));
	        }
	    }
	    return sb.toString();
	}

	public static void writeNewLine(String path, String line) throws IOException
	{
		BufferedWriter bw = new BufferedWriter(new FileWriter(path, true));
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
		File parent = file.getParentFile();
		if(!parent.exists()){
			parent.mkdirs();
		}
		if (!file.exists()) {
			file.createNewFile();
		}
	}
}
