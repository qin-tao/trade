package tao.trading.strategy.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import tao.trading.Constants;
import tao.trading.QuoteData;

public class TimeUtil
{
	static public boolean atNight( String time )
	{
		int hr = new Integer(time.substring(10,12).trim());
		//int minute = new Integer(time.substring(13,15));

		/*
		String hrStr = new Integer(hr).toString();
		if ( hr < 10 )
			hrStr = "0" + new Integer(hr).toString();
		else
			hrStr = new Integer(hr).toString();
		 */
		if ((hr > 20) || (hr < 6))
			return true;
		else
			return false;

	}
	
	
	static public int timeDiffInHr( String time1, String time2 )
	{
		try{
			Date t1 = Constants.IBDataFormatter.parse(time1);
			Date t2 = Constants.IBDataFormatter.parse(time2);
			
			return (int)((t1.getTime() - t2.getTime())/(60*60000L));
		}
		catch (Exception e)
		{
			return 0;
		}
		
		
	}
	
	
	static public int get60Pos( String time, QuoteData[] quotes )
	{
		//spublic static SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss")
		int hr = new Integer(time.substring(10,12).trim());
		int minute = new Integer(time.substring(13,15));
		
		String hrStr = new Integer(hr).toString();
		if ( hr < 10 )
			hrStr = "0" + new Integer(hr).toString();
		else
			hrStr = new Integer(hr).toString();
		
		time = time.substring(0,10)	+ hrStr + ":00:00";
		
		return findPositionByHour(quotes, time, Constants.FRONT_TO_BACK );
		
	}

	static public int get240Pos( String time, QuoteData[] quotes )
	{
		//spublic static SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss")
		int hr = new Integer(time.substring(10,12).trim());
		int minute = new Integer(time.substring(13,15));
		
		hr = hr - hr%4;
		String hrStr = new Integer(hr).toString();
		if ( hr < 10 )
			hrStr = "0" + new Integer(hr).toString();
		else
			hrStr = new Integer(hr).toString();
		
		time = time.substring(0,10)	+ hrStr + ":00:00";
		
		return findPositionByHour(quotes, time, Constants.FRONT_TO_BACK );
		
	}

	
	
	static public int findPositionByHour(QuoteData[] quotes, String timeStr, int order )
	{
		String time = timeStr.substring(0,12);
		
		if ( order == Constants.BACK_TO_FRONT)  // back to front
		{
			int i = quotes.length - 1;
			while ( i >= 0 )
			{
				if (quotes[i].time.substring(0,12).equals(time))
					return i;
				i--;
			}
		}
		else if (order == Constants.FRONT_TO_BACK )  // front to back
		{
			int i = 0;
			int lastbar = quotes.length - 1;
			while ( i <= lastbar )
			{
				if (quotes[i].time.substring(0,12).equals(time))
					return i;
				i++;
			}
		}
		
		return Constants.NOT_FOUND;
	}

	
	static public int findPositionByMinute(QuoteData[] quotes, String timeStr, int order )
	{
		String time = timeStr.substring(0,15);
		
		if ( order == Constants.BACK_TO_FRONT)  // back to front
		{
			int i = quotes.length - 1;
			while ( i >= 0 )
			{
				if (quotes[i].time.substring(0,15).equals(time))
					return i;
				i--;
			}
		}
		else if (order == Constants.FRONT_TO_BACK )  // front to back
		{
			int i = 0;
			int lastbar = quotes.length - 1;
			while ( i <= lastbar )
			{
				if (quotes[i].time.substring(0,15).equals(time))
					return i;
				i++;
			}
		}
		
		return Constants.NOT_FOUND;
	}


}
