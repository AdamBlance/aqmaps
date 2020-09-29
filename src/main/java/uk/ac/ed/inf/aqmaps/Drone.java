package uk.ac.ed.inf.aqmaps;

import java.lang.Math;

public class Drone {
	
	private double latitude;  // north-south movement
	private double longitude; // east-west movement
	
	private int timesMoved = 0;
	
	private static final double MOVE_DISTANCE = 0.0003;
	private static final int MAX_MOVES = 150;
	
	// should maybe standardise lat(itude) etc
	
	public Drone(double initialLong, double initialLat) {
		latitude = initialLat;
		longitude = initialLong;
		
		
		
	}
	
	public DroneStatus move(int bearing) {
		if (timesMoved == 150) {
			return DroneStatus.OUT_OF_MOVES;
		}
		if (bearing % 10 == 0 
				&& bearing >= 0 
				&& bearing <= 350) {
			
			// we need to check if its legal to move first
			
			
			
			latitude += MOVE_DISTANCE*Math.sin(bearing);
			longitude += MOVE_DISTANCE*Math.cos(bearing);
			return DroneStatus.ILLEGAL;
			
		} else {
			throw new IllegalArgumentException("Invalid bearing");
		}
	}
	
	public boolean isLegal(double latitude, double longitude) {
		return true;
		// Honestly we need the webserver stuff first before we do other things cause we need the data
	}
	
	
	
}
