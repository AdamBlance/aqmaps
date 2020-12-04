package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;


public class FlightPlanner {
	
	// Returns a path (ordered list of Sensors) generated using the greedy TSP algorithm
	public static List<Sensor> greedyPath(Point start, List<Sensor> sensors) {		
		var dronePath = new ArrayList<Sensor>();  // The generated greedy path
		var unvisited = new ArrayList<Sensor>(sensors);  // Put all sensors initially in the unvisited list
		var currentPoint = start;
		
		for (int i = 0; i < sensors.size(); i++) {
			var closest = closestSensor(currentPoint, unvisited);
			currentPoint = closest.getPoint();
			dronePath.add(closest);                   // Put the current closest sensor in the greedy path
			unvisited.remove(closest);                // Mark it as visited
		}
		return dronePath;
	}
	
	// Optimises and returns the provided path (ordered list of Sensors) using the 2-opt path optimisation algorithm
	public static List<Sensor> twoOptPath(Point start, List<Sensor> sensors) {
		var startWaypoint = new StartEndPoint(start);
		
		// I didn't fancy using modulo because Java's % operator seems to do weird things sometimes
		// To make things easier, I just put the start/end point at both ends of the path so path[i-1] and path[i+1] won't jump out of the array
		List<Waypoint> path = new ArrayList<>();
		path.add(startWaypoint);
		path.addAll(greedyPath(start, sensors));
		path.add(startWaypoint);
		
		// Repeatedly make 2-opt moves until there are none left that shorten the path
		boolean improved = true;
		while (improved) {
			improved = false;
			outerloop:
			for (int i = 1; i <= path.size() - 3; i++) {
				for (int j = i+1; j <= path.size() - 2; j++) {  // i-j (inclusive) define the sub-path
					if (reversalImprovesPath(path, i, j)) {     // Does reversing the sub-path shorten the path's length?
						path = modifiedPath(path, i, j);        // Then reverse the sub-path
						improved = true;					
						break outerloop;
					}
				}
			}
		}
		var sensorPath = path.subList(1, path.size() - 1)  // Remove the start and end of the path (the start/end points)
				.stream()
				.map(waypoint -> (Sensor) waypoint)        // Cast from List<Waypoint> to List<Sensor>
				.collect(Collectors.toList());

		return sensorPath;
	}
	
	// Returns true if reversing the sub-path i-j (inclusive) decreases the overall length of the path
	private static boolean reversalImprovesPath(List<Waypoint> path, int i, int j) {
		
		var beforeStartP = path.get(i - 1).getPoint(); // sensor right before the beginning of the sub-path
		var startP = path.get(i).getPoint();	       // sensor at the beginning of the sub-path
		var endP = path.get(j).getPoint();             // sensor at the end of the sub-path
		var afterEndP = path.get(j + 1).getPoint();    // sensor right after the end of the sub-path
		
		// We don't have to evaluate the entire path length, just the connections at the start and end
		double lengthBefore = distanceBetween(beforeStartP, startP) + distanceBetween(endP, afterEndP);  // before 2-opt move
		double lengthAfter = distanceBetween(beforeStartP, endP) + distanceBetween(startP, afterEndP);   // after 2-opt move
				
		return lengthAfter < lengthBefore;
	}
	
	// Returns the provided path with the sub-path i-j (inclusive) reversed
	private static List<Waypoint> modifiedPath(List<Waypoint> path, int i, int j) {
		var output = new ArrayList<>(path);              // Just copying the path here
		for (int curr = i; curr <= j; curr++) {
			output.set(curr, path.get(j - (curr - i)));  // Writing the reversed sub-path to the copy 
		}
		return output;
	}
	
	// Returns the sensor closest to point in the provided list of sensors
	private static Sensor closestSensor(Point point, List<Sensor> sensors) {
		// Just gets the sensor with the minimum distance from point using streams (just fancied using streams)
		// I know this isn't super efficient because we're computing the same thing more than once, but euclidean distance takes basically no time
		return sensors.stream()
				.min((Sensor a, Sensor b) -> Double.compare(distanceBetween(point, a.getPoint()), distanceBetween(point, b.getPoint())))
				.get();
	}
	
}
