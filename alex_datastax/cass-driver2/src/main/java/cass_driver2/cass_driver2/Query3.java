// Author:  Jim Miller

package cass_driver2.cass_driver2;


import com.datastax.driver.core.*;

import java.util.*;


public class Query3 {

	public static void main(String[] args) {
		long startTime = System.nanoTime();
		Cluster cluster = Cluster.builder()
				.addContactPoints("54.186.36.251")
				.build();
		Session session = cluster.connect();

		// Find length_mid for each NB station.
		ResultSet stationID_lengthMidResults = getNBStations(session);
		List<Row> stationID_lengthMidList = new ArrayList<Row>();
		for (Row row : stationID_lengthMidResults) {
			stationID_lengthMidList.add(row);
		}

		// Find total length of 205 NB, just for the heck of it.
		double highwayLength = 0;
		for (Row row : stationID_lengthMidList) {
			highwayLength += row.getDouble(1);
		}
		System.out.println("\nOut of curiosity:  total NB 205 length:  " + highwayLength + " miles\n");

		// Query "LoopData" columnFamily, for each relevant (NB) StationID.
		HashMap<FourTuple, List<Row>> newMap;
		FourTuple timeSignature;
		HashMap<FourTuple, List<Row>> timeSigValueMap;
		List<Row> timeSigRowList;
		List<HashMap<FourTuple, List<Row>>> loopDataMapList = new ArrayList<HashMap<FourTuple, List<Row>>>(6);
		for (int rushHour = 0; rushHour < 6; rushHour++) {
			newMap = new HashMap<FourTuple, List<Row>>();
			loopDataMapList.add(rushHour, newMap);
		}

		for (Row stationRow : stationID_lengthMidList) {
			String cqlQueryLoopData = "SELECT \"StationID\", \"StartHour\", \"StartMinute\", \"DayOfWeek\", \"Speed\","
					+ " \"StartSecond\", \"StartDate\" FROM \"CloudDataMgt\".\"LoopData\" WHERE \"StationID\" = "
					+ stationRow.getInt(0) + " LIMIT 20000000;";
			ResultSet loopDataResults = session.execute(cqlQueryLoopData);

			// Filter rows for specific rush hours (combo of AM/PM and day of week), and create a List of 6 Hashmaps (one for each rush hour)
			// each holding a mapping from an instant's "time signature" to a list of all LoopData rows with that signature.
			// This is in order to group all readings from the same instant in time together, to calculate travel time for the whole NB 205.
			for (Row row : loopDataResults) {
				if (row.getInt(1) >= 7 && row.getInt(1) < 9) {
					if (row.getString(3).equals("Tuesday")) {
						timeSignature = new FourTuple(row.getInt(1), row.getInt(2), row.getInt(5), row.getDate(6).toString());
						timeSigValueMap = loopDataMapList.remove(0);
						timeSigRowList = timeSigValueMap.get(timeSignature);
						if (timeSigRowList == null)
							timeSigRowList = new ArrayList<Row>();
						timeSigRowList.add(row);
						timeSigValueMap.put(timeSignature, timeSigRowList);
						loopDataMapList.add(0, timeSigValueMap);
					}
					else if (row.getString(3).equals("Wednesday")) {
						timeSignature = new FourTuple(row.getInt(1), row.getInt(2), row.getInt(5), row.getDate(6).toString());
						timeSigValueMap = loopDataMapList.remove(1);
						timeSigRowList = timeSigValueMap.get(timeSignature);
						if (timeSigRowList == null)
							timeSigRowList = new ArrayList<Row>();
						timeSigRowList.add(row);
						timeSigValueMap.put(timeSignature, timeSigRowList);
						loopDataMapList.add(1, timeSigValueMap);
					}
					else if (row.getString(3).equals("Thursday")) {
						timeSignature = new FourTuple(row.getInt(1), row.getInt(2), row.getInt(5), row.getDate(6).toString());
						timeSigValueMap = loopDataMapList.remove(2);
						timeSigRowList = timeSigValueMap.get(timeSignature);
						if (timeSigRowList == null)
							timeSigRowList = new ArrayList<Row>();
						timeSigRowList.add(row);
						timeSigValueMap.put(timeSignature, timeSigRowList);
						loopDataMapList.add(2, timeSigValueMap);
					}
				}
				else if (row.getInt(1) >= 16 && row.getInt(1) < 18) {
					if (row.getString(3).equals("Tuesday")) {
						timeSignature = new FourTuple(row.getInt(1), row.getInt(2), row.getInt(5), row.getDate(6).toString());
						timeSigValueMap = loopDataMapList.remove(3);
						timeSigRowList = timeSigValueMap.get(timeSignature);
						if (timeSigRowList == null)
							timeSigRowList = new ArrayList<Row>();
						timeSigRowList.add(row);
						timeSigValueMap.put(timeSignature, timeSigRowList);
						loopDataMapList.add(3, timeSigValueMap);
					}
					else if (row.getString(3).equals("Wednesday")) {
						timeSignature = new FourTuple(row.getInt(1), row.getInt(2), row.getInt(5), row.getDate(6).toString());
						timeSigValueMap = loopDataMapList.remove(4);
						timeSigRowList = timeSigValueMap.get(timeSignature);
						if (timeSigRowList == null)
							timeSigRowList = new ArrayList<Row>();
						timeSigRowList.add(row);
						timeSigValueMap.put(timeSignature, timeSigRowList);
						loopDataMapList.add(4, timeSigValueMap);
					}
					else if (row.getString(3).equals("Thursday")) {
						timeSignature = new FourTuple(row.getInt(1), row.getInt(2), row.getInt(5), row.getDate(6).toString());
						timeSigValueMap = loopDataMapList.remove(5);
						timeSigRowList = timeSigValueMap.get(timeSignature);
						if (timeSigRowList == null)
							timeSigRowList = new ArrayList<Row>();
						timeSigRowList.add(row);
						timeSigValueMap.put(timeSignature, timeSigRowList);
						loopDataMapList.add(5, timeSigValueMap);
					}
				}
			}
		}

		// Calculate and print average travel times for each of the 6 time periods of interest.
		List<Double> avgTravelTimes = new ArrayList<Double>(6);
		Double periodTravelTimeSum;
		Integer periodTravelTimeCount;
		Double instantTravelTimeSum;
		HashMap<FourTuple, List<Row>> periodMap;
		int periodRowStationID;
		for (int rushHour = 0; rushHour < 6; rushHour++) {
			periodTravelTimeSum = 0.0;
			periodTravelTimeCount = 0;
			periodMap = loopDataMapList.get(rushHour);
			for (Map.Entry<FourTuple, List<Row>> mapEntry : periodMap.entrySet()) {
				instantTravelTimeSum = 0.0;
				for (Row rushHourRow : mapEntry.getValue()) {
					periodRowStationID = rushHourRow.getInt(0);
					for (Row stationRow : stationID_lengthMidList) {
						// Find the corresponding LengthMid in the stationID list
						if (stationRow.getInt(0) == periodRowStationID) {
							if (rushHourRow.getInt(4) != 0)
								instantTravelTimeSum += (stationRow.getDouble(1) / (double)rushHourRow.getInt(4));
							break;
						}
					}
				}
				periodTravelTimeSum += instantTravelTimeSum;
				periodTravelTimeCount += 1;
			}
			if (periodTravelTimeCount != 0) {
				avgTravelTimes.add(rushHour, periodTravelTimeSum / (double)periodTravelTimeCount);
			}
			else
				avgTravelTimes.add(rushHour, 0.0);
			switch (rushHour) {
			case 0:  System.out.println("Tuesday AM rush:  " + avgTravelTimes.get(0) * 60 + " minutes");
			break;
			case 1:  System.out.println("Wednesday AM rush:  " + avgTravelTimes.get(1) * 60 + " minutes");
			break;
			case 2:  System.out.println("Thursday AM rush:  " + avgTravelTimes.get(2) * 60 + " minutes");
			break;
			case 3:  System.out.println("Tuesday PM rush:  " + avgTravelTimes.get(3) * 60 + " minutes");
			break;
			case 4:  System.out.println("Wednesday PM rush:  " + avgTravelTimes.get(4) * 60 + " minutes");
			break;
			case 5:  System.out.println("Thursday PM rush:  " + avgTravelTimes.get(5) * 60 + " minutes");
			break;
			default:  System.out.println("This case should not be reached.");
			break;
			}
		}
		long elapsedTime = System.nanoTime() - startTime;
		double seconds = (double)elapsedTime / 1000000000.0;
		System.out.println("\nQuery 3 - Done | Elapsed Time in " + seconds + " seconds");
		session.close(); // finish session
		cluster.close(); // finish cluster connection
		System.exit(0);
	}

	/*
	 * method: getNBStations
	 * argument: Session session - current Cassandra session
	 *
	 * This method will return all northbound StationIDs and their LengthMid in the form of a ResultSet.
	 */
	public static ResultSet getNBStations (Session session) {
		// Query "Stations" columnFamily.
		String cqlQueryStations = "SELECT \"StationID\", \"LengthMid\" FROM \"CloudDataMgt\".\"Stations\""
				+ " WHERE \"ShortDirection\" = 'N' LIMIT 1000000000;";
		return session.execute(cqlQueryStations);
	}
	
	private static class FourTuple {
		private int hour, minute, second;
		private String ts;

		FourTuple(int hour, int minute, int second, String ts) {
			this.hour = hour;
			this.minute = minute;
			this.second = second;
			this.ts = ts;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null)return false;
			if (getClass() != o.getClass()) return false;
			FourTuple other = (FourTuple) o;
			if (hour != other.hour || minute != other.minute || second != other.second || !ts.equals(other.ts))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			int result = hour;
			result = result * 5000 + 60 * minute + second;
			int monthDate = Integer.parseInt(ts.substring(9, 10));
			result = result + 150000 + monthDate;
			if (ts.substring(5, 7).equals("Sep"))
				result *= 2;
			else if (ts.substring(5, 7).equals("Oct"))
				result *= 3;
			else
				result *= 5;
			return result;
		}
	}
}
