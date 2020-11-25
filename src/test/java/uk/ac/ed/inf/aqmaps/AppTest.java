package uk.ac.ed.inf.aqmaps;

import com.sun.tools.javac.Main;

public class AppTest 
{

	public static void main(String[] args) throws Exception {
    	for (int y = 2020; y <= 2021; y++) {
    		for (int m = 1; m <= 12; m++) {
    			for (int d = 1; d <= 31; d++) {
    				test(String.format("%02d", d), String.format("%02d", m), Integer.toString(y));
    			}
    		}
    	}
	}
	
    public static void test(String day, String month, String year) throws Exception {
        Main.main(new String[] {"01"});
    }
}
