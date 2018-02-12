package tao.trading.setup;

import tao.trading.QuoteData;

public class SlimJim
{
	public static double findRangeHighLow(QuoteData[] quotes, int start, int end)
	{
		// check for slimJim on open/close
		double high = 0;
		double low = 999;
		for ( int i = end; i >= start; i--)
		{
			if (quotes[i].high > high )
				high = quotes[i].high;
			if (quotes[i].low < low )
				low = quotes[i].low;
		}
		
		double range = high - low;

		return range;
	}

	public static double findRangeOpenClose(QuoteData[] quotes, int start, int end)
	{
		// check for slimJim on open/close
		double high = 0;
		double low = 999;
		for ( int i = end; i >= start; i--)
		{
			if (quotes[i].open > high )
				high = quotes[i].open;
			if (quotes[i].close > high )
				high = quotes[i].close;
			if (quotes[i].open < low )
				low = quotes[i].close;
			if (quotes[i].close < low )
				low = quotes[i].open;
		}
		
		double range = high - low;

		return range;
	}

	public static SlimJimDTO findSlimJim(QuoteData[] quotes, int start, int end)
	{
		double high = 0;
		int highPos = 0;
		double low = 999;
		int lowPos = 0;
		for ( int i = end; i >= start; i--){
			if (quotes[i].high > high ){
				high = quotes[i].high;
				highPos = i;
			}
			if (quotes[i].low < low ){
				low = quotes[i].low;
				lowPos = i;
			}
		}
		
		for ( int i = 1; i <= 3; i++){
			if ((( highPos - i ) < start ) || (( highPos + i ) > end ))
				return null;
			if ((( lowPos - i ) < start ) || (( lowPos + i ) > end ))
				return null;
		}
		
		
		double range = high - low;
		return new SlimJimDTO(highPos, lowPos, range);

	}

}
