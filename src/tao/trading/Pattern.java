package tao.trading;

import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.strategy.util.Utility;

public class Pattern
{
	private static Logger logger = Logger.getLogger("pattern");

	///////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	//	Cup methods
	//
	//
	///////////////////////////////////////////////////////////////////////////////////////////////////////
	public static Cup upCup( String symbol, Object[] quotes, double[] ema, int start, int lastbar, double prevDistMin, double currDistMin, double pullbackDist, boolean touchEMA )
	{
		if ( currDistMin != 0 )
		{
			if ( ((QuoteData)quotes[lastbar]).high - ema[lastbar]  < currDistMin )
			{
				logger.info("downcup did not reach currDistMin");
				return null;
			}
		}

		double lastHigh = Utility.getHigh(quotes, start, lastbar-1).high;
		if (((QuoteData)quotes[lastbar]).high < lastHigh )
			return null;  // not the highest

		// we find the last time
		int thisHighStarts = lastbar -1;
		while ((thisHighStarts > start) && (((QuoteData)quotes[thisHighStarts-1]).high <= ((QuoteData)quotes[thisHighStarts]).high )) 
		{	
			thisHighStarts--;
		}
		
		if ( thisHighStarts == start)
			return null;   // stright , no pull back

		logger.info(symbol + " this high starts " + ((QuoteData)quotes[thisHighStarts]).time );

		// find the highest between pos and this highstarts
		int previousHighPos = -1;
		double previousHigh = 0;
		for ( int i = start; i <thisHighStarts; i++)
		{
			if ( ((QuoteData)quotes[i]).high > previousHigh )
			{
				previousHigh = ((QuoteData)quotes[i]).high;
				previousHighPos = i;
			}
		}
		
		if ( previousHighPos == -1 )
		{
			return null;   // this high could start from 200ma so there is no previous high
		}

		logger.info(symbol + " previousHighPos is " + ((QuoteData)quotes[thisHighStarts]).time );

		
		if ( prevDistMin != 0 )
		{
			if ( previousHigh - ema[previousHighPos]< prevDistMin )
			{
				logger.info(symbol + " did not meet prevDistMin of " + prevDistMin );
				return null; // not enough distance
			}
		}

		if ( previousHighPos >= lastbar -1 )
			return null; // previous high next to lastbar

		double pullbacksize = 0;
		int pullbackpos = -1;
		double pullbacklow = 0;
		// need a big pull back from the previous high
		for ( int i = previousHighPos+1; i < lastbar; i++)
		{
			if (( previousHigh - ((QuoteData)quotes[i]).low)> pullbacksize)
			{
				pullbacksize = previousHigh - ((QuoteData)quotes[i]).low;
				pullbackpos = i;
				pullbacklow = ((QuoteData)quotes[i]).low;
			}
		}

		if (( pullbackDist != 0 ) && ( pullbacksize < pullbackDist ))
			return null;
		
		if (((touchEMA == true ) && (((QuoteData)quotes[pullbackpos]).low < ema[pullbackpos] )) 
		  || ( touchEMA == false)) 
		{
			Cup cup = new Cup();
			cup.thisHighLowStarts = thisHighStarts;
			cup.pullBackPos = pullbackpos;
			cup.lastHighLow = previousHigh;
			cup.lastHighLowPos = previousHighPos;
			cup.pullBackSize = pullbacksize;
			cup.pullBackWidth = lastbar - previousHighPos;
			cup.pullBackHighLow = pullbacklow;
			return cup;
		}
		else
			return null;
	}

	

	public static Cup downCup( String symbol, Object[] quotes, double[] ema, int start, int lastbar, double prevDistMin, double currDistMin, double stretchDist, boolean touchEMA )
	{
		//int lastbar = quotes.length -1;

		if ( currDistMin != 0 )
		{
			if ( ema[lastbar] - ((QuoteData)quotes[lastbar]).high < currDistMin )
			{
				logger.info("downcup did not reach currDistMin");
				return null;
			}
		}

		double lastLow = Utility.getLow(quotes, start, lastbar-1).low;
		if ( ((QuoteData)quotes[lastbar]).low > lastLow )
			return null;

		// we find the last time
		int thisLowStarts = lastbar -1;
		while ((thisLowStarts > start ) && (((QuoteData)quotes[thisLowStarts-1]).low >= ((QuoteData)quotes[thisLowStarts]).low ))
		{	
			thisLowStarts--;
		}

		if ( thisLowStarts == start)
			return null;   // strigt , no pull back

		logger.info(symbol + " this low starts " + ((QuoteData)quotes[thisLowStarts]).time );

		// find the highest between pos and this low
		int previousLowPos = -1;
		double previousLow = 999;
		for ( int i = start; i < thisLowStarts; i++)
		{
			if ( ((QuoteData)quotes[i]).low < previousLow )
			{
				previousLow = ((QuoteData)quotes[i]).low;
				previousLowPos = i;
			}
		}

		if (  previousLowPos== -1 )
			return null;

		logger.info(symbol + " previousLowPos is " + ((QuoteData)quotes[previousLowPos]).time );

		if ( prevDistMin != 0 )
		{
			if (ema[previousLowPos] - previousLow< prevDistMin )
			{
				logger.info(symbol + " did not meet prevDistMin of " + prevDistMin );
				return null; // not enough distance
			}
		}
		
		if ( previousLowPos >= lastbar -1 )
			return null; // previous low next to lastbar
		
		// check the pullback between the previous low and this low
		double pullbacksize = 0;
		int pullbackpos = -1;
		double pullbackhigh = 0;
		// need a big pull back from the previous high
		for ( int i = previousLowPos+1; i < lastbar; i++)
		{
			if ((((QuoteData)quotes[i]).high - previousLow) > pullbacksize)
			{
				pullbacksize = ((QuoteData)quotes[i]).high - previousLow;
				pullbackpos = i;
				pullbackhigh = ((QuoteData)quotes[i]).high;
			}
		}

		if (( stretchDist != 0 ) && ( pullbacksize < stretchDist ))
			return null;

		if (((touchEMA == true ) && (((QuoteData)quotes[pullbackpos]).high> ema[pullbackpos])) 
				  || ( touchEMA == false)) 
		{
			Cup cup = new Cup();
			cup.thisHighLowStarts = thisLowStarts;
			cup.pullBackPos = pullbackpos;
			cup.pullBackSize = pullbacksize;
			cup.pullBackWidth = lastbar - previousLowPos;
			cup.lastHighLow = previousLow;
			cup.lastHighLowPos = previousLowPos;
			cup.pullBackHighLow = pullbackhigh;
			return cup;
		}
		else
			return null;

	}


	
	
	
	
	
	
	
	/*
	if ( Pattern.find20MAExhaustingTop( quotes5, ema20_5) == true )
	{
		logger.info( symbol + " trade 5 minute cup entry at " + data.close + " " + data.time );
		System.out.println( symbol + " trade 5 minute cup entry at " + data.close + " " + data.time );

		trade.price = data.close;
		trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.positionSize);
		// stop is 30 pips temporily
		trade.stopId = placeStopMarketOrder( Constants.ACTION_BUY, adjustPrice( data.close + INITIAL_STOP*pipSize, Constants.ADJUST_TYPE_UP));
		trade.status = Constants.STATUS_PLACED;
		trade.position = Constants.POSITION_SHORT;
		trade.entryTime = ((QuoteData)quotes[lastbar]).time;
		return;
	}
	int pos = findPriceCross20MAUp(quotes5, ema20_5);
	
	Cup cup = Pattern.upCup20MA(quotes5, ema20_5, pos, avgSize*2, avgSize*2, avgSize*2);  
	*/


	
	
	
	
	/*
	
	static public Vector<QuoteData> buildVector( Vector<QuoteData> quotes, int startPos, int endPos )
	{
		Vector<QuoteData> vec = new Vector<QuoteData>();
		
		for ( int i = startPos; i < endPos; i++)
		{
			vec.add(quotes.elementAt(i));
		}
		return vec;
		
	}*/

	
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	//	Wave methods
	//
	//
	///////////////////////////////////////////////////////////////////////////////////////////////////////
	public static Vector<Wave> upWave( String symbol, Object[] quotes, int start, double pullBackMinSize )
	{
		Vector<Wave> waves = new Vector(); 

		int lastbar = quotes.length-1;
		int pushStart = start;
		int pushEnd = pushStart;
		double pushHigh = ((QuoteData)quotes[pushEnd]).high;
		
		while (true)
		{	
			while ((pushEnd < lastbar) && ( ((QuoteData)quotes[pushEnd+1]).high > pushHigh ))
			{
				pushHigh = ((QuoteData)quotes[pushEnd+1]).high;
				pushEnd = pushEnd +1;
			}

			if ( pushEnd == lastbar)
			{
				if ( pushStart != pushEnd )
				{
					Wave w = new Wave(pushHigh - ((QuoteData)quotes[pushStart]).high, 0);
					waves.add(w);
				}
				return waves;
			}
		
			
			// now it stops making new high
			int pullbackStart = pushEnd+1;
			int pullbackEnd = pullbackStart;
			while (( pullbackEnd < lastbar) && (((QuoteData)quotes[pullbackEnd+1]).high < pushHigh ))
				pullbackEnd++;
			
			
			double low = Utility.getLow(quotes, pullbackStart, pullbackEnd ).low;
			double pullback = pushHigh - low;
		
			if ( pullback > pullBackMinSize)
			{
				Wave w = new Wave(pushHigh - ((QuoteData)quotes[pushStart]).high, pullback);
				waves.add(w);
				pushStart = pullbackEnd;
				pushEnd = pushStart;
			}
			else if ( pullbackEnd == lastbar )
			{
				Wave w = new Wave(pushHigh - ((QuoteData)quotes[pushStart]).high, 0);
				waves.add(w);
				return waves;
			}
		}
	}
	
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////
	//  CrossOver methods
	////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////
	//
	//  find the last time when there is a cross
	//
	///////////////////////////////////////////////////////////////
	static public int findLastMACrossUp( double[] ema5, double[] ema20)
	{
		return findLastMACrossUp( ema5, ema20, 1);
	}

	///////////////////////////////////////////////////////////////
	//
	//  find the last time when there is a cross, at least > n bars
	//
	///////////////////////////////////////////////////////////////
	static public int findLastMACrossUp( double[] ema5, double[] ema20, int minBars)
	{
		int lastbar = ema5.length-1;

		return findLastMACrossUp( ema5, ema20, lastbar, minBars);
	}


	static public int findLastMACrossUp( double[] ema5, double[] ema20, int lastbar, int minBars)
	{
		for ( int i = lastbar; i > minBars; i-- )
		{
			boolean b = false;
			// need to have minBar on the other side
			for ( int j = 0; j < minBars; j++ )
			{
				if (ema5[i-j] > ema20[i-j])
				{	
					b = true;
					break;
				}
			}
			
			if ( b == false )
				return i+1;
		}
		
		return Constants.NOT_FOUND;
		
	}


	
	
	static public int findLastMAConsectiveUp( double[] ema5, double[] ema20, int minBars)
	{
		int lastbar = ema5.length-1;
		return findLastMAConsectiveUp( ema5, ema20, minBars, 50, lastbar);
	}	

	static public int findLastMAConsectiveUp( double[] ema5, double[] ema20, int minBars, int start, int lastbar)
	{
		for ( int i = lastbar; i > minBars + start; i-- )
		{
			if ((ema5[i] > ema20[i]))
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					if (ema5[i-j] < ema20[i-j])
					{	
						crossed = true;
						break;
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}

	static public int findNextMAConsectiveUp( double[] ema5, double[] ema20, int minBars, int start)
	{
		int lastbar = ema5.length - 1;
		
		for ( int i = start; i < lastbar - minBars; i++ )
		{
			if ((ema5[i] > ema20[i]))
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					if (ema5[i+j] < ema20[i+j])
					{	
						crossed = true;
						break;
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}

	
	static public int findLastMAConsectiveDown( double[] ema5, double[] ema20, int minBars)
	{
		int lastbar = ema5.length-1;
		return findLastMAConsectiveDown( ema5, ema20, minBars, 50, lastbar);
	}	
	
	static public int findLastMAConsectiveDown( double[] ema5, double[] ema20, int minBars, int start, int lastbar)
	{

		for ( int i = lastbar; i > minBars + start; i-- )
		{
			if ((ema5[i] < ema20[i]))
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					if (ema5[i-j] > ema20[i-j])
					{	
						crossed = true;
						break;
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}

	
	static public int findNextMAConsectiveDown( double[] ema5, double[] ema20, int minBars, int start)
	{
		int lastbar = ema5.length -1;
		
		//for ( int i = lastbar; i > minBars; i-- )
		for ( int i = start; i < lastbar - minBars; i++ )
		{
			if ((ema5[i] < ema20[i]))
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					if (ema5[i+j] > ema20[i+j])
					{	
						crossed = true;
						break;
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}

	
	
	
	static public int findLastPriceConsectiveAboveMA( Object[] quotes, double[] ema, int minBars)
	{
		int lastbar = quotes.length-1;
		return 	findLastPriceConsectiveAboveMA( quotes, ema, lastbar, minBars);
	}
	
	
	static public int findLastPriceConsectiveAboveMA( Object[] quotes, double[] ema, int lastbar, int minBars)
	{
		for ( int i = lastbar; i > minBars; i-- )
		{
			if (((QuoteData)quotes[i]).low > ema[i])
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					if (((QuoteData)quotes[i-j]).low <= ema[i-j])
					{	
						crossed = true;
						break;
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}

	
	static public int findLastPriceConsectiveAboveOrAtMA( Object[] quotes, double[] ema, int lastbar, int minBars)
	{
		for ( int i = lastbar; i > minBars; i-- )
		{
			if (((QuoteData)quotes[i]).low > ema[i])
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					if (((QuoteData)quotes[i-j]).high < ema[i-j])
					{	
						crossed = true;
						break;
					}
					
					//for ( int k = 0; k < minOnOtherSide; k++)
					{
						
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}

	
	static public int findLastPriceConsectiveBelowMA( Object[] quotes, double[] ema, int minBars)
	{
		int lastbar = quotes.length-1;
		return 	findLastPriceConsectiveBelowMA( quotes, ema, lastbar, minBars);
	}
	

	static public int findLastPriceConsectiveBelowMA( Object[] quotes, double[] ema, int lastbar, int minBars)
	{
		for ( int i = lastbar; i > minBars; i-- )
		{
			if (((QuoteData)quotes[i]).high < ema[i])
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					//if (((QuoteData)quotes[i-j]).high >= ema[i-j])
					if ((((QuoteData)quotes[i-j]).high >= ema[i-j]) && (((QuoteData)quotes[i-j-1]).high >= ema[i-j-1]))  // 1 bar over is allowed
					{	
						crossed = true;
						break;
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}

	static public int findLastPriceConsectiveBelowOrAtMA( Object[] quotes, double[] ema, int lastbar, int minBars)
	{
		for ( int i = lastbar; i > minBars; i-- )
		{
			if (((QuoteData)quotes[i]).high < ema[i])
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					if (((QuoteData)quotes[i-j]).low > ema[i-j])
					{	
						crossed = true;
						break;
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}
	

	
	static public int findLastPriceOpenCloseAboveMA( QuoteData[] quotes, double[] ema, int lastbar, int minBars)
	{
		for ( int i = lastbar; i > 0; i-- )
		{
			boolean crossed = false;
			// looking for a continuous 5 over 20 for minBars number of bars
			for ( int j = 0; j < minBars; j++ )
			{
				if ((quotes[i-j].close < ema[i-j]) || (quotes[i-j].open < ema[i-j]))
				{	
					crossed = true;
					break;
				}
			}
		
			if ( crossed == false )
				return i;
		}
		
		return Constants.NOT_FOUND;
		
	}

	static public int findLastPriceOpenCloseBelowMA( QuoteData[] quotes, double[] ema, int lastbar, int minBars)
	{
		for ( int i = lastbar; i > 0; i-- )
		{
			boolean crossed = false;
			// looking for a continuous 5 over 20 for minBars number of bars
			for ( int j = 0; j < minBars; j++ )
			{
				if ((quotes[i-j].close > ema[i-j]) || (quotes[i-j].open > ema[i-j]))
				{	
					crossed = true;
					break;
				}
			}
		
			if ( crossed == false )
				return i;
		}
		
		return Constants.NOT_FOUND;
		
	}

	
	
	static public int findNextPriceConsectiveAboveMA( QuoteData[] quotes, double[] ema, int start, int minBars)
	{
		int lastbar = quotes.length - 1;
		
		for ( int i = start; i <= lastbar+1 - minBars; i++ )
		{
			if (quotes[i].low > ema[i])
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					if (((QuoteData)quotes[i+j]).low <= ema[i+j])
					{	
						crossed = true;
						break;
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}


	static public int findNextPriceConsectiveBelowMA( QuoteData[] quotes, double[] ema, int start, int minBars)
	{
		int lastbar = quotes.length - 1;
		
		for ( int i = start; i <= lastbar+1 - minBars; i++ )
		{
			if (quotes[i].high < ema[i])
			{
				boolean crossed = false;
				// looking for a continuous 5 over 20 for minBars number of bars
				for ( int j = 1; j < minBars; j++ )
				{
					if (((QuoteData)quotes[i+j]).high >= ema[i+j])
					{	
						crossed = true;
						break;
					}
				}
			
				if ( crossed == false )
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
		
	}

	
	///////////////////////////////////////////////////////////////
	//
	//  find the last time when there is a cross
	//
	///////////////////////////////////////////////////////////////
	static public int findLastMACrossDown( double[] ema5, double[] ema20)
	{
		return findLastMACrossDown( ema5, ema20, 1);
	}

	///////////////////////////////////////////////////////////////
	//
	//  find the last time when there is a cross, at least > n bars
	//
	///////////////////////////////////////////////////////////////
	static public int findLastMACrossDown( double[] ema60_5, double[] ema60_20, int minBars)
	{
		int lastbar = ema60_5.length-1;
		
		return findLastMACrossDown(ema60_5, ema60_20, lastbar, minBars);

	}

	
	static public int findLastMACrossDown( double[] ema5, double[] ema20, int lastbar, int minBars)
	{
		for ( int i = lastbar; i > minBars; i-- )
		{
			boolean b = false;
			// need to have minBar on the other side
			for ( int j = 0; j < minBars; j++ )
			{
				if (ema5[i-j] < ema20[i-j])
				{	
					b = true;
					break;
				}
			}
			
			if ( b == false )
				return i+1;
		}
		
		return Constants.NOT_FOUND;

	}
	
	
	static public int findPriceCross20MAUp(Object[] quotes, double[] ema20)
	{
		return findPriceCross20MAUp(quotes, ema20, 10) ;
	}

	static public int findPriceCross20MAUp(Object[] quotes, double[] ema20, int count)
	{
		return findPriceCross20MAUp(quotes, ema20, quotes.length-1, count) ;
	}

	static public int findPriceCross20MAUp(Object[] quotes, double[] ema20, int lastbar, int count)
	{
		T:for ( int i = lastbar-1; i >=count; i-- )
		{
			// needs to have 10 bar in the opposite trend to avoid noise
			for ( int j = i; j > i-count; j--)
			{
				if (((QuoteData)quotes[j]).high > ema20[j])
					continue T;
			}

			// needs to have at least 1 bars on this side
			for ( int j = i+1; j < lastbar; j++)
			{
				if (((QuoteData)quotes[j]).low > ema20[j])
					return i;
			}

		}
		
		return Constants.NOT_FOUND;
	}

	
	
	
	static public int findPriceCross20MADown(Object[] quotes, double[] ema20)
	{
		return findPriceCross20MADown(quotes, ema20, 10) ;
	}

	static public int findPriceCross20MADown(Object[] quotes, double[] ema20, int count)
	{
		return 	findPriceCross20MADown( quotes, ema20, quotes.length -1, count);
	}

	static public int findPriceCross20MADown(Object[] quotes, double[] ema20, int lastbar, int count)
	{
		// needs to have 10 bar in the opposite trend to avoid noise
		T:for ( int i = lastbar-1; i >= count; i-- )
		{
			for ( int j = i; j > i-count; j--)
			{
				if (((QuoteData)quotes[j]).low < ema20[j])
					continue T;
			}
			
			// needs to have at least 1 bars on this side
			for ( int j = i+1; j < lastbar; j++)
			{
				if (((QuoteData)quotes[j]).high < ema20[j]) 
					return i;
			}
		}
		
		return Constants.NOT_FOUND;
	}

	
	static public int verifyAboveOrBelowMA(Object[] quotes, double[] ema)
	{
		int lastbar = quotes.length -1;
		
		if (((QuoteData)quotes[lastbar]).low > ema[lastbar])
			return Constants.DIRECTION_UP;
		else if (((QuoteData)quotes[lastbar]).high <  ema[lastbar])
			return Constants.DIRECTION_DOWN;
		else
		{
			// check the one before whether it is up or down
			for ( int i = lastbar-1; i > 0; i--)
			{
				if ((((QuoteData)quotes[i]).low > ema[i]))
					return Constants.DIRECTION_UP;
				if (((QuoteData)quotes[i]).high <  ema[i])
					return Constants.DIRECTION_DOWN;
			}
		}
		
		return Constants.DIRECTION_UNKNOWN;
	}

	
	
	static public boolean findADXCrossUp( ADX[] adxs)
	{
		int lastBar = adxs.length-1;

		if (( adxs[lastBar].ADM_UP > adxs[lastBar].ADM_DOWN ) &&
		   ( adxs[lastBar-1].ADM_UP < adxs[lastBar-1].ADM_DOWN ))
			return true;
		else
			return false;
	}

	
	static public boolean findADXCrossDown( ADX[] adxs)
	{
		int lastBar = adxs.length-1;

		if (( adxs[lastBar].ADM_UP < adxs[lastBar].ADM_DOWN ) &&
		   ( adxs[lastBar-1].ADM_UP > adxs[lastBar-1].ADM_DOWN ))
			return true;
		else
			return false;
	}
	
	
	static public boolean findMACDCrossUp( MACD[] macds)
	{
		int last = macds.length - 1;
		
		if (( macds[last].histogram > macds[last].histogram) && 
		   ( macds[last-1].histogram < macds[last-1].histogram ) )
		   return true;
		else
			return false;
	}
		
	static public boolean findMACDCrossDown( MACD[] macds)
	{
		int last = macds.length - 1;
		
		if (( macds[last].histogram < macds[last].histogram) && 
		   ( macds[last-1].histogram > macds[last-1].histogram ) )
		   return true;
		else
			return false;
	}
		
	
    static public Vector<BreakOut> findMABreakoutUps(Object[] quotes, double[] ema20, int start)
    {
    	int lastbar = quotes.length-1;
		Vector<BreakOut> breakouts = new Vector<BreakOut>();
		
		int pos = start;
		while ( pos <= lastbar ) 
		{
			// this is the part touch or below
			double b = 99;
			int bw = 0;
			int belowPos = 0;
			while ((pos <= lastbar) &&((QuoteData)quotes[pos]).low <= ema20[pos])
			{
				bw++;
				if (((QuoteData)quotes[pos]).low < b)
				{
					b = ((QuoteData)quotes[pos]).low;
					belowPos = pos;
				}
				pos++;
			}

			// this is the part above 20MA
			double h = 0;
			int hw = 0;
			int highPos = 0;
			while ((pos <= lastbar) &&((QuoteData)quotes[pos]).low > ema20[pos])
			{
				hw++;
				if ((((QuoteData)quotes[pos]).high - ema20[pos] ) > h )
				{
					h = ((QuoteData)quotes[pos]).high;
					highPos = pos;
				}
				pos++;
			}

			if ( hw > 0 )	
			{
				//public BreakOut(double below, int belowPos, int belowWidth, double high, int highPos, int highWidth)
				BreakOut bo = new BreakOut(b, belowPos, bw, h, highPos, hw);
				breakouts.add(bo);
			}
			
		}
		
		return breakouts;

    }

    
    static public Vector<BreakOut> findMABreakoutDowns(Object[] quotes, double[] ema20, int start)
    {
    	int lastbar = quotes.length-1;
		Vector<BreakOut> breakouts = new Vector<BreakOut>();

		int pos = start;
		while ( pos <= lastbar ) 
		{
			// this is the part touch or above
			double b = 0;
			int bw = 0;
			int belowPos = 0;
			while ((pos <= lastbar) &&((QuoteData)quotes[pos]).high >= ema20[pos])
			{
				bw++;
				if (((QuoteData)quotes[pos]).high > b)
				{
					b = ((QuoteData)quotes[pos]).high;
					belowPos = pos;
				}
				pos++;
			}

			// this is the part below 20MA
			double h = 99;
			int hw = 0;
			int highPos = 0;
			while ((pos <= lastbar) &&((QuoteData)quotes[pos]).high < ema20[pos])
			{
				hw++;
				if (((QuoteData)quotes[pos]).low < h )
				{
					h = ((QuoteData)quotes[pos]).low;
					highPos = pos;
				}
				pos++;
			}

			if ( hw > 0 )	
			{
				//public BreakOut(double below, int belowPos, int belowWidth, double high, int highPos, int highWidth)
				BreakOut bo = new BreakOut(b, belowPos, bw, h, highPos, hw);
				breakouts.add(bo);
			}
			
		}
		
		return breakouts;
    
    }
		
		
    static public Vector<BreakOut> findMABreakoutUps2(Object[] quotes, double[] ema20, int start)
    {
    	int lastbar = quotes.length-1;
		Vector<BreakOut> breakouts = new Vector<BreakOut>();

		int pos = start;
		while ( pos <= lastbar ) 
		{
			// this is the part touch or below
			BreakOut breakout = new BreakOut();
			while ((pos <= lastbar) &&((QuoteData)quotes[pos]).low <= ema20[pos])
			{
				breakout.belowQuotes.add((QuoteData)quotes[pos]);
				pos++;
			}

			// this is the part below 20MA
			while ((pos <= lastbar) &&((QuoteData)quotes[pos]).low > ema20[pos])
			{
				breakout.highlowQuotes.add((QuoteData)quotes[pos]);
				pos++;
			}

			if ( breakout.highlowQuotes.size() > 0 )	
			{
				breakouts.add(breakout);
			}
			
		}
		
		return breakouts;
    
    }
    

    
    
    static public Vector<BreakOut> findMABreakoutDowns2(Object[] quotes, double[] ema20, int start)
    {
    	int lastbar = quotes.length-1;
		Vector<BreakOut> breakouts = new Vector<BreakOut>();

		int pos = start;
		while ( pos <= lastbar ) 
		{
			// this is the part touch or above
			BreakOut breakout = new BreakOut();
			while ((pos <= lastbar) &&((QuoteData)quotes[pos]).high >= ema20[pos])
			{
				breakout.belowQuotes.add((QuoteData)quotes[pos]);
				pos++;
			}

			// this is the part below 20MA
			while ((pos <= lastbar) &&((QuoteData)quotes[pos]).high < ema20[pos])
			{
				breakout.highlowQuotes.add((QuoteData)quotes[pos]);
				pos++;
			}

			if ( breakout.highlowQuotes.size() > 0 )	
			{
				breakouts.add(breakout);
			}
			
		}
		
		return breakouts;
    
    }

    
    

	boolean ifFirstPullBackUp(String symbol, Object[] quotes, double[] ema20, int pullback_width)
	{
		int lastbar = quotes.length-1;
		
		int pos = Pattern.findPriceCross20MAUp(quotes, ema20, 5);  // five bar consider other side
		if ( pos == Constants.NOT_FOUND )
			return false;
		logger.info(symbol + " 20MA cross up at " + ((QuoteData)quotes[pos]).time);
		
		if (Utility.findMAMoveDownDuration(quotes, ema20, pos) > 12 )
		{
			Vector breakouts = Pattern.findMABreakoutUps(quotes, ema20, pos);
			logger.info(symbol + " " + breakouts.size() + " breakouts after the cross");
		
			if ( breakouts.size() > 1 )  // should only be one
				return false;
		
			BreakOut bo = (BreakOut)breakouts.elementAt(0);
		
			logger.info(symbol + " breakout width " + bo.width);
			if ( bo.width < pullback_width)
				return false;
		
			return true;
		}
		
		return false;
	}

	
	boolean ifFirstPullBackDown(String symbol, Object[] quotes, double[] ema20, int pullback_width ) 
	{
		int lastbar = quotes.length-1;
		
		int pos = Pattern.findPriceCross20MADown(quotes, ema20);
		if ( pos == Constants.NOT_FOUND )
			return false;
		logger.info(symbol + " 20MA cross down at " + ((QuoteData)quotes[pos]).time);

		
		if (Utility.findMAMoveUpDuration(quotes, ema20, pos) > 12 )
		{
			Vector breakouts = Pattern.findMABreakoutDowns(quotes, ema20, pos);
			logger.info(symbol + " " + breakouts.size() + " breakouts after the cross");
		
			if ( breakouts.size() > 1 )  // should only be one
				return false;
		
			BreakOut bo = (BreakOut)breakouts.elementAt(0);
		
			logger.info(symbol + " breakout width " + bo.width);
			if ( bo.width < pullback_width)
				return false;
		
			return true;
		}
		
		return false;
	}


    static public Object[] findPastPullBacksUp(Object[] quotes, int begin, int end )
    {
    	Vector<Integer> pullbackpos = new Vector<Integer>();
    	
    	for ( int i = end-2; i > begin; i--)
    	{
    		if ((((QuoteData)quotes[i]).low < ((QuoteData)quotes[i+1]).low) && (((QuoteData)quotes[i]).low < ((QuoteData)quotes[i+2]).low)
    		   && (((QuoteData)quotes[i]).low < ((QuoteData)quotes[i-1]).low))
    		{
    			pullbackpos.add(i);
    			i -=2;
    		}
    	}
    	
    	return pullbackpos.toArray();
    }
    
    
    
    
    
    
    
    
		
	
/*	
	boolean ifFirstSecondPullBackUp(Object[] quotes, double[] ema20 )
	{
		int lastbar = quotes.length-1;
		int pos = findPriceCross20MAUp(quotes, ema20);
		logger.info(symbol + " 20MA cross up at " + ((QuoteData)quotes[pos]).time);
		
		Vector breakouts = find20MABreakoutUps(quotes, ema20, pos);
		
		int sum = 0;
		Iterator it = breakouts.iterator();
		while( it.hasNext())
		{
			BreakOut bo = (BreakOut)it.next();
			if ( bo.width >= PULLBACK_WIDTH)
				sum++;
		}

		logger.info(symbol + sum + " break outs up found");
		
		if ( sum > 3 )
			return false;
		else
			return true;
			
	}

	
	boolean ifFirstSecondPullBackDown(Object[] quotes, double[] ema20 )
	{
		int lastbar = quotes.length-1;
		int pos = findPriceCross20MADown(quotes, ema20);
		logger.info(symbol + " 20MA cross down at " + ((QuoteData)quotes[pos]).time);

		Vector breakouts = find20MABreakoutDowns(quotes, ema20, pos);
		
		int sum = 0;
		Iterator it = breakouts.iterator();
		while( it.hasNext())
		{
			BreakOut bo = (BreakOut)it.next();
			if ( bo.width >= PULLBACK_WIDTH)
				sum++;
		}
		
		logger.info(symbol + sum + " break outs down found");

		if ( sum > 3 )
			return false;
		else
			return true;
			
	}

	
	boolean	verify20MAStretchUpSequence( Object[] quotes, double[] ema20, double[] ema200, int start, int end)
	{
		// has to wait for 1 pull back if meets either width or heigh
		// no need to wait for 1 pull back if meets both width and height
		Vector<BreakOut> breakouts = find20MABreakoutUps(quotes, ema20, start);
		
		/*BreakOut last = (BreakOut)breakouts.lastElement();
		if ( last.below > 1 * pipSize )
		{
			logger.info(symbol + " last breakout up is no less than 1 pip in pull back");
			return false;		// it is prefered to have 		
		}
		
		
		/*
		Iterator it = breaks.iterator();
		while(it.hasNext()) {

		    Break element = (Break)it.next();
		    logger.info("Stretches: " + element.width + " " + element.height);
		    if ( element.width <= 3 )		// remove the breakouts that last less than 3 bars
		    {
		    	it.remove();
			    logger.info("Above stretch removed");
		    }
		}/
		
		int size = breakouts.size();
		logger.info(symbol + " " + size + " breakouts up");
		
		// it needs to be at least two stretches
		if ( size >= 2 )
		{
			// for width to qualify, it needs to be 
			for (int i = 0; i < size; i++ )
			{
				BreakOut b = breakouts.elementAt(i);
				if ( b.width > STRETCH_SETUP_WIDTH ) 
				{
					return true;
				}
			}
		}

		logger.info( symbol + " no breakouts up is more than " + STRETCH_SETUP_WIDTH + "wide");
		return false;
	}
	
	boolean	verify20MAStretchDownSequence( Object[] quotes, double[] ema20, double[] ema200, int start, int end)
	{
		// has to wait for 1 pull back if meets either width or heigh
		// no need to wait for 1 pull back if meets both width and height
		Vector<BreakOut> breakouts = find20MABreakoutDowns(quotes, ema20, start);
		
		/*BreakOut last = (BreakOut)breakouts.lastElement();
		if ( last.below > 1 * pipSize )
		{
			logger.info(symbol + " last breakout down is no less than 1 pip in pull back");
			return false;		// it is prefered to have
		}*/
		
		
		/*
		Iterator it = breaks.iterator();
		while(it.hasNext()) {

		    Break element = (Break)it.next();
		    logger.info("Stretches: " + element.width + " " + element.height);
		    if ( element.width <= 3 )		// remove the breakouts that last less than 3 bars
		    {
		    	it.remove();
			    logger.info("Above stretch removed");
		    }
		} 
		
		int size = breakouts.size();

		logger.info(symbol + size + " breakouts down");
		
		// it needs to be at least two stretches
		if ( size >= 2 )
		{
			// for width to qualify, it needs to be 
			for (int i = 0; i < size; i++ )
			{
				BreakOut b = breakouts.elementAt(i);
				if ( b.width > STRETCH_SETUP_WIDTH ) 
				{
					return true;
				}
			}
		}

		logger.info( symbol + " no breakouts down is more than " + STRETCH_SETUP_WIDTH + "wide");
		return false;
	}

	*/

    public static PushHighLow[] findPast1Highs(Object[] quotes, int start, int lastbar )
    {
    	QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return null;  // not the highest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	while( lastbar > start+1)
    	{
    		for ( int i = lastbar-1; i > start; i-- )
    		{
    			QuoteData afterHigh = Utility.getHigh( quotes, i+1, lastbar);
    			if (( afterHigh == null ) || ( afterHigh.high < ((QuoteData)quotes[i]).high))
    				continue;
    			
    			QuoteData beforeHigh = Utility.getHigh( quotes, start, i-1);
    			if (( beforeHigh == null ) || ( beforeHigh.high < ((QuoteData)quotes[i]).high))
    				continue;
    			
    			// now there is a "before" and a "after"
    			PushHighLow phl = new PushHighLow(beforeHigh.pos, afterHigh.pos);
    			phl.pullBack = Utility.getLow( quotes, beforeHigh.pos, afterHigh.pos); 
    			phls.add(phl);
    			
    			lastbar = beforeHigh.pos;
    			break;
    		}
    		lastbar--;
    	}
    	
    	PushHighLow [] ret = new PushHighLow[phls.size()];	
    	return (PushHighLow[]) phls.toArray(ret);
     }
    
    
    public static PushHighLow[] findPast1Lows(Object[] quotes, int start, int lastbar )
    {
    	QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return null;  // not the lowest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	while( lastbar > start+1)
    	{
    		for ( int i = lastbar-1; i > start; i-- )
    		{
    			QuoteData afterLow = Utility.getLow( quotes, i+1, lastbar);
    			if (( afterLow == null ) || ( afterLow.low > ((QuoteData)quotes[i]).low))
    				continue;
    			
    			QuoteData beforeLow = Utility.getLow( quotes, start, i-1);
    			if (( beforeLow == null ) || ( beforeLow.low > ((QuoteData)quotes[i]).low))
    				continue;

    			// now there is a "before" and a "after"
    			PushHighLow phl = new PushHighLow(beforeLow.pos, afterLow.pos);
    			phl.pullBack = Utility.getHigh( quotes, beforeLow.pos, afterLow.pos); 
    			phls.add(phl);
    			
    			lastbar = beforeLow.pos;
    			break;
    		}
    		lastbar--;
    	}
    	
    	PushHighLow [] ret = new PushHighLow[phls.size()];	
    	return (PushHighLow[]) phls.toArray(ret);
     }

    

//    public static PushHighLow[] findPast2Highs(Object[] quotes, int start, int lastbar )
    public static PushList findPast2Highs(QuoteData[] quotes, int start, int lastbar )
    {
    	QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return new PushList(Constants.DIRECTION_UP, null, start, lastbar);  // not the highest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();
    	int end = lastbar;

    	while( lastbar > start+1)
    	{
    		for ( int i = lastbar-1; i > start; i-- )
    		{
    			// we are checking i and i-1
    			QuoteData afterHigh = Utility.getHigh( quotes, i+1, lastbar);
    			if (( afterHigh == null ) || ( afterHigh.high < ((QuoteData)quotes[i]).high) || ( afterHigh.high < ((QuoteData)quotes[i-1]).high))
    				continue;
    			
    			QuoteData beforeHigh = Utility.getHigh( quotes, start, i-2);
    			if (( beforeHigh == null ) || ( beforeHigh.high < ((QuoteData)quotes[i]).high) || ( beforeHigh.high < ((QuoteData)quotes[i-1]).high))
    				continue;
    			
    			if ( afterHigh.high < beforeHigh.high )
    				continue;

    			int breakOutPos = afterHigh.pos;
    			for ( int j = i+1; j <= afterHigh.pos; j++)
    				if ( quotes[j].high > beforeHigh.high )
    				{
    					breakOutPos = j;
    					break;
    				}
    			// now there is a "before" and a "after"
    			//int extAfterHighPos = afterHigh.pos;
    			//while (( extAfterHighPos +1 <= lastbar) && ( quotes[extAfterHighPos+1].high > quotes[extAfterHighPos].high ))
    			//	extAfterHighPos++;
    			
    			PushHighLow phl = new PushHighLow(beforeHigh.pos, afterHigh.pos);
    			phl.breakOutPos = breakOutPos;
    			//PushHighLow phl = new PushHighLow(beforeHigh.pos, afterHigh.pos);
    			//phl.pushExt = extAfterHighPos;
    			phl.pullBack = Utility.getLow( quotes, beforeHigh.pos+1, afterHigh.pos); 
    			phls.add(phl);
    			
    			lastbar = beforeHigh.pos+1;
    			break;
    		}
    		lastbar--;
    	}
    	
    	//PushHighLow [] ret = new PushHighLow[phls.size()];	
    	//return (PushHighLow[]) phls.toArray(ret);

    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);
    	
    	// 0 is the lastbar
    	if ( size > 0 )
    	{
    		ret[size-1].pushStart = start;
	    	for ( int i = size-2; i >= 0 ; i--)
	    		ret[i].pushStart = ret[i+1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ )
	    	{
	    		ret[i].pushSize =  quotes[ret[i].prePos].high - quotes[ret[i].pushStart].low;
	    		ret[i].pullBackSize = quotes[ret[i].prePos].high - ret[i].pullBack.low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}
    	}
    	
    	return new PushList(Constants.DIRECTION_UP, ret, start, end);

     }
    
    
    //public static PushHighLow[] findPast2Lows(Object[] quotes, int start, int lastbar )
    public static PushList findPast2Lows(QuoteData[] quotes, int start, int lastbar )
    {
    	QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return new PushList(Constants.DIRECTION_DOWN, null, start, lastbar);  // not the lowest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();
    	int end = lastbar;

    	while( lastbar > start+1)
    	{
    		for ( int i = lastbar-1; i > start; i-- )
    		//for ( int i = lastbar-3; i > start+1; i-- )
    		{
    			QuoteData afterLow = Utility.getLow( quotes, i+1, lastbar);
    			if (( afterLow == null ) || ( afterLow.low > ((QuoteData)quotes[i]).low) || ( afterLow.low > ((QuoteData)quotes[i-1]).low))
    				continue;
    			
    			QuoteData beforeLow = Utility.getLow( quotes, start, i-2);
    			if (( beforeLow == null ) || ( beforeLow.low > ((QuoteData)quotes[i]).low)|| ( beforeLow.low > ((QuoteData)quotes[i-1]).low))
    				continue;

    			if ( afterLow.low > beforeLow.low )
    				continue;
    			
    			int breakOutPos = afterLow.pos;
    			for ( int j = i+1; j <= afterLow.pos; j++)
    				if ( quotes[j].low < beforeLow.low )
    				{	
    					breakOutPos = j;
    					break;
    				}
    			
    			// now there is a "before" and a "after"
    			//int extAfterLowPos = afterLow.pos;
    			//while (( extAfterLowPos +1 <= lastbar) && ( quotes[extAfterLowPos+1].low < quotes[extAfterLowPos].low ))
    				//extAfterLowPos++;
    			PushHighLow phl = new PushHighLow(beforeLow.pos, afterLow.pos);
    			phl.breakOutPos = breakOutPos;
    			//phl.pushExt = extAfterLowPos;
    			phl.pullBack = Utility.getHigh( quotes, beforeLow.pos+1, afterLow.pos);
    			phls.add(phl);
    			
    			lastbar = beforeLow.pos+1;
    			break;
    		}
    		lastbar--;
    	}
    	
    	//return (PushHighLow[]) phls.toArray(ret);
    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);
    	
    	if ( size > 0 )
    	{	
	    	// 0 is the lastbar
	    	ret[size-1].pushStart = start;
	    	for ( int i = size-2; i >= 0 ; i--)
	    		ret[i].pushStart = ret[i+1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ )
	    	{
	    		ret[i].pushSize = quotes[ret[i].pushStart].high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackSize = ret[i].pullBack.high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}
    	}
    	return new PushList(Constants.DIRECTION_DOWN, ret, start, end);
     }


    

    
    
    public static PushList findPast2Highs2(QuoteData[] quotes, int start, int lastbar )
    {
    	QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

    	if ( lastHigh == null )
			return new PushList(Constants.DIRECTION_UP, null, start, lastbar);  // not the highest

    	if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return new PushList(Constants.DIRECTION_UP, null, start, lastbar);  // not the highest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();
    	
   		for ( int i = (quotes[start+1].high < quotes[start].high)?start+1:start; i <= lastbar-3; i++ )
   		{
    			// looking for the next high
    		for ( int j = i+1; j <= lastbar; j++  )
    		{
    			if ( quotes[j].high > quotes[i].high)
    			{
    				// make sure it is 2 bar away
    				/*
    				if ( j > i + 2)
    				{
    	    			PushHighLow phl = new PushHighLow(i, j);
    	    			phl.pullBack = Utility.getLow( quotes, i, j); 
    	    			phls.add(phl);
    					
    				}*/
    				
    				
    				boolean firsthigh = true;
    				for ( int k = i+1; k < j; k++)
    				{
    					if ( quotes[k].high > quotes[i].high )
    						firsthigh = false;
    				}

    				// make sure it is 2 bar away
    				if (( j > i + 2) && ( firsthigh == true )) 
    				{
    	    			PushHighLow phl = new PushHighLow(i, j);
    	    			phl.pullBack = Utility.getLow( quotes, i+1, j-1); 
    	    			phls.add(phl);
    					
    				}
    				
    				i = j-1;  // will be add 1 at the end of the i loop
	    			break;
    			}
    		}
    	}
    	
    	//PushHighLow [] ret = new PushHighLow[phls.size()];	
    	//return (PushHighLow[]) phls.toArray(ret);

    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);
    	
    	// 0 is the lastbar
    	if ( size > 0 )
    	{
    		ret[0].pushStart = start;
	    	for ( int i = 1; i <= size-1; i++)
	    		ret[i].pushStart = ret[i-1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ )
	    	{
	    		ret[i].pushSize =  quotes[ret[i].prePos].high - quotes[ret[i].pushStart].low;
	    		ret[i].pullBackSize = quotes[ret[i].prePos].high - ret[i].pullBack.low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}


    		//ret[0].breakOutSize = ret[0].pushSize;
	    	for ( int i = 0; i < size-1; i++)
	    		ret[i].breakOutSize = quotes[ret[i+1].prePos].high - quotes[ret[i].prePos].high;

    	}
    	
    	return new PushList(Constants.DIRECTION_UP, ret, start, lastbar);

     }
    
    

    public static PushList findPast2Lows2(QuoteData[] quotes, int start, int lastbar )
    {
    	QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

    	if ( lastLow == null )
			return new PushList(Constants.DIRECTION_DOWN, null, start, lastbar);  // not the lowest
    	
		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return new PushList(Constants.DIRECTION_DOWN, null, start, lastbar);  // not the lowest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();
    	
   		for ( int i = (quotes[start+1].low > quotes[start].low)?start+1:start; i <= lastbar-3; i++ )
   		{
    		// looking for the next high
   			/*
    		for ( int j = i+1; j <= lastbar; j++  )
    		{
    			if ( quotes[j].low < quotes[i].low)
    			{
    				// make sure it is 2 bar away
    				if ( j > i + 2)
    				{
    	    			PushHighLow phl = new PushHighLow(i, j);
    	    			phl.pullBack = Utility.getHigh( quotes, i, j); 
    	    			phls.add(phl);
    					
    				}

    				i = j-1;  // will be add 1 at the end of the i loop
	    			break;
    			}
    		}*/
    		
    		
    		for ( int j = i+1; j <= lastbar; j++  )
    		{
    			if ( quotes[j].low < quotes[i].low)
    			{
    				boolean firstlow = true;
    				for ( int k = i+1; k < j; k++)
    				{
    					if ( quotes[k].low < quotes[i].low )
    						firstlow = false;
    				}
    				
    				if (( j > i + 2) && ( firstlow == true ))
    				{
    	    			PushHighLow phl = new PushHighLow(i, j);
    	    			phl.pullBack = Utility.getHigh( quotes, i+1, j-1); 
    	    			phls.add(phl);
    					
    				}

    				i = j-1;  // will be add 1 at the end of the i loop
	    			break;
    			}
    		}
    	}
    	
   // 	PushHighLow lastPush = phls.getLastElement();
    	//PushHighLow [] ret = new PushHighLow[phls.size()];	
    	//return (PushHighLow[]) phls.toArray(ret);

    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);
    	
    	// 0 is the lastbar
    	if ( size > 0 )
    	{
    		ret[0].pushStart = start;
	    	for ( int i = 1; i <= size-1; i++)
	    		ret[i].pushStart = ret[i-1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ )
	    	{
	    		ret[i].pushSize = quotes[ret[i].pushStart].high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackSize = ret[i].pullBack.high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;

	    		ret[i].pushExt = ret[i].curPos;
		    	while (( (ret[i].pushExt+1) <= lastbar) && ( quotes[ret[i].pushExt+1].low < quotes[ret[i].pushExt].low))
		    		ret[i].pushExt++;
	    	}

	    	
    		//ret[0].breakOutSize = ret[0].pushSize;
	    	for ( int i = 0; i < size-1; i++)
	    		ret[i].breakOutSize = quotes[ret[i].prePos].low - quotes[ret[i+1].prePos].low;

    	}
    	
    	return new PushList(Constants.DIRECTION_DOWN, ret, start, lastbar);

     }


    public static PushList findPast1Highs1(QuoteData[] quotes, int start, int lastbar )
    {
    	QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

    	if ( lastHigh == null )
			return null;  // not the highest

    	if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return null;  // not the highest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();
    	
    	PushHighLow phl = null;
   		//for ( int i = (quotes[start+1].high < quotes[start].high)?start+1:start; i <= lastbar-3; i++ )
   		for ( int i = start; i <= lastbar-2; i++ ){
    		for ( int j = i+1; j <= lastbar; j++ ){ // looking for the next high
    			if ( quotes[j].high > quotes[i].high){
    				if ( j > i + 1){ // make sure it is 2 bar away
    	    			phl = new PushHighLow(i, j);
    	    			phl.pullBack = Utility.getLow( quotes, i, j); 
    	    			phls.add(phl);
       	    			while ((j+1 <= lastbar) && (quotes[j+1].high > quotes[j].high ))
        					j++;
        				i = j-1;  // will be add 1 at the end of the i loop
        				break;
    				}else{// smaller than the gap
    					i = j-1;
    					break;
    				}
     			}
    		}
    	}
    	
   		if (( phl == null ) || (( phl != null )&& (lastbar != phl.curPos)))
   			return null;
    	//PushHighLow [] ret = new PushHighLow[phls.size()];	
    	//return (PushHighLow[]) phls.toArray(ret);

    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);
    	
    	// 0 is the lastbar
    	if ( size > 0 )
    	{
    		ret[0].pushStart = start;
	    	for ( int i = 1; i <= size-1; i++)
	    		ret[i].pushStart = ret[i-1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ )
	    	{
	    		ret[i].pushSize =  quotes[ret[i].prePos].high - quotes[ret[i].pushStart].low;
	    		ret[i].pushWidth =  ret[i].prePos - ret[i].pushStart + 1;
	    		ret[i].pullBackSize = quotes[ret[i].prePos].high - ret[i].pullBack.low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}

	    	for ( int i = 1; i < size; i++)
	    	{
	    		ret[i].breakOutSize =  quotes[ret[i].prePos].high - quotes[ret[i-1].prePos].high;
	    	}
    	}
    	
    	return new PushList(Constants.DIRECTION_UP, ret, start, lastbar);

     }

    
    public static PushList findPast1Lows1(QuoteData[] quotes, int start, int lastbar )
    {
    	QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

    	if ( lastLow == null )
			return null;  // not the lowest
    	
		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return null;  // not the lowest
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();
    	
    	PushHighLow phl = null;
   		//for ( int i = (quotes[start+1].high < quotes[start].high)?start+1:start; i <= lastbar-3; i++ )
   		for ( int i = start; i <= lastbar-2; i++ ){
    		for ( int j = i+1; j <= lastbar; j++ ){ // looking for the next high
    			if ( quotes[j].low < quotes[i].low){ // make sure it is 2 bar away
    				if ( j > i + 1){
    	    			phl = new PushHighLow(i, j);
    	    			phl.pullBack = Utility.getHigh( quotes, i, j); 
    	    			phls.add(phl);

    	    			while ((j+1 <= lastbar) && (quotes[j+1].low < quotes[j].low ))
        					j++;
        				i = j-1;  // will be add 1 at the end of the i loop
        				break;
    				}else{// smaller than the gap
    					i = j-1;
    					break;
    				}
    			}
    		}
    	}
    	
   		if (( phl == null ) || (( phl != null )&& (lastbar != phl.curPos)))
   			return null;
    	//PushHighLow [] ret = new PushHighLow[phls.size()];	
    	//return (PushHighLow[]) phls.toArray(ret);

    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);
    	
    	// 0 is the lastbar
    	if ( size > 0 )
    	{
    		ret[0].pushStart = start;
	    	for ( int i = 1; i <= size-1; i++)
	    		ret[i].pushStart = ret[i-1].pullBack.pos;
	    	
	    	for ( int i = 0; i < size; i++ )
	    	{
	    		ret[i].pushSize =  quotes[ret[i].pushStart].high - quotes[ret[i].prePos].low;
	    		ret[i].pushWidth =  ret[i].prePos - ret[i].pushStart + 1;
	    		ret[i].pullBackSize = ret[i].pullBack.high - quotes[ret[i].prePos].low;
	    		ret[i].pullBackRatio = ret[i].pullBackSize/ret[i].pushSize;
	    	}

	    	for ( int i = 1; i < size; i++)
	    	{
	    		ret[i].breakOutSize =  quotes[ret[i-1].prePos].low - quotes[ret[i].prePos].low;
	    	}
    	}
    	
    	return new PushList(Constants.DIRECTION_DOWN, ret, start, lastbar);

     }


     
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

    public static PushList findPastHighList(QuoteData[] quotes, int start, int lastbar )
    {
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	int start0 = start;
    	int start1 = start0;
    	int end0 = -1;
    	while ( start0 <= lastbar )
    	{
    		start1 = start0;
    		while  (( start1 < lastbar ) && (!( quotes[start1+1].high < quotes[start1].high)))
    			start1++;
    		
    		if ( start1 >= lastbar )
    			break;
    		
    		end0 = start1+2;  // start+1 already lower
    		while (( end0 <= lastbar ) && ( quotes[end0].high < quotes[start1].high))
    			end0++;

    		if ( end0 > lastbar )
    			break;

    		PushHighLow phl = new PushHighLow(start0, start1, end0);
			phl.pullBack = Utility.getLow( quotes, start1+1, end0); 
			phls.add(phl);

			start0 = end0;
			end0 = -1;
    	}
    	
    	if ( start1 >= lastbar)
    	{
    		PushHighLow phl = new PushHighLow(start0, start1, -1);
			phl.pullBack = null; 
			phls.add(phl);
    	}
    	else if ( end0 > lastbar)
    	{
    		PushHighLow phl = new PushHighLow(start0, start1, -1);
			phl.pullBack = Utility.getLow( quotes, start1+1, lastbar); 
			phls.add(phl);
    	}
    		
    	int size = phls.size();
    	PushHighLow [] ret = new PushHighLow[size];
    	phls.toArray(ret);

    	for ( int i = 0; i < size; i++ )
    	{
    		if ( ret[i].pullBack != null )
    			ret[i].pullBackSize = quotes[ret[i].prePos].high - ret[i].pullBack.low;
    		if ( i < size - 1 )
    			ret[i].breakOutSize = quotes[ret[i+1].prePos].high - quotes[ret[i].prePos].high;
    	}
    	
    	return new PushList(Constants.DIRECTION_UP, ret, start, lastbar);

     }

    
    
    
    
    
    
    
    
    
    
    
    
    
    public static QuoteData[] getLastFractualHighs2(QuoteData[] quotes, int start, int lastbar )
    {
    	Vector<QuoteData> f = new Vector<QuoteData>();
    	
    	int i = lastbar - 2;
    	
    	while ( i >= start + 2)
    	{
    		if (( quotes[i].high > quotes[i+1].high ) && ( quotes[i].high > quotes[i+2].high ) && ( quotes[i].high > quotes[i-1].high ) && ( quotes[i].high > quotes[i-2].high ))
    		{
    			f.add(0, quotes[i]);
    			i = i - 3;
    		}
    		else
    			i--;
    	}
    	
    	int size = f.size();
    	QuoteData [] ret = new QuoteData[size];
    	return f.toArray(ret);
    	
    }
    
    public static QuoteData[] getLastFractualLows2(QuoteData[] quotes, int start, int lastbar )
    {
    	Vector<QuoteData> f = new Vector<QuoteData>();
    	
    	int i = lastbar - 2;
    	
    	while ( i >= start + 2)
    	{
    		if (( quotes[i].low < quotes[i+1].low ) && ( quotes[i].low < quotes[i+2].low ) && ( quotes[i].low < quotes[i-1].low ) && ( quotes[i].low < quotes[i-2].low ))
    		{
    			f.add(0, quotes[i]);
    			i = i - 3;
    		}
    		else
    			i--;
    	}
    	
    	int size = f.size();
    	QuoteData [] ret = new QuoteData[size];
    	return f.toArray(ret);
    	
    }
    

    
    public static QuoteData[] getLastFractualHighs1(QuoteData[] quotes, int start, int lastbar )
    {
    	Vector<QuoteData> f = new Vector<QuoteData>();
    	
    	int i = lastbar - 2;
    	
    	while ( i >= start + 2)
    	{
    		if (( quotes[i].high > quotes[i+1].high ) && ( quotes[i].high > quotes[i-1].high ))
    		{
    			f.add(0, quotes[i]);
    			i = i - 3;
    		}
    		else
    			i--;
    	}
    	
    	int size = f.size();
    	QuoteData [] ret = new QuoteData[size];
    	return f.toArray(ret);
    	
    }
    
    public static QuoteData[] getLastFractualLows1(QuoteData[] quotes, int start, int lastbar )
    {
    	Vector<QuoteData> f = new Vector<QuoteData>();
    	
    	int i = lastbar - 2;
    	
    	while ( i >= start + 2)
    	{
    		if (( quotes[i].low < quotes[i+1].low ) && ( quotes[i].low < quotes[i-1].low ))
    		{
    			f.add(0, quotes[i]);
    			i = i - 3;
    		}
    		else
    			i--;
    	}
    	
    	int size = f.size();
    	QuoteData [] ret = new QuoteData[size];
    	return f.toArray(ret);
    	
    }

    
    
    
    
    public static PushHighLow[] findPastHighs_no_need_highest(Object[] quotes, int start, int lastbar )
    {
    	while( lastbar > start )
    	{	
    		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);
    		if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
    			lastbar--;
    		else
    		    break;
    	}
    	
    	if ( lastbar <= start )
    		return null;
    		    
		
    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	while( lastbar > start+1)
    	{
    		for ( int i = lastbar-1; i > start; i-- )
    		{
    			// we are checking i and i-1
    			QuoteData afterHigh = Utility.getHigh( quotes, i+1, lastbar);
    			if (( afterHigh == null ) || ( afterHigh.high < ((QuoteData)quotes[i]).high) || ( afterHigh.high < ((QuoteData)quotes[i-1]).high))
    				continue;
    			
    			QuoteData beforeHigh = Utility.getHigh( quotes, start, i-2);
    			if (( beforeHigh == null ) || ( beforeHigh.high < ((QuoteData)quotes[i]).high) || ( beforeHigh.high < ((QuoteData)quotes[i-1]).high))
    				continue;
    			
    			// now there is a "before" and a "after"
    			PushHighLow phl = new PushHighLow(beforeHigh.pos, afterHigh.pos);
    			phl.pullBack = Utility.getLow( quotes, beforeHigh.pos, afterHigh.pos); 
    			phls.add(phl);
    			
    			lastbar = beforeHigh.pos+1;
    			break;
    		}
    		lastbar--;
    	}
    	
    	PushHighLow [] ret = new PushHighLow[phls.size()];	
    	return (PushHighLow[]) phls.toArray(ret);
     }
    
    
    public static PushHighLow[] findPast2Lows_no_need_lowest(Object[] quotes, int start, int lastbar )
    {
    	while( lastbar > start )
    	{	
        	QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);
    		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
    			lastbar--;
    		else
    		    break;
    	}
    	
    	if ( lastbar <= start )
    		return null;

    	Vector<PushHighLow> phls = new Vector<PushHighLow>();

    	while( lastbar > start+1)
    	{
    		for ( int i = lastbar-1; i > start; i-- )
    		{
    			QuoteData afterLow = Utility.getLow( quotes, i+1, lastbar);
    			if (( afterLow == null ) || ( afterLow.low > ((QuoteData)quotes[i]).low) || ( afterLow.low > ((QuoteData)quotes[i-1]).low))
    				continue;
    			
    			QuoteData beforeLow = Utility.getLow( quotes, start, i-2);
    			if (( beforeLow == null ) || ( beforeLow.low > ((QuoteData)quotes[i]).low)|| ( beforeLow.low > ((QuoteData)quotes[i-1]).low))
    				continue;

    			// now there is a "before" and a "after"
    			PushHighLow phl = new PushHighLow(beforeLow.pos, afterLow.pos);
    			phl.pullBack = Utility.getHigh( quotes, beforeLow.pos, afterLow.pos); 
    			phls.add(phl);
    			
    			lastbar = beforeLow.pos+1;
    			break;
    		}
    		lastbar--;
    	}
    	
    	PushHighLow [] ret = new PushHighLow[phls.size()];	
    	return (PushHighLow[]) phls.toArray(ret);
     }

    
    
    
    
    
    
    
    
	public static PushHighLow findLastNHigh( Object[] quotes, int start, int lastbar, int n )
	{
		//return findLastNHigh_v2( quotes, start, lastbar, n );
		
		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

		if ((lastHigh == null ) || ((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return null;  // not the highest
		
		if (( lastbar - lastHigh.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos,lastbar);
			phl.pullBack = Utility.getLow( quotes, lastHigh.pos, lastbar); 
			return phl;
		}
		else
			return null;
		
	}

	
	public static PushHighLow findLastNHigh2( Object[] quotes, int start, int lastbar, int n )
	{
		int lastbarOrg = lastbar;
		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return null;  // not the highest
		
        while(((QuoteData)quotes[lastbar]).high > ((QuoteData)quotes[lastbar-1]).high)
        	lastbar--;
        
		lastHigh = Utility.getHigh(quotes, start, lastbar);
        
		int num = 0;
		for ( int i = lastHigh.pos+1; i < lastbarOrg; i++ )
		{
			if (((QuoteData)quotes[i]).high < lastHigh.high)
					num++;
		}
		
		if (num >= n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos,lastbarOrg);
			phl.pullBack = Utility.getLow( quotes, lastHigh.pos, lastbarOrg); 
			return phl;
		}
		else
			return null;
		
	}

	
	public static PushHighLow findLastNLow( Object[] quotes, int start, int lastbar, int n )
	{
		QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		if ((lastLow == null ) || (((QuoteData)quotes[lastbar]).low > lastLow.low ))
			return null;  // not the lowest
		
		if (( lastbar - lastLow.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos,lastbar);
			phl.pullBack = Utility.getHigh( quotes, lastLow.pos, lastbar); 
			return phl;
		}
		else
			return null;
		
	}


	public static PushHighLow findLastNLow2( Object[] quotes, int start, int lastbar, int n )
	{
		int lastbarOrg = lastbar;
		QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return null;  // not the lowest
		
        while(((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low)
        	lastbar--;
        
        lastLow = Utility.getLow(quotes, start, lastbar);
        
		int num = 0;
		for ( int i = lastLow.pos+1; i < lastbarOrg; i++ )
		{
			if (((QuoteData)quotes[i]).low > lastLow.low)
					num++;
		}
		
		if (num >= n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos,lastbarOrg);
			phl.pullBack = Utility.getHigh( quotes, lastLow.pos, lastbarOrg); 
			return phl;
		}
		else
			return null;
		
	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public static int calculatDirectionByCount( Object[] quotes, double[] ema20, int period)
	{
		int dir = 0;
		
		int lastbar = quotes.length - 1;
		
		for ( int i = lastbar; i > lastbar-period; i--)
		{
			if (((QuoteData)quotes[i]).low > ema20[i])
				dir++;
			if ( ((QuoteData)quotes[i]).high < ema20[i])
				dir--;
		}
		
		return dir;
		
	}

	
	public static Object[] findPastHighPeaksAboveMA( Object[] quotes, double[] ema, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		Peak peak = null;
		int pos = start;

		while(( pos <= end ) && ((QuoteData) quotes[pos]).low <= ema[pos])
				pos++;

		if ( pos > end )
			return null;

		for (;;)
		{	
			peak = new Peak();
			peak.highlowStartPos = pos;
			while (( pos <= end ) && (((QuoteData)quotes[pos]).low > ema[pos] ))
			{
				pos++;
			}
			
			peak.highlowEndPos = pos-1;
			if ( pos > end )
			{
				peaks.add(peak);
				return peaks.toArray();
			}
			
			// now below ema
			peak.pullbackStartPos = pos;
			while (( pos <= end ) && (((QuoteData)quotes[pos]).low <= ema[pos] ))
			{
				pos++;
			}
			
			peak.pullbackEndPos = pos-1;
			if ( pos > end )
			{
				peak.pullbackEndPos = end;
				peaks.add(peak);
				return peaks.toArray();
			}
			
			peaks.add(peak);
		}
			
	}
	

	public static Object[] findPastLowPeaksBelowMA( Object[] quotes, double[] ema, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		Peak peak = null;
		int pos = start;
		
		while(( pos <= end ) && ((QuoteData) quotes[pos]).high >= ema[pos])
				pos++;
		
		if ( pos > end )
			return null;

		for (;;)
		{	
			peak = new Peak();
			peak.highlowStartPos = pos;
			while (( pos <= end ) && (((QuoteData)quotes[pos]).high < ema[pos] ))
			{
				pos++;
			}
			
			peak.highlowEndPos = pos-1;
			if ( pos > end )
			{
				peaks.add(peak);
				return peaks.toArray();
			}
			
			// now touch or above ema
			peak.pullbackStartPos = pos;
			while (( pos <= end ) && (((QuoteData)quotes[pos]).high >= ema[pos] ))
			{
				pos++;
			}
			
			peak.pullbackEndPos = pos-1;
			if ( pos > end )
			{
				peak.pullbackEndPos = end;
				peaks.add(peak);
				return peaks.toArray();
			}
			
			peaks.add(peak);
		}
			
	}

	// the difference is that two bar needed to consider a peak
	public static Object[] findPastHighPeaks2AboveMA( Object[] quotes, double[] ema, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		Peak peak = null;
		int pos = start;

		while(( pos < end ) && !((((QuoteData) quotes[pos]).low > ema[pos]) && (((QuoteData) quotes[pos+1]).low > ema[pos+1])))
				pos++;

		if ( pos >= end )
			return null;

		for (;;)
		{	
			// now the next two is above
			peak = new Peak();
			peak.highlowStartPos = pos;
			while (( pos <= end ) && (((QuoteData)quotes[pos]).low > ema[pos] ))
			{
				pos++;
			}
			
			peak.highlowEndPos = pos-1;
			if ( pos > end )
			{
				peaks.add(peak);
				return peaks.toArray();
			}
			
			// now below ema
			peak.pullbackStartPos = pos;
			while (( pos < end ) && !((((QuoteData)quotes[pos]).low > ema[pos]) && (((QuoteData)quotes[pos+1]).low > ema[pos+1])))
			{
				pos++;
			}
			
			peak.pullbackEndPos = pos-1;
			if ( pos >= end )
			{
				peak.pullbackEndPos = end;
				peaks.add(peak);
				return peaks.toArray();
			}
			
			peaks.add(peak);
		}
			
	}
	

	public static  Object[] findPastLowPeaks2BelowMA( Object[] quotes, double[] ema, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		Peak peak = null;
		int pos = start;

		while(( pos < end ) && !((((QuoteData) quotes[pos]).high < ema[pos]) && (((QuoteData) quotes[pos+1]).high < ema[pos+1])))
				pos++;

		if ( pos >= end )
			return null;

		for (;;)
		{	
			// now the next two is above
			peak = new Peak();
			peak.highlowStartPos = pos;
			while (( pos <= end ) && (((QuoteData)quotes[pos]).high < ema[pos] ))
			{
				pos++;
			}
			
			peak.highlowEndPos = pos-1;
			if ( pos > end )
			{
				peaks.add(peak);
				return peaks.toArray();
			}
			
			// now below ema
			peak.pullbackStartPos = pos;
			while (( pos < end ) && !((((QuoteData)quotes[pos]).high < ema[pos]) && (((QuoteData)quotes[pos+1]).high < ema[pos+1])))
			{
				pos++;
			}
			
			peak.pullbackEndPos = pos-1;
			if ( pos >= end )
			{
				peak.pullbackEndPos = end;
				peaks.add(peak);
				return peaks.toArray();
			}
			
			peaks.add(peak);
		}
			
	}

	


	public static Vector<BreakOut> find20MABreakoutUps(Object[] quotes, double[] ema20, int start)
    {
    	int lastbar = quotes.length-1;
    	
		Vector<BreakOut> breakouts = new Vector<BreakOut>();
		int i = start;
		while ( i < lastbar ) 
		{
			double b = 99;
			while ((i <= lastbar) &&((QuoteData)quotes[i]).low <= ema20[i])
			{
				if ((((QuoteData)quotes[i]).low - ema20[i]) < b)
					b = ((QuoteData)quotes[i]).low - ema20[i];
				i++;
			}

			int w = 0;
			double h = 0;
			while ((i <= lastbar) &&((QuoteData)quotes[i]).low > ema20[i])
			{
				w++;
				if ((((QuoteData)quotes[i]).low - ema20[i] ) > h )
					h = ((QuoteData)quotes[i]).low - ema20[i];

				i++;
			}

			if ( w > 0 )
			{
				BreakOut bo = new BreakOut(b, 0,0, h, 0, w);
				breakouts.add(bo);
			}
			
		}
		
		return breakouts;

    }

	public static Vector<BreakOut> find20MABreakoutDowns(Object[] quotes, double[] ema20, int start)
    {
    	int lastbar = quotes.length-1;
    	
		Vector<BreakOut> breakouts = new Vector();
		int i = start;
		while ( i < lastbar ) 
		{
			double b = 99;
			while ((i <= lastbar) && (((QuoteData)quotes[i]).high >= ema20[i]))
			{
				if (( ema20[i] - ((QuoteData)quotes[i]).high) < b)
					b = ( ema20[i] - ((QuoteData)quotes[i]).high);
				i++;
			}

			int w = 0;
			double h = 0;
			while ((i <= lastbar) && ((QuoteData)quotes[i]).high < ema20[i])
			{
				w++;
				if (( ema20[i] - ((QuoteData)quotes[i]).high ) > h )
					h = ema20[i] - ((QuoteData)quotes[i]).high;

				i++;
			}

			if ( w > 0 )
			{
				BreakOut bo = new BreakOut(b, 0,0, h, 0, w);
//				BreakOut bo = new BreakOut(b,h,w);

				breakouts.add(bo);
			}
			
		}
		
		return breakouts;

    }


	
	public static PositionToMA countAboveMA(QuoteData[]quotes, double[] ema, int begin, int end )
	{
		PositionToMA ptm = new PositionToMA();

		for ( int i = begin; i <= end; i++)
		{
			if ( quotes[i].low > ema[i] )
				ptm.above++;
			else if ( quotes[i].high < ema[i])
				ptm.below++;
			else
				ptm.at++;
		}
		
		return ptm;
	}
		


	
		


	
}
