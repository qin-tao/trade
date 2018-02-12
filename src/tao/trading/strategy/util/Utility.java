package tao.trading.strategy.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Vector;

import tao.trading.Constants;
import tao.trading.Push;
import tao.trading.QuoteData;
import tao.trading.dao.ConsectiveBars;

public class Utility
{
	static public double makeSameLength( double num1, double num2 )
	{
		String num1str = (new Double(num1)).toString();
		int len1 = num1str.length();
		
		String num2str = (new Double(num1)).toString();
		int len2 = num2str.length();
		if (len2 < len1)
		{
			return num2;
		}
		else
		{
			num2str = num2str.substring(0, len1-1);
			return new Double(num2str);
		}
	}
	
	
	static public int getQuotePositionByHour(Object[] quotes, String time)
	{
		int size = quotes.length-1;
		for ( int i = size; i >= 0; i--)
		{
			//System.out.println("time=" + time.substring(0,12));
			//System.out.println("quotetime=" + ((QuoteData)quotes[i]).time.substring(0, 12));
			
			//SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
			//														   0123456789ab		
		    if (time.substring(0,12).equals(((QuoteData)quotes[i]).time.substring(0, 12)))
		    		return i;
			
		}
		
		return Constants.NOT_FOUND;
	}

	static public int getQuotePositionByMinute(Object[] quotes, String time)
	{
		int size = quotes.length-1;
		for ( int i = size; i >= 0; i--)
		{
			//System.out.println("time=" + time.substring(0,12));
			//System.out.println("quotetime=" + ((QuoteData)quotes[i]).time.substring(0, 12));
			
			//SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
			//														   0123456789abcde		
		    if (time.substring(0,15).equals(((QuoteData)quotes[i]).time.substring(0, 15)))
		    		return i;
		    
		    if ((time.substring(0,15).compareTo(((QuoteData)quotes[i-1]).time.substring(0, 15)) > 0 ) &&  
		    		(time.substring(0,15).compareTo(((QuoteData)quotes[i]).time.substring(0, 15)) < 0 ))
		    	return i-1;
			
		}
		
		return Constants.NOT_FOUND;
	}

	
	static public int getQuotePositionByHigh(Object[] quotes, double high)
	{
		int size = quotes.length-1;
		for ( int i = size; i >= 0; i--)
		{
		    if (((QuoteData)quotes[i]).high == high)
		    		return i;
		}
		
		return Constants.NOT_FOUND;
	}
	
	static public int getQuotePositionByLow(Object[] quotes, double low)
	{
		int size = quotes.length-1;
		for ( int i = size; i >= 0; i--)
		{
		    if (((QuoteData)quotes[i]).low == low)
		    		return i;
		}
		
		return Constants.NOT_FOUND;
	}

	
	static public double getHigh(Vector<QuoteData> quotes)
	{
		Iterator iterator = quotes.iterator();

		double high = 0;
		while (iterator.hasNext())
		{
			QuoteData quote = (QuoteData) (iterator.next());
			if (quote.high > high)
				high = quote.high;
		}
		return high;
	}

	static public double getLow(Vector<QuoteData> quotes)
	{
		Iterator iterator = quotes.iterator();

		double low = 999;
		while (iterator.hasNext())
		{
			QuoteData quote = (QuoteData) (iterator.next());
			if (quote.low < low)
				low = quote.low;
		}
		return low;
	}

	
	static public QuoteData getHigh(Object[] quotes, int startPos, int endPos )
	{
		if ((endPos == 0 )  || ( endPos < startPos))
			return null;
		
		QuoteData high = new QuoteData();
		high.high = ((QuoteData)quotes[startPos]).high;
		high.pos = startPos;
		high.time = ((QuoteData)quotes[startPos]).time;

		for ( int i = startPos; i <= endPos; i++ )
		{
			if (((QuoteData)quotes[i]).high > high.high)
			{
				high.high = ((QuoteData)quotes[i]).high;
				high.low = ((QuoteData)quotes[i]).low;
				high.pos = i;
				high.time = ((QuoteData)quotes[i]).time;
			}
		}
		
		return high;
		
	}

	static public QuoteData getLow(Object[] quotes, int startPos, int endPos )
	{
		if ((endPos == 0 )  || ( endPos < startPos))
			return null;

		QuoteData low = new QuoteData();
		low.low = ((QuoteData)quotes[startPos]).low ; 
		low.pos = startPos;
		low.time = ((QuoteData)quotes[startPos]).time;
		
		for ( int i = startPos; i <= endPos; i++ )
		{
			if (((QuoteData)quotes[i]).low < low.low )
			{	
				low.high = ((QuoteData)quotes[i]).high;
				low.low = ((QuoteData)quotes[i]).low;
				low.pos = i;
				low.time = ((QuoteData)quotes[i]).time;
			}
		}
		
		return low;

		/*
		QuoteData low = (QuoteData)quotes[startPos];
		
		for ( int i = startPos; i <= endPos; i ++ )
		{
			if (((QuoteData)quotes[i]).low < low.low )
			{	
				low = (QuoteData)quotes[i];
			}
		}
		
		return low; */

	
	}
	

	
	static public QuoteData getHighOpenClose(QuoteData[] quotes, int startPos, int endPos )
	{
		if ((endPos == 0 )  || ( endPos < startPos))
			return null;
		
		QuoteData high = new QuoteData();
		high.high = 0;

		for ( int i = startPos; i <= endPos; i++ )
		{
			if ((quotes[i].open > high.high) || (quotes[i].close > high.high)) 
			{
				high = quotes[i];
			}
		}
		
		return high;
		
	}


	static public QuoteData getLowOpenClose(QuoteData[] quotes, int startPos, int endPos )
	{
		if ((endPos == 0 )  || ( endPos < startPos))
			return null;
		
		QuoteData low = new QuoteData();
		low.low = 999999;

		for ( int i = startPos; i <= endPos; i++ )
		{
			if ((quotes[i].open < low.low ) | (quotes[i].close < low.low)) 
			{
				low = quotes[i];
			}
		}
		
		return low;
		
	}

	
	


	static public double getAverage(Vector<QuoteData> quotes)
	{
		Iterator iterator = quotes.iterator();

		double sum = 0;
		double total = 0;
		while (iterator.hasNext())
		{
			QuoteData quote = (QuoteData) (iterator.next());
			sum += (quote.high - quote.low);
			total++;
		}
		
		return sum/total;
	}
	
	static public double getAverage(Object[] quotes)
	{
		int size = quotes.length;
		double sum = 0;
		for ( int i = 0; i < size; i++)
		{
			sum += (((QuoteData)quotes[i]).high - ((QuoteData)quotes[i]).low);
		}
		
		return sum/size;
	}

	static public double getAverage(Object[] quotes, int begin, int end )
	{
		double sum = 0;
		for ( int i = begin; i <= end; i++)
		{
			sum += (((QuoteData)quotes[i]).high - ((QuoteData)quotes[i]).low);
		}
		
		return sum/((double)end-(double)begin);
	}

	static public int findQuotebyDate( Object[] quotes, String date)
	{
		int lastBar = quotes.length-1;
		while (!(((QuoteData)quotes[lastBar]).time.equals(date)) && (lastBar > 0 ))
			lastBar--;
		
		return lastBar;
		
	}
	
	static public int findMAMoveUpDuration(Object[] quotes, double[] ema, int endPos )
	{
		for ( int i = endPos-1; i > 0 ; i-- )
		{
			if ((((QuoteData)quotes[i]).high < ema[i]) && ( ((QuoteData)quotes[i-1]).high < ema[i-1]))
				return endPos - i;
		}
		
		return Constants.NOT_FOUND;
	}

	static public int findMAMoveDownDuration(Object[] quotes, double[] ema, int endPos )
	{
		for ( int i = endPos-1; i > 0 ; i-- )
		{
			if ((((QuoteData)quotes[i]).low > ema[i]) && (((QuoteData)quotes[i-1]).low < ema[i-1]))
				return endPos - i;
		}
		
		return Constants.NOT_FOUND;
	}

	
	static public void setQuotePositions( Object[] quotes )
	{
		int lastbar = quotes.length -1;
		
		for ( int i = 0; i <= lastbar; i++)
			((QuoteData) quotes[i]).pos = i;
	}
	

	static public int findFirstPriceHigh(QuoteData[] quotes, int start, double high )
	{
		int lastbar = quotes.length - 1;
		
		for ( int i = start; i <= lastbar; i++)
		{
			if ( quotes[i].high == high )
				return i;
		}
		return Constants.NOT_FOUND;
	}
	
	static public int findFirstPriceLow(QuoteData[] quotes, int start, double low )
	{
		int lastbar = quotes.length - 1;
		
		for ( int i = start; i <= lastbar; i++)
		{
			if ( quotes[i].low == low )
				return i;
		}
		return Constants.NOT_FOUND;
	}


	static public int findPosition(QuoteData[] quotes, int CHART, String timeStr,  int order )
	{
		int minute = 0;
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

		String minStr = Integer.toString(minute);
		if ( minute < 10 )
			minStr = "0" + minute;
			
		String time = timeStr.substring(0, 13) + minStr;
		
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

	static public int findPositionByTime(QuoteData[] quotes, String timeStr, int order ){
		int i = quotes.length - 1;
		while ( i >= 0 ){
			if (quotes[i].time.equals(timeStr))
				return i;
			else
				i--;
		}
		return Constants.NOT_FOUND;
	}
	
	static public Push findLargestConsectiveUpBars(QuoteData[] quotes, int start, int end)
	{
		Push upPush = null;
		int upStart = start;
		while ( upStart <= end)
		{
			if ( quotes[upStart].close > quotes[upStart].open )
			{
				int upEnd = upStart+1;
				while (( upEnd <= end) && ( quotes[upEnd].close > quotes[upEnd].open )) 
					upEnd++;
				
				upEnd = upEnd-1;
				
				if (( upPush == null ) || (( quotes[upEnd].high - quotes[upStart].low ) > ( quotes[upPush.pushEnd].high - quotes[upPush.pushStart].low )))
				{
					upPush = new Push( upStart, upEnd );
				}
				upStart = upEnd + 1;
			}
			else
				upStart++;
			
		}
		
		return upPush;
		
	}

		
	static public Push findLargestConsectiveDownBars(QuoteData[] quotes, int start, int end)
	{
		Push downPush = null;
		int downStart = start;
		while ( downStart <= end)
		{
			if ( quotes[downStart].close < quotes[downStart].open )
			{
				int downEnd = downStart+1;
				while (( downEnd <= end) && ( quotes[downEnd].close < quotes[downEnd].open )) 
					downEnd++;
				
				downEnd = downEnd-1;
				
				if (( downPush == null ) || (( quotes[downStart].high - quotes[downEnd].low  ) > ( quotes[downPush.pushStart].high - quotes[downPush.pushEnd].low )))
				{
					downPush = new Push( downStart, downEnd );
				}
				downStart = downEnd + 1;
			}
			else
				downStart++;
			
		}
		
		return downPush;
		
	}


	
	
	
	
	
	
	
	static public Push findLargePushUpBars(QuoteData[] quotes, int start, int end)
	{
		Push upPush = null;
		int upEnd = end;
		if ( quotes[upEnd].close > quotes[upEnd].open )
		{
			int upStart = upEnd - 1;
			while (!( quotes[upStart].low < quotes[upStart-1].low ) &&  ( quotes[upStart].high < quotes[upStart-1].high )) 
				upStart--;
			
			int numOfBars = upEnd - upStart + 1;
			double avgBarSize = BarUtil.averageBarSize( quotes );
			
			if (( quotes[upEnd].close - quotes[upStart].open ) > 1.2 * avgBarSize * numOfBars )
			{
				upPush = new Push( upStart, upEnd );
				return upPush;
			}
		}
		
		return upPush;
		
	}

	

	static public Push findLargePushDownBars(QuoteData[] quotes, int start, int end)
	{
		Push downPush = null;
		int downEnd = end;
		if ( quotes[downEnd].close < quotes[downEnd].open )
		{
			int downStart = downEnd - 1;
			while (!( quotes[downStart].low > quotes[downStart-1].low ) &&  ( quotes[downStart].high > quotes[downStart-1].high )) 
				downStart--;
			
			int numOfBars = downEnd - downStart + 1;
			double avgBarSize = BarUtil.averageBarSize( quotes );
			
			if (( quotes[downStart].open - quotes[downEnd].close ) > 1.2 * avgBarSize * numOfBars )
			{
				downPush = new Push( downStart, downEnd );
				return downPush;
			}
		}
		
		return downPush;
		
	}



	
	
	static public Push findLargestPullBackFromHigh(QuoteData[] quotes, int start, int end)
	{
		Push push = new Push();
		
		for ( int i = start; i< end; i++)
		{
			for (int j=i+1; j <=end; j++)
			{
				double pullback = quotes[i].high - quotes[j].low;
				if ( pullback > push.pullback )
				{
					push.pushStart = i;
					push.pushEnd = j;
					push.pullback = pullback; 
				}
			}
		}
		
		return push;
		
	}


	static public Push findLargestPullBackFromLow(QuoteData[] quotes, int start, int end)
	{
		Push push = new Push();
		
		for ( int i = start; i< end; i++)
		{
			for (int j=i+1; j <=end; j++)
			{
				double pullback = quotes[j].high - quotes[i].low;
				if ( pullback > push.pullback )
				{
					push.pushStart = i;
					push.pushEnd = j;
					push.pullback = pullback; 
				}
			}
		}
		
		return push;
		
	}

	static public Double readCSVTokenDouble(String token )
	{
		token = token.trim();
		if (( token != null ) && !token.equals(""))
			return new Double(token);
		else
			return null;
	}
	
	static public Integer readCSVTokenInteger(String token )
	{
		token = token.trim();
		if (( token != null ) && !token.equals(""))
			return new Integer(token);
		else
			return null;
	}

	
	
	static public void saveFile ( String dir, String fileName, String content )
	{
	    try // open file
	    {
	        //Writer out = new OutputStreamWriter(new FileOutputStream(fileName));
			//out.write(content);
			//out.close();
	    	
	    	
	    	File file = new File (dir, fileName);
	    	
            //File file = new File(fileName);
            BufferedWriter output = new BufferedWriter(new FileWriter(file));
            output.write(content);
            output.close();

	    } 
	    catch ( Exception e )
	    {
		    System.out.println("Exception during saving file " + fileName);
		    	e.printStackTrace();
		}
	
	    
        // TODO Auto-generated method stub
	    /*
        String osname = System.getProperty("os.name", "").toLowerCase();
        if (osname.startsWith("windows")) {
            String text = "Hello world Windows";
            try {
                File file = new File("C:/example.txt");
                BufferedWriter output = new BufferedWriter(new FileWriter(file));
                output.write(text);
                output.close();
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        } else if (osname.startsWith("linux")) {    
            String text = "Hello world Linux";
            try {
                File file = new File("/root/Desktop/example.txt");
                BufferedWriter output = new BufferedWriter(new FileWriter(file));
                output.write(text);
                output.close();
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Sorry, your operating system is different");    
        }*/
	}
	

	static public void saveFile ( String fileName, String content )
	{
		File file;
	    try // open file
	    {
	        //Writer out = new OutputStreamWriter(new FileOutputStream(fileName));
			//out.write(content);
			//out.close();
	    	
	    	int fileInd = fileName.lastIndexOf("/");
	    	if ( fileInd == -1 ){
	          file = new File(fileName);
	    	}else{
		    	String filename = fileName.substring(fileInd+1);
		    	String dir = fileName.substring(0, fileInd);
		    	System.out.println("filename:" + filename + " dir:" + dir);
		    	file = new File (dir, filename);
	    	}
	    	
            BufferedWriter output = new BufferedWriter(new FileWriter(file));
            output.write(content);
            output.close();

	    } 
	    catch ( Exception e )
	    {
		    System.out.println("Exception during saving file " + fileName);
		    	e.printStackTrace();
		}
	
	    
        // TODO Auto-generated method stub
	    /*
        String osname = System.getProperty("os.name", "").toLowerCase();
        if (osname.startsWith("windows")) {
            String text = "Hello world Windows";
            try {
                File file = new File("C:/example.txt");
                BufferedWriter output = new BufferedWriter(new FileWriter(file));
                output.write(text);
                output.close();
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        } else if (osname.startsWith("linux")) {    
            String text = "Hello world Linux";
            try {
                File file = new File("/root/Desktop/example.txt");
                BufferedWriter output = new BufferedWriter(new FileWriter(file));
                output.write(text);
                output.close();
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Sorry, your operating system is different");    
        }*/
	}

	
	

	public static ConsectiveBars findLastConsectiveUpBars( QuoteData[] quotes)
	{
		int lastbar = quotes.length -1 ;
		return findLastConsectiveUpBars( quotes, 0, lastbar);
	}

	
	public static ConsectiveBars findLastConsectiveUpBars( QuoteData[] quotes, int begin, int end){
		if ( BarUtil.isUpBar(quotes[end])){
			int upBarStart = end - 1;
			while (!BarUtil.isDownBar(quotes[upBarStart])&& (upBarStart >= begin))
				upBarStart--;
		
			upBarStart = upBarStart+1;
			return new ConsectiveBars(upBarStart, end);
		}
		else
			return new ConsectiveBars(end, end);
	}


	public static ConsectiveBars findLastConsectiveDownBars( QuoteData[] quotes){
		int lastbar = quotes.length -1 ;
		return findLastConsectiveDownBars( quotes, 0, lastbar);
	}

	public static ConsectiveBars findLastConsectiveDownBars( QuoteData[] quotes, int begin, int end){
		if ( BarUtil.isDownBar(quotes[end])){
			int downBarStart = end - 1;
			while (!BarUtil.isUpBar(quotes[downBarStart]) && (downBarStart >= begin))
				downBarStart--;
		
			downBarStart = downBarStart+1;
			return new ConsectiveBars(downBarStart, end);
		}
		else
			return new ConsectiveBars(end, end);
		
	}


	
}
