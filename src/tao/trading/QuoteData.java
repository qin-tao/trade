package tao.trading;

import java.io.Serializable;
import java.util.Date;

public class QuoteData implements Comparable<QuoteData>, Serializable
{
	public String time;
	public long timeInMillSec;
	public double open;
	public double high;
	public double low;
	public double close;
	public int volume;
	public int count;
	public double WAP;
	public boolean hasGaps;
	public double ema5;
	public double ema20;
	public double ema50;
	public double ema200;
	public boolean isHigh;
	public boolean isLow;
	public int trend;
	public int pos;
	public int req_id;
	public boolean updated;
	public int numOfUpdates;

	public QuoteData()
	{
		
	}
	
	public QuoteData(String time, double open, double high, double low,
			double close, int volume, int count, double WAP, boolean hasGaps)
	{
		this.time = time;
		this.open = open;
		this.high = high;
		this.low = low;
		this.close = close;
		this.volume = volume;
		this.count = count;
		this.WAP = WAP;
		this.hasGaps = hasGaps;

	}

	public QuoteData(String time, long timeInMillSecond, double open, double high, double low,
			double close, int volume, int count, double WAP, boolean hasGaps)
	{
		this.time = time;
		this.timeInMillSec = timeInMillSecond;
		this.open = open;
		this.high = high;
		this.low = low;
		this.close = close;
		this.volume = volume;
		this.count = count;
		this.WAP = WAP;
		this.hasGaps = hasGaps;

	}

	public QuoteData(int req_id, long time, double open, double high, double low, double close)
	{
		this.req_id = req_id;
		this.time = new Long(time).toString();
		this.timeInMillSec = time;
		this.open = open;
		this.high = high;
		this.low = low;
		this.close = close;

	}

	
	
	public String toString()
	{
		return "Time:" + time + " Open:" + open + " High:" + high  + " Low:" + low + " Close:" + close;
/*				" Volumne:" + volume +
				" Count:" + count +
				" WAP:" + WAP +
				" hasGap:" + hasGaps +
				" ema5:" + ema5 +
				" ema20:" + ema20 +
				" ema50:" + ema50 +
				" ema200:" + ema200 +
				" isHigh:" + isHigh +
				" isLow:" + isLow;*/
 
	}

	public int compareTo(QuoteData data) {
	/*	if ( req_id > data.req_id )
			return 1;
		else if ( req_id < data.req_id )
			return -1;
		else*/
			return time.compareTo(data.time);
	}
	
	public boolean equals(QuoteData data) {
		if (( open == data.open ) && ( close == data.close ) && ( high == data.high ) && ( low == data.low ))
			return true;
		else
			return false;
	}
	
	public boolean isUpBar(){
		if (close > open )
			return true;
		else 
			return false;
	}

	public boolean isDownBar(){
		if (close < open )
			return true;
		else 
			return false;
	}

}
