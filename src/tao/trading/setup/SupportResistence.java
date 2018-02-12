package tao.trading.setup;

import tao.trading.Indicator;
import tao.trading.QuoteData;

public class SupportResistence {

	public static int findSupport(QuoteData[] quotes, int start, int end, int supportRange){
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double low = 999;
		int lowPos = -1;
		for ( int i = end; i >= start; i--){
			if (( quotes[i].low < ema20[i]) && (quotes[i].low < low )){
				low = quotes[i].low;
				lowPos = i;
			}
		}
		
		if ( lowPos > 0 ){
			for ( int i = 1; i <= supportRange; i++){
				if (quotes[lowPos - i].low < quotes[lowPos].low)
					return -1;
			}
		}
		
		return lowPos;

	}

	
	public static int findResistence(QuoteData[] quotes, int start, int end, int supportRange){
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double high = 0;
		int highPos = -1;
		for ( int i = end; i >= start; i--){
			if (( quotes[i].low > ema20[i]) && (quotes[i].high > high )){
				high = quotes[i].high;
				highPos = i;
			}
		}
		
		if ( highPos > 0 ){
			for ( int i = 1; i <= supportRange; i++){
				if (quotes[highPos - i].high > quotes[highPos].high)
					return -1;
			}
		}
		
		return highPos;

	}

}
