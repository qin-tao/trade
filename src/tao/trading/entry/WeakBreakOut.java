package tao.trading.entry;

import tao.trading.AlertOption;
import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.util.Utility;

public class WeakBreakOut {

	public void trackPullbackEntryUsingWeakBreakOut(QuoteData data)
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		PushList pushListHHLL = null;
		double triggerPrice;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			// run push setup
			pushListHHLL = PushSetup.findPastHighsByX(quotes60, startL, lastbar60-2, 1);
			if ((pushListHHLL != null ) &&( pushListHHLL.phls != null ) && ( pushListHHLL.phls.length > 0)){
				int pushSize = pushListHHLL.phls.length;
				lastPush = pushListHHLL.phls[pushSize - 1];
				double prevHigh = quotes60[lastPush.prePos].high;
				double breakout = quotes60[lastbar60-2].high - prevHigh;
				
				if (( breakout < (PIP_SIZE * FIXED_STOP / 3)) &&  
				   ( quotes60[lastbar60-1].high < quotes60[lastbar60-2].high)){
					triggerPrice = data.close;
					logger.info(data.time + " " + data.close + " weak breakout entry: prevHigh" + quotes60[lastPush.prePos].time + " " + prevHigh + " breakout dist:" + breakout + " trend start:" + quotes60[startL].time);
					trade.peakPrice = quotes60[lastbar60-2].high + PIP_SIZE;
			    	enterMarketPosition(triggerPrice);
					super.strategy.sentAlertEmail(AlertOption.trigger, trade.symbol + " " + trade.action + " " + triggerPrice + " weak breakout triggered", trade);
					return;
				}
			}

			/*
			PushList pushList = PushSetup.getUp2PushList( quotes60, startL, lastbar60 );
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotes60[lastPush.prePos].high - lastPush.pullBack.low;

				if (( lastPush != null ) && ( data.low < lastPush.pullBack.low ))
				{
					triggerPrice = lastPush.pullBack.low;
					warning("Break higher high detected " + data.time + " " + triggerPrice);
					//TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_HIGHER_HIGH, quotes60[lastbar60].time);
					//trade.addTradeEvent(tv);

					for ( int j = lastPush.pullBack.pos +1; j < lastbar60; j++ ){
						if (quotes60[j].low < triggerPrice){
							removeTrade();
							return;
						}
					}

					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					warning("Breakout higher high triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
			    	enterMarketPosition(triggerPrice);
					return;
				}

			}*/
			
			/*
			if ( data.low < quotes60[startL].low )
			{
				triggerPrice = quotes60[startL].low;
				double highestPoint = Utility.getHigh( quotes60, startL, lastbar60).high;
				if (( highestPoint -  quotes60[startL].low ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				for ( int j = startL +1; j < lastbar60; j++ ){
					if (quotes60[j].low < triggerPrice){
						removeTrade();
						return;
					}
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
		    	enterMarketPosition(triggerPrice);
				//enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				//cancelLimits();
				return;
			}*/
			
			
			// run MA breakout and return setup
			//public static MATouch[] findMATouchUpsFromGoingDown(Object[] quotes, double[] ema, int begin, int end )
			/*
			double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
			MABreakOutList molL = MABreakOutAndTouch.findMABreakOutsDown(quotes60, ema20_60, startL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				//wave2PtL = 1;
			}
			return;*/
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			pushListHHLL = PushSetup.findPastLowsByX(quotes60, startL, lastbar60-2, 1);
			if ((pushListHHLL != null ) &&( pushListHHLL.phls != null ) && ( pushListHHLL.phls.length > 0)){
				int pushSize = pushListHHLL.phls.length;
				lastPush = pushListHHLL.phls[pushSize - 1];
				double prevLow = quotes60[lastPush.prePos].low;
				double breakout = prevLow - quotes60[lastbar60-2].low;
				
				if (( breakout < (PIP_SIZE * FIXED_STOP / 3)) &&   
				   ( quotes60[lastbar60-1].low > quotes60[lastbar60-2].low)){
					triggerPrice = data.close;
					logger.info(data.time + " " + data.close + " weak breakout entry: prevLow" + quotes60[lastPush.prePos].time + " " + prevLow + " breakout dist:" + breakout + " trend start:" + quotes60[startL].time);
					trade.peakPrice = quotes60[lastbar60-2].low - PIP_SIZE;
			    	enterMarketPosition(triggerPrice);
					super.strategy.sentAlertEmail(AlertOption.trigger, strategy.getStrategyName() + " " + strategy.IB_PORT + " " + trade.symbol + " " + trade.action + " " + triggerPrice + " weak breakout triggered", trade);
					return;
				}
			}

			
			/*
			PushList pushList = PushSetup.getDown2PushList(quotes60, startL, lastbar60);
			//if ( pushList != null )
				//warning( trade.action + " EntryPushes:" + pushList.toString());
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];

				if (( lastPush != null ) && ( data.high > lastPush.pullBack.high ))
				{
					triggerPrice = lastPush.pullBack.high;  // data.close
					warning("Break lower low detected " + data.time + " " + triggerPrice);
					//TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_LOWER_LOW, quotes60[lastbar60].time);
					//trade.addTradeEvent(tv);

					for ( int j = lastPush.pullBack.pos +1; j < lastbar60; j++ ){
						if (quotes60[j].high > triggerPrice){
							removeTrade();
							return;
						}
					}

					
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					warning("Breakout lower low buy triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + triggerPrice );
			    	enterMarketPosition(triggerPrice);
			    	//enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					//cancelLimits();

					return;
				}
			}*/
			
			/*
			if ( data.high > quotes60[startL].high )
			{
				triggerPrice = quotes60[startL].high;

				double lowestPoint = Utility.getLow( quotes60, startL, lastbar60).low;
				if (( triggerPrice - lowestPoint ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				for ( int j = startL + 1; j < lastbar60; j++ ){
					if (quotes60[j].low < triggerPrice){
						removeTrade();
						return;
					}
				}

				//warning("Breakout base sell triggered at " + triggerPrice + " " + data.time );
				//enterMarketPosition(triggerPrice);
				//cancelLimits();
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
		    	enterMarketPosition(triggerPrice);

				//enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				//cancelLimits();

				return;
			}*/
		}
		

		return;
	}


}
