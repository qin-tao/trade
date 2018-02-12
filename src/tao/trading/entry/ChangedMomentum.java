package tao.trading.entry;

import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.ReturnToMADAO;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;

public class ChangedMomentum {

	public void trackEntryUsingChangedMomentum(QuoteData data, TradeManager2 tradeManager)
	{
		QuoteData[] quotes = tradeManager.getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		
		ReturnToMADAO returnToMA = (ReturnToMADAO)trade.setupDAO;

		int startL = Utility.findPositionByHour(quotes, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			startL = Utility.getLow( quotes, startL-4<0?0:startL-4, startL+4>lastbar?lastbar:startL+4).pos;

			// detect failed momentum
			int lastUpEnd = lastbar -1;
			while (!BarUtil.isUpBar(quotes[lastUpEnd]))
				lastUpEnd--;
			int lastUpBegin = lastUpEnd;
			while (!BarUtil.isUpBar(quotes[lastUpBegin]))
				lastUpBegin--;
	
			if ( data.low < quotes[lastUpBegin].low ){
				double triggerPrice = quotes[lastUpBegin].low;
			
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes[startL].time + " @ " + quotes[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
			
			if ( data.low < quotes[startL].low )
			{
				double triggerPrice = quotes[startL].low;

				double highestPoint = Utility.getHigh( quotes, startL, lastbar).high;
				if (( highestPoint -  quotes[startL].low ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				if ( isFakeLowQuote( triggerPrice )){
					removeTrade();
					return;
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes[startL].time + " @ " + quotes[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
			
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			startL = Utility.getHigh( quotes, startL-4<0?0:startL-4, startL+4>lastbar?lastbar:startL+4).pos;

			// detect failed momentum
			int lastDownEnd = lastbar -1;
			while (!BarUtil.isDownBar(quotes[lastDownEnd]))
				lastDownEnd--;
			int lastDownBegin = lastDownEnd;
			while (!BarUtil.isDownBar(quotes[lastDownBegin]))
				lastDownBegin--;
	
			if ( data.high > quotes[lastDownBegin].high ){
				double triggerPrice = quotes[lastDownBegin].high;
			
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes[startL].time + " @ " + quotes[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
			
			if ( data.high > quotes[startL].high )
			{
				double triggerPrice = quotes[startL].high;

				double lowestPoint = Utility.getLow( quotes, startL, lastbar).low;
				if (( triggerPrice - lowestPoint ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				if ( isFakeHighQuote( triggerPrice )){
					removeTrade();
					return;
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes[startL].time + " @ " + quotes[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
		}
		return;
	}

}
