package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.util.ArrayList;

public class AppTest 
{

	public static void main(String[] args) throws IOException {
		
		int count = 0;
		
		double cummulative = 0;
		
		var moves = new ArrayList<String>();
		
		while (count < 50) {
			
			double avg = 0;
			int days = 0;
			
			System.out.println("Pass " + count);
	    	for (int y = 2020; y <= 2021; y++) {
	    		for (int m = 1; m <= 12; m++) {
	    			for (int d = 1; d <= 31; d++) {
	    				test(String.format("%02d", d), String.format("%02d", m), Integer.toString(y));
	    				if (App.moves != -1) {
	    					avg += App.moves;
	    					moves.add(String.format("%d%n", App.moves));
	    					days += 1;
	    				}
    					App.moves = -1;
	    			}
	    		}
	    	}
	    	count += 1;
	    	avg = avg / days;
	    	cummulative += avg;
	    	
	    	System.out.println(cummulative / count);
	    	
		}
		
		App.writeFile("moveResults.txt", String.join("", moves));
		System.out.println("done!");
		
	}
	
    public static void test(String day, String month, String year) {
    	App.main(new String[] {day, month, year, "55.9444", "-3.1878", "5678", "80"});
    }
}
