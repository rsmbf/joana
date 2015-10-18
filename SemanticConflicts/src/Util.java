import java.util.ArrayList;
import java.util.List;


public class Util {	
	public static List<String> getArgs(String signature){
		String parameters= "";
		String aux 		 = "";

		signature = signature.replaceAll("\\s+","");

		for (int i = 0, n = signature.length(); i < n; i++) {
			char chr = signature.charAt(i);
			if (chr == '('){
				aux = signature.substring(i+1,signature.length());
				break;
			}
		}
		for (int i = 0, n = aux.length(); i < n; i++) {
			char chr = aux.charAt(i);
			if (chr == ')'){
				break;
			}else
				parameters += chr;
		}
		List<String> list = new ArrayList<String>();
		String[] duplicArgs = parameters.split("-");
		for(int i = 0; i < duplicArgs.length; i += 2){
			list.add(duplicArgs[i]);
		}
		return list;		
	}
	
	public static void main(String[] args) {
		System.out.println(getArgs("soma(int-int-boolean-boolean) throws Exception"));
		System.out.println(getArgs("int soma()"));
		System.out.println(getArgs("public static void int soma(int-int-int-int)"));
	}
}