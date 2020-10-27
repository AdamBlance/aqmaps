package uk.ac.ed.inf.aqmaps;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class LogWriter {

	String filename;
	List<LogData> logs;
	
	public LogWriter(List<LogData> logs, String day, String month, String year) {
		
		filename = String.format("flightpath-%s-%s-%s.txt", day, month, year);
		this.logs = logs;
	}
	
	// This should maybe return boolean or something
	// Really need to sort out the throws things
	public void writeLogs() throws IOException {
		var file = new File(filename);
		file.createNewFile();
    	var writer = new FileWriter(filename);
    	for (var log : logs) writer.write(log.toString());
    	writer.close();
	}
	
}
