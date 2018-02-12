package tao.trading;

import tao.trading.strategy.util.Utility;

public class MovingAverageAlgorithm {

	public static int findLastLowTouchOrBelowMA( Object[] quotes, double ema[] )
	{
		int lastbar = quotes.length - 1;
		int end = lastbar-1;
		
		while (((QuoteData)quotes[end]).low <= ema[end])
			end--;
		
		//  now here is above ema
		while (((QuoteData)quotes[end]).low > ema[end])
			end--;
		
		int lowend = end;
		while (((QuoteData)quotes[end]).low <= ema[end])
			end--;
		
		int lowbegin = end;
		
		return Utility.getLow(quotes, lowbegin, lowend).pos;
		
		
	}
	
	
	public static int findLastHighTouchOrAboveMA( Object[] quotes, double ema[] )
	{
		int lastbar = quotes.length - 1;
		int end = lastbar-1;
		
		while (((QuoteData)quotes[end]).high >= ema[end])
			end--;
		
		//  now here is below ema
		while (((QuoteData)quotes[end]).high < ema[end])
			end--;
		
		int highend = end;
		while (((QuoteData)quotes[end]).high >= ema[end])
			end--;
		
		int highbegin = end;
		
		return Utility.getHigh(quotes, highbegin, highend).pos;
		
		
	}

	
}
