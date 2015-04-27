/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package examples;

public class B extends Thread {
	int x = 0;

	public static void main(String[] args) {
		B a = new B();
		synchronized(a){
			a.start();
			//try {this.wait();} catch (InterruptedException e) {}
			System.out.println(a.x);
		}
	}

	public void run() {
		synchronized(this){
			System.out.println(x);
			x = 17;
			//try {this.wait();} catch (InterruptedException e) {}
		}
		x = 42;
	}
}
