package tao.trading.setup;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MACD;
import tao.trading.QuoteData;
import tao.trading.SetupStatus;
import tao.trading.dao.DiverageDAO;
import tao.trading.strategy.util.BarUtil;

public class DiverageWithWeakBreakOut {
	public SetupStatus status;

	public static DiverageWithWeakBreakOutDto detect(QuoteData data, QuoteData[] quotes, int FIXED_STOP, double PIP_SIZE )
	{
		int lastbar = quotes.length - 1;
		double[] ema = Indicator.calculateEMA(quotes, 20);

		if ((quotes[lastbar-2].high > ema[lastbar-2]) && (quotes[lastbar-2].high > quotes[lastbar-1].high )){
				for ( int i = lastbar-4; i > lastbar - 48 ; i--){
					DiverageDAO dv = DiverageSetup.findLastDiverageHigh(quotes, i, lastbar-2, 15, 100 *  PIP_SIZE); 
					if ( dv != null ){
			    		double breakOutDist = quotes[dv.curPos].high - quotes[dv.prePos].high;
			    		if ( breakOutDist < FIXED_STOP * PIP_SIZE/3 ){
			    			int gap = dv.curPos - dv.prePos + 1;
			    			double pullBackSize = dv.pullBackSize /PIP_SIZE;

			    			DiverageWithWeakBreakOutDto setup = new DiverageWithWeakBreakOutDto();
			    			setup.action = Constants.ACTION_SELL;
			    			setup.status = SetupStatus.TRIGGER;
			    			setup.triggerPos = lastbar; 
			    			setup.prevPeakPos = dv.prePos;
							return setup;
			    		}
					}
				}
			}
			else if ((quotes[lastbar-2].low < ema[lastbar-2]) && (quotes[lastbar-2].low < quotes[lastbar-1].low )){
				for ( int i = lastbar-4; i > lastbar - 48 ; i--){
					DiverageDAO dv = DiverageSetup.findLastDiverageLow(quotes, i, lastbar-2, 15, 100 * PIP_SIZE);
					if ( dv != null ) {
			    		double breakOutDist = quotes[dv.prePos].low - quotes[dv.curPos].low;
			    		if ( breakOutDist < FIXED_STOP * PIP_SIZE/3 ){
						
			    			int gap = dv.curPos - dv.prePos + 1;
			    			double pullBackSize = dv.pullBackSize /PIP_SIZE;
		
			    			DiverageWithWeakBreakOutDto setup = new DiverageWithWeakBreakOutDto();
			    			setup.action = Constants.ACTION_BUY;
			    			setup.status = SetupStatus.TRIGGER;
			    			setup.triggerPos = lastbar; 
			    			setup.prevPeakPos = dv.prePos;
							return setup;
			    		}
					}
				}
			}
			return null;
		}



	public static DiverageWithWeakBreakOutDto detectWithMACD(QuoteData data, QuoteData[] quotes, int FIXED_STOP, double PIP_SIZE )
	{
		int lastbar = quotes.length - 1;
		double[] ema = Indicator.calculateEMA(quotes, 20);
		double avgBarSize = BarUtil.averageBarSize(quotes);
		//System.out.println("Average Bar Size:" + avgBarSize);
	
		//if ((quotes[lastbar-2].high > ema[lastbar-2]) && (quotes[lastbar-2].high > quotes[lastbar-1].high )){
		if (quotes[lastbar].high > ema[lastbar]){
			for ( int i = lastbar-4; i > lastbar - 48 ; i--){
				//DiverageDAO dv = DiverageSetup.findLastDiverageHigh(quotes, i, lastbar-1, 30, 3 * avgBarSize); 
				//if (( dv != null ) && ( quotes[lastbar-1].close < quotes[dv.prePos].high)){

					DiverageDAO dv = DiverageSetup.findLastDiverageHigh(quotes, i, lastbar-2, 30, 3 * avgBarSize); 
					if ( dv != null ){
						
						int MACDNegative = 0;
						MACD[] macd = Indicator.calculateMACD( quotes );
						for ( int j = dv.prePos; j <= dv.curPos; j++ ){
							if ( macd[j].histogram < 0 )
								MACDNegative++;
						
						if ( MACDNegative > 0 ){
			    			//int pullBackWidth = dv.curPos - dv.prePos + 1;
			    			//double pullBackSize = dv.pullBackSize /PIP_SIZE;
			    			
			    			/*if ((( dv.pullBackWidth == 2)  && (dv.pullBackSize > 2 * avgBarSize ))|| 
			    			   (( dv.pullBackWidth == 3 ) && (dv.pullBackSize > 2.5 * avgBarSize ))|| 
			    			   (( dv.pullBackWidth > 3 ) && (dv.pullBackSize > 4 * avgBarSize )))*/ 
			    			{	
				    			DiverageWithWeakBreakOutDto setup = new DiverageWithWeakBreakOutDto();
				    			setup.action = Constants.ACTION_SELL;
				    			setup.status = SetupStatus.TRIGGER;
				    			setup.triggerPos = lastbar; 
				    			setup.prevPeakPos = dv.prePos;
								return setup;
			    			}
			    		}
					}
				}
			}
		}
		//else if ((quotes[lastbar-2].low < ema[lastbar-2]) && (quotes[lastbar-2].low < quotes[lastbar-1].low )){
		else if (quotes[lastbar].low < ema[lastbar]){
				for ( int i = lastbar-4; i > lastbar - 48 ; i--){
					DiverageDAO dv = DiverageSetup.findLastDiverageLow(quotes, i, lastbar-2, 30, 3 * avgBarSize);
					if ( dv != null ) {
						
						int MACDPositive = 0;
						MACD[] macd = Indicator.calculateMACD( quotes );
						for ( int j = dv.prePos; j <= dv.curPos; j++ ){
							if ( macd[j].histogram > 0 )
								MACDPositive++;

						if ( MACDPositive > 0 ){
			    			//int pullBackWidth = dv.curPos - dv.prePos + 1;
			    			//double pullBackSize = dv.pullBackSize /PIP_SIZE;
			    			/*
			    			if ((( dv.pullBackWidth == 2)  && (dv.pullBackSize > 2 * avgBarSize ))|| 
			    			   (( dv.pullBackWidth == 3 ) && (dv.pullBackSize > 2.5 * avgBarSize ))|| 
			    			   (( dv.pullBackWidth > 3 ) && (dv.pullBackSize > 4 * avgBarSize ))) */
			    			{	
				    			DiverageWithWeakBreakOutDto setup = new DiverageWithWeakBreakOutDto();
				    			setup.action = Constants.ACTION_BUY;
				    			setup.status = SetupStatus.TRIGGER;
				    			setup.triggerPos = lastbar; 
				    			setup.prevPeakPos = dv.prePos;
								return setup;
			    			}
			    		}
					}
				}
			}
		}
		return null;
	}
	
	
	public static DiverageWithWeakBreakOutDto detectWithPinBar(QuoteData data, QuoteData[] quotes, int FIXED_STOP, double PIP_SIZE )
	{
		int lastbar = quotes.length - 1;
		double[] ema = Indicator.calculateEMA(quotes, 20);
		double avgBarSize = BarUtil.averageBarSize(quotes);
		//System.out.println("Average Bar Size:" + avgBarSize);
	
		// detecting using the lastbar -1 for pin bar
		if ( quotes[lastbar].numOfUpdates == 0 ){	// only detect if this is the first bar
			if (quotes[lastbar-1].high > ema[lastbar-1]){
				for ( int i = lastbar-4; i > lastbar - 48 ; i--){
					DiverageDAO dv = DiverageSetup.findLastDiverageHighClosest(quotes, i, lastbar-1, 4, 15, 2.5 * avgBarSize); 
					if ( dv != null ){
						double preHigh = quotes[dv.prePos].high;
						if ( quotes[lastbar-1].close < preHigh ){ 
			    			DiverageWithWeakBreakOutDto setup = new DiverageWithWeakBreakOutDto();
			    			setup.action = Constants.ACTION_SELL;
			    			setup.status = SetupStatus.TRIGGER;
			    			setup.triggerPos = lastbar; 
			    			setup.prevPeakPos = dv.prePos;
			    			setup.triggerPrice = data.close;
							return setup;
			    		}
					}
				}
			}
			else if (quotes[lastbar-1].low < ema[lastbar-1]){
				for ( int i = lastbar-4; i > lastbar - 48 ; i--){
					DiverageDAO dv = DiverageSetup.findLastDiverageLowClosest(quotes, i, lastbar-1, 4, 15, 2.5 * avgBarSize); 
					if ( dv != null ) {
						double preLow = quotes[dv.prePos].low;
						if ( quotes[lastbar-1].close > preLow ){ 
			    			DiverageWithWeakBreakOutDto setup = new DiverageWithWeakBreakOutDto();
			    			setup.action = Constants.ACTION_BUY;
			    			setup.status = SetupStatus.TRIGGER;
			    			setup.triggerPos = lastbar; 
			    			setup.prevPeakPos = dv.prePos;
			    			setup.triggerPrice = data.close;
							return setup;
			    		}
					}
				}
			}
		}
		return null;
	}

}
	


