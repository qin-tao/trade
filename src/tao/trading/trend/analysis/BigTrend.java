package tao.trading.trend.analysis;

import java.util.Iterator;
import java.util.Vector;

import tao.trading.Constants;
import tao.trading.MATouch;
import tao.trading.Push;
import tao.trading.QuoteData;
import tao.trading.dao.PushList;

public class BigTrend
{
	static public int STATE_UNKNOWN = 0;
	
	public String state;
	static public String STATE_UP = "STATE_UP";
	static public String STATE_UP_BREAKOUT = "STATE_UP_BREAKOUT";
	static public String STATE_UP_DIVERAGE = "STATE_UP_DIVERAGE";
	static public String STATE_DOWN = "STATE_DOWN";
	static public String STATE_DOWN_BREAKOUT = "STATE_DOWN_BREAKOUT";
	static public String STATE_DOWN_DIVERAGE = "STATE_DOWN_DIVERAGE";
	
	// direction & strength
	public String direction;
	public int dir;

	// trend & strength
	public int trend;
	public int strength;

	public String feature;
	public int start;
	public boolean isTrendy;
	public int lastPushIndex, lastTrendyPushIndex, lastReallyTrendyPushIndex;
	public Vector<Push> pushes = new Vector<Push>();
	public MATouch[] maTouches;
	public QuoteData resistance, support;
	public PushList pushList;	

	
	public BigTrend()
	{
		super();
	}

	public BigTrend(int trend, int strength)
	{
		super();
		this.trend = trend;
		this.strength = strength;
	}

	public int getTrend()
	{
		return trend;
	}

	public int getStrength()
	{
		return strength;
	}



	public String toString( QuoteData[] quotes )
	{
		StringBuffer strBuf = new StringBuffer();
		
		strBuf.append("DIRECTION: " + dir + "\n");
		
		/*
		if ( resistance != null )
			strBuf.append("RESISTANCE: " + resistance.high + " " + resistance.time + "\n");
		if ( support != null )
			strBuf.append("SUPPORT: " + support.low + " " + support.time + "\n");
		
		if ( lastReallyTrendyPushIndex != 0 )
			strBuf.append("Very Large Trend:" + quotes[pushes.elementAt(lastReallyTrendyPushIndex).pushStart].time + " " + quotes[pushes.elementAt(lastReallyTrendyPushIndex).pushEnd].time +"\n");
		if ( lastTrendyPushIndex != 0 )
			strBuf.append("Large Trend:" + quotes[pushes.elementAt(lastTrendyPushIndex).pushStart].time + " " + quotes[pushes.elementAt(lastTrendyPushIndex).pushEnd].time +"\n");
		*/
		Iterator it = pushes.iterator();
		while (it.hasNext())
		{
			Push p = (Push)it.next();
			//strBuf.append(((p.direction == Constants.DIRECTION_UP)?"UP  ":"DOWN") + "  start:" + quotes[p.pushStart].time + " end:" + quotes[p.pushEnd].time + " duration:" + p.duration + "\n");
			strBuf.append(p.direction + "  start:" + quotes[p.pushStart].time + " end:" + quotes[p.pushEnd].time + " duration:" + p.duration + "\n");
		}
		
		
		//strBuf.append("Num of Touches:" + maTouches.length + "\n");
		/*
		if (( maTouches != null ) && ( maTouches.length > 0))
		{
			for ( int i = 0; i < maTouches.length; i++)
			{
				strBuf.append("Touch" + (i+1) + ":  " + quotes[maTouches[i].touchBegin].time + "-" + quotes[maTouches[i].touchEnd].time + "\n" );
			}
		}*/
		
		
		
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

	

}
