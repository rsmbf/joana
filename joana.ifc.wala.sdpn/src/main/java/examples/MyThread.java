/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package examples;

public class MyThread extends Thread{

	public static void main (String[] args) {
		createAndRun();
	}
	public static void createAndRun() {
		MyThread t = new MyThread();
		t.start();
	}
}
