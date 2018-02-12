package tao.trading.utility;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import org.hsqldb.lib.Iterator;

public class VerifyDB extends DBUtil
{
	public VerifyDB( String connURL)
	{
		super( connURL);
	}
	
	public static void main(String[] args)
	{
		String beginDate = args[0] + "  17:15:00";
		String endDate   = args[1] + "  16:49:00";
		String connectionURL = "jdbc:derby://localhost:1527/QuoteDB";

		VerifyDB vd = new VerifyDB(connectionURL);
		vd.verifyRecords(beginDate, endDate);

	}
	
	
	public void verifyRecords(String beginDate, String endDate)
	{
		SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
		PreparedStatement psSelect, psInsert;

		Vector<String>[] missingDates = new Vector[19];
		for ( int i  = 0; i <= 18; i++ )
			missingDates[i] = new Vector();
		
		try
		{
			Date start = IBDataFormatter.parse(beginDate);
			long mill = start.getTime();
			Date nextWeek = null;
				
			do 
			{
				for ( int i = 0; i <= 7184; i++)
				{
					long mill2 = mill + i * 60 * 1000;
					Date time = new Date();
					time.setTime(mill2);
					String date = IBDataFormatter.format(time);
					//System.out.println(i + " " + IBDataFormatter.format(time));
					
					//17:00 - 17:14 will be empty
					String mins = date.substring(10);
					//System.out.println(mins);
					if (( mins.compareTo("16:59:00") > 0 ) && ( mins.compareTo("17:15:00") < 0 ))
						continue;
					
					for ( int req_id  = 0; req_id <= 18; req_id++ )
					{	
						psSelect = conn.prepareStatement("select * from QUOTE where REQ_ID = ? and DATE= ?");
						psSelect.setInt(1,new Integer(req_id));
						psSelect.setString(2, date);
						
						ResultSet rs = psSelect.executeQuery();
						if (!(rs.next())){
							//System.out.println(req_id + " " + date + " not found");
							missingDates[req_id].add(req_id + " " + date);
						}
						psSelect.close();
					}
				}
				
				mill += 7 * 24 * 60 * 60000;
				nextWeek = new Date();
				nextWeek.setTime(mill);
			}
			while ( endDate.compareTo(IBDataFormatter.format(nextWeek)) > 0 );
			
			for ( int i = 0; i <=18; i++)
			{
				java.util.Iterator it = missingDates[i].iterator();
				while ( it.hasNext())
				{
					System.out.println(it.next());
				}
				
				System.out.println("\n\n");
			}
			
			
	    }
		catch( Exception e)
		{
			e.printStackTrace();
		}

	}

}
