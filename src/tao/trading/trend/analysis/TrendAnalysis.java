package tao.trading.trend.analysis;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.Push;
import tao.trading.QuoteData;
import tao.trading.dao.ConsectiveBars;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.dao.ReturnToMADAO;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.util.Utility;

public class TrendAnalysis
{

	
	/*********************************************************************************************************
	 * 
	 * 
	 * 	TREND BREAK SETUPS
	 * 
	 * 
	 * 
	 **********************************************************************************************************/
	static public TrendBreak detectTrendBreak( QuoteData[] quotes240)
	{
		int lastbar240 = quotes240.length - 1;
		double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes240, ema20_240, 1);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes240, ema20_240, 1);

		if (downPos > upPos) 
		{
			int downStart = downPos -1;
			while ( quotes240[downStart].high < ema20_240[downStart])
				downStart--;
			downStart++;	// the last one is not down

			if ( ( downPos - downStart ) > 8 )
				return null;
			
			int upEnd = downStart - 1;
			while ( upEnd > 0 )
			{
				if ( quotes240[upEnd].low > ema20_240[upEnd])
					break;
				else if ( quotes240[upEnd].high < ema20_240[upEnd])
					return null;
				else
					upEnd--;
				
			}
			//while ((upEnd > 0) && !( quotes240[upEnd].low > ema20_240[upEnd]))
			//	upEnd--;
			
			int lastConsectiveUpEnd = Pattern.findLastPriceConsectiveAboveOrAtMA( quotes240, ema20_240, upEnd, 10);
			if ( upEnd != lastConsectiveUpEnd )
				return null;
			
			//System.out.println(symbol + " Big trend reversal DOWN detected at "  + quotes240[lastbar240].time);
			TrendBreak tb = new TrendBreak( Constants.DIRECTION_DOWN, downStart);
			return tb;

		}
		else if (upPos > downPos)
		{
			int upStart = upPos -1;
			while ( quotes240[upStart].low > ema20_240[upStart])
				upStart--;
			upStart++;	// the last one is not up
			
			if ( ( upPos - upStart ) > 8 )
				return null;
			
			int downEnd = upStart - 1;
			while ( downEnd > 0 )
			{
				if ( quotes240[downEnd].high < ema20_240[downEnd])
					break;
				else if ( quotes240[downEnd].low > ema20_240[downEnd])
					return null;
				else
					downEnd--;
			}
			//while ((downEnd > 0) && !( quotes240[downEnd].high < ema20_240[downEnd]))
			//	downEnd--;
			
			int lastConsectiveDownEnd = Pattern.findLastPriceConsectiveBelowOrAtMA( quotes240, ema20_240, downEnd, 10);
			if ( downEnd != lastConsectiveDownEnd )
				return null;
			
			//System.out.println(symbol + " Big trend reversal UP detected at "  + quotes240[lastbar240].time);

			TrendBreak tb = new TrendBreak( Constants.DIRECTION_UP, upStart);
			return tb;
			
		}
		
		return null;
	}
	

	public static TrendBreak getLastTrendBreak( QuoteData[] quotes )
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int lastCrossUp = Pattern.findPriceCross20MAUp(quotes, ema20);
		int lastCrossDown = Pattern.findPriceCross20MAUp(quotes, ema20);

		if ( lastCrossUp > lastCrossDown )
		{
			TrendBreak tb = new TrendBreak(Constants.DIRECTION_UP, lastCrossUp);
			return tb;

		}
		else if ( lastCrossDown > lastCrossUp )
		{
			TrendBreak tb = new TrendBreak(Constants.DIRECTION_DOWN, lastCrossDown);
			return tb;
		}
		
		return null;
		
		
	}
	
	
	
	/*********************************************************************************************************
	 * 
	 * 
	 * 	DETERMIN BIG TRENDS
	 * 
	 * 
	 * 
	 **********************************************************************************************************/

	
	
/*	
	
	// assuming we detected a trend up pullback
	void analysisBigPushTrendUp( QuoteData[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2);
		if ( lastDownPos == Constants.NOT_FOUND )
			lastDownPos = 0;
		
		MATouch[] maTouches = Pattern.findNextMATouchDownsFromGoingUps(quotes, ema20, lastDownPos, int gap )

		
			if ( lastUpPos > lastDownPos)
			{
				if ((lastUpPos == (lastbar240 - 1)) &&  (quotes240[lastbar240].low < ema20_240[lastbar240]) && ( quotes240[lastbar240 - 1].low > ema20_240[lastbar240 - 1]))
				{
					BigTrend btt = calculateTrend( quotes240);
					if (!Constants.DIRT_UP.equals(btt.direction))
						return null;;
					
					if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
						return null;

					int prevTouchPos = lastUpPos - 5;
					while ( quotes240[prevTouchPos].low > ema20_240[prevTouchPos])
						prevTouchPos--;
					
					QuoteData prevHigh = Utility.getHigh( quotes240, prevTouchPos, lastUpPos);
					
					int pushStart = Utility.findPositionByHour(quotes60, prevHigh.time, 1 );
					QuoteData prevHigh60 = Utility.getHigh( quotes60, pushStart-4, pushStart+4);

					if ( findTradeHistory( Constants.ACTION_BUY, prevHigh60.time) != null )
						return null;

					System.out.println(symbol + " pullback buy detected at " + quotes240[lastbar240].time + " start:" + prevHigh.time);
					createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.setFirstBreakOutStartTime(prevHigh60.time);

					return trade;

				}
			}
			
			int wave2PtL = 0;
			int first2above = Pattern.findNextPriceConsectiveAboveMA(quotesL, ema20L, tradeEntryPosL, 2);
			int first2below = Pattern.findNextPriceConsectiveBelowMA(quotesL, ema20L, tradeEntryPosL, 2);
			if (( first2above < first2below ) && ( first2above > 0 ))
			{
				wave2PtL = -1;
			}
			else
			{	
				maTouches = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
				if ( !trade.type.equals(Constants.TRADE_CNT))
				{
					if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
					{
						wave2PtL =  maTouches[0].touchBegin;
					}
				}
				else 
				{
					if ( maTouches.length > 1 )
					{
						if ( maTouches[1].touchBegin != 0 )
						{
							wave2PtL =  maTouches[1].touchBegin;
						}
					}
					else if ( maTouches.length > 0 ) 
					{
						if (( maTouches[0].lowEnd != 0 ) && ((  maTouches[0].lowEnd -  maTouches[0].lowBegin) >= 12))
						{
							wave2PtL =  maTouches[0].lowEnd + 1;
						}
					}
				}
			}

	}*/

	
	
	public static BigTrend determineBigTrend2050_2( QuoteData[] quotes )
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		
		BigTrend bigTrend = new BigTrend();
		bigTrend.dir = Constants.DIRECTION_UNKNOWN;
		int trendQualifyPeriodMin = 48;
		
		/*
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2);
	
		int trendStartPos = -1;
		if ( lastUpPos > lastDownPos)
		{
			bigTrend.direction = Constants.DIRT_UP;
			
			// a trend is 6 consective up on the other side or 12 consecitve up or on the other side
			trendStartPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 6);
	
			if ( trendStartPos == Constants.NOT_FOUND )
				trendStartPos = Pattern.findLastPriceConsectiveBelowOrAtMA(quotes, ema20, lastbar, 12);
			

		}
		else if ( lastDownPos > lastUpPos)
		{
			bigTrend.direction = Constants.DIRT_DOWN;
			
			// a trend is 6 consective up on the other side or 12 consecitve up or on the other side
			trendStartPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 6);
	
			//if ( trendStartPos == Constants.NOT_FOUND )
				trendStartPos = Pattern.findLastPriceConsectiveAboveOrAtMA(quotes, ema20, lastbar, 12);
		}*/
		
		int start = 0;
		int QUOTE_START_POS = 20;
		
		// calculate the ups and downs
		int end = lastbar;
		while ( end >= QUOTE_START_POS )
		{
			if (( ema20[end] > ema50[end]) && ( ema20[end-1] > ema50[end-1])){
				start = end-2;
				while ( (start >= QUOTE_START_POS) && !(( ema20[start] < ema50[start]) && ( ema20[start-1] < ema50[start-1]))){
					start--;
				}
				Push p = new Push ( Constants.DIRECTION_UP, start+1, end);
				bigTrend.pushes.insertElementAt(p,0);
				end = start;
			}
			else if (( ema20[end] < ema50[end]) && ( ema20[end-1] < ema50[end-1])){
				start = end-2;
				while ( (start >= QUOTE_START_POS) && !(( ema20[start] > ema50[start]) && ( ema20[start-1] > ema50[start-1]))){
					start--;
				}
				Push p = new Push ( Constants.DIRECTION_DOWN, start+1, end);
				bigTrend.pushes.insertElementAt(p,0);
				end = start;
			}
			else
				end--;
		}
		
		
		int pushSize = bigTrend.pushes.size();
		if ( pushSize < 1 ){
			bigTrend.direction = Constants.DIRT_UNKNOWN;
			return bigTrend;
		}
		
		Push lastPush =  bigTrend.pushes.lastElement();
		int lastPushInd = bigTrend.pushes.size()-1;
		
		// Rule 1:  find the last push that > trendQualifyPeriod
		if (lastPush.duration >= trendQualifyPeriodMin ){
			bigTrend.lastTrendyPushIndex = pushSize-1;
			bigTrend.dir = lastPush.direction;
			System.out.println("Direction follow last push");
			return bigTrend;
		}
		// if the last push is < trendQualifyPeriod, then if the previous one is > trendQualifyPeriod, the direction is the pevious one
		else if (( pushSize >= 2 ) && ( bigTrend.pushes.elementAt(pushSize-2).duration >= trendQualifyPeriodMin ))
		{
			bigTrend.lastTrendyPushIndex = pushSize-2;
			bigTrend.dir = bigTrend.pushes.elementAt(pushSize-2).direction;
			System.out.println("Direction follow last-2 push");
			return bigTrend;
		}
			

		// Rule 2:  if a first push followed by a diverge, and then the second push is significantly less than the first push, then the trend is about to change
	/*	if ((lastPush.duration < trendQualifyPeriodMin ) && ( pushSize >= 4 ))
		{
			if (( bigTrend.pushes.elementAt(lastPushInd-1).duration < bigTrend.pushes.elementAt(lastPushInd-3).duration * 0.8 )
					&& ( bigTrend.pushes.elementAt(lastPushInd-3).duration > 1.2 * trendQualifyPeriodMin ))
			{
				if (bigTrend.pushes.elementAt(lastPushInd-1).direction == Constants.DIRECTION_UP )
				{
					bigTrend.direction = Constants.DIRT_DOWN_SEC_2;
				}
				else 
				{
					bigTrend.direction = Constants.DIRT_UP_SEC_2;
				}
			}
			
		}*/

		return bigTrend;
	}

	
	
	
	
	public static BigTrend determineBigTrend2050( QuoteData[] quotes )
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		
		BigTrend bigTrend = new BigTrend();
		bigTrend.direction = Constants.DIRT_UNKNOWN;
		int trendQualifyPeriodMin = 48;
		
		/*
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2);
	
		int trendStartPos = -1;
		if ( lastUpPos > lastDownPos)
		{
			bigTrend.direction = Constants.DIRT_UP;
			
			// a trend is 6 consective up on the other side or 12 consecitve up or on the other side
			trendStartPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 6);
	
			if ( trendStartPos == Constants.NOT_FOUND )
				trendStartPos = Pattern.findLastPriceConsectiveBelowOrAtMA(quotes, ema20, lastbar, 12);
			

		}
		else if ( lastDownPos > lastUpPos)
		{
			bigTrend.direction = Constants.DIRT_DOWN;
			
			// a trend is 6 consective up on the other side or 12 consecitve up or on the other side
			trendStartPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 6);
	
			//if ( trendStartPos == Constants.NOT_FOUND )
				trendStartPos = Pattern.findLastPriceConsectiveAboveOrAtMA(quotes, ema20, lastbar, 12);
		}*/
		
		int start = 0;
		int QUOTE_START_POS = 20;
		
		// calculate the ups and downs
		int end = lastbar;
		while ( end >= QUOTE_START_POS )
		{
			if (( ema20[end] > ema50[end]) && ( ema20[end-1] > ema50[end-1])){
				start = end-2;
				while ( (start >= QUOTE_START_POS) && !(( ema20[start] < ema50[start]) && ( ema20[start-1] < ema50[start-1]))){
					start--;
				}
				Push p = new Push ( Constants.DIRECTION_UP, start+1, end);
				bigTrend.pushes.insertElementAt(p,0);
				end = start;
			}
			else if (( ema20[end] < ema50[end]) && ( ema20[end-1] < ema50[end-1])){
				start = end-2;
				while ( (start >= QUOTE_START_POS) && !(( ema20[start] > ema50[start]) && ( ema20[start-1] > ema50[start-1]))){
					start--;
				}
				Push p = new Push ( Constants.DIRECTION_DOWN, start+1, end);
				bigTrend.pushes.insertElementAt(p,0);
				end = start;
			}
			else
				end--;
		}
		
		
		int pushSize = bigTrend.pushes.size();
		if ( pushSize < 1 ){
			bigTrend.direction = Constants.DIRT_UNKNOWN;
			return bigTrend;
		}
		
		Push lastPush =  bigTrend.pushes.lastElement();
		int lastPushInd = bigTrend.pushes.size()-1;
		
		// Rule 1:  find the last push that > trendQualifyPeriod
		if (lastPush.duration >= trendQualifyPeriodMin )
		{
			bigTrend.lastTrendyPushIndex = pushSize-1;
			if ( lastPush.direction == Constants.DIRECTION_UP )
			{
				bigTrend.direction = Constants.DIRT_UP;
				
				/*if ( lastPushInd > 1 )
					bigTrend.resistance = Utility.getHigh( quotes,bigTrend.pushes.elementAt(lastPushInd-1).pushStart, bigTrend.pushes.elementAt(lastPushInd).pushEnd);
				else
					bigTrend.resistance = Utility.getHigh( quotes,0, bigTrend.pushes.elementAt(lastPushInd).pushEnd);*/
			}
			else 
			{
				bigTrend.direction = Constants.DIRT_DOWN;
				
				/*if ( lastPushInd > 1 )
					bigTrend.support = Utility.getLow( quotes,bigTrend.pushes.elementAt(lastPushInd-1).pushStart, bigTrend.pushes.elementAt(lastPushInd).pushEnd);
				else
					bigTrend.support = Utility.getLow( quotes,0, bigTrend.pushes.elementAt(lastPushInd).pushEnd);*/
			}
			
		}
		// if the last push is < trendQualifyPeriod, then if the previous one is > trendQualifyPeriod, the direction is the pevious one
		else if (( pushSize >= 2 ) && ( bigTrend.pushes.elementAt(pushSize-2).duration >= trendQualifyPeriodMin ))
		{
			bigTrend.lastTrendyPushIndex = pushSize-2;
			if ( bigTrend.pushes.elementAt(pushSize-2).direction == Constants.DIRECTION_UP )
			{
				bigTrend.direction = Constants.DIRT_UP;
				//bigTrend.resistance = Utility.getHigh( quotes,bigTrend.pushes.elementAt(pushSize-2).pushStart, lastbar-1);
				
				// if there is a discrption on the price, trend might change
				MATouch lastTouch = MABreakOutAndTouch.findLastMATouchFromAbove(quotes, ema50, lastbar);
				//MATouch lastTouch = Pattern.findLastMATouchFromAbove(quotes, ema20, bigTrend.pushes.elementAt(pushSize-2).pushEnd-1);
				if (( lastTouch != null ) && (  lastTouch.touchEnd < lastbar ))
				{
					QuoteData latestLow = Utility.getLow( quotes, lastTouch.touchEnd+1, lastbar);
				/*	comment out for FIXED_STOP NOT available
				 * if ( latestLow.low < ( lastTouch.low.low - FIXED_STOP * PIP_SIZE) )
					{
						bigTrend.direction = Constants.DIRT_DOWN_REVERSAL;
						return bigTrend;
					}*/ 
				}
				
			}
			else if ( bigTrend.pushes.elementAt(pushSize-2).direction == Constants.DIRECTION_DOWN )
			{
				bigTrend.direction = Constants.DIRT_DOWN;
				//bigTrend.support = Utility.getLow( quotes,bigTrend.pushes.elementAt(pushSize-2).pushStart, lastbar-1);

				MATouch lastTouch = MABreakOutAndTouch.findLastMATouchFromBelow(quotes, ema50, lastbar);
				//MATouch lastTouch = Pattern.findLastMATouchFromBelow(quotes, ema20, bigTrend.pushes.elementAt(pushSize-2).pushEnd-1);
				if (( lastTouch != null ) && (  lastTouch.touchEnd < lastbar ))
				{
					QuoteData latestHigh = Utility.getHigh( quotes, lastTouch.touchEnd+1, lastbar);
					/*
					 * comment out for FIXED_STOP not available
					if ( latestHigh.high > ( lastTouch.high.high + FIXED_STOP * PIP_SIZE) )
					{
						bigTrend.direction = Constants.DIRT_UP_REVERSAL;
						return bigTrend;
					}*/
				}
			}
		}
		// if the last two all < trendQualifyPeriod, and the last third one is > trendQualifyPeriod, then the direction is the last third, assuming it will continue
		/*  // this might be too complicated to calculate
		else if (( pushSize >= 3 ) && ( bigTrend.pushes.elementAt(pushSize-3).duration >= trendQualifyPeriod ))
		{
			// we assume this is the resume of the trend although the last push has not reqch the trend qualifier
			if ( bigTrend.pushes.elementAt(pushSize-3).direction == Constants.DIRECTION_UP )
			{
				bigTrend.direction = Constants.DIRT_UP;
				bigTrend.resistance = Utility.getHigh( quotes,bigTrend.pushes.elementAt(pushSize-3).pushStart, lastbar-1);
			}
			else 
			{
				bigTrend.direction = Constants.DIRT_DOWN;
				bigTrend.support = Utility.getLow( quotes,bigTrend.pushes.elementAt(pushSize-3).pushStart, lastbar-1);
			}
			
		}*/
			
			

		// Rule 2:  if a first push followed by a diverge, and then the second push is significantly less than the first push, then the trend is about to change
		if ((lastPush.duration < trendQualifyPeriodMin ) && ( pushSize >= 4 ))
		{
			if (( bigTrend.pushes.elementAt(lastPushInd-1).duration < bigTrend.pushes.elementAt(lastPushInd-3).duration * 0.8 )
					&& ( bigTrend.pushes.elementAt(lastPushInd-3).duration > 1.2 * trendQualifyPeriodMin ))
			{
				if (bigTrend.pushes.elementAt(lastPushInd-1).direction == Constants.DIRECTION_UP )
				{
					bigTrend.direction = Constants.DIRT_DOWN_SEC_2;
				}
				else 
				{
					bigTrend.direction = Constants.DIRT_UP_SEC_2;
				}
			}
			
		}


		
		
		return bigTrend;
	}



	
	
	public static int determineBigTrend_3( QuoteData[] quotes )
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		
		BigTrend bigTrend = new BigTrend();
		bigTrend.direction = Constants.DIRT_UNKNOWN;
		int trendQualifyPeriodMin = 36;
		int trendQualifyPeriod = 128;
		
		int start = 0;
		
		// calculate the wave
		int end = lastbar;
		while ( end >= 20 )
		{
			if (( ema20[end] > ema50[end]) && ( ema20[end-1] > ema50[end-1]))
			{
				start = end-2;
				while ( (start >= 20) && !(( ema20[start] < ema50[start]) && ( ema20[start-1] < ema50[start-1])))
				{
					start--;
				}
				
				Push p = new Push ( Constants.TREND_UP, start+1, end);
				bigTrend.pushes.insertElementAt(p,0);
				end = start;

			}
			else if (( ema20[end] < ema50[end]) && ( ema20[end-1] < ema50[end-1]))
			{
				start = end-2;
				while ( (start >= 20) && !(( ema20[start] > ema50[start]) && ( ema20[start-1] > ema50[start-1])))
				{
					start--;
				}
				
				Push p = new Push ( Constants.TREND_DOWN, start+1, end);
				bigTrend.pushes.insertElementAt(p,0);
				end = start;
				
			}
			else
				end--;
		}
		
		
		int pushSize = bigTrend.pushes.size();
		if ( pushSize < 1 )
			return Constants.TREND_UNKNOWN;
			
		
		Push lastPush =  bigTrend.pushes.lastElement();
		int lastPushInd = bigTrend.pushes.size()-1;
		
		if ( lastPush.duration > trendQualifyPeriod ){
			return lastPush.direction;
		}else{
			if ( pushSize > 1 ){
				Push pushBefore = bigTrend.pushes.elementAt(pushSize-1-1);
				if ( pushBefore.duration > trendQualifyPeriod){
					return pushBefore.direction;
				}else if ( pushBefore.duration < trendQualifyPeriodMin){
					if ( pushSize > 2 ){
						Push pushBeforeBefore = bigTrend.pushes.elementAt(pushSize-1-1-1);
						if ( pushBeforeBefore.duration > trendQualifyPeriod){
							return pushBeforeBefore.direction;
						}
					}
				}
			}
		}
		
		
		return Constants.TREND_UNKNOWN;
	}


	
	/*********************************************************************************************************
	 * 
	 * 
	 * 	MOMENTUM CHANGE SETUPS
	 * 
	 * 
	 * 
	 **********************************************************************************************************/

	
	/*
	 * 
	 * this is to detect after it reaches a new high/low, it has a large pull back, and has the potential of reversal
	 * 
	 * 
	 */
	public static int detectLatestMomentumChange2( QuoteData[] quotes, double minDist )
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		int lastDownPos = Pattern.findLastPriceConsectiveBelowOrAtMA(quotes, ema20, lastbar, 6);
		int lastUpPos = Pattern.findLastPriceConsectiveAboveOrAtMA(quotes, ema20, lastbar, 6);
		
		if (( lastUpPos > lastDownPos ) && ( Utility.isUpBar(quotes[lastbar-1])))
		{
			if ( lastDownPos == Constants.NOT_FOUND )
				lastDownPos = 0;
			
			int highestPoint = Utility.getHigh( quotes, lastDownPos, lastbar-1).pos;
		
			int firstDown = highestPoint;
			if (!Utility.isDownBar(quotes[firstDown]))
			{
				if ((Utility.isDownBar(quotes[firstDown+1])))
					firstDown = firstDown+1;
				else
					return Constants.DIRECTION_UNKNOWN;
			}
			
			//System.out.println("11");
			int downBarEnd = firstDown+1;
			while ((downBarEnd < lastbar)  && Utility.isDownBar(quotes[downBarEnd]))
				downBarEnd++;
			
			//System.out.println("downBar=" + downBarEnd + " lastbar" + (lastbar-1));
			if ( downBarEnd != lastbar-1 )
				return Constants.DIRECTION_UNKNOWN;
			
			double pullbackSize = quotes[firstDown].high - quotes[downBarEnd-1].low;
			
			//System.out.println(pullbackSize);
			
			if ( pullbackSize > minDist )
			{
				return Constants.DIRECTION_UP;
			}
		}
		else if (( lastDownPos > lastUpPos ) && ( Utility.isDownBar(quotes[lastbar-1])))
		{
			if ( lastUpPos == Constants.NOT_FOUND )
				lastUpPos = 0;
			
			int lowestPoint = Utility.getLow( quotes, lastUpPos, lastbar-1).pos;
		
			int firstUp = lowestPoint;
			if (!Utility.isUpBar(quotes[firstUp]))
			{
				if ((Utility.isUpBar(quotes[firstUp+1])))
					firstUp = firstUp+1;
				else
					return Constants.DIRECTION_UNKNOWN;
			}
			
			//System.out.println("11");
			int upBarEnd = firstUp+1;
			while ((upBarEnd < lastbar)  && Utility.isUpBar(quotes[upBarEnd]))
				upBarEnd++;
			
			//System.out.println("downBar=" + downBarEnd + " lastbar" + (lastbar-1));
			if ( upBarEnd != lastbar-1 )
				return Constants.DIRECTION_UNKNOWN;
			
			double pullbackSize = quotes[upBarEnd-1].high - quotes[firstUp].low;
			
			//System.out.println(pullbackSize);
			
			if ( pullbackSize > minDist )
			{
				return Constants.DIRECTION_DOWN;
			}
		}
		
		return Constants.DIRECTION_UNKNOWN;
	}



	
	
	/*
	 * 
	 * this is to detect after it reaches a new high/low, it has a large pull back, and has the potential of reversal
	 * 
	 * 
	 */
	public static Push detectLatestMomentumChange3( QuoteData[] quotes, double minDist )
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		int prevDowns = Pattern.findLastPriceConsectiveBelowOrAtMA(quotes, ema20, lastbar, 2);
		int prevUps = Pattern.findLastPriceConsectiveAboveOrAtMA(quotes, ema20, lastbar, 2);
		
		if ( prevUps > prevDowns ) 
		{
			int lastDownBar = lastbar;
			if (!Utility.isDownBar( quotes[lastDownBar]))
			{
				lastDownBar--;
				if (!Utility.isDownBar( quotes[lastDownBar]))
					return null;
			}

			if ( prevDowns == Constants.NOT_FOUND )
				prevDowns = 0;
			int highestPoint = Utility.getHigh( quotes, prevDowns, lastDownBar).pos;
			if ( highestPoint == Constants.NOT_FOUND )
				highestPoint = 0;
			//System.out.println(quotes + " " + highestPoint + " " + (lastbar-2));
			//	if ( highestPoint > lastbar-2 )
			//		return null;
				//int lowestPoint = Utility.getLow( quotes, highestPoint, lastbar-2).pos;
				//if ( lowestPoint != lastbar-2 )	
				//	return Constants.DIRECTION_UNKNOWN;
				
			int firstDownBar = lastDownBar;
			while ((firstDownBar >= highestPoint )  && Utility.isDownBar(quotes[firstDownBar-1]))
				firstDownBar--;
			
			double pullbackSize = quotes[firstDownBar].open - quotes[lastDownBar].close;
			
			//System.out.println(pullbackSize);
			
			if ( pullbackSize > minDist )
			{
				//System.out.println("downBar detected:" + quotes[highestPoint].time + " " + quotes[firstDownBar].time + " " + quotes[lastDownBar].time );
				Push p = new Push(	Constants.DIRECTION_DOWN, firstDownBar, lastDownBar);
				p.peakPos = highestPoint;
				return p;
			}
		}
		
		return null;
	}


	
	
	
	
	public static PushList detectNewHigherHighLowerLow( QuoteData[] quotes, int lastbar )
	{
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		if ( Utility.isUpBar(quotes[lastbar]) )  // last bar green
		{
			int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2);
			if ( lastDownPos == Constants.NOT_FOUND )
				return null;
			
			int downStart = lastDownPos;
			while (( quotes[downStart].high < ema20[downStart]) && ( downStart > 0 ))
				downStart--;
			downStart = Utility.getLow( quotes, downStart, lastDownPos).pos;
			
			PushList pushList = PushSetup.getUp1PushList(quotes, downStart, lastbar);
			
			if (( pushList == null ) || (pushList.phls == null) || ( pushList.phls.length > 1 ))
				return null;

			PushHighLow phl = pushList.phls[0];
			int count = 0;
			for ( int j = phl.prePos + 1; j <= lastbar; j++)
			if (( quotes[j].high > quotes[phl.prePos].high ) )
				count ++;
			
			if ( count > 1 )
				return null; // already passed
			else
				return pushList;
		}
		else if ( Utility.isDownBar(quotes[lastbar]) )  // last bar green
		{
			int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 2);
			if ( lastUpPos == Constants.NOT_FOUND )
				return null;
			
			int upStart = lastUpPos;
			while (( quotes[upStart].low > ema20[upStart]) && ( upStart >0 ))
				upStart--;
			upStart = Utility.getHigh( quotes, upStart, lastUpPos).pos;
			
			PushList pushList = PushSetup.getDown1PushList(quotes, upStart, lastbar);
			
			if (( pushList == null ) || (pushList.phls == null) || ( pushList.phls.length > 1 ))
				return null;

			PushHighLow phl = pushList.phls[0];
			int count = 0;
			for ( int j = phl.prePos + 1; j <= lastbar; j++)
			if (( quotes[j].low < quotes[phl.prePos].low ) )
				count ++;
			
			if ( count > 1 )
				return null; // already passed
			else
				return pushList;
		}
		
		return null;
			
	}


	
	public static Push detectLatestMomentum( QuoteData[] quotes, int start, int end, int direction, double minMomentumSize )
	{
		// find the momentum
		if ( direction == Constants.DIRECTION_DOWN )
		{
			int mEnd = end;
			while (!Utility.isDownBar( quotes[mEnd]))
				mEnd--;
			int mBegin = mEnd - 1;
			while (Utility.isDownBar( quotes[mBegin]))
				mBegin--;
			
			mBegin = mBegin + 1;
			
			if ( mBegin >= start )
			{
				double mSize = quotes[mBegin].open - quotes[mEnd].close; 
				
				if ( mSize < minMomentumSize )
					return null;
				else
				{
					Push p = new Push(	Constants.DIRECTION_DOWN, mBegin, mEnd);
					return p;
				}
			}
		}
		else if ( direction == Constants.DIRECTION_UP )
		{
			int mEnd = end;
			while (!Utility.isUpBar( quotes[mEnd]))
				mEnd--;
			int mBegin = mEnd - 1;
			while (Utility.isUpBar( quotes[mBegin]))
				mBegin--;
			
			mBegin = mBegin + 1;
			
			if ( mBegin >= start )
			{
				double mSize = quotes[mEnd].close - quotes[mBegin].open; 
				
				if ( mSize < minMomentumSize )
					return null;
				else
				{
					Push p = new Push(	Constants.DIRECTION_UP, mBegin, mEnd);
					return p;
				}
			}
		}

		/*
		if ( Utility.isUpBar( quotes[end-2] ))
		{
			int mBegin = end-3;
			int mEnd = end-2;
			while ( Utility.isUpBar( quotes[mBegin]))
				mBegin--;
			mBegin = mBegin + 1;
			
			double mSize = quotes[mEnd].close - quotes[mBegin].open;
			
			if ( mSize < minMomentumSize )
				return null;
			
			if ( Utility.isDownBar(quotes[end-1]) || (quotes[end-1].high < quotes[end-2].high)) 
			{
				Push p = new Push(	Constants.DIRECTION_UP, mBegin, mEnd);
				return p;
			}
		}
		else if ( Utility.isDownBar( quotes[end-2] ))
		{
			
		}*/
		
		return null;
			
	}

	
	
	

	
	public static BigTrend checkBigTrend( QuoteData[] quotes )
	{
		// this is to be expected on 240 min chart
		int lastbar = quotes.length - 1;
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		
		BigTrend bigTrend = new BigTrend(Constants.DIRECTION_UNKNOWN, 0);
		
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema50, lastbar, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema50, lastbar, 2);
	
		int trendStartPos = -1;
		if ( lastUpPos > lastDownPos)
		{
			bigTrend = new BigTrend(Constants.DIRECTION_UP, 1);
		}
		else if ( lastDownPos > lastUpPos)
		{
			bigTrend = new BigTrend(Constants.DIRECTION_DOWN, 1);
		}
		
		return bigTrend;
	}


	
	
}
