package tao.trading.setup;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.dao.FirstPullBackBreakOutDAO;
import tao.trading.strategy.util.Utility;

public class FirstPullBackBreakOutSetup
{
	public static FirstPullBackBreakOutDAO detect(QuoteData[] quotes, QuoteData data, int FIXED_STOP, double PIP_SIZE)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int prevUpPos, prevDownPos;

		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		int start = lastbar;
			
		if ( lastUpPos > lastDownPos)
		{
			int lastUpPosStart = lastUpPos;
			while ( quotes[lastUpPosStart].low > ema20[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			while ( quotes[prevDownPos-1].low < quotes[prevDownPos].low )
				prevDownPos--;

			// looking for upside
			for ( start = prevDownPos+1; start < lastbar; start++)
				if (( quotes[start].low > ema20[start]) && ( quotes[start+1].low > ema20[start+1]))
					break;
				
			if ( start == lastbar )
				return null;


			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++)
			{
				if ( quotes[i].low <= ema20[i] )
				{
					touch20MA = i;
					break;
				}
			}
				
			if ( touch20MA == 0 )
				return null;

				
			QuoteData firstBreakOut = Utility.getHigh( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;

			for ( int i = touch20MA+1; i < lastbar; i++)
			{
				if ( quotes[i].high > firstBreakOut.high )
				{
					//debug("first breakout high missed at " + quotes[i].time);
					return null;
				}
			}
					

			double entryPrice = firstBreakOut.high;

			if ( data.high > entryPrice )
			{
				int firstBreakOutL = firstBreakOut.pos;
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOutL+1, lastbar );
				if (( lowAfterFirstBreakOut != null ) && ((entryPrice - lowAfterFirstBreakOut.low) > 1.5 * FIXED_STOP * PIP_SIZE))
				{
					double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
					if ( diverage > 1.5 * FIXED_STOP )
					{
						//info("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}
				}

				return new FirstPullBackBreakOutDAO( Constants.DIRECTION_UP, entryPrice, start);
			
			}

		}	
		else if ( lastDownPos > lastUpPos )
		{	
			int lastDownPosStart = lastDownPos;
			while ( quotes[lastDownPosStart].high < ema20[lastDownPosStart])
				lastDownPosStart--;
				
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
				
			while ( quotes[prevUpPos-1].high > quotes[prevUpPos].high )
				prevUpPos--;

			// looking for upside
			for ( start = prevUpPos+1; start < lastbar; start++)
				if (( quotes[start].high < ema20[start]) && ( quotes[start+1].high < ema20[start+1]))
					break;
				
			if ( start == lastbar )
				return null;

			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++)
			{
				if ( quotes[i].high >= ema20[i])
				{
					touch20MA=i;
					break;
				}
			}

			if ( touch20MA == 0 )
				return null;

			QuoteData firstBreakOut = Utility.getLow( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;
				

			//for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
			for ( int i = touch20MA+1; i < lastbar; i++)
			{
				if ( quotes[i].low < firstBreakOut.low )
				{
					//debug("first breakout low missed at " + quotes[i].time);
					return null;
				}
			}


			double entryPrice = firstBreakOut.low;
				
			if (data.low < entryPrice ) 
			{
				// filter 1:  deep pullbacks
				int firstBreakOutL = firstBreakOut.pos;
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOutL+1, lastbar );
				if (( highAfterFirstBreakOut != null ) && ((highAfterFirstBreakOut.high - entryPrice) > 1.5 * FIXED_STOP * PIP_SIZE))
				{
					double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
					if ( diverage > 1.5 * FIXED_STOP )
					{
						//info("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}
				}
						
				return new FirstPullBackBreakOutDAO( Constants.DIRECTION_DOWN, entryPrice, start);
			}

		}
		
		return null;
	}


	
}
