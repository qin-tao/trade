package tao.trading.strategy.tm;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import tao.trading.AlertOption;
import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.Instrument;
import tao.trading.MACD;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.PositionToMA;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.TradePosition;
import tao.trading.dao.DiverageDAO;
import tao.trading.dao.MABreakOutList;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.setup.BarTrendDetector;
import tao.trading.setup.BreakHHLL;
import tao.trading.setup.BreakHHLLDto;
import tao.trading.setup.DiverageSetup;
import tao.trading.setup.DiverageWithWeakBreakOut;
import tao.trading.setup.DiverageWithWeakBreakOutDto;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.PushSetup;
import tao.trading.setup.ReturnToMASetup;
import tao.trading.setup.ReturnToMASetupDto;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;

import com.ib.client.EClientSocket;

public class RV_Diverage extends TradeManager2
{
	boolean CNT = false;;
	double minTarget1, minTarget2;
	HashMap<Integer, Integer> profitTake = new HashMap<Integer, Integer>();
	String currentTime;
	int DEFAULT_RETRY = 0;
	int DEFAULT_PROFIT_TARGET = 400;
	QuoteData currQuoteData, lastQuoteData;
//	int firstRealTimeDataPosL = 0;
	double lastTick_bid, lastTick_ask, lastTick_last;
	int lastTick;
	long firstRealTime = 0;
	int bigChartState;
	
	// important switch
	boolean prremptive_limit = false;
	boolean breakout_limit = false;
	boolean reverse_trade = false; // reverse after stopped out
	boolean rsc_trade = false; // do a rsc trade instead of rst if there is
								// consective 3 bars
	boolean reverse_after_profit = false; // reverse after there is a profit
	boolean after_target_reversal = false;
	
	boolean market_order = true;
	String checkPullbackSetup_start_date = null;
	boolean trade_detect_email_notifiy = true;
	HashSet <String>pivots_set = new HashSet <String>();

	/********************************
	 *  reverse trade setting
	 *******************************/
	boolean average_up = false;
	boolean tip_reverse = true;
	boolean stop_reverse = true;

	public RV_Diverage()
	{
		super();
	}
	
	public RV_Diverage(String ib_account, EClientSocket m_client, int symbol_id, Instrument instrument, Strategy stragety, HashMap<String, Double> exchangeRate )
	{
		super(ib_account, m_client, instrument.getContract(), symbol_id);
		this.IB_ACCOUNT = ib_account;
		this.instrument = instrument;
		this.symbol = instrument.getSymbol();
	   	this.PIP_SIZE = instrument.getPIP_SIZE();
	   	this.exchangeRate = exchangeRate.get(instrument.getContract().m_currency);
	   	this.currency = instrument.getContract().m_currency;
	   	this.strategy = stragety;
	   	this.MODE = strategy.mode;
	   	this.logger = strategy.logger;
	}

	
	
	/*****************************************************************************************************************************
	 * 
	 * 
	 * Static Methods
	 * 
	 * 
	 *****************************************************************************************************************************/
	public void process( int req_id, QuoteData data )
	{
		//if ( req_id != 501 )  //500
		  //return;
		
		if (req_id == getInstrument().getId_realtime())
		{

			//checkReturnMA_DiverageWithWeakBreakOut2(data,20);
			//checkDiverageSignal_closestDiverage( data, 20);  // this to be test
			
			//if (( trade == null) || ((trade != null) && ( trade.status.equals(Constants.STATUS_DETECTED))))
				//checkDiverageSignal_closestDiverage(data,Constants.CHART_240_MIN, 20, 0);  // this works, find average bar diverage and fade it
				//checkDiverageSignal_closestDiverage(data,Constants.CHART_15_MIN, 20,  PIP_SIZE * FIXED_STOP);  // this works, find average bar diverage and fade it
			
			if (( trade == null) || ((trade != null) && trade.status.equals(Constants.STATUS_DETECTED)))
				//checkDiverageSignal_breakouts(data,Constants.CHART_60_MIN, 20);  // this works, find small pullback breakouts and enter
				checkDiverageSignal_givenBigTrend(data,Constants.CHART_60_MIN, 20);

			//if (( trade == null) || ((trade != null) && trade.status.equals(Constants.STATUS_DETECTED)))
			//	checkDiverageSignal_againstTrendDiverage(data,20);  
			
			
			//if (( trade == null) || ((trade != null) && trade.status.equals(Constants.STATUS_DETECTED)))
			//		checkPullBackTrend_PinBarDiverage(data,20);  // this works

				//checkReturnMA_BreakHHLL(data,20); 
			
			
			
			if (( trade != null) && (trade.status.equals(Constants.STATUS_DETECTED) || trade.status.equals(Constants.STATUS_FILLED))){
				manage(data );
			}

			
			return;
		}
	}
	
	
	
	
	
	
	/***********************************************************************************************************************
	 * 
	 * 
	 *	Standard Interface 
	 * 
	 * 
	 ************************************************************************************************************************/
	
    public void run() {
    }

	public Trade detect( QuoteData data )
	{
		//return checkREVTradeSetup2(data, 20);
		return checkREVTradeSetup3(data);
	}


	private Trade on_Diverage_Up(QuoteData data, QuoteData[] quotes, double[] ema )
	{
		int lastbar = quotes.length - 1;

		if (quotes[lastbar].high > ema[lastbar])
		{
			int pushStart = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, lastbar, 2);
			if (pushStart == Constants.NOT_FOUND)
				pushStart = 2;
			pushStart = Utility.getLow( quotes, pushStart-2, pushStart).pos;
			
			PushList pushList = Pattern.findPast2Highs(quotes, pushStart, lastbar );
			if ( pushList == null )
				return null;
			
			PushHighLow[] phls = pushList.phls;
			if (( phls != null ) && ( phls.length > 0 ))
			{
				// 1.  at least three with MCAD diverage
				// wide range, requires a MACD diverage
				PushHighLow phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotes );
				int negatives = 0;
				int touch20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++)
				{
					if ( macd[j].histogram < 0 )
						negatives ++;
					if ( quotes[j].low < ema[j])
						touch20MA ++;
				}

				/*
				boolean pushDetected = false;
				double avgSize = BarUtil.averageBarSize(quotesL);
				for ( int j = phl.curPos-1; j > phl.prePos; j-- )
				{
					ConsectiveBars consecDown = Utility.findLastConsectiveDownBars( quotesL, phl.prePos, j);
					if ( consecDown != null )
					{
						double consecDownSize = quotesL[consecDown.getBegin()].high - quotesL[consecDown.getEnd()].low;
						int consectDownBarNum = consecDown.getSize();
						//System.out.println(consectDownBarNum + " " +  consecDownSize/avgSize);
						
						if ((( consecDown.getSize() == 1 ) && ( consecDownSize > 1.5 * avgSize ))
						  ||(( consecDown.getSize() == 2 ) && ( consecDownSize > 3 * avgSize ))
						  ||(( consecDown.getSize() == 3 ) && ( consecDownSize > 4 * avgSize ))
						  ||(( consecDown.getSize() == 4 ) && ( consecDownSize > consecDown.getSize() * 1.5 * avgSize )))
						{	
							pushDetected = true;
							break;
						}
					}
				}*/
				
				
				
				
				double pullBackDist = quotes[phls[0].prePos].high - phls[0].pullBack.low;
				int pullBackSize = (int)(pullBackDist/PIP_SIZE);
				if (( negatives > 0 ) || (pullBackDist > 2.5 * FIXED_STOP * PIP_SIZE )){
					if ( findTradeHistory( Constants.ACTION_SELL, quotes[phl.prePos].time) != null )
						return null;
					
					logger.warning(symbol + " SELL detected " + data.time + " pullback break is " );

					warning("sell detected 1 " + data.time + " push start:" + ((QuoteData)quotes[pushStart]).time);
		    	    System.out.println(symbol + " " + 
							 ((QuoteData)quotes[ phl.prePos]).time + "@" + ((QuoteData)quotes[ phl.prePos]).high + "  -  " +
							 ((QuoteData)quotes[ phl.curPos]).time + "@" + ((QuoteData)quotes[ phl.curPos]).high + 
							 " pullback@" + phl.pullBack.time);

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.setFirstBreakOutStartTime(quotes[phl.prePos].time);// for history lookup only
					trade.pullBackStartTime = quotes[pushStart].time;

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes[lastbar].time);
					tv.addNameValue("PBSize", new Integer(pullBackSize).toString());
					tv.addNameValue("detail", (((QuoteData)quotes[ phl.prePos]).time + "@" + ((QuoteData)quotes[ phl.prePos]).high + "  -  " +
							 ((QuoteData)quotes[ phl.curPos]).time + "@" + ((QuoteData)quotes[ phl.curPos]).high + 
							 " pullback@" + phl.pullBack.time));
					trade.addTradeEvent(tv);

					return trade;
				}
			}
		}
		else if (quotes[lastbar].low < ema[lastbar])
		{
			int pushStartL = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, lastbar, 2);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 2;
			
			pushStartL = Utility.getHigh( quotes, pushStartL-2, pushStartL).pos;

			PushList pushList = Pattern.findPast2Lows(quotes, pushStartL, lastbar );
			if ( pushList == null )
				return null;
			
			PushHighLow[] phls = pushList.phls;
			if (( phls != null ) && (phls.length > 0))
			{
				/*
				for ( int i = 0; i < phls.length; i++)
				{
		    	    System.out.println(symbol + " " + CHART + " last pull back " + i + "    " +
					 ((QuoteData)quotes[ phls[i].prePos]).time + "@" + ((QuoteData)quotes[ phls[i].prePos]).high + "  -  " +
					 ((QuoteData)quotes[ phls[i].curPos]).time + "@" + ((QuoteData)quotes[ phls[i].curPos]).high + 
					 " pullback@" + phls[i].pullBack.time);
				}*/
				
				// 1.  at least three with MCAD diverage
				
				// wide range, requires a MACD diverage
				PushHighLow phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotes );
				int positive = 0;
				int touch20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++)
				{
					if ( macd[j].histogram > 0 )
						positive ++;
					if ( quotes[j].high > ema[j])
						touch20MA ++;
				}

				/*
				boolean pushDetected = false;
				double avgSize = BarUtil.averageBarSize(quotesL);
				for ( int j = phl.curPos-1; j > phl.prePos; j-- )
				{
					ConsectiveBars consecUp = Utility.findLastConsectiveUpBars( quotesL, phl.prePos, j);
					if ( consecUp != null )
					{
						double consecUpSize = quotesL[consecUp.getEnd()].high - quotesL[consecUp.getBegin()].low;
						int consectUpBarNum = consecUp.getSize();
						
						if ((( consecUp.getSize() == 1 ) && ( consecUpSize > 1.5 * avgSize ))
						  ||(( consecUp.getSize() == 2 ) && ( consecUpSize > 3 * avgSize ))
						  ||(( consecUp.getSize() == 3 ) && ( consecUpSize > 4 * avgSize ))
						  ||(( consecUp.getSize() == 4 ) && ( consecUpSize > consecUp.getSize() * 1.5 * avgSize )))
						{	
							pushDetected = true;
							break;
						}
					}
				}*/
				
				double pullBackDist = phls[0].pullBack.high - quotes[phls[0].prePos].low ;
				int pullBackSize = (int)(pullBackDist/PIP_SIZE);
				if (( positive > 0 ) || (pullBackDist > 2.5 * FIXED_STOP * PIP_SIZE ))
				//if ( pushDetected )
				{
					if ( findTradeHistory( Constants.ACTION_BUY, quotes[phl.prePos].time) != null )
						return null;

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.setFirstBreakOutStartTime(quotes[phl.prePos].time);// for history lookup only
					trade.pullBackStartTime = quotes[pushStartL].time;


					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes[lastbar].time);
					tv.addNameValue("PBSize", new Integer(pullBackSize).toString());
					tv.addNameValue("detail", (((QuoteData)quotes[ phl.prePos]).time + "@" + ((QuoteData)quotes[ phl.prePos]).low + "  -  " +
							 ((QuoteData)quotes[ phl.curPos]).time + "@" + ((QuoteData)quotes[ phl.curPos]).low + 
							 " pullback@" + phl.pullBack.time));
					trade.addTradeEvent(tv);

					return trade;
				}
			}
		}
		return null;
	}

	
	
	public Trade checkReturnMA_DiverageWithWeakBreakOut(QuoteData data, int ma)
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		
		DiverageWithWeakBreakOutDto weakBreakOutSetup = null;
		if ( (weakBreakOutSetup = DiverageWithWeakBreakOut.detect(data, quotes60, FIXED_STOP, PIP_SIZE )) != null ){
			QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
			ReturnToMASetupDto returnToMASetup;
			if ( (returnToMASetup = ReturnToMASetup.detect(data, quotes240)) != null ){
				if  ( returnToMASetup != null ){
					if (weakBreakOutSetup.action.equals(Constants.ACTION_BUY) && returnToMASetup.action.equals(Constants.ACTION_BUY)){
						warning("weak breakout buy detected " + data.time + weakBreakOutSetup.toString(quotes60) );

						if ( findTradeHistory( Constants.ACTION_BUY, quotes60[weakBreakOutSetup.prevPeakPos].time) != null )
							return null;
						
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotes60[lastbar60].time;
						trade.setFirstBreakOutStartTime(quotes60[weakBreakOutSetup.prevPeakPos].time);// for history lookup only
						double triggerPrice = data.close;
						enterMarketPosition(triggerPrice);
						return trade;
					}
					else if (weakBreakOutSetup.action.equals(Constants.ACTION_SELL) && returnToMASetup.action.equals(Constants.ACTION_SELL)){
						warning("weak breakout sell detected " + data.time + weakBreakOutSetup.toString(quotes60) );
						if ( findTradeHistory( Constants.ACTION_SELL, quotes60[weakBreakOutSetup.prevPeakPos].time) != null )
							return null;
						
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotes60[lastbar60].time;
						trade.setFirstBreakOutStartTime(quotes60[weakBreakOutSetup.prevPeakPos].time);// for history lookup only
						double triggerPrice = data.close;
						enterMarketPosition(triggerPrice);
						return trade;
					}
				}
			}
		}
		return null;
	}


	public Trade checkPullBackTrend_PinBarDiverage(QuoteData data, int ma)
	{
	//	System.out.println( data.toString() );
		//if ("20160530  21:23:00".equals(data.time.toString())){
		//	System.out.println("here");
		//}

		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		DiverageWithWeakBreakOutDto weakBreakOutSetup = null;
		//if ((weakBreakOutSetup = DiverageWithWeakBreakOut.detectWithMACD(data, quotes60, FIXED_STOP, PIP_SIZE )) != null ){
		if ((weakBreakOutSetup = DiverageWithWeakBreakOut.detectWithPinBar(data, quotes60, FIXED_STOP, PIP_SIZE )) != null ){
			BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes60);
			//if (weakBreakOutSetup.action.equals(Constants.ACTION_BUY)) { 
			if (weakBreakOutSetup.action.equals(Constants.ACTION_BUY) && 
					(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))){
				warning("weak breakout buy detected " + data.toString() + " " + weakBreakOutSetup.toString(quotes60) );

				if ( findTradeHistory( Constants.ACTION_BUY, quotes60[weakBreakOutSetup.prevPeakPos].time) != null )
					return null;
				
				createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes60[lastbar60].time;
				trade.setFirstBreakOutStartTime(quotes60[weakBreakOutSetup.prevPeakPos].time);// for history lookup only
				double triggerPrice = weakBreakOutSetup.triggerPrice;
				//double triggerPrice = quotes60[weakBreakOutSetup.prevPeakPos].low;
				enterMarketPosition(triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, trade.symbol + " " + strategy.IB_PORT + " pin bar RV 60 " + trade.action + " " + trade.triggerPrice, trade);
				//enterLimitPosition1(triggerPrice, trade.POSITION_SIZE);
				//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
				return trade;
				//removeTrade();
				//return null;

			}
			//else if (weakBreakOutSetup.action.equals(Constants.ACTION_SELL)) {
			else if (weakBreakOutSetup.action.equals(Constants.ACTION_SELL) &&
					(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))){
				warning("weak breakout sell detected " + data.toString() + " " +  weakBreakOutSetup.toString(quotes60) );
				if ( findTradeHistory( Constants.ACTION_SELL, quotes60[weakBreakOutSetup.prevPeakPos].time) != null )
					return null;
				
				createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes60[lastbar60].time;
				trade.setFirstBreakOutStartTime(quotes60[weakBreakOutSetup.prevPeakPos].time);// for history lookup only
				//double triggerPrice = quotes60[weakBreakOutSetup.prevPeakPos].high;
				//double triggerPrice = data.close;
				double triggerPrice = weakBreakOutSetup.triggerPrice;
				enterMarketPosition(triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, trade.symbol + " " + strategy.IB_PORT + " pin bar RV 60 " + trade.action + " " + trade.triggerPrice, trade);
				//enterLimitPosition1(triggerPrice, trade.POSITION_SIZE);
				//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
				return trade;
				//removeTrade();
				//return null;
			}
		}
		return null;
	}



	
	
	
	
	public Trade checkPullBackTrend_DiverageSmallTimeFrame(QuoteData data, int ma)
	{
	//	System.out.println( data.toString() );
		//if ("20160530  21:23:00".equals(data.time.toString())){
		//	System.out.println("here");
		//}

		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		DiverageWithWeakBreakOutDto weakBreakOutSetup = null;
		if ((weakBreakOutSetup = DiverageWithWeakBreakOut.detectWithMACD(data, quotes60, FIXED_STOP, PIP_SIZE )) != null ){
			BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes60);
			if (weakBreakOutSetup.action.equals(Constants.ACTION_BUY) && 
					(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))){
				warning("weak breakout buy detected " + data.toString() + " " + weakBreakOutSetup.toString(quotes60) );

				if ( findTradeHistory( Constants.ACTION_BUY, quotes60[weakBreakOutSetup.prevPeakPos].time) != null )
					return null;
				
				createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes60[lastbar60].time;
				trade.setFirstBreakOutStartTime(quotes60[weakBreakOutSetup.prevPeakPos].time);// for history lookup only
				double triggerPrice = data.close;//quotes60[weakBreakOutSetup.prevPeakPos].low;
				//double triggerPrice = quotes60[weakBreakOutSetup.prevPeakPos].low;
				enterMarketPosition(triggerPrice);
				//enterLimitPosition1(triggerPrice, trade.POSITION_SIZE);
				//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
				return trade;
			}
			else if (weakBreakOutSetup.action.equals(Constants.ACTION_SELL) &&
					(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))){
				warning("weak breakout sell detected " + data.toString() + " " +  weakBreakOutSetup.toString(quotes60) );
				if ( findTradeHistory( Constants.ACTION_SELL, quotes60[weakBreakOutSetup.prevPeakPos].time) != null )
					return null;
				
				createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes60[lastbar60].time;
				trade.setFirstBreakOutStartTime(quotes60[weakBreakOutSetup.prevPeakPos].time);// for history lookup only
				//double triggerPrice = quotes60[weakBreakOutSetup.prevPeakPos].high;
				double triggerPrice = data.close;
				enterMarketPosition(triggerPrice);
				//enterLimitPosition1(triggerPrice, trade.POSITION_SIZE);
				//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
				return trade;
			}
		}
		return null;
	}

	
	
	public Trade checkReturnMA_DiverageWithWeakBreakOut2(QuoteData data, int ma)
	{
	//	System.out.println( data.toString() );
		if ("20160530  21:23:00".equals(data.time.toString())){
			System.out.println("here");
		}
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		ReturnToMASetupDto returnToMASetup;
		if ( (returnToMASetup = ReturnToMASetup.detect(data, quotes240)) != null ){
			QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
			int lastbar60 = quotes60.length - 1;
			DiverageWithWeakBreakOutDto weakBreakOutSetup = null;
			if ((weakBreakOutSetup = DiverageWithWeakBreakOut.detectWithMACD(data, quotes60, FIXED_STOP, PIP_SIZE )) != null ){
				if (weakBreakOutSetup.action.equals(Constants.ACTION_BUY) && returnToMASetup.action.equals(Constants.ACTION_BUY)){
					warning("weak breakout buy detected " + data.toString() + " " + weakBreakOutSetup.toString(quotes60) );

					if ( findTradeHistory( Constants.ACTION_BUY, quotes60[weakBreakOutSetup.prevPeakPos].time) != null )
						return null;
					
					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes60[lastbar60].time;
					trade.setFirstBreakOutStartTime(quotes60[weakBreakOutSetup.prevPeakPos].time);// for history lookup only
					double triggerPrice = data.close;//quotes60[weakBreakOutSetup.prevPeakPos].low;
					//double triggerPrice = quotes60[weakBreakOutSetup.prevPeakPos].low;
					enterMarketPosition(triggerPrice);
					//enterLimitPosition1(triggerPrice, trade.POSITION_SIZE);
					//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
					return trade;
				}
				else if (weakBreakOutSetup.action.equals(Constants.ACTION_SELL) && returnToMASetup.action.equals(Constants.ACTION_SELL)){
					warning("weak breakout sell detected " + data.toString() + " " +  weakBreakOutSetup.toString(quotes60) );
					if ( findTradeHistory( Constants.ACTION_SELL, quotes60[weakBreakOutSetup.prevPeakPos].time) != null )
						return null;
					
					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes60[lastbar60].time;
					trade.setFirstBreakOutStartTime(quotes60[weakBreakOutSetup.prevPeakPos].time);// for history lookup only
					//double triggerPrice = quotes60[weakBreakOutSetup.prevPeakPos].high;
					double triggerPrice = data.close;
					enterMarketPosition(triggerPrice);
					//enterLimitPosition1(triggerPrice, trade.POSITION_SIZE);
					//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
					return trade;
				}
			}
		}
		return null;
	}


	
	
	public Trade checkReturnMA_BreakHHLL(QuoteData data, int ma)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		ReturnToMASetupDto returnToMASetup;
		if ( (returnToMASetup = ReturnToMASetup.detect(data, quotes240)) != null ){
			QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
			int lastbar60 = quotes60.length - 1;
			BreakHHLLDto setup2 = null;
			int startPos = -1;
			if ( Constants.ACTION_BUY.equals(returnToMASetup.action)){
				startPos = Utility.findPositionByHour(quotes60, returnToMASetup.pullBackStartTime, Constants.BACK_TO_FRONT );
				startPos = Utility.getHigh( quotes60, startPos-4<0?0:startPos-4, lastbar60-1).pos;
			
				if ((setup2 = BreakHHLL.detect(data, quotes60, Constants.ACTION_BUY, startPos)) != null ){
					warning("breaking HHLL buy detected " + data.time  );

					if ( findTradeHistory( Constants.ACTION_BUY, quotes60[startPos].time) != null )
						return null;
					
					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes60[lastbar60].time;
					trade.setFirstBreakOutStartTime(quotes60[startPos].time);// for history lookup only
					double triggerPrice = setup2.triggerPrice;
					enterMarketPosition(triggerPrice);
					return trade;
				}
			} else if ( Constants.ACTION_SELL.equals(returnToMASetup.action)){
				startPos = Utility.findPositionByHour(quotes60, returnToMASetup.pullBackStartTime, Constants.BACK_TO_FRONT );
				startPos = Utility.getLow( quotes60, startPos-4<0?0:startPos-4, lastbar60-1).pos;

				if ((setup2 = BreakHHLL.detect(data, quotes60, Constants.ACTION_SELL, startPos)) != null ){
					warning("breaking HHLL sell detected " + data.time  );

					if ( findTradeHistory( Constants.ACTION_SELL, quotes60[startPos].time) != null )
						return null;
					
					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes60[lastbar60].time;
					trade.setFirstBreakOutStartTime(quotes60[startPos].time);// for history lookup only
					double triggerPrice = setup2.triggerPrice;
					enterMarketPosition(triggerPrice);
					return trade;
				}
			}
		}
		return null;
	}

	
	
	
	

	public Trade checkDiverageSignal_closestDiverageWithWeakBreakOut(QuoteData data, int ma)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] emaL = Indicator.calculateEMA(quotesL, ma);

		if ((quotesL[lastbarL-2].high > emaL[lastbarL-2]) && (quotesL[lastbarL-2].high > quotesL[lastbarL-1].high )){
			for ( int i = lastbarL-4; i > lastbarL - 48 ; i--){
				DiverageDAO dv = DiverageSetup.findLastDiverageHigh(quotesL, i, lastbarL-2, 15, 100 *  PIP_SIZE); 
				if ( dv != null ){
		    		double breakOutDist = quotesL[dv.curPos].high - quotesL[dv.prePos].high;
		    		if ( breakOutDist < FIXED_STOP * PIP_SIZE/3 ){
		    			int gap = dv.curPos - dv.prePos + 1;
		    			double pullBackSize = dv.pullBackSize /PIP_SIZE;

		    		//if (( gap <= 12 ) && ( pullBackSize > 4 * FIXED_STOP * PIP_SIZE )){
		    		/*if ((( gap == 1 ) && ( pullBackSize > 80 )) ||  
		    		    (( gap == 2 ) && ( pullBackSize > 100 )) || 
		    		    (( gap >= 3 ) && ( pullBackSize > gap * 40 )))*/	

		    			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[dv.prePos].time) != null )
							return null;
						
						//logger.warning(symbol + " SELL detected " + data.time + " pullback break is " );
	
						warning("sell detected 1 " + data.time + " last high:" + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].high);
			    	    //System.out.println(symbol + " " + 
						//		 ((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
						//		 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
						//		 " pullback@" + phl.pullBack.time);
	
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[pushStartL].time;
	
						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
						tv.addNameValue("PBSize", new Integer((int)(dv.pullBackSize/PIP_SIZE)).toString());
						tv.addNameValue("detail", (quotesL[ dv.prePos]).time + "@" + quotesL[dv.prePos].high + "  -  " +
								 quotesL[ dv.curPos].time + "@" + quotesL[ dv.curPos].high + " pullback:" +(int)(dv.pullBackSize/PIP_SIZE));
						
						trade.addTradeEvent(tv);
						double triggerPrice = data.close;
						enterMarketPosition(triggerPrice);
						//super.strategy.sentTriggerEmail(strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice);
						super.strategy.sentAlertEmail(AlertOption.trigger, "Close Diverage 240 " + strategy.IB_PORT + " " + trade.symbol + " " + trade.action + " " + triggerPrice + " weak breakout triggered", trade);
	
						return trade;
		    		}
				}
			}
		}
		else if ((quotesL[lastbarL-2].low < emaL[lastbarL-2]) && (quotesL[lastbarL-2].low < quotesL[lastbarL-1].low )){
			for ( int i = lastbarL-4; i > lastbarL - 48 ; i--){
				DiverageDAO dv = DiverageSetup.findLastDiverageLow(quotesL, i, lastbarL-2, 15, 100 * PIP_SIZE);
				if ( dv != null ) {
		    		double breakOutDist = quotesL[dv.prePos].low - quotesL[dv.curPos].low;
		    		if ( breakOutDist < FIXED_STOP * PIP_SIZE/3 ){
					
		    			int gap = dv.curPos - dv.prePos + 1;
		    			double pullBackSize = dv.pullBackSize /PIP_SIZE;
		    		//if (( gap <= 12 ) && ( pullBackSize > 5 * FIXED_STOP * PIP_SIZE )){
		    		/*if ((( gap == 1 ) && ( pullBackSize > 80 )) ||  
		    		    (( gap == 2 ) && ( pullBackSize > 100 )) || 
		    		    (( gap >= 3 ) && ( pullBackSize > gap * 40 )))*/	

						if ( findTradeHistory( Constants.ACTION_BUY, quotesL[dv.prePos].time) != null )
							return null;
						
						warning("buy detected 1 " + data.time + " last low:" + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].low);
	
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[pushStartL].time;
	
						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
						tv.addNameValue("PBSize", new Integer((int)(dv.pullBackSize/PIP_SIZE)).toString());
						tv.addNameValue("detail", (quotesL[ dv.prePos]).time + "@" + quotesL[dv.prePos].high + "  -  " +
								 quotesL[ dv.curPos].time + "@" + quotesL[ dv.curPos].high + " pullback:" +(int)(dv.pullBackSize/PIP_SIZE));
						
						trade.addTradeEvent(tv);

						double triggerPrice = data.close;
						enterMarketPosition(triggerPrice);
						super.strategy.sentAlertEmail(AlertOption.trigger, "Close Diverage 240 " + strategy.IB_PORT + " " + trade.symbol + " " + trade.action + " " + triggerPrice + " weak breakout triggered", trade);
	
						return trade;
		    		}
				}
			}
		}
		return null;
	}
	
	public Trade checkDiverageSignal_closestDiverage(QuoteData data, int chart, int ma, double miniPullBackSize)
	{
		QuoteData[] quotesL = getQuoteData(chart);
		int lastbarL = quotesL.length - 1;
		double[] emaL = Indicator.calculateEMA(quotesL, ma);
		double avgBarSize = BarUtil.averageBarSize(quotesL);
		//System.out.println("Average Size=" + avgSize);
		
		int CupWithPeriod = 15;
		if (quotesL[lastbarL].high > emaL[lastbarL]){
			//DiverageDAO dv =  DiverageSetup.findLastDiverageHighClosest(quotesL, lastbarL - 36, lastbarL, 8, 15, avgSize*5 );
			for ( int i = lastbarL-2; i >= lastbarL - CupWithPeriod ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageHighClosest(quotesL, i, lastbarL, 3, 12, avgBarSize );
				if ( dv != null ){ 
					//System.out.println("Diverage sell detected at " + data.time);
		    		if ( miniPullBackSize == 0 )
					   miniPullBackSize =  avgBarSize;//(1.2 + dv.pullBackWidth * 0.1) * avgSize;// 1.5 * FIXED_STOP * PIP_SIZE;
		    		if ( dv.pullBackSize > miniPullBackSize){ 
			    		if ( findTradeHistory( Constants.ACTION_SELL, quotesL[dv.prePos].time) != null )
							return null;
						
						String detectDetail = "Prev High: " + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].high +   
								 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
						warning(symbol + " SELL detected " + data.time + detectDetail);
		
						double triggerPrice = quotesL[dv.prePos].high;

						Calendar calendar = Calendar.getInstance();
						calendar.setTime( new Date(data.timeInMillSec) );
						int weekday = calendar.get(Calendar.DAY_OF_WEEK);
						int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
						int minute=calendar.get(Calendar.MINUTE);
						if (( weekday == Calendar.SUNDAY)  &&  (hour_of_day == 17) && ( minute < 30 )){
							triggerPrice = data.close;
						}
						
						/*QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
						int lastbar5 = quotes5.length - 1;
						if ( quotes5[lastbar5-1].high > triggerPrice )
							return null;*/
						
						// close previous trades
						//public void closePositionByMarket(int posSize, double currentPrice) 
						
						if ( trade != null ){
							if (Constants.ACTION_SELL.equals(trade.action)){
								return trade;
							}
							else if (trade.status.equals(Constants.STATUS_FILLED)){
								closePositionByMarket(trade.remainingPositionSize,triggerPrice);
							}
						}

						
						
						
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[pushStartL].time;
		
						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
						tv.addNameValue("Detected", detectDetail);
						trade.addTradeEvent(tv);
		
						triggerPrice = quotesL[dv.prePos].high;
						enterMarketPosition(triggerPrice);
						super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart" + chart + " " + trade.action + " " + triggerPrice, trade);
		
						//triggerPrice = quotesL[dv.prePos].high + 15 * PIP_SIZE;
						//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
						//trade.status = Constants.STATUS_PLACED;
		
						return trade;
						
						//removeTrade();
						//return null;

		    		}
				}
			}
		}
		else if (quotesL[lastbarL].low < emaL[lastbarL]){
			for ( int i = lastbarL-2; i >= lastbarL - CupWithPeriod ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageLowClosest(quotesL, i, lastbarL, 3, 12, avgBarSize );
				if ( dv != null ) {
		    		if ( miniPullBackSize == 0 )
		    			miniPullBackSize =  avgBarSize;//(1.2 + dv.pullBackWidth * 0.1) * avgBarSize;// 1.5 * FIXED_STOP * PIP_SIZE;
		    		if ( dv.pullBackSize > miniPullBackSize){ 
					
						if ( findTradeHistory( Constants.ACTION_BUY, quotesL[dv.prePos].time) != null )
							return null;
						
						String detectDetail = "Prev Low: " + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].low +   
								 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
						warning(symbol + " BUY detected " + data.time + detectDetail);
		
						double triggerPrice = quotesL[dv.prePos].low;
						Calendar calendar = Calendar.getInstance();
						calendar.setTime( new Date(data.timeInMillSec) );
						int weekday = calendar.get(Calendar.DAY_OF_WEEK);
						int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
						int minute=calendar.get(Calendar.MINUTE);
						if (( weekday == Calendar.SUNDAY)  &&  (hour_of_day == 17) && ( minute < 30 )){
							triggerPrice = data.close;
						}
		
						/*QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
						int lastbar5 = quotes5.length - 1;
						if ( quotes5[lastbar5-1].low < triggerPrice )
							return null;*/
						
						if ( trade != null ){
							if (Constants.ACTION_BUY.equals(trade.action)){
								return trade;
							}
							else if (trade.status.equals(Constants.STATUS_FILLED)){
								closePositionByMarket(trade.remainingPositionSize,triggerPrice);
							}
						}

						
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[pushStartL].time;
		
						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
						tv.addNameValue("Detected", detectDetail);
						trade.addTradeEvent(tv);
		
						triggerPrice = quotesL[dv.prePos].low;
						enterMarketPosition(triggerPrice);
						super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart" + chart + " " + trade.action + " " + triggerPrice, trade);
		
						//triggerPrice = quotesL[dv.prePos].low - 15 * PIP_SIZE;
						//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
						//trade.status = Constants.STATUS_PLACED;
		
						return trade;
						
						//removeTrade();
						//return null;
		    		}
				}
			}
		}
		return null;
	}


	
	public Trade checkDiverageSignal_breakouts(QuoteData data, int chart, int ma)
	{
		QuoteData[] quotesL = getQuoteData(chart);
		int lastbarL = quotesL.length - 1;
		double[] emaL = Indicator.calculateEMA(quotesL, ma);
		double avgBarSize = BarUtil.averageBarSize(quotesL);
		//System.out.println("Average Size=" + avgSize);
		
		int CupWithPeriod = 15;
		if (quotesL[lastbarL].high > emaL[lastbarL]){
			//DiverageDAO dv =  DiverageSetup.findLastDiverageHighClosest(quotesL, lastbarL - 36, lastbarL, 8, 15, avgSize*5 );
			for ( int i = lastbarL-2; i >= lastbarL - CupWithPeriod ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageHighClosest(quotesL, i, lastbarL, 3, 12, avgBarSize );
				if ( dv != null ){ 
					//System.out.println("Diverage sell detected at " + data.time);
		    		double maxPullBackSize =  1.5 * avgBarSize;//(1.2 + dv.pullBackWidth * 0.1) * avgSize;// 1.5 * FIXED_STOP * PIP_SIZE;
		    		if ( dv.pullBackSize < maxPullBackSize){ 
			    		if ( findTradeHistory( Constants.ACTION_SELL, quotesL[dv.prePos].time) != null )
							return null;
						
						String detectDetail = "Prev High: " + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].high +   
								 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
						//warning(symbol + " SELL detected " + data.time + detectDetail);
		
						double triggerPrice = quotesL[dv.prePos].high;

						Calendar calendar = Calendar.getInstance();
						calendar.setTime( new Date(data.timeInMillSec) );
						int weekday = calendar.get(Calendar.DAY_OF_WEEK);
						int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
						int minute=calendar.get(Calendar.MINUTE);
						if (( weekday == Calendar.SUNDAY)  &&  (hour_of_day == 17) && ( minute < 30 )){
							triggerPrice = data.close;
						}
						
						if ( trade != null ){
							if (Constants.ACTION_SELL.equals(trade.action)){
								return trade;
							}
							else if (trade.status.equals(Constants.STATUS_FILLED)){
								closePositionByMarket(trade.remainingPositionSize,triggerPrice);
							}
						}

						
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[pushStartL].time;
		
						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
						tv.addNameValue("Detected", detectDetail);
						trade.addTradeEvent(tv);
		
						triggerPrice = quotesL[dv.prePos].high;
						//enterMarketPosition(triggerPrice);
						if ((MODE == Constants.SIGNAL_MODE) || (MODE == Constants.REAL_MODE)) {
							super.strategy.addStrategyTrigger(trade.symbol + " " + trade.detectTime + " " + trade.action + " " + triggerPrice);
							super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart" + chart + " " + trade.action + " " + triggerPrice, trade);
						}
		
						//triggerPrice = quotesL[dv.prePos].high + 15 * PIP_SIZE;
						//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
						//trade.status = Constants.STATUS_PLACED;
						if (MODE == Constants.SIGNAL_MODE){
							removeTrade();
							return null;
						}else{
							return trade;
						}
		    		}
				}
			}
		}
		else if (quotesL[lastbarL].low < emaL[lastbarL]){
			for ( int i = lastbarL-2; i >= lastbarL - CupWithPeriod ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageLowClosest(quotesL, i, lastbarL, 3, 12, avgBarSize );
				if ( dv != null ) {
		    		double maxPullBackSize =  2 * avgBarSize;//(1.2 + dv.pullBackWidth * 0.1) * avgBarSize;// 1.5 * FIXED_STOP * PIP_SIZE;
		    		if ( dv.pullBackSize < maxPullBackSize ){ 
					
						if ( findTradeHistory( Constants.ACTION_BUY, quotesL[dv.prePos].time) != null )
							return null;
						
						String detectDetail = "Prev Low: " + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].low +   
								 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
						//warning(symbol + " SELL detected " + data.time + detectDetail);
		
						double triggerPrice = quotesL[dv.prePos].low;
						Calendar calendar = Calendar.getInstance();
						calendar.setTime( new Date(data.timeInMillSec) );
						int weekday = calendar.get(Calendar.DAY_OF_WEEK);
						int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
						int minute=calendar.get(Calendar.MINUTE);
						if (( weekday == Calendar.SUNDAY)  &&  (hour_of_day == 17) && ( minute < 30 )){
							triggerPrice = data.close;
						}
		
						if ( trade != null ){
							if (Constants.ACTION_BUY.equals(trade.action)){
								return trade;
							}
							else if (trade.status.equals(Constants.STATUS_FILLED)){
								closePositionByMarket(trade.remainingPositionSize,triggerPrice);
							}
						}

						
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[pushStartL].time;
		
						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
						tv.addNameValue("Detected", detectDetail);
						trade.addTradeEvent(tv);
		
						triggerPrice = quotesL[dv.prePos].low;
						//enterMarketPosition(triggerPrice);
						if ((MODE == Constants.SIGNAL_MODE) || (MODE == Constants.REAL_MODE)) {
							super.strategy.addStrategyTrigger(trade.symbol + " " + trade.detectTime + " " + trade.action + " " + triggerPrice);
							super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart" + chart + " " + trade.action + " " + triggerPrice, trade);
						}
						//triggerPrice = quotesL[dv.prePos].low - 15 * PIP_SIZE;
						//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
						//trade.status = Constants.STATUS_PLACED;
		
						if (MODE == Constants.SIGNAL_MODE){
							removeTrade();
							return null;
						}else{
							return trade;
						}
		    		}
				}
			}
		}
		return null;
	}



	public Trade checkDiverageSignal_givenBigTrend(QuoteData data, int chart, int ma)
	{
		QuoteData[] quotesL = getQuoteData(chart);
		int lastbarL = quotesL.length - 1;
		double[] emaL = Indicator.calculateEMA(quotesL, ma);

		int nbarLow = 2;	// the number of bars lower before the last nigh
		int gapDiverageWidth = 240;  // max gap width
		double maxPullBackSize = 0;
		
		int bigTrend = this.strategy.sftest.setting.getStragetySymbolTrend(this.strategy.STRATEGY_NAME,symbol);
		if ( bigTrend != 0 ){
			warning(symbol + " big trend " + bigTrend);
			maxPullBackSize = BarUtil.averageBarSizeOpenClose(quotesL);
		}
		else{
			 maxPullBackSize = 1.5 * BarUtil.averageBarSize(quotesL);
		}
		
		if ((( bigTrend == Constants.DIRECTION_UP ) && BarUtil.isUpBar(quotesL[lastbarL])) || (quotesL[lastbarL].high >= emaL[lastbarL])){
			for ( int i = lastbarL-2; i >= lastbarL - gapDiverageWidth ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageHighClosest(quotesL, i, lastbarL, maxPullBackSize );
				if ( dv != null ){ 
		    		if ( findTradeHistory( Constants.ACTION_SELL, quotesL[dv.prePos].time) != null )
						return null;
					
					String detectDetail = "Prev High: " + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].high +   
							 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
					warning(symbol + " SELL detected " + data.time + detectDetail);
	
					double triggerPrice = quotesL[dv.prePos].high;

					if ( trade != null ){
						if (Constants.ACTION_SELL.equals(trade.action)){
							return trade;
						}
						else if (trade.status.equals(Constants.STATUS_FILLED)){
							closePositionByMarket(trade.remainingPositionSize,triggerPrice);
						}
					}

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotesL[lastbarL].time;
					trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
	
					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
					tv.addNameValue("Detected", detectDetail);
					trade.addTradeEvent(tv);
	
					//enterMarketPosition(triggerPrice);
					if ((MODE == Constants.SIGNAL_MODE) || (MODE == Constants.REAL_MODE)) {
						super.strategy.addStrategyTrigger(trade.symbol + " " + trade.detectTime + " " + trade.action + " " + triggerPrice);
						super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart" + chart + " " + trade.action + " " + triggerPrice, trade);
					}
	
					if (MODE == Constants.SIGNAL_MODE){
						removeTrade();
						return null;
					}else{
						return trade;
					}
	    		}
			}
		}
		else if ((( bigTrend == Constants.DIRECTION_DOWN ) && BarUtil.isDownBar(quotesL[lastbarL])) || (quotesL[lastbarL].low <= emaL[lastbarL])){
			for ( int i = lastbarL-2; i >= lastbarL - gapDiverageWidth ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageLowClosest(quotesL, i, lastbarL, maxPullBackSize );
				if ( dv != null ) {
					if ( findTradeHistory( Constants.ACTION_BUY, quotesL[dv.prePos].time) != null )
						return null;
					
					String detectDetail = "Prev Low: " + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].low +   
							 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
					//warning(symbol + " SELL detected " + data.time + detectDetail);
	
					double triggerPrice = quotesL[dv.prePos].low;
	
					if ( trade != null ){
						if (Constants.ACTION_BUY.equals(trade.action)){
							return trade;
						}
						else if (trade.status.equals(Constants.STATUS_FILLED)){
							closePositionByMarket(trade.remainingPositionSize,triggerPrice);
						}
					}

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotesL[lastbarL].time;
					trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
	
					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
					tv.addNameValue("Detected", detectDetail);
					trade.addTradeEvent(tv);
	
					triggerPrice = quotesL[dv.prePos].low;
					//enterMarketPosition(triggerPrice);
					if ((MODE == Constants.SIGNAL_MODE) || (MODE == Constants.REAL_MODE)) {
						super.strategy.addStrategyTrigger(trade.symbol + " " + trade.detectTime + " " + trade.action + " " + triggerPrice);
						super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart" + chart + " " + trade.action + " " + triggerPrice, trade);
					}
					//triggerPrice = quotesL[dv.prePos].low - 15 * PIP_SIZE;
					//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
					//trade.status = Constants.STATUS_PLACED;
	
					if (MODE == Constants.SIGNAL_MODE){
						removeTrade();
						return null;
					}else{
						return trade;
					}
	    		}
			}
		}
		return null;
	}


	
	
	
	public Trade checkDiverageSignal_withBigTrend(QuoteData data){
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		QuoteData[] quoteL = getQuoteData(Constants.CHART_DAILY);
		int lastbar = quotes.length - 1;
		double[] ema = Indicator.calculateEMA(quotes, 20);

		double avgBarSize = BarUtil.averageBarSize(quotes);
		//System.out.println("Average Size=" + avgSize);

		
		int CupWithPeriod = 15;
		if (quotes[lastbar].high > ema[lastbar]){
			//DiverageDAO dv =  DiverageSetup.findLastDiverageHighClosest(quotesL, lastbarL - 36, lastbarL, 8, 15, avgSize*5 );
			for ( int i = lastbar-2; i >= lastbar - CupWithPeriod ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageHighClosest(quotes, i, lastbar, 3, 12, avgBarSize );
				if ( dv != null ){ 

					// check big trend
					//BarTrendDetector tbddL = new BarTrendDetector(quoteL, PIP_SIZE);
					//tbddL.detectTrend() == Constants.DIRECTION_UP
					
					
					//System.out.println("Diverage sell detected at " + data.time);
		    		double maxPullBackSize =  1.5 * avgBarSize;//(1.2 + dv.pullBackWidth * 0.1) * avgSize;// 1.5 * FIXED_STOP * PIP_SIZE;
		    		if ( dv.pullBackSize < maxPullBackSize){ 
			    		if ( findTradeHistory( Constants.ACTION_SELL, quotes[dv.prePos].time) != null )
							return null;
						
						String detectDetail = "Prev High: " + quotes[dv.prePos].time + " " + quotes[dv.prePos].high +   
								 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
						//warning(symbol + " SELL detected " + data.time + detectDetail);
		
						double triggerPrice = quotes[dv.prePos].high;

						Calendar calendar = Calendar.getInstance();
						calendar.setTime( new Date(data.timeInMillSec) );
						int weekday = calendar.get(Calendar.DAY_OF_WEEK);
						int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
						int minute=calendar.get(Calendar.MINUTE);
						if (( weekday == Calendar.SUNDAY)  &&  (hour_of_day == 17) && ( minute < 30 )){
							triggerPrice = data.close;
						}
						
						if ( trade != null ){
							if (Constants.ACTION_SELL.equals(trade.action)){
								return trade;
							}
							else if (trade.status.equals(Constants.STATUS_FILLED)){
								closePositionByMarket(trade.remainingPositionSize,triggerPrice);
							}
						}

						
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotes[lastbar].time;
						trade.setFirstBreakOutStartTime(quotes[dv.prePos].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[pushStartL].time;
		
						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes[lastbar].time);
						tv.addNameValue("Detected", detectDetail);
						trade.addTradeEvent(tv);
		
						triggerPrice = quotes[dv.prePos].high;
						//enterMarketPosition(triggerPrice);
						if ((MODE == Constants.SIGNAL_MODE) || (MODE == Constants.REAL_MODE)) {
							super.strategy.addStrategyTrigger(trade.symbol + " " + trade.detectTime + " " + trade.action + " " + triggerPrice);
							super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart" + chart + " " + trade.action + " " + triggerPrice, trade);
						}
		
						//triggerPrice = quotesL[dv.prePos].high + 15 * PIP_SIZE;
						//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
						//trade.status = Constants.STATUS_PLACED;
						if (MODE == Constants.SIGNAL_MODE){
							removeTrade();
							return null;
						}else{
							return trade;
						}
		    		}
				}
			}
		}
		else if (quotes[lastbar].low < ema[lastbar]){
			for ( int i = lastbar-2; i >= lastbar - CupWithPeriod ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageLowClosest(quotes, i, lastbar, 3, 12, avgBarSize );
				if ( dv != null ) {
		    		double maxPullBackSize =  2 * avgBarSize;//(1.2 + dv.pullBackWidth * 0.1) * avgBarSize;// 1.5 * FIXED_STOP * PIP_SIZE;
		    		if ( dv.pullBackSize < maxPullBackSize ){ 
					
						if ( findTradeHistory( Constants.ACTION_BUY, quotes[dv.prePos].time) != null )
							return null;
						
						String detectDetail = "Prev Low: " + quotes[dv.prePos].time + " " + quotes[dv.prePos].low +   
								 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
						//warning(symbol + " SELL detected " + data.time + detectDetail);
		
						double triggerPrice = quotes[dv.prePos].low;
						Calendar calendar = Calendar.getInstance();
						calendar.setTime( new Date(data.timeInMillSec) );
						int weekday = calendar.get(Calendar.DAY_OF_WEEK);
						int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
						int minute=calendar.get(Calendar.MINUTE);
						if (( weekday == Calendar.SUNDAY)  &&  (hour_of_day == 17) && ( minute < 30 )){
							triggerPrice = data.close;
						}
		
						if ( trade != null ){
							if (Constants.ACTION_BUY.equals(trade.action)){
								return trade;
							}
							else if (trade.status.equals(Constants.STATUS_FILLED)){
								closePositionByMarket(trade.remainingPositionSize,triggerPrice);
							}
						}

						
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotes[lastbar].time;
						trade.setFirstBreakOutStartTime(quotes[dv.prePos].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[pushStartL].time;
		
						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes[lastbar].time);
						tv.addNameValue("Detected", detectDetail);
						trade.addTradeEvent(tv);
		
						triggerPrice = quotes[dv.prePos].low;
						//enterMarketPosition(triggerPrice);
						if ((MODE == Constants.SIGNAL_MODE) || (MODE == Constants.REAL_MODE)) {
							super.strategy.addStrategyTrigger(trade.symbol + " " + trade.detectTime + " " + trade.action + " " + triggerPrice);
							super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart" + chart + " " + trade.action + " " + triggerPrice, trade);
						}
						//triggerPrice = quotesL[dv.prePos].low - 15 * PIP_SIZE;
						//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
						//trade.status = Constants.STATUS_PLACED;
		
						if (MODE == Constants.SIGNAL_MODE){
							removeTrade();
							return null;
						}else{
							return trade;
						}
		    		}
				}
			}
		}
		return null;
	}


	
	
	

	public Trade checkDiverageSignal_againstTrendDiverage(QuoteData data, int ma)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] emaL = Indicator.calculateEMA(quotesL, ma);
		double avgSize = BarUtil.averageBarSize(quotesL);
		
		int CupWithPeriod = 15;
		if (quotesL[lastbarL].high > emaL[lastbarL]){
			//DiverageDAO dv =  DiverageSetup.findLastDiverageHighClosest(quotesL, lastbarL - 36, lastbarL, 8, 15, avgSize*5 );
			for ( int i = lastbarL-2; i >= lastbarL - CupWithPeriod ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageHighClosest(quotesL, i, lastbarL, 4, 12, avgSize );
				if ( dv != null ){ 
		    		double miniPullBackSize =  (1.2 + dv.pullBackWidth * 0.1) * avgSize;// 1.5 * FIXED_STOP * PIP_SIZE;
		    		if ( dv.pullBackSize > miniPullBackSize){ 
						BigTrend bt = TrendAnalysis.determineBigTrend2050( quotesL);
							//if (weakBreakOutSetup.action.equals(Constants.ACTION_BUY)) { 
						if (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction))){
				    		if ( findTradeHistory( Constants.ACTION_SELL, quotesL[dv.prePos].time) != null )
								return null;
							
							String detectDetail = "Prev High: " + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].high +   
									 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
							warning(symbol + " SELL detected " + data.time + detectDetail);
			
							double triggerPrice = quotesL[dv.prePos].high;
	
							Calendar calendar = Calendar.getInstance();
							calendar.setTime( new Date(data.timeInMillSec) );
							int weekday = calendar.get(Calendar.DAY_OF_WEEK);
							int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
							int minute=calendar.get(Calendar.MINUTE);
							if (( weekday == Calendar.SUNDAY)  &&  (hour_of_day == 17) && ( minute < 30 )){
								triggerPrice = data.close;
							}
							
							if ( trade != null ){
								if (Constants.ACTION_SELL.equals(trade.action)){
									return trade;
								}
								else if (trade.status.equals(Constants.STATUS_FILLED)){
									closePositionByMarket(trade.remainingPositionSize,triggerPrice);
									AddOpenRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, triggerPrice);
								}
							}
							
							createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
							trade.status = Constants.STATUS_DETECTED;
							trade.detectTime = quotesL[lastbarL].time;
							trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
							//trade.pullBackStartTime = quotesL[pushStartL].time;
			
							TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
							tv.addNameValue("Detected", detectDetail);
							trade.addTradeEvent(tv);
			
							triggerPrice = quotesL[dv.prePos].high;
							enterMarketPosition(triggerPrice);
							super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " " + strategy.IB_PORT + " ATD60 " + trade.action + " " + triggerPrice, trade);
			
							//triggerPrice = quotesL[dv.prePos].high + 15 * PIP_SIZE;
							//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
							//trade.status = Constants.STATUS_PLACED;
			
							if (MODE == Constants.SIGNAL_MODE){
								removeTrade();
								return null;
							}
							else
								return trade;
			    		}
		    		}
				}
			}
		}
		else if (quotesL[lastbarL].low < emaL[lastbarL]){
			for ( int i = lastbarL-2; i >= lastbarL - CupWithPeriod ; i--){
				DiverageDAO dv =  DiverageSetup.findLastDiverageLowClosest(quotesL, i, lastbarL, 4, 12, avgSize );
				if ( dv != null ) {
		    		double miniPullBackSize =  (1.2 + dv.pullBackWidth * 0.1) * avgSize;// 1.5 * FIXED_STOP * PIP_SIZE;
		    		if ( dv.pullBackSize > miniPullBackSize){ 
						BigTrend bt = TrendAnalysis.determineBigTrend2050( quotesL);
						//if (weakBreakOutSetup.action.equals(Constants.ACTION_BUY)) { 
						if (( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction))){
						
							if ( findTradeHistory( Constants.ACTION_BUY, quotesL[dv.prePos].time) != null )
								return null;
							
							String detectDetail = "Prev Low: " + quotesL[dv.prePos].time + " " + quotesL[dv.prePos].low +   
									 " pullbackSize: " + dv.pullBackSize/PIP_SIZE + " pullbackWidth: " + dv.pullBackWidth;
							warning(symbol + " BUY detected " + data.time + detectDetail);
			
							double triggerPrice = quotesL[dv.prePos].low;
							Calendar calendar = Calendar.getInstance();
							calendar.setTime( new Date(data.timeInMillSec) );
							int weekday = calendar.get(Calendar.DAY_OF_WEEK);
							int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
							int minute=calendar.get(Calendar.MINUTE);
							if (( weekday == Calendar.SUNDAY)  &&  (hour_of_day == 17) && ( minute < 30 )){
								triggerPrice = data.close;
							}
			
							/*QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
							int lastbar5 = quotes5.length - 1;
							if ( quotes5[lastbar5-1].low < triggerPrice )
								return null;*/
							
							if ( trade != null ){
								if (Constants.ACTION_BUY.equals(trade.action)){
									return trade;
								}
								else if (trade.status.equals(Constants.STATUS_FILLED)){
									closePositionByMarket(trade.remainingPositionSize,triggerPrice);
									AddOpenRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, triggerPrice);
								}
							}
	
							
							createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
							trade.status = Constants.STATUS_DETECTED;
							trade.detectTime = quotesL[lastbarL].time;
							trade.setFirstBreakOutStartTime(quotesL[dv.prePos].time);// for history lookup only
							//trade.pullBackStartTime = quotesL[pushStartL].time;
			
							TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
							tv.addNameValue("Detected", detectDetail);
							trade.addTradeEvent(tv);
			
							triggerPrice = quotesL[dv.prePos].low;
							enterMarketPosition(triggerPrice);
							super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " " + strategy.IB_PORT + " ATD60 " + trade.action + " " + triggerPrice, trade);
			
							//triggerPrice = quotesL[dv.prePos].low - 15 * PIP_SIZE;
							//enterLimitPositionMulti(trade.POSITION_SIZE, triggerPrice);
							//trade.status = Constants.STATUS_PLACED;
			
							if (MODE == Constants.SIGNAL_MODE){
								removeTrade();
								return null;
							}
							else
								return trade;
			    		}
		    		}
				}
			}
		}
		return null;
	}
	
	




	
	public Trade checkDiverageSignal_SmallConsecDiverage(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		double[] emaL = Indicator.calculateEMA(quotesL, 20);

		if (quotesL[lastbarL].high > emaL[lastbarL])
		{
			int pushStartL = Pattern.findLastPriceConsectiveBelowMA(quotesL, emaL, lastbarL, 2);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 2;
			pushStartL = Utility.getLow( quotesL, pushStartL-2, pushStartL).pos;
			
			PushList pushList = Pattern.findPast2Highs(quotesL, pushStartL, lastbarL );
			if ( pushList == null )
				return null;
			
			PushHighLow[] phls = pushList.phls;
			if (( phls != null ) && ( phls.length > 2 ))
			{
				PushHighLow phl = phls[0];
				
				double pullBackDist = quotesL[phls[0].prePos].high - phls[0].pullBack.low;
				int pullBackSize = (int)(pullBackDist/PIP_SIZE);
				//if (( negatives > 0 ) || (pullBackDist > 2.5 * FIXED_STOP * PIP_SIZE ))
				//if ( pushDetected )
				{
					if ( findTradeHistory( Constants.ACTION_SELL, quotesL[phl.prePos].time) != null )
						return null;
					
					logger.warning(symbol + " SELL detected " + data.time + " pullback break is " );

					warning("sell detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time);
		    	    System.out.println(symbol + " " + 
							 ((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
							 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
							 " pullback@" + phl.pullBack.time);

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotesL[lastbarL].time;
					trade.setFirstBreakOutStartTime(quotesL[phl.prePos].time);// for history lookup only
					trade.pullBackStartTime = quotesL[pushStartL].time;

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
					tv.addNameValue("PBSize", new Integer(pullBackSize).toString());
					tv.addNameValue("detail", (((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
							 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
							 " pullback@" + phl.pullBack.time));
					trade.addTradeEvent(tv);

					enterMarketPosition(quotesL[phls[0].prePos].high);

					return trade;
				}
			}
		}
		else if (quotesL[lastbarL].low < emaL[lastbarL])
		{
			int pushStartL = Pattern.findLastPriceConsectiveAboveMA(quotesL, emaL, lastbarL, 2);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 2;
			
			pushStartL = Utility.getHigh( quotesL, pushStartL-2, pushStartL).pos;

			PushList pushList = Pattern.findPast2Lows(quotesL, pushStartL, lastbarL );
			if ( pushList == null )
				return null;
			
			PushHighLow[] phls = pushList.phls;
			if (( phls != null ) && (phls.length > 2))
			{
				PushHighLow phl = phls[0];
				
				double pullBackDist = phls[0].pullBack.high - quotesL[phls[0].prePos].low ;
				int pullBackSize = (int)(pullBackDist/PIP_SIZE);
				//if (( positive > 0 ) || (pullBackDist > 2.5 * FIXED_STOP * PIP_SIZE ))
				//if ( pushDetected )
				{
					if ( findTradeHistory( Constants.ACTION_BUY, quotesL[phl.prePos].time) != null )
						return null;

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotesL[lastbarL].time;
					trade.setFirstBreakOutStartTime(quotesL[phl.prePos].time);// for history lookup only
					trade.pullBackStartTime = quotesL[pushStartL].time;


					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
					tv.addNameValue("PBSize", new Integer(pullBackSize).toString());
					tv.addNameValue("detail", (((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).low + "  -  " +
							 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).low + 
							 " pullback@" + phl.pullBack.time));
					trade.addTradeEvent(tv);

					enterMarketPosition(quotesL[phls[0].prePos].low);

					return trade;
				}
			}
		}
		return null;
	}

	
	

	
	public Trade checkREVTradeSetup3(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		double[] emaL = Indicator.calculateEMA(quotesL, 20);

		if (quotesL[lastbarL].high > emaL[lastbarL]){

			int pushStartL = Pattern.findLastPriceConsectiveBelowMA(quotesL, emaL, lastbarL, 2);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 2;
			pushStartL = Utility.getLow( quotesL, pushStartL-2, pushStartL).pos;
			
			PushList pushList = Pattern.findPast2Highs(quotesL, pushStartL, lastbarL );
			if ( pushList == null )
				return null;
			
			PushHighLow[] phls = pushList.phls;
			if (( phls != null ) && ( phls.length > 0 )){

				// 1.  at least three with MCAD diverage
				PushHighLow phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotesL );
				int negatives = 0;
				int touch20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++){
					if ( macd[j].histogram < 0 )
						negatives ++;
					if ( quotesL[j].low < emaL[j])
						touch20MA ++;
				}

				if ( touch20MA == 0 )
					return null;
				
				double pullBackDist = quotesL[phls[0].prePos].high - phls[0].pullBack.low;
				int pullBackSize = (int)(pullBackDist/PIP_SIZE);
				int pullBackWidth = phl.curPos - phl.prePos;
				if (( pullBackWidth <= 12 ) && (pullBackSize > 8 * FIXED_STOP)){
					if ( findTradeHistory( Constants.ACTION_SELL, quotesL[phl.prePos].time) != null )
						return null;
					logger.warning(symbol + " SELL detected " + data.time + " pullback break is " );

					warning("sell detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time);
		    	    System.out.println(symbol + " " + 
							 ((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
							 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
							 " pullback@" + phl.pullBack.time);

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotesL[lastbarL].time;
					trade.setFirstBreakOutStartTime(quotesL[phl.prePos].time);// for history lookup only
					trade.pullBackStartTime = quotesL[pushStartL].time;

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
					tv.addNameValue("PBSize", new Integer(pullBackSize).toString());
					tv.addNameValue("detail", (((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
							 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
							 " pullback@" + phl.pullBack.time));
					trade.addTradeEvent(tv);

					return trade;
				}
			}
		}
		else if (quotesL[lastbarL].low < emaL[lastbarL])
		{
			int pushStartL = Pattern.findLastPriceConsectiveAboveMA(quotesL, emaL, lastbarL, 2);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 2;
			
			pushStartL = Utility.getHigh( quotesL, pushStartL-2, pushStartL).pos;

			PushList pushList = Pattern.findPast2Lows(quotesL, pushStartL, lastbarL );
			if ( pushList == null )
				return null;
			
			PushHighLow[] phls = pushList.phls;
			if (( phls != null ) && (phls.length > 0))
			{
				// 1.  at least three with MCAD diverage
				// wide range, requires a MACD diverage
				PushHighLow phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotesL );
				int positive = 0;
				int touch20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++)
				{
					if ( macd[j].histogram > 0 )
						positive ++;
					if ( quotesL[j].high > emaL[j])
						touch20MA ++;
				}

				if ( touch20MA == 0 )
					return null;
				
				double pullBackDist = phls[0].pullBack.high - quotesL[phls[0].prePos].low ;
				int pullBackSize = (int)(pullBackDist/PIP_SIZE);
				int pullBackWidth = phl.curPos - phl.prePos;
				if (( pullBackWidth <= 12 ) && (pullBackSize > 8 * FIXED_STOP)){
					if ( findTradeHistory( Constants.ACTION_BUY, quotesL[phl.prePos].time) != null )
						return null;

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotesL[lastbarL].time;
					trade.setFirstBreakOutStartTime(quotesL[phl.prePos].time);// for history lookup only
					trade.pullBackStartTime = quotesL[pushStartL].time;


					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
					tv.addNameValue("PBSize", new Integer(pullBackSize).toString());
					tv.addNameValue("detail", (((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).low + "  -  " +
							 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).low + 
							 " pullback@" + phl.pullBack.time));
					trade.addTradeEvent(tv);

					return trade;
				}
			}
		}
		return null;
	}
	
	
	
	
    
	public Trade checkREVTradeSetup2(QuoteData data, int ma)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		double[] emaL = Indicator.calculateEMA(quotesL, ma);

		if (quotesL[lastbarL].high > emaL[lastbarL])
		{
			int pushStartL = Pattern.findLastPriceConsectiveBelowMA(quotesL, emaL, lastbarL, 2);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 2;
			pushStartL = Utility.getLow( quotesL, pushStartL-2, pushStartL).pos;
			
			PushList pushList = Pattern.findPast2Highs(quotesL, pushStartL, lastbarL );
			if ( pushList == null )
				return null;
			
			PushHighLow[] phls = pushList.phls;
			if (( phls != null ) && ( phls.length > 0 ))
			{
				// 1.  at least three with MCAD diverage
				// wide range, requires a MACD diverage
				PushHighLow phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotesL );
				int negatives = 0;
				int touch20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++)
				{
					if ( macd[j].histogram < 0 )
						negatives ++;
					if ( quotesL[j].low < emaL[j])
						touch20MA ++;
				}

				/*
				boolean pushDetected = false;
				double avgSize = BarUtil.averageBarSize(quotesL);
				for ( int j = phl.curPos-1; j > phl.prePos; j-- )
				{
					ConsectiveBars consecDown = Utility.findLastConsectiveDownBars( quotesL, phl.prePos, j);
					if ( consecDown != null )
					{
						double consecDownSize = quotesL[consecDown.getBegin()].high - quotesL[consecDown.getEnd()].low;
						int consectDownBarNum = consecDown.getSize();
						//System.out.println(consectDownBarNum + " " +  consecDownSize/avgSize);
						
						if ((( consecDown.getSize() == 1 ) && ( consecDownSize > 1.5 * avgSize ))
						  ||(( consecDown.getSize() == 2 ) && ( consecDownSize > 3 * avgSize ))
						  ||(( consecDown.getSize() == 3 ) && ( consecDownSize > 4 * avgSize ))
						  ||(( consecDown.getSize() == 4 ) && ( consecDownSize > consecDown.getSize() * 1.5 * avgSize )))
						{	
							pushDetected = true;
							break;
						}
					}
				}*/
				
				
				
				
				double pullBackDist = quotesL[phls[0].prePos].high - phls[0].pullBack.low;
				int pullBackSize = (int)(pullBackDist/PIP_SIZE);
				if (( negatives > 0 ) || (pullBackDist > 2.5 * FIXED_STOP * PIP_SIZE ))
				//if ( pushDetected )
				{
					if ( findTradeHistory( Constants.ACTION_SELL, quotesL[phl.prePos].time) != null )
						return null;
					
					logger.warning(symbol + " SELL detected " + data.time + " pullback break is " );

					warning("sell detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time);
		    	    System.out.println(symbol + " " + 
							 ((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
							 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
							 " pullback@" + phl.pullBack.time);

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotesL[lastbarL].time;
					trade.setFirstBreakOutStartTime(quotesL[phl.prePos].time);// for history lookup only
					trade.pullBackStartTime = quotesL[pushStartL].time;

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
					tv.addNameValue("PBSize", new Integer(pullBackSize).toString());
					tv.addNameValue("detail", (((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
							 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
							 " pullback@" + phl.pullBack.time));
					trade.addTradeEvent(tv);

					return trade;
				}
			}
		}
		else if (quotesL[lastbarL].low < emaL[lastbarL])
		{
			int pushStartL = Pattern.findLastPriceConsectiveAboveMA(quotesL, emaL, lastbarL, 2);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 2;
			
			pushStartL = Utility.getHigh( quotesL, pushStartL-2, pushStartL).pos;

			PushList pushList = Pattern.findPast2Lows(quotesL, pushStartL, lastbarL );
			if ( pushList == null )
				return null;
			
			PushHighLow[] phls = pushList.phls;
			if (( phls != null ) && (phls.length > 0))
			{
				/*
				for ( int i = 0; i < phls.length; i++)
				{
		    	    System.out.println(symbol + " " + CHART + " last pull back " + i + "    " +
					 ((QuoteData)quotes[ phls[i].prePos]).time + "@" + ((QuoteData)quotes[ phls[i].prePos]).high + "  -  " +
					 ((QuoteData)quotes[ phls[i].curPos]).time + "@" + ((QuoteData)quotes[ phls[i].curPos]).high + 
					 " pullback@" + phls[i].pullBack.time);
				}*/
				
				// 1.  at least three with MCAD diverage
				
				// wide range, requires a MACD diverage
				PushHighLow phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotesL );
				int positive = 0;
				int touch20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++)
				{
					if ( macd[j].histogram > 0 )
						positive ++;
					if ( quotesL[j].high > emaL[j])
						touch20MA ++;
				}

				/*
				boolean pushDetected = false;
				double avgSize = BarUtil.averageBarSize(quotesL);
				for ( int j = phl.curPos-1; j > phl.prePos; j-- )
				{
					ConsectiveBars consecUp = Utility.findLastConsectiveUpBars( quotesL, phl.prePos, j);
					if ( consecUp != null )
					{
						double consecUpSize = quotesL[consecUp.getEnd()].high - quotesL[consecUp.getBegin()].low;
						int consectUpBarNum = consecUp.getSize();
						
						if ((( consecUp.getSize() == 1 ) && ( consecUpSize > 1.5 * avgSize ))
						  ||(( consecUp.getSize() == 2 ) && ( consecUpSize > 3 * avgSize ))
						  ||(( consecUp.getSize() == 3 ) && ( consecUpSize > 4 * avgSize ))
						  ||(( consecUp.getSize() == 4 ) && ( consecUpSize > consecUp.getSize() * 1.5 * avgSize )))
						{	
							pushDetected = true;
							break;
						}
					}
				}*/
				
				double pullBackDist = phls[0].pullBack.high - quotesL[phls[0].prePos].low ;
				int pullBackSize = (int)(pullBackDist/PIP_SIZE);
				if (( positive > 0 ) || (pullBackDist > 2.5 * FIXED_STOP * PIP_SIZE ))
				//if ( pushDetected )
				{
					if ( findTradeHistory( Constants.ACTION_BUY, quotesL[phl.prePos].time) != null )
						return null;

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotesL[lastbarL].time;
					trade.setFirstBreakOutStartTime(quotesL[phl.prePos].time);// for history lookup only
					trade.pullBackStartTime = quotesL[pushStartL].time;


					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
					tv.addNameValue("PBSize", new Integer(pullBackSize).toString());
					tv.addNameValue("detail", (((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).low + "  -  " +
							 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).low + 
							 " pullback@" + phl.pullBack.time));
					trade.addTradeEvent(tv);

					return trade;
				}
			}
		}
		return null;
	}
	


	public Trade checkREVTradeSetup(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 2);

	
		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_UP) && (quotesL[lastbarL].high > ema20L[lastbarL]))
		{
			int startL = upPos;
			int pushStartL = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 5);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 5;

			pushStartL = Utility.getLow( quotesL, pushStartL-5, pushStartL+5).pos;
			
			PushList pushList = Pattern.findPast2Highs(quotesL, pushStartL, lastbarL );
			
			if ( pushList == null )
				return null;
			
			
			PushHighLow[] phls = pushList.phls;

			if ( phls != null )
			{
				if ( phls.length >=2 )
				{
					// 1.  at least three with MCAD diverage
					// wide range, requires a MACD diverage
					PushHighLow phl = phls[0];
					MACD[] macd = Indicator.calculateMACD( quotesL );
					int negatives = 0;
					int touch20MA = 0;
					for ( int j = phl.prePos; j <= phl.curPos; j++)
					{
						if ( macd[j].histogram < 0 )
							negatives ++;
						//System.out.print(macd[j].macd + "   ");
						
						if ( quotesL[j].low < ema20L[j])
							touch20MA ++;
					}
					
					double pullBackDist = quotesL[phls[0].prePos].high - phls[0].pullBack.low;
					if (( negatives > 0 ) || (pullBackDist > 2.5 * FIXED_STOP * PIP_SIZE ))
					{
						if ( findTradeHistory( Constants.ACTION_SELL, quotesL[phl.prePos].time) != null )
							return null;
						
						logger.warning(symbol + " SELL detected " + data.time + " pullback break is " );

						warning("sell detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time);
			    	    System.out.println(symbol + " " + 
								 ((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
								 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
								 " pullback@" + phl.pullBack.time);

						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutStartTime(quotesL[phl.prePos].time);// for history lookup only
						trade.pullBackStartTime = quotesL[pushStartL].time;
								
						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
						tv.addNameValue("detail", (((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
								 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
								 " pullback@" + phl.pullBack.time));
						trade.addTradeEvent(tv);

						return trade;
					}
				}
			}
		}
		else if ((direction == Constants.DIRECTION_DOWN) && (quotesL[lastbarL].low < ema20L[lastbarL]))
		{
			int startL = downPos;
			//while ( quotesL[startL].high < ema20L[startL])
				//startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			int pushStartL = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 5);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 5;
			
			pushStartL = Utility.getHigh( quotesL, pushStartL-5, pushStartL+5).pos;

			PushList pushList = Pattern.findPast2Lows(quotesL, pushStartL, lastbarL );
			
			if ( pushList == null )
				return null;
			
			PushHighLow[] phls = pushList.phls;
			
			if ( phls != null )
			{
				/*
				for ( int i = 0; i < phls.length; i++)
				{
		    	    System.out.println(symbol + " " + CHART + " last pull back " + i + "    " +
					 ((QuoteData)quotes[ phls[i].prePos]).time + "@" + ((QuoteData)quotes[ phls[i].prePos]).high + "  -  " +
					 ((QuoteData)quotes[ phls[i].curPos]).time + "@" + ((QuoteData)quotes[ phls[i].curPos]).high + 
					 " pullback@" + phls[i].pullBack.time);
				}*/
				
				if ( phls.length >= 2 )
				{
					// 1.  at least three with MCAD diverage
					
					// wide range, requires a MACD diverage
					PushHighLow phl = phls[0];
					MACD[] macd = Indicator.calculateMACD( quotesL );
					int positive = 0;
					int touch20MA = 0;
					for ( int j = phl.prePos; j <= phl.curPos; j++)
					{
						if ( macd[j].histogram > 0 )
							positive ++;
						//System.out.print(macd[j].macd + "   ");
						if ( quotesL[j].high > ema20L[j])
							touch20MA ++;
					}
					//System.out.println();
					
					double pullBackDist = phls[0].pullBack.high - quotesL[phls[0].prePos].low ;
					if (( positive > 0 ) || (pullBackDist > 2.5 * FIXED_STOP * PIP_SIZE ))
					{
						if ( findTradeHistory( Constants.ACTION_BUY, quotesL[phl.prePos].time) != null )
							return null;

						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
						trade.status = Constants.STATUS_DETECTED;
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutStartTime(quotesL[phl.prePos].time);// for history lookup only
						trade.pullBackStartTime = quotesL[pushStartL].time;

						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotesL[lastbarL].time);
						tv.addNameValue("detail", (((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).low + "  -  " +
								 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).low + 
								 " pullback@" + phl.pullBack.time));
						trade.addTradeEvent(tv);

						return trade;
					}
				}
			}
		}
		return null;
	}
	

	

	
	

  
	
	
	
	public void entry( QuoteData data )
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data,0,0);
		else if (MODE == Constants.REAL_MODE){
			if (trade != null){  
				//trackPullBackEntry3( data );
				//trackPullBackEntry( data);
				trackEntryUsingConfluenceIndicator(data);
			}
		}
	}
		
		
	public void manage( QuoteData data )
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data,0,0);

		if (( trade != null ) && ( trade.status.equals(Constants.STATUS_DETECTED))){
				trackEntry( data );
		}

		
		
		if (( trade != null ) && ( trade.status.equals(Constants.STATUS_FILLED))){
			//exit123_new9c4_123( data );  
			//exit123_new9c4( data );  		//previous worked  
			exit123_close_monitor(data);  	// to copy from above
		}
	}

	
	public QuoteData[] getQuoteData(int CHART)
	{
		return instrument.getQuoteData(CHART);
	}

	public Instrument getInstrument()
	{
		return instrument;
	}


	
	private void OrderFilled_stopId(String time){
		
		info("Order stopped out @ " + trade.stop + " " + time);
		AddCloseRecord(time, Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);

		trade.stopId = 0;

		cancelTargets();
		
		//boolean reversequalified = true;
		//if (mainProgram.isNoReverse(symbol ))
		//	reversequalified = false;
		trade.status = Constants.STATUS_STOPPEDOUT;	
		//processAfterHitStopLogic_c();
		removeTrade();
		return;
	}
	
	public void checkOrderFilled(int orderId, int filled)
	{
		checkStopTarget(null, orderId, filled);
	}
	

	
	//////////////////////////////////////////////////////////////////////////
	//
	//  Custom Methods
	//
	///////////////////////////////////////////////////////////////////////////
	
	public void checkStopTarget(QuoteData data, int orderId, int filled)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		if (lastbar == -1 )
			lastbar = 0;	// this could be triggered before all the quotes been completed
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check stop;
			if ((( orderId != 0 ) && (orderId == trade.stopId)) || (( data != null ) && (trade.stopId != 0) && (data.high > trade.stop))){ 
				OrderFilled_stopId(quotes[lastbar].time);
				return;
			}

			// check entry limit;
			for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
			{
				TradePosition limit = trade.limits[i];
				if (( limit != null ) && ((( orderId != 0 ) && (orderId == limit.orderId)) || (( data != null ) && (limit.orderId != 0 ) && (data.high >= limit.price ))))
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					limit.orderId = 0;
					limit.filled = true;

					if (trade.recordOpened == false){
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
					}
					
					if ( trade.getEntryTime() == null )
						trade.setEntryTime(quotes[lastbar].time);
					if ( trade.entryPrice == 0 )
						trade.entryPrice = limit.price;
					trade.addFilledPosition(limit.price, limit.position_size, quotes[lastbar].time);
					trade.remainingPositionSize += limit.position_size;
					trade.status = Constants.STATUS_FILLED;
					
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, limit.position_size, limit.price);
					
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					if ( trade.stopId != 0 )
						cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);

					trade.openOrderDurationInHour = 0;

					/*if ( trade.averageUp ){
						double averageUpPrice = limit.price - FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketStopPrice = averageUpPrice;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_SELL, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}*/

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
				}
			}


			/*
			if ((trade.stopMarketId != 0) && (data.low <= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.stopMarketSize, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.stopMarketSize;

				if ( trade.entryPrice == 0 )
					trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.stopMarketSize;
				//trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
			}*/

			

			for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
			{
				TradePosition target = trade.targets[i];
				if (( target != null ) && ((( orderId != 0 ) && (orderId == target.orderId)) || (( data != null ) && (target.orderId != 0 ) && (data.low <= target.price))))
				{
					warning("target hit, close " + target.position_size + " @ " + target.price);
					if ( data != null )
						AddCloseRecord(data.time, Constants.ACTION_BUY, target.position_size, target.price);
					trade.targets[i].orderId = 0;

					trade.remainingPositionSize -= target.position_size;
					if (trade.remainingPositionSize <= 0)
					{
						cancelAllOrders();
						removeTrade();
						return;
					}
					else
					{
						if ( trade.stop != 0 ){
							info("replace stop order");
							cancelStop();
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						}
						if ( trade.targetId != 0 ){
							info("replace target order");
							cancelOrder(trade.targetId);
							trade.targetPos = trade.remainingPositionSize;
							logger.warning(symbol + " place target order " + trade.targetPrice + " " + trade.targetPos );
							trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, null);
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ((( orderId != 0 ) && (orderId == trade.stopId)) || (( data != null ) && (trade.stopId != 0) && (data.low < trade.stop))){ 
				OrderFilled_stopId(quotes[lastbar].time);
				return;
			}
			
			
			// check entry limit;
			for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
			{
				TradePosition limit = trade.limits[i];
				if (( limit != null ) && ((( orderId != 0 ) && (orderId == limit.orderId)) || (( data != null ) && (limit.orderId != 0 ) && (data.low <= limit.price ))))
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					limit.orderId = 0;
					limit.filled = true;
					if (trade.recordOpened == false){
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						trade.recordOpened = true;
					}
					
					if ( trade.getEntryTime() == null )
						trade.setEntryTime(quotes[lastbar].time);
					if ( trade.entryPrice == 0 )
						trade.entryPrice = limit.price;
					trade.addFilledPosition(limit.price, limit.position_size, quotes[lastbar].time);
					trade.remainingPositionSize += limit.position_size;
					trade.status = Constants.STATUS_FILLED;
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, limit.position_size, limit.price);

					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					if ( trade.stopId != 0 )
						cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

					trade.openOrderDurationInHour = 0;

					/*if ( trade.averageUp ){
						double averageUpPrice = limit.price + FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketStopPrice = averageUpPrice;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_BUY, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}*/

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
				}
			}

			/*
			if ((trade.stopMarketId != 0) && (data.high >= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.stopMarketSize, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.stopMarketSize;

				if ( trade.entryPrice == 0 )
					trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.stopMarketSize;
				//trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
			}*/


			
			for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
			{
				TradePosition target = trade.targets[i];
				if (( target != null ) && ((( orderId != 0 ) && (orderId == target.orderId)) || (( data != null ) && (target.orderId != 0 ) && (data.high >= target.price))))
				{
					warning("target hit, close " + target.position_size + " @ " + target.price);
					if ( data != null )
						AddCloseRecord(data.time, Constants.ACTION_SELL, target.position_size, target.price);
					trade.targets[i].orderId = 0;

					trade.remainingPositionSize -= target.position_size;
					if (trade.remainingPositionSize <= 0)
					{
						cancelAllOrders();
						removeTrade();
						return;
					}
					else
					{
						if ( trade.stop != 0 ){
							info("replace stop order");
							cancelStop();
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
						}
						if ( trade.targetId != 0 ){
							info("replace target order");
							cancelOrder(trade.targetId);
							trade.targetPos = trade.remainingPositionSize;
							logger.warning(symbol + " place target order " + trade.targetPrice + " " + trade.targetPos );
							trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, null);
						}
					}
				}
			}
		}
	}



	
	void processAfterHitStopLogic_c()
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		
		//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);

		double prevStop = trade.stop;
		String prevAction = trade.action;
		String prevType = trade.type;
		String prevEntryTime = trade.getEntryTime();
		double prevEntryPrice = trade.entryPrice;
		
		warning("check reverse logic");

		if (prevType == Constants.TRADE_CNT )
		{
			warning("prev trade was CNT, reverse does not apply");
			removeTrade();
			return;
		}

		int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
		int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, prevEntryTime, 2 );

		removeTrade();
		
		// check no_reverse list
		/*
		if (mainProgram.isNoReverse(symbol ))
		{
			warning("no reverse");
			return;
		}*/
		
		
		//BigTrend bt = TrendAnalysis.determineBigTrend( quotesL);
		BigTrend bt = TrendAnalysis.determineBigTrend2050( quotesL);
		warning(" trend is " + bt.direction);

		
		if (Constants.ACTION_SELL.equals(prevAction))
		{
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			warning("low point after entry is " + lowestPointAfterEntry + " entry price:" + prevEntryPrice); 
			
			if ((( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 ) || 
			(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) && (( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE ))) 
			{
				System.out.println(bt.toString(quotesL));
				//bt = determineBigTrend( quotesL);
				warning(" close trade with small tip");
				double reversePrice = prevStop;
				boolean reversequalified = true;
				
				if ( (touch20MAPosL - firstBreakOutStartL) > 5)
				{
					double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
					double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
					if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
						reversequalified = false;
				}

				// reverse;
				if ( reversequalified )
				{
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.setEntryTime(((QuoteData) quotes[lastbar]).time);

					enterMarketPosition(reversePrice);
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(prevAction))
		{
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 

			if ((( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE * 0.3 ) ||
			     (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE )))
			{
				System.out.println(bt.toString(quotesL));
				//bt = determineBigTrend( quotesL);
				warning(" close trade with small tip");
				double reversePrice = prevStop;
				boolean reversequalified = true;
				if ( (touch20MAPosL - firstBreakOutStartL) > 5)
				{
					double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
					double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
					if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
						reversequalified = false;
				}

				// reverse;
				if ( reversequalified )
				{
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.setEntryTime(((QuoteData) quotes[lastbar]).time);

					enterMarketPosition(reversePrice);
					return;
				}
			}
		}
	}

	
	
	public void trackTradeTickerEntry(int field, double price)
	{
		if (trade.filled == true)
			return;

		// ticker type: 1 = bid, 2 = ask, 4 = last, 6 = high, 7 = low, 9 = close

		/*
		if (field == 1) // bid
		{
			checkTriggerMarketSell(price);
		}
		else if (field == 2) // ask
		{
			checkTriggerMarketBuy(price);
		}*/
		
		if (( field == 4 ) && (trade != null ))
		{
			if (Constants.ACTION_SELL.equals( trade.action))
			{
				//checkTriggerMarketSell(price);
			}
			else if (Constants.ACTION_BUY.equals( trade.action))
			{
				//checkTriggerMarketBuy(price);
			}
		}
	}

	
	
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Entry Setups
	//
	//
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////


	
	
	
	
	public Trade checkPullbackSetup2(QuoteData data, double price )
	{
		if (( bigChartState != Constants.STATE_BREAKOUT_UP ) && ( bigChartState != Constants.STATE_BREAKOUT_DOWN ))
			return null;
		
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);

		if  ( bigChartState == Constants.STATE_BREAKOUT_UP )
		{
			// find last 20MA touch point on 60 minute chart
			 //System.out.println("Now=" + data.time);
			
			int lastTouchMA = lastbar60;
			while (!( quotes60[lastTouchMA].low < ema20_60[lastTouchMA]) && ( quotes60[lastTouchMA].high > ema20_60[lastTouchMA])) 
				lastTouchMA--;
			//System.out.println("Last Touch 20MA = " + quotes60[lastTouchMA].time);
			
			 
			int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes60, ema20_60, lastTouchMA, 2);
			int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes60, ema20_60, lastTouchMA, 2);
			
			if ( lastDownPos > lastUpPos )
				return null;
			
			// find where the push starts
			int lastUpPosStart = lastUpPos-2;
			while ( quotes60[lastUpPosStart].low > ema20_60[lastUpPosStart])
				lastUpPosStart--;
			
			int start60 = Utility.getHigh( quotes60, lastUpPosStart, lastUpPos).pos;
		    //System.out.println("Start60=" + quotes60[start60].time);

		    if (previousTradeExist(Constants.TRADE_RV, Constants.ACTION_BUY, start60))
				return null;
			
		    for ( int i = lastbar60 -1; i > start60; i--)
		    {
			    PushList pushList = Pattern.findPast1Lows1(quotes60, start60, i );
					
				if (( pushList != null )&& (pushList.phls != null) && (pushList.phls.length > 0 ) )
				{
					PushHighLow phl = pushList.phls[pushList.phls.length-1];
				    System.out.println("Start60=" + quotes60[start60].time);
				    System.out.println("Push Detect at" + quotes60[phl.curPos].time);
					
					int pushDuration = phl.pushWidth;
					double pushSize = phl.pushSize;

					if ( data.high > phl.pullBack.high)
					{
						for ( int j = phl.pullBack.pos + 1; j < lastbar60; j++)
						{
							if ( data.high > phl.pullBack.high )
								return null;
						}
						
						System.out.println("Now=" + data.time);
						System.out.println("Last Touch 20MA = " + quotes60[lastTouchMA].time);
						System.out.println("Start60=" + quotes60[start60].time);
						System.out.println("push Top found" + quotes60[i].time + " " + data.time);
						logger.warning(symbol + " " + " buy detected " + data.time + " pushStart:" + quotes60[start60].time  );
						createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);

						trade.setEntryTime(quotes60[lastbar60].time);
						trade.entryPrice = phl.pullBack.high;
						trade.pushStartL = start60; 
						trade.pullBackPos = start60;  // this is used to track history
			
						trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;
			
						enterMarketPosition(trade.entryPrice);
						return trade;
					}
					break;
				}
		    }
		}
		
		return null;
	
	}
	

	
	
	
	

	

	
	
	
	
	
	
	
	
	
	
	
	

	
	
	
	
	
	
	

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Trade Entry
	//
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void trackPullBackEntry_new(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;

		int startL = Utility.findPositionByHour(quotesL, trade.pullBackStartTime, 2 );
		//System.out.println("startL:" + quotesL[startL].time + " " + startL);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// find last upbar;
	    	int lastUpbar = lastbarL - 1;
	    	while (!BarUtil.isUpBar(quotesL[lastUpbar]))
	    		lastUpbar--;
	    	int lastUpbarBegin = lastUpbar-1;
	    	while(BarUtil.isUpBar(quotesL[lastUpbarBegin]))
	    		lastUpbarBegin--;
	    	lastUpbarBegin = lastUpbarBegin + 1;
	    	
	    	double lowestUpPoint = Utility.getLow( quotesL, lastUpbarBegin, lastUpbar).low;
	    	
			if ( quotesL[lastbarL].low < lowestUpPoint )
			{
				//warning("3 sell triggered " + triggerPrice + quotesL[lastbarL].time);
				enterMarketPosition(lowestUpPoint);
				return;
				//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
			}
			
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// entry at tips
			// find last upbar;
	    	int lastDownbar = lastbarL - 1;
	    	while (!BarUtil.isDownBar(quotesL[lastDownbar]))
	    		lastDownbar--;
	    	int lastDownbarBegin = lastDownbar-1;
	    	while(BarUtil.isDownBar(quotesL[lastDownbarBegin]))
	    		lastDownbarBegin--;
	    	lastDownbarBegin = lastDownbarBegin + 1;
	    	
	    	double highestDownPoint = Utility.getHigh( quotesL, lastDownbarBegin, lastDownbar).high;
	    	
			if ( quotesL[lastbarL].high > highestDownPoint )
			{
				//warning("3 sell triggered " + triggerPrice + quotesL[lastbarL].time);
				enterMarketPosition(highestDownPoint);
				return;
				//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
			}
		}

		return;
	}


	
	
	
	
	
	
	
	
	
	
	public void trackEntryUsingConfluenceIndicator(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		

		
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			// run push setup
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
					double triggerPrice = lastPush.pullBack.low;
					warning("Breakout base sell triggered at " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					enterMarketPosition(triggerPrice);
					return;
				}

				if ( lastbar60 == pushList.end )
				{	
					int pullBackSize = (int)(lastPush.pullBackSize/PIP_SIZE);

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH, quotes60[lastbar60].time);
					tv.addNameValue("High Price", new Double(quotes60[lastPush.prePos].high).toString());
					tv.addNameValue("NumPush", new Integer(pushSize).toString());
					tv.addNameValue("PullBackSize", new Integer(pullBackSize).toString());
					trade.addTradeEvent(tv);

					if (( pushSize >= 2 ) && ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE))
					{
						double triggerPrice = quotes60[lastPush.prePos].high;
						//warning("Pullback trade sell triggered on large pull back  @ " + quotes60[lastbar60].time );
						//enterMarketPosition(triggerPrice);
						return;
					}
				}
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
			// entry at tips

			PushList pushList = PushSetup.getDown2PushList(quotes60, startL, lastbar60);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];

				if (( lastPush != null ) && ( data.high > lastPush.pullBack.high ))
				{
					double triggerPrice = lastPush.pullBack.high;
					warning("Breakout base buy triggered at " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					enterMarketPosition(triggerPrice);
					return;
				}

				if ( lastbar60 == pushList.end )
				{	
					int pullBackSize = (int)(lastPush.pullBackSize/PIP_SIZE);

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_LOW, quotes60[lastbar60].time);
					tv.addNameValue("Low Price", new Double(quotes60[lastPush.prePos].low).toString());
					tv.addNameValue("NumPush", new Integer(pushSize).toString());
					tv.addNameValue("PullBackSize", new Integer(pullBackSize).toString());
					trade.addTradeEvent(tv);
					
					if (( pushSize >= 2 ) && ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE))
					{
						double triggerPrice = quotes60[lastPush.prePos].low;
						//warning("Pullback trade sell triggered on large pull back  @ " + quotes60[lastbar60].time );
						//enterMarketPosition(triggerPrice);
						return;
					}

				}
			}
			
		}
		

		return;
	}


	
	public void trackPullBackEntry(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		
		//double avgBarSize240 = Utility.averageBarSize(quotes240);
		//double avgBarSize60 = Utility.averageBarSize(quotes60);
		//int start240 = Utility.findPositionByHour(quotes240, trade.pullBackStartTime, 2 );
		//int detect240 = Utility.findPositionByHour(quotes240, trade.detectTime, 2 );
		//boolean slowDown = false;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// look at large timeframe 240
			/*
			if ( lastbar240 == detect240 )
			{
				if ((quotes60[lastbar240-1].high - quotes60[lastbar240-2].high) < 5 * PIP_SIZE )
				{
					slowDown = true;
					warning("Enter trade on slowing touching " + data.time );
					enterMarketPosition(data.close);
					return;
				}
			}
			else if ( lastbar240 == detect240 + 1 )
			{
				
			}
			else if ( lastbar240 == detect240 + 2 )
			{
				
			}
			else if ( lastbar240 >= detect240 + 3 )
			{
				
			}*/
			
			
			// look at smaller timeframe 60
			int startL = Utility.findPositionByHour(quotes60, trade.pullBackStartTime, Constants.BACK_TO_FRONT );
			startL = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;
			//System.out.println("startL:" + quotesL[startL].time + " " + startL);
			
			// entry at tips
			PushList pushList = Pattern.findPast1Lows1(quotes60, startL, lastbar60 );
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotes60[lastPush.prePos].high - lastPush.pullBack.low;
				double triggerPrice = quotes60[lastPush.prePos].high;


				System.out.println(pushList.toString(quotes60, PIP_SIZE));
				if ( lastbar60 == pushList.end )
				{	
					TradeEvent tv = new TradeEvent(TradeEvent.NEW_HIGH, quotes60[lastPush.prePos].time);
					tv.addNameValue("NumPush", new Integer(pushSize).toString());
					trade.addTradeEvent(tv);

					// three scenarios
					// big diverages
					/*
					if ( lastPush.pullBackSize > 2 * avgBarSize60)
					{
						warning("2 sell triggered: pullbackSize=" + lastPush.pullBackSize + " " +  triggerPrice + quotes60[lastbar60].time + " averageSize:" + avgBarSize60);
						enterMarketPosition(triggerPrice);
						return;
						//enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}
					
					// last did not break out much
				
					if (( prevPush != null ) && ( prevPush.breakOutSize <= 8 * PIP_SIZE ))
					{
						warning("2.1 sell triggered " + triggerPrice + quotes60[lastbar60].time);
						enterMarketPosition(triggerPrice);
						return;
						//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}
					

					// previous breakout size is big, now it is on the cup
					if ( pushSize >= 3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize > 5 * PIP_SIZE )
						{
							warning("3 sell triggered " + triggerPrice + quotes60[lastbar60].time);
							enterMarketPosition(triggerPrice);
							return;
							//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
						}
					}
					
					/*
					if ( pushSize >=4 )
					{
						warning("4 sell triggered " + triggerPrice + quotesL[lastbarL].time);
						enterMarketPosition(triggerPrice);
						return;
						//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}*/
					
				}
				
			}

			
			int highestPoint = Utility.getHigh( quotes60, startL, lastbar60).pos;

			// enter trade if it fall below the starting point
			if ( data.low < quotes60[startL].low )
			{
				if ((quotes60[highestPoint].high - quotes60[startL].low) > 2.5 * FIXED_STOP * PIP_SIZE )
				{
					removeTrade();
					return;
				}

				double triggerPrice = quotes60[startL].low;
				warning("Breakout base sell triggered at " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
				enterMarketPosition(triggerPrice);
				return;
			}
			
			// enter trade when start to resume the move
			/*
			Push anyDownPush = TrendAnalysis.detectLatestMomentum( quotes60, highestPoint, lastbar60-1, Constants.DIRECTION_DOWN, 1.5 * FIXED_STOP * PIP_SIZE );
			if (( anyDownPush != null ) && ( anyDownPush.pushEnd < lastbar60-1))  
			{
				boolean pushDetected = false;
				for ( int j = anyDownPush.pushEnd + 1; j < lastbar60; j++ )
				{
					if ((quotes60[j].low > quotes60[anyDownPush.pushEnd].low) || ( Utility.isUpBar(quotes60[j])))
					{
						pushDetected = true;
						break;
					}
				}
				
				if (( pushDetected ) && ( quotes60[lastbar60].low < quotes60[anyDownPush.pushEnd].high))
				{
					warning("Resuming trend sell triggered at " + data.time );
					enterMarketPosition(quotes60[anyDownPush.pushEnd].low);
					return;
				}
			}*/
			
			return;

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// look at large timeframe 240
			/*
			if ( lastbar240 == detect240 )
			{
				if ((quotes60[lastbar240-2].low - quotes60[lastbar240-1].low) < 5 * PIP_SIZE )
				{
					slowDown = true;
					warning("Enter trade on slowing touching " + data.time );
					enterMarketPosition(data.close);
					return;
				}
			}
			else if ( lastbar240 == detect240 + 1 )
			{
				
			}
			else if ( lastbar240 == detect240 + 2 )
			{
				
			}
			else if ( lastbar240 >= detect240 + 3 )
			{
				
			}*/
			

			
			
			// entry at tips
			int startL = Utility.findPositionByHour(quotes60, trade.pullBackStartTime, Constants.BACK_TO_FRONT );
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			//PushList pushList = SmoothSetup.getDown2PushList(quotes60, startL, lastbar60);
			PushList pushList = Pattern.findPast1Highs1(quotes60, startL, lastbar60 );
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				double triggerPrice = quotes60[lastPush.prePos].low;

				System.out.println(pushList.toString(quotes60, PIP_SIZE));
				if ( lastbar60 == pushList.end )
				{	
					TradeEvent tv = new TradeEvent(TradeEvent.NEW_LOW, quotes60[lastPush.prePos].time);
					tv.addNameValue("NumPush", new Integer(pushSize).toString());
					trade.addTradeEvent(tv);
					
					/*
					if ( lastPush.pullBackSize > 2 * avgBarSize60)
					{
						warning("2 buy triggered: pullbackSize=" + lastPush.pullBackSize + " " +  triggerPrice + quotes60[lastbar60].time +  " averageSize:" + avgBarSize60);
						enterMarketPosition(triggerPrice);
						return;
						//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}

					if (( prevPush != null ) && ( prevPush.breakOutSize <= 8 * PIP_SIZE ))
					{
						warning("2.1 buy triggered " + triggerPrice + quotes60[lastbar60].time);
						enterMarketPosition(triggerPrice);
						return;
						//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}
					
					
					if ( pushSize >= 3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize > 5 * PIP_SIZE )
						{
							warning("3 buy triggered " + triggerPrice + quotes60[lastbar60].time);
							enterMarketPosition(triggerPrice);
							return;
							//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
						}
	
					}
					/*
					if ( pushSize >= 4 )
					{
						warning("4 buy triggered " + triggerPrice + quotesL[lastbarL].time);
						enterMarketPosition(triggerPrice);
						return;
						//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}*/
				}
			}
			
			
			int lowestPoint = Utility.getLow( quotes60, startL, lastbar60).pos;
			
			if ( data.high > quotes60[startL].high )
			{
				if ((quotes60[startL].high - quotes60[lowestPoint].low) > 2.5 * FIXED_STOP * PIP_SIZE )
				{
					removeTrade();
					return;
				}

				double triggerPrice = quotes60[startL].high;
				System.out.println("Breakout base buy triggered at " + data.time +  " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].high );
				enterMarketPosition(triggerPrice);
				return;
				
				//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
			}

			/*
			Push anyUpPush = TrendAnalysis.detectLatestMomentum( quotes60, lowestPoint, lastbar60-1, Constants.DIRECTION_UP, 1.5 * FIXED_STOP * PIP_SIZE );
			
			if (( anyUpPush != null ) && ( anyUpPush.pushEnd < lastbar60-1))  
			{
				boolean pushDetected = false;
				for ( int j = anyUpPush.pushEnd + 1; j < lastbar60; j++ )
				{
					if ((quotes60[j].high < quotes60[anyUpPush.pushEnd].high) || ( Utility.isDownBar(quotes60[j])))
					{
						pushDetected = true;
						break;
					}
				}
				
				if (( pushDetected ) && ( quotes60[lastbar60].high > quotes60[anyUpPush.pushEnd].high))
				{
					warning("Resuming trend buy triggered at " + data.time );
					enterMarketPosition(quotes60[anyUpPush.pushEnd].high);
					return;
				}
			}*/
		}
		

		return;
	}



	
	
	
	public void trackPullBackEntry3(QuoteData data)
	{
		//QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		//int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		//double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);
		PushHighLow lastPush, prevPush;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;

		//int startL = trade.getFirstBreakOutStartPosL(quotesL);

		//int startL = trade.getFirstBreakOutStartPosL(quotesL);
		//String startTime = trade.getFirstBreakOutStartTime();
		//int pullBackStartL = trade.pullBackStartL;
		String startTime = trade.getFirstBreakOutTime();
		int pullBackStartL = Utility.findPositionByHour(quotesL, startTime, Constants.FRONT_TO_BACK );
		
		//int start = Utility.findPositionByHour(quotesL, startTime, Constants.FRONT_TO_BACK );
		//int start2= Utility.findPositionByHour(quotesL, startTime,  Constants.BACK_TO_FRONT );
		
		//int detectPos = Utility.findPositionByHour(quotesL, trade.detectTime,  Constants.BACK_TO_FRONT );
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			//startL = Utility.getLow( quotesL, start, start2).pos;
			//int pullBackStart = Utility.getLow(quotesL, trade.pullBackStartL, trade.detectPos).pos;
			//int pullBackStart = trade.pullBackStartL;
			
			//pullBackStartL = Utility.getLow(quotesL, pullBackStartL-4, pullBackStartL+4).pos;
			pullBackStartL = Utility.getLow(quotesL, pullBackStartL-4, lastbarL-1).pos;
			PushList pushList = PushSetup.getUp1PushList(quotesL, pullBackStartL, lastbarL);
			if ( pushList != null )
			{	
				//System.out.println(bt.direction);
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				lastPullbackSize = quotesL[lastPush.prePos].high - lastPush.pullBack.low;

				if ( lastbarL == pushList.end )
				{
					// send notification email
					
					
					if ( pushSize >= 2 )
					{
						if ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)
						{
							warning("Pullback trade sell triggered push size 2 at " + data.time + " @ " + quotesL[lastPush.prePos].high );
							enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
							return;
						}
					}
					
					if ( pushSize >=3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize < 5 * PIP_SIZE )
						{
							warning("Pullback trade sell triggered push size 3 at " + data.time + " @ " + quotesL[lastPush.prePos].high );
							enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
							return;
						}
					}
					
					
					if ( pushSize >=4 )
					{
						warning("Pullback trade sell triggered push size 4 at " + data.time + " @ " + quotesL[lastPush.prePos].high );
						enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
						return;
					}
				}
				else if ( lastbarL == ( pushList.end - 1))
				{
					
				}
				
				
				if (( data.low < lastPush.pullBack.low ) &&  (lastQuoteData.low > lastPush.pullBack.low)) 
				{

					/*
					BigTrend btt = calculateTrend( quotes240);
					if (!Constants.DIRT_DOWN.equals(btt.direction))
					{
						removeTrade();
						return;
					}*/
					warning("Pullback trade sell triggered < pullback low at " + data.time + " @ " + lastPush.pullBack.low );
					enterTrade( lastPush.pullBack.low, quotesL[lastbarL].time);
					//enterTrade( data.close, quotesL[lastbarL].time);
					return;
				}
			}

			
			if ( data.high > ema20L[lastbarL])
			{
				if ( lastbarL - pullBackStartL > 8 )
				{
					warning("Pullback trade sell triggered 8 bars at " + data.time + " @ " + data.close + " pullback start is " + quotesL[pullBackStartL].time + " trigged: " + quotesL[lastbarL].time + " " + quotesL[lastbarL].close);
					System.out.println(data.toString());
					//trade.entryPrice = data.close;
					//trade.setEntryTime(quotesL[lastbarL].time);
					//enterMarketPosition(trade.entryPrice);
					enterTrade( data.close, quotesL[lastbarL].time);
					return;
				}
			}
			
			if (( data.low < quotesL[pullBackStartL].low ) && (lastQuoteData.low > quotesL[pullBackStartL].low)) 
			{

				QuoteData afterHigh = Utility.getHigh( quotesL, pullBackStartL+1, lastbarL);
				if (( afterHigh != null ) && (( afterHigh.high - quotesL[pullBackStartL].low) > 2.5 * FIXED_STOP * PIP_SIZE ))
				{
					removeTrade();
					return;
				}

				warning("Pullback trade sell triggered < start at " + data.time + " pullback: " + quotesL[pullBackStartL].time + " @ " + quotesL[pullBackStartL].low );
				//trade.positionSize = POSITION_SIZE;
				//trade.entryPrice = quotesL[pullBackStartL].low;
				//trade.entryTime = ((QuoteData) quotesL[lastbarL]).time;
				//enterMarketPosition(quotesL[pullBackStartL].low);
				enterTrade( quotesL[pullBackStartL].low, quotesL[lastbarL].time);
				//enterTrade( data.close, quotesL[lastbarL].time);
				return;
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			// find first downbar after detection
			/*
			int firstUpbar = 0;
			for ( int i = detectPos+1; i < lastbarL; i++ )
				if ( quotesL[i].close > quotesL[lastbarL-1].open )
				{
					firstUpbar = i;
					break;
				}
			
			// if no above 20MA bar between the detection and downbar, enter the trade;
			boolean below = false;
			for ( int i = detectPos + 1; i <= lastbarL-1; i++)
			{
				if (quotesL[i].high < ema20L[i])
					below = true;
			}
				
			if (( firstUpbar != 0 ) && !below )
			{
				if ( data.high > quotesL[firstUpbar].high )
				{
					enterTrade( quotesL[firstUpbar].high, quotesL[lastbarL].time);
					return;
				}
			}*/
			/*
			MABreakOutList mol = MABreakOutTouchSetup.findMABreakOutsDown(quotes240, ema20_240,-1);
			if (( mol != null ) && (mol.getNumOfBreakOuts() > 0 ))
			{	
				System.out.print(mol.toString(quotes240));
				//if ( mol.getBreakOutTimes() > 2 )
					//return null;
				MABreakOut lastBreakOut = mol.getLastMBBreakOut();
				Push pullBack = Utility.findLargestPullBackFromLow(quotes240, lastBreakOut.begin, lastBreakOut.end );
				//System.out.println("pullback size is " + pullBack.pullback/PIP_SIZE);
				if ( pullBack.pullback > 2 * FIXED_STOP * PIP_SIZE )
				{
					removeTrade();
					return;
				}
			}*/

			//startL = Utility.getHigh( quotesL, start, start2).pos;
			//int pullBackStart = Utility.getHigh(quotesL, trade.pullBackStartL, trade.detectPos).pos;
			//int pullBackStart = trade.pullBackStartL;
			pullBackStartL = Utility.getHigh(quotesL, pullBackStartL-4, pullBackStartL+4).pos;
			PushList pushList = PushSetup.getDown1PushList(quotesL, pullBackStartL, lastbarL);

			if ( pushList != null )
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];

				// enter based on push
				if ( lastbarL == pushList.end )
				{	
					// send notification email

					if ( pushSize >= 2 ) 
					{
						if ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)
						{
							enterTrade( quotesL[lastPush.prePos].low, quotesL[lastbarL].time);
							return;
						}
					}
					
					if ( pushSize >= 3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize < 5 * PIP_SIZE )
						{
							enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
							return;
						}
	
					}
					
					if ( pushSize >= 4 )
					{
						enterTrade( quotesL[lastPush.prePos].low, quotesL[lastbarL].time);
						return;
					}
				}

				// enter based on reversal
				if ( data.high > lastPush.pullBack.high )
				{
					/*
					BigTrend btt = calculateTrend( quotes240);
					if (!Constants.DIRT_DOWN.equals(btt.direction))
					{
						removeTrade();
						return;
					}*/
					warning("Pullback trade buy triggered at " + data.time + " @ " + lastPush.pullBack.high + " pullback: " + quotesL[lastPush.prePos].time + " " + quotesL[lastPush.curPos].time + " " + lastPush.pullBack.time );
					//double entryPrice = lastPush.pullBack.high;
					//trade.setEntryTime(quotesL[lastbarL].time);
					//trade.entryPrice = entryPrice;
					//enterMarketPosition(lastPush.pullBack.high);
					enterTrade( lastPush.pullBack.high, quotesL[lastbarL].time);
					return;
				}
			}
				
			
			if ( data.low < ema20L[lastbarL])
			{
				if ( lastbarL - pullBackStartL > 8 )
				{
					warning("Pullback trade buy triggered at long pullback" + data.time + " @ " + data.close);
					//trade.entryPrice = data.close;
					//trade.setEntryTime(quotesL[lastbarL].time);
					//enterMarketPosition(trade.entryPrice);
					enterTrade( data.close, quotesL[lastbarL].time);
					return;
				}
			}
			
			if ( data.high > quotesL[pullBackStartL].high )
			{

				QuoteData afterLow = Utility.getLow( quotesL, pullBackStartL+1, lastbarL);
				if (( afterLow != null ) && (( quotesL[pullBackStartL].high - afterLow.low) > 2.5 * FIXED_STOP * PIP_SIZE ))
				{
					removeTrade();
					return;
				}

				/*
				BigTrend btt = calculateTrend( quotes240);
				if (!Constants.DIRT_UP.equals(btt.direction))
				{
					removeTrade();
					return;
				}*/

				System.out.println("Pullback trade buy triggered at " + data.time +  " pullback: " + quotesL[pullBackStartL].time + " @ " + quotesL[pullBackStartL].high );
				enterTrade( quotesL[pullBackStartL].high, quotesL[lastbarL].time);
				return;
			}
		}
		
	}

	

	private void enterTrade( double entryPrice, String entryTime)
	{
		if ( MODE != Constants.SIGNAL_MODE )
		{
			trade.setEntryTime(entryTime);
			trade.entryPrice = entryPrice;
			enterMarketPosition(entryPrice);
			return;
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	

	public int counting123(Object[] quotes, int begin, int end, int direction)
	{
		int pos = begin + 1;
		int count = 0;

		if (Constants.DIRECTION_UP == direction)
		{
			while (true)
			{
				// while (( pos <= end ) && (((QuoteData) quotes[pos]).high >=
				// ((QuoteData) quotes[pos-1]).high))
				// pos++;
				while ((pos <= end)
						&& !((((QuoteData) quotes[pos]).high < ((QuoteData) quotes[pos - 1]).high) && (((QuoteData) quotes[pos]).low < ((QuoteData) quotes[pos - 1]).low)))
					pos++;

				logger.info(symbol + " count high: " + ((QuoteData) quotes[pos - 1]).high + " at " + ((QuoteData) quotes[pos - 1]).time);
				count++;

				if (pos > end)
					return count;

				// now pos is smaller than pos-1
				double lastHigh = ((QuoteData) quotes[pos - 1]).high;
				while ((pos <= end) && (((QuoteData) quotes[pos]).high <= lastHigh))
					pos++;

				if (pos > end)
					return count;

				pos++;
				// now pos is > lastHigh
			}
		}
		else if (Constants.DIRECTION_DOWN == direction)
		{
			while (true)
			{
				// while (( pos <= end ) && (((QuoteData) quotes[pos]).low <=
				// ((QuoteData) quotes[pos-1]).low))
				// pos++;
				while ((pos <= end)
						&& !((((QuoteData) quotes[pos]).high > ((QuoteData) quotes[pos - 1]).high) && (((QuoteData) quotes[pos]).low > ((QuoteData) quotes[pos - 1]).low)))
					pos++;

				logger.info(symbol + " count low: " + ((QuoteData) quotes[pos - 1]).low + " at " + ((QuoteData) quotes[pos - 1]).time);
				count++;

				if (pos > end)
					return count;

				// now pos is smaller than pos-1
				double lastLow = ((QuoteData) quotes[pos - 1]).low;
				while ((pos <= end) && (((QuoteData) quotes[pos]).low >= lastLow))
					pos++;

				if (pos > end)
					return count;

				pos++;

				// now pos is < lastLow
			}
		}

		return Constants.NOT_FOUND;

	}


	
	
	
	
	
	
	
	
	public void exit123_new9c4( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5_MIN);;
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		
		int LOT_SIZE = POSITION_SIZE/2;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		
		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + " can not find trade or trade entry point!");
			return;
		}


		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes240);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
			if (( trade.getProgramTrailingStop() != 0 ) && ((trade.getProgramTrailingStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramTrailingStop() - data.close)/PIP_SIZE;

			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.high > trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}

			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && ((( trade.entryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 )))     
			{
				if (( data.high > (lowestPointAfterEntry + FIXED_STOP * PIP_SIZE )) 
					&& ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
				{
					//System.out.println(bt.toString(quotesL));
					logger.warning(symbol + " " + " close trade with small tip");
					double reversePrice = lowestPointAfterEntry +  FIXED_STOP * PIP_SIZE;

					boolean reversequalified = true;
					//if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}


					// reverse;
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						warning(" reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
						trade.detectTime = quotes[lastbar].time;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}
					return;
				}
			}			
			
			if (lastbarL < tradeEntryPosL + 2)
			return;

			// gathering parameters
			if (trade.reach2FixedStop == false){
				if((trade.entryPrice - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE){ 	
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					warning(" move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
				maTouches = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
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
			
			
			int pushStart = tradeEntryPosL-1;
			PushHighLow[] phls = Pattern.findPast2Lows(quotesL, pushStart, lastbarL).phls;
			PushHighLow phl = null;
			if ((phls != null) && (phls.length >= 1 ))
			{
				//System.out.println("on tip:" + data.time);
				phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotesL );
				int positive = 0;
				int above20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++)
				{
					if ( macd[j].histogram > 0 )
						positive ++;
					
					if (quotesL[j].low > ema20L[j])
						above20MA++;
				}
				
				double pullback = phl.pullBack.high - quotesL[phl.prePos].low;
				double triggerPrice = quotesL[phl.prePos].low;
				int phl_width = phl.curPos - phl.prePos;
				int numOfPush = phls.length;
				
				if (!exitTradePlaced())
				{
					if ( pullback  > 2 * FIXED_STOP * PIP_SIZE)
					{
						if ( wave2PtL != 0 )
						{
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}
					else if (  pullback  > 1.5 * FIXED_STOP * PIP_SIZE )
					{
						if ( positive > 0 )
						{	
							if ( wave2PtL != 0 )
							{	
								for ( int j = 0; j < phls.length; j++ )
								{
									System.out.println(quotesL[phls[j].prePos].time + " " + quotesL[phls[j].breakOutPos].time + " " + quotesL[phls[j].curPos].time); 									
								}
								warning(data.time + " take prift buy on MACD with pullback > 1.5");
								takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
								takeProfit( adjustPrice(triggerPrice - 30 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
							}
						}					
					}
					else
					{
						if ( positive > 0 ) 
						{
							if (( wave2PtL != 0 ) && (!takeProfit2_set()))
							{	
								double targetPrice = adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								takeProfit2( targetPrice, POSITION_SIZE/2 );
								warning(data.time + " take half prift buy on MACD with pullback < 1.5");
							}
						}

							
						if ( ( wave2PtL != 0 ) && (trade.reach2FixedStop == false) && ( timePassed >= 24 ))
						{
							MATouch[] maTouches2 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 1);

							double prevProfit = trade.entryPrice - quotesL[phl.prePos].low;
							double avgProfit = prevProfit / ( lastbarL - tradeEntryPosL );
							if ( avgProfit > 0 )
							{	
								//double avgPullBack = pullback / ( lastbarL - phl.prePos);
								
								//if (( pullback > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
								if (( maTouches2.length >=4 ) || maTouches1.length >= 6 )
								{
									System.out.println(data.time + " take profit on disporportional pull back");
									takeProfit( adjustPrice(quotesL[phl.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								}
							}
							
							
							PositionToMA ptm = Pattern.countAboveMA(quotesL, ema20L, tradeEntryPosL, lastbarL);
							float numberOfbars = lastbarL-tradeEntryPosL;
							if (ptm.below < ptm.above ) 
							{
								System.out.println(data.time + " take profit on disporportional pull back2");
								takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}

							if ( lastbarL >= tradeEntryPos + 8 )
							{	
								float numAbove = 0;
								for ( int j = tradeEntryPosL+1; j <=lastbarL; j++)
								{	
									if ( quotesL[j].low > trade.entryPrice )
										numAbove += 1;
								}
							
								if ( numAbove/numberOfbars > 0.6 )
								{
									System.out.println(data.time + " take profit on disporportional pull back 3");
									takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
							}
						}
					}
				}
			}

				
			if (!exitTradePlaced())
			{	
				// check on small time frame
				int tradeEntryPosS1 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.FRONT_TO_BACK);
				int tradeEntryPosS2 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.BACK_TO_FRONT);
				int tradeEntryPosS = Utility.getHigh( quotesS, tradeEntryPosS1,tradeEntryPosS2).pos;
				
				PushHighLow phlS = Pattern.findLastNLow(quotesS, tradeEntryPosS, lastbarS, 1);
				if (phlS != null)
				{
					double pullBackDist =  phlS.pullBack.high - quotesS[phlS.prePos].low;
	
					// exit scenario1, large parfit
					if ( ( phlS.curPos - phlS.prePos) <= 48 )
					{
						if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( profit > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
						}
					}
				}
				
				// check if there has been a big run
				double[] ema20S = Indicator.calculateEMA(quotesS, 20);
				double[] ema10S = Indicator.calculateEMA(quotesS, 10);

				if (( ema10S[lastbarS] > ema20S[lastbarS]) && ( ema10S[lastbarS-1] < ema20S[lastbarS-1]))
				{
					//System.out.println(data.time + " cross over detected " + quotesS[lastbarS].time);
					// just cross over;
					int lastCrossDown = Pattern.findLastMACrossDown(ema10S, ema20S, lastbarS-1, 8);
					//if (lastCrossUp != Constants.NOT_FOUND )
					//System.out.println(data.time + " last cross up " + quotesS[lastCrossUp].time);
					
					if ((lastCrossDown != Constants.NOT_FOUND )&& (( ema10S[lastCrossDown] - ema10S[lastbarS-1]) > 5 * PIP_SIZE * FIXED_STOP ))
					{
						warning(data.time + " cross over after large rundown detected " + quotesS[lastbarS].time);
						takeProfit( quotesS[lastbarS].close, trade.remainingPositionSize );
					}
				}
			}

			
			// move stop
			if (trade.reach2FixedStop == true)
			{	
				if (phl != null)
				{
					// count the pull bacck bars
					int pullbackcount = 0;
					for ( int j = phl.prePos+1; j < phl.curPos; j++)
						if ( quotesL[j+1].high > quotes[j].high)
							pullbackcount++;
					
					//System.out.println("pullback count=" + pullbackcount);
					//if ( pullbackcount >= 2 )
					{
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_UP);
						if ( stop < trade.stop )
						{
							System.out.println(symbol + " " + quotes[lastbar].time + " move stop to " + stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							//trade.lastStopAdjustTime = data.time;
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			profitInRisk =  ( data.close - trade.stop )/PIP_SIZE;
			if (( trade.getProgramTrailingStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramTrailingStop() )/PIP_SIZE;

			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.low < trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}

			
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && ((( highestPointAfterEntry - trade.entryPrice) < FIXED_STOP * PIP_SIZE *0.3 ))            )/*      || 
				(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - trade.entryPrice) < FIXED_STOP * PIP_SIZE ))*/
			{
				if (( data.low <  (highestPointAfterEntry - FIXED_STOP * PIP_SIZE ))
					&& ( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					// reverse;
					System.out.println(bt.toString(quotesL));
					logger.warning(symbol + " " + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  FIXED_STOP * PIP_SIZE;
					
					boolean reversequalified = true;
					//if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					/*
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}*/

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						logger.warning(symbol + " " + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						logger.warning(symbol + " " + " reverse opportunity detected");
						createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
						trade.detectTime = quotes[lastbar].time;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}
					return;
				}
			}

			
			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.reach2FixedStop == false)
			{
				if /*((( Constants.DIRT_DOWN.equals(bt.direction) ||  Constants.DIRT_DOWN_SEC_2.equals(bt.direction))
					&& ((quotes[lastbar].high - trade.entryPrice) >= FIXED_STOP * PIP_SIZE))
				    ||*/  ((quotes[lastbar].high - trade.entryPrice) >= 2 * FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					logger.warning(symbol + " "  + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
				}
			}
			

			int wave2PtL = 0;
			int first2above = Pattern.findNextPriceConsectiveAboveMA(quotesL, ema20L, tradeEntryPosL, 2);
			int first2below = Pattern.findNextPriceConsectiveBelowMA(quotesL, ema20L, tradeEntryPosL, 2);
			if (( first2below > 0 ) && ( first2below < first2above ))
			{
				wave2PtL = -1;
			}
			else
			{	
				maTouches = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 2);
				if ( !trade.type.equals(Constants.TRADE_CNT))
				{
					if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
					{
						wave2PtL =  maTouches[0].touchBegin;
					}
				}
				else
				{
					if (( maTouches.length > 1 ) )
					{
						if ( maTouches[1].touchBegin != 0 )
						{
							wave2PtL =  maTouches[1].touchBegin;
						}
					}
					else if ( maTouches.length > 0 )
					{
						if ( ( maTouches[0].highEnd != 0 ) && ((  maTouches[0].highEnd -  maTouches[0].highBegin) >= 12))
						{
							wave2PtL =  maTouches[0].highEnd + 1;
						}
					}
				}
			}

			if ( wave2PtL != 0 )
			{
				//System.out.println("first touch 20MA point:" + quotesL[wave2PtL].time);
			}
			
			int pushStart = tradeEntryPosL-1;
			PushHighLow[] phls = Pattern.findPast2Highs(quotesL, pushStart, lastbarL).phls;
			PushHighLow phl = null;
			if ((phls != null) && (phls.length >=1))
			{
				phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotesL );
				int negatives = 0;
				int below20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++)
				{
					if ( macd[j].histogram < 0 )
						negatives ++;

					if (quotesL[j].high < ema20L[j])
						below20MA++;
				}
				
				double pullback = quotesL[phl.prePos].high - phl.pullBack.low;
				double triggerPrice = quotesL[phl.prePos].high;
				int phl_width = phl.curPos - phl.prePos;
				int numOfPush = phls.length;
				
				//System.out.println("number of push is " + numOfPush);
				if (!exitTradePlaced())
				{
					if ( pullback  > 2 * FIXED_STOP * PIP_SIZE)
					{
						if ( wave2PtL != 0 )
						{
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
					}
					else if (  pullback  > 1.5 * FIXED_STOP * PIP_SIZE )
					{
						if ( negatives > 0 )
						{	
							if ( wave2PtL != 0 )
							{	
								warning(data.time + " take prift buy on MACD with pullback > 1.5");
								takeProfit2( adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
								takeProfit( adjustPrice(triggerPrice + 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
							}
						}					
					}
					else
					{
						if ( negatives > 0 ) 
						{
							if (( wave2PtL != 0 ) && (!takeProfit2_set()))
							{	
								double targetPrice = adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								takeProfit2( targetPrice, POSITION_SIZE/2 );
								warning(data.time + " take half prift sell on MACD with pullback < 1.5");
							}
						}

							
						if ( ( wave2PtL != 0 ) && (trade.reach2FixedStop == false) && ( timePassed >= 24 ))
						{
							MATouch[] maTouches2 = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 1);
							// Exit Scenario 2:  disporportional pullback
							double prevProfit = quotesL[phl.prePos].high - trade.entryPrice;
							double avgProfit = prevProfit / ( lastbarL - tradeEntryPosL );
							if ( avgProfit > 0 )
							{	
								//double avgPullBack = pullback / ( lastbarL - phl.prePos);
								//System.out.println(data.time + " exit detected average profit:" + avgProfit + " pullback avg:" + avgPullBack + " " + avgPullBack/avgProfit);
		
								//if (( pullback > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
								if (( maTouches2.length >=4 ) || maTouches1.length >= 6 )
								{
									System.out.println(data.time + " take profit on disporportional pull back");
									takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
							}
							
							
							PositionToMA ptm = Pattern.countAboveMA(quotesL, ema20L, tradeEntryPosL, lastbarL);
							float numberOfbars = lastbarL-tradeEntryPosL;
							if (ptm.below > ptm.above ) 
							{
								System.out.println(data.time + " take profit on disporportional pull back 2");
								takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}

						
							if ( lastbarL >= tradeEntryPos + 8 )
							{	
								float numBelow = 0;
								for ( int j = tradeEntryPosL+1; j <=lastbarL; j++)
								{	
									if ( quotesL[j].high < trade.entryPrice )
										numBelow += 1;
								}
							
								if ( numBelow/numberOfbars > 0.6 )
								{
									System.out.println(data.time + " take profit on disporportional pull back 3");
									takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
							}

						}
					}
				}
			}	


			// smaller timefram for detecting sharp pullbacks
			if (!exitTradePlaced())
			{	
				int tradeEntryPosS1 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.FRONT_TO_BACK);
				int tradeEntryPosS2 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.BACK_TO_FRONT);
				int tradeEntryPosS = Utility.getLow( quotesS, tradeEntryPosS1,tradeEntryPosS2).pos;
				
				PushHighLow phlS = Pattern.findLastNHigh(quotesS, tradeEntryPosS, lastbarS, 1);
				if (phlS != null)
				{
					double pullBackDist =  quotesS[phlS.prePos].high - phlS.pullBack.low;
					
					// exit scenario1, large parfit
					if ( ( phlS.curPos - phlS.prePos) <= 48 )
					{
						if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( profit > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
						}
					}
				}

				// check if there has been a big run
				double[] ema20S = Indicator.calculateEMA(quotesS, 20);
				double[] ema10S = Indicator.calculateEMA(quotesS, 10);

				if (( ema10S[lastbarS] < ema20S[lastbarS]) && ( ema10S[lastbarS-1] > ema20S[lastbarS-1]))
				{
					int lastCrossUp = Pattern.findLastMACrossUp(ema10S, ema20S, lastbarS-1, 8);
					//if (lastCrossUp != Constants.NOT_FOUND )
					//System.out.println(data.time + " last cross up " + quotesS[lastCrossUp].time);
					
					if ((lastCrossUp != Constants.NOT_FOUND )&& ((ema10S[lastbarS-1] - ema10S[lastCrossUp]) > 5 * PIP_SIZE * FIXED_STOP ))
					{
						warning(data.time + " cross over after large runup detected " + quotesS[lastbarS].time);
						takeProfit( quotesS[lastbarS].close, trade.remainingPositionSize );
					}
				}
			}

			
			// move stop
			if (trade.reach2FixedStop == true)
			{	
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_DOWN);
					if ( stop > trade.stop )
					{
						System.out.println(symbol + quotes[lastbar].time + " move stop to " + stop );
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						//trade.lastStopAdjustTime = data.time;
						warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
					}
				}
			}
		}
	}
	


	
	
	
	
	
	

	
	
	

	
	
	
	

	
	
	
	
	
	


	
	

	
	// this is the first half profit taking
	protected void takeProfit2( double price, int size )
	{
		if (trade.targetPlaced1 == true)  // order already placed earlier
			return;

		if ( !trade.targetPlaced2)
		{	
			trade.targetPrice2 = price;
			trade.targetPos2 = size;
			String action = Constants.ACTION_BUY;
			if (trade.action.equals(Constants.ACTION_BUY))
				action = Constants.ACTION_SELL;
			
			trade.targetId2 = placeLmtOrder(action, trade.targetPrice2, trade.targetPos2, null);
			warning("take profit 2 " + action + " target order placed@ " + trade.targetPrice2 + " " + trade.targetPos2 );
	
			trade.targetPlaced2 = true;
			return;
		}
	}
	
	protected boolean takeProfit2_set()
	{
		if (trade.targetPlaced1 == true)  // order already placed earlier
			return true;

		return trade.targetPlaced2;
	}

	// this is to take all profit
	protected boolean exitTradePlaced()
	{
		if ( trade == null )
			return true;
		
		return trade.targetPlaced1;
	}
	
	protected void takeProfit( double price, int size )
	{
		if (trade.targetPlaced1 == true)  // order already placed earlier
			return;
		
		if ( trade.targetPlaced2 == true )
		{	
			if ( size + trade.targetPos2 > trade.POSITION_SIZE )
				size = trade.POSITION_SIZE - trade.targetPos2;
		}
		
		String action = Constants.ACTION_BUY;
		if (trade.action.equals(Constants.ACTION_BUY))
			action = Constants.ACTION_SELL;
		
		trade.targetPrice1 = price;
		trade.targetPos1 = size;//trade.remainingPositionSize;
		trade.targetId1 = placeLmtOrder(action, trade.targetPrice1, trade.targetPos1, null);
		warning("take profit remainning profit " + action + " target order placed@ " + trade.targetPrice1 + " " + trade.targetPos1 );

		trade.targetPlaced1 = true;
		return;
	}


	

	
	protected Trade findTradeHistory(String action, String firstBreakOutStartTime)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ( t.action.equals(action) && (firstBreakOutStartTime.equals(t.getFirstBreakOutStartTime())))
				return t;
		}

		return null;

	}

	protected Trade findTradeHistory( String detectTime)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ( detectTime.equals(t.detectTime))
				return t;
		}

		return null;

	}
	
	
	
	
	protected void moveStop( double stopPrice )
	{
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			double stop = adjustPrice(stopPrice, Constants.ADJUST_TYPE_UP);
			cancelOrder(trade.stopId);
			trade.stop = stopPrice;
			trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
			warning(" stop moved to " + stop + " orderId:" + trade.stopId );
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			double stop = adjustPrice(stopPrice, Constants.ADJUST_TYPE_DOWN);
			cancelOrder(trade.stopId);
			trade.stop = stopPrice;
			trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
			warning(" stop moved to " + stop + " orderId:" + trade.stopId );
		}
	}

	
	
	public void createTradeTargetOrder( double quantity, double price )
	{
		if (( trade != null ) && trade.status.equals(Constants.STATUS_PLACED))
		{
			if ( trade.targetId != 0 )
				cancelTargets();

			trade.targetPrice = price;
			trade.targetPos = (int)(POSITION_SIZE*quantity);
			trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, null);
		}
		else
		{
			System.out.println(symbol + " Set Target, trade does not exist");
			System.out.println(symbol + " Set Target, trade does not exist");
			System.out.println(symbol + " Set Target, trade does not exist");
		}
	}
	
	/*
	public void createTrade( String action, double quantity, double price1 )
	{
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length -1;
		
		if ( trade == null )
		{
			createOpenTrade("MANUAL", action);
			trade.status = Constants.STATUS_DETECTED;
			trade.positionSize = (int)(POSITION_SIZE*quantity);
			
			enterLimitPosition1(price1, trade.positionSize, 0); 
		}
		else
		{
			System.out.println(symbol + " Place Trade, trade already exist");
			System.out.println(symbol + " Place Trade, trade already exist");
			System.out.println(symbol + " Place Trade, trade already exist");
		}
	}*/
	
	

	public boolean inTradingTime( int hour, int min )
	{
		//return true;
		int minute = hour * 60 + min;
		if (( minute >= 0) && (minute <= 420))//600
									return true;
								else
									return false;		
		/*
		switch ( TIME_ZONE)
		{
			case 23:  	if (( minute >= 90) && (minute <= 420))//600
							return true;
						else
							return false;
			
			case 12:  	if ((minute >= 1080) || (minute <= 420))//600
							return true;
						else
							return false;
			
			default:	return false;

		}*/
	}

	
	public Trade findPivotPoints(QuoteData data, int direction)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		
		int GO_BACK = (lastbar > 72)?72:lastbar;
		int PREVIOUS_BAR = 2;
		double minimum_pullback = FIXED_STOP * 3 * PIP_SIZE;
		
		if ( direction == Constants.DIRECTION_UP )
		{
			outerloop:for ( int i = lastbar-3; i > lastbar - GO_BACK; i--)
			{	
				// last bar should be the highest
				if (quotes[lastbar].high <= quotes[i].high)
					continue;
				
				// the last bar has to be the first one higher than this bar
				double low = 999;
				for ( int j = i + 1; j < lastbar; j++)
				{
					if ( quotes[j].high > quotes[i].high )
						continue outerloop;
					if ( quotes[j].high > quotes[lastbar].high )
						continue outerloop;
					if ( quotes[j].low < low )
						low = quotes[j].low;
				}
				
				if (( minimum_pullback > 0) && ((quotes[i].high - low ) < minimum_pullback ))
					continue outerloop;
				
				// should be plenty of bars before this i bar that is lowerer than this bar
				//if ( lastbar - i > 5)
				//	PREVIOUS_BAR = (int)((lastbar - i ) * 1.5);
				
				for ( int j = i - 1; j > i - PREVIOUS_BAR; j--)
					if ( quotes[j].high > quotes[i].high )
						continue outerloop;
				
				String pivot_found = symbol + " Pivot UP found: " + quotes[lastbar].time + " " + quotes[i].time;

				if (!pivots_set.contains(pivot_found))
				{
					System.out.println(pivot_found);
					pivots_set.add(pivot_found);

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = data.time;

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH,  quotes[lastbar].time);
					tv.addNameValue("LastHigh", quotes[i].time + " " + quotes[i].high); 
					trade.addTradeEvent(tv);

					enterMarketPosition(quotes[i].high);
					return trade;					
				}
			}
		}
		else if ( direction == Constants.DIRECTION_DOWN )
		{
			outerloop: for ( int i = lastbar-3; i > lastbar - GO_BACK; i--)
			{	
				// last bar should be the lowest
				if (quotes[lastbar].low >= quotes[i].low)
					continue;
				
				// the last bar has to be the first one higher than this bar
				double high = 0;
				for ( int j = i + 1; j < lastbar; j++)
				{
					if ( quotes[j].low < quotes[i].low )
						continue outerloop;
					if ( quotes[j].low < quotes[lastbar].low )
						continue outerloop;
					if ( quotes[j].high > high )
						high = quotes[j].high;
				}

				if (( minimum_pullback > 0) && ((high - quotes[i].low ) < minimum_pullback ))
					continue outerloop;
				
				
				// should be plenty of bars before this i bar that is lowerer than this bar
				//if ( lastbar - i > 5)
				//	PREVIOUS_BAR = (int)((lastbar - i ) * 1.5);
				
				for ( int j = i - 1; j > i - PREVIOUS_BAR; j--)
					if ( quotes[j].low < quotes[i].low )
						continue outerloop;
				
				String pivot_found = symbol + " Pivot DOWN found: " + quotes[lastbar].time + " " + quotes[i].time;

				if (!pivots_set.contains(pivot_found))
				{
					System.out.println(pivot_found);
					pivots_set.add(pivot_found);

					createOpenTrade(Constants.TRADE_RV, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = data.time;

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_LOW,  quotes[lastbar].time);
					tv.addNameValue("LastLow", quotes[i].time + " " + quotes[i].low); 
					trade.addTradeEvent(tv);

					enterMarketPosition(quotes[i].high);
					return trade;					
				}
			}
		}

		return null;
	
	}

	
	public void exit123_close_monitor( QuoteData data )
	{
//		QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);;
//		int lastbar5 = quotes5.length - 1;
//		QuoteData[] quote15 = getQuoteData(Constants.CHART_15_MIN);
//		int lastbar15 = quote15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		double avgSize60 = BarUtil.averageBarSize( quotes60 );

		//System.out.println("avg="+avgSize60);
		int tradeEntryPos60 = Utility.findPositionByHour(quotes60, trade.entryTime, 2 );
		int timePassed = lastbar60 - tradeEntryPos60; 

		Date now = new Date(data.timeInMillSec);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime( now );
		int weekday = calendar.get(Calendar.DAY_OF_WEEK);

		/*********************************************************************
		 *  status: closed
		 *********************************************************************/
		if  (Constants.STATUS_CLOSED.equals(trade.status)){

			try{
				Date closeTime = IBDataFormatter.parse(trade.closeTime);
				Date currTime = IBDataFormatter.parse(data.time);
				
				if ((currTime.getTime() - closeTime.getTime()) > (0 * 60000L)){
					warning("trade closed, remove trade");
					removeTrade();
					return;
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}

			// not to do anything at the moment
			return;
		}
		

		/*********************************************************************
		 *  status: stopped out, check to reverse
		 *********************************************************************/
		if (Constants.STATUS_STOPPEDOUT.equals(trade.status)){ 	

			if ( reverse_trade != true ){
				info("reverse not turned on, remove trade");
				removeTrade();
				return;
			}

			//processAfterHitStopLogic_c();
			double prevStop = trade.stop;
			String prevAction = trade.action;
			String prevType = trade.type;
			String prevEntryTime = trade.getEntryTime();
			double prevEntryPrice = trade.entryPrice;

			if (prevType == Constants.TRADE_CNT ){
				info("prev trade was CNT, reverse does not apply");
				removeTrade();
				return;
			}

			info("reverse trade is set to trade, try to reverse the trade");
			double reversePrice = prevStop;
			boolean reversequalified = false;

			//  look to reverse if it goes against me soon after entry
			//BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes60);
			BigTrend bt2 = TrendAnalysis.determineBigTrend2050_2( quotes60);
			if (Constants.ACTION_SELL.equals(prevAction)){
				double lowestPointAfterEntry = Utility.getLow(quotes60, tradeEntryPos60, lastbar60).low;
				info("low point after entry is " + lowestPointAfterEntry + " entry price:" + prevEntryPrice); 
				
				//if ((( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 ) || 
					//(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) && (( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE ))) 
				if ((( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.275 ))/* && 
					( Constants.DIRECTION_UP == bt2.dir )) */
				{
					//System.out.println(bt.toString(quotes60));
					info("close trade with small tip, reverse trade to buy qualified");
					System.out.println(bt2.toString(quotes60));
					reversequalified = true;
				}
			}
			else if (Constants.ACTION_BUY.equals(prevAction)){
				double highestPointAfterEntry = Utility.getHigh(quotes60, tradeEntryPos60, lastbar60).high;
				info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 
 
				//if ((( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE * 0.3 ) ||
				    //(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE )))
				if ((( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE * 0.275 ))/* &&
					( Constants.DIRECTION_DOWN == bt2.dir )) */
				{
					info("close trade with small tip, reverse trade to sell qualified");
					System.out.println(bt2.toString(quotes60));
					reversequalified = true;
				}
			}
			
			// reverse;
			if ( reversequalified ){
				removeTrade();
				info("close trade with small tip, reverse trade qualified");
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL.equals(prevAction)?Constants.ACTION_BUY:Constants.ACTION_SELL);
				enterMarketPosition(reversePrice, 0);
				return;
			}else{
				// stay to see if there is further opportunity
				trade.closeTime = data.time;
				trade.status = Constants.STATUS_CLOSED;
				return;
			}
		}

		
		if (!(Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_PARTIAL_FILLED.equals(trade.status)))
			return; 	
		
		// detect weakness after entry
		
		if ( lastbar60 == tradeEntryPos60 +2 ){
			if (Constants.ACTION_SELL.equals(trade.action) && (quotes60[tradeEntryPos60+1].low > quotes60[tradeEntryPos60].low)
				&& ((trade.entryPrice - quotes60[tradeEntryPos60].low) < PIP_SIZE * FIXED_STOP / 3)){ 
				
				if ( trade.weakBreakOut == false ){
					int breakoutPip = (int)((trade.entryPrice - quotes60[tradeEntryPos60].low)/PIP_SIZE);
					super.strategy.sentAlertEmail(AlertOption.weakbreakout, trade.symbol + " " + trade.action, trade );
					trade.weakBreakOut = true;
	
					
					/*closePositionByMarket( trade.remainingPositionSize, data.close );
					removeTrade();
					return;*/
					/*
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL.equals(prevAction)?Constants.ACTION_BUY:Constants.ACTION_SELL);
					enterMarketPosition(data.close);
					return;*/
					//trade.weakBreakOut = true;
					
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action) && (quotes60[tradeEntryPos60+1].high < quotes60[tradeEntryPos60].high)
				&& (( quotes60[tradeEntryPos60].low - trade.entryPrice) < PIP_SIZE * FIXED_STOP / 3)){ 
				if ( trade.weakBreakOut == false ){
					int breakoutPip = (int)((quotes60[tradeEntryPos60].low - trade.entryPrice)/PIP_SIZE);
					super.strategy.sentAlertEmail(AlertOption.weakbreakout, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
					trade.weakBreakOut = true;

					
					/*closePositionByMarket( trade.remainingPositionSize, data.close );
					removeTrade();
					return;*/
					/*
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL.equals(prevAction)?Constants.ACTION_BUY:Constants.ACTION_SELL);
					enterMarketPosition(data.close);
					return;*/
				}
			}	
		}
		
		
		
		
		/*********************************************************************
		 *  add additional positions
		 *********************************************************************/
		if ((average_up == true ) && (trade.averageUp == false ))
		{
			if (Constants.ACTION_SELL.equals(trade.action) &&  (( trade.entryPrice - data.low ) > FIXED_STOP * PIP_SIZE )){
				enterMarketPositionAdditional(trade.entryPrice - FIXED_STOP * PIP_SIZE, POSITION_SIZE);
				trade.averageUp = true;
			}
			else if (Constants.ACTION_BUY.equals(trade.action) &&  (( data.high - trade.entryPrice ) > FIXED_STOP * PIP_SIZE )){
				enterMarketPositionAdditional(trade.entryPrice + FIXED_STOP * PIP_SIZE, POSITION_SIZE);
				trade.averageUp = true;
			}
		}

		
		/*********************************************************************
		 *  status: detect big spike moves
		 *********************************************************************/
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (( quotes60[lastbar60].open - quotes60[lastbar60].close ) > 3 * avgSize60 ){
				super.strategy.sentAlertEmail(AlertOption.big_rev_bar, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
			}
			else if (( quotes60[lastbar60].close - quotes60[lastbar60].open ) > 3 * avgSize60 ){
				super.strategy.sentAlertEmail(AlertOption.big_bar, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (( quotes60[lastbar60].close - quotes60[lastbar60].open ) > 3 * avgSize60 ){
				super.strategy.sentAlertEmail(AlertOption.big_bar, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
			}
			else if (( quotes60[lastbar60].open - quotes60[lastbar60].close ) > 3 * avgSize60 ){
				super.strategy.sentAlertEmail(AlertOption.big_rev_bar, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
			}
		}	

		
		
		/*********************************************************************
		 *  status: detect profit and move stop
		 *********************************************************************/
		//double profit = Math.abs( quotes60[lastbar60].close - trade.entryPrice)/ PIP_SIZE;
		double profitInPip = 0;
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			profitInPip = (trade.entryPrice - data.low)/PIP_SIZE;
			if (profitInPip > FIXED_STOP )
				trade.reach1FixedStop = true;

			/*if (!trade.reach2FixedStop && (profitInPip > 2 * FIXED_STOP)){
				warning("place break even order");
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
			}*/
					
			//profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
			//if (( trade.getProgramTrailingStop() != 0 ) && ((trade.getProgramTrailingStop() - data.close)/PIP_SIZE < profitInRisk ))
			//	profitInRisk = (trade.getProgramTrailingStop() - data.close)/PIP_SIZE;
			/*
			trade.adjustProgramTrailingStop(data);
			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.high > trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}*/
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			profitInPip = ( data.high - trade.entryPrice )/PIP_SIZE;
			if (profitInPip > FIXED_STOP )
				trade.reach1FixedStop = true;

			/*
			if (!trade.reach2FixedStop && (profitInPip > 2 * FIXED_STOP)){
				warning("place break even order");
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
			}*/

			//profitInRisk =  ( data.close - trade.stop )/PIP_SIZE;
			//if (( trade.getProgramTrailingStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
			//	profitInRisk = ( data.close - trade.getProgramTrailingStop() )/PIP_SIZE;

			/*
			trade.adjustProgramTrailingStop(data);
			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.low < trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}*/
		}
		

		
		
		//if (lastbar60 < tradeEntryPos60 + 2)
		//return;

		/*********************************************************************
		 *  status: detect peaks
		 *********************************************************************/
		if (Constants.ACTION_SELL.equals(trade.action)){
			int pushStart = (lastbar60 - tradeEntryPos60 > 4)? tradeEntryPos60+4: lastbar60;
			pushStart = Utility.getHigh(quotes60, tradeEntryPos60-12, pushStart).pos;
			PushList pushList = PushSetup.findPastLowsByX(quotes60, pushStart, lastbar60, 1);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0)){

				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes60[lastPush.prePos].low;
				PushHighLow phl = lastPush;
				double pullback = phl.pullBack.high - quotes60[phl.prePos].low;
				int peakwidth = phl.curPos - phl.prePos;
				pushList.calculatePushMACD( quotes60);
				int positive = phls[lastPushIndex].pullBackMACDPositive;

				TradeEvent tv = new TradeEvent(TradeEvent.NEW_LOW, quotes60[lastbar60].time);
				tv.addNameValue("Low Price", new Double(quotes60[phl.prePos].low).toString());
				tv.addNameValue("NumPush", new Integer(numOfPush).toString());
				trade.addTradeEvent(tv);
				super.strategy.sentAlertEmail(AlertOption.new_low, trade.symbol + " " + trade.action + " pushNum" + numOfPush + " " + quotes60[phl.prePos].low, trade);
				
				if ( timePassed  > 60 ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new low after 60 hours " + quotes60[phl.prePos].low, trade);
				}
				if ( weekday == Calendar.FRIDAY ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new low on friday " + quotes60[phl.prePos].low, trade );
				}
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				//if (!trade.finalPeakExisting &&( pullback  > 2 * FIXED_STOP * PIP_SIZE) && ( timePassed  > 84 ) && ( peakwidth < 12 )) 
				/*if (!trade.finalPeakExisting &&( pullback  > 4 * avgSize60) && ( timePassed  > 60 ) && ( positive < 0 )){ 
					double exitTargetPrice =  adjustPrice((triggerPrice - FIXED_STOP/2 * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					createTradeTargetOrder( trade.remainingPositionSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}*/
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action)){
			int pushStart = (lastbar60 - tradeEntryPos60 > 4)? tradeEntryPos60+4: lastbar60;
			pushStart = Utility.getLow(quotes60, tradeEntryPos60-12, pushStart).pos;
			PushList pushList = PushSetup.findPastHighsByX(quotes60, pushStart, lastbar60, 1);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0) ){

				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes60[lastPush.prePos].high;
				PushHighLow phl = lastPush;
				double pullback = quotes60[phl.prePos].high - phl.pullBack.low;
				int peakwidth = phl.curPos - phl.prePos;
				pushList.calculatePushMACD( quotes60);
				int negatives = phls[lastPushIndex].pullBackMACDNegative;

				TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH, quotes60[lastbar60].time);
				tv.addNameValue("High Price", new Double(quotes60[phl.prePos].high).toString());
				tv.addNameValue("NumPush", new Integer(numOfPush).toString());
				super.strategy.sentAlertEmail(AlertOption.new_high, trade.symbol + " " + trade.action + " pushNum" + numOfPush + " " + quotes60[phl.prePos].high, trade);

				if ( timePassed  > 60 ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new high after 60 hours " + quotes60[phl.prePos].high, trade);
				}
				if ( weekday == Calendar.FRIDAY ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new high on friday " + quotes60[phl.prePos].high, trade);
				}
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				//if (!trade.finalPeakExisting && ( pullback  > 2 * FIXED_STOP * PIP_SIZE) && ( timePassed  > 84 ) && ( peakwidth < 12 )) 
				/*if (!trade.finalPeakExisting &&( pullback  > 4 * avgSize60 ) && ( timePassed  > 60 ) && ( negatives > 0 )){ 
					double exitTargetPrice =  adjustPrice((triggerPrice + FIXED_STOP/2 * PIP_SIZE ), Constants.ADJUST_TYPE_UP);
					createTradeTargetOrder( trade.remainingPositionSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}*/
			}
		}


		
		/*********************************************************************
		 *  status: detect small peaks for exit
		 *********************************************************************/
		/*
		if ( (STRATEGY.sftest.friday_10) && (trade.finalPeakExisting == false )){  
		
			double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
	
			if (Constants.ACTION_SELL.equals(trade.action)){
				int prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes60, ema20_60, lastbar60, 2);
				PushList pushList = PushSetup.findPastLowsByX(quotes60, prevUpPos, lastbar15, 1);
				if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0)){
					PushHighLow[] phls = pushList.phls;
					int lastPushIndex = phls.length - 1;
					PushHighLow lastPush = phls[phls.length - 1]; 
					PushHighLow phl = lastPush;
					double pullback = phl.pullBack.high - quotes60[phl.prePos].low;
					double triggerPrice = quotes60[lastPush.prePos].low;
	
					if (pullback > 0.8 * FIXED_STOP * PIP_SIZE){
						double exitTargetPrice =  adjustPrice((triggerPrice - PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
						info("place final exiting order " + exitTargetPrice);
						createTradeTargetOrder( trade.remainingPositionSize, exitTargetPrice );
						trade.finalPeakExisting = true;
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action)){
				int prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes60, ema20_60, lastbar60, 2);
				PushList pushList = PushSetup.findPastHighsByX(quotes60, prevDownPos, lastbar60, 1);
				if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0) ){
					PushHighLow[] phls = pushList.phls;
					int lastPushIndex = phls.length - 1;
					PushHighLow lastPush = phls[phls.length - 1]; 
					PushHighLow phl = lastPush;
					double pullback = quotes60[phl.prePos].high - phl.pullBack.low;
					double triggerPrice = quotes60[lastPush.prePos].high;
	
					if (pullback > 0.8* FIXED_STOP * PIP_SIZE){
						System.out.println("Existing peak detected" + data.close + " " + pullback/PIP_SIZE + " " + triggerPrice);
						double exitTargetPrice =  adjustPrice((triggerPrice + PIP_SIZE * (FIXED_STOP/5)), Constants.ADJUST_TYPE_UP);
						info("place final exiting order " + exitTargetPrice);
						createTradeTargetOrder( trade.remainingPositionSize, exitTargetPrice );
						trade.finalPeakExisting = true;
					}
				}
			}
		}*/

		
		
		
		/******************************************************************************
		// smaller timefram for detecting sharp pullbacks
		 * ****************************************************************************/
		/*
		if (!exitTradePlaced())
		{	
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				int tradeEntryPosS1 = Utility.findPositionByMinute( quotes5, trade.entryTime, Constants.FRONT_TO_BACK);
				int tradeEntryPosS2 = Utility.findPositionByMinute( quotes5, trade.entryTime, Constants.BACK_TO_FRONT);
				int tradeEntryPosS = Utility.getHigh( quotes5, tradeEntryPosS1,tradeEntryPosS2).pos;
				
				PushHighLow phlS = Pattern.findLastNLow(quotes5, tradeEntryPosS, lastbar5, 1);
				if (phlS != null)
				{
					double pullBackDist =  phlS.pullBack.high - quotes5[phlS.prePos].low;
		
					// exit scenario1, large parfit
					if ( ( phlS.curPos - phlS.prePos) <= 48 )
					{
						if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotes5[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( profitInPip > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								takeProfit( adjustPrice(quotes5[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
						}
					}
				}
				
				// check if there has been a big run
				double[] ema20S = Indicator.calculateEMA(quotes5, 20);
				double[] ema10S = Indicator.calculateEMA(quotes5, 10);
		
				if (( ema10S[lastbar5] > ema20S[lastbar5]) && ( ema10S[lastbar5-1] < ema20S[lastbar5-1]))
				{
					//System.out.println(data.time + " cross over detected " + quotesS[lastbarS].time);
					// just cross over;
					int lastCrossDown = Pattern.findLastMACrossDown(ema10S, ema20S, lastbar5-1, 8);
					//if (lastCrossUp != Constants.NOT_FOUND )
					//System.out.println(data.time + " last cross up " + quotesS[lastCrossUp].time);
					
					if ((lastCrossDown != Constants.NOT_FOUND )&& (( ema10S[lastCrossDown] - ema10S[lastbar5-1]) > 5 * PIP_SIZE * FIXED_STOP ))
					{
						warning(data.time + " cross over after large rundown detected " + quotes5[lastbar5].time);
						takeProfit( quotes5[lastbar5].close, trade.remainingPositionSize );
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				int tradeEntryPosS1 = Utility.findPositionByMinute( quotes5, trade.entryTime, Constants.FRONT_TO_BACK);
				int tradeEntryPosS2 = Utility.findPositionByMinute( quotes5, trade.entryTime, Constants.BACK_TO_FRONT);
				int tradeEntryPosS = Utility.getLow( quotes5, tradeEntryPosS1,tradeEntryPosS2).pos;
				
				PushHighLow phlS = Pattern.findLastNHigh(quotes5, tradeEntryPosS, lastbar5, 1);
				if (phlS != null)
				{
					double pullBackDist =  quotes5[phlS.prePos].high - phlS.pullBack.low;
					
					// exit scenario1, large parfit
					if ( ( phlS.curPos - phlS.prePos) <= 48 )
					{
						if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotes5[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( profitInPip > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								takeProfit( adjustPrice(quotes5[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
						}
					}
				}

				// check if there has been a big run
				double[] ema20S = Indicator.calculateEMA(quotes5, 20);
				double[] ema10S = Indicator.calculateEMA(quotes5, 10);

				if (( ema10S[lastbar5] < ema20S[lastbar5]) && ( ema10S[lastbar5-1] > ema20S[lastbar5-1]))
				{
					int lastCrossUp = Pattern.findLastMACrossUp(ema10S, ema20S, lastbar5-1, 8);
					//if (lastCrossUp != Constants.NOT_FOUND )
					//System.out.println(data.time + " last cross up " + quotesS[lastCrossUp].time);
					
					if ((lastCrossUp != Constants.NOT_FOUND )&& ((ema10S[lastbar5-1] - ema10S[lastCrossUp]) > 5 * PIP_SIZE * FIXED_STOP ))
					{
						warning(data.time + " cross over after large runup detected " + quotes5[lastbar5].time);
						takeProfit( quotes5[lastbar5].close, trade.remainingPositionSize );
					}
				}
			}

		}*/
	
	}
	

	void placeBreakEvenStop()
	{
		if ( trade.breakEvenStopPlaced == false )
		{	
			cancelOrder(trade.stopId);
			
			if (trade.action.equals(Constants.ACTION_BUY))
			{
				trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else if (trade.action.equals(Constants.ACTION_SELL))
			{
				trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			}
			
			trade.breakEvenStopPlaced = true;
		}
	}
	

	public void trackEntry(QuoteData data)
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		PushList pushListHHLL = null;
		double triggerPrice;
		
		//int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
		//startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;
		int detectPos = Utility.findPositionByHour(quotes60, trade.detectTime, Constants.BACK_TO_FRONT );
		
		if ( lastbar60 == detectPos + 1 ){
			if (Constants.ACTION_SELL.equals(trade.action)){
				if (BarUtil.isDownBar(quotes60[detectPos])){
					enterRVMarketPosition(quotes60[detectPos].close);
				}	
			}
			else if (Constants.ACTION_BUY.equals(trade.action)){
				if (BarUtil.isUpBar(quotes60[detectPos])){
					System.out.println("RV buy triggered: detect time" + quotes60[detectPos].time + " first break out:" + trade.getFirstBreakOutStartTime() );
					enterRVMarketPosition(quotes60[detectPos].close);
				}	
			}
		}else if ( lastbar60 == detectPos + 2){
			
		}else if ( lastbar60 == detectPos + 3){
		}
		else if ( lastbar60 == detectPos + 3){
			trade = null;
			return;
		}
	}

	public void enterRVMarketPosition(double entryPrice){
    	enterMarketPosition( entryPrice);
		super.strategy.sentAlertEmail(AlertOption.trigger, trade.symbol + " " + trade.action + " " + entryPrice + " trade entered", trade);
		
	}
	
	
}


