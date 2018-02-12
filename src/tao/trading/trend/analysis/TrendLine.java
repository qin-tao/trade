package tao.trading.trend.analysis;

import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.Reversal123;
import tao.trading.strategy.util.Utility;

public class TrendLine {
	
	private String symbol;
	private Logger logger = Logger.getLogger("Reversal_123");

	public TrendLine(String symbol, Logger logger) {
		super();
		this.symbol = symbol;
		this.logger = logger;
	}


	public Reversal123 calculateUp123_20MA( Object[] quotes, double[] ema20)
	{
		int lastbar = quotes.length -1;
		int pos = Constants.NOT_FOUND;
		
		// find the start point of 10 consective up bars
		for ( int i = lastbar; i > lastbar - 10; i-- )
		{
			pos = i;
			for ( int j = 0; j <10; j++)
			{	
				if (((QuoteData) quotes[i-j]).low <= ema20[i-j])
				{		
					pos = Constants.NOT_FOUND;
					break;
				}
			}
			
			if ( pos == i )
				break;
		}

		if ( pos == Constants.NOT_FOUND )
			return null;		// can not find 10 consective ups
		
		logger.info(symbol + " 10 consective up found at " + ((QuoteData) quotes[pos]).time);
		
		pos = Pattern.findPriceCross20MAUp(quotes, ema20, pos, 1);
		logger.info(symbol + " 10 consective up start at " + ((QuoteData) quotes[pos]).time);
		
		// find the last one that touches 20MA
		while (((QuoteData) quotes[pos+1]).low <= ema20[pos+1] )
				pos++;
		logger.info(symbol + " last MA touch is " + ((QuoteData) quotes[pos]).time);
	
		return calculateUp123(quotes, pos, lastbar );
		
	}

	
	
	public Reversal123 calculateDown123_20MA( Object[] quotes, double[] ema20)
	{
		int lastbar = quotes.length -1;
		int pos = Constants.NOT_FOUND;
		
		// find the start point of 10 consective up bars
		for ( int i = lastbar; i > lastbar - 10; i-- )
		{
			pos = i;
			for ( int j = 0; j <10; j++)
			{	
				if (((QuoteData) quotes[i-j]).high >= ema20[i-j])
				{		
					pos = Constants.NOT_FOUND;
					break;
				}
			}
			
			if ( pos == i )
				break;
		}

		if ( pos == Constants.NOT_FOUND )
			return null;		// can not find 10 consective ups

		logger.info(symbol + " 10 consective down found at " + ((QuoteData) quotes[pos]).time);
		
		pos = Pattern.findPriceCross20MADown(quotes, ema20, pos, 1);
		logger.info(symbol + " 10 consective down start at " + ((QuoteData) quotes[pos]).time);
		
		// find the last one that touches 20MA
		while (((QuoteData) quotes[pos+1]).high >= ema20[pos+1] )
				pos++;
		logger.info(symbol + " last MA touch is " + ((QuoteData) quotes[pos]).time);
	
		return calculateDown123(quotes, pos, lastbar );
		
	}


	
	
	
	public Reversal123 calculateDown123_20MA_2( Object[] quotes, int end, double[] ema20, double[] ema50)
	{
		// find the highest point of last consective 20 over 50
		// starting point is the highest point of the 20 over 50
		int consectUp = Pattern.findLastMAConsectiveUp(ema20, ema50, 30);
		if ( consectUp == Constants.NOT_FOUND )
			 return null;
		//logger.info(symbol + " consective up position is " + ((QuoteData)quotes[consectUp]).time);
		
		int consectStart = Pattern.findLastMACrossUp(ema20, ema50, consectUp, 30);
		if ( consectStart == Constants.NOT_FOUND )
			consectStart = 0;
		//logger.info(symbol + " consective up start position is " + ((QuoteData)quotes[consectStart]).time);
		
		int start = Utility.getHigh(quotes, consectStart, consectUp).pos;

		//logger.info(symbol + " trend line start position is " + ((QuoteData)quotes[start]).time);
		double a = 0;
		int startpoint = start;
		T: for ( int i = start; i < end -20; i++ )  // we exepct the trend line is at least 20 bar long
		{
			// find the lowest a, while cover everything
			double a1 = -1;
			for ( int j = i+1; j < end; j++)
			{	
				//System.out.println("i=" + i + " j="+j);
				double a0 = cal_a(i, j, ((QuoteData)quotes[i]).high, ((QuoteData)quotes[j]).high);
				if ( a0 > 0 )
					continue T; // not a down trend

				if (a0 > a1)
				   a1 = a0;
			}
			// a1 = the biggest a0 of all
			
			// looking for the smallest a1 among all
			if (( a1 != -1 ) && ( a1 < a ))
			{
				a = a1;
				startpoint = i;
			}
			
		}

		if ( a == 0 )
			return null;
		else	
		{
			Reversal123 r = new Reversal123();
			r.a = a;
			r.startpos = startpoint;
		
			return r;
		}
		
	}

	
	
	
	
	public Reversal123 calculateUp123( Object[] quotes, int pos, int lastbar)
	{
		// calculate y = ax;
		double a = 1;
		int supportpos = 0;
		for ( int i = pos+6; i < lastbar; i++)
		{
			if  ( a == 1 )
			{
				//double a0 = cal_a_low(quotes[pos], quotes[i-5]);
				double a0 = cal_a(pos, i-5, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-5]).low);
				logger.info(symbol + " a0 = " + a0);

				// need 4 bars sheld out
				/*if (( cal_a_low(quotes[pos], quotes[i-4]) > a0) && 
					( cal_a_low(quotes[pos], quotes[i-3]) > a0) && 
					( cal_a_low(quotes[pos], quotes[i-2]) > a0) && 
					( cal_a_low(quotes[pos], quotes[i-1]) > a0))*/
				if (( cal_a(pos, i-4, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-4]).low) > a0) && 
					( cal_a(pos, i-3, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-3]).low) > a0) && 
					( cal_a(pos, i-2, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-2]).low) > a0) &&
					( cal_a(pos, i-1, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-1]).low) > a0))
				{
					supportpos = i-5;
					logger.info(symbol + " 123 support point is at " + ((QuoteData) quotes[supportpos]).time);
					a = a0;
				}
					
			}
			else
			{
				// need two bars closed below
				//if (( cal_a_close(quotes[pos], quotes[i-2]) < a) && 
				//	( cal_a_close(quotes[pos], quotes[i-1]) < a))
				if ((cal_a(pos, i-2, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-2]).high) < a ) && 
				    (cal_a(pos, i-1, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-1]).high) < a))
				{
					logger.info(symbol + " 123 found at " + ((QuoteData) quotes[i-3]).time);
					return new Reversal123(pos, supportpos, i-3);
				}
			}
		}
		
		return null;
		
	}

	
	
	
	
	public Reversal123 calculateDown123( Object[] quotes, int pos, int lastbar)
	{
		// calculate y = ax;
		double a = -1;
		int supportpos = 0;
		for ( int i = pos+6; i < lastbar; i++)
		{
			if  ( a == -1 )
			{
				//double a0 = cal_a_high(quotes[pos], quotes[i-5]);
				double a0 = cal_a(pos, i-5, ((QuoteData)quotes[pos]).high, ((QuoteData)quotes[i-5]).high);
				logger.info(symbol + " a0 = " + a0);

				// need 4 bars sheld out
				/*if (( cal_a_high(quotes[pos], quotes[i-4]) < a0) && 
					( cal_a_high(quotes[pos], quotes[i-3]) < a0) && 
					( cal_a_high(quotes[pos], quotes[i-2]) < a0) && 
					( cal_a_high(quotes[pos], quotes[i-1]) < a0))*/
				if (( cal_a(pos, i-4, ((QuoteData)quotes[pos]).high, ((QuoteData)quotes[i-4]).high) < a0) && 
					( cal_a(pos, i-3, ((QuoteData)quotes[pos]).high, ((QuoteData)quotes[i-3]).high) < a0) && 
					( cal_a(pos, i-2, ((QuoteData)quotes[pos]).high, ((QuoteData)quotes[i-2]).high) < a0) && 
					( cal_a(pos, i-1, ((QuoteData)quotes[pos]).high, ((QuoteData)quotes[i-1]).high) < a0))
				{
					supportpos = i-5;
					logger.info(symbol + " 123 support point is at " + ((QuoteData) quotes[supportpos]).time);
					a = a0;
				}
					
			}
			else
			{
				// need two bars closed below
				/*if (( cal_a_close(quotes[pos], quotes[i-2]) > a) && 
					( cal_a_close(quotes[pos], quotes[i-1]) > a))*/
				if (( cal_a(pos, i-2, ((QuoteData)quotes[pos]).high, ((QuoteData)quotes[i-2]).low) > a) && 
					( cal_a(pos, i-1, ((QuoteData)quotes[pos]).high, ((QuoteData)quotes[i-1]).low) > a))
				{
					logger.info(symbol + " 123 found at " + ((QuoteData) quotes[i-3]).time);
					return new Reversal123(pos, supportpos, i-3);
				}
			}
		}
		
		return null;
		
	}

	
	
	
	
	
	
	public Reversal123 calculateUp123_2( QuoteData[] quotes, int start, int end)
	{
		// calculate y = ax;
		QuoteData lowest = Utility.getLow( quotes, start, end);
		QuoteData highest = Utility.getHigh( quotes, start, end);
		
		if ( lowest.pos >= highest.pos )
			return null;

		// find fractuals
		QuoteData[] fractuals = Pattern.getLastFractualLows1( quotes, lowest.pos, highest.pos);
		if ( fractuals == null )
			return null;
		int fractuals_length = fractuals.length;
		for ( int i = 0; i < fractuals_length; i++)
		{
			//System.out.println("Fractual:" + fractuals[i].time + " " + fractuals[i].low + " pos " + fractuals[i].pos);
		}
		
		// calculate the last a
		double last_a = 0;
		if ( fractuals_length >= 2 )
		{
			last_a = cal_a(fractuals[fractuals_length-2].pos, fractuals[fractuals_length-1].pos,fractuals[fractuals_length-2].low, fractuals[fractuals_length-1].low );
		}
		if ( fractuals_length >= 1 )
		{
			last_a = cal_a(lowest.pos, fractuals[fractuals_length-1].pos,lowest.low, fractuals[fractuals_length-1].low );
		}
		
		//System.out.println("last_a:" + last_a);
		//System.out.println();
		if ( last_a != 0 )
		{
			Reversal123 r = new Reversal123(last_a, fractuals, Reversal123.UP_BY_LOW);
			return r;
		}

		return null;

		/*
		int supportpos = 0;
		for ( int i = pos+6; i < lastbar; i++)
		{
			if  ( a == 1 )
			{
				//double a0 = cal_a_low(quotes[pos], quotes[i-5]);
				double a0 = cal_a(fractuals[fractuals_length-2].pos, fractuals[fractuals_length-1].pos,fractuals[fractuals_length-2].low, fractuals[fractuals_length-1].low );
				pos, ; pos, i-5, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-5]).low);
				logger.info(symbol + " a0 = " + a0);

				// need 4 bars sheld out
				if (( cal_a(pos, i-4, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-4]).low) > a0) && 
					( cal_a(pos, i-3, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-3]).low) > a0) && 
					( cal_a(pos, i-2, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-2]).low) > a0) &&
					( cal_a(pos, i-1, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-1]).low) > a0))
				{
					supportpos = i-5;
					logger.info(symbol + " 123 support point is at " + ((QuoteData) quotes[supportpos]).time);
					a = a0;
				}
					
			}
			else
			{
				// need two bars closed below
				//if (( cal_a_close(quotes[pos], quotes[i-2]) < a) && 
				//	( cal_a_close(quotes[pos], quotes[i-1]) < a))
				if ((cal_a(pos, i-2, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-2]).high) < a ) && 
				    (cal_a(pos, i-1, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-1]).high) < a))
				{
					logger.info(symbol + " 123 found at " + ((QuoteData) quotes[i-3]).time);
					return new Reversal123(pos, supportpos, i-3);
				}
			}
		}*/
		
		//return 0;
		
	}


	
	public Reversal123 calculateUp123_by_2_fractuals( QuoteData[] quotes, int fractual1, int fractual2)
	{
		// calculate the last a
		double last_a = 0;
		last_a = cal_a(quotes[fractual1].pos, quotes[fractual2].pos,quotes[fractual1].low, quotes[fractual2].low );
		
		Reversal123 r = new Reversal123(last_a, fractual1, fractual2);

		return null;

		/*
		int supportpos = 0;
		for ( int i = pos+6; i < lastbar; i++)
		{
			if  ( a == 1 )
			{
				//double a0 = cal_a_low(quotes[pos], quotes[i-5]);
				double a0 = cal_a(fractuals[fractuals_length-2].pos, fractuals[fractuals_length-1].pos,fractuals[fractuals_length-2].low, fractuals[fractuals_length-1].low );
				pos, ; pos, i-5, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-5]).low);
				logger.info(symbol + " a0 = " + a0);

				// need 4 bars sheld out
				if (( cal_a(pos, i-4, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-4]).low) > a0) && 
					( cal_a(pos, i-3, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-3]).low) > a0) && 
					( cal_a(pos, i-2, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-2]).low) > a0) &&
					( cal_a(pos, i-1, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-1]).low) > a0))
				{
					supportpos = i-5;
					logger.info(symbol + " 123 support point is at " + ((QuoteData) quotes[supportpos]).time);
					a = a0;
				}
					
			}
			else
			{
				// need two bars closed below
				//if (( cal_a_close(quotes[pos], quotes[i-2]) < a) && 
				//	( cal_a_close(quotes[pos], quotes[i-1]) < a))
				if ((cal_a(pos, i-2, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-2]).high) < a ) && 
				    (cal_a(pos, i-1, ((QuoteData)quotes[pos]).low, ((QuoteData)quotes[i-1]).high) < a))
				{
					logger.info(symbol + " 123 found at " + ((QuoteData) quotes[i-3]).time);
					return new Reversal123(pos, supportpos, i-3);
				}
			}
		}*/
		
		//return 0;
		
	}

	
	
	
	
	
	
	
	
	
	
	double cal_a_low( Object x1, Object x2 )
	{
		logger.info( symbol + "x2.low=" + ((QuoteData) x2).low);
		logger.info( symbol + "x1.low=" + ((QuoteData) x1).low);
		logger.info( symbol + "x2.pos=" + ((QuoteData) x2).pos);
		logger.info( symbol + "x1.pos=" + ((QuoteData) x2).pos);
		return (((QuoteData) x2).low - ((QuoteData) x1).low)/((double)((QuoteData)x2).pos - (double)((QuoteData)x1).pos);
	}

	double cal_a_high( Object x1, Object x2 )
	{
		logger.info( symbol + "x2.high=" + ((QuoteData) x2).high);
		logger.info( symbol + "x1.high=" + ((QuoteData) x1).high);
		logger.info( symbol + "x2.pos=" + ((QuoteData) x2).pos);
		logger.info( symbol + "x1.pos=" + ((QuoteData) x2).pos);
		return (((QuoteData) x2).high - ((QuoteData) x1).high)/((double)((QuoteData)x2).pos - (double)((QuoteData)x1).pos);
	}
	
	double cal_a_close( Object x1, Object x2 )
	{
		return (((QuoteData) x2).close - ((QuoteData) x1).close)/((double)((QuoteData)x2).pos - (double)((QuoteData)x1).pos);
	}

	public static double cal_a( double x1,double x2, double y1, double y2)
	{
		return (y2 - y1)/(x2 - x1);
	}
	
	
	

}
