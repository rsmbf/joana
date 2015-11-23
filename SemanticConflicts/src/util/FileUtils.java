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
}
