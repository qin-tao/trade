package tao.trading.setup;

import java.util.Vector;

import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.RST;

public class Reversal_RST {

	static public Vector<RST> countingRST( Object[] quotes, int begin, int end, int direction )
	{
		int pos = begin;
		int count = 0;
		Vector<RST> rsts = new Vector<RST>();
		
		if ( Constants.DIRECTION_UP == direction )
		{
			//while (( pos < end ) && (((QuoteData) quotes[pos+1]).high <= ((QuoteData) quotes[pos]).high))
			//	pos++;
			// find a lowest poing
			while (( pos < end ) && !((((QuoteData) quotes[pos-1]).low > ((QuoteData) quotes[pos]).low) && (((QuoteData) quotes[pos+1]).low > ((QuoteData) quotes[pos]).low))) 
				pos++;

			// next the higher high, starting point
			if ( pos >= end )
				return rsts;

			while (true )
			{	
				RST rst = new RST();
				rst.high = ((QuoteData) quotes[pos]).high;
				rst.highpos = pos;
				rst.NumOfHighBar = 1;

				// going higher
				while (( pos < end ) && 
						!((((QuoteData) quotes[pos+1]).high < ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low < ((QuoteData) quotes[pos]).low)))
				{
					if (((QuoteData) quotes[pos]).high > rst.high)
					{
						rst.high = ((QuoteData) quotes[pos]).high;
						rst.highpos = pos;
					}
					pos++;
					rst.NumOfHighBar++;
				}
				
			//	logger.info(symbol + " count high: " + rst.high + " at " + ((QuoteData) quotes[rst.highpos]).time );
				count++;
			
				if ( pos >= end )
				{
					rsts.add(rst);
					return rsts;
				}
				
				// now pos+1 is lower
				pos++;
				rst.low = 999;
				double lastHigh = ((QuoteData) quotes[pos]).high;

				while (( pos <= end ) && (((QuoteData) quotes[pos]).high <= lastHigh))
				{
					if (((QuoteData) quotes[pos]).low < rst.low )
					{
						rst.low = ((QuoteData) quotes[pos]).low;
						rst.lowpos = pos;
					}
					pos++;
					rst.NumOfLowBar++;
				}

			//	logger.info(symbol + " count high low: " + rst.low + " at " + ((QuoteData) quotes[rst.lowpos]).time );

				if ( pos > end )
				{
					rsts.add(rst);
					return rsts;
				}
				
				rsts.add(rst);
				
				// now pos > lastHigh, next count starts;
			}
		}
		else if ( Constants.DIRECTION_DOWN == direction )
		{
			//while (( pos < end ) && (((QuoteData) quotes[pos+1]).low >= ((QuoteData) quotes[pos]).low))
			//	pos++;
			// find a highest poing
			while (( pos < end ) && !((((QuoteData) quotes[pos-1]).high < ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).high < ((QuoteData) quotes[pos]).high))) 
				pos++;

			// next the higher high, starting point
			if ( pos >= end )
				return rsts;

			while (true )
			{	
				RST rst = new RST();
				rst.low = ((QuoteData) quotes[pos]).low;
				rst.lowpos = pos;
				rst.NumOfLowBar = 1;

				while (( pos < end ) && 
						!((((QuoteData) quotes[pos+1]).high > ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low > ((QuoteData) quotes[pos]).low)))
				{
					if (((QuoteData) quotes[pos]).low < rst.low)
					{
						rst.low = ((QuoteData) quotes[pos]).low;
						rst.lowpos = pos;
					}
					pos++;
					rst.NumOfLowBar++;
				}
				
			//	logger.info(symbol + " count high: " + rst.high + " at " + ((QuoteData) quotes[rst.highpos]).time );
				count++;
			
				if ( pos >= end )
				{
					rsts.add(rst);
					return rsts;
				}
				
				// now pos+1 is lower
				//rst.low = 999;
				double lastLow = ((QuoteData) quotes[pos]).low;
				pos++;
				while (( pos <= end ) && (((QuoteData) quotes[pos]).low >= lastLow))
				{
					if (((QuoteData) quotes[pos]).high > rst.high )
					{
						rst.high = ((QuoteData) quotes[pos]).high;
						rst.highpos = pos;
					}
					pos++;
					rst.NumOfHighBar++;
				}

			//	logger.info(symbol + " count high low: " + rst.low + " at " + ((QuoteData) quotes[rst.lowpos]).time );

				if ( pos > end )
				{
					rsts.add(rst);
					return rsts;
				}
				
				rsts.add(rst);
				
				// now pos > lastHigh, next count starts;
			}
		}

		
		return rsts;
	}

	

}
