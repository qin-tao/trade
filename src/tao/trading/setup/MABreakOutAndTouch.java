package tao.trading.setup;

import java.util.Vector;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.dao.MABreakOut;
import tao.trading.dao.MABreakOutList;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;

public class MABreakOutAndTouch
{
	
	/*
	
	
	public static MATouch findLastMATouchDown(Object[] quotes, double[] ema, int end )
	{
		int i = end;
		MATouch mat = new MATouch();
		
		while (((QuoteData)quotes[i]).high > ema[i])  // added for higher high
			i--;

		while (((QuoteData)quotes[i]).high < ema[i])
			i--;
		
		while (((QuoteData)quotes[i]).high >= ema[i] )
		{
			mat.touched++;
			
			if  (((QuoteData)quotes[i]).low > ema[i])
				mat.crossed++;
		
			if ( mat.high == null )
			{
				mat.high = (QuoteData)quotes[i];
				mat.high.pos = i;
			}
			else if (((QuoteData)quotes[i]).high > mat.high.high )
			{
				mat.high = ((QuoteData)quotes[i]);
				mat.high.pos = i;
			}
			
			i--;
		}
		
		return mat;
		
	}

	public static MATouch findLastMATouchUp(Object[] quotes, double[] ema, int end )
	{
		int i = end;
		MATouch mat = new MATouch();
		
		while (((QuoteData)quotes[i]).low < ema[i])  // added for higher high
			i--;

		while (((QuoteData)quotes[i]).low > ema[i])
			i--;
		
		while (((QuoteData)quotes[i]).low <= ema[i] )
		{
			mat.touched++;
			
			if  (((QuoteData)quotes[i]).high < ema[i])
				mat.crossed++;
		
			if ( mat.low == null )
			{
				mat.low = (QuoteData)quotes[i];
				mat.low.pos = i;
			}
			else if (((QuoteData)quotes[i]).low < mat.low.low )
			{
				mat.low = ((QuoteData)quotes[i]);
				mat.low.pos = i;
			}
			
			i--;
		}
		
		return mat;
		
	}*/

	/*
	public static MATouch findLastMATouchDown(Object[] quotes, double[] ema, int begin, int end )
	{
		int i = end;
		
		if (((QuoteData)quotes[i]).high >= ema[i])  // added for higher high
			return null;

		while (((QuoteData)quotes[i]).high < ema[i])
			i--;
		
		MATouch mat = new MATouch();
		while (((QuoteData)quotes[i]).high >= ema[i] )
		{
			mat.touched++;
			
			if  (((QuoteData)quotes[i]).low > ema[i])
				mat.crossed++;
		
			if ( mat.high == null )
			{
				mat.high = (QuoteData)quotes[i];
				mat.high.pos = i;
			}
			else if (((QuoteData)quotes[i]).high > mat.high.high )
			{
				mat.high = ((QuoteData)quotes[i]);
				mat.high.pos = i;
			}
			
			i--;
			if ( i < begin )
				break;
		}
		
		return mat;
		
	}

	public static MATouch findLastMATouchUp(Object[] quotes, double[] ema, int begin, int end )
	{
		int i = end;
		
		while (((QuoteData)quotes[i]).low <= ema[i])  // added for higher high
			return null;

		MATouch mat = new MATouch();

		while (((QuoteData)quotes[i]).low > ema[i])
			i--;
		
		while (((QuoteData)quotes[i]).low <= ema[i] )
		{
			mat.touched++;
			
			if  (((QuoteData)quotes[i]).high < ema[i])
				mat.crossed++;
		
			if ( mat.low == null )
			{
				mat.low = (QuoteData)quotes[i];
				mat.low.pos = i;
			}
			else if (((QuoteData)quotes[i]).low < mat.low.low )
			{
				mat.low = ((QuoteData)quotes[i]);
				mat.low.pos = i;
			}
			
			i--;
			if ( i < begin )
				break;
		}
		
		return mat;
		
	}*/

	/*
	// this is the same as MATouchUp except it requires 2 "above" to consider the pull back is over
	public static MATouch findLastMATouchUp2(Object[] quotes, double[] ema, int begin, int end )
	{
		int i = end;
		
		while (!((((QuoteData)quotes[i-1]).low > ema[i-1]) && (((QuoteData)quotes[i-2]).low > ema[i-2])))  
			i--;

		if ( i-3 < begin )
			return null;
		
		// now i-1 and i-2 is above 20MA, looking for the touch point
		i = i-3;
		while (((QuoteData)quotes[i]).low > ema[i]) 
			i--;

		if ( i < begin )
			return null;

		// now it begins the touch;
		MATouch mat = new MATouch();
		mat.touchEnd = i;
		
		while (((QuoteData)quotes[i]).low <= ema[i] )
		{
			mat.touched++;
			
			if  (((QuoteData)quotes[i]).high < ema[i])
				mat.crossed++;
		
			if ( mat.low == null )
			{
				mat.low = (QuoteData)quotes[i];
				mat.low.pos = i;
			}
			else if (((QuoteData)quotes[i]).low < mat.low.low )
			{
				mat.low = ((QuoteData)quotes[i]);
				mat.low.pos = i;
			}
			
			i--;
		}
		
		//  now it came out of touch
		if ( i > begin )
		{
			mat.touchBegin = i+1;
			return mat;
		}
		else
		{
			mat.touchBegin = begin;
			return mat;
		}
		
	}


	
	
	// this is the same as MATouchUp except it requires 2 "below" to consider the pull back is over
	public static MATouch findLastMATouchDown2(Object[] quotes, double[] ema, int begin, int end )
	{
		int i = end;
		
		while (!((((QuoteData)quotes[i-1]).high < ema[i-1]) && (((QuoteData)quotes[i-2]).high < ema[i-2])))  
			i--;

		if ( i-3 < begin )
			return null;
		
		// now i-1 and i-2 is below 20MA, looking for the touch point
		i = i-3;
		while (((QuoteData)quotes[i]).high < ema[i])
			i--;

		if ( i <= begin )
			return null;

		// now it begins the touch;
		MATouch mat = new MATouch();
		mat.touchEnd = i;
		
		while (((QuoteData)quotes[i]).high >= ema[i] )
		{
			mat.touched++;
			
			if  (((QuoteData)quotes[i]).low > ema[i])
				mat.crossed++;
		
			if ( mat.high == null )
			{
				mat.high = (QuoteData)quotes[i];
				mat.high.pos = i;
			}
			else if (((QuoteData)quotes[i]).high > mat.high.high )
			{
				mat.high = ((QuoteData)quotes[i]);
				mat.high.pos = i;
			}
			
			i--;
		}
		
		//  now it came out of touch
		if ( i > begin )
		{
			mat.touchBegin = i+1;
			return mat;
		}
		else
		{
			mat.touchBegin = begin;
			return mat;
			//return null;
		}
		
	}*/

	
	/////////////////////////////////////////////////////////////////////////////////////////
	//
	//  find touch down to 20MA, but require to be above 20MA first
	//
	/////////////////////////////////////////////////////////////////////////////////////////
	public static MATouch[] findMATouchDownsFromGoingUp(Object[] quotes, double[] ema, int begin, int end )
	{
		Vector<MATouch> v = new Vector<MATouch>();
		int i = end;
		
		while (((QuoteData)quotes[i]).low <= ema[i])  
			i--;

		if ( i < begin )
			return null;
		
		// now i is above 20MA, looking for the touch point
		while ( i > begin )
		{	
			while (((QuoteData)quotes[i]).low > ema[i])
				i--;

			//if ( i < begin )
				//return null;

			// now it begins the touch;
			MATouch mat = new MATouch();
			mat.touchEnd = i;
		
			while (((QuoteData)quotes[i]).low <= ema[i] )
			{
				mat.touched++;
			
				if  (((QuoteData)quotes[i]).high < ema[i])
					mat.crossed++;
		
				if ( mat.low == null )
				{
					mat.low = (QuoteData)quotes[i];
					mat.low.pos = i;
				}
				else if (((QuoteData)quotes[i]).low < mat.low.low )
				{
					mat.low = ((QuoteData)quotes[i]);
					mat.low.pos = i;
				}
			
				i--;
			}
			
			if ( i > begin )
			{
				mat.touchBegin = i+1;
				v.add(mat);
			}
		}

		MATouch [] ret = new MATouch[v.size()];	
    	return (MATouch[]) v.toArray(ret);

	}


	
	//////////////////////////////////////////////////////////////////////////////////////////
	//
	//  find touch down to 20MA, but require to be below 20MA first
	//
	//////////////////////////////////////////////////////////////////////////////////////////
	public static MATouch[] findMATouchUpsFromGoingDown(Object[] quotes, double[] ema, int begin, int end )
	{
		Vector<MATouch> v = new Vector<MATouch>();
		int i = end;
		
		while (((QuoteData)quotes[i]).high >= ema[i])  
			i--;

		if ( i < begin )
			return null;
		
		// now i is belowabove 20MA, looking for the touch point
		while ( i > begin )
		{	
			while (((QuoteData)quotes[i]).high < ema[i])
				i--;

			// now it begins the touch;
			MATouch mat = new MATouch();
			mat.touchEnd = i;
		
			while (((QuoteData)quotes[i]).high >= ema[i] )
			{
				mat.touched++;
			
				if  (((QuoteData)quotes[i]).low > ema[i])
					mat.crossed++;
		
				if ( mat.high == null )
				{
					mat.high = (QuoteData)quotes[i];
					mat.high.pos = i;
				}
				else if (((QuoteData)quotes[i]).high > mat.high.high )
				{
					mat.high = ((QuoteData)quotes[i]);
					mat.high.pos = i;
				}
			
				i--;
			}
			
			if ( i > begin )
			{
				mat.touchBegin = i+1;
				v.add(mat);
			}
		}

		MATouch [] ret = new MATouch[v.size()];	
    	return (MATouch[]) v.toArray(ret);
		
	}

	
	
	
	
	
	
	// not finished
	public static MATouch[] findNextMATouchDownsFromGoingUps(QuoteData[]quotes, double[] ema, int begin, int gap )
	{
		Vector<MATouch> v = new Vector<MATouch>();
		int lastbar = quotes.length - 1;
		int i = begin;
		
		int next = Constants.NOT_FOUND;
		while ( (next = Pattern.findNextPriceConsectiveAboveMA(quotes, ema, i, gap )) != Constants.NOT_FOUND)
		{
			MATouch mat = new MATouch();
			mat.highBegin = next;
			while ((next <= lastbar) && (quotes[next].low > ema[next]))
				next++;
			
			if ( next <= lastbar )
			{
				mat.highEnd = next-1;
				mat.touchBegin = next;
				v.add(mat);
				i = next;
			}
			else
			{
				v.add(mat);
				break;
			}
		}

		MATouch [] ret = new MATouch[v.size()];	
    	return (MATouch[]) v.toArray(ret);

	}

	
	public static MATouch[] findNextMATouchUpFromGoingDowns(QuoteData[]quotes, double[] ema, int begin, int gap )
	{
		Vector<MATouch> v = new Vector<MATouch>();
		int lastbar = quotes.length - 1;
		int i = begin;
		
		int next = Constants.NOT_FOUND;
		while ( (next = Pattern.findNextPriceConsectiveBelowMA(quotes, ema, i, gap )) != Constants.NOT_FOUND)
		{
			MATouch mat = new MATouch();
			mat.lowBegin = next;
			while ((next <= lastbar) && (quotes[next].high < ema[next]))
				next++;
			
			if ( next <= lastbar )
			{
				mat.lowEnd = next-1;
				mat.touchBegin = next;
				v.add(mat);
				i = next;
			}
			else
			{
				v.add(mat);
				break;
			}
		}

		MATouch [] ret = new MATouch[v.size()];	
    	return (MATouch[]) v.toArray(ret);

	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	public static MATouch findLastMATouchFromBelow(QuoteData[] quotes, double[] ema, int end )
	{
		MATouch mat = new MATouch();
		
		mat.touchEnd = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, 2);
		if ( mat.touchEnd == Constants.NOT_FOUND )
			return null;

		while (( mat.touchEnd > 0 ) && (((QuoteData)quotes[mat.touchEnd]).high < ema[mat.touchEnd]))
			mat.touchEnd--;
		if ( mat.touchEnd <= 0 )
			return null;
		
		mat.touchBegin = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, mat.touchEnd, 2);
		
		if ( mat.touchBegin == Constants.NOT_FOUND )
			return null;
		
		mat.touchBegin++;
		
		for ( int i = mat.touchBegin; i < mat.touchEnd-1; i++)
			if (( quotes[i].low > ema[i]) && ( quotes[i+1].low > ema[i+1]))
				return null;
			
		mat.high = Utility.getHigh( quotes, mat.touchBegin, mat.touchEnd);
		
		return mat;
			
	}


	public static MATouch findLastMATouchFromAbove(QuoteData[] quotes, double[] ema, int end )
	{
		MATouch mat = new MATouch();
		
		mat.touchEnd = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, 2);
		if ( mat.touchEnd == Constants.NOT_FOUND )
			return null;
		
		while (( mat.touchEnd > 0 ) && (((QuoteData)quotes[mat.touchEnd]).low > ema[mat.touchEnd]))
			mat.touchEnd--;
		if ( mat.touchEnd <= 0 )
			return null;
		
		
		mat.touchBegin = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, mat.touchEnd, 2);
		
		if ( mat.touchBegin == Constants.NOT_FOUND )
			return null;
		
		mat.touchBegin++;
		
		for ( int i = mat.touchBegin; i < mat.touchEnd-1; i++)
			if (( quotes[i].high < ema[i]) && ( quotes[i+1].high < ema[i+1]))
				return null;
			
		mat.low = Utility.getLow( quotes, mat.touchBegin, mat.touchEnd);
		
		return mat;
			
	}

	
	
	
	

	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//  require begin to set to the correct start ema20> ema50
	//  requires 3 bar as the next up
	//
	/////////////////////////////////////////////////////////////////////////////////////////////////////
	public static MATouch[] findMATouchDownsFromGoingUp(double[] ema20, double[] ema50, int begin, int end )
	{
		int numOfBarForNextUp = 3;
		
		Vector<MATouch> v = new Vector<MATouch>();
		int i = begin;
		
		// now i is above 20MA, looking for the touch point
		while ( i <= end )
		{	
			while (( i <= end ) && (ema20[i] > ema50[i]))
				i++;

			if ( i > end )
				break;
			
			// now it begins the touch;
			MATouch mat = new MATouch();
			mat.touchBegin = i;
		
			mat.touchEnd = Pattern.findNextMAConsectiveUp(ema20, ema50, numOfBarForNextUp, i);
			if ( mat.touchEnd == Constants.NOT_FOUND )
			{
				mat.touchEnd = end;
				v.add(mat);
				MATouch [] ret = new MATouch[v.size()];	
		    	return (MATouch[]) v.toArray(ret);
			}
			else
			{
				v.add(mat);
				i =  mat.touchEnd; 
			}
		}
		
		MATouch [] ret = new MATouch[v.size()];	
    	return (MATouch[]) v.toArray(ret);

	}


	/////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//  require begin to set to the correct start ema20 < ema50
	//  requires 3 bar as the next up
	//
	/////////////////////////////////////////////////////////////////////////////////////////////////////
	public static MATouch[] findMATouchUpsFromGoingDown(double[] ema20, double[] ema50, int begin, int end )
	{
		int numOfBarForNextUp = 3;
		
		Vector<MATouch> v = new Vector<MATouch>();
		int i = begin;
		
		// now i is above 20MA, looking for the touch point
		while ( i <= end )
		{	
			while (( i <= end ) && (ema20[i] < ema50[i]))
				i++;

			if ( i > end )
				break;

			// now it begins the touch;
			MATouch mat = new MATouch();
			mat.touchBegin = i;
		
			mat.touchEnd = Pattern.findNextMAConsectiveDown(ema20, ema50, numOfBarForNextUp, i);
			if ( mat.touchEnd == Constants.NOT_FOUND )
			{
				mat.touchEnd = end;
				v.add(mat);
				MATouch [] ret = new MATouch[v.size()];	
		    	return (MATouch[]) v.toArray(ret);
			}
			else
			{
				v.add(mat);
				i =  mat.touchEnd; 
			}
		}
		
		MATouch [] ret = new MATouch[v.size()];	
    	return (MATouch[]) v.toArray(ret);

	}


	
	
	public static MABreakOutList getMABreakOuts(QuoteData[] quotes, double[] ema )
	{
		int lastbar = quotes.length - 1;
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, lastbar, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, lastbar, 2);
	
		if ( lastUpPos > lastDownPos)
			return findMABreakOutsUp(quotes, ema, 0 );
		else if ( lastDownPos > lastUpPos)
			return findMABreakOutsDown(quotes, ema, 0 );
		else
			return null;
		
	}

	
	public static MABreakOutList getMABreakOuts2(QuoteData[] quotes, double[] ema)
	{
		int lastbar = quotes.length - 1;
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, lastbar, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, lastbar, 2);
	
		MABreakOutList brl = null;
		if ( lastUpPos > lastDownPos)
		{
			int begin = lastDownPos+2;
			brl = findMABreakOutsUp(quotes, ema, begin );
			brl.direction = Constants.DIRECTION_UP;
		}
		else if ( lastDownPos > lastUpPos)
		{
			int begin = lastUpPos+2;
			brl = findMABreakOutsDown(quotes, ema, begin );
			brl.direction = Constants.DIRECTION_DOWN;
		}
		
		if ( brl != null )
		{
			BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes);
		
			if ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction))
				brl.direction = Constants.DIRECTION_UP;
			else
				brl.direction = Constants.DIRECTION_DOWN;
		}

		return brl;
		
	}

	
	/////////////////////////////////////////////////////////////////////////////////////////
	//
	//  find touch down to 20MA, but require to be above 20MA first
	//
	/////////////////////////////////////////////////////////////////////////////////////////
	public static MABreakOutList findMABreakOutsUp(QuoteData[] quotes, double[] ema, int start ){
		int lastbar = quotes.length - 1;
		Vector<MABreakOut> v = new Vector<MABreakOut>();
		
		int breakOutMinWidth = 2;
		
		if ( start <= 0 ){
			int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, lastbar, 2);
			if ( lastDownPos == Constants.NOT_FOUND )
				return null;
			start = lastDownPos;
		}

		int i = start;
		
		int next = Constants.NOT_FOUND;
		while ( (next = Pattern.findNextPriceConsectiveAboveMA(quotes, ema, i, breakOutMinWidth )) != Constants.NOT_FOUND){

			MABreakOut mat = new MABreakOut(next);
			while ((next <= lastbar) && (quotes[next].low > ema[next]))
				next++;
			
			if ( next <= lastbar ){
				mat.end = next-1;
				mat.high = Utility.getHigh(quotes, mat.begin, mat.end);
				v.add(mat);
				i = next;
			}
			else{
				mat.end = Constants.NOT_FOUND;
				mat.high = Utility.getHigh(quotes, mat.begin, lastbar);
				v.add(mat);
				break;
			}
		}

		MABreakOut [] ret = new MABreakOut[v.size()];	
		MABreakOutList mbl = new MABreakOutList( Constants.DIRECTION_UP, (MABreakOut[]) v.toArray(ret));
		
		
		mbl.begin = start;
    	return mbl;
 	}


	
	/////////////////////////////////////////////////////////////////////////////////////////
	//
	//  find touch down to 20MA, but require to be above 20MA first
	//
	/////////////////////////////////////////////////////////////////////////////////////////
	public static MABreakOutList findMABreakOutsDown(QuoteData[] quotes, double[] ema, int start )
	{
		int lastbar = quotes.length - 1;
		Vector<MABreakOut> v = new Vector<MABreakOut>();
		
		int breakOutMinWidth = 2;
		
		if ( start <= 0 )
		{
			int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, lastbar, 2);
			if ( lastUpPos == Constants.NOT_FOUND )
				return null;
			start = lastUpPos;
		}

		int i = start;
		
		int next = Constants.NOT_FOUND;
		while ( (next = Pattern.findNextPriceConsectiveBelowMA(quotes, ema, i, breakOutMinWidth )) != Constants.NOT_FOUND)
		{
			MABreakOut mat = new MABreakOut(next);
			
			while ((next <= lastbar) && (quotes[next].high < ema[next]))
				next++;
			
			if ( next <= lastbar )
			{
				mat.end = next-1;
				mat.low = Utility.getLow(quotes, mat.begin, mat.end);
				v.add(mat);
				i = next;
			}
			else
			{
				mat.end = Constants.NOT_FOUND;
				mat.low = Utility.getLow(quotes, mat.begin, lastbar);
				v.add(mat);
				break;
			}
		}

		MABreakOut [] ret = new MABreakOut[v.size()];	
		MABreakOutList mbl = new MABreakOutList( Constants.DIRECTION_DOWN, (MABreakOut[]) v.toArray(ret));
		mbl.begin = start;
    	return mbl;
 	}

	

}
