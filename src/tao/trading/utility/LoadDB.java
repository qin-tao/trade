package tao.trading.utility;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

public class LoadDB extends DBUtil
{
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");

	public static void main(String[] args)
	{
		String connURL = "jdbc:derby://localhost:1527/QuoteDB";
		String historyFileName = args[0];

		LoadDB ld = new LoadDB(connURL);
		ld.readHistoryData(historyFileName);
	}


	public LoadDB( String connURL)
	{
		super( connURL);
	}
	
	
	protected void readHistoryData(String historyFileName)
	{
		PreparedStatement psSelect, psInsert;

		String reqId;
		String date;
		String open;
		String high;
		String low;
		String close;
		String volume;
		String count;
		String WAP;
		String hasGaps;

		try
		{
			// csv file containing data
			String strFile = historyFileName;
			BufferedReader br = new BufferedReader(new FileReader(strFile));

			System.out.println("load history: " + historyFileName + "...");

			String strLine = "";
			StringTokenizer st = null;
			int lineNumber = 0;
			int duplicats = 0;
			int difference = 0;

			// read comma separated file line by line
			while ((strLine = br.readLine()) != null)
			{
				//lineNumber++;
				// break comma separated line using ","
				st = new StringTokenizer(strLine, ",");

				// System.out.println(strLine);
				// while(st.hasMoreTokens())
				{
					reqId = st.nextToken();
					if ( reqId.length() > 2 )
						reqId = reqId.substring(1);
					
					date = st.nextToken();
					if (date.indexOf("finished-") != -1)
						continue;
					if (date.indexOf(":") == -1)
						date = IBDataFormatter.format(new Date( new Long(date)*1000));

					open = st.nextToken();
					high = st.nextToken();
					low = st.nextToken();
					close = st.nextToken();
					volume = st.nextToken();
					count = st.nextToken();
					WAP = st.nextToken();
					hasGaps = st.nextToken();
				}

				// historicalData(new Integer(reqId), date, new Double(open),
				// new Double(high), new Double(low), new Double(close),
				// new Integer(volume), new Integer(count), new Double(WAP), new
				// Boolean(hasGaps));
				
				psSelect = conn.prepareStatement("select * from QUOTE where REQ_ID = ? and DATE= ?");
				psSelect.setInt(1,new Integer(reqId));
				psSelect.setString(2, date);
				
				ResultSet rs = psSelect.executeQuery();
				boolean exist = false;
				if (rs.next()){
				
				  int r_req_id = rs.getInt(1);
				  String r_date = rs.getString(2);
				  double r_open = rs.getDouble(3);
				  double r_high = rs.getDouble(4);
				  double r_low = rs.getDouble(5);
				  double r_close = rs.getDouble(6);
				  int r_volumne = rs.getInt(7);
				  int r_count = rs.getInt(8);
				  double r_WAP = rs.getDouble(9);
				  boolean r_hasGap = rs.getBoolean(10);

				  if (( r_open != new Double(open) ) || ( r_high != new Double(high) ) || ( r_low != new Double(low) ) || ( r_close != new Double(close) ))
				  {
					  System.out.println("Data discrpency:");
					  System.out.println("In DB" + " " + reqId + " " + date + " " + r_open + " " + r_high + " " + r_low + " " + r_close + " " + r_volumne + " " + r_count + " " + r_WAP + " " + r_hasGap);
					  System.out.println("New  " + " " + reqId + " " + date + " " + open + " " + high + " " + low + " " + close + " " + volume + " " + count + " " + WAP + " " + hasGaps);
					  difference++;
					  
					  // save to temp db to resolve the difference later
						psInsert = conn
								.prepareStatement("insert into QUOTE_temp(REQ_ID, DATE, O, H, L, C, VOLUME, COUNT, WAP, HASGAPS) values (?,?,?,?,?,?,?,?,?,?)");
						psInsert.setInt(1, new Integer(reqId));
						psInsert.setString(2, date);
						psInsert.setDouble(3, new Double(open));
						psInsert.setDouble(4, new Double(high));
						psInsert.setDouble(5, new Double(low));
						psInsert.setDouble(6, new Double(close));
						psInsert.setInt(7, new Integer(volume));
						psInsert.setInt(8, new Integer(count));
						psInsert.setDouble(9, new Double(WAP));
						psInsert.setBoolean(10, new Boolean(hasGaps));
						psInsert.executeUpdate();
						psInsert.close();
				  }
				  
				  exist = true;
				  duplicats ++;
				}
				
				if ( exist )
					continue;
				
				

				System.out.println("Insert " + reqId + " " + date + " " + open + " " + high + " " + low + " " + close + " " + volume + " " + count + " " + WAP
						+ " " + hasGaps);

				psInsert = conn
						.prepareStatement("insert into QUOTE(REQ_ID, DATE, O, H, L, C, VOLUME, COUNT, WAP, HASGAPS) values (?,?,?,?,?,?,?,?,?,?)");
				psInsert.setInt(1, new Integer(reqId));
				psInsert.setString(2, date);
				psInsert.setDouble(3, new Double(open));
				psInsert.setDouble(4, new Double(high));
				psInsert.setDouble(5, new Double(low));
				psInsert.setDouble(6, new Double(close));
				psInsert.setInt(7, new Integer(volume));
				psInsert.setInt(8, new Integer(count));
				psInsert.setDouble(9, new Double(WAP));
				psInsert.setBoolean(10, new Boolean(hasGaps));
				psInsert.executeUpdate();
				psInsert.close();

				// System.out.println("1 row inserted");
				lineNumber++;
			}
			
			System.out.println(duplicats + " duplicats found");
			System.out.println(difference + " difference found");
			System.out.println(lineNumber + " rows inserted");

		} catch (Exception e)
		{
			e.printStackTrace();
		}

	}
	


}
