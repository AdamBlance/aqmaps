package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.inRange;

public class Drone {
	
	private Point position;
	private int timesMoved = 0;
	
	// These constants are public so that they can be easily accessed
	public static final int MAX_MOVES = 150;
	public static final double MOVE_DISTANCE = 0.0003;
	public static final double SENSOR_READ_DISTANCE = 0.0002;
	public static final double END_POINT_DISTANCE = 0.0003;
	
	// Creates drone with the specified start position
	public Drone(Point startPosition) {
		position = startPosition;
	}

	// Moves the drone with the provided bearing if it is not out of moves
	// Returns true if the move was successful, false otherwise
	public boolean move(int bearing) {
		if (timesMoved >= MAX_MOVES) {
			return false;
		}
		position = moveDestination(position, bearing);
		timesMoved += 1;
		return true;
	}

	// Returns the pollution reading of the provided sensor if it is in range
	public double readSensor(Sensor sensor) {
		if (inRange(position, sensor)) {
			return sensor.getReading();
		}
		System.out.println("Fatal error: Drone tried to read sensor that was out of range. Exiting...");
		System.exit(1);
		return 0;  // Method always needs to return something
	}
	
	public Point getPosition() {
		return position;
	}

	public int getTimesMoved() {
		return timesMoved;
	}
	
}
