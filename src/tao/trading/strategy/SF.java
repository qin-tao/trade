package tao.trading.strategy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import tao.trading.Constants;
import tao.trading.QuoteData;

import com.ib.client.Contract;
import com.thoughtworks.xstream.XStream;

public class SF 
{
	
	public void sleep ( int min )
	{
		try{
			Thread.sleep(min*60000L);
		}
		catch ( Exception e)
		{
			e.printStackTrace();
		}
	}

	/*

	public HashMap<String, QuoteData> rebuildQuote( int CHART, QuoteData[] baseQuotes )
	{
		HashMap<String, QuoteData> quotes = new HashMap<String, QuoteData>(500);
		
		int len = baseQuotes.length;
		
		for ( int i = 0; i < len; i++)
		{
			String time = baseQuotes[i].time;
			int hr = new Integer(time.substring(10,12).trim());
			int minute = new Integer(time.substring(13,15));

			if ( CHART == Constants.CHART_3_MIN )
				minute = minute - minute%3;
			else if ( CHART == Constants.CHART_5_MIN )
				minute = minute - minute%5;
			else if ( CHART == Constants.CHART_10_MIN )
				minute = minute - minute%10;
			else if ( CHART == Constants.CHART_15_MIN )
				minute = minute - minute%15;
			else if ( CHART == Constants.CHART_30_MIN )
				minute = minute - minute%30;
			else if ( CHART == Constants.CHART_60_MIN )
				minute = 0;
			else if ( CHART == Constants.CHART_240_MIN )
			{
				hr = hr- hr%4;
				minute = 0;
				
				try
				{
					Date date = InternalDataFormatter.parse(time);
					Calendar calendar = Calendar.getInstance();
					calendar.setTime( date );
					int weekday = calendar.get(Calendar.DAY_OF_WEEK);
					int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
					
					if (( weekday ==  Calendar.SUNDAY ) && ( hour_of_day < 20 ))
					{
						// this date will be conslidate to last friday's 16:00 bar
						long datel = date.getTime(); // milliseconds
						datel -= 48*60*60*1000;
						
						date.setTime(datel);
						time = InternalDataFormatter.format(date);
					}
				}
				catch ( Exception e)
				{
					logger.severe("can not parse date during 240 conversion " + baseQuotes[i].time);
				}
			}
			
			String hrStr = new Integer(hr).toString();
			if ( hr < 10 )
				hrStr = "0" + new Integer(hr).toString();
			else
				hrStr = new Integer(hr).toString();

			String minStr = new Integer(minute).toString();
			if ( minute < 10 )
				minStr = "0" + new Integer(minute).toString();
			else
				minStr = new Integer(minute).toString();
			
			//String indexStr = baseQuotes[i].time.substring(0,13) + minStr;
			String indexStr = time.substring(0,10) + hrStr + ":" + minStr + ":00";
			
			QuoteData qdata = quotes.get(indexStr);
			if ( qdata == null )
			{
				qdata = new QuoteData();
				qdata.open = baseQuotes[i].open;
				qdata.high = baseQuotes[i].high;
				qdata.low = baseQuotes[i].low;
				qdata.close = baseQuotes[i].close;
				qdata.time = indexStr;
			}
			else
			{
				if (baseQuotes[i].high > qdata.high)
					qdata.high = baseQuotes[i].high;
				if (baseQuotes[i].low < qdata.low )
					qdata.low = baseQuotes[i].low;

				qdata.close = baseQuotes[i].close;

			}
			
			quotes.put( indexStr, qdata);
		}
		
		return quotes;
	}*/

	

	
	

}
