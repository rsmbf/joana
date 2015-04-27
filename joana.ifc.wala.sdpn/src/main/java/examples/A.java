/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package examples;

public class A extends Thread {
	static int x = 0;

	public static void main(String[] args) {
		A a = new A();
		synchronized(a){
			a.start();
//			try {a.wait();} catch (InterruptedException e) {}
			System.out.println(x);
		}
	}

	public void run() {
		synchronized(this){
			System.out.println(x);
			x = 17;
//			try {this.wait();} catch (InterruptedException e) {}
		}
		x = 42;
	}
}
