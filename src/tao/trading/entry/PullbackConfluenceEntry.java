package tao.trading.entry;

import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.TradePosition;
import tao.trading.dao.MABreakOutList;
import tao.trading.dao.MomentumDAO;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.dao.ReturnToMADAO;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.MomentumDetector;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.tm.TradeManagerInf;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;

public class PullbackConfluenceEntry {

	TradeManager2 tradeManager;
	Logger logger = Logger.getLogger("SFTest");
	int FIXED_STOP, POSITION_SIZE;
	double PIP_SIZE;
	
	
	public PullbackConfluenceEntry(TradeManager2 tradeManager) {
		super();
		this.tradeManager = tradeManager;
		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
	}

	public void entry_manage( QuoteData data, TradeManager2 tradeManager )
	{
		//entry_peak( data, tradeManager);
		//follow_up_bar( data, tradeManager);
		//entry_break_LL_HH(data,tradeManager);
		//track_PBK_entry_using_momentum2(data,tradeManager);
		//track_PBK_entry_using_momentum3(data,tradeManager);  //what works
		//track_PBK_entry_using_momentum4(data,tradeManager);  
		//track_PBK_entry_using_momentum5(data,tradeManager);
		//track_PBK_entry_using_20ma(data,tradeManager);
		track_PBK_entry_using_everything(data,tradeManager);
	}
	
	
	public void entry_peak( QuoteData data, TradeManagerInf tradeManager )
	{
		QuoteData[] quotes60 = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();

		Trade trade = tradeManager.getTrade();
		
		int pullBackStartL = 0;
		if (( trade.setupDAO != null ) && ( trade.setupDAO instanceof ReturnToMADAO )){
			ReturnToMADAO dao = (ReturnToMADAO)trade.setupDAO;
			pullBackStartL = dao.getLowestHighestPos();
		}
			
		if ( pullBackStartL == 0 )
			return;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			PushList pushList = PushSetup.getUp2PushList( quotes60, startL, lastbar60 );
			//if ( pushList != null )
				//logger.warning( trade.action + " EntryPushes:" + pushList.toString());
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				// this is the highest
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotes60[lastPush.prePos].high - lastPush.pullBack.low;
				
				if ( lastbar60 == pushList.end )
				{	
					int pullBackSize = (int)(lastPush.pullBackSize/PIP_SIZE);

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH, quotes60[lastbar60].time);
					tv.addNameValue("High Price", new Double(quotes60[lastPush.prePos].high).toString());
					tv.addNameValue("NumPush", new Integer(pushSize).toString());
					tv.addNameValue("PullBackSize", new Integer(pullBackSize).toString());
					if (!trade.findTradeEvent(tv)){
						trade.addTradeEvent(tv);
						
						if (lastPush.pullBackSize > 2 * FIXED_STOP * PIP_SIZE)
						{
							double triggerPrice = tradeManager.adjustPrice(quotes60[lastPush.prePos].high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							//logger.warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
							
							/*
							if ( !trade.isLimitAlreadyExist( triggerPrice ) )
							{
								logger.warning("Peak trade sell triggered on large pull back  @ " + quotes60[lastbar60].time + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.curPos].time );
								tradeManager.enterLimitPositionMulti( POSITION_SIZE, triggerPrice );
							}*/
							
							//tradeManager.enterMarketPositionMulti( triggerPrice, POSITION_SIZE );

						}
					}
				}

				if (( lastPush != null ) && ( data.low < lastPush.pullBack.low ))
				{
					logger.warning("Break higher high detected");
					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_HIGHER_HIGH, quotes60[lastbar60].time);
					trade.addTradeEvent(tv);

					double triggerPrice = lastPush.pullBack.low;// data.close
					if ( isFakeLowQuote( triggerPrice ))
					{
						tradeManager.removeTrade();
						return;
					}

					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					logger.warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
					tradeManager.enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					tradeManager.cancelLimits();
					return;
				}

			}
			
			
			if ( data.low < quotes60[startL].low )
			{
				double triggerPrice = quotes60[startL].low;

				double highestPoint = Utility.getHigh( quotes60, startL, lastbar60).high;
				if (( highestPoint -  quotes60[startL].low ) > 2 * FIXED_STOP * PIP_SIZE){
					tradeManager.removeTrade();
					return;
				}

				if ( isFakeLowQuote( triggerPrice ))
				{
					tradeManager.removeTrade();
					return;
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				logger.warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
				tradeManager.enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				tradeManager.cancelLimits();
				return;
			}
			
			
			// run MA breakout and return setup
			//public static MATouch[] findMATouchUpsFromGoingDown(Object[] quotes, double[] ema, int begin, int end )
			double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
			MABreakOutList molL = MABreakOutAndTouch.findMABreakOutsDown(quotes60, ema20_60, startL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				//wave2PtL = 1;
			}
			return;

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			PushList pushList = PushSetup.getDown2PushList(quotes60, startL, lastbar60);
			//if ( pushList != null )
				//logger.warning( trade.action + " EntryPushes:" + pushList.toString());
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];

				if ( lastbar60 == pushList.end )
				{	
					int pullBackSize = (int)(lastPush.pullBackSize/PIP_SIZE);

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_LOW, quotes60[lastbar60].time);
					tv.addNameValue("Low Price", new Double(quotes60[lastPush.prePos].low).toString());
					tv.addNameValue("NumPush", new Integer(pushSize).toString());
					tv.addNameValue("PullBackSize", new Integer(pullBackSize).toString());
					
					if (!trade.findTradeEvent(tv)){
						trade.addTradeEvent(tv);
	
						if (lastPush.pullBackSize > 2 * FIXED_STOP * PIP_SIZE)
						{
							double triggerPrice = tradeManager.adjustPrice(quotes60[lastPush.prePos].low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							//logger.warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
							
							/*if ( !trade.isLimitAlreadyExist( triggerPrice ))
							{
								logger.warning("Peak trade buy triggered on large pull back  @ " + quotes60[lastbar60].time + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.curPos].time );
								tradeManager.enterLimitPositionMulti( POSITION_SIZE, triggerPrice );
							}*/
							//tradeManager.enterMarketPositionMulti( triggerPrice, POSITION_SIZE );
						}
					}
				}

				if (( lastPush != null ) && ( data.high > lastPush.pullBack.high )) {
					logger.warning("Break lower low detected");
					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_LOWER_LOW, quotes60[lastbar60].time);
					trade.addTradeEvent(tv);

					double triggerPrice = lastPush.pullBack.high;  // data.close
					if ( isFakeHighQuote( triggerPrice ))
					{
						tradeManager.removeTrade();
						return;
					}

					//logger.warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					//enterMarketPosition(triggerPrice);
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					
					logger.warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.high );
					tradeManager.enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					tradeManager.cancelLimits();

					return;
				}
			}
			
			if ( data.high > quotes60[startL].high )
			{
				double triggerPrice = quotes60[startL].high;

				double lowestPoint = Utility.getLow( quotes60, startL, lastbar60).low;
				if (( triggerPrice - lowestPoint ) > 2 * FIXED_STOP * PIP_SIZE){
					tradeManager.removeTrade();
					return;
				}

				if ( isFakeHighQuote( triggerPrice ))
				{
					tradeManager.removeTrade();
					return;
				}

				//logger.warning("Breakout base sell triggered at " + triggerPrice + " " + data.time );
				//enterMarketPosition(triggerPrice);
				//cancelLimits();
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				logger.warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
				tradeManager.enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				tradeManager.cancelLimits();

				return;
			}

			
		}
		

		return;
	}


	private boolean isFakeLowQuote( double triggerPrice )
	{
		QuoteData[] quotes = tradeManager.getQuoteData(Constants.CHART_1_MIN);
		int lastbar = quotes.length - 1;
		if (/*( realtime_count < 3 ) ||*/ (quotes[lastbar-1].low < triggerPrice))
			return true;
		return false;
	}

	private boolean isFakeHighQuote( double triggerPrice )
	{
		QuoteData[] quotes = tradeManager.getQuoteData(Constants.CHART_1_MIN);
		int lastbar = quotes.length - 1;
		if (/*( realtime_count < 3 ) ||*/ (quotes[lastbar-1].high > triggerPrice))
			return true;
		return false;
	}

	
	public void follow_up_bar( QuoteData data, TradeManagerInf tradeManager )
	{
		QuoteData[] quotes60 = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;

		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		Trade trade = tradeManager.getTrade();
		
		int detectPos = Utility.findPositionByHour(quotes60, trade.detectTime, Constants.BACK_TO_FRONT );

		//tradeManager.cancelStop();
		if ( trade.stopId != 0 )
			trade.stopId = 0;
		
//		if ( !trade.incrementalComplete){
		if (Constants.ACTION_SELL.equals(trade.action)){
			if ((lastbar60-1 > detectPos) && BarUtil.isDownBar(quotes60[lastbar60-1]) && (data.low < quotes60[lastbar60-1].low)){ 
				
				double entryPrice = quotes60[lastbar60-1].low;
				TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_LOW, quotes60[lastbar60].time);
				tv.addNameValue("Low Price", new Double(entryPrice).toString());
				if (!trade.findTradeEvent(tv)){
					trade.addTradeEvent(tv);

					TradePosition[] positions = trade.getFilledPositions();
					int posSize = positions.length;
					if (( posSize > 0 ) ){
						TradePosition lastTP = positions[posSize-1];
						int lastTPPos = Utility.findPositionByHour(quotes60, lastTP.position_time, Constants.BACK_TO_FRONT );
						if ((entryPrice > lastTP.price ) && (lastbar60 > lastTPPos)){
						/*if ((lastbar60 > lastTPPos) && 
							(((posSize == 1 ) && (entryPrice > lastTP.price + FIXED_STOP * PIP_SIZE ))
							|| ((posSize > 1 ) && (entryPrice > lastTP.price)))){*/
							System.out.println("place incremental sell order " + entryPrice);
							tradeManager.enterMarketPositionMulti(entryPrice, POSITION_SIZE);
							//trade.status = Constants.STATUS_PARTIAL_FILLED;
							tradeManager.cancelStop();
						}
					}
				}
			}

		}
		else if (Constants.ACTION_BUY.equals(trade.action)){
			if ((lastbar60-1 > detectPos) && BarUtil.isUpBar(quotes60[lastbar60-1]) && (data.high > quotes60[lastbar60-1].high)){ 
				double entryPrice = quotes60[lastbar60-1].high;

				TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH, quotes60[lastbar60].time);
				tv.addNameValue("High Price", new Double(entryPrice).toString());
				if (!trade.findTradeEvent(tv)){
					trade.addTradeEvent(tv);
				
					TradePosition[] positions = trade.getFilledPositions();
					int posSize = positions.length;
					if (( posSize > 0 ) ){
						TradePosition lastTP = positions[posSize-1];
						int lastTPPos = Utility.findPositionByHour(quotes60, lastTP.position_time, Constants.BACK_TO_FRONT );
						if ((entryPrice < lastTP.price ) && (lastbar60 > lastTPPos)){
					/*	if ((lastbar60 > lastTPPos) && 
								(((posSize == 1 ) && (entryPrice < lastTP.price - FIXED_STOP * PIP_SIZE ))
								|| ((posSize > 1 ) && (entryPrice < lastTP.price)))){*/
							System.out.println("place incremental buy order " + entryPrice);
							tradeManager.enterMarketPositionMulti(entryPrice, POSITION_SIZE);
							//trade.status = Constants.STATUS_PARTIAL_FILLED;
							tradeManager.cancelStop();
						}
					}
				}
			}
		}

		// set stop

		// unload positions
		/*
		TradePosition[] positions = trade.getFilledPositions();
		int posSize = positions.length;
		
		if ( posSize > 1 ){

			if ( !trade.status.equals(Constants.STATUS_INCREMENTAL_COMPLETED)){
				// last one
				TradePosition position = positions[posSize-1]; // last one
				if (!Constants.STATUS_CLOSED.equals( position.getStatus())){
					if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  2 * FIXED_STOP * PIP_SIZE )){
						double closePrice = position.price -  2 * FIXED_STOP * PIP_SIZE;
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
						System.out.println("initial close position by market " + closePrice);
						tradeManager.closePositionByMarket( position.position_size, closePrice);
						position.setStatus(Constants.STATUS_CLOSED);
						trade.incrementalComplete = true;
					}
					else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  2 * FIXED_STOP * PIP_SIZE )){
						double closePrice = position.price +  2 * FIXED_STOP * PIP_SIZE;
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_SELL, position.position_size, closePrice);
						System.out.println("initial close position by market " + closePrice);
						tradeManager.closePositionByMarket( position.position_size, closePrice);
						position.setStatus(Constants.STATUS_CLOSED);
						trade.incrementalComplete = true;
					}
				}
			}
			else{
				// all the ones before, other than the first 1
				for ( int i = posSize-2; i > 0; i--){
					TradePosition position = positions[i];
					if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  FIXED_STOP * PIP_SIZE ) && !Constants.STATUS_CLOSED.equals(position.getStatus())){
						double closePrice = position.price -  FIXED_STOP * PIP_SIZE;
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
						System.out.println("close position by market " + closePrice);
						tradeManager.closePositionByMarket( position.position_size, closePrice);
						position.setStatus(Constants.STATUS_CLOSED);
					}
					else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  FIXED_STOP * PIP_SIZE )&& !Constants.STATUS_CLOSED.equals(position.getStatus())){
						double closePrice = position.price +  FIXED_STOP * PIP_SIZE;
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_SELL, position.position_size, closePrice);
						System.out.println("close position by market " + closePrice);
						tradeManager.closePositionByMarket( position.position_size, closePrice);
						position.setStatus(Constants.STATUS_CLOSED);
					}
				}
			}
		}*/
		
		
		// set stop
	}		

	
	public void entry_break_LL_HH(QuoteData data, TradeManagerInf tradeManager)
	{
		QuoteData[] quotes240 = tradeManager.getInstrument().getQuoteData(Constants.CHART_240_MIN);
		QuoteData[] quotes60 = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		QuoteData[] quotes15 = tradeManager.getInstrument().getQuoteData(Constants.CHART_15_MIN);
		int lastbar240 = quotes240.length - 1;
		int lastbar60 = quotes60.length - 1;
		int lastbar15 = quotes15.length - 1;
		int startPos60 = 0;
		double[] ema = Indicator.calculateEMA(quotes60, 20);

		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		Trade trade = tradeManager.getTrade();
			
		
		if (Constants.ACTION_BUY.equals(trade.action))
		{
			// remove if it is lower than previous low
			/*PushHighLow phlL = trade.phl;
			if ( data.low < phlL.pullBack.low ){
				removeTrade();
				return;
			}*/
			/*double avgSize = BarUtil.averageBarSizeOpenClose(quotes240);
			if (BarUtil.isDownBar(quotes240[lastbar240]) && ( BarUtil.barSize(quotes240[lastbar240]) > 3 * avgSize )){
				removeTrade();
				return;
			}*/
			
			startPos60 = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startPos60 = Utility.getHigh( quotes60, startPos60-4<0?0:startPos60-4, lastbar60-1).pos;
			
			
			PushList pushList = PushSetup.getDown2PushList( quotes60, startPos60, lastbar60 );
			if ((pushList != null ) &&( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
				
				if ( data.high > lastPush.pullBack.high ){
					trade.entryPrice = lastPush.pullBack.high;
					if ( quotes15[lastbar15-1].high > trade.entryPrice ){
						tradeManager.removeTrade();
						return;
					}

					//warning("Trade entry_HH_LL: Trade buy entered at " + data.time + " " + trade.entryPrice);
					tradeManager.enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
			    	//enterMarketPosition(trade.entryPrice);
					return;
				}
			}

			int startPos240 = Utility.findPositionByHour(quotes240, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			double startPrice = quotes240[startPos240].high;
			if ( data.high > startPrice ){
				trade.entryPrice = startPrice;
				if ( quotes15[lastbar15-1].high > trade.entryPrice ){
					tradeManager.removeTrade();
					return;
				}
				//warning("Trade entry_HH_LL higher than start price: Trade entered at " + data.time + " " + startPrice);
				tradeManager.enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
		    	//enterMarketPosition(trade.entryPrice);
				return;
			}

		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			// remove if it is high than previous low
			/*PushHighLow phlL = trade.phl;
			if ( data.high > phlL.pullBack.high ){
				removeTrade();
				return;
			}*/

			/*
			double avgSize = BarUtil.averageBarSizeOpenClose(quotes240);
			if (BarUtil.isUpBar(quotes240[lastbar240]) && ( BarUtil.barSize(quotes240[lastbar240]) > 3 * avgSize )){
				removeTrade();
				return;
			}*/

			startPos60 = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startPos60 = Utility.getLow( quotes60, startPos60-4<0?0:startPos60-4, lastbar60-1).pos;
			
			PushList pushList = PushSetup.getUp2PushList( quotes60, startPos60, lastbar60 );
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
					
				if ( data.low < lastPush.pullBack.low ){
					trade.entryPrice = lastPush.pullBack.low;
					if ( quotes15[lastbar15-1].low < trade.entryPrice ){
						tradeManager.removeTrade();
						return;
					}
					//warning("Trade entry_HH_LL: Trade sell entered at " + data.time + " " + trade.entryPrice);
					tradeManager.enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
					return;
			    	//enterMarketPosition(trade.entryPrice);
				}
			}

			int startPos240 = Utility.findPositionByHour(quotes240, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			double startPrice = quotes240[startPos240].low;
			if ( data.low < startPrice ){
				trade.entryPrice = startPrice;
				if ( quotes15[lastbar15-1].low < trade.entryPrice ){
					tradeManager.removeTrade();
					return;
				}
				//warning("Trade entry_HH_LL sell lower than start price: Trade entered at " + data.time + " " + startPrice);
				tradeManager.enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
		    	//enterMarketPosition(trade.entryPrice);
				return;
			}
		}
	}


	public void track_PBK_entry_using_momentum2(QuoteData data, TradeManagerInf tradeManager)
	{
		QuoteData[] quotes240 = tradeManager.getInstrument().getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotes240.length - 1;
		QuoteData[] quotes60 = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes15 = tradeManager.getInstrument().getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;

		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		Trade trade = tradeManager.getTrade();
	
		//int detectPos = Utility.findPositionByHour(quotesL, trade.detectTime, Constants.BACK_TO_FRONT );
		int pullBackStart = Utility.findPositionByHour(quotes60, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		
		if (Constants.ACTION_BUY.equals(trade.action)){
			
			//if ( lastbarL >= pullBackStart + 2)
			{ 
				int highestPoint = Utility.getHigh(quotes60, ((pullBackStart-4<0)?0:(pullBackStart-4)), ((pullBackStart+4)>lastbar60)?lastbar60:(pullBackStart+4)).pos;
				
				int strongBarEnd = lastbar60-1;
				while ( --strongBarEnd > highestPoint){
					if (!BarUtil.isUpBar(quotes60[strongBarEnd]))
						continue;
					//System.out.println(symbol + " " + data.time + " Up Bar detected:" + quotes60[strongBarEnd].time + " " + quotes60[strongBarEnd].high);
					
					if (!(quotes60[strongBarEnd+1].high < quotes60[strongBarEnd].high))
						continue;
					
					for ( int i = strongBarEnd+1; i < lastbar60; i++)
						if (quotes60[i].high > quotes60[strongBarEnd].high)// not the highest
							continue;
					
					if (!(data.high > quotes60[strongBarEnd].high))
						continue;


					int strongBarBegin = strongBarEnd;
					while (BarUtil.isUpBar(quotes60[strongBarBegin-1])) 
							strongBarBegin--;

					double avgBarSize = BarUtil.averageBarSize(quotes60);
					int prevPushNum = strongBarEnd - strongBarBegin + 1;
					double prevPushSize = quotes60[strongBarEnd].close - quotes60[strongBarBegin].open;
					if (!BarUtil.isStrongPush( prevPushSize, prevPushNum, avgBarSize ))
							continue;

					//System.out.println(symbol + " " + data.time + " Strong Up Bar detected:" + quotes60[strongBarBegin].time + " " + quotes60[strongBarEnd].time + " " + quotes60[strongBarEnd].high);
					double triggerPrice = quotes60[strongBarEnd].high;
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					tradeManager.enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					tradeManager.cancelLimits(); 
					return;

				}
				
				/*
				for ( int i = lastbar60-1; i > highestPoint+1; i--){
					PushList pushList = Pattern.findPast1Highs1(quotes60, i, i);
	
					if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
						PushHighLow lastPush = pushList.phls[pushList.phls.length-1];
						int prevPushEnd = lastPush.prePos;
						int prevPushBegin = prevPushEnd;
						while ( BarUtil.isUpBar(quotes60[prevPushBegin-1]))
								prevPushBegin--;
	
						double avgBarSize = BarUtil.averageBarSize(quotes60);
						int prevPushNum = prevPushEnd - prevPushBegin + 1;
						double prevPushSize = quotes60[prevPushEnd].close - quotes60[prevPushBegin].open;
						
						if (BarUtil.isStrongPush( prevPushSize, prevPushNum, avgBarSize )){
							double triggerPrice = quotes60[lastPush.prePos].high;
							int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
							//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
							enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
							cancelLimits(); 
							return;
						}
						
					}
				}*/
			}

			
			if ( data.high > quotes60[pullBackStart].high ){
				double entryPrice = quotes60[pullBackStart].high;
				if ( quotes15[lastbar15-1].high > entryPrice ){
					tradeManager.removeTrade();
					return;
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
				tradeManager.enterMarketPositionMulti(entryPrice,remainingToBeFilledPos);
				tradeManager.cancelLimits();
				return;
			}
			
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){

			//if ( lastbarL >= pullBackStart + 2)
			{ 
				int lowestPoint = Utility.getLow(quotes60, ((pullBackStart-4<0)?0:(pullBackStart-4)), ((pullBackStart+4)>lastbar60)?lastbar60:(pullBackStart+4)).pos;
	
				int strongBarEnd = lastbar60-1;
				while ( --strongBarEnd > lowestPoint){
					if (!BarUtil.isDownBar(quotes60[strongBarEnd]))
						continue;
					
					if (!(quotes60[strongBarEnd+1].low > quotes60[strongBarEnd].low))
						continue;
					
					for ( int i = strongBarEnd+1; i < lastbar60; i++)
						if (quotes60[i].low < quotes60[strongBarEnd].low)// not the lowest
							continue;
					
					if (!(data.low < quotes60[strongBarEnd].low))
						continue;
					
					int strongBarBegin = strongBarEnd;
					while (BarUtil.isDownBar(quotes60[strongBarBegin-1])) 
							strongBarBegin--;

					double avgBarSize = BarUtil.averageBarSize(quotes60);
					int prevPushNum = strongBarEnd - strongBarBegin + 1;
					double prevPushSize = quotes60[strongBarBegin].open - quotes60[strongBarEnd].close;
					if (!BarUtil.isStrongPush( prevPushSize, prevPushNum, avgBarSize ))
							continue;

					//System.out.println(symbol + " " + data.time + " String Down Bar detected:" + quotes60[strongBarBegin].time + " " + quotes60[strongBarEnd].time + " " + quotes60[strongBarEnd].low);
					double triggerPrice = quotes60[strongBarEnd].low;
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					tradeManager.enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					tradeManager.cancelLimits(); 
					return;

				}
				
				
				
				/*
				for ( int i = lastbar60-1; i > lowestPoint+1; i--){
					PushList pushList = Pattern.findPast1Lows1(quotes60, i, i);
	
					if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
						PushHighLow lastPush = pushList.phls[pushList.phls.length-1];
						int prevPushEnd = lastPush.prePos;
						int prevPushBegin = prevPushEnd;
						while ( BarUtil.isDownBar(quotes60[prevPushBegin-1]))
								prevPushBegin--;
	
						double avgBarSize = BarUtil.averageBarSize(quotes60);
						int prevPushNum = prevPushEnd - prevPushBegin + 1;
						double prevPushSize = quotes60[prevPushEnd].close - quotes60[prevPushBegin].open;
						
						if (BarUtil.isStrongPush( prevPushSize, prevPushNum, avgBarSize )){
							double triggerPrice = quotes60[lastPush.prePos].low;
							int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
							//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
							enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
							cancelLimits(); 
							return;
						}
						
					}
				}*/
			}

			int startPos240 = Utility.findPositionByHour(quotes240, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			double startPrice = quotes240[startPos240].low;
			if ( data.low < startPrice ){
				trade.entryPrice = startPrice;
				if ( quotes15[lastbar15-1].low < trade.entryPrice ){
					tradeManager.removeTrade();
					return;
				}
				//warning("Trade entry_HH_LL sell lower than start price: Trade entered at " + data.time + " " + startPrice);
				tradeManager.enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
		    	//enterMarketPosition(trade.entryPrice);
				return;
			}
		}
	}


	
	public void track_PBK_entry_using_momentum3(QuoteData data, TradeManagerInf tradeManager)
	{
		QuoteData[] quotes240 = tradeManager.getInstrument().getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes15 = tradeManager.getInstrument().getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;

		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		int INCREMENT_POSITION_SIZE = POSITION_SIZE/2;
		Trade trade = tradeManager.getTrade();
		double triggerPrice;
	
		//System.out.println("First BreakOut StartTime " + trade.getFirstBreakOutStartTime());
		int pushStart240 = Utility.findPositionByHour(quotes240, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		int pushStart60 = Utility.findPositionByHour(quotes60, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		int detectTime60 = Utility.findPositionByHour(quotes60, trade.detectTime, Constants.BACK_TO_FRONT );
		
		
		int pullBackStartPoint = 0;
		if (Constants.ACTION_BUY.equals(trade.action)){
			int highestPoint = 0;
			if ( pushStart60 == -1 ) // sometimes 240-60 can not be exactly mapped
				highestPoint = Utility.getHigh(quotes60, detectTime60-9, detectTime60-1).pos;
			else	
				highestPoint = Utility.getHigh(quotes60, pushStart60, detectTime60-1).pos;
			
			pullBackStartPoint = highestPoint;
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			int lowestPoint = 0;
			if ( pushStart60 == -1 ) // sometimes 240-60 can not be exactly mapped
				lowestPoint = Utility.getLow(quotes60, detectTime60-9, detectTime60-1).pos;
			else
				lowestPoint = Utility.getLow(quotes60, pushStart60, detectTime60-1).pos;
			pullBackStartPoint = lowestPoint;
		}
		
			/*
			PushList push2List = PushSetup.findPast2Highs2(quotes60, pullBackStartPoint, lastbar60);
			if (( push2List.phls != null ) && ( push2List.phls.length > 0 )){
				PushHighLow lastPush = push2List.phls[push2List.phls.length-1];
				if ( lastPush.pullBackSize > FIXED_STOP * PIP_SIZE ){
					trade.pullBackSetup.largeDiverage = true;
					triggerPrice = quotes60[lastPush.prePos].high;
					if (!trade.findFilledPosition(triggerPrice)){
						System.out.println()
						tradeManager.enterIncrementalMarketPosition(triggerPrice, INCREMENT_POSITION_SIZE, data.time);
					}
				}
			}*/

			
		/************************************
		 *  check momentum change and additional position after momentum
		 ************************************/
		if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_CLOSED.equals(trade.status))){
			if ( trade.pullBackSetup.momentumChangePoint == 0 ){
				if (Constants.ACTION_BUY.equals(trade.action)){
					MomentumDAO momentumChange = MomentumDetector.detect_momentum_change_by_close(quotes60, Constants.DIRECTION_DOWN,  pullBackStartPoint, lastbar60);
					if ( momentumChange != null ){
						trade.pullBackSetup.momentumChangePoint = momentumChange.momentumChangePoint;
						System.out.println("Momentum Change BUY triggered " + momentumChange.triggerPrice + " " + data.time + " lastDownBegin:" + quotes60[momentumChange.lastDownBarBegin].time + " " + quotes60[momentumChange.lastDownBarBegin].high + " pullbackStart:"+ quotes60[pullBackStartPoint].time);
						triggerPrice = momentumChange.triggerPrice;
						if ( trade.stop == 0 )
							trade.stop = triggerPrice - 2 * FIXED_STOP * PIP_SIZE;
						tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);
					}
				}
				else if (Constants.ACTION_SELL.equals(trade.action)){
					MomentumDAO momentumChange = MomentumDetector.detect_momentum_change_by_close(quotes60, Constants.DIRECTION_UP,  pullBackStartPoint, lastbar60);
					if ( momentumChange != null ){
						trade.pullBackSetup.momentumChangePoint = momentumChange.momentumChangePoint;
						System.out.println("Momentum Change SELL triggered " + momentumChange.triggerPrice + " " + data.time + " lastUpBegin:" + quotes60[momentumChange.lastUpBarBegin].time + " " + quotes60[momentumChange.lastUpBarBegin].low + " pullbackStart:"+ quotes60[pullBackStartPoint].time);
						triggerPrice = momentumChange.triggerPrice;
						if ( trade.stop == 0 )
							trade.stop = triggerPrice + 2 * FIXED_STOP * PIP_SIZE;
						tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);
					}
				}
			}else{
				if (Constants.ACTION_BUY.equals(trade.action)){
					//PushList push1List = PushSetup.findPast1Lows1(quotes60, pullBackStartPoint, lastbar60);
					PushList push1List = PushSetup.findPastLowsByX(quotes60,  pullBackStartPoint,  lastbar60,  1 );
					if (( push1List != null ) && ( push1List.getNumOfPushes() > 0 )){
						PushHighLow lastPush = push1List.getLastPush();
						triggerPrice = quotes60[lastPush.prePos].low;
						if (!trade.findFilledPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
						//if (!trade.findLimitPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
							System.out.println("Add additonal positon after Momentum Change BUY triggered " + triggerPrice + " " + data.time + " lastpeak:" + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.prePos].low);
							//tradeManager.enterLimitPositionMulti(INCREMENT_POSITION_SIZE, triggerPrice);
							tradeManager.enterIncrementalMarketPosition(triggerPrice, INCREMENT_POSITION_SIZE, data.time);
							trade.pullBackSetup.numOfAdditionalPosition++;
							
							if ((trade.stop == 0 )&&( trade.pullBackSetup.numOfAdditionalPosition == 2 )){
								trade.stop = triggerPrice- FIXED_STOP*PIP_SIZE;
								System.out.println("set stop after 2 addtional positions: Stop set at " + trade.stop);
								tradeManager.createStopOrder(trade.stop);
							}
								//trade.status = Constants.STATUS_FILLED;  // can still add final positions
							return;
						}

					}
				}else if (Constants.ACTION_SELL.equals(trade.action)){
					//PushList push1List = PushSetup.findPast1Highs1(quotes60, pullBackStartPoint, lastbar60);
					PushList push1List = PushSetup.findPastHighsByX(quotes60,  pullBackStartPoint,  lastbar60,  1 );
					if (( push1List != null ) && ( push1List.getNumOfPushes() > 0 )){
						PushHighLow lastPush = push1List.getLastPush();
						triggerPrice = quotes60[lastPush.prePos].high;
						if (!trade.findFilledPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
						//if (!trade.findLimitPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
							System.out.println("Add additonal positon after Momentum Change SELL triggered " + triggerPrice + " " + data.time + " lastpeak:" + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.prePos].low);
							//tradeManager.enterLimitPositionMulti(INCREMENT_POSITION_SIZE, triggerPrice);
							tradeManager.enterIncrementalMarketPosition(triggerPrice, INCREMENT_POSITION_SIZE, data.time);
							trade.pullBackSetup.numOfAdditionalPosition++;

							if ((trade.stop == 0 )&&( trade.pullBackSetup.numOfAdditionalPosition == 2 )){
								trade.stop = triggerPrice + FIXED_STOP*PIP_SIZE;
								System.out.println("set stop after 2 addtional positions: Stop set at " + trade.stop);
								tradeManager.createStopOrder(trade.stop);
							}
						}
					}
				}
			}
		

			// take practial profitcs
			/*
			if ( trade.remainingPositionSize > POSITION_SIZE ){
	    		for ( int j = lastbar60-2; j > pushStart60; j--){
	    			PushList pushList = Pattern.findPast1Lows1(quotes60, j, lastbar60);
	    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
    					PushHighLow lastPush = pushList.getPushHighLow(pushList.getNumOfPushes()-1);
    					triggerPrice = quotes60[lastPush.prePos].low;
    					tradeManager.createTradeTargetOrder(INCREMENT_POSITION_SIZE, triggerPrice);
    					break;
	   				}
	   			}
			}*/
			
			
		/************************************
		 *  add final position
		 ************************************/
		if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_CLOSED.equals(trade.status))){
			if (Constants.ACTION_BUY.equals(trade.action)){
				double startPrice = quotes240[pushStart240].high;
				if (( data.high > startPrice ) && (trade.remainingPositionSize < POSITION_SIZE)) {

					// check if it is passed trigger time
					trade.entryPrice = startPrice;
					if ( quotes15[lastbar15-1].high > trade.entryPrice ){
						tradeManager.removeTrade();
						return;
					}
					triggerPrice = quotes240[pushStart240].high;
					if (!trade.findFilledPosition(triggerPrice)){
						logger.info("Add final position " + triggerPrice + " " + (POSITION_SIZE - trade.remainingPositionSize) + data.time);
						QuoteData lowestPointSincePullBack = Utility.getLow(quotes60, pullBackStartPoint, lastbar60);
						if (trade.stop == 0 ){
							trade.stop = lowestPointSincePullBack.low;
							if ( triggerPrice - trade.stop > 2 * FIXED_STOP * PIP_SIZE )
								trade.stop = triggerPrice - 2 * FIXED_STOP * PIP_SIZE;
							logger.fine("set stop to the lowest point " + trade.stop);
							//tradeManager.createStopOrder(trade.stop);
						}
						// this sets stop
						tradeManager.enterIncrementalMarketPosition(triggerPrice, POSITION_SIZE - trade.remainingPositionSize, data.time);
						
						trade.status = Constants.STATUS_FILLED;
						return;
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				double startPrice = quotes240[pushStart240].low;
				if (( data.low < startPrice ) && (trade.remainingPositionSize < POSITION_SIZE)) {
					trade.entryPrice = startPrice;
					if ( quotes15[lastbar15-1].low < trade.entryPrice ){
						tradeManager.removeTrade();
						return;
					}
					triggerPrice = quotes240[pushStart240].low;
					if (!trade.findFilledPosition(triggerPrice)){
						logger.info("Add final position " + triggerPrice + " " + (POSITION_SIZE - trade.remainingPositionSize) + data.time);
						QuoteData highestPointSincePullBack = Utility.getHigh(quotes60, pullBackStartPoint, lastbar60);
						if (trade.stop == 0 ){
							trade.stop = highestPointSincePullBack.high;
							if ( trade.stop - triggerPrice > 2 * FIXED_STOP * PIP_SIZE )
								trade.stop = triggerPrice + 2 * FIXED_STOP * PIP_SIZE;
							logger.fine("set stop to the highest point " + trade.stop);
							//tradeManager.createStopOrder(trade.stop);
						}
						// this sets stop
						tradeManager.enterIncrementalMarketPosition(triggerPrice, POSITION_SIZE - trade.remainingPositionSize, data.time);
						trade.status = Constants.STATUS_FILLED;
						return;
					}
				}
			}
		}
		/*if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_CLOSED.equals(trade.status))){
			if (Constants.ACTION_BUY.equals(trade.action)){
				double startPrice = quotes240[pushStart240].high;
				if ( data.high > startPrice ){ 
					if (trade.remainingPositionSize < POSITION_SIZE) {
	
						// check if it is passed trigger time
						trade.entryPrice = startPrice;
						if ( quotes15[lastbar15-1].high > trade.entryPrice ){
							tradeManager.removeTrade();
							return;
						}
						triggerPrice = quotes240[pushStart240].high;
						if (!trade.findFilledPosition(triggerPrice)){
							logger.info("Add final position " + triggerPrice + " " + (POSITION_SIZE - trade.remainingPositionSize) + data.time);
							QuoteData lowestPointSincePullBack = Utility.getLow(quotes60, pullBackStartPoint, lastbar60);
							if (trade.stop == 0 ){
								trade.stop = lowestPointSincePullBack.low;
								if ( triggerPrice - trade.stop > 2 * FIXED_STOP * PIP_SIZE )
									trade.stop = triggerPrice - 2 * FIXED_STOP * PIP_SIZE;
								logger.fine("set stop to the lowest point " + trade.stop);
								//tradeManager.createStopOrder(trade.stop);
							}
							// this sets stop
							tradeManager.enterIncrementalMarketPosition(triggerPrice, POSITION_SIZE - trade.remainingPositionSize, data.time);
							
							trade.status = Constants.STATUS_FILLED;
							return;
						}
					}
					else{
						if (trade.stop == 0 ){
							triggerPrice = quotes240[pushStart240].high;
							QuoteData lowestPointSincePullBack = Utility.getLow(quotes60, pullBackStartPoint, lastbar60);
							trade.stop = lowestPointSincePullBack.low;
							if ( triggerPrice - trade.stop > 2 * FIXED_STOP * PIP_SIZE )
								trade.stop = triggerPrice - 2 * FIXED_STOP * PIP_SIZE;
							logger.fine("set stop to the lowest point " + trade.stop);
							tradeManager.createStopOrder(trade.stop);
						}
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				double startPrice = quotes240[pushStart240].low;
				if ( data.low < startPrice ){
					if (trade.remainingPositionSize < POSITION_SIZE) {
						trade.entryPrice = startPrice;
						if ( quotes15[lastbar15-1].low < trade.entryPrice ){
							tradeManager.removeTrade();
							return;
						}
						triggerPrice = quotes240[pushStart240].low;
						if (!trade.findFilledPosition(triggerPrice)){
							logger.info("Add final position " + triggerPrice + " " + (POSITION_SIZE - trade.remainingPositionSize) + data.time);
							QuoteData highestPointSincePullBack = Utility.getHigh(quotes60, pullBackStartPoint, lastbar60);
							if (trade.stop == 0 ){
								trade.stop = highestPointSincePullBack.high;
								if ( trade.stop - triggerPrice > 2 * FIXED_STOP * PIP_SIZE )
									trade.stop = triggerPrice + 2 * FIXED_STOP * PIP_SIZE;
								logger.fine("set stop to the highest point " + trade.stop);
								//tradeManager.createStopOrder(trade.stop);
							}
							// this sets stop
							tradeManager.enterIncrementalMarketPosition(triggerPrice, POSITION_SIZE - trade.remainingPositionSize, data.time);
							trade.status = Constants.STATUS_FILLED;
							return;
						}
					}
					else{
						QuoteData highestPointSincePullBack = Utility.getHigh(quotes60, pullBackStartPoint, lastbar60);
						if (trade.stop == 0 ){
							triggerPrice = quotes240[pushStart240].low;
							trade.stop = highestPointSincePullBack.high;
							if ( trade.stop - triggerPrice > 2 * FIXED_STOP * PIP_SIZE )
								trade.stop = triggerPrice + 2 * FIXED_STOP * PIP_SIZE;
							logger.fine("set stop to the highest point " + trade.stop);
							//tradeManager.createStopOrder(trade.stop);
						}
					}
				}						
			}*/
		}

			
		/*************************************************
		// set stop by time
		*************************************************/
		if (( trade.stop == 0 )/* && ( trade.remainingPositionSize > 0 )*/){
			if (Constants.ACTION_BUY.equals(trade.action)){
				QuoteData lowestPointSincePullBack = Utility.getLow(quotes60, pullBackStartPoint+1, lastbar60);
				if  (( lowestPointSincePullBack != null ) && ( lastbar60 > lowestPointSincePullBack.pos + 14 )){
					System.out.println("Stop set at lowestpoint " + lowestPointSincePullBack.time + " at " + lowestPointSincePullBack.low);
					trade.stop = lowestPointSincePullBack.low;
					tradeManager.createStopOrder(lowestPointSincePullBack.low);
					//trade.status = Constants.STATUS_FILLED;  // can still add final positions
					return;
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				QuoteData highestPointSincePullBack = Utility.getHigh(quotes60, pullBackStartPoint+1, lastbar60);
				if  (( highestPointSincePullBack != null ) && ( lastbar60 > highestPointSincePullBack.pos + 14 )){
					System.out.println("Stop set at highestpoint " + highestPointSincePullBack.time + " at " + highestPointSincePullBack.high);
					trade.stop = highestPointSincePullBack.high;
					tradeManager.createStopOrder(highestPointSincePullBack.high);
					//trade.status = Constants.STATUS_FILLED;  // can still add final positions
					return;
				}
			}
		}

		/****************************************
		//  set stop by profit
		//***************************************/
		if (Constants.ACTION_BUY.equals(trade.action) && ((data.high - trade.entryPrice) > 2 * FIXED_STOP * PIP_SIZE) ){
			if ( trade.stop < trade.entryPrice ){
				trade.stop = trade.entryPrice;
				tradeManager.createStopOrder(trade.stop);
			}
		}
		else if (Constants.ACTION_SELL.equals(trade.action) && ((trade.entryPrice - data.low ) > 2 * FIXED_STOP * PIP_SIZE) ){
			if ( trade.stop > trade.entryPrice ){
				trade.stop = trade.entryPrice;
				tradeManager.createStopOrder(trade.stop);
			}
		}
			

		
		
			/*************************************************
			// set stop and take profits
			// set stop by trend resume
			 * */
			/*
			if ((trade.pullBackSetup.momentumChangeDetected) && ( trade.pullBackSetup.trendResumePos == 0 )){
				MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes60, Constants.DIRECTION_DOWN, lastbar60-1);
				if ( mom != null ){
					System.out.println("Trend resume detected at " + quotes60[lastbar60].time);
					trade.pullBackSetup.trendResumePos = mom.startPos;
					// calculate stop;
				}
			}*/
			
		/*************************************************
		// detect trend resume
		 *************************************************/
		if (( trade.pullBackSetup.trendResumePos == 0 ) && ((trade.pullBackSetup.momentumChangePoint > 0 ) || Constants.STATUS_FILLED.equals(trade.status))){
			if (Constants.ACTION_BUY.equals(trade.action)){
				if ( BarUtil.isUpBar(quotes60[lastbar60-1])){
					int upBarEnd = lastbar60-1;
					MomentumDAO mom = MomentumDetector.detect_large_momentum(quotes60, Constants.DIRECTION_UP, upBarEnd);//upBarBegin-1);
					if ( mom != null ){
						int downBarEnd = mom.startPos-1;
						int downBarBegin = downBarEnd;
						while (BarUtil.isDownBar(quotes60[downBarBegin-1]))
							downBarBegin--;
						
						//double startPrice = quotes240[pushStart240].high;gg

						if (( quotes60[mom.endPos].close > quotes60[downBarBegin].high ) && ( mom.startPos > (trade.pullBackSetup.momentumChangePoint+3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes60[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				if ( BarUtil.isDownBar(quotes60[lastbar60-1])){
					int downBarEnd = lastbar60-1;
					MomentumDAO mom = MomentumDetector.detect_large_momentum(quotes60, Constants.DIRECTION_DOWN, downBarEnd);//upBarBegin-1);
					if ( mom != null ){
						int upBarEnd = mom.startPos-1;
						int upBarBegin = upBarEnd;
						while (BarUtil.isUpBar(quotes60[upBarBegin-1]))
							upBarBegin--;
						if (( quotes60[mom.endPos].close < quotes60[upBarBegin].low ) && ( mom.startPos > (trade.pullBackSetup.momentumChangePoint+3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes60[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
		}
	
	
		
/*		if (( trade.pullBackSetup.trendResumePos == 0 ) && (trade.pullBackSetup.momentumChangePoint > 0 )){
			if (Constants.ACTION_BUY.equals(trade.action)){
				if ( BarUtil.isUpBar(quotes60[lastbar60-1]) && BarUtil.isDownBar(quotes60[lastbar60-2])){
					int downBarEnd = lastbar60-2;
					int downBarBegin = downBarEnd;
					while (BarUtil.isDownBar(quotes60[downBarBegin-1]))
						downBarBegin--;
					
					MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes60, Constants.DIRECTION_UP, downBarEnd);//downBarBegin-1);
					if (( mom != null ) && (quotes60[mom.startPos].low < quotes60[downBarEnd].low )){
						if (( mom.startPos > (trade.pullBackSetup.momentumChangePoint - 3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes60[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				if ( BarUtil.isDownBar(quotes60[lastbar60-1]) && BarUtil.isUpBar(quotes60[lastbar60-2])){
					int upBarEnd = lastbar60-2;
					int upBarBegin = upBarEnd;
					while (BarUtil.isUpBar(quotes60[upBarBegin-1]))
						upBarBegin--;
					
					MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes60, Constants.DIRECTION_DOWN, upBarEnd);//upBarBegin-1);
					if (( mom != null ) && (quotes60[mom.startPos].high > quotes60[upBarEnd].high )){
						if (( mom.startPos > (trade.pullBackSetup.momentumChangePoint - 3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes60[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
		}
	*/	
		
		/*************************************************
		// take profit after trend resume
		 *************************************************/
		if (( trade.pullBackSetup.trendResumePos != 0 ) && ( trade.pullBackSetup.momentumChangeAfterTrendResume == false )){
			//System.out.println("check " + data.time);
			//if ("20150203  00:58:00".equals(data.time))
			if (Constants.ACTION_BUY.equals(trade.action)){
				//if ("20150217  04:02:00".equals(data.time))
				//	System.out.println("here");
				MomentumDAO momentumChange = MomentumDetector.detect_momentum_change_by_close(quotes60, Constants.DIRECTION_UP,  trade.pullBackSetup.trendResumePos, lastbar60);
				if ( momentumChange != null ){
					triggerPrice = momentumChange.triggerPrice;
					double avgBarSizeL = BarUtil.averageBarSize(quotes240);
					//(tradeManager.inProfit(data) > avgBarSizeL)

					System.out.println("Momentum Changed detected after trend resume " + quotes60[lastbar60].time + " lastUpBegin:" + quotes60[momentumChange.lastUpBarBegin].time + " " + quotes60[momentumChange.lastUpBarBegin].low + " take profit and exit positions at " + triggerPrice);
					int positionSizeTobeClosed = trade.remainingPositionSize;//-POSITION_SIZE;
					if (/*(inProfit(trade, triggerPrice) > avgBarSizeL) &&*/ (positionSizeTobeClosed > 0 ) && !trade.findTargetPosition(triggerPrice)) {
						//tradeManager.createTradeTargetOrder(INCREMENT_POSITION_SIZE, triggerPrice);
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_SELL, positionSizeTobeClosed, triggerPrice);
						tradeManager.closePositionByMarket( positionSizeTobeClosed, triggerPrice);
						trade.pullBackSetup.momentumChangeAfterTrendResume = true;
						//trade.setStatus(Constants.STATUS_CLOSED);
					}
					return;
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				MomentumDAO momentumChange = MomentumDetector.detect_momentum_change_by_close(quotes60, Constants.DIRECTION_DOWN,  trade.pullBackSetup.trendResumePos, lastbar60);
				if ( momentumChange != null ){
					triggerPrice = momentumChange.triggerPrice;
					double avgBarSizeL = BarUtil.averageBarSize(quotes240);
					System.out.println("Momentum Changed detected after trend resume " + quotes60[lastbar60].time + "lastdownbarbegin:" + quotes60[momentumChange.lastDownBarBegin].time + " " + quotes60[momentumChange.lastDownBarBegin].high + " take profit and exit positions at " + triggerPrice);
					int positionSizeTobeClosed = trade.remainingPositionSize;//-POSITION_SIZE;
					if (/*(inProfit(trade, triggerPrice) > avgBarSizeL) &&*/ (positionSizeTobeClosed > 0 ) && !trade.findTargetPosition(triggerPrice)) {
						//tradeManager.createTradeTargetOrder(INCREMENT_POSITION_SIZE, triggerPrice);
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, positionSizeTobeClosed, triggerPrice);
						tradeManager.closePositionByMarket( positionSizeTobeClosed, triggerPrice);
						trade.pullBackSetup.momentumChangeAfterTrendResume = true;
						//trade.setStatus(Constants.STATUS_CLOSED);
					}
					return;
				}
			}
		
		
		}
		
		
		// unload positions
/*		int posSize = 0;
		while( positions[posSize] != null ){
			posSize++;
		}
		
		if ( posSize > 0 ){
			// last one
			TradePosition position = positions[posSize-1]; // last one
			if (!Constants.STATUS_CLOSED.equals( position.getStatus())){
				if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  2 * FIXED_STOP * PIP_SIZE )){
					double closePrice = position.price -  2 * FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
				else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  2 * FIXED_STOP * PIP_SIZE )){
					double closePrice = position.price +  2 * FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
			} 
			
			// all the ones before, other than the first 1
			Constants.STATUS_CLOSED.equals( position.getStatus());
			for ( int i = posSize-2; i > 0; i--){
				position = positions[i];
				if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  FIXED_STOP * PIP_SIZE ) && !Constants.STATUS_CLOSED.equals(position.getStatus())){
					double closePrice = position.price -  FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
				else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  FIXED_STOP * PIP_SIZE )&& !Constants.STATUS_CLOSED.equals(position.getStatus())){
					double closePrice = position.price +  FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
			}
		}
	*/	
		
		// set stop

		
	}


	
	
	public void track_PBK_entry_using_momentum4(QuoteData data, TradeManagerInf tradeManager)
	{
		QuoteData[] quotes240 = tradeManager.getInstrument().getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes15 = tradeManager.getInstrument().getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;

		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		int INCREMENT_POSITION_SIZE = POSITION_SIZE/2;
		Trade trade = tradeManager.getTrade();
		double triggerPrice;
	
		//System.out.println("First BreakOut StartTime " + trade.getFirstBreakOutStartTime());
		int pushStart240 = Utility.findPositionByHour(quotes240, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		int pushStart60 = Utility.findPositionByHour(quotes60, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		int detectTime60 = Utility.findPositionByHour(quotes60, trade.detectTime, Constants.BACK_TO_FRONT );
		
		
		int pullBackStartPoint = 0;
		if (Constants.ACTION_BUY.equals(trade.action)){
			int highestPoint = 0;
			if ( pushStart60 == -1 ) // sometimes 240-60 can not be exactly mapped
				highestPoint = Utility.getHigh(quotes60, detectTime60-9, detectTime60-1).pos;
			else	
				highestPoint = Utility.getHigh(quotes60, pushStart60, detectTime60-1).pos;
			
			pullBackStartPoint = highestPoint;
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			int lowestPoint = 0;
			if ( pushStart60 == -1 ) // sometimes 240-60 can not be exactly mapped
				lowestPoint = Utility.getLow(quotes60, detectTime60-9, detectTime60-1).pos;
			else
				lowestPoint = Utility.getLow(quotes60, pushStart60, detectTime60-1).pos;
			pullBackStartPoint = lowestPoint;
		}
		
			
		/************************************
		 *  check momentum change and additional position after momentum
		 ************************************/
		if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_CLOSED.equals(trade.status))){
			if ( trade.pullBackSetup.momentumChangePoint == 0 ){
				if (Constants.ACTION_BUY.equals(trade.action)){
					MomentumDAO momentumChange = MomentumDetector.detect_momentum_change_by_close(quotes60, Constants.DIRECTION_DOWN,  pullBackStartPoint, lastbar60);
					if ( momentumChange != null ){
						tradeManager.removeTrade();
						return;
						/*trade.pullBackSetup.momentumChangePoint = momentumChange.momentumChangePoint;
						System.out.println("Momentum Change BUY triggered " + momentumChange.triggerPrice + " " + data.time + " lastDownBegin:" + quotes60[momentumChange.lastDownBarBegin].time + " " + quotes60[momentumChange.lastDownBarBegin].high + " pullbackStart:"+ quotes60[pullBackStartPoint].time);
						triggerPrice = momentumChange.triggerPrice;
						if ( trade.stop == 0 )
							trade.stop = triggerPrice - 2 * FIXED_STOP * PIP_SIZE;
						tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);*/
					}
				}
				else if (Constants.ACTION_SELL.equals(trade.action)){
					MomentumDAO momentumChange = MomentumDetector.detect_momentum_change_by_close(quotes60, Constants.DIRECTION_UP,  pullBackStartPoint, lastbar60);
					if ( momentumChange != null ){
						tradeManager.removeTrade();
						return;
						/*trade.pullBackSetup.momentumChangePoint = momentumChange.momentumChangePoint;
						System.out.println("Momentum Change SELL triggered " + momentumChange.triggerPrice + " " + data.time + " lastUpBegin:" + quotes60[momentumChange.lastUpBarBegin].time + " " + quotes60[momentumChange.lastUpBarBegin].low + " pullbackStart:"+ quotes60[pullBackStartPoint].time);
						triggerPrice = momentumChange.triggerPrice;
						if ( trade.stop == 0 )
							trade.stop = triggerPrice + 2 * FIXED_STOP * PIP_SIZE;
						tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);*/
					}
				}
			}else{
				if (Constants.ACTION_BUY.equals(trade.action)){
					//PushList push1List = PushSetup.findPast1Lows1(quotes60, pullBackStartPoint, lastbar60);
					PushList push1List = PushSetup.findPastLowsByX(quotes60,  pullBackStartPoint,  lastbar60,  1 );
					if (( push1List != null ) && ( push1List.getNumOfPushes() > 0 )){
						PushHighLow lastPush = push1List.getLastPush();
						triggerPrice = quotes60[lastPush.prePos].low;
						if (!trade.findFilledPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
						//if (!trade.findLimitPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
							System.out.println("Add additonal positon after Momentum Change BUY triggered " + triggerPrice + " " + data.time + " lastpeak:" + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.prePos].low);
							//tradeManager.enterLimitPositionMulti(INCREMENT_POSITION_SIZE, triggerPrice);
							tradeManager.enterIncrementalMarketPosition(triggerPrice, INCREMENT_POSITION_SIZE, data.time);
							trade.pullBackSetup.numOfAdditionalPosition++;
							
							if ((trade.stop == 0 )&&( trade.pullBackSetup.numOfAdditionalPosition == 2 )){
								trade.stop = triggerPrice- FIXED_STOP*PIP_SIZE;
								System.out.println("set stop after 2 addtional positions: Stop set at " + trade.stop);
								tradeManager.createStopOrder(trade.stop);
							}
								//trade.status = Constants.STATUS_FILLED;  // can still add final positions
							return;
						}

					}
				}else if (Constants.ACTION_SELL.equals(trade.action)){
					//PushList push1List = PushSetup.findPast1Highs1(quotes60, pullBackStartPoint, lastbar60);
					PushList push1List = PushSetup.findPastHighsByX(quotes60,  pullBackStartPoint,  lastbar60,  1 );
					if (( push1List != null ) && ( push1List.getNumOfPushes() > 0 )){
						PushHighLow lastPush = push1List.getLastPush();
						triggerPrice = quotes60[lastPush.prePos].high;
						if (!trade.findFilledPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
						//if (!trade.findLimitPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
							System.out.println("Add additonal positon after Momentum Change SELL triggered " + triggerPrice + " " + data.time + " lastpeak:" + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.prePos].low);
							//tradeManager.enterLimitPositionMulti(INCREMENT_POSITION_SIZE, triggerPrice);
							tradeManager.enterIncrementalMarketPosition(triggerPrice, INCREMENT_POSITION_SIZE, data.time);
							trade.pullBackSetup.numOfAdditionalPosition++;

							if ((trade.stop == 0 )&&( trade.pullBackSetup.numOfAdditionalPosition == 2 )){
								trade.stop = triggerPrice + FIXED_STOP*PIP_SIZE;
								System.out.println("set stop after 2 addtional positions: Stop set at " + trade.stop);
								tradeManager.createStopOrder(trade.stop);
							}
						}
					}
				}
			}
		

			// take practial profitcs
			/*
			if ( trade.remainingPositionSize > POSITION_SIZE ){
	    		for ( int j = lastbar60-2; j > pushStart60; j--){
	    			PushList pushList = Pattern.findPast1Lows1(quotes60, j, lastbar60);
	    			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 )){
    					PushHighLow lastPush = pushList.getPushHighLow(pushList.getNumOfPushes()-1);
    					triggerPrice = quotes60[lastPush.prePos].low;
    					tradeManager.createTradeTargetOrder(INCREMENT_POSITION_SIZE, triggerPrice);
    					break;
	   				}
	   			}
			}*/
			
			
		/************************************
		 *  add final position
		 ************************************/
		if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_CLOSED.equals(trade.status))){
			if (Constants.ACTION_BUY.equals(trade.action)){
				double startPrice = quotes240[pushStart240].high;
				if (( data.high > startPrice ) && (trade.remainingPositionSize < POSITION_SIZE)) {

					// check if it is passed trigger time
					trade.entryPrice = startPrice;
					if ( quotes15[lastbar15-1].high > trade.entryPrice ){
						tradeManager.removeTrade();
						return;
					}
					triggerPrice = quotes240[pushStart240].high;
					if (!trade.findFilledPosition(triggerPrice)){
						logger.info("Add final position " + triggerPrice + " " + (POSITION_SIZE - trade.remainingPositionSize) + data.time);
						QuoteData lowestPointSincePullBack = Utility.getLow(quotes60, pullBackStartPoint, lastbar60);
						if (trade.stop == 0 ){
							trade.stop = lowestPointSincePullBack.low;
							if ( triggerPrice - trade.stop > 2 * FIXED_STOP * PIP_SIZE )
								trade.stop = triggerPrice - 2 * FIXED_STOP * PIP_SIZE;
							logger.fine("set stop to the lowest point " + trade.stop);
							//tradeManager.createStopOrder(trade.stop);
						}
						// this sets stop
						tradeManager.enterIncrementalMarketPosition(triggerPrice, POSITION_SIZE - trade.remainingPositionSize, data.time);
						
						trade.status = Constants.STATUS_FILLED;
						return;
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				double startPrice = quotes240[pushStart240].low;
				if (( data.low < startPrice ) && (trade.remainingPositionSize < POSITION_SIZE)) {
					trade.entryPrice = startPrice;
					if ( quotes15[lastbar15-1].low < trade.entryPrice ){
						tradeManager.removeTrade();
						return;
					}
					triggerPrice = quotes240[pushStart240].low;
					if (!trade.findFilledPosition(triggerPrice)){
						logger.info("Add final position " + triggerPrice + " " + (POSITION_SIZE - trade.remainingPositionSize) + data.time);
						QuoteData highestPointSincePullBack = Utility.getHigh(quotes60, pullBackStartPoint, lastbar60);
						if (trade.stop == 0 ){
							trade.stop = highestPointSincePullBack.high;
							if ( trade.stop - triggerPrice > 2 * FIXED_STOP * PIP_SIZE )
								trade.stop = triggerPrice + 2 * FIXED_STOP * PIP_SIZE;
							logger.fine("set stop to the highest point " + trade.stop);
							//tradeManager.createStopOrder(trade.stop);
						}
						// this sets stop
						tradeManager.enterIncrementalMarketPosition(triggerPrice, POSITION_SIZE - trade.remainingPositionSize, data.time);
						trade.status = Constants.STATUS_FILLED;
						return;
					}
				}
			}
		}
		/*if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_CLOSED.equals(trade.status))){
			if (Constants.ACTION_BUY.equals(trade.action)){
				double startPrice = quotes240[pushStart240].high;
				if ( data.high > startPrice ){ 
					if (trade.remainingPositionSize < POSITION_SIZE) {
	
						// check if it is passed trigger time
						trade.entryPrice = startPrice;
						if ( quotes15[lastbar15-1].high > trade.entryPrice ){
							tradeManager.removeTrade();
							return;
						}
						triggerPrice = quotes240[pushStart240].high;
						if (!trade.findFilledPosition(triggerPrice)){
							logger.info("Add final position " + triggerPrice + " " + (POSITION_SIZE - trade.remainingPositionSize) + data.time);
							QuoteData lowestPointSincePullBack = Utility.getLow(quotes60, pullBackStartPoint, lastbar60);
							if (trade.stop == 0 ){
								trade.stop = lowestPointSincePullBack.low;
								if ( triggerPrice - trade.stop > 2 * FIXED_STOP * PIP_SIZE )
									trade.stop = triggerPrice - 2 * FIXED_STOP * PIP_SIZE;
								logger.fine("set stop to the lowest point " + trade.stop);
								//tradeManager.createStopOrder(trade.stop);
							}
							// this sets stop
							tradeManager.enterIncrementalMarketPosition(triggerPrice, POSITION_SIZE - trade.remainingPositionSize, data.time);
							
							trade.status = Constants.STATUS_FILLED;
							return;
						}
					}
					else{
						if (trade.stop == 0 ){
							triggerPrice = quotes240[pushStart240].high;
							QuoteData lowestPointSincePullBack = Utility.getLow(quotes60, pullBackStartPoint, lastbar60);
							trade.stop = lowestPointSincePullBack.low;
							if ( triggerPrice - trade.stop > 2 * FIXED_STOP * PIP_SIZE )
								trade.stop = triggerPrice - 2 * FIXED_STOP * PIP_SIZE;
							logger.fine("set stop to the lowest point " + trade.stop);
							tradeManager.createStopOrder(trade.stop);
						}
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				double startPrice = quotes240[pushStart240].low;
				if ( data.low < startPrice ){
					if (trade.remainingPositionSize < POSITION_SIZE) {
						trade.entryPrice = startPrice;
						if ( quotes15[lastbar15-1].low < trade.entryPrice ){
							tradeManager.removeTrade();
							return;
						}
						triggerPrice = quotes240[pushStart240].low;
						if (!trade.findFilledPosition(triggerPrice)){
							logger.info("Add final position " + triggerPrice + " " + (POSITION_SIZE - trade.remainingPositionSize) + data.time);
							QuoteData highestPointSincePullBack = Utility.getHigh(quotes60, pullBackStartPoint, lastbar60);
							if (trade.stop == 0 ){
								trade.stop = highestPointSincePullBack.high;
								if ( trade.stop - triggerPrice > 2 * FIXED_STOP * PIP_SIZE )
									trade.stop = triggerPrice + 2 * FIXED_STOP * PIP_SIZE;
								logger.fine("set stop to the highest point " + trade.stop);
								//tradeManager.createStopOrder(trade.stop);
							}
							// this sets stop
							tradeManager.enterIncrementalMarketPosition(triggerPrice, POSITION_SIZE - trade.remainingPositionSize, data.time);
							trade.status = Constants.STATUS_FILLED;
							return;
						}
					}
					else{
						QuoteData highestPointSincePullBack = Utility.getHigh(quotes60, pullBackStartPoint, lastbar60);
						if (trade.stop == 0 ){
							triggerPrice = quotes240[pushStart240].low;
							trade.stop = highestPointSincePullBack.high;
							if ( trade.stop - triggerPrice > 2 * FIXED_STOP * PIP_SIZE )
								trade.stop = triggerPrice + 2 * FIXED_STOP * PIP_SIZE;
							logger.fine("set stop to the highest point " + trade.stop);
							//tradeManager.createStopOrder(trade.stop);
						}
					}
				}						
			}*/
		}

			
		/*************************************************
		// set stop by time
		*************************************************/
		if (( trade.stop == 0 )/* && ( trade.remainingPositionSize > 0 )*/){
			if (Constants.ACTION_BUY.equals(trade.action)){
				QuoteData lowestPointSincePullBack = Utility.getLow(quotes60, pullBackStartPoint+1, lastbar60);
				if  (( lowestPointSincePullBack != null ) && ( lastbar60 > lowestPointSincePullBack.pos + 14 )){
					System.out.println("Stop set at lowestpoint " + lowestPointSincePullBack.time + " at " + lowestPointSincePullBack.low);
					trade.stop = lowestPointSincePullBack.low;
					tradeManager.createStopOrder(lowestPointSincePullBack.low);
					//trade.status = Constants.STATUS_FILLED;  // can still add final positions
					return;
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				QuoteData highestPointSincePullBack = Utility.getHigh(quotes60, pullBackStartPoint+1, lastbar60);
				if  (( highestPointSincePullBack != null ) && ( lastbar60 > highestPointSincePullBack.pos + 14 )){
					System.out.println("Stop set at highestpoint " + highestPointSincePullBack.time + " at " + highestPointSincePullBack.high);
					trade.stop = highestPointSincePullBack.high;
					tradeManager.createStopOrder(highestPointSincePullBack.high);
					//trade.status = Constants.STATUS_FILLED;  // can still add final positions
					return;
				}
			}
		}
			
			/*************************************************
			// set stop and take profits
			// set stop by trend resume
			 * */
			/*
			if ((trade.pullBackSetup.momentumChangeDetected) && ( trade.pullBackSetup.trendResumePos == 0 )){
				MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes60, Constants.DIRECTION_DOWN, lastbar60-1);
				if ( mom != null ){
					System.out.println("Trend resume detected at " + quotes60[lastbar60].time);
					trade.pullBackSetup.trendResumePos = mom.startPos;
					// calculate stop;
				}
			}*/
			
		/*************************************************
		// detect trend resume
		 *************************************************/
		if (( trade.pullBackSetup.trendResumePos == 0 ) && ((trade.pullBackSetup.momentumChangePoint > 0 ) || Constants.STATUS_FILLED.equals(trade.status))){
			if (Constants.ACTION_BUY.equals(trade.action)){
				if ( BarUtil.isUpBar(quotes60[lastbar60-1])){
					int upBarEnd = lastbar60-1;
					MomentumDAO mom = MomentumDetector.detect_large_momentum(quotes60, Constants.DIRECTION_UP, upBarEnd);//upBarBegin-1);
					if ( mom != null ){
						int downBarEnd = mom.startPos-1;
						int downBarBegin = downBarEnd;
						while (BarUtil.isDownBar(quotes60[downBarBegin-1]))
							downBarBegin--;
						
						//double startPrice = quotes240[pushStart240].high;gg

						if (( quotes60[mom.endPos].close > quotes60[downBarBegin].high ) && ( mom.startPos > (trade.pullBackSetup.momentumChangePoint+3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes60[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				if ( BarUtil.isDownBar(quotes60[lastbar60-1])){
					int downBarEnd = lastbar60-1;
					MomentumDAO mom = MomentumDetector.detect_large_momentum(quotes60, Constants.DIRECTION_DOWN, downBarEnd);//upBarBegin-1);
					if ( mom != null ){
						int upBarEnd = mom.startPos-1;
						int upBarBegin = upBarEnd;
						while (BarUtil.isUpBar(quotes60[upBarBegin-1]))
							upBarBegin--;
						if (( quotes60[mom.endPos].close < quotes60[upBarBegin].low ) && ( mom.startPos > (trade.pullBackSetup.momentumChangePoint+3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes60[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
		}
	
	
		
/*		if (( trade.pullBackSetup.trendResumePos == 0 ) && (trade.pullBackSetup.momentumChangePoint > 0 )){
			if (Constants.ACTION_BUY.equals(trade.action)){
				if ( BarUtil.isUpBar(quotes60[lastbar60-1]) && BarUtil.isDownBar(quotes60[lastbar60-2])){
					int downBarEnd = lastbar60-2;
					int downBarBegin = downBarEnd;
					while (BarUtil.isDownBar(quotes60[downBarBegin-1]))
						downBarBegin--;
					
					MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes60, Constants.DIRECTION_UP, downBarEnd);//downBarBegin-1);
					if (( mom != null ) && (quotes60[mom.startPos].low < quotes60[downBarEnd].low )){
						if (( mom.startPos > (trade.pullBackSetup.momentumChangePoint - 3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes60[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				if ( BarUtil.isDownBar(quotes60[lastbar60-1]) && BarUtil.isUpBar(quotes60[lastbar60-2])){
					int upBarEnd = lastbar60-2;
					int upBarBegin = upBarEnd;
					while (BarUtil.isUpBar(quotes60[upBarBegin-1]))
						upBarBegin--;
					
					MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes60, Constants.DIRECTION_DOWN, upBarEnd);//upBarBegin-1);
					if (( mom != null ) && (quotes60[mom.startPos].high > quotes60[upBarEnd].high )){
						if (( mom.startPos > (trade.pullBackSetup.momentumChangePoint - 3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes60[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
		}
	*/	
		
		/*************************************************
		// take profit after trend resume
		 *************************************************/
		if (( trade.pullBackSetup.trendResumePos != 0 ) && ( trade.pullBackSetup.momentumChangeAfterTrendResume == false )){
			//System.out.println("check " + data.time);
			//if ("20150203  00:58:00".equals(data.time))
			if (Constants.ACTION_BUY.equals(trade.action)){
				//if ("20150217  04:02:00".equals(data.time))
				//	System.out.println("here");
				MomentumDAO momentumChange = MomentumDetector.detect_momentum_change_by_close(quotes60, Constants.DIRECTION_UP,  trade.pullBackSetup.trendResumePos, lastbar60);
				if ( momentumChange != null ){
					triggerPrice = momentumChange.triggerPrice;
					double avgBarSizeL = BarUtil.averageBarSize(quotes240);
					//(tradeManager.inProfit(data) > avgBarSizeL)

					System.out.println("Momentum Changed detected after trend resume " + quotes60[lastbar60].time + " lastUpBegin:" + quotes60[momentumChange.lastUpBarBegin].time + " " + quotes60[momentumChange.lastUpBarBegin].low + " take profit and exit positions at " + triggerPrice);
					int positionSizeTobeClosed = trade.remainingPositionSize;//-POSITION_SIZE;
					if (/*(inProfit(trade, triggerPrice) > avgBarSizeL) &&*/ (positionSizeTobeClosed > 0 ) && !trade.findTargetPosition(triggerPrice)) {
						//tradeManager.createTradeTargetOrder(INCREMENT_POSITION_SIZE, triggerPrice);
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_SELL, positionSizeTobeClosed, triggerPrice);
						tradeManager.closePositionByMarket( positionSizeTobeClosed, triggerPrice);
						trade.pullBackSetup.momentumChangeAfterTrendResume = true;
						//trade.setStatus(Constants.STATUS_CLOSED);
					}
					return;
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				MomentumDAO momentumChange = MomentumDetector.detect_momentum_change_by_close(quotes60, Constants.DIRECTION_DOWN,  trade.pullBackSetup.trendResumePos, lastbar60);
				if ( momentumChange != null ){
					triggerPrice = momentumChange.triggerPrice;
					double avgBarSizeL = BarUtil.averageBarSize(quotes240);
					System.out.println("Momentum Changed detected after trend resume " + quotes60[lastbar60].time + "lastdownbarbegin:" + quotes60[momentumChange.lastDownBarBegin].time + " " + quotes60[momentumChange.lastDownBarBegin].high + " take profit and exit positions at " + triggerPrice);
					int positionSizeTobeClosed = trade.remainingPositionSize;//-POSITION_SIZE;
					if (/*(inProfit(trade, triggerPrice) > avgBarSizeL) &&*/ (positionSizeTobeClosed > 0 ) && !trade.findTargetPosition(triggerPrice)) {
						//tradeManager.createTradeTargetOrder(INCREMENT_POSITION_SIZE, triggerPrice);
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, positionSizeTobeClosed, triggerPrice);
						tradeManager.closePositionByMarket( positionSizeTobeClosed, triggerPrice);
						trade.pullBackSetup.momentumChangeAfterTrendResume = true;
						//trade.setStatus(Constants.STATUS_CLOSED);
					}
					return;
				}
			}
		
		
		}
		
		
		// unload positions
/*		int posSize = 0;
		while( positions[posSize] != null ){
			posSize++;
		}
		
		if ( posSize > 0 ){
			// last one
			TradePosition position = positions[posSize-1]; // last one
			if (!Constants.STATUS_CLOSED.equals( position.getStatus())){
				if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  2 * FIXED_STOP * PIP_SIZE )){
					double closePrice = position.price -  2 * FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
				else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  2 * FIXED_STOP * PIP_SIZE )){
					double closePrice = position.price +  2 * FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
			} 
			
			// all the ones before, other than the first 1
			Constants.STATUS_CLOSED.equals( position.getStatus());
			for ( int i = posSize-2; i > 0; i--){
				position = positions[i];
				if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  FIXED_STOP * PIP_SIZE ) && !Constants.STATUS_CLOSED.equals(position.getStatus())){
					double closePrice = position.price -  FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
				else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  FIXED_STOP * PIP_SIZE )&& !Constants.STATUS_CLOSED.equals(position.getStatus())){
					double closePrice = position.price +  FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
			}
		}
	*/	
		
		// set stop

		
	}


	// should not last more than 3 bar
	public void track_PBK_entry_usingmomentum5(QuoteData data, TradeManagerInf tradeManager)
	{
		QuoteData[] quotes240 = tradeManager.getInstrument().getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes15 = tradeManager.getInstrument().getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;

		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		int INCREMENT_POSITION_SIZE = POSITION_SIZE/2;
		Trade trade = tradeManager.getTrade();
		double triggerPrice;
	
		//System.out.println("First BreakOut StartTime " + trade.getFirstBreakOutStartTime());
		int pushStart240 = Utility.findPositionByHour(quotes240, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		int pushStart60 = Utility.findPositionByHour(quotes60, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		int detectTime60 = Utility.findPositionByHour(quotes60, trade.detectTime, Constants.BACK_TO_FRONT );
		
		
		int pullBackStartPoint = 0;
		if (Constants.ACTION_BUY.equals(trade.action)){
			int highestPoint = 0;
			if ( pushStart60 == -1 ) // sometimes 240-60 can not be exactly mapped
				highestPoint = Utility.getHigh(quotes60, detectTime60-9, detectTime60-1).pos;
			else	
				highestPoint = Utility.getHigh(quotes60, pushStart60, detectTime60-1).pos;
			
			pullBackStartPoint = highestPoint;
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			int lowestPoint = 0;
			if ( pushStart60 == -1 ) // sometimes 240-60 can not be exactly mapped
				lowestPoint = Utility.getLow(quotes60, detectTime60-9, detectTime60-1).pos;
			else
				lowestPoint = Utility.getLow(quotes60, pushStart60, detectTime60-1).pos;
			pullBackStartPoint = lowestPoint;
		}
		
			
		/************************************
		 *  check momentum change and additional position after momentum
		 ************************************/
		if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_CLOSED.equals(trade.status))){
			if ( lastbar240 > pushStart240+3 ){
				tradeManager.removeTrade();
				return;
			}						
			if (Constants.ACTION_BUY.equals(trade.action)){
				if ( quotes240[lastbar240].high > quotes240[lastbar240-1].high ){
					triggerPrice = quotes240[lastbar240-1].high;
					if ( quotes15[lastbar15-1].high > triggerPrice){
						triggerPrice = data.close;
						//tradeManager.removeTrade();
						//return;
					}
					System.out.println("buy triggered at " + triggerPrice + " " + quotes240[lastbar240].time);
					if ( trade.stop == 0 )
						trade.stop = triggerPrice - FIXED_STOP * PIP_SIZE;
					tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);
					trade.status = Constants.STATUS_FILLED;  // can still add final positions
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				if ( quotes240[lastbar240].low < quotes240[lastbar240-1].low ){
					triggerPrice = quotes240[lastbar240-1].low;
					if ( quotes15[lastbar15-1].low < triggerPrice){
						triggerPrice = data.close;
						//tradeManager.removeTrade();
						//return;
					}
					System.out.println("sell triggered at " + triggerPrice + " " + quotes240[lastbar240].time);
					if ( trade.stop == 0 )
						trade.stop = triggerPrice + FIXED_STOP * PIP_SIZE;
					tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);
					trade.status = Constants.STATUS_FILLED;  // can still add final positions
				}
			}
		}
	}

	public void track_PBK_entry_using_20ma(QuoteData data, TradeManagerInf tradeManager)
	{
		QuoteData[] quotes240 = tradeManager.getInstrument().getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		//QuoteData[] quotes15 = tradeManager.getInstrument().getQuoteData(Constants.CHART_15_MIN);
		//int lastbar15 = quotes15.length - 1;

		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		int INCREMENT_POSITION_SIZE = POSITION_SIZE/2;
		Trade trade = tradeManager.getTrade();
		double triggerPrice;
	
		//System.out.println("First BreakOut StartTime " + trade.getFirstBreakOutStartTime());
		int pushStart240 = Utility.findPositionByHour(quotes240, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		int pushStart60 = Utility.findPositionByHour(quotes60, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		int detectTime60 = Utility.findPositionByHour(quotes60, trade.detectTime, Constants.BACK_TO_FRONT );
		
		PushHighLow lastPushL = trade.phl;
		logger.info("Last PushL starts:" + lastPushL.pullBack.time + " ends:" + quotes240[lastPushL.curPos].time );
		
		int pullBackStartPoint = 0;
		if (Constants.ACTION_BUY.equals(trade.action)){
			int highestPoint = 0;
			if ( pushStart60 == -1 ) // sometimes 240-60 can not be exactly mapped
				highestPoint = Utility.getHigh(quotes60, detectTime60-9, detectTime60-1).pos;
			else	
				highestPoint = Utility.getHigh(quotes60, pushStart60, detectTime60-1).pos;
			
			pullBackStartPoint = highestPoint;
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			int lowestPoint = 0;
			if ( pushStart60 == -1 ) // sometimes 240-60 can not be exactly mapped
				lowestPoint = Utility.getLow(quotes60, detectTime60-9, detectTime60-1).pos;
			else
				lowestPoint = Utility.getLow(quotes60, pushStart60, detectTime60-1).pos;
			pullBackStartPoint = lowestPoint;
		}
		
			
		/************************************
		 *  check momentum change and additional position after momentum
		 ************************************/
		if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_CLOSED.equals(trade.status))){
			if (Constants.ACTION_BUY.equals(trade.action)){
				if (BarUtil.isUpBar(quotes60[lastbar60-1]) && (quotes60[lastbar60].high > quotes60[lastbar60-1].high)){ 

					triggerPrice = quotes60[lastbar60-1].high;

					double[] ema20 = Indicator.calculateEMA(quotes60, 20);
					int below20MA = 0;
					boolean touch20MA = false;
					for ( int i = pushStart60; i<lastbar60; i++ ){
						if ( quotes60[i].high < ema20[i] )
							below20MA++;
						if (quotes60[i].low < ema20[i] )
							touch20MA = true;
					}
					
					if ((touch20MA == false) || ( below20MA > 2)){ 
						tradeManager.removeTrade();
						return;
					}
					
					System.out.println("buy triggered at " + triggerPrice + " " + quotes240[lastbar240].time);
					if ( trade.stop == 0 )
						trade.stop = triggerPrice - FIXED_STOP * PIP_SIZE;
					tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);
					trade.status = Constants.STATUS_FILLED;  // can still add final positions
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){

				if (BarUtil.isDownBar(quotes60[lastbar60-1]) && (quotes60[lastbar60].low < quotes60[lastbar60-1].low)){ 

					triggerPrice = quotes60[lastbar60-1].low;

					double[] ema20 = Indicator.calculateEMA(quotes60, 20);
					int above20MA = 0;
					boolean touch20MA = false;
					for ( int i = pushStart60; i<lastbar60; i++ ){
						if ( quotes60[i].low > ema20[i] )
							above20MA++;
						if (quotes60[i].high > ema20[i] )
							touch20MA = true;
					}
					
					if ((touch20MA == false) || ( above20MA > 2)){ 
						tradeManager.removeTrade();
						return;
					}

				
					System.out.println("sell triggered at " + triggerPrice + " " + quotes240[lastbar240].time);
					if ( trade.stop == 0 )
						trade.stop = triggerPrice + FIXED_STOP * PIP_SIZE;
					tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);
					trade.status = Constants.STATUS_FILLED;  // can still add final positions
				}
			}
		}
	}


	
	
	
	
	
	
	double inProfit(Trade trade, double triggerPrice)
	{
		if (Constants.ACTION_BUY.equals(trade.action))
		{
			return ( triggerPrice - trade.entryPrice ); 
		}
		else if (Constants.ACTION_SELL.equals(trade.action))
		{
			return ( trade.entryPrice - triggerPrice ); 
		}
		else
			return 0;
		
	}

	
	public static void track_PBK_entry_using_everything(QuoteData data, TradeManager2 tradeManager)
	{
		QuoteData[] quotesL = tradeManager.getInstrument().getQuoteData(Constants.CHART_240_MIN);
		QuoteData[] quotes = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		QuoteData[] quotesS = tradeManager.getInstrument().getQuoteData(Constants.CHART_15_MIN);
		int lastbarL = quotesL.length - 1;
		int lastbar = quotes.length - 1;
		int lastbarS = quotesS.length - 1;
		double[] ema = Indicator.calculateEMA(quotes, 20);
		double triggerPrice;
		
		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		Trade trade = tradeManager.getTrade();
		int INCREMENT_POSITION_SIZE = POSITION_SIZE/2;
		
		
		// start point 
		int startPos = 0;
		if (Constants.ACTION_BUY.equals(trade.action)){
			startPos = Utility.findPositionByHour(quotes, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startPos = Utility.getHigh( quotes, startPos-4<0?0:startPos-4, lastbar-1).pos;
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			startPos = Utility.findPositionByHour(quotes, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startPos = Utility.getLow( quotes, startPos-4<0?0:startPos-4, lastbar-1).pos;
		}
		
		// check peak high entries && HH_LL entries
		PushList pushListHHLL = null;
		if (Constants.ACTION_BUY.equals(trade.action)){
			pushListHHLL = PushSetup.findPastLowsByX(quotes, startPos, lastbar, 2);
			if ((pushListHHLL != null ) &&( pushListHHLL.phls != null ) && ( pushListHHLL.phls.length > 0)){
				int pushSize = pushListHHLL.phls.length;
				PushHighLow lastPush = pushListHHLL.phls[pushSize - 1];
				
				if ( data.high > lastPush.pullBack.high ){
					triggerPrice = lastPush.pullBack.high;
					if ( quotesS[lastbarS-1].high > triggerPrice ){
						tradeManager.removeTrade();
						return;
					}

					//warning("Trade entry_HH_LL: Trade buy entered at " + data.time + " " + trade.entryPrice);
					int positionSize = POSITION_SIZE - trade.remainingPositionSize;
			    	//enterMarketPosition(trade.entryPrice);
					tradeManager.enterMarketPositionMulti(triggerPrice,positionSize);
					return;
				}
			}
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			pushListHHLL = PushSetup.findPastHighsByX(quotes, startPos, lastbar, 2);
			if ((pushListHHLL != null ) &&( pushListHHLL.phls != null ) && ( pushListHHLL.phls.length > 0)){
				int pushSize = pushListHHLL.phls.length;
				PushHighLow lastPush = pushListHHLL.phls[pushSize - 1];
				
				if ( data.low > lastPush.pullBack.low ){
					triggerPrice = lastPush.pullBack.low;
					if ( quotesS[lastbarS-1].low < triggerPrice ){
						tradeManager.removeTrade();
						return;
					}

					//warning("Trade entry_HH_LL: Trade buy entered at " + data.time + " " + trade.entryPrice);
					int positionSize = POSITION_SIZE - trade.remainingPositionSize;
			    	//enterMarketPosition(trade.entryPrice);
					tradeManager.enterMarketPositionMulti(triggerPrice,positionSize);
					return;
				}
			}
		}
		
		// check fall out of bottom entry
		if (Constants.ACTION_BUY.equals(trade.action)){
			triggerPrice = quotes[startPos].high;
			if ( quotesS[lastbarS-1].high > triggerPrice ){
				tradeManager.removeTrade();
				return;
			}
			int positionSize = POSITION_SIZE - trade.remainingPositionSize;
			tradeManager.enterMarketPositionMulti(triggerPrice,positionSize);
			return;
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			triggerPrice = quotes[startPos].low;
			if ( quotesS[lastbarS-1].low < triggerPrice ){
				tradeManager.removeTrade();
				return;
			}
			int positionSize = POSITION_SIZE - trade.remainingPositionSize;
			tradeManager.enterMarketPositionMulti(triggerPrice,positionSize);
			return;
		}	
		
		
		/************************************
		 *  check momentum change and additional position after momentum
		 ************************************/
		/*
		if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_CLOSED.equals(trade.status))){
			if ( trade.pullBackSetup.momentumChangePoint == 0 ){
				if (Constants.ACTION_BUY.equals(trade.action)){
					MomentumDAO momentumChange = MomentumSetup.detect_momentum_change_by_close(quotes, Constants.DIRECTION_DOWN,  startPos, lastbar);
					if ( momentumChange != null ){
						trade.pullBackSetup.momentumChangePoint = momentumChange.momentumChangePoint;
						System.out.println("Momentum Change BUY triggered " + momentumChange.triggerPrice + " " + data.time + " lastDownBegin:" + quotes[momentumChange.lastDownBarBegin].time + " " + quotes[momentumChange.lastDownBarBegin].high + " pullbackStart:"+ quotes[startPos].time);
						triggerPrice = momentumChange.triggerPrice;
						if ( trade.stop == 0 )
							trade.stop = triggerPrice - 2 * FIXED_STOP * PIP_SIZE;
						tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);
					}
				}
				else if (Constants.ACTION_SELL.equals(trade.action)){
					MomentumDAO momentumChange = MomentumSetup.detect_momentum_change_by_close(quotes, Constants.DIRECTION_UP,  startPos, lastbar);
					if ( momentumChange != null ){
						trade.pullBackSetup.momentumChangePoint = momentumChange.momentumChangePoint;
						System.out.println("Momentum Change SELL triggered " + momentumChange.triggerPrice + " " + data.time + " lastUpBegin:" + quotes[momentumChange.lastUpBarBegin].time + " " + quotes[momentumChange.lastUpBarBegin].low + " pullbackStart:"+ quotes[startPos].time);
						triggerPrice = momentumChange.triggerPrice;
						if ( trade.stop == 0 )
							trade.stop = triggerPrice + 2 * FIXED_STOP * PIP_SIZE;
						tradeManager.enterIncrementalMarketPosition(triggerPrice,POSITION_SIZE, data.time);
					}
				}
			}else{
				if (Constants.ACTION_BUY.equals(trade.action)){
					//PushList push1List = PushSetup.findPast1Lows1(quotes, startPos, lastbar60);
					PushList push1List = PushSetup.findPastLowsByX(quotes,  startPos,  lastbar,  1 );
					if (( push1List != null ) && ( push1List.getNumOfPushes() > 0 )){
						PushHighLow lastPush = push1List.getLastPush();
						triggerPrice = quotes[lastPush.prePos].low;
						if (!trade.findFilledPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
						//if (!trade.findLimitPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
							System.out.println("Add additonal positon after Momentum Change BUY triggered " + triggerPrice + " " + data.time + " lastpeak:" + quotes[lastPush.prePos].time + " " + quotes[lastPush.prePos].low);
							//tradeManager.enterLimitPositionMulti(INCREMENT_POSITION_SIZE, triggerPrice);
							tradeManager.enterIncrementalMarketPosition(triggerPrice, INCREMENT_POSITION_SIZE, data.time);
							trade.pullBackSetup.numOfAdditionalPosition++;
							
							if ((trade.stop == 0 )&&( trade.pullBackSetup.numOfAdditionalPosition == 2 )){
								trade.stop = triggerPrice- FIXED_STOP*PIP_SIZE;
								System.out.println("set stop after 2 addtional positions: Stop set at " + trade.stop);
								tradeManager.createStopOrder(trade.stop);
							}
								//trade.status = Constants.STATUS_FILLED;  // can still add final positions
							return;
						}

					}
				}else if (Constants.ACTION_SELL.equals(trade.action)){
					//PushList push1List = PushSetup.findPast1Highs1(quotes, startPos, lastbar);
					PushList push1List = PushSetup.findPastHighsByX(quotes,  startPos,  lastbar,  1 );
					if (( push1List != null ) && ( push1List.getNumOfPushes() > 0 )){
						PushHighLow lastPush = push1List.getLastPush();
						triggerPrice = quotes[lastPush.prePos].high;
						if (!trade.findFilledPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
						//if (!trade.findLimitPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
							System.out.println("Add additonal positon after Momentum Change SELL triggered " + triggerPrice + " " + data.time + " lastpeak:" + quotes[lastPush.prePos].time + " " + quotes[lastPush.prePos].low);
							//tradeManager.enterLimitPositionMulti(INCREMENT_POSITION_SIZE, triggerPrice);
							tradeManager.enterIncrementalMarketPosition(triggerPrice, INCREMENT_POSITION_SIZE, data.time);
							trade.pullBackSetup.numOfAdditionalPosition++;

							if ((trade.stop == 0 )&&( trade.pullBackSetup.numOfAdditionalPosition == 2 )){
								trade.stop = triggerPrice + FIXED_STOP*PIP_SIZE;
								System.out.println("set stop after 2 addtional positions: Stop set at " + trade.stop);
								tradeManager.createStopOrder(trade.stop);
							}
						}
					}
				}
			}
		

		}

		
		
			/*************************************************
			// set stop and take profits
			// set stop by trend resume
			 * */
			/*
			if ((trade.pullBackSetup.momentumChangeDetected) && ( trade.pullBackSetup.trendResumePos == 0 )){
				MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes, Constants.DIRECTION_DOWN, lastbar-1);
				if ( mom != null ){
					System.out.println("Trend resume detected at " + quotes[lastbar].time);
					trade.pullBackSetup.trendResumePos = mom.startPos;
					// calculate stop;
				}
			}*/
			
		/*************************************************
		// detect trend resume
		 *************************************************/
		/*
		if (( trade.pullBackSetup.trendResumePos == 0 ) && ((trade.pullBackSetup.momentumChangePoint > 0 ) || Constants.STATUS_FILLED.equals(trade.status))){
			if (Constants.ACTION_BUY.equals(trade.action)){
				if ( BarUtil.isUpBar(quotes[lastbar-1])){
					int upBarEnd = lastbar-1;
					MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes, Constants.DIRECTION_UP, upBarEnd);//upBarBegin-1);
					if ( mom != null ){
						int downBarEnd = mom.startPos-1;
						int downBarBegin = downBarEnd;
						while (BarUtil.isDownBar(quotes[downBarBegin-1]))
							downBarBegin--;
						
						//double startPrice = quotes240[pushStart240].high;gg

						if (( quotes[mom.endPos].close > quotes[downBarBegin].high ) && ( mom.startPos > (trade.pullBackSetup.momentumChangePoint+3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				if ( BarUtil.isDownBar(quotes[lastbar-1])){
					int downBarEnd = lastbar-1;
					MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes, Constants.DIRECTION_DOWN, downBarEnd);//upBarBegin-1);
					if ( mom != null ){
						int upBarEnd = mom.startPos-1;
						int upBarBegin = upBarEnd;
						while (BarUtil.isUpBar(quotes[upBarBegin-1]))
							upBarBegin--;
						if (( quotes[mom.endPos].close < quotes[upBarBegin].low ) && ( mom.startPos > (trade.pullBackSetup.momentumChangePoint+3))){
							trade.pullBackSetup.trendResumePos = mom.startPos;
							System.out.println("Trend resume detected at " + quotes[trade.pullBackSetup.trendResumePos].time);
						}
					}
				}
			}
		}*/
		}
}
