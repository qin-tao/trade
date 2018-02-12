package tao.trading.trend.analysis;

import java.util.Iterator;
import java.util.Vector;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.Push;
import tao.trading.QuoteData;
import tao.trading.dao.PushList;
import tao.trading.strategy.util.Utility;

public class BigTrendAnalysis
{
	public int direction;
	

	public String feature;
	public int start;
	public boolean isTrendy;
	public int lastTrendPushIndex;
	public Vector<Push> pushes = new Vector<Push>();
	public MATouch[] maTouches;
	public QuoteData resistance, support;
	
	
	public String toString( QuoteData[] quotes )
	{
		StringBuffer strBuf = new StringBuffer();
		
		strBuf.append("DIRECTION: " + direction + "\n");
		
		if ( resistance != null )
			strBuf.append("RESISTANCE: " + resistance.high + " " + resistance.time + "\n");
		if ( support != null )
			strBuf.append("SUPPORT: " + support.low + " " + support.time + "\n");
		
		
		Iterator it = pushes.iterator();
		while (it.hasNext())
		{
			Push p = (Push)it.next();
			strBuf.append(((p.direction == Constants.DIRECTION_UP)?"UP  ":"DOWN") + "  start:" + quotes[p.pushStart].time + " end:" + quotes[p.pushEnd].time + " duration:" + p.duration + "\n");
		}
		
		
		
		//strBuf.append("Num of Touches:" + maTouches.length + "\n");
		
		if (( maTouches != null ) && ( maTouches.length > 0))
		{
			for ( int i = 0; i < maTouches.length; i++)
			{
				strBuf.append("Touch" + (i+1) + ":  " + quotes[maTouches[i].touchBegin].time + "-" + quotes[maTouches[i].touchEnd].time + "\n" );
			}
		}
		
		
		
		return strBuf.toString();
	}
	

	
	public int getLastPushIndex( int direction )
	{
		int lastPush = pushes.size() - 1;
		
		for ( int i = lastPush; i>=0; i--)
		{
			if ((pushes.elementAt(i)).direction == direction)
			{
				return i;
			}
		}
		
		return -1;
		
	}
	

	public Push getLastPush( int direction )
	{
		int lastPush = pushes.size() - 1;
		
		for ( int i = lastPush; i>=0; i--)
		{
			if ((pushes.elementAt(i)).direction == direction)
			{
				return pushes.elementAt(i);
			}
		}
		
		return null;
		
	}

	
	public Push getLastPush()
	{
		return pushes.lastElement();

	}
	
	

	BigTrend calculateTrend( QuoteData[] quotes )
	{
		// this is to be expected on 240 min chart
		int lastbar = quotes.length - 1;
		double[] ema5 = Indicator.calculateEMA(quotes, 5);
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		BigTrend bigTrend = new BigTrend();
		bigTrend.direction = Constants.DIRT_UNKNOWN;

		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 5);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 5);

		// looking for first 5MA cross that has more than 5 bars
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			int start = lastDownPos;
			while (!(( quotes[start].low > ema20[start] ) && ( quotes[start+1].low > ema20[start+1] )))
					start++;
			bigTrend.start = start;
			
			int touch20MA = -1;
			for ( int i = start+1; i <=lastbar; i++)
			{
				if (quotes[i].low < ema20[i])
				{
					touch20MA = i;
					break;
				}
			}
			
			if ( touch20MA == -1 )
			{
				bigTrend.state = BigTrend.STATE_BREAKOUT;
				return bigTrend;
			}
			
			
			// now touched 20MA, to see how deep the pull back is
			boolean deepPullBack = false;
			for ( int i = lastbar; i < start+1; i--)
			{	
				PushList pushList = Pattern.findPast1Highs1(quotes, start, i);

				if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 ))
				{
					// check each one System.out.println("find high @" + data.time);
					for ( int i = 0; i < pushList.phls.length; i++)
					{
						if (( quotes[pushList.phls[i].prePos].high - pushList.phls[i].pullBack.low )  > 2 * FIXED_STOP * SIZE ) 
						{
							deepPullBack = true;
							break;
						}
					}
				}
			}
				
			
			
			//
			// if it has not reach any push, to see if there's any large pull back that would change the trend
			QuoteData highestPoint = Utility.getHigh( quotes, start, lastbar);
			QuoteData latestLow = Utility.getLow ( quotes, highestPoint.pos + 1, lastbar);
			if ( highestPoint.high - latestLow.low ) > 200 * PIP_SIZE )
			{
				
			}
			
		}
		return null;
	}


	
	
	

	
	
	

}
