package tao.trading.setup;

import java.util.Vector;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MACD;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.dao.DiverageDAO;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.strategy.util.Utility;

public class DiverageSetup {

	static public DiverageDAO macd_diverage(QuoteData data, QuoteData[] quotes, double[] ema, int pushStart, int direction) {
		
		int lastbar = quotes.length - 1;
		PushList pushList = null;
				
		if (direction == Constants.DIRECTION_UP) {

			pushList = PushSetup.getUp2PushList(quotes, pushStart, lastbar);

			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes[lastPush.prePos].high;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				double pullback =  quotes[phl.prePos].high - phl.pullBack.low;
				pushList.calculatePushMACD(quotes);
				int negative = phls[lastPushIndex].pullBackMACDNegative;

			
				if (negative > 0) {
					DiverageDAO diverageDAO = new DiverageDAO();
					diverageDAO.action = Constants.ACTION_SELL;
					diverageDAO.curPos = lastbar;
					diverageDAO.prePos = phl.prePos;
					diverageDAO.pushStart = pushStart;
					return diverageDAO;
				}
			}
		} else if (direction == Constants.DIRECTION_DOWN) {

			pushList = PushSetup.getDown2PushList(quotes, pushStart, lastbar);

			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes[lastPush.prePos].low;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				double pullback = phl.pullBack.high - quotes[phl.prePos].low;
				pushList.calculatePushMACD(quotes);
				int positive = phls[lastPushIndex].pullBackMACDPositive;

				if (positive > 0) {
					DiverageDAO diverageDAO = new DiverageDAO();
					diverageDAO.action = Constants.ACTION_SELL;
					diverageDAO.curPos = lastbar;
					diverageDAO.prePos = phl.prePos;
					diverageDAO.pushStart = pushStart;
					return diverageDAO;
				}
			}
		}
		return null;
	}

	
	/*****************************************************
	 * 
	 * This returns the furthest peak
	 * 
	 ****************************************************/
	
	public static DiverageDAO findLastDiverageHigh(QuoteData[] quotes, int start, int lastbar, int gapMaxWidth, double diverageSize/*, Double breakOutSize*/ )
    {
    	int dStart = lastbar - 1;
    	DiverageDAO dv = null;
    	next: while( --dStart >= start ){

    		// dStart should be lower than the lastbar
    		if ( quotes[dStart].high > quotes[lastbar].high )
    			continue;
    		
    		// dStart should be the highest other than last bar
    		QuoteData lastHigh = Utility.getHigh(quotes, dStart+1, lastbar-1);
    		if ( lastHigh.high > quotes[dStart].high )
    			continue;
    		if (quotes[lastbar].high < lastHigh.high )
    			continue;  // not the highest
    		
    		
    		// n bar before dStart should be lower
    		int n = 12;
    	/*	for ( int i = 1; i <=n; i++){
    			if ( quotes[dStart-i].high >= quotes[dStart].high){
    				continue next;
    			}
    		}*/
    		
    		int nBarLower = 0;
    		int lowestPos = dStart-1;
      		for ( int i = dStart-1; i >=0; i--){
      			
    			if ( quotes[i].high < quotes[dStart].high){
    				nBarLower++;
    				if (quotes[i].low < quotes[lowestPos].low){
    					lowestPos = i;
    				}
    			}
    			else
    				break;
    		}
      		if (nBarLower <= 12) 
      			continue next;
      		//if(( quotes[dStart].high - quotes[lowestPos].low) < (diverageSize * 0.7))
      		//	continue next;
    		

    		QuoteData pullBackLow = Utility.getLow(quotes, dStart, lastbar-1);
    		if  (( quotes[dStart].high - pullBackLow.low ) < diverageSize  )
    			continue;
    		
    		//if ( lastbar - dStart > gapMaxWidth)
    		//	continue;

       		//return new DiverageDAO(dStart, lastbar);
    		DiverageDAO dv1 = new DiverageDAO(dStart, lastbar);
    		dv1.pullBackSize = quotes[dStart].high - pullBackLow.low;
    		dv1.pullBackWidth = lastbar - dStart+1;
    		if (( dv == null ) ||  ( quotes[dv1.prePos].high > quotes[dv.prePos].high ))
    			dv = dv1;

    	}
    	
    	return dv;
     }

	
	/*****************************************************
	 * 
	 * This returns the furthest peak
	 * 
	 ****************************************************/
	public static DiverageDAO findLastDiverageLow(QuoteData[] quotes, int start, int lastbar, int gapMaxWidth, double diverageSize )
    {
    	int dStart = lastbar - 1;
    	DiverageDAO dv = null;
    	next: while( --dStart >= start ){

    		// dStart should be lower than the lastbar
    		if ( quotes[dStart].low < quotes[lastbar].low )
    			continue;
    		
    		// dStart should be the highest other than last bar
    		QuoteData lastLow = Utility.getLow(quotes, dStart+1, lastbar-1);
    		if ( lastLow.low < quotes[dStart].low )
    			continue;
    		if (quotes[lastbar].low > lastLow.low )
    			continue;  // not the highest
    		
    		// n bar before dStart should be lower
    		int n = 12;
    		/*for ( int i = 1; i <=n; i++){
    			if ( quotes[dStart-i].low <= quotes[dStart].low)
    				continue next;
    		}*/

    		int nBarHigher = 0;
    		int highestPos = dStart-1;
      		for ( int i = dStart-1; i >=0; i--){
      			
    			if ( quotes[i].low > quotes[dStart].low){
    				nBarHigher++;
    				if (quotes[i].high > quotes[highestPos].high){
    					highestPos = i;
    				}
    			}
    			else
    				break;
    		}
      		if (nBarHigher <= 12) 
      			continue next;
      		//if((  quotes[highestPos].high - quotes[dStart].low) < (diverageSize * 0.7))
      		//	continue next;


    		QuoteData pullBackHigh = Utility.getHigh(quotes, dStart, lastbar-1);
    		if  (( pullBackHigh.high - quotes[dStart].low ) < diverageSize )
    			continue;
    		
    		//if ( lastbar - dStart > gapMaxWidth)
    		//	continue;

    		DiverageDAO dv1 = new DiverageDAO(dStart, lastbar);
    		dv1.pullBackSize = pullBackHigh.high - quotes[dStart].low;
    		dv1.pullBackWidth = lastbar - dStart+1;
    		
    		if (( dv == null ) ||  ( quotes[dv1.prePos].high > quotes[dv.prePos].high ))
    			dv = dv1;

    	}
    	
    	return dv;
     }
	
	/*****************************************************
	 * 
	 * This returns the closes peak
	 * 
	 ****************************************************/
	public static DiverageDAO findLastDiverageHighClosest(QuoteData[] quotes, int start, int lastbar, /*int preBarsLow, int gapMaxWidth,*/ double diverageSize )
    {
		if ( start < 0 )
			start = 2;

		int dStart = lastbar - 1;
    	DiverageDAO dv = null;
    	while( --dStart >= start ){

    		// dStart should be lower than the lastbar
    		if ( quotes[dStart].high > quotes[lastbar].high )
    			continue;
    		
    		// dStart should be the highest other than last bar
    		QuoteData lastHigh = Utility.getHigh(quotes, dStart+1, lastbar-1);
    		if ( lastHigh.high > quotes[dStart].high )
    			continue;
    		if ( lastHigh.high > quotes[lastbar].high )
    			continue;  // not the highest
    		
    		QuoteData pullBackLow = Utility.getLow(quotes, dStart+1, lastbar);
    		DiverageDAO dv1 = new DiverageDAO(dStart, lastbar);
    		dv1.pullBackSize = quotes[dStart].high - pullBackLow.low;
    		dv1.pullBackWidth = lastbar - dStart - 1;
    		if (( dv1.pullBackSize < diverageSize )/* || ( dv1.pullBackWidth > gapMaxWidth )*/)
    			continue;

    		// n bar before dStart should be lower
    		int preBarsLow = dv1.pullBackWidth;   // the bar below should be at least the pullback width
    		int nBarLower = 0;
      		for ( int i = dStart-1; i >=0; i--)
    			if ( quotes[i].high < quotes[dStart].high)
    				nBarLower++;
    			else
    				break;
    		if (nBarLower <= preBarsLow)   // default 12
      			continue;
    		

    		return dv1;
    	}
    	
    	return dv;
     }

	
	/*****************************************************
	 * 
	 * This returns the closes peak
	 * 
	 ****************************************************/
	public static DiverageDAO findLastDiverageLowClosest(QuoteData[] quotes, int start, int lastbar, /*int prevBarsHigh, int gapMaxWidth,*/ double diverageSize )
    {
		if ( start < 0 )
			start = 2;
		
    	int dStart = lastbar - 1;
    	DiverageDAO dv = null;
    	while ( --dStart >= start ){

    		// dStart should be lower than the lastbar
    		if ( quotes[dStart].low < quotes[lastbar].low )
    			continue;
    		
    		// dStart should be the highest other than last bar
    		QuoteData lastLow = Utility.getLow(quotes, dStart+1, lastbar-1);
    		if ( lastLow.low < quotes[dStart].low )
    			continue;
    		if ( lastLow.low < quotes[lastbar].low )
    			continue;  // not the lowest
    		
    		QuoteData pullBackHigh = Utility.getHigh(quotes, dStart+1, lastbar);
    		DiverageDAO dv1 = new DiverageDAO(dStart, lastbar);
    		dv1.pullBackSize = pullBackHigh.high - quotes[dStart].low;
    		dv1.pullBackWidth = lastbar - dStart - 1;
    		if (( dv1.pullBackSize < diverageSize )/* || ( dv1.pullBackWidth > gapMaxWidth )*/)
    			continue;

    		// n bar before dStart should be lower
    		int prevBarsHigh = dv1.pullBackWidth;   // the bar below should be at least the pullback width
    		int nBarHigher = 0;
      		for ( int i = dStart-1; i >=0; i--)
    			if ( quotes[i].low > quotes[dStart].low)
    				nBarHigher++;
    			else
    				break;

      		if (nBarHigher <= prevBarsHigh ) 
      			continue;

    		return dv1;
    	}
    	
    	return dv;
     }
	
	
}
