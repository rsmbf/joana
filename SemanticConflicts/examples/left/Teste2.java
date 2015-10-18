package cin.ufpe.br;
public class Teste2 {
	private int a;
	private int b;
	
	public Teste2()
	{
		
		a = 3;
		
		b = 2;
	}
	
	public int m()
	{
		return 0;
	}
	
	public int n()
	{
		return 1;
	}
	
	public int getA()
	{
		return a;
	}
	
	public int getB()
	{
		return b;
	}
	
	public int mnplusAB()
	{
		return m() + n() + a + b;
	}
	
	public static void main(String[] args) {
		Teste2 test = new Teste2();
		boolean test1 = test.m() == 0;
		boolean test2 = test.n() == 1;
		boolean test3 = test.mnplusAB() == 6;

		
		System.out.print("Resultado: ");
		System.out.println(test1 && test2 && test3);
	}
}