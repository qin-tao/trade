package tao.trading.strategy.tm;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import tao.trading.AlertOption;
import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.Instrument;
import tao.trading.MACD;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.PositionToMA;
import tao.trading.Push;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.TradePosition;
import tao.trading.dao.ConsectiveBars;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.setup.BarTrendDetector;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;

import com.ib.client.EClientSocket;

public class FirstPullBackAfterBreakOut extends TradeManager2 
{
	boolean CNT = false;;
	double minTarget1, minTarget2;
	HashMap<Integer, Integer> profitTake = new HashMap<Integer, Integer>();
	String currentTime;
	int DEFAULT_RETRY = 0;
	int DEFAULT_PROFIT_TARGET = 400;
	int PULLBACK_SIZE = 0;
	QuoteData currQuoteData, lastQuoteData;
//	int firstRealTimeDataPosL = 0;
	double lastTick_bid, lastTick_ask, lastTick_last;
	int lastTick;
	long firstRealTime = 0;
	int bigChartState;
	
	// important switch
	//boolean prremptive_limit = false;
	boolean breakout_limit = false;
	boolean rsc_trade = false; // do a rsc trade instead of rst if there is
								// consective 3 bars
	boolean reverse_after_profit = false; // reverse after there is a profit
	boolean after_target_reversal = false;
	
	boolean market_order = true;
	String checkPullbackSetup_start_date = null;
	boolean trade_detect_email_notifiy = true;
	
	/********************************
	 *  trading rules
	 *******************************/
	boolean active_hours_only = false;
	boolean thur_scale_out = false;
	boolean average_up = false;
	boolean otherside_ma = true;		
	boolean tip_reverse = false;
	boolean preemptive_reversal = false;
	boolean reverse_trade = true; 		// reverse after stopped out
	

	public FirstPullBackAfterBreakOut()
	{
		super();
	}

	
	public FirstPullBackAfterBreakOut(String ib_account, EClientSocket m_client, int symbol_id, Instrument instrument, Strategy stragety, HashMap<String, Double> exchangeRate )
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
	
	public void setPullBackSize(int pullBackSize){
	   	this.PULLBACK_SIZE = pullBackSize;
	}
	

	private int getFixedStopSize(){
		
		/*QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		int stopSize = (int)Math.round(BarUtil.averageBarSize(quotes, lastbar-121, lastbar-1)/PIP_SIZE);
		stopSize = stopSize + PARM1;
		return stopSize;*/
		
		return FIXED_STOP;
	}

	private int getPullBackSize(){
		/*QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		int pullBackSize = (int)Math.round(2* BarUtil.averageBarSize(quotes, lastbar-121, lastbar-1)/PIP_SIZE);
		pullBackSize = pullBackSize + PARM2;
		return pullBackSize;*/
		
		return PULLBACK_SIZE;
	}

	
	/*****************************************************************************************************************************
	 * 
	 * 
	 * Static Methods
	 * 
	 * 
	 *****************************************************************************************************************************/
	 /*	createInstrument(  0, "EUR", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4);
		createInstrument(  1, "EUR", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
		createInstrument(  2, "EUR", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3);
		createInstrument(  3, "EUR", "CASH", "IDEALPRO", "GBP", 0.0001, Constants.PRICE_TYPE_4);
		createInstrument(  4, "EUR", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4);
		createInstrument(  5, "EUR", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_3);
		createInstrument(  6, "EUR", "CASH", "IDEALPRO", "AUD", 0.0001, Constants.PRICE_TYPE_4);
		createInstrument(  7, "GBP", "CASH", "IDEALPRO", "AUD", 0.0001, Constants.PRICE_TYPE_4);
		createInstrument(  8, "GBP", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_3);
		createInstrument(  9, "GBP", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4);
		createInstrument( 10, "GBP", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3);
		createInstrument( 11, "GBP", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4);
		createInstrument( 12, "GBP", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
		createInstrument( 13, "CHF", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
		createInstrument( 14, "USD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
		createInstrument( 15, "USD", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3);
		createInstrument( 16, "USD", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4);
		createInstrument( 17, "CAD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
		createInstrument( 18, "AUD", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4);
		createInstrument( 19, "AUD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
		createInstrument( 20, "AUD", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_4);*/
	public void process( int req_id, QuoteData data ){

		//if ( req_id != 514 )  //500
		 //	 return;
		//if (data.time.equals("20170308  05:44:00")){
		//	System.out.println("here");
		//}
		PAUSE_TRADING = this.strategy.sftest.setting.getBooleanProperty("BO.pause");
		String hr = data.time.substring(9,12).trim();
		int hour = new Integer(hr);
		
		if (req_id == getInstrument().getId_realtime()){
			if (inMarkActiveHour(hour) && (( trade == null) || ((trade != null) && trade.status.equals(Constants.STATUS_DETECTED)))){
					if (!PAUSE_TRADING && !strategy.sftest.friday_12){
						//System.out.println(req_id + " " + data.time);
						trade = detect2(data);   // production
						//trade = detect2_new(data);   
						//trade = detect4(data);   
						//trade = detect5(data);   
					}/*else{
						if (MODE == Constants.REAL_MODE) 
							System.out.println("BO trading stopped");
					}*/
			}
			else if ((trade != null) && !(trade.status.equals(Constants.STATUS_DETECTED)|| trade.status.equals(Constants.STATUS_OPEN)|| Constants.TRADE_MA.equals(trade.type))){
					manage(data );
			}

			return;
		}
	}
	

	
	//////////////////////////////////////////////////////////////////////////////////////////////
	//
	//  detects
	//
	//////////////////////////////////////////////////////////////////////////////////////////////
	public Trade detect( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int prevUpPos, prevDownPos;
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		int start = lastbar;

		if ( lastUpPos > lastDownPos)
		{
			//if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				//return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotes[lastUpPosStart].low > ema20[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevDownPos-1].low < quotes[prevDownPos].low )
				prevDownPos--;

			// first 2 bar above 20ma
			for ( start = prevDownPos+1; start < lastbar; start++)
				if (( quotes[start].low > ema20[start]) && ( quotes[start+1].low > ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++){
				if ( quotes[i].low <= ema20[i] ){
					touch20MA = i;
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;

			// find first break out
			QuoteData firstBreakOut = Utility.getHigh( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;
			for ( int i = touch20MA+1; i < lastbar; i++){
				if ( quotes[i].high > firstBreakOut.high ){
					fine("first breakout high missed at " + quotes[i].time);
					return null;
				}
			}
					
			double entryPrice = firstBreakOut.high;
			if ( data.high > entryPrice )
			{
				int firstBreakOutL = firstBreakOut.pos;
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOutL+1, lastbar );
				double diverageSize = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
				if (( lowAfterFirstBreakOut != null ) && (diverageSize > PULLBACK_SIZE)){
					fine("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}

				Trade trade = new Trade(symbol);
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_BUY;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevDownStart = prevDownPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info("break UP detected at " + data.time + " start:" + quotes[start].time );
				
				return trade;
			
			}

		}	
		else if ( lastDownPos > lastUpPos )
		{	
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			int lastDownPosStart = lastDownPos;
			while ( quotes[lastDownPosStart].high < ema20[lastDownPosStart])
				lastDownPosStart--;
				
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevUpPos-1].high > quotes[prevUpPos].high )
				prevUpPos--;

			// first 2 bar below 20ma
			for ( start = prevUpPos+1; start < lastbar; start++)
				if (( quotes[start].high < ema20[start]) && ( quotes[start+1].high < ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			
			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++){
				if ( quotes[i].high >= ema20[i]){
					touch20MA=i;
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;

			// find first break out
			QuoteData firstBreakOut = Utility.getLow( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;
			for ( int i = touch20MA+1; i < lastbar; i++){
				if ( quotes[i].low < firstBreakOut.low ){
					fine("first breakout low missed at " + quotes[i].time);
					return null;
				}
			}


			double entryPrice = firstBreakOut.low;
				
			if (data.low < entryPrice ) 
			{
				// filter 1:  deep pullbacks
				int firstBreakOutL = firstBreakOut.pos;
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOutL+1, lastbar );
				double diverageSize = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
				if (( highAfterFirstBreakOut != null ) && (diverageSize > PULLBACK_SIZE)){
					fine("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverageSize is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}
						
				Trade trade = new Trade(symbol);
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_SELL;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevUpStart = prevUpPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info("break DOWN detected at " + data.time + " start:" + quotes[start].time );
				
				return trade;
			}

		}
		
		return null;
	}


	/************************
	 * This is the algorithm	
	 ************************/
	public Trade detect2_new( QuoteData data ){
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int prevUpPos, prevDownPos;
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		//QuoteData[] quotesL = getQuoteData(Constants.CHART_DAILY);// add- on
		//BarTrendDetector tbddL = new BarTrendDetector(quotesL, PIP_SIZE);// ADD-ON  

		int start = lastbar;
		if (( lastUpPos > lastDownPos)/* && ( tbddL.getLastTrend().direction == Constants.DIRECTION_UP )*/){
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotes[lastUpPosStart].low > ema20[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevDownPos-1].low < quotes[prevDownPos].low )
				prevDownPos--;
			
			// first 2 bar above 20ma
			for ( start = prevDownPos+1; start < lastbar; start++)
				if (( quotes[start].low > ema20[start]) && ( quotes[start+1].low > ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			// check if the trade has triggered before
			if ( findTradeHistory( Constants.ACTION_BUY, quotes[start].time) != null )
				return null;

			QuoteData firstBreakOut = Utility.getHigh( quotes, start, lastbar-2);  // should be -1 but to confirm with old code
			if ( firstBreakOut == null ) 
				return null;
			
			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start ; i <= lastbar; i++){
				if ( quotes[i].low <= ema20[i] ){
					if ( i < firstBreakOut.pos )
						return null;
					else{
						touch20MA = i;
						break;
					}
				}
			}
			if ( touch20MA == 0 ) 
				return null;

			// find first break out
			//QuoteData firstBreakOut = Utility.getHigh( quotes, start, touch20MA-1);
			//if ( firstBreakOut == null )
			//	return null;

			for ( int i = firstBreakOut.pos+1; i < lastbar; i++){
				if ( quotes[i].high > firstBreakOut.high ){
					fine("first breakout high missed at " + quotes[i].time);
					return null;
				}
			}
			
			
			double entryPrice = firstBreakOut.high;
			
			QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
			int lastbar5 = quotes5.length - 1;
			if ( quotes5[lastbar5-1].high > entryPrice ){
				fine("first breakout high missed at " + quotes5[lastbar5-1].time);
				return null;
			}
			
			if ( data.high > entryPrice ){
				int firstBreakOutL = firstBreakOut.pos;
				
				// added
				/*
				int firstBreakOutStarted = firstBreakOutL;
				while((quotes[firstBreakOutStarted-1].low < quotes[firstBreakOutStarted].low ) || BarUtil.isUpBar(quotes[firstBreakOutStarted-1]))
					firstBreakOutStarted--;
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOutL+1, lastbar );
				if ( lowAfterFirstBreakOut.low < quotes[firstBreakOutStarted].low ){
					debug("pull back too large too large, trade removed");
					return null;
				}*/
				
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOutL+1, lastbar );
				double diverageSize = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
				if (( lowAfterFirstBreakOut != null ) && (diverageSize > PULLBACK_SIZE)){
					fine("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}

				trade = new Trade(symbol);
				trade.account = getAccount();
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_BUY;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevDownStart = prevDownPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info(IB_ACCOUNT + "break UP detected at " + data.time + " start:" + quotes[start].time );

				// check Stochastics
				/*
				double[] so = Indicator.calculateStochastics( quotes, 14 );
				if ( so[firstBreakOut.pos] > ( so[lastbar] + 8 )){
					removeTrade();
					return null;
				}*/
				
				enterMarketPosition(trade);
				//super.strategy.sentTriggerEmail(strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
				return trade;
			}
		}	
		else if (( lastDownPos > lastUpPos ) /*&& ( tbddL.getLastTrend().direction == Constants.DIRECTION_DOWN )*/){
			//System.out.println(data.time + " " + data.low);
			//if ("20170719  01:10:00".equals(data.time))
			if ("20170620  12:01:00".equals(data.time))
			   System.out.println("here");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			int lastDownPosStart = lastDownPos;
			while ( quotes[lastDownPosStart].high < ema20[lastDownPosStart])
				lastDownPosStart--;
				
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevUpPos-1].high > quotes[prevUpPos].high )
				prevUpPos--;

			// first 2 bar below 20ma
			for ( start = prevUpPos+1; start < lastbar; start++)
				if (( quotes[start].high < ema20[start]) && ( quotes[start+1].high < ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			// check if the trade has triggered before
			if ( findTradeHistory( Constants.ACTION_SELL, quotes[start].time) != null )
				return null;

			QuoteData firstBreakOut = Utility.getLow( quotes, start, lastbar-2);  // should be -1 but to confirm with old code
			if ( firstBreakOut == null )
				return null;

			int pushEnd = firstBreakOut.pos;
			int pushBegin = pushEnd;
			while (BarUtil.isDownBar(quotes[pushBegin-1]))
				pushBegin--;
			double pushSize = quotes[pushBegin].open - quotes[pushEnd].close;
			double avgBarSize = BarUtil.averageBarSizeOpenClose(quotes);
			if ( pushSize < 6 * avgBarSize ){

				// check touch 20MA
			int touch20MA = 0;
			for ( int i = start ; i <= lastbar; i++){
				if ( quotes[i].high >= ema20[i]){
					if ( i < firstBreakOut.pos )
						return null;
					else{
						touch20MA=i;
						break;
					}
				}
			}
			if ( touch20MA == 0 ) 
				return null;
			
			}
			
			//if ("20170719  01:41:00".equals(data.time))
			//	System.out.println("here");
			// find first break out
			//QuoteData firstBreakOut = Utility.getLow( quotes, start, touch20MA-1);
			//if ( firstBreakOut == null )
			//	return null;
			
			for ( int i = firstBreakOut.pos+1; i < lastbar; i++){
				if ( quotes[i].low < firstBreakOut.low ){
					fine("first breakout low missed at " + quotes[i].time);
					return null;
				}
			}

			double entryPrice = firstBreakOut.low;

			QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
			int lastbar5 = quotes5.length - 1;
			if ( quotes5[lastbar5-1].low < entryPrice ){
				fine("first breakout low missed at " + quotes5[lastbar5-1].time);
				return null;
			}

			if (data.low < entryPrice ){ 
				// filter 1:  deep pullbacks
				int firstBreakOutL = firstBreakOut.pos;

				// added
				/*
				int firstBreakOutStarted = firstBreakOutL;
				while((quotes[firstBreakOutStarted-1].high > quotes[firstBreakOutStarted].high ) || BarUtil.isDownBar(quotes[firstBreakOutStarted-1]))
					firstBreakOutStarted--;
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOutL+1, lastbar );
				if ( highAfterFirstBreakOut.high > quotes[firstBreakOutStarted].high ){
					debug("pull back too large too large, trade removed");
					return null;
				}*/

				
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOutL+1, lastbar );
				double diverageSize = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
				if (( highAfterFirstBreakOut != null ) && (diverageSize > PULLBACK_SIZE)){
					fine("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverageSize is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}
						
				trade = new Trade(symbol);
				trade.account = getAccount();
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_SELL;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				//trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevUpStart = prevUpPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info(IB_ACCOUNT + " break DOWN detected at " + data.time + " start:" + quotes[start].time + " startHigh:" + quotes[start].high + " data:" + data.time );

				/*
				double[] so = Indicator.calculateStochastics( quotes, 14 );
				if ( so[firstBreakOut.pos] < ( so[lastbar] - 8 )){
					removeTrade();
					return null;
				}*/

				enterMarketPosition(trade);
				//super.strategy.sentTriggerEmail(strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice,  trade);
				return trade;
			}
		}
		
		return null;
	}


	
	
	public Trade detect2( QuoteData data ){
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int prevUpPos, prevDownPos;
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		//QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);// add- on
		//BarTrendDetector tbddL = new BarTrendDetector(quotesL, PIP_SIZE);// ADD-ON  

		int start = lastbar;
		if (( lastUpPos > lastDownPos) /*&& ( tbddL.detectTrend() == Constants.DIRECTION_UP )*/){
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotes[lastUpPosStart].low > ema20[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevDownPos-1].low < quotes[prevDownPos].low )
				prevDownPos--;
			
			//added
			/*
			int prevDownPeak = Utility.getLow( quotes, prevDownPos-20, prevDownPos).pos;
			PushList pushList = PushSetup.findPastHighsByX(quotes, prevDownPeak, lastbar, 2);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0) ){
				PushHighLow[] phls = pushList.phls;
				int numOfPush = phls.length;
				if ( numOfPush > 2 )
					return null;
			}*/

			// first 2 bar above 20ma
			for ( start = prevDownPos+1; start < lastbar; start++)
				if (( quotes[start].low > ema20[start]) && ( quotes[start+1].low > ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			// check if the trade has triggered before
			if ( findTradeHistory( Constants.ACTION_BUY, quotes[start].time) != null )
				return null;
			
			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++){
				if ( quotes[i].low <= ema20[i] ){
					touch20MA = i;
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;

			// find first break out
			QuoteData firstBreakOut = Utility.getHigh( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;

			for ( int i = touch20MA+1; i < lastbar; i++){
				if ( quotes[i].high > firstBreakOut.high ){
					fine("first breakout high missed at " + quotes[i].time);
					return null;
				}
			}
			
			
			double entryPrice = firstBreakOut.high;
			
			QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
			int lastbar5 = quotes5.length - 1;
			if ( ( lastbar5 < 1 ) || quotes5[lastbar5-1].high > entryPrice ){
				fine("first breakout high missed at " + quotes5[lastbar5-1].time);
				return null;
			}
			
			if ( data.high > entryPrice ){
				int firstBreakOutL = firstBreakOut.pos;
				
				// added
				/*
				int firstBreakOutStarted = firstBreakOutL;
				while((quotes[firstBreakOutStarted-1].low < quotes[firstBreakOutStarted].low ) || BarUtil.isUpBar(quotes[firstBreakOutStarted-1]))
					firstBreakOutStarted--;
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOutL+1, lastbar );
				if ( lowAfterFirstBreakOut.low < quotes[firstBreakOutStarted].low ){
					debug("pull back too large too large, trade removed");
					return null;
				}*/
				
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOutL+1, lastbar );
				double diverageSize = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
				//System.out.println("PULLBACKSIZE:" + PULLBACK_SIZE + " AVGbarsize:" + BarUtil.averageBarSize(quotes, lastbar-121, lastbar-1)/PIP_SIZE);
				if (( lowAfterFirstBreakOut != null ) && (diverageSize > getPullBackSize())){
					fine("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}

				trade = new Trade(symbol);
				trade.account = getAccount();
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_BUY;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevDownStart = prevDownPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info(IB_ACCOUNT + "break UP detected at " + data.time + " start:" + quotes[start].time );

				// check Stochastics
				/*
				double[] so = Indicator.calculateStochastics( quotes, 14 );
				if ( so[firstBreakOut.pos] > ( so[lastbar] + 8 )){
					removeTrade();
					return null;
				}*/
				
				enterMarketPosition(trade);
				//super.strategy.sentTriggerEmail(strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
				return trade;
			}
		}	
		else if (( lastDownPos > lastUpPos ) /*&& ( tbddL.detectTrend() == Constants.DIRECTION_DOWN )*/){
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			int lastDownPosStart = lastDownPos;
			while ( quotes[lastDownPosStart].high < ema20[lastDownPosStart])
				lastDownPosStart--;
				
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevUpPos-1].high > quotes[prevUpPos].high )
				prevUpPos--;

			//added
			/*
			int prevUpPeak = Utility.getHigh( quotes, prevUpPos-20, prevUpPos).pos;
			PushList pushList = PushSetup.findPastLowsByX(quotes, prevUpPeak, lastbar, 2);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0) ){
				PushHighLow[] phls = pushList.phls;
				int numOfPush = phls.length;
				if ( numOfPush > 2 )
					return null;
			}*/
			
			// first 2 bar below 20ma
			for ( start = prevUpPos+1; start < lastbar; start++)
				if (( quotes[start].high < ema20[start]) && ( quotes[start+1].high < ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			// check if the trade has triggered before
			if ( findTradeHistory( Constants.ACTION_SELL, quotes[start].time) != null )
				return null;
			
			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++){
				if ( quotes[i].high >= ema20[i]){
					touch20MA=i;
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;

			// find first break out
			QuoteData firstBreakOut = Utility.getLow( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;
			
			for ( int i = touch20MA+1; i < lastbar; i++){
				if ( quotes[i].low < firstBreakOut.low ){
					fine("first breakout low missed at " + quotes[i].time);
					return null;
				}
			}

			double entryPrice = firstBreakOut.low;

			QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
			int lastbar5 = quotes5.length - 1;
			if (( lastbar5 < 1 ) || ( quotes5[lastbar5-1].low < entryPrice )){
				fine("first breakout low missed at " + quotes5[lastbar5-1].time);
				return null;
			}

			if (data.low < entryPrice ){ 
				// filter 1:  deep pullbacks
				int firstBreakOutL = firstBreakOut.pos;

				// added
				/*
				int firstBreakOutStarted = firstBreakOutL;
				while((quotes[firstBreakOutStarted-1].high > quotes[firstBreakOutStarted].high ) || BarUtil.isDownBar(quotes[firstBreakOutStarted-1]))
					firstBreakOutStarted--;
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOutL+1, lastbar );
				if ( highAfterFirstBreakOut.high > quotes[firstBreakOutStarted].high ){
					debug("pull back too large too large, trade removed");
					return null;
				}*/

				
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOutL+1, lastbar );
				double diverageSize = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
				//System.out.println("PULLBACKSIZE:" + PULLBACK_SIZE + " AVGbarsize:" + BarUtil.averageBarSize(quotes, lastbar-121, lastbar-1)/PIP_SIZE);
				if (( highAfterFirstBreakOut != null ) && (diverageSize > getPullBackSize())){
					fine("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverageSize is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}
						
				trade = new Trade(symbol);
				trade.account = getAccount();
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_SELL;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevUpStart = prevUpPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info(IB_ACCOUNT + " break DOWN detected at " + data.time + " start:" + quotes[start].time );

				/*
				double[] so = Indicator.calculateStochastics( quotes, 14 );
				if ( so[firstBreakOut.pos] < ( so[lastbar] - 8 )){
					removeTrade();
					return null;
				}*/

				enterMarketPosition(trade);
				//super.strategy.sentTriggerEmail(strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice,  trade);
				return trade;
			}
		}
		
		return null;
	}

	
	
	

	/************************
	 * This is to see if we can trade without toughing 20MA	
	 ************************/
	public Trade detect4( QuoteData data ){
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int prevUpPos, prevDownPos;
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		int start = lastbar;
		if ( lastUpPos > lastDownPos){
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotes[lastUpPosStart].low > ema20[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevDownPos-1].low < quotes[prevDownPos].low )
				prevDownPos--;

			// first 2 bar above 20ma
			for ( start = prevDownPos+1; start < lastbar; start++)
				if (( quotes[start].low > ema20[start]) && ( quotes[start+1].low > ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			// check if the trade has triggered before
			if ( findTradeHistory( Constants.ACTION_BUY, quotes[start].time) != null )
				return null;
			
			// check touch 20MA
			int firstpeak = start;
			while ((firstpeak < lastbar) && (quotes[firstpeak+1].high >= quotes[firstpeak].high))
				firstpeak++;
			if ( firstpeak >= lastbar - 1 )
				return null;
			firstpeak = Utility.getHigh( quotes, prevDownPos+1, firstpeak).pos;
			for ( int i = firstpeak+2; i < lastbar; i++)
				if ( quotes[i].high > quotes[firstpeak].high)
					return null;
			if (!( quotes[lastbar].high > quotes[firstpeak].high))
				return null;
			double avgBarSize = BarUtil.averageBarSizeOpenClose(quotes);
			if (!((quotes[firstpeak].high - ema20[firstpeak]) > 2 * avgBarSize))
				return null;
			QuoteData firstBreakOut = quotes[firstpeak];
					
			double entryPrice = firstBreakOut.high;
			QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
			int lastbar5 = quotes5.length - 1;
			if ( quotes5[lastbar5-1].high > firstBreakOut.high ){
				fine("first breakout high missed");
				return null;
			}
			if ( data.high > entryPrice ){
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstpeak, lastbar );
				double diverageSize = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
				if (( lowAfterFirstBreakOut != null ) && (diverageSize > PULLBACK_SIZE)){
					fine("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}

				trade = new Trade(symbol);
				trade.account = getAccount();
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_BUY;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				//trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevDownStart = prevDownPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info(IB_ACCOUNT + "break UP detected at " + data.time + " start:" + quotes[start].time );
				
				enterMarketPosition(trade);
				//super.strategy.sentTriggerEmail(strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
				return trade;
			}
		}	
		else if ( lastDownPos > lastUpPos ){
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			int lastDownPosStart = lastDownPos;
			while ( quotes[lastDownPosStart].high < ema20[lastDownPosStart])
				lastDownPosStart--;
				
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevUpPos-1].high > quotes[prevUpPos].high )
				prevUpPos--;

			// first 2 bar below 20ma
			for ( start = prevUpPos+1; start < lastbar; start++)
				if (( quotes[start].high < ema20[start]) && ( quotes[start+1].high < ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			// check if the trade has triggered before
			if ( findTradeHistory( Constants.ACTION_SELL, quotes[start].time) != null )
				return null;
			
			int firstpeak = start;
			while  ((firstpeak < lastbar) && (quotes[firstpeak+1].low <= quotes[firstpeak].low))
				firstpeak++;
			if ( firstpeak >= lastbar-1 )
				return null;
			firstpeak = Utility.getLow( quotes, prevUpPos+1, firstpeak).pos;
			for ( int i = firstpeak+2; i < lastbar; i++)
				if ( quotes[i].low < quotes[firstpeak].low)
					return null;
			if (!( quotes[lastbar].low < quotes[firstpeak].low))
				return null;
			double avgBarSize = BarUtil.averageBarSizeOpenClose(quotes);
			if (!((ema20[firstpeak] - quotes[firstpeak].low ) > 2 * avgBarSize))
				return null;
			
			QuoteData firstBreakOut = quotes[firstpeak];


			double entryPrice = firstBreakOut.low;
			QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
			int lastbar5 = quotes5.length - 1;
			if ( quotes5[lastbar5-1].low < firstBreakOut.low ){
				fine("first breakout high missed");
				return null;
			}
			if (data.low < entryPrice ){ 
				// filter 1:  deep pullbacks
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstpeak+1, lastbar );
				double diverageSize = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
				if (( highAfterFirstBreakOut != null ) && (diverageSize > PULLBACK_SIZE)){
					System.out.println("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverageSize is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}
						
				trade = new Trade(symbol);
				trade.account = getAccount();
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_SELL;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				//trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevUpStart = prevUpPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info(IB_ACCOUNT + " break DOWN detected at " + data.time + " start:" + quotes[start].time );
				
				enterMarketPosition(trade);
				//super.strategy.sentTriggerEmail(strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice,  trade);
				return trade;
			}
		}
		
		return null;
	}


	
	
	public Trade detect5( QuoteData data ){
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int prevUpPos, prevDownPos;
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		int start = lastbar;
		if ( lastUpPos > lastDownPos){
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotes[lastUpPosStart].low > ema20[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevDownPos-1].low < quotes[prevDownPos].low )
				prevDownPos--;

			// first 2 bar above 20ma
			for ( start = prevDownPos+1; start < lastbar; start++)
				if (( quotes[start].low > ema20[start]) && ( quotes[start+1].low > ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			// check if the trade has triggered before
			if ( findTradeHistory( Constants.ACTION_BUY, quotes[start].time) != null )
				return null;
			
			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++){
				if ( quotes[i].low <= ema20[i]){
					touch20MA=i;
					break;
				}
			}
			
			QuoteData firstBreakOut = null;
			if ( touch20MA != 0 ){
				firstBreakOut = Utility.getHigh( quotes, start, touch20MA-1);
			}
			else{
				int firstpeak = start;
				while ((firstpeak < lastbar) && (quotes[firstpeak+1].high >= quotes[firstpeak].high))
					firstpeak++;
				if ( firstpeak >= lastbar - 1 )
					return null;
				firstpeak = Utility.getHigh( quotes, prevDownPos+1, firstpeak).pos;
				for ( int i = firstpeak+2; i < lastbar; i++)
					if ( quotes[i].high > quotes[firstpeak].high)
						return null;
				if (!( quotes[lastbar].high > quotes[firstpeak].high))
					return null;
				double avgBarSize = BarUtil.averageBarSizeOpenClose(quotes);
				if (!((quotes[firstpeak].high - ema20[firstpeak]) > 2 * avgBarSize))
					return null;
				firstBreakOut = quotes[firstpeak];
				firstBreakOut.pos = firstpeak;
			}
			if ( firstBreakOut == null )
				return null;

			for ( int i = firstBreakOut.pos+1; i < lastbar; i++){
				if ( quotes[i].high > firstBreakOut.high ){
					fine("first breakout high missed at " + quotes[i].time);
					return null;
				}
			}
			QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
			int lastbar5 = quotes5.length - 1;
			if ( quotes5[lastbar5-1].high > firstBreakOut.high ){
				fine("first breakout high missed");
				return null;
			}

			double entryPrice = firstBreakOut.high;
			if ( data.high > entryPrice ){
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOut.pos+1, lastbar );
				double diverageSize = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
				if (( lowAfterFirstBreakOut != null ) && (diverageSize > PULLBACK_SIZE)){
					fine("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}

				trade = new Trade(symbol);
				trade.account = getAccount();
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_BUY;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				//trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevDownStart = prevDownPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info(IB_ACCOUNT + "break UP detected at " + data.time + " start:" + quotes[start].time );
				
				enterMarketPosition(trade);
				//super.strategy.sentTriggerEmail(strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
				return trade;
			}
		}	
		else if ( lastDownPos > lastUpPos ){
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			int lastDownPosStart = lastDownPos;
			while ( quotes[lastDownPosStart].high < ema20[lastDownPosStart])
				lastDownPosStart--;
				
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			while ( quotes[prevUpPos-1].high > quotes[prevUpPos].high )
				prevUpPos--;

			// first 2 bar below 20ma
			for ( start = prevUpPos+1; start < lastbar; start++)
				if (( quotes[start].high < ema20[start]) && ( quotes[start+1].high < ema20[start+1]))
					break;
			if ( start == lastbar )
				return null;

			// check if the trade has triggered before
			if ( findTradeHistory( Constants.ACTION_SELL, quotes[start].time) != null )
				return null;

			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++){
				if ( quotes[i].high >= ema20[i]){
					touch20MA=i;
					break;
				}
			}
			
			QuoteData firstBreakOut = null;
			if ( touch20MA != 0 ){
				firstBreakOut = Utility.getLow( quotes, start, touch20MA-1);
			}
			else{
				int firstpeak = start;
				while  ((firstpeak < lastbar) && (quotes[firstpeak+1].low <= quotes[firstpeak].low))
					firstpeak++;
				if ( firstpeak >= lastbar-1 )
					return null;
				firstpeak = Utility.getLow( quotes, prevUpPos+1, firstpeak).pos;
				for ( int i = firstpeak+2; i < lastbar; i++)
					if ( quotes[i].low < quotes[firstpeak].low)
						return null;
				if (!( quotes[lastbar].low < quotes[firstpeak].low))
					return null;
				double avgBarSize = BarUtil.averageBarSizeOpenClose(quotes);
				if (!((ema20[firstpeak] - quotes[firstpeak].low ) > 2 * avgBarSize))
					return null;
				
				firstBreakOut = quotes[firstpeak];
				firstBreakOut.pos = firstpeak;
			}
			if ( firstBreakOut == null )
				return null;

			for ( int i = firstBreakOut.pos+1; i < lastbar; i++){
				if ( quotes[i].low < firstBreakOut.low ){
					fine("first breakout low missed at " + quotes[i].time);
					return null;
				}
			}
			QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
			int lastbar5 = quotes5.length - 1;
			if ( quotes5[lastbar5-1].low < firstBreakOut.low ){
				fine("first breakout high missed");
				return null;
			}

			double entryPrice = firstBreakOut.low;
			if (data.low < entryPrice ){ 
				// filter 1:  deep pullbacks
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOut.pos+1, lastbar );
				double diverageSize = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
				if (( highAfterFirstBreakOut != null ) && (diverageSize > PULLBACK_SIZE)){
					System.out.println("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverageSize is "+  + diverageSize + "pips,  too large, trade removed");
					return null;
				}
						
				trade = new Trade(symbol);
				trade.account = getAccount();
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_SELL;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				//trade.setTouch20MATime(quotes[touch20MA].time);
				trade.prevUpStart = prevUpPos;
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				info(IB_ACCOUNT + " break DOWN detected at " + data.time + " start:" + quotes[start].time );
				
				enterMarketPosition(trade);
				//super.strategy.sentTriggerEmail(strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.trigger, strategy.getStrategyName() + " " + strategy.IB_PORT + " TRIGGER " + trade.symbol + " " + trade.action + " " + trade.triggerPrice,  trade);
				return trade;
			}
		}
		
		return null;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public Trade detect_moonlight( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);

		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int prevUpPos, prevDownPos;

		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		int start = lastbar;
			
		if ( lastUpPos > lastDownPos)
		{
			int lastUpPosStart = lastUpPos;
			while ( quotes[lastUpPosStart].low > ema20[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			while ( quotes[prevDownPos-1].low < quotes[prevDownPos].low )
				prevDownPos--;

			// looking for upside
			for ( start = prevDownPos+1; start < lastbar; start++)
				if (( quotes[start].low > ema20[start]) && ( quotes[start+1].low > ema20[start+1]))
					break;
				
			if ( start == lastbar )
				return null;


			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++)
			{
				if ( quotes[i].low <= ema20[i] )
				{
					touch20MA = i;
					break;
				}
			}
				
			if ( touch20MA == 0 )
				return null;

				
			QuoteData firstBreakOut = Utility.getHigh( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;

			for ( int i = touch20MA+1; i < lastbar; i++)
			{
				if ( quotes[i].high > firstBreakOut.high )
				{
					//debug("first breakout high missed at " + quotes[i].time);
					return null;
				}
			}
					

			double entryPrice = firstBreakOut.high;

			if ( data.high > entryPrice )
			{
				int firstBreakOutL = firstBreakOut.pos;
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOutL+1, lastbar );
				if (( lowAfterFirstBreakOut != null ) && ((entryPrice - lowAfterFirstBreakOut.low) > 1.5 * getFixedStopSize() * PIP_SIZE))
				{
					double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
					if ( diverage > 1.5 * getFixedStopSize() )
					{
						//info("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}
				}

				Trade trade = new Trade(symbol);
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_BUY;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				warning("break DOWN detected at " + data.time + " start:" + quotes[start].time );
				
				return trade;
			
			}

		}	
		else if ( lastDownPos > lastUpPos )
		{	
			int lastDownPosStart = lastDownPos;
			while ( quotes[lastDownPosStart].high < ema20[lastDownPosStart])
				lastDownPosStart--;
				
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
				
			while ( quotes[prevUpPos-1].high > quotes[prevUpPos].high )
				prevUpPos--;

			// looking for upside
			for ( start = prevUpPos+1; start < lastbar; start++)
				if (( quotes[start].high < ema20[start]) && ( quotes[start+1].high < ema20[start+1]))
					break;
				
			if ( start == lastbar )
				return null;

			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++)
			{
				if ( quotes[i].high >= ema20[i])
				{
					touch20MA=i;
					break;
				}
			}

			if ( touch20MA == 0 )
				return null;

			QuoteData firstBreakOut = Utility.getLow( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;
				

			//for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
			for ( int i = touch20MA+1; i < lastbar; i++)
			{
				if ( quotes[i].low < firstBreakOut.low )
				{
					//debug("first breakout low missed at " + quotes[i].time);
					return null;
				}
			}


			double entryPrice = firstBreakOut.low;
				
			if (data.low < entryPrice ) 
			{
				// filter 1:  deep pullbacks
				int firstBreakOutL = firstBreakOut.pos;
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOutL+1, lastbar );
				if (( highAfterFirstBreakOut != null ) && ((highAfterFirstBreakOut.high - entryPrice) > 1.5 * getFixedStopSize() * PIP_SIZE))
				{
					double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
					if ( diverage > 1.5 * getFixedStopSize() )
					{
						//info("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}
				}
						 
				Trade trade = new Trade(symbol);
				trade.type = Constants.TRADE_EUR;
				trade.action = Constants.ACTION_SELL;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.entryPrice = trade.triggerPrice = entryPrice;
				trade.POSITION_SIZE = POSITION_SIZE;
				warning("break DOWN detected at " + data.time + " start:" + quotes[start].time );
				
				return trade;
			}

		}
		
		return null;
	}





	
	public void entry( QuoteData data )
	{
		if ((MODE == Constants.TEST_MODE) /*&& Constants.STATUS_PLACED.equals(trade.status)*/)
			checkStopTarget(data,0,0);

		if (trade != null)
		{
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				trackPullBackTradeSell( data, 0);
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				trackPullBackTradeBuy( data, 0 );
			}
		}
	}
		
		
	public void manage( QuoteData data )
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data,0,0);

		if ( trade != null )
		{	
			//if ( trade.status.equals(Constants.STATUS_OPEN))
			//	checkTradeExpiring_ByTime(data);
			exit123_close_monitor(data);
			
			//if (( trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED)))
			/*if (!STRATEGY.sftest.friday_15){
				exit123_close_monitor(data);
				//exit123_new9c7( data );  //   this is the latest
				//exit123_new9c4( data );  // this is default  
				//exit123_new9c6( data );  c6 addes to close position quick if it does not move  
			}else{
				if ( trade.finalExisting == false ){
					double exitPrice = 0;
					if (Constants.ACTION_SELL.equals(trade.action)){
						exitPrice = adjustPrice( ( data.close - 2 * PIP_SIZE), Constants.ADJUST_TYPE_DOWN); 
						createTradeTargetOrder( trade.remainingPositionSize, exitPrice );
					}
					else if (Constants.ACTION_BUY.equals(trade.action)){
						exitPrice = adjustPrice( ( data.close + 2 * PIP_SIZE), Constants.ADJUST_TYPE_UP); 
						createTradeTargetOrder( trade.remainingPositionSize, exitPrice );
					}
						
					trade.finalExisting = true;
				}
			}*/
		}
		
	}

	
	
	
/*
	
	public boolean createTradeReport( String reportFileName )
	{
    	double totalProfit = 0;
    	double totalUnrealizedProfit = 0;
    	int totalTrade = 0;

    	System.out.println("report:"+OUTPUT_DIR + ":" + strategy);
    	if ( reportFileName == null )
    	  reportFileName = OUTPUT_DIR + "/" + strategy + "/report.txt";
    	
    	for ( int i = 0; i < TOTAL_SYMBOLS; i++ )
    	{
    		QuoteData[] quotes240 = tradeManager[i].getQuoteData(Constants.CHART_240_MIN);
    		int lastbar240 = quotes240.length - 1;

    		if ( lastbar240 > 0 )
    		{
    			double close = quotes240[lastbar240].close;
    			tradeManager[i].report2(close);
    		}
    	}
    	
    	
    	try
	    {
    		StringBuffer sb_report = new StringBuffer();
    		
	    	for ( int i = 0; i < TOTAL_SYMBOLS; i++ )
	    	{
	    		TradeReport tr = tradeManager[i].getTradeReport();
	    		
    			if (( tr.getTradeReport() != null ) && (!tr.getTradeReport().equals("")))
    			{
    				//out.write(tr.getTradeReport() + "\n");
    		    	if ( reportFileName != null )
    		    		sb_report.append(tr.getTradeReport() + "\n");
    				totalProfit += tr.getTotalProfit();
    				totalUnrealizedProfit += tr.getTotalProfit();
    				totalTrade += tr.getTotalTrade();
    			}
	    	}

	    	if ( reportFileName != null )
	    	{	
		    	FileWriter fstream = new FileWriter(reportFileName);
		    	BufferedWriter out = new BufferedWriter(fstream);
	
		    	out.write("Trade Report " + IBDataFormatter.format( new Date()) + "\n\n");
				out.write(sb_report.toString() + "\n");
		    	
				out.write("\n\nTOTAL PROFIT: " + totalProfit + " USD          TOTAL TRADES: " + totalTrade + "\n" );
				out.write("\nTOTAL UNREALIZED PROFIT: " + totalUnrealizedProfit + " USD\n" );
	
		    	out.close();
	    	}
	    	
	    	if ( totalProfit < -1500 )
	    		return true;
	   
	    }
	   catch (Exception e)
	   {
		   e.printStackTrace();
   	   }

	   return false;
	}




	public void removeTradeNotUpdated()
	{
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			Trade trade = tradeManager[i].getTrade();
			String symbol = tradeManager[i].getSymbol();
			if ((trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED )) && (trade.positionUpdated == false))
			{
				logger.warning(symbol + " " +  trade.action + " position did not updated, trade removed");
				tradeManager[i].setTrade(null);
			}
		}
	}*/
	
	
	
	
	/***********************************************************************************************************************
	 * 
	 * 
	 *	Standard Interface 
	 * 
	 * 
	 ************************************************************************************************************************/
	
    public void run() {
    }

	public QuoteData[] getQuoteData(int CHART){
		//int default_chart_size = 60;
		return instrument.getQuoteData(CHART);
	}

	private void OrderFilled_stopId(String time){
		info("BO Order stopped out @ " + trade.stop + " " + time);
		AddCloseRecord(time, Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);

		trade.stopId = 0;

		cancelTargets();
		
		//boolean reversequalified = true;
		//if (mainProgram.isNoReverse(symbol ))
		//	reversequalified = false;
		trade.status = Constants.STATUS_STOPPEDOUT;	
		//processAfterHitStopLogic_c();
		//removeTrade();
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
					
					// Do not set stops on limit orders
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price + getFixedStopSize() * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					if ( trade.stopId != 0 )
						cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);

					trade.openOrderDurationInHour = 0;

					/*if ( trade.averageUp ){
						double averageUpPrice = limit.price - getFixedStopSize() * PIP_SIZE;
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
					info("remaining position size: " + trade.remainingPositionSize);
					if (trade.remainingPositionSize <= 0)
					{
						cancelAllOrders();
						removeTrade();
						return;
					}
					else
					{
						if ( trade.stop != 0 ){
							info("replace stop order to " + trade.stop + " " + trade.remainingPositionSize);
							cancelStop();
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						}
						else{
							info("stop is 0, no stop order placed");
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

					// Do not set stops on limit orders if necessary
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price - getFixedStopSize() * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					if ( trade.stopId != 0 )
						cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					
					trade.openOrderDurationInHour = 0;

					/*if ( trade.averageUp ){
						double averageUpPrice = limit.price + getFixedStopSize() * PIP_SIZE;
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
					info("remaining position size: " + trade.remainingPositionSize);
					if (trade.remainingPositionSize <= 0){
						cancelAllOrders();
						removeTrade();
						return;
					}
					else{
						if ( trade.stop != 0 ){
							info("replace stop order:" + trade.stop + " " + trade.remainingPositionSize );
							cancelStop();
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
						}
						else{
							info("stop is 0, no stop order placed");
						}
					}
				}
			}
		}
	}
	



	
	void processAfterHitStopLogic_c()
	{
		QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
		int lastbar5 = quotes5.length - 1;

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

		
		double reversePrice = prevStop;
		boolean reversequalified = false;

		if (Constants.ACTION_SELL.equals(prevAction))
		{
			// check if there is a big move against the trade
			/*
			double avgSize5 = Utility.averageBarSizeOpenClose( quotes5 );
			if ((( quotes5[lastbar5].close - quotes5[lastbar5].open ) > 3 * avgSize5 ) && (Math.abs(quotes5[lastbar5].close - quotes5[lastbar5].high) < 5 * PIP_SIZE))
				reversequalified = true;
			*/
			
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			warning("low point after entry is " + lowestPointAfterEntry + " entry price:" + prevEntryPrice); 
			
			if ((( prevEntryPrice - lowestPointAfterEntry) < getFixedStopSize() * PIP_SIZE * 0.3 ) || 
			(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) && (( prevEntryPrice - lowestPointAfterEntry) < getFixedStopSize() * PIP_SIZE ))) 
			{
				System.out.println(bt.toString(quotesL));
				//bt = determineBigTrend( quotesL);
				warning(" close trade with small tip");
				reversequalified = true;
				
				if ( (touch20MAPosL - firstBreakOutStartL) > 5)
				{
					double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
					double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
					if (Math.abs(high-low) > 2 * PIP_SIZE * getFixedStopSize())
						reversequalified = false;
				}

			}
		}
		else if (Constants.ACTION_BUY.equals(prevAction))
		{
			/*
			double avgSize5 = Utility.averageBarSizeOpenClose( quotes5 );
			if ((( quotes5[lastbar5].close - quotes5[lastbar5].open ) > 3 * avgSize5 ) && (Math.abs(quotes5[lastbar5].close - quotes5[lastbar5].low) < 5 * PIP_SIZE))
				reversequalified = true;
			*/
			
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 

			if ((( highestPointAfterEntry - prevEntryPrice) < getFixedStopSize() * PIP_SIZE * 0.3 ) ||
			     (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < getFixedStopSize() * PIP_SIZE )))
			{
				System.out.println(bt.toString(quotesL));
				//bt = determineBigTrend( quotesL);
				warning(" close trade with small tip");
				reversequalified = true;

				if ( (touch20MAPosL - firstBreakOutStartL) > 5)
				{
					double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
					double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
					if (Math.abs(high-low) > 2 * PIP_SIZE * getFixedStopSize())
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
		
		// reverse;
		if ( reversequalified )
		{
			createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL.equals(prevAction)?Constants.ACTION_BUY:Constants.ACTION_SELL);
			trade.detectTime = quotes[lastbar].time;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.entryPrice = reversePrice;
			trade.setEntryTime(((QuoteData) quotes[lastbar]).time);

			enterMarketPosition(reversePrice);
			return;
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

	
	
	public void checkTradeExpiring_ByTime(QuoteData data)
	{
		if ( trade.status.equals(Constants.STATUS_OPEN) && ( trade.openOrderExpireInMill != 0 )) 
		{
			Date now = new Date();
			if (now.getTime() > trade.openOrderExpireInMill )
			{
				warning( "trade " + trade.action + " expired: detectTime "+ trade.detectTime);
				
				if ( trade.limitId1 != 0 )
				{
					cancelOrder(trade.limitId1);
					trade.limitId1 = 0;
				}
				if ( trade.stopMarketId != 0 )
				{
					cancelOrder(trade.stopMarketId);
					trade.stopMarketId = 0;
				}
				if ( trade.stopId != 0 )
				{
					cancelOrder(trade.stopId);
					trade.stopId = 0;
				}
				
				trade.status = Constants.STATUS_DETECTED;
			}
		}
	}

	
	
	public void checkTradeExpiring(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		
		int tradeDetectPos = Utility.findPositionByMinute( quotes, trade.detectTime, Constants.BACK_TO_FRONT);
		if ( lastbar == tradeDetectPos )
			return;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			double lowAfterDetect = Utility.getLow(quotes, tradeDetectPos+1,lastbar).low;
			
			if ( trade.status.equals(Constants.STATUS_OPEN)) 
			{
				if((trade.limitPrice1 - lowAfterDetect ) > 1.5 * getFixedStopSize() * PIP_SIZE )
				{
					logger.warning(symbol + " trade " + trade.detectTime + " missed as price move away");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
				else if (( data.timeInMillSec - trade.detectTimeInMill) > 5 * 3600000L)
				{
					logger.warning(symbol + " trade " + trade.detectTime + " missed as time passed");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			double highAfterDetect = Utility.getHigh(quotes, tradeDetectPos+1,lastbar).high;

			if ( trade.status.equals(Constants.STATUS_OPEN))
			{
				if (( highAfterDetect - trade.limitPrice1) > 1.5 * getFixedStopSize() * PIP_SIZE )
				{
					logger.warning(symbol + " trade " + trade.detectTime + " missed as price moved away");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
				else if (( data.timeInMillSec - trade.detectTimeInMill) > 5 * 3600000L)
				{
					logger.warning(symbol + " trade " + trade.detectTime + " missed as time passed ");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
			}
		}
	}
	
	
	
	



	protected Trade findPastTradeHistory(String symbol, String action, String tradeType)
	{
		Iterator it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = (Trade) it.next();
			if (t.symbol.equals(symbol) && t.action.equals(action) && t.type.equals(tradeType))
				return t;
		}

		return null;

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

	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Stragety Methods
	//
	//
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////
	public Trade checkBreakOutMO(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 5);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 5);
	
		if ( lastUpPos > lastDownPos)
		{
			//debug("check SELL");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			if (( quotesL[lastbarL-1].low > quotesL[lastbarL-2].low ) && ( quotesL[lastbarL-2].low < ema20L[lastbarL-2])) // a pullback
			{
				ConsectiveBars consecDown = Utility.findLastConsectiveDownBars(quotesL, 0, lastbarL-2);
				if ( consecDown != null )
				{
					double consecDownSize = quotesL[consecDown.getBegin()].high - quotesL[consecDown.getEnd()].low;
					double avgSize = BarUtil.averageBarSize(quotesL);
					
					if ((( consecDown.getSize() == 1 ) && ( consecDownSize > 2 * avgSize ))
					  ||(( consecDown.getSize() == 2 ) && ( consecDownSize > 3 * avgSize ))
					  ||(( consecDown.getSize() == 3 ) && ( consecDownSize > 4 * avgSize ))
					  ||(( consecDown.getSize() > 3 ) && ( consecDownSize > consecDown.getSize() * 1.5 * avgSize )))
					{
						// detected
						fine("break out start detected at " + quotesL[consecDown.getBegin()].time);
						if ( findTradeHistory( Constants.ACTION_SELL, quotesL[consecDown.getBegin()].time) != null )
							return null;

						createOpenTrade(Constants.TRADE_PULLBACK, Constants.ACTION_SELL);
						trade.status = Constants.STATUS_DETECTED;
						
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutTime(quotesL[consecDown.getEnd()].time);
						trade.setFirstBreakOutStartTime(quotesL[consecDown.getBegin()].time);
						
						trade.entryPrice = trade.triggerPrice = quotesL[lastbarL-2].low;
						trade.POSITION_SIZE = POSITION_SIZE;

						warning("break DOWN detected at " + data.time + " start:" + quotesL[consecDown.getBegin()].time );
						
						return trade;
					
					}
				}
			}
		}	
		else if ( lastDownPos > lastUpPos ) 
		{
			//debug("check BUY");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			if (( quotesL[lastbarL-1].high < quotesL[lastbarL-2].high ) && ( quotesL[lastbarL-2].high > ema20L[lastbarL-2]))// a pullback
			{
				ConsectiveBars consecUp = Utility.findLastConsectiveUpBars(quotesL, 0, lastbarL-2);
				if ( consecUp != null )
				{
					double consecUpSize = quotesL[consecUp.getEnd()].high - quotesL[consecUp.getBegin()].low ;
					double avgSize = BarUtil.averageBarSize(quotesL);
					
					if ((( consecUp.getSize() == 1 ) && ( consecUpSize > 2 * avgSize ))
					  ||(( consecUp.getSize() == 2 ) && ( consecUpSize > 3 * avgSize ))
					  ||(( consecUp.getSize() == 3 ) && ( consecUpSize > 4 * avgSize ))
					  ||(( consecUp.getSize() > 3 ) && ( consecUpSize > consecUp.getSize() * 1.5 * avgSize )))
					{
						// detected
						fine("break out start detected at " + quotesL[consecUp.getBegin()].time);
						if ( findTradeHistory( Constants.ACTION_BUY, quotesL[consecUp.getBegin()].time) != null )
							return null;

						createOpenTrade(Constants.TRADE_PULLBACK, Constants.ACTION_BUY);
						trade.status = Constants.STATUS_DETECTED;
						
						trade.detectTime = quotesL[lastbarL].time;
						trade.setFirstBreakOutTime(quotesL[consecUp.getEnd()].time);
						trade.setFirstBreakOutStartTime(quotesL[consecUp.getBegin()].time);
						
						trade.entryPrice = trade.triggerPrice = quotesL[lastbarL-2].high;
						trade.POSITION_SIZE = POSITION_SIZE;

						warning("break UP detected at " + data.time + " start:" + quotesL[consecUp.getBegin()].time );
						
						return trade;
					
					}
				}
			}
		}	
		
		return null;
	}

	
	
	
	
	public Trade checkBreakOutM4a(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		//labelPositions( quotesL );
		
		//System.out.println("check break out");
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
			//	return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotesL[lastUpPosStart].low > ema20L[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			while ( quotesL[prevDownPos-1].low < quotesL[prevDownPos].low )
				prevDownPos--;

			
			
					
			// looking for upside
			for ( start = prevDownPos+1; start < lastbarL; start++)
				if (( quotesL[start].low > ema20L[start]) && ( quotesL[start+1].low > ema20L[start+1]))
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			// now it is the first up
			
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].low <= ema20L[i])
				{
					touch20MA = i;
					fine("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			/*
			int touch20MA = 0;
			for ( int i = start+1 ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].high < quotesL[i].high ) && ( quotesL[i+2].high < quotesL[i].high ))
				{
					touch20MA = i+1;
					debug("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			*/

			QuoteData firstBreakOut = Utility.getHigh( quotesL, start, touch20MA-1);
			if ( firstBreakOut != null )
			{
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)) && !trade.status.equals(Constants.STATUS_PLACED))
				{
					removeTrade();
				}

				for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
					if ( quotesL[i].high > firstBreakOut.high )
					{
						fine("first breakout high missed at " + quotesL[i].time);
						return null;
					}
				
				
				// run trend analyser
				String tradeType = Constants.TRADE_EUR;
				/*
				int lastUpTrend = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, start, 24);
				if ( lastUpTrend < start - 24 )
				{

				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				/*
				int prevDownStart = prevDownPos-1;
				while ( ! (( quotesL[prevDownStart-1].high > ema20L[prevDownStart-1]) && ( quotesL[prevDownStart-2].high > ema20L[prevDownStart-2])))
						prevDownStart--;
				
				if ( prevDownPos - prevDownStart < 24 )
					return null;
					*/
				
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				//System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;
						
						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;
						
						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}*/

				
				//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
				//BigTrend bt = TrendAnalysis.determineBigTrend_Trendy(quotes240 );
				//if (!Constants.DIRT_UP.equals(bt.direction))
				//		return null;

				
				
				createOpenTrade(tradeType, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.prevDownStart = prevDownPos;
//				trade.lastLargeMoveDirection = lastLargeMoveDirection;
//				trade.lastLargeMoveEnd = lastLargeMoveEnd;
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
				trade.POSITION_SIZE = POSITION_SIZE;

				warning("break UP detected at " + data.time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				
				return trade;
			}
		}	
		else if ( lastDownPos > lastUpPos )
		{	
			//debug("check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
			//	return null;

			int lastDownPosStart = lastDownPos;
			while ( quotesL[lastDownPosStart].high < ema20L[lastDownPosStart])
				lastDownPosStart--;
			
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 2);
			//prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			//int prevUpPos1 = prevUpPos;  //
			//int prevDownPosBeforeUp = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, prevUpPos1, 2); //
			//if ( prevDownPosBeforeUp > prevUpPos1-48 )//
			//	return null;//
			
			while ( quotesL[prevUpPos-1].high > quotesL[prevUpPos].high )
				prevUpPos--;

			// looking for upside
			for ( start = prevUpPos+1; start < lastbarL; start++)
			{
				if (( quotesL[start].high < ema20L[start]) && ( quotesL[start+1].high < ema20L[start+1]))
					break;
			}
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[start].time) != null )
				return null;

			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].high >= ema20L[i])
				{
					touch20MA=i;
					fine( "touch20MA is" + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			/*
			int touch20MA = 0;
			for ( int i = start ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].low > quotesL[i].low ) && ( quotesL[i+2].low > quotesL[i].low ))
				{
					touch20MA = i+1;
					warning("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
*/

			QuoteData firstBreakOut = Utility.getLow( quotesL, start, touch20MA-1);
			if ( firstBreakOut != null )
			{
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)) && !trade.status.equals(Constants.STATUS_PLACED))
				{
					removeTrade();
				}

				for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
					if ( quotesL[i].low < firstBreakOut.low )
					{
						fine("first breakout low missed at " + quotesL[i].time);
						return null;
					}


				// run trend analyser
			//	String tradeType = Constants.TRADE_EUR;
				/*
				int lastDownTrend = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, start, 24);
				if ( lastDownTrend < start - 24 )
				{
				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				
				/*
				int prevUpStart = prevUpPos1-1;
				while ( ! (( quotesL[prevUpStart-1].low < ema20L[prevUpStart-1]) && ( quotesL[prevUpStart-2].low < ema20L[prevUpStart-2])))
					prevUpStart--;
				
				if ( prevUpPos - prevUpStart < 24 )
					return null;
*/
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;

						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;

						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}
				
				/*
				if ( tradeType == Constants.TRADE_RVS )
				{	
				System.out.println("large move end :" + lastLargeMoveEnd.time);
				for ( int i =lastLargeMoveEnd.pos+1; i < lastbarL-2; i++)
				{
					if (( quotesL[i].low < ema20L[i]) && ( quotesL[i+1].low < ema20L[i+1]))
					{
						int latestUp = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
						
						if ( latestUp > i+1 )
						{
							warning (" find latest diverage at " + quotesL[latestUp].time + " trade removed");
							//return null;
						}
						break;
					}
				}
				
				}*/
				
				
				
				
				
				
				
				
				
				//BigTrend bt = TrendAnalysis.determineBigTrend_Trendy(quotes240 );
				//if (!Constants.DIRT_DOWN.equals(bt.direction))
				//		return null;
				
				
				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;

				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.prevUpStart = prevUpPos;
			//	trade.lastLargeMoveDirection = lastLargeMoveDirection;
			//	trade.lastLargeMoveEnd = lastLargeMoveEnd;

				trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
				trade.POSITION_SIZE = POSITION_SIZE;
				
				warning("break DOWN detected at " + quotesL[lastbarL].time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				return trade;
			}
		}
		
		return null;
	}


	
	public void trackPullBackTradeSell(QuoteData data, double price)
	{
		//QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		//int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		//double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int start = trade.getFirstBreakOutStartPosL(quotesL);
		int firstBreakOutL = trade.getFirstBreakOutPos(quotesL);
		double entryPrice = quotesL[firstBreakOutL].low;


		QuoteData highAfterFirstBreakOut = Utility.getHigh( quotesL, firstBreakOutL+1, lastbarL );
		if (( highAfterFirstBreakOut != null ) && ((highAfterFirstBreakOut.high - entryPrice) > 1.5 * getFixedStopSize() * PIP_SIZE))
		{
			double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
			if ( diverage > 1.5 * getFixedStopSize() )
			{
				double push = (quotesL[trade.prevUpStart].high - entryPrice)/PIP_SIZE;
				{
					info("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
					removeTrade();   
					return;
				}
			}
		}

		
		double triggerPrice = 99999;
		if ( data != null )
			triggerPrice = data.low;
		
		if ( market_order == true ) 
		{
			if (triggerPrice < entryPrice ) 
			{
				trade.setEntryTime(quotesL[lastbarL].time);
				trade.entryPrice = entryPrice;
	
				warning("break DOWN trade entered at " + data.time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice );
				if ( data != null )
					warning(data.time + " " + data.high + " " + data.low );
				else
					warning("last tick:" + price);
					
	
				trade.stop = trade.entryPrice + getFixedStopSize() * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
	
				if ( MODE == Constants.REAL_MODE )
				{	
					if (( ( data != null ) && (( data.timeInMillSec - firstRealTime ) < 60*60000L ) && (Math.abs( triggerPrice - entryPrice) > 5  * PIP_SIZE ))
						||  (Math.abs( triggerPrice - entryPrice) > 7  * PIP_SIZE ))
					{
						warning("Entry missed, set limit order of " + trade.entryPrice);
						entryPrice = adjustPrice( entryPrice, Constants.ADJUST_TYPE_UP);
						trade.openOrderPlacedTimeInMill = currQuoteData.timeInMillSec;
						trade.openOrderDurationInHour = 3;
						trade.limit1Stop = adjustPrice(entryPrice + getFixedStopSize()  * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						enterLimitPosition1(entryPrice, trade.POSITION_SIZE, trade.limit1Stop); 
						return;
					}
				}
				
				enterMarketPosition(entryPrice);
				//enterLimitPosition1(entryPrice);
			}
		}
		else
		{
			if ((triggerPrice < entryPrice + 0.3 * getFixedStopSize() * PIP_SIZE) && ( trade.stopMarketPlaced == false ))
			{
				trade.stopMarketStopPrice = adjustPrice(entryPrice, Constants.ADJUST_TYPE_UP);
				trade.stop = entryPrice + getFixedStopSize() * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
	
				Date time = new Date();
				trade.openOrderPlacedTimeInMill = time.getTime();
				trade.openOrderDurationInHour = 3;
				trade.openOrderExpireInMill = trade.openOrderPlacedTimeInMill + 3*60*60000L;
				time.setTime(trade.openOrderExpireInMill);
				String goodTill = OrderDateFormatter.format(time);
				enterStopMarketPosition(goodTill);

				trade.stopMarketPlaced = true;
				warning(data.time + " place stop market " + trade.action + " order, orderId:" + trade.stopMarketId + " stop triggerPrice:" + trade.stopMarketStopPrice + " good till:" + goodTill);

			}
		}
		
	}


	
	public void trackPullBackTradeBuy(QuoteData data, double price )
	{
		//QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		//int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		//double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int start = trade.getFirstBreakOutStartPosL(quotesL);
		int firstBreakOutL = trade.getFirstBreakOutPos(quotesL);
		double entryPrice = quotesL[firstBreakOutL].high;

		
		QuoteData lowAfterFirstBreakOut = Utility.getLow( quotesL, firstBreakOutL+1, lastbarL );
		if (( lowAfterFirstBreakOut != null ) && ((entryPrice - lowAfterFirstBreakOut.low) > 1.5 * getFixedStopSize() * PIP_SIZE))
		{
			double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
			if ( diverage > 1.5 * getFixedStopSize() )
			{
				double push = (quotesL[trade.prevUpStart].high - entryPrice)/PIP_SIZE;
				{
					info("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
					removeTrade();   
					return;
				}
			}
		}

		
		
		double triggerPrice = 0;
		if ( data != null )
			triggerPrice = data.high;

		if ( market_order == true )
		{
			if (triggerPrice > entryPrice) 
			{
				trade.setEntryTime(quotesL[lastbarL].time);
				trade.entryPrice = entryPrice;
				
				
				trade.targetPrice = adjustPrice(entryPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_UP);

				warning("break UP trade entered at " + data.time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice );
				if ( data != null )
					warning(data.time +	" " + data.high + " " + data.low );
				else
					warning("last tick:" + price);
					
				trade.stop = trade.entryPrice - getFixedStopSize() * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
	
				if ( MODE == Constants.REAL_MODE )
				{	
					if ((( data != null ) && (( data.timeInMillSec - firstRealTime ) < 60*60000L ) && (Math.abs( triggerPrice - entryPrice) > 5  * PIP_SIZE ))
						||  (Math.abs( triggerPrice - entryPrice) > 7  * PIP_SIZE ))
					{
						warning("Entry missed, set limit order of " + trade.entryPrice);
						entryPrice = adjustPrice( entryPrice, Constants.ADJUST_TYPE_DOWN);
						trade.openOrderPlacedTimeInMill = currQuoteData.timeInMillSec;
						trade.openOrderDurationInHour = 3;
						trade.limit1Stop = adjustPrice(entryPrice - getFixedStopSize()  * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						enterLimitPosition1(entryPrice, trade.POSITION_SIZE, trade.limit1Stop); 
						return;
					}
				}
	
				enterMarketPosition(entryPrice);
				//enterLimitPosition1(entryPrice);
	
			}
		}
		else
		{
			if ((triggerPrice >  entryPrice - 0.25 * getFixedStopSize() * PIP_SIZE) && ( trade.stopMarketPlaced == false ))
			{
				trade.stopMarketStopPrice = adjustPrice(entryPrice, Constants.ADJUST_TYPE_DOWN);
				trade.stop = entryPrice - getFixedStopSize() * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
	
				Date time = new Date();
				trade.openOrderPlacedTimeInMill = time.getTime();
				trade.openOrderDurationInHour = 3;
				trade.openOrderExpireInMill = trade.openOrderPlacedTimeInMill + 3*60*60000L;
				time.setTime(trade.openOrderExpireInMill);
				String goodTill = OrderDateFormatter.format(time);
				enterStopMarketPosition(goodTill);

				trade.stopMarketPlaced = true;
				warning(data.time + " place stop market " + trade.action + " order, orderId:" + trade.stopMarketId + " stop triggerPrice:" + trade.stopMarketStopPrice + " good till:" + goodTill );
			}
		}
	}
	
	
	
	public void exit123_close_monitor( QuoteData data )
	{
		QuoteData[] quote5 = getQuoteData(Constants.CHART_5_MIN);
		int lastbar5 = quote5.length - 1;

		QuoteData[] quote15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quote15.length - 1;
		
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
		
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		BarTrendDetector tbdd240 = new BarTrendDetector(quotes240, PIP_SIZE);

		QuoteData[] quotesL = getQuoteData(Constants.CHART_DAILY);
		BarTrendDetector tbddD = new BarTrendDetector(quotesL, PIP_SIZE);

		int tradeEntryPos60 = Utility.findPositionByTime(quotes60, trade.entryTime60, Constants.BACK_TO_FRONT);
		int tradeEntryPos15 = Utility.findPositionByTime( quote15, trade.entryTime15, Constants.BACK_TO_FRONT);
		
		int timePassed = lastbar60 - tradeEntryPos60; 
		finest("trade entry time:" + trade.entryTime + " entry pos 60=" + tradeEntryPos60 + " tradeEntryPos15:" + tradeEntryPos15); 

		Date now = new Date(data.timeInMillSec);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime( now );
		int curr_day = calendar.get(Calendar.DAY_OF_WEEK);
		String hr = data.time.substring(9,12).trim();
		int curr_hour = new Integer(hr);

		/*********************************************************************
		 *  status: closed, do nothing
		 *********************************************************************/
		// closed trade, do nothing
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
		
		double profitLossAfterEntry = 0;
		double lowestPointAfterEntry = Utility.getLow(quotes60, tradeEntryPos60, lastbar60).low; 
		double highestPointAfterEntry = Utility.getHigh(quotes60, tradeEntryPos60, lastbar60).high;
		if (Constants.ACTION_SELL.equals(trade.action)){
			profitLossAfterEntry = trade.entryPrice - lowestPointAfterEntry;
		}
		else if (Constants.ACTION_BUY.equals(trade.action)){
			profitLossAfterEntry = highestPointAfterEntry - trade.entryPrice;
		}

		/*********************************************************************
		 *  status: stopped out, check to reverse
		 *********************************************************************/
		if (Constants.STATUS_STOPPEDOUT.equals(trade.status)){ 	
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

			if ( reverse_trade != true ){
				info("reverse not turned on, remove trade");
				removeTrade();
				return;
			}

			info("reverse trade is set to trade, try to reverse the trade");
			double reversePrice = prevStop;
			boolean reversequalified = false;

			//  look to reverse if it goes against me soon after entry
			//BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes60);
			//BigTrend bt2 = TrendAnalysis.determineBigTrend2050_2( quotes60);
			/*if (Constants.ACTION_SELL.equals(prevAction) && (tbddD.getLastTrend().direction == Constants.DIRECTION_UP ||  tbdd240.getLastTrend().direction == Constants.DIRECTION_UP)){
				lowestPointAfterEntry = Utility.getLow(quotes60, tradeEntryPos60, lastbar60).low;
				info("low point after entry is " + lowestPointAfterEntry + " entry price:" + prevEntryPrice); 
				
				//if ((( prevEntryPrice - lowestPointAfterEntry) < getFixedStopSize() * PIP_SIZE * 0.3 ) || 
					//(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) && (( prevEntryPrice - lowestPointAfterEntry) < getFixedStopSize() * PIP_SIZE ))) 
				if ((( prevEntryPrice - lowestPointAfterEntry) < getFixedStopSize() * PIP_SIZE * 0.275 ))/* && 
					( Constants.DIRECTION_UP == bt2.dir )) */
				/*{
					//System.out.println(bt.toString(quotes60));
					info("close trade with small tip, reverse trade to buy qualified");
					//System.out.println(bt2.toString(quotes60));
					reversequalified = true;
				}
			}
			else if (Constants.ACTION_BUY.equals(prevAction) && (tbddD.getLastTrend().direction == Constants.DIRECTION_DOWN ||  tbdd240.getLastTrend().direction == Constants.DIRECTION_DOWN)){
				highestPointAfterEntry = Utility.getHigh(quotes60, tradeEntryPos60, lastbar60).high;
				info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 
 
				//if ((( highestPointAfterEntry - prevEntryPrice) < getFixedStopSize() * PIP_SIZE * 0.3 ) ||
				    //(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < getFixedStopSize() * PIP_SIZE )))
				if ((( highestPointAfterEntry - prevEntryPrice) < getFixedStopSize() * PIP_SIZE * 0.275 ))/* &&
					( Constants.DIRECTION_DOWN == bt2.dir )) */
				/*{
					info("close trade with small tip, reverse trade to sell qualified");
					//System.out.println(bt2.toString(quotes60));
					reversequalified = true;
				}
			}*/
			//  look to reverse if it goes against me soon after entry
			int tradeEntryPos5 = Utility.findPositionByTime( quote5, trade.entryTime5, Constants.BACK_TO_FRONT);
			int numOfBarAboveEntry = 0;
			for (int i = tradeEntryPos5; i <= lastbar5; i++){
				if (Constants.ACTION_BUY.equals(prevAction) && ( quote5[i].high > prevEntryPrice )) 
					numOfBarAboveEntry++;
				else if (Constants.ACTION_SELL.equals(prevAction) && ( quote5[i].low < prevEntryPrice )) 
					numOfBarAboveEntry++;
			}
			if (numOfBarAboveEntry <= 10){  // only over the entry price less than 60 minutes before stopping out
				reversequalified = true;
			}

			
			//if ((lastbar1- tradeEntryPos1) <= 1){  // stop out less than 2 hours
			//	reversequalified = true;
			//}

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
		

		/*********************************************************************
		 *  detect fake breakout, not enabled
		 *********************************************************************/
		/*int tradeEntryPos1 = Utility.findPositionByTime( quote1, trade.entryTime1, Constants.BACK_TO_FRONT);
		int numOfBarAboveEntry1 = 0;
		int pullBackSize = 0;
		int pb = 0;
		double highestPoint = 0;  // FOR BUY
		double lowestPullback = 999999;
		double lowestPoint = 999999; // FOR SELL
		double highestPullback = 0;
		for (int i = tradeEntryPos1; i <= lastbar1; i++){
			if (Constants.ACTION_BUY.equals(trade.action) && ( quote1[i].high > trade.entryPrice )){ 
				numOfBarAboveEntry1++;
				if ( quote1[i].high > highestPoint )
					highestPoint = quote1[i].high;
				if ( quote1[i].low < lowestPullback )
					lowestPullback = quote1[i].low ;
				pb = (int)((trade.entryPrice - quote1[i].low)/PIP_SIZE);
				if ( pb > pullBackSize)
					pullBackSize = pb;
			}
			else if (Constants.ACTION_SELL.equals(trade.action) && ( quote1[i].low < trade.entryPrice )) 
				numOfBarAboveEntry1++;
				if ( quote1[i].low < lowestPoint )
					lowestPoint = quote1[i].low;
				if ( quote1[i].high > highestPullback)
					highestPullback = quote1[i].high;
				pb = (int)((quote1[i].high - trade.entryPrice)/PIP_SIZE);
				if ( pb > pullBackSize)
					pullBackSize = pb;
		}*/

		
	
		/*********************************************************************
		 *  tip-reversal, this does not work
		 *********************************************************************/
		if ((tip_reverse) &&  !Constants.TRADE_CNT.equals(trade.type) /*&& ( numOfBarAboveEntry1 < 120 )*/){
			if ( Constants.ACTION_SELL.equals(trade.action) && ( tbddD.getLastTrend().direction == Constants.DIRECTION_UP)){
				if ( (data.close > lowestPointAfterEntry + getFixedStopSize()*PIP_SIZE)){ 
					double price = lowestPointAfterEntry + getFixedStopSize()*PIP_SIZE;
					warning("close sell trade with preempertive reversal");
					int remainingPositionSize = trade.remainingPositionSize; 
					closePositionByMarket( remainingPositionSize, price/*data.close*/, false );
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
					enterMarketPosition(data.close, remainingPositionSize);
					return;
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action) && ( tbddD.getLastTrend().direction == Constants.DIRECTION_DOWN)){
				if ((data.close < highestPointAfterEntry - getFixedStopSize()*PIP_SIZE)){ 
					double price = lowestPointAfterEntry - getFixedStopSize()*PIP_SIZE;
					warning("close buy trade with preempertive reversal");
					int remainingPositionSize = trade.remainingPositionSize; 
					closePositionByMarket( remainingPositionSize, price/*data.close*/, false );
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
					enterMarketPosition(data.close, remainingPositionSize);
					return;
				}
			}
		}
		
		
		
		
		/*********************************************************************
		 *  preemptive_reveral, does not seem to work
		 *********************************************************************/
		double avg15 = BarUtil.averageBarSize(quote15);
		if (preemptive_reversal && !Constants.TRADE_CNT.equals(trade.type)){
			/*if (( numOfBarAboveEntry1 < 10 ) && ( pullBackSize >= (FIXED_STOP * 2 / 3)))*/ {
				if ( Constants.ACTION_SELL.equals(trade.action)){// && quotel[lastbar1].high > highestPullback -  ){
					if ((( lastbar60 < tradeEntryPos60 + 5 ) && (profitLossAfterEntry < 1.5*getFixedStopSize()*PIP_SIZE) && BarUtil.isUpBar(quote15[lastbar15-1]) && ( BarUtil.barSize(quote15[lastbar15-1]) > 2.5 * avg15))){
							double price = lowestPointAfterEntry + getFixedStopSize()*PIP_SIZE;
							warning("close sell trade with preempertive reversal");
							int remainingPositionSize = trade.remainingPositionSize; 
							closePositionByMarket( remainingPositionSize, price/*data.close*/, false );
							createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
							enterMarketPosition(data.close, remainingPositionSize);
							return;
					}
				}
				else if (Constants.ACTION_BUY.equals(trade.action)){
					if ((( lastbar60 < tradeEntryPos60 + 5 ) && (profitLossAfterEntry < 1.5*getFixedStopSize()*PIP_SIZE) && BarUtil.isDownBar(quote15[lastbar15-1]) && ( BarUtil.barSize(quote15[lastbar15-1]) > 2.5 * avg15))){
							double price = lowestPointAfterEntry - getFixedStopSize()*PIP_SIZE;
							warning("close buy trade with preempertive reversal");
							int remainingPositionSize = trade.remainingPositionSize; 
							closePositionByMarket( remainingPositionSize, price/*data.close*/, false );
							createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
							enterMarketPosition(data.close, remainingPositionSize);
							return;
					}
				}
			}
		}
		
		
		/*********************************************************************
		 *  add additional positions
		 *********************************************************************/
		if ((average_up == true ) && (trade.averageUp == false )&& (lastbar15 < tradeEntryPos15 + 3 )){
			if (Constants.ACTION_SELL.equals(trade.action) && BarUtil.isDownBar(quote15[lastbar15-1]) && ( BarUtil.barSize(quote15[lastbar15-1]) > 4 * avg15)){
				enterMarketPositionAdditional(data.close, POSITION_SIZE);
				trade.averageUp = true;
			}
			else if (Constants.ACTION_BUY.equals(trade.action) && BarUtil.isUpBar(quote15[lastbar15-1]) && ( BarUtil.barSize(quote15[lastbar15-1]) > 4 * avg15)){
				enterMarketPositionAdditional(data.close, POSITION_SIZE);
				trade.averageUp = true;
			}
		}

		
		/*********************************************************************
		 *  status: detect big spike moves
		 *********************************************************************/
		double avgSize60 = BarUtil.averageBarSizeOpenClose( quotes60 );
		if ( Math.abs(quotes60[lastbar60].open - quotes60[lastbar60].close ) > 3 * avgSize60 ){
			super.strategy.sentAlertEmail(AlertOption.big_rev_bar, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
		}
		else if ( Math.abs(quotes60[lastbar60].close - quotes60[lastbar60].open ) > 3 * avgSize60 ){
			super.strategy.sentAlertEmail(AlertOption.big_bar, trade.symbol + " " + trade.action + " " + trade.triggerPrice, trade);
		}
		
		
		/*********************************************************************
		 *  status: detect profit and move stop
		 *********************************************************************/
		if (profitLossAfterEntry > getFixedStopSize() * PIP_SIZE )
			trade.reach1FixedStop = true;

		if (!trade.reach2FixedStop && (profitLossAfterEntry > 2 * getFixedStopSize() * PIP_SIZE)){
			info("place break even order");
			placeBreakEvenStop();
			trade.reach2FixedStop = true;
		}

		
		// detect support/resistence ahead
		/*
		int supportResistence = -1;
		if (Constants.ACTION_SELL.equals(trade.action)){
			if (( supportResistence = SupportResistence.findSupport(quotes60, tradeEntryPos60-48, tradeEntryPos60, 12)) > 0 ){
				//System.out.println("support found at " + quotes60[supportResistence].time + " " + quotes60[supportResistence].low);
				double triggerPrice = quotes60[supportResistence].low;
				if ( !trade.finalPeakExisting){
					double exitTargetPrice =  adjustPrice((triggerPrice), Constants.ADJUST_TYPE_DOWN);
					int posSize = (int)(POSITION_SIZE * 0.5);
					createTradeTargetOrder( posSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (( supportResistence = SupportResistence.findResistence(quotes60, tradeEntryPos60-48, tradeEntryPos60, 12)) > 0 ){
				//System.out.println("support found at " + quotes60[supportResistence].time + " " + quotes60[supportResistence].low);
				double triggerPrice = quotes60[supportResistence].high;
				if ( !trade.finalPeakExisting){
					double exitTargetPrice =  adjustPrice((triggerPrice), Constants.ADJUST_TYPE_UP);
					int posSize = (int)(POSITION_SIZE * 0.5);
					createTradeTargetOrder( posSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}
			}
		}*/
		
		
		if (lastbar60 < tradeEntryPos60 + 2)
			return;

		// detect support/resistence ahead
		if ( otherside_ma == true ){
			if (Constants.ACTION_SELL.equals(trade.action)){
				if ((quotes60[lastbar60-2].low > ema20_60[lastbar60-2]) && (quotes60[lastbar60-1].low > ema20_60[lastbar60-1]) && (quotes60[lastbar60].low < ema20_60[lastbar60])){ 
					int begin = lastbar60-2;
					while (quotes60[begin].low > ema20_60[begin])
						begin--;
					double stop = Utility.getHigh(quotes60, begin, lastbar60-1).high;
					if ( trade.stop > stop ){
						cancelOrder(trade.stopId);
						trade.stop = adjustPrice(stop, Constants.ADJUST_TYPE_UP);
						info("place new buy stop on otherside 20MA " + trade.stop + " " + trade.remainingPositionSize );
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action)){
				if ((quotes60[lastbar60-2].high < ema20_60[lastbar60-2]) && (quotes60[lastbar60-1].high < ema20_60[lastbar60-1]) && (quotes60[lastbar60].high > ema20_60[lastbar60])){ 
					int begin = lastbar60-2;
					while (quotes60[begin].high < ema20_60[begin])
						begin--;
					double stop = Utility.getLow(quotes60, begin, lastbar60-1).low;
					if ( trade.stop < stop ){
						cancelOrder(trade.stopId);
						trade.stop = adjustPrice(stop, Constants.ADJUST_TYPE_DOWN);
						info("place new sell stop on otherside 20MA " + trade.stop + " " + trade.remainingPositionSize);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					}
				}
			}
		}
		

		/*********************************************************************
		 *  status: detect peaks
		 *********************************************************************/
		if (Constants.ACTION_SELL.equals(trade.action)){
			int pushStart = (lastbar60 - tradeEntryPos60 > 4)? tradeEntryPos60+4: lastbar60;
			pushStart = Utility.getHigh(quotes60, tradeEntryPos60-12, pushStart).pos;
			PushList pushList = Pattern.findPast1Lows1(quotes60, pushStart, lastbar60);
			//PushList pushList = PushSetup.getDown1PushList(quotes60, pushStart, lastbar60);
			//PushList pushList = PushSetup.findPastLowsByX(quotes60, pushStart, lastbar60, 1); // this does not use lowest value
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0)){
				PushHighLow[] phls = pushList.phls;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes60[lastPush.prePos].low;
				PushHighLow phl = lastPush;
				double pullback = phl.pullBack.high - quotes60[phl.prePos].low;
				double pullBackvAvgSize = pullback/avgSize60;
				int pushWidth = phl.curPos - phl.prePos - 1;
				//pushList.calculatePushMACD( quotes60);
				//int positive = phls[lastPushIndex].pullBackMACDPositive;
				double[] so = Indicator.calculateStochastics( quotes60, 14 );
				
				//double lastBreakOut = -1;
				//if ( numOfPush >= 2 )
					//lastBreakOut = quotes60[phls[lastPushIndex].prePos].high - quotes60[phls[lastPushIndex-1].prePos].high; 
				
				/*
				System.out.println("numOfPush:" + numOfPush);
				if  ( numOfPush == 6 ){
					System.out.println("here" + BarUtil.averageBarSize(quotes60));
					System.out.println(pushList.toString(quotes60, PIP_SIZE));
					
				}*/
				
				TradeEvent tv = new TradeEvent(TradeEvent.NEW_LOW, quotes60[lastbar60].time);
				tv.addNameValue("Low Price", new Double(quotes60[phl.prePos].low).toString());
				tv.addNameValue("NumPush", new Integer(numOfPush).toString());
				tv.addNameValue("Pullback", new Double(pullback/PIP_SIZE).toString());
				trade.addTradeEvent(tv);
				super.strategy.sentAlertEmail(AlertOption.new_low, trade.symbol + " " + trade.action + " pushNum" + numOfPush + " " + quotes60[phl.prePos].low + " pullback:" + pullback/PIP_SIZE, trade);
				
				if ( timePassed  > 60 ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new low after 60 hours " + quotes60[phl.prePos].low, trade);
				}
				if ( curr_day == Calendar.FRIDAY ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new low on friday " + quotes60[phl.prePos].low, trade );
				}
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				// take profit by size
				/*if (!trade.finalPeakExisting &&( pullback  > 2 * getFixedStopSize() * PIP_SIZE) && ( timePassed  > 84 ) && ( peakwidth < 12 )){ 
					double exitTargetPrice =  adjustPrice((triggerPrice - getFixedStopSize()/2 * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					createTradeTargetOrder( trade.remainingPositionSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}*/

				/*
				if ( !trade.finalPeakExisting && ( pullBackvAvgSize > 5 + pushWidth*0.3 )){
					double exitTargetPrice =  adjustPrice((triggerPrice), Constants.ADJUST_TYPE_DOWN);
					int posSize = (int)(POSITION_SIZE * 0.5);
					createTradeTargetOrder( posSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}*/

				// take profit by time
				double avgBarSize = BarUtil.averageBarSize(quotes60);
				if ( thur_scale_out ){
					if ( ( timePassed  >= 72 ) && ( pullback  > getFixedStopSize() *PIP_SIZE)){
						double exitTargetPrice =  adjustPrice((triggerPrice ), Constants.ADJUST_TYPE_DOWN);
						int posSize = (int)(POSITION_SIZE * 0.5);
						if ((trade.exitTargetPlaced < 2 ) && !targetOrderExists( posSize, exitTargetPrice) ){
							logger.info("place thursday exit order: last peaklow is " + quotes60[phl.prePos].time + " " + triggerPrice );
							createTradeTargetOrder( posSize, exitTargetPrice );
							trade.exitTargetPlaced++;
						}
					}
					/*else if (( curr_day >= Calendar.FRIDAY ) && ( curr_hour > 10 )){
						double exitTargetPrice =  adjustPrice((triggerPrice ), Constants.ADJUST_TYPE_DOWN) - 10 * PIP_SIZE;
						int posSize = (int)(POSITION_SIZE * 0.5);
						if ( !targetOrderExists( posSize, exitTargetPrice) ){
							logger.info("place friday exit order " + triggerPrice );
							createTradeTargetOrder( posSize, exitTargetPrice );
							trade.exitTargetPlaced++;
						}
					}*/
				}

				//double pullbackReq = (2 * getFixedStopSize() +10 - timePassed/4)* PIP_SIZE;
				/*if  (!trade.finalPeakExisting && ( weekday >= Calendar.THURSDAY  ) &&( profitInPip > 2 * getFixedStopSize()  ) && (pullback > getFixedStopSize() * PIP_SIZE )){
					double exitTargetPrice1 =  adjustPrice((triggerPrice - 10 * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					double exitTargetPrice2 =  adjustPrice((triggerPrice - (10 + getFixedStopSize()) * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					double exitTargetPrice3 =  adjustPrice((triggerPrice - (10 + 2*getFixedStopSize()) * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					double exitTargetPrice4 =  adjustPrice((triggerPrice - (10 + 3*getFixedStopSize()) * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					createTradeTargetOrder( trade.remainingPositionSize/4, exitTargetPrice1 );
					createTradeTargetOrder( trade.remainingPositionSize/4, exitTargetPrice2 );
					createTradeTargetOrder( trade.remainingPositionSize/4, exitTargetPrice3 );
					createTradeTargetOrder( trade.remainingPositionSize/4, exitTargetPrice4 );
					trade.finalPeakExisting = true;
				}*/
				
				// move stop after thursday
				/*double pullBackHigh = lastPush.pullBack.high; 
				if ( ( weekday >= Calendar.THURSDAY ) &&  ( trade.stop > pullBackHigh)){
					trade.stop = adjustPrice(pullBackHigh, Constants.ADJUST_TYPE_DOWN);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				}*/
				
				//String takePullBackProfit = this.strategy.sftest.setting.getStrategyPauseTrading(this.strategy.STRATEGY_NAME);
				//if ((pullback > getFixedStopSize()) && takePullBackProfit.contains(symbol) ){
				if ((pullback > getFixedStopSize()) && trade.targetOnPullback ){
					double exitTargetPrice =  adjustPrice((triggerPrice ), Constants.ADJUST_TYPE_DOWN) + PIP_SIZE;
					int posSize = (int)(POSITION_SIZE * 0.25);
					if (!targetOrderExists( posSize, exitTargetPrice) ){
						logger.info("place targeting exit order: last peaklow is " + quotes60[phl.prePos].time + " " + triggerPrice );
						createTradeTargetOrder( posSize, exitTargetPrice );
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int pushStart = (lastbar60 - tradeEntryPos60 > 4)? tradeEntryPos60+4: lastbar60;
			pushStart = Utility.getLow(quotes60, tradeEntryPos60-12, pushStart).pos;
			PushList pushList = Pattern.findPast1Highs1(quotes60, pushStart, lastbar60);
			//PushList pushList = PushSetup.getUp1PushList(quotes60, pushStart, lastbar60);
			//PushList pushList = PushSetup.findPastHighsByX(quotes60, pushStart, lastbar60, 1);  // this does not use the highest value
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0) ){

				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes60[lastPush.prePos].high;
				PushHighLow phl = lastPush;
				double pullback = quotes60[phl.prePos].high - phl.pullBack.low;
				double pullBackvAvgSize = pullback/avgSize60;
				int pushWidth = phl.curPos - phl.prePos - 1;
				//pushList.calculatePushMACD( quotes60);
				//int negatives = phls[lastPushIndex].pullBackMACDNegative;
				double[] so = Indicator.calculateStochastics( quotes60, 14 );


				//System.out.println("push detected:" + data.time + " " + quotes60[phl.prePos].high + " " + so[phl.prePos] + " " + data.high + " " + so[lastbar60]);
				/*if  ( numOfPush == 6 ){
					System.out.println("here" + BarUtil.averageBarSize(quotes60));
					System.out.println(pushList.toString(quotes60, PIP_SIZE));
					
				}*/

				String newHighInfo = "NumOfPush" + numOfPush + " New High: " + quotes60[phl.prePos].high + " Pullback:" + pullback/PIP_SIZE + " pullBack/avgBarsize:" + pullBackvAvgSize + " width:" + pushWidth;
				info(newHighInfo);
				
				TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH, quotes60[lastbar60].time);
				tv.addNameValue("High Price", new Double(quotes60[phl.prePos].high).toString());
				tv.addNameValue("NumPush", new Integer(numOfPush).toString());
				tv.addNameValue("Pullback", new Double(pullback/PIP_SIZE).toString());
				super.strategy.sentAlertEmail(AlertOption.new_high, trade.symbol + " " + trade.action + " pushNum" + numOfPush + " " + quotes60[phl.prePos].high + " pullback:" + pullback/PIP_SIZE, trade);

				//System.out.println(data.time + " triggerPrice:" + triggerPrice + " " + quotes60[phl.prePos].time + " " + so[phl.prePos] + "  " + so[phl.curPos]);
				
				if ( timePassed  > 60 ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new high after 60 hours " + quotes60[phl.prePos].high, trade);
				}
				if ( curr_day == Calendar.FRIDAY ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new high on friday " + quotes60[phl.prePos].high, trade);
				}
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				/*if (!trade.finalPeakExisting && ( pullback  > 2 * getFixedStopSize() * PIP_SIZE) && ( timePassed  > 84 ) && ( peakwidth < 12 )){ 
					double exitTargetPrice =  adjustPrice((triggerPrice + getFixedStopSize()/2 * PIP_SIZE ), Constants.ADJUST_TYPE_UP);
					createTradeTargetOrder( trade.remainingPositionSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}*/

				/*
				if ( !trade.finalPeakExisting && ( pullBackvAvgSize > 5 + pushWidth*0.3 )){
					double exitTargetPrice =  adjustPrice((triggerPrice), Constants.ADJUST_TYPE_UP);
					int posSize = (int)(POSITION_SIZE * 0.5);
					createTradeTargetOrder( posSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}*/

				// take profit by time
				double avgBarSize = BarUtil.averageBarSize(quotes60);
				if ( thur_scale_out ){
					if (( timePassed  >= 72 ) && ( pullback  > getFixedStopSize() *PIP_SIZE)){
						double exitTargetPrice =  adjustPrice((triggerPrice ), Constants.ADJUST_TYPE_UP);
						int posSize = (int)(POSITION_SIZE * 0.5);
						if ((trade.exitTargetPlaced < 2 ) && !targetOrderExists( posSize, exitTargetPrice) ){
							logger.info("place thursday exit order: last peaklow is " + quotes60[phl.prePos].time + " " + triggerPrice );
							createTradeTargetOrder( posSize, exitTargetPrice );
							trade.exitTargetPlaced++;
						}
					}/*else if (( curr_day >= Calendar.FRIDAY ) && ( curr_hour > 10 )){
						double exitTargetPrice =  adjustPrice((triggerPrice ), Constants.ADJUST_TYPE_UP) + 10 * PIP_SIZE;
						int posSize = (int)(POSITION_SIZE * 0.5);
						if ( !targetOrderExists( posSize, exitTargetPrice) ){
							logger.info("place friday exit order " + triggerPrice );
							createTradeTargetOrder( posSize, exitTargetPrice );
							trade.exitTargetPlaced++;
						}
					}*/
				}

				/*
				if  (!trade.finalPeakExisting && ( weekday >= Calendar.THURSDAY  ) && ( profitInPip > 2 * getFixedStopSize() ) && (pullback > getFixedStopSize() * PIP_SIZE )){
					double exitTargetPrice1 =  adjustPrice((triggerPrice + 10 * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					double exitTargetPrice2 =  adjustPrice((triggerPrice + (10 + getFixedStopSize()) * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					double exitTargetPrice3 =  adjustPrice((triggerPrice + (10 + 2*getFixedStopSize()) * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					double exitTargetPrice4 =  adjustPrice((triggerPrice + (10 + 3*getFixedStopSize()) * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					createTradeTargetOrder( trade.remainingPositionSize/4, exitTargetPrice1 );
					createTradeTargetOrder( trade.remainingPositionSize/4, exitTargetPrice2 );
					createTradeTargetOrder( trade.remainingPositionSize/4, exitTargetPrice3 );
					createTradeTargetOrder( trade.remainingPositionSize/4, exitTargetPrice4 );
					trade.finalPeakExisting = true;
				}*/

				// move stop after thursday
				/*double pullBackLow = lastPush.pullBack.low; 
				if ( ( weekday >= Calendar.THURSDAY ) && ( trade.stop < pullBackLow)){
					trade.stop = adjustPrice(pullBackLow, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}*/
				//String takePullBackProfit = this.strategy.sftest.setting.getStrategyPauseTrading(this.strategy.STRATEGY_NAME);
				//if ((pullback > getFixedStopSize()) && takePullBackProfit.contains(symbol) ){
				if ((pullback > getFixedStopSize()) && trade.targetOnPullback ){
					double exitTargetPrice =  adjustPrice((triggerPrice ), Constants.ADJUST_TYPE_UP) + PIP_SIZE;
					int posSize = (int)(POSITION_SIZE * 0.25);
					if (!targetOrderExists( posSize, exitTargetPrice) ){
						logger.info("place targeting exit order: last peaklow is " + quotes60[phl.prePos].time + " " + triggerPrice );
						createTradeTargetOrder( posSize, exitTargetPrice );
					}
				}
			}
		}

		
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
						if ( pullBackDist > 2 * getFixedStopSize() * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotes5[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * getFixedStopSize() * PIP_SIZE)
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
					
					if ((lastCrossDown != Constants.NOT_FOUND )&& (( ema10S[lastCrossDown] - ema10S[lastbar5-1]) > 5 * PIP_SIZE * getFixedStopSize() ))
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
						if ( pullBackDist > 2 * getFixedStopSize() * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotes5[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * getFixedStopSize() * PIP_SIZE)
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
					
					if ((lastCrossUp != Constants.NOT_FOUND )&& ((ema10S[lastbar5-1] - ema10S[lastCrossUp]) > 5 * PIP_SIZE * getFixedStopSize() ))
					{
						warning(data.time + " cross over after large runup detected " + quotes5[lastbar5].time);
						takeProfit( quotes5[lastbar5].close, trade.remainingPositionSize );
					}
				}
			}

		}*/
	
	}
	

	
	
	public void exit123_close_monitor_bak( QuoteData data )
	{
		QuoteData[] quote15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quote15.length - 1;
		
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		
		QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);;
		int lastbar5 = quotes5.length - 1;
		double[] ema20_5 = Indicator.calculateEMA(quotes60, 20);
		
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		double avgSize1 = BarUtil.averageBarSizeOpenClose( quotes1 );

		MATouch[] maTouches = null;

		
		int LOT_SIZE = POSITION_SIZE/2;
		int tradeEntryPosL = Utility.findPositionByHour(quotes60, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quote15, trade.entryTime, Constants.BACK_TO_FRONT);
		int timePassed = lastbar60 - tradeEntryPosL; 

		/*********************************************************************
		 *  status: closed
		 *********************************************************************/
		if  (Constants.STATUS_CLOSED.equals(trade.status))
		{
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


			/******************
			 * to do something
			 */
			// not to do anything at the moment
			return;

		}
		
		/*********************************************************************
		 *  status: stopped out, check to reverse
		 *********************************************************************/
		if (Constants.STATUS_STOPPEDOUT.equals(trade.status)){ 	

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

			if ( reverse_trade != true ){
				info("reverse not turned on, remove trade");
				removeTrade();
				return;
			}
			else
			{	
				info("reverse trade is set to trade, try to reverse the trade");
				double reversePrice = prevStop;
				boolean reversequalified = false;
	
				BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes60);
				if (Constants.ACTION_SELL.equals(prevAction))
				{
					//  look to reverse if it goes against me soon after entry
					double lowestPointAfterEntry = Utility.getLow(quotes60, tradeEntryPosL, lastbar60).low;
					info("low point after entry is " + lowestPointAfterEntry + " entry price:" + prevEntryPrice); 
					
					if ((( prevEntryPrice - lowestPointAfterEntry) < getFixedStopSize() * PIP_SIZE * 0.3 ) || 
					(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) && (( prevEntryPrice - lowestPointAfterEntry) < getFixedStopSize() * PIP_SIZE ))) 
					{
						//System.out.println(bt.toString(quotes60));
						info("close trade with small tip, reverse trade qualified");
						reversequalified = true;
					}
				}
				else if (Constants.ACTION_BUY.equals(prevAction))
				{
					
					//  look to reverse if it goes against me soon after entry
					double highestPointAfterEntry = Utility.getHigh(quotes60, tradeEntryPosL, lastbar60).high;
					info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 
	 
					if ((( highestPointAfterEntry - prevEntryPrice) < getFixedStopSize() * PIP_SIZE * 0.3 ) ||
					     (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < getFixedStopSize() * PIP_SIZE )))
					{
						info("close trade with small tip, reverse trade qualified");
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
					//addTradeEvent( "stopped out");
					return;
				}
			}
		}

		
		
		/*********************************************************************
		 *  status: detect an counter spike move
		 *********************************************************************/
		boolean reversequalified = false;
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check if there is a big move against the trade
			if ((( quotes1[lastbar1].close - quotes1[lastbar1].open ) > 3 * avgSize1 )/* && (Math.abs(quotes5[lastbar5].close - quotes5[lastbar5].high) < 5 * PIP_SIZE)*/)
			{
				//System.out.println("spike UP detected at " + quotes1[lastbar1].time + " " + (quotes1[lastbar1].close - quotes1[lastbar1].open)/PIP_SIZE);
				//addTradeEvent( "spike up detected on 1 minute");
				reversequalified = true;
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ((( quotes1[lastbar1].open - quotes1[lastbar1].close ) > 3 * avgSize1 ) /*&& (Math.abs(quotes5[lastbar5].close - quotes5[lastbar5].low) < 5 * PIP_SIZE)*/)
			{
				//System.out.println("spike DOWN detected " + quotes1[lastbar1].time + " " + (quotes1[lastbar1].open - quotes1[lastbar1].close)/PIP_SIZE);
				//addTradeEvent( "spike down detected on 1 minute");
				reversequalified = true;
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
			if (profitInPip > getFixedStopSize() )
				trade.reach1FixedStop = true;

			if (profitInPip > 2 * getFixedStopSize() )
			{
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
				//addTradeEvent( "2 times stop size profit reached");
			}
					
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
			if (profitInPip > getFixedStopSize() )
				trade.reach1FixedStop = true;

			if ( profitInPip > 2 * getFixedStopSize() )
			{
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
				//addTradeEvent( "2 times stop size profit reached");
			}

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
		

		
		
		if (lastbar60 < tradeEntryPosL + 2)
		return;

		/*********************************************************************
		 *  status: detect peaks
		 *********************************************************************/
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int wave2PtL = 0;
			/*MABreakOutList molL = MABreakOutTouchSetup.findMABreakOutsDown(quotesL, ema20L, tradeEntryPosL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				wave2PtL = 1;
			}
			/*
		
			
			public static MABreakOutList findMABreakOutsDown(QuoteData[] quotes, double[] ema, int start )*/
/*
			int first2above = Pattern.findNextPriceConsectiveAboveMA(quotes60, ema20_5, tradeEntryPosL, 2);
			int first2below = Pattern.findNextPriceConsectiveBelowMA(quotes60, ema20_5, tradeEntryPosL, 2);
			if (( first2above < first2below ) && ( first2above > 0 ))
			{
				wave2PtL = -1;
			}
			else
			{	
				maTouches = MABreakOutTouchSetup.findNextMATouchUpFromGoingDowns( quotes60, ema20_5, tradeEntryPosL, 2);
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
	*/		
			
			int pushStart = (lastbar60 - tradeEntryPosL > 4)? tradeEntryPosL+4: lastbar60;
			pushStart = Utility.getHigh(quotes60, tradeEntryPosL-12, pushStart).pos;
			PushList pushList = PushSetup.getDown2PushList(quotes60, pushStart, lastbar60);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0)){

				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes60[lastPush.prePos].low;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				double pullback = phl.pullBack.high - quotes60[phl.prePos].low;
				pushList.calculatePushMACD( quotes60);
				int positive = phls[lastPushIndex].pullBackMACDPositive;

				//addTradeEvent( "peack low reached: " + quotes60[phl.prePos].low);
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if (!exitTradePlaced())
				{
					
					if ( numOfPush == 1 )
					{
						lastBreakOut1 = pushList.phls[0].breakOutSize;
						lastPullBack1 = pushList.phls[0].pullBackSize;
						lastBreakOut2 = 0;
						lastPullBack2 = 0;
					}
					else  
					{
						lastBreakOut1 = pushList.phls[lastPushIndex].breakOutSize;
						lastBreakOut2 = pushList.phls[lastPushIndex-1].breakOutSize;
						lastPullBack1 = pushList.phls[lastPushIndex].pullBackSize;
						lastPullBack2 = pushList.phls[lastPushIndex-1].pullBackSize;
					}
							
					for ( int i = 1; i < numOfPush; i++ )
					{
						if ( phls[i].breakOutSize > 0.3 * getFixedStopSize() * PIP_SIZE)
						{
							largePushIndex = i;
							break;
						}
					}
					
					// calculate touch20MA
					//pushList.calculatePushTouchMA( quotesL, ema20L);
					//wave2PtL = pushList.getTouch20MANum(); 
	
					
					// take profit rule 1:
					/*
					if ( numOfPush >= 3 )
					{
						
						if (( phls[lastPushIndex-2].breakOutSize > 0.3 * getFixedStopSize() * PIP_SIZE ) && ( phls[lastPushIndex-1].breakOutSize < 0.2 * getFixedStopSize() * PIP_SIZE ))
						{
							//takePartialProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
							takeProfit2( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
						}
					}*/
					
					// take profit rule 2:
	
					
					
					/******************************************************************************
					// look for pullbacks
					 * ****************************************************************************/
	//				if ( pushList.end == lastbarL)
					{
						if (( numOfPush >= 4 ) && ( largePushIndex == 0 ))
						{
							//takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							/*if (!trade.type.equals(Constants.TRADE_CNT))
								closeReverseTrade( true, triggerPrice, POSITION_SIZE );
							else
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );*/
							//return;
						}
						

						if ( pullback  > 3 * getFixedStopSize() * PIP_SIZE){
							//if ( wave2PtL != 0 )
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if (  pullback  > 1.5 * getFixedStopSize() * PIP_SIZE ){
							// positive > 0, wave2PtL != 0
							// takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
						}
						else
						{
							if ( (trade.reach2FixedStop == false) && ( timePassed >= 24 ))
							{
								MATouch[] maTouches2 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotes60, ema20_5, tradeEntryPosL, 2);
								MATouch[] maTouches1 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotes60, ema20_5, tradeEntryPosL, 1);
	
								double prevProfit = trade.entryPrice - quotes60[phl.prePos].low;
								double avgProfit = prevProfit / ( lastbar60 - tradeEntryPosL );
								if ( avgProfit > 0 )
								{	
									//double avgPullBack = pullback / ( lastbarL - phl.prePos);
									
									//if (( pullback > 0.7 * getFixedStopSize() * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
									if (( maTouches2.length >=4 ) || maTouches1.length >= 6 )
									//if ( numOfPush >= 4 )
									{
										logger.info(data.time + " take profit on disporportional pull back");
//comment out 20150224										takeProfit( adjustPrice(quotes60[phl.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
									}
								}
								
								
								PositionToMA ptm = Pattern.countAboveMA(quotes60, ema20_5, tradeEntryPosL, lastbar60);
								float numberOfbars = lastbar60-tradeEntryPosL;
								if (ptm.below < ptm.above ) 
								{
									System.out.println(data.time + " take profit on disporportional pull back2");
//comment out 20150224									takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
	
								if ( lastbar60 >= tradeEntryPos + 8 )
								{	
									float numAbove = 0;
									for ( int j = tradeEntryPosL+1; j <=lastbar60; j++)
									{	
										if ( quotes60[j].low > trade.entryPrice )
											numAbove += 1;
									}
								
									if ( numAbove/numberOfbars > 0.6 )
									{
										logger.info(data.time + " take profit on disporportional pull back 3");
//comment out 20150224										takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
									}
								}
							}
						}
					}
				}
				
				
				/******************************************************************************
				// move stops
				 * ****************************************************************************/
				if (trade.reach2FixedStop == true)
				{	
					// count the pull bacck bars
					int pullbackcount = 0;
					for ( int j = phl.prePos+1; j < phl.curPos; j++)
						if ( quotes60[j+1].high > quote15[j].high)
							pullbackcount++;
					
					//System.out.println("pullback count=" + pullbackcount);
					//if ( pullbackcount >= 2 )
					{
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_UP);
						if ( stop < trade.stop )
						{
							//System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
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

			if (lastbar60 < tradeEntryPosL + 2)
				return;
			

			// calculate touch 20MA point
			int wave2PtL = 0;
			/*MABreakOutList molL = MABreakOutTouchSetup.findMABreakOutsUp(quotesL, ema20L, tradeEntryPosL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				wave2PtL = 1;
			}*/

		/*	
			int first2above = Pattern.findNextPriceConsectiveAboveMA(quotes60, ema20_5, tradeEntryPosL, 2);
			int first2below = Pattern.findNextPriceConsectiveBelowMA(quotes60, ema20_5, tradeEntryPosL, 2);
			if (( first2below > 0 ) && ( first2below < first2above ))
			{
				wave2PtL = -1;
			}
			else
			{	
				maTouches = MABreakOutTouchSetup.findNextMATouchDownsFromGoingUps( quotes60, ema20_5, tradeEntryPosL, 2);
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
*/
			
			//int pushStart = (trade.prevDownStart > 12)? trade.prevDownStart-12:0;
			//pushStart = Utility.getLow(quotesL, pushStart, lastbarL).pos;

			int pushStart = (lastbar60 - tradeEntryPosL > 4)? tradeEntryPosL+4: lastbar60;
			pushStart = Utility.getLow(quotes60, tradeEntryPosL-12, pushStart).pos;

			PushList pushList = PushSetup.getUp2PushList(quotes60, pushStart, lastbar60);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0)){

				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes60[lastPush.prePos].high;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				double pullback = quotes60[phl.prePos].high - phl.pullBack.low;
				pushList.calculatePushMACD( quotes60);
				int negatives = phls[lastPushIndex].pullBackMACDNegative;

				//addTradeEvent( "peack high reached: " + quotes60[phl.prePos].high);
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if (!exitTradePlaced())
				{
					if ( numOfPush == 1 )
					{
						lastBreakOut1 = pushList.phls[0].breakOutSize;
						lastPullBack1 = pushList.phls[0].pullBackSize;
						lastBreakOut2 = 0;
						lastPullBack2 = 0;
					}
					else  
					{
						lastBreakOut1 = pushList.phls[lastPushIndex].breakOutSize;
						lastBreakOut2 = pushList.phls[lastPushIndex-1].breakOutSize;
						lastPullBack1 = pushList.phls[lastPushIndex].pullBackSize;
						lastPullBack2 = pushList.phls[lastPushIndex-1].pullBackSize;
					}
					
					for ( int i = 1; i < numOfPush; i++ )
					{
						if ( phls[i].breakOutSize > 0.5 * getFixedStopSize() * PIP_SIZE)
						{
							largePushIndex = i;
							break;
						}
					}
	
					//pushList.calculatePushTouchMA( quotesL, ema20L);
					//wave2PtL = pushList.getTouch20MANum(); 
					
					// take profit rule 1:
					/*
					if ( numOfPush >= 3 )
					{
						if (( phls[lastPushIndex-2].breakOutSize > 0.3 * getFixedStopSize() * PIP_SIZE ) && ( phls[lastPushIndex-1].breakOutSize < 0.2 * getFixedStopSize() * PIP_SIZE ))
						{
							//takePartialProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
							takeProfit2( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), POSITION_SIZE/2 );
						}
					}*/
					
					// take profit rule 2:
		//			if ( pushList.end == lastbarL)
					{
						if (( numOfPush >= 4 ) && ( largePushIndex == 0 ))
						{
						//	takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							/*if (!trade.type.equals(Constants.TRADE_CNT))
								closeReverseTrade( true, triggerPrice, POSITION_SIZE );
							else
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );*/
							//return;
						}
						
	
						if ( pullback  > 3 * getFixedStopSize() * PIP_SIZE){
							//if ( wave2PtL != 0 )
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if (  pullback  > 1.5 * getFixedStopSize() * PIP_SIZE ){
							//if ( negatives > 0 ) && ( wave2PtL != 0 )
							//	takeProfit2( adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
						}
						else
						{
							if ( ( wave2PtL != 0 ) && (trade.reach2FixedStop == false) && ( timePassed >= 24 ))
							{
								MATouch[] maTouches2 = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotes60, ema20_5, tradeEntryPosL, 2);
								MATouch[] maTouches1 = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotes60, ema20_5, tradeEntryPosL, 1);
								// Exit Scenario 2:  disporportional pullback
								double prevProfit = quotes60[phl.prePos].high - trade.entryPrice;
								double avgProfit = prevProfit / ( lastbar60 - tradeEntryPosL );
								if ( avgProfit > 0 )
								{	
									//double avgPullBack = pullback / ( lastbarL - phl.prePos);
									//System.out.println(data.time + " exit detected average profit:" + avgProfit + " pullback avg:" + avgPullBack + " " + avgPullBack/avgProfit);
			
									//if (( pullback > 0.7 * getFixedStopSize() * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
									if (( maTouches2.length >=4 ) || maTouches1.length >= 6 )
									//if ( numOfPush >= 4 )
									{
										System.out.println(data.time + " take profit on disporportional pull back");
//comment out 20150224										takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
									}
								}
								
								
								PositionToMA ptm = Pattern.countAboveMA(quotes60, ema20_5, tradeEntryPosL, lastbar60);
								float numberOfbars = lastbar60-tradeEntryPosL;
								if (ptm.below > ptm.above ) 
								{
									System.out.println(data.time + " take profit on disporportional pull back 2");
//comment out 20150224									takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
	
							
								if ( lastbar60 >= tradeEntryPos + 8 )
								{	
									float numBelow = 0;
									for ( int j = tradeEntryPosL+1; j <=lastbar60; j++)
									{	
										if ( quotes60[j].high < trade.entryPrice )
											numBelow += 1;
									}
								
									if ( numBelow/numberOfbars > 0.6 )
									{
										System.out.println(data.time + " take profit on disporportional pull back 3");
//comment out 20150224										takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
									}
								}
	
							}
						}
					}
					
				}

				
				/******************************************************************************
				// move stop
				 * ****************************************************************************/
				if (trade.reach2FixedStop == true)
				{	
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_DOWN);
					if ( stop > trade.stop )
					{
						//System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						//trade.lastStopAdjustTime = data.time;
						warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
					}
				}
				
			}
		}


		/******************************************************************************
		// smaller timefram for detecting sharp pullbacks
		 * ****************************************************************************/
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
						if ( pullBackDist > 2 * getFixedStopSize() * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotes5[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * getFixedStopSize() * PIP_SIZE)
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
					
					if ((lastCrossDown != Constants.NOT_FOUND )&& (( ema10S[lastCrossDown] - ema10S[lastbar5-1]) > 5 * PIP_SIZE * getFixedStopSize() ))
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
						if ( pullBackDist > 2 * getFixedStopSize() * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotes5[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * getFixedStopSize() * PIP_SIZE)
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
					
					if ((lastCrossUp != Constants.NOT_FOUND )&& ((ema10S[lastbar5-1] - ema10S[lastCrossUp]) > 5 * PIP_SIZE * getFixedStopSize() ))
					{
						warning(data.time + " cross over after large runup detected " + quotes5[lastbar5].time);
						takeProfit( quotes5[lastbar5].close, trade.remainingPositionSize );
					}
				}
			}

		}
	
	}

	
	
	
	
	public void exit123_new9_checkReversalsOrEalyExit( QuoteData data/*, MABreakOutList mol*/ )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes);
		//BigTrend bt = determineBigTrend2( quotes240);
				//BigTrend bt = determineBigTrend_v3( quotesL);
		/*
		if ( mol != null )
		{	
		if (mol.getDirection() == Constants.DIRECTION_DOWN)
			bt.direction =  Constants.DIRT_DOWN;
		else
			bt.direction =  Constants.DIRT_UP;
		}*/	

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			/*
			int pushStart = trade.prevUpStart;
			if ( pushStart > 24 )
			    pushStart = Utility.getHigh( quotesL, pushStart-24, pushStart).pos;

			PushList pushList = SmoothSetup.getDown2PushList(quotesL, pushStart, lastbarL);
			if ( pushList != null )
			{
				//System.out.println(bt.direction);
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
				double lastPullbackSize = quotesL[lastPush.prePos].high - lastPush.pullBack.low;
				double triggerPrice = quotesL[lastPush.prePos].low;

				
				if ( pushSize >= 2 )
				{
				}
				
				if ( pushSize >=3 )
				{
				}
				
				
				if ( pushSize >=4 )
				{
				}
			} */
			
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( tip_reverse && !trade.type.equals(Constants.TRADE_CNT) && ((( trade.entryPrice - lowestPointAfterEntry) < getFixedStopSize() * PIP_SIZE * 0.3 )))     
			{
				if (( data.high > (lowestPointAfterEntry + getFixedStopSize() * PIP_SIZE )) 
					&& ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
				{
					//System.out.println(bt.toString(quotesL));
					//logger.warning(symbol + " " + CHART + " close trade with small tip at " + data.high);
					double reversePrice = lowestPointAfterEntry +  getFixedStopSize() * PIP_SIZE;

					boolean reversequalified = true;
				/*	if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}*/

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * getFixedStopSize())
							reversequalified = false;
					}

					closeReverseTrade( reversequalified, reversePrice, POSITION_SIZE );
					// reverse;
					/*
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);
						cancelTargets();

						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
						trade.detectTime = quotes[lastbar].time;
						trade.positionSize = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}*/
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			/*
			int pushStart = trade.prevDownStart;
			if ( pushStart > 24 )
				pushStart = Utility.getLow( quotesL, pushStart-24, pushStart).pos;
			
			PushList pushList = SmoothSetup.getUp2PushList(quotesL, pushStart, lastbarL);
			if ( pushList != null )
			{	
				//System.out.println(bt.direction);
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));

				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
				double triggerPrice = quotesL[lastPush.prePos].high;

				
				if ( pushSize >= 2 )
				{
				}
				
				if ( pushSize >=3 )
				{
				}
				
				
				if ( pushSize >=4 )
				{
					/*
					if ( pushList.rules_2_n_allSmallBreakOuts( 12 * PIP_SIZE))
					{
						closeReverseTrade( true, triggerPrice, POSITION_SIZE );
						return;
					}*/
			//	}
			/*	
				double avgBarSize = Utility.averageBarSize(quotesL);
				//System.out.println("Average Bar Size:" + avgBarSize);
				
				int bigBreakOutInd = -99;
				if (pushList.phls[0].pushSize > 3 * avgBarSize )
				   bigBreakOutInd = -1;

				for ( int i = 1; i < pushList.phls.length; i++)
				{
					if ( pushList.phls[i].breakOutSize > 2 * avgBarSize )
					{
						if ( bigBreakOutInd <= 0 )
							bigBreakOutInd = i;
						else
						{
							if ( pushList.phls[i].breakOutSize > pushList.phls[bigBreakOutInd].breakOutSize )
								bigBreakOutInd = i;
						}
					}
				}
				
				//System.out.println("break out size " + pushList.phls.length);
				//System.out.println("big break is " + bigBreakOutInd);
				
				int NoOfBreak = pushList.phls.length;
				double initBreakOut = pushList.phls[0].pushSize;
				double break1 = (NoOfBreak >= 1)? pushList.phls[0].breakOutSize:0;
				double break2 = (NoOfBreak >= 2)? pushList.phls[1].breakOutSize:0;
				double break3 = (NoOfBreak >= 3)? pushList.phls[2].breakOutSize:0;
				double break4 = (NoOfBreak >= 4)? pushList.phls[3].breakOutSize:0;
				double break5 = (NoOfBreak >= 5)? pushList.phls[4].breakOutSize:0;
				double break6 = (NoOfBreak >= 6)? pushList.phls[5].breakOutSize:0;
				
				double currentTakeProfitPrice = adjustPrice(quotesL[pushList.phls[NoOfBreak-1].prePos].high,  Constants.ADJUST_TYPE_UP);
						
				if ( NoOfBreak == 1 ) 
				{
					// do nothing
				}
				else if ( NoOfBreak == 2 ) 
				{
					/*
					if ( bigBreakOutInd >= -1 )
					{
						takePartialProfit(currentTakeProfitPrice, TAKE_PROFIT_LOT_SIZE );
					}*/
				}
		/*		else if ( NoOfBreak == 3 ) 
				{
					if ( break2 < 5 * PIP_SIZE )
					{
						//System.out.println("<<<<<<<<<<<<<<<<<");
					}
				}
				else if ( NoOfBreak >= 4 ) 
				{
					
				}
			}
			
			
			*/
			
			
			
			
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (tip_reverse && !trade.type.equals(Constants.TRADE_CNT) && (( highestPointAfterEntry - trade.entryPrice) < getFixedStopSize() * PIP_SIZE *0.3 ))           
			{
				if (( data.low <  (highestPointAfterEntry - getFixedStopSize() * PIP_SIZE ))
					&& ( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					// reverse;
					System.out.println(bt.toString(quotesL));
					//logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  getFixedStopSize() * PIP_SIZE;
					
					boolean reversequalified = true;
					/*if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}*/

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * getFixedStopSize())
							reversequalified = false;
					}

					closeReverseTrade( reversequalified, reversePrice, POSITION_SIZE );

					/*
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);
						cancelTargets();

						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
						trade.detectTime = quotes[lastbar].time;
						trade.positionSize = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}*/
					//return;
				//}
			}
		}
	}


	
	void placeBreakEvenStop(){

		if ( trade.breakEvenStopPlaced == false ){

			cancelOrder(trade.stopId);
			
			if (trade.action.equals(Constants.ACTION_BUY)){
				trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else if (trade.action.equals(Constants.ACTION_SELL)){
				trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			}
			
			trade.breakEvenStopPlaced = true;
		}
	}
	
	
	void placeTrailingStop ( int n ){
		if (trade.action.equals(Constants.ACTION_BUY)){
			double trailingStop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP) + (n-2)* FIXED_STOP*PIP_SIZE;
			if ( trade.stop < trailingStop){
				cancelOrder(trade.stopId);
				trade.stop = trailingStop;
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
		}
		else if (trade.action.equals(Constants.ACTION_SELL)){
			double trailingStop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP) - (n-2)* FIXED_STOP*PIP_SIZE;
			if ( trade.stop > trailingStop){
				cancelOrder(trade.stopId);
				trade.stop = trailingStop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			}
		}
	}
	
	


	protected int takePartialProfit( double price, int size )
	{
		TradePosition tp = new TradePosition(price, size, null);
		
		boolean found = false;
		Iterator<TradePosition> it = trade.takeProfitQueue.iterator();
		while ( it.hasNext())
		{
			TradePosition tpq = it.next();
			if ( tp.equals(tpq))
			{
				found = true;
				break;
			}
		}
		
		it = trade.takeProfitHistory.iterator();
		while ( it.hasNext())
		{
			TradePosition tpq = it.next();
			if ( tp.equals(tpq))
			{
				found = true;
				break;
			}
		}

		
		if (!found)
			trade.takeProfitQueue.add(tp);
		
		if ( trade.takeProfitPartialId == 0 )
		{
			TradePosition tr = trade.takeProfitQueue.poll();
			if ( tr!= null )
			{	
				trade.takeProfitHistory.add(tr);
				
				trade.takeProfitPartialPrice = tr.price;
				trade.takeProfitPartialPosSize = tr.position_size;
	
				String action = Constants.ACTION_BUY;
				if (trade.action.equals(Constants.ACTION_BUY))
					action = Constants.ACTION_SELL;
				
				trade.takeProfitPartialId = placeLmtOrder(action, trade.takeProfitPartialPrice, trade.takeProfitPartialPosSize, null);
				warning("take partial profit " + action + " order placed@ " + trade.takeProfitPartialPrice + " " + trade.takeProfitPartialPosSize );
				return trade.takeProfitPartialId;
			}
		}
		
		return 0;
	}
	


	
	protected void enterPartialPosition( double price, int size )
	{
		TradePosition tp = new TradePosition( price, size, null);
		
		boolean found = false;
		Iterator<TradePosition> it = trade.enterPositionQueue.iterator();
		while ( it.hasNext())
		{
			TradePosition tpq = it.next();
			if ( tp.equals(tpq))
			{
				found = true;
				break;
			}
		}
		
		it = trade.entryPositionHistory.iterator();
		while ( it.hasNext())
		{
			TradePosition tpq = it.next();
			if ( tp.equals(tpq))
			{
				found = true;
				break;
			}
		}

		
		if (!found)
			trade.enterPositionQueue.add(tp);
		
		if ( trade.limitId2 == 0 )
		{
			TradePosition tr = trade.enterPositionQueue.poll();
			if ( tr!= null )
			{	
				trade.entryPositionHistory.add(tr);
				
				trade.limitPrice2 = tr.price;
				trade.limitPos2 = tr.position_size;
	
				trade.limitId2 = placeLmtOrder(trade.action, trade.limitPrice2, trade.limitPos2, null);
				warning("place partial position entry " + trade.action + " order placed@ " + trade.limitPrice2 + " " + trade.limitPos2 );
				trade.limit2Placed = true;
			}
		}
		
	}

	
	
	public void closeReverseTrade( boolean reverse, double reversePrice, int reverse_size )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// reverse;
			AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
			if ( reverse == false )
			{
				closePositionByMarket(trade.remainingPositionSize, reversePrice);
			}
			else
			{	
				cancelOrder(trade.stopId);
				cancelTargets();

				//logger.warning(symbol + " " + CHART + " reverse opportunity detected");
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
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
			if ( reverse == false )
			{
				closePositionByMarket(trade.remainingPositionSize, reversePrice);
			}
			else
			{	
				cancelOrder(trade.stopId);
				cancelTargets();

				//logger.warning(symbol + " " + CHART + " reverse opportunity detected");
				int prevPosionSize = trade.remainingPositionSize;
				removeTrade();
				
				//logger.warning(symbol + " " + CHART + " reverse opportunity detected");
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
				trade.detectTime = quotes[lastbar].time;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = reversePrice;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;

				enterMarketPosition(reversePrice, prevPosionSize);
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


	public Trade checkBreakOut4a(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		//labelPositions( quotesL );
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos){

			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
			//	return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotesL[lastUpPosStart].low > ema20L[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			while ( quotesL[prevDownPos-1].low < quotesL[prevDownPos].low )
				prevDownPos--;

			// looking for upside
			for ( start = prevDownPos+1; start < lastbarL; start++)
				if (( quotesL[start].low > ema20L[start]) && ( quotesL[start+1].low > ema20L[start+1]))
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].low <= ema20L[i])
				{
					touch20MA = i;
					fine("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			/*
			int touch20MA = 0;
			for ( int i = start+1 ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].high < quotesL[i].high ) && ( quotesL[i+2].high < quotesL[i].high ))
				{
					touch20MA = i+1;
					debug("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			*/

			QuoteData firstBreakOut = Utility.getHigh( quotesL, start, touch20MA-1);
			if ( firstBreakOut != null )
			{
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)) && !trade.status.equals(Constants.STATUS_PLACED)){
					removeTrade();
				}

				for ( int i = firstBreakOut.pos+1; i < lastbarL; i++){
					if ( quotesL[i].high > firstBreakOut.high ){
						fine("first breakout high missed at " + quotesL[i].time);
						return null;
					}
				}
				
				
				// run trend analyser
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.detectTime = quotes[lastbar].time;
				trade.prevDownStart = prevDownPos;
//				trade.lastLargeMoveDirection = lastLargeMoveDirection;
//				trade.lastLargeMoveEnd = lastLargeMoveEnd;
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
				trade.POSITION_SIZE = POSITION_SIZE;

				warning("break UP detected at " + data.time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				
				return trade;
			}
		}	
		else if ( lastDownPos > lastUpPos )
		{	
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
			//	return null;

			int lastDownPosStart = lastDownPos;
			while ( quotesL[lastDownPosStart].high < ema20L[lastDownPosStart])
				lastDownPosStart--;
			
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			
			while ( quotesL[prevUpPos-1].high > quotesL[prevUpPos].high )
				prevUpPos--;

			// looking for upside
			for ( start = prevUpPos+1; start < lastbarL; start++)
				if (( quotesL[start].high < ema20L[start]) && ( quotesL[start+1].high < ema20L[start+1]))
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[start].time) != null )
				return null;

			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].high >= ema20L[i])
				{
					touch20MA=i;
					fine("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			/*
			int touch20MA = 0;
			for ( int i = start ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].low > quotesL[i].low ) && ( quotesL[i+2].low > quotesL[i].low ))
				{
					touch20MA = i+1;
					warning("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
*/

			QuoteData firstBreakOut = Utility.getLow( quotesL, start, touch20MA-1);
			if ( firstBreakOut != null )
			{
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)) && !trade.status.equals(Constants.STATUS_PLACED)){
					removeTrade();
				}

				for ( int i = firstBreakOut.pos+1; i < lastbarL; i++){
					if ( quotesL[i].low < firstBreakOut.low ){
						fine("first breakout low missed at " + quotesL[i].time);
						return null;
					}
				}


				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;

				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.detectTime = quotes[lastbar].time;
				trade.prevUpStart = prevUpPos;
			//	trade.lastLargeMoveDirection = lastLargeMoveDirection;
			//	trade.lastLargeMoveEnd = lastLargeMoveEnd;

				trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
				trade.POSITION_SIZE = POSITION_SIZE;
				
				warning("break DOWN detected at " + quotesL[lastbarL].time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				return trade;
			}
		}
		
		return null;
	}
		

	public void trackTradeEntry(QuoteData data, double price )
	{
		if ((MODE == Constants.TEST_MODE) /*&& Constants.STATUS_PLACED.equals(trade.status)*/)
			checkStopTarget(data,0,0);

		if (trade != null)
		{
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				trackPullBackTradeSell( data, price);
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				trackPullBackTradeBuy( data, price );
			}
		}
	}

	
	public void trackTradeTarget(QuoteData data, int field, double currPrice)
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data,0,0);

		if ( trade != null )
		{	
			if ( trade.status.equals(Constants.STATUS_OPEN))
				checkTradeExpiring_ByTime(data);
			
			if (( trade != null ))// && ( trade.status.equals(Constants.STATUS_PLACED) || ( trade.status.equals(Constants.STATUS_FILLED))))
			{
				exit123_close_monitor(data);
				//exit123_new9c7( data );  //   this is the latest
				//exit123_new9c4( data );  // this is default 
				//exit123_new9c4_adjustStopOnly( data);
				//exit123_new9c6( data );  c6 addes to close position quick if it does not move  
				//exit123_close_monitor2( data );
			}		
		}
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
			//logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}


		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Iteger(data.time.substring(9,12).trim()); 

		BigTrend bt = determineBigTrend( quotesL);
		//BigTrend bt = determineBigTrend2( quotes240);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
		/*	if (( trade.getProgramStop() != 0 ) && ((trade.getProgramStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramStop() - data.close)/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.high > trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
				return;
			}*/

			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && ((( trade.entryPrice - lowestPointAfterEntry) < getFixedStopSize() * PIP_SIZE * 0.3 )))     
			{
				if (( data.high > (lowestPointAfterEntry + getFixedStopSize() * PIP_SIZE )) 
					&& ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
				{
					//System.out.println(bt.toString(quotesL));
					//logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = lowestPointAfterEntry +  getFixedStopSize() * PIP_SIZE;

					boolean reversequalified = true;
					if (!reverse_trade)
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * getFixedStopSize())
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

						//logger.warning(symbol + " " + CHART + " reverse opportunity detected");
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
			
			
			// average up
			if ((average_up == true ) && (trade.averageUp == false ) && (( trade.entryPrice - data.low ) > getFixedStopSize() * PIP_SIZE ))
			{
				boolean missed = false;
				for ( int i = tradeEntryPos+1; i < lastbar; i++)
					if ( quotes[i].low < trade.entryPrice - getFixedStopSize() * PIP_SIZE)
						missed = true;
				
				if (!missed )
					enterMarketPositionAdditional(trade.entryPrice - getFixedStopSize() * PIP_SIZE, LOT_SIZE );
				
				trade.averageUp = true;

				
				//trade.remainingPositionSize += POSITION_SIZE;
				//trade.positionSize += POSITION_SIZE;
				//AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, trade.entryPrice - getFixedStopSize() * PIP_SIZE);
				//trade.averageUp = true;
				
			
			}
			
			
			if (lastbarL < tradeEntryPosL + 2)
			return;

			// gathering parameters
			if (trade.reach1FixedStop == false)
			{
				if/* (((( Constants.DIRT_UP.equals(bt.direction) ||  Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
					&& ((trade.entryPrice - quotes[lastbar].low) >  getFixedStopSize() * PIP_SIZE))
					||*/ ((trade.entryPrice - quotes[lastbar].low) > 2 * getFixedStopSize() * PIP_SIZE) 	
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
					//logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + getFixedStopSize());
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
					if ( pullback  > 2 * getFixedStopSize() * PIP_SIZE)
					{
						if ( wave2PtL != 0 )
						{
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}
					else if (  pullback  > 1.5 * getFixedStopSize() * PIP_SIZE )
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
						{
							MATouch[] maTouches2 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 1);

							double prevProfit = trade.entryPrice - quotesL[phl.prePos].low;
							double avgProfit = prevProfit / ( lastbarL - tradeEntryPosL );
							if ( avgProfit > 0 )
							{	
								//double avgPullBack = pullback / ( lastbarL - phl.prePos);
								
								//if (( pullback > 0.7 * getFixedStopSize() * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
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
						if ( pullBackDist > 2 * getFixedStopSize() * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * getFixedStopSize() * PIP_SIZE)
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
					
					if ((lastCrossDown != Constants.NOT_FOUND )&& (( ema10S[lastCrossDown] - ema10S[lastbarS-1]) > 5 * PIP_SIZE * getFixedStopSize() ))
					{
						warning(data.time + " cross over after large rundown detected " + quotesS[lastbarS].time);
						takeProfit( quotesS[lastbarS].close, trade.remainingPositionSize );
					}
				}
			}

			
			// move stop
			if (trade.reach1FixedStop == true)
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
							//System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
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
		/*	if (( trade.getProgramStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramStop() )/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.low < trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
				return;
			}*/

			
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && ((( highestPointAfterEntry - trade.entryPrice) < getFixedStopSize() * PIP_SIZE *0.3 ))            )/*      || 
				(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - trade.entryPrice) < getFixedStopSize() * PIP_SIZE ))*/
			{
				if (( data.low <  (highestPointAfterEntry - getFixedStopSize() * PIP_SIZE ))
					&& ( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					// reverse;
					//System.out.println(bt.toString(quotesL));
					//logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  getFixedStopSize() * PIP_SIZE;
					
					boolean reversequalified = true;
					if (!reverse_trade)
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * getFixedStopSize())
							reversequalified = false;
					}

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						//logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						//logger.warning(symbol + " " + CHART + " reverse opportunity detected");
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

			
			if ((average_up == true ) && (trade.averageUp == false ) && (( data.high - trade.entryPrice ) > getFixedStopSize() * PIP_SIZE ))
			{
				boolean missed = false;
				for ( int i = tradeEntryPos+1; i < lastbar; i++)
					if ( quotes[i].high > trade.entryPrice + getFixedStopSize() * PIP_SIZE)
						missed = true;
				
				if (!missed )
					enterMarketPositionAdditional(trade.entryPrice + getFixedStopSize() * PIP_SIZE, LOT_SIZE );

				trade.averageUp = true;
				//trade.averageUp = true;

				//trade.remainingPositionSize += POSITION_SIZE;
				//trade.positionSize += POSITION_SIZE;
				//AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, POSITION_SIZE, trade.entryPrice + getFixedStopSize() * PIP_SIZE);
			}

			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.reach1FixedStop == false)
			{
				if /*((( Constants.DIRT_DOWN.equals(bt.direction) ||  Constants.DIRT_DOWN_SEC_2.equals(bt.direction))
					&& ((quotes[lastbar].high - trade.entryPrice) >= getFixedStopSize() * PIP_SIZE))
				    ||*/  ((quotes[lastbar].high - trade.entryPrice) >= 2 * getFixedStopSize() * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
					//logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + getFixedStopSize());
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
					if ( pullback  > 2 * getFixedStopSize() * PIP_SIZE)
					{
						if ( wave2PtL != 0 )
						{
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
					}
					else if (  pullback  > 1.5 * getFixedStopSize() * PIP_SIZE )
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
		
								//if (( pullback > 0.7 * getFixedStopSize() * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
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
						if ( pullBackDist > 2 * getFixedStopSize() * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * getFixedStopSize() * PIP_SIZE)
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
					
					if ((lastCrossUp != Constants.NOT_FOUND )&& ((ema10S[lastbarS-1] - ema10S[lastCrossUp]) > 5 * PIP_SIZE * getFixedStopSize() ))
					{
						warning(data.time + " cross over after large runup detected " + quotesS[lastbarS].time);
						takeProfit( quotesS[lastbarS].close, trade.remainingPositionSize );
					}
				}
			}

			
			// move stop
			if (trade.reach1FixedStop == true)
			{	
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_DOWN);
					if ( stop > trade.stop )
					{
						//System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
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
	

	
	BigTrend determineBigTrend( QuoteData[] quotes )
	{
		int trendQualifyPeriod = 48;
		
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		
		BigTrend bigTrend = new BigTrend();
		
		int prevMAConsectiveUp = 0, prevMAConsectiveCrossUp = 0, prevMAConsectiveDown = 0, prevMAConsectiveCrossDown = 0;
		int downDuration = 0, upDuration = 0;
		int start = 0;
		
		int end = lastbar;
		while ( end >= 50 )
		{
			if (( ema20[end] > ema50[end]) && ( ema20[end-1] > ema50[end-1]))
			{
				start = end-2;
				while ( (start >= 50) && !(( ema20[start] < ema50[start]) && ( ema20[start-1] < ema50[start-1])))
				{
					start--;
				}
				
				if ( start < 50 )
				{
					Push p = new Push ( Constants.DIRECTION_UP, 0, end);
					bigTrend.pushes.insertElementAt(p,0);
					bigTrend.start = 0;
					break;
				}
				else
				{
					Push p = new Push ( Constants.DIRECTION_UP, start+1, end);
					bigTrend.pushes.insertElementAt(p,0);
					end = start;

				}
			}
			else if (( ema20[end] < ema50[end]) && ( ema20[end-1] < ema50[end-1]))
			{
				start = end-2;
				while ( (start >= 50) && !(( ema20[start] > ema50[start]) && ( ema20[start-1] > ema50[start-1])))
				{
					start--;
				}
				
				if ( start < 50 )
				{
					Push p = new Push ( Constants.DIRECTION_DOWN, 0, end);
					bigTrend.pushes.insertElementAt(p,0);
					bigTrend.start = 0;
					break;
				}
				else
				{
					Push p = new Push ( Constants.DIRECTION_DOWN, start+1, end);
					bigTrend.pushes.insertElementAt(p,0);
					end = start;
				}
				
			}
			else
				end--;
		}
		
		
		// find the last push that > trendQualifyPeriod
		
		int lastPushIndex = bigTrend.pushes.size() - 1;
		bigTrend.lastPushIndex = lastPushIndex;
		
		Push lastTrendyPush = null;
		Push lastPush = bigTrend.pushes.elementAt(lastPushIndex);
		
		for ( int i = lastPushIndex; i >= 0; i--)
		{
			Push p = bigTrend.pushes.elementAt(i);
			if ( p.duration > trendQualifyPeriod )
			{
				lastTrendyPush = p;
				bigTrend.lastTrendyPushIndex = i;
				break;
			}
		}
		
		
		
		// Determine trend by 20/50 length
		/*
		int trendDirection = 0;
		int trendDuration = 0;
		int lastTrendyIndex48 = 0;  // trend last at least 48 hours
		for ( int i = 1; i <= lastPushIndex; i++)
		{
			if ( bigTrend.pushes.elementAt(i).duration > bigTrend.pushes.elementAt(i-1).duration)
			{
				trendDirection = bigTrend.pushes.elementAt(i).direction;
				trendDuration = bigTrend.pushes.elementAt(i).duration;
				
				if ( trendDuration >= 48 )
					lastTrendyIndex48 = i;
			}
			else
			{
				trendDirection = bigTrend.pushes.elementAt(i-1).direction;
				trendDuration = bigTrend.pushes.elementAt(i-1).duration;

				if ( trendDuration >= 48 )
					lastTrendyIndex48 = i-1;
			}
		}*/

		
		
		
		if (( bigTrend.lastTrendyPushIndex == lastPushIndex ) || ( bigTrend.lastTrendyPushIndex == (lastPushIndex-1)))
				bigTrend.isTrendy = true;
		
		if (( lastTrendyPush != null ) && ( lastTrendyPush.direction == Constants.DIRECTION_UP ))
		{
			bigTrend.direction = Constants.DIRT_UP;
			
			// 1.  check if last two add up made a difference
			/*
			int lastDownPushIndex = bigTrend.getLastPushIndex(Constants.DIRECTION_DOWN);
			if ( lastDownPushIndex >= lastTrendPushIndex + 3 )
			{
				if (( bigTrend.pushes.elementAt( lastDownPushIndex).getPushLow(quotes).low < bigTrend.pushes.elementAt( lastDownPushIndex - 2 ).getPushLow(quotes).low)
					&& ( bigTrend.pushes.elementAt( lastDownPushIndex-1).getPushHigh(quotes).high < bigTrend.pushes.elementAt( lastDownPushIndex - 3 ).getPushHigh(quotes).high))
				{
					bigTrend.direction = Constants.DIRT_DOWN_SEC_2;
					return bigTrend;
				}
			}*/
			
			if ( lastPushIndex >= bigTrend.lastTrendyPushIndex + 3 )
			{	
				int lastTwo = ( bigTrend.pushes.elementAt(lastPushIndex).pushEnd - bigTrend.pushes.elementAt(lastPushIndex).pushStart +1 ) + ( bigTrend.pushes.elementAt(lastPushIndex-2).pushEnd - bigTrend.pushes.elementAt(lastPushIndex-2).pushStart + 1 );
				int lastTwoPre = ( bigTrend.pushes.elementAt(lastPushIndex-1).pushEnd - bigTrend.pushes.elementAt(lastPushIndex-1).pushStart +1 ) + ( bigTrend.pushes.elementAt(lastPushIndex-3).pushEnd - bigTrend.pushes.elementAt(lastPushIndex-3).pushStart +1 );
				if (bigTrend.pushes.elementAt(lastPushIndex).direction == Constants.DIRECTION_DOWN )
				{
					if ((lastTwo> 48 ) || (( lastTwo > 36 )  &&  (lastTwo > lastTwoPre )))
					{
						bigTrend.direction = Constants.DIRT_DOWN_SEC_2;
						return bigTrend;
					}
				}
				else
				{
					if (( lastTwoPre > 48 ) || (( lastTwoPre > 36 ) && ( lastTwoPre > lastTwo )))
					{
						bigTrend.direction = Constants.DIRT_DOWN_SEC_2;
						return bigTrend;
					}
				}
			}
			
			
			// 2.  check to see if there is a large price change
			QuoteData lPoint, hPoint;
			int ind = lastPushIndex;
			if ( lastPush.direction == Constants.DIRECTION_DOWN )
				ind = lastPushIndex - 1;
			
			// Determin the latest highest point
			hPoint = bigTrend.pushes.elementAt(ind).getPushHigh(quotes);
			if (( ind - 2 ) >= 0 )
			{
				Push prevHighPush = bigTrend.pushes.elementAt(ind - 2);
				Push prevLowPush = bigTrend.pushes.elementAt( ind - 1);
				QuoteData prevHighPushHigh = prevHighPush.getPushHigh(quotes);
				if (( prevHighPushHigh.high > hPoint.high) && ( hPoint.pos - prevHighPushHigh.pos < trendQualifyPeriod ))
				{
					hPoint = prevHighPushHigh;
				}
			}
			
			lPoint = Utility.getLow( quotes, hPoint.pos+1, lastbar);
			double totalAvgBarSize = BarUtil.averageBarSize(quotes);
			
			if ( hPoint.pos != lastbar )
			{	
				//if (((hPoint.high - lPoint.low) > 8 * totalAvgBarSize ) && ( lastbar - lPoint.pos < trendQualifyPeriod ))
				if (((hPoint.high - lPoint.low) > 8 * totalAvgBarSize ) && ( lPoint.pos > lastTrendyPush.pushStart ))
				{
					bigTrend.direction = Constants.DIRT_DOWN_REVERSAL;
					//bigTrend.start = ((Push)bigTrend.pushes.elementAt(lastPushDownIndex)).pushStart;
					return bigTrend;
				}
			}
			
			
			// see if we can find a relatively strong spike up
			Push push = Utility.findLargestConsectiveDownBars(quotes, hPoint.pos, lastbar);
			//Push push = Utility.findLargestConsectiveDownBars(quotes, bigTrend.pushes.elementAt(ind).pushStart, lastbar);
			if ( push != null )
			{
				double totalPushSize = quotes[push.pushStart].open - quotes[push.pushEnd].close;
				int numOfBar = push.pushEnd - push.pushStart + 1;
				double avgPushSize = totalPushSize/numOfBar;
				
				if(((( numOfBar == 2 ) && (avgPushSize > 1.2 * totalAvgBarSize)) || (( numOfBar == 3 ) && (avgPushSize > totalAvgBarSize)) ||
			       (( numOfBar >= 4 ) && (avgPushSize > 0.9 * totalAvgBarSize)) || (( numOfBar >= 5 ) && (avgPushSize > 0.8 * totalAvgBarSize))))
				{
					boolean brokenLowerLow = false;
				
					for ( int j = lastbar; (j > bigTrend.pushes.elementAt(ind).pushStart+3) && (brokenLowerLow == false) ; j--)
					{
						PushList pushList = Pattern.findPast2Highs2(quotes, bigTrend.pushes.elementAt(ind).pushStart, j);
						if (( pushList.phls != null ) && ( pushList.phls.length > 0 ))
						{
							for ( int k = 0; (k<pushList.phls.length) && (brokenLowerLow == false); k++)
							{
								PushHighLow p = pushList.phls[pushList.phls.length-1];
								for ( int l = p.curPos+1; l < lastbar; l++)
								{
									if (( quotes[l].low < p.pullBack.low) && ( push.pushStart > p.pullBack.pos))
									{
										brokenLowerLow = true;
										break;
									}	
								}
							}	
						}
					}
						
					for (int k = push.pushEnd+1; k <= lastbar; k++)
					{
						if ( quotes[k].low < quotes[bigTrend.pushes.elementAt(ind).pushStart].low)
						{
							brokenLowerLow = true;
							break;
						}
					}
	
	
					if ( brokenLowerLow == true )
					{
						bigTrend.direction = Constants.DIRT_DOWN_REVERSAL;
						return bigTrend;
					}
				}
			}
		}
		else if (( lastTrendyPush != null ) && ( lastTrendyPush.direction == Constants.DIRECTION_DOWN ))
		{
			bigTrend.direction = Constants.DIRT_DOWN;

			// 1.  check if last two add up made a difference
			/*
			int lastUpPushIndex = bigTrend.getLastPushIndex(Constants.DIRECTION_UP);
			if ( lastUpPushIndex >= lastTrendPushIndex + 3 )
			{
				if (( bigTrend.pushes.elementAt( lastUpPushIndex).getPushHigh(quotes).high > bigTrend.pushes.elementAt( lastUpPushIndex - 2 ).getPushHigh(quotes).high)
					&& ( bigTrend.pushes.elementAt( lastUpPushIndex-1).getPushLow(quotes).low > bigTrend.pushes.elementAt( lastUpPushIndex - 3 ).getPushLow(quotes).low))
				{
					bigTrend.direction = Constants.DIRT_UP_SEC_2;
					return bigTrend;
				}
			}*/
			
			if ( lastPushIndex >= bigTrend.lastTrendyPushIndex + 3 )
			{	
				int lastTwo = ( bigTrend.pushes.elementAt(lastPushIndex).pushEnd - bigTrend.pushes.elementAt(lastPushIndex).pushStart +1 ) + ( bigTrend.pushes.elementAt(lastPushIndex-2).pushEnd - bigTrend.pushes.elementAt(lastPushIndex-2).pushStart + 1 );
				int lastTwoPre = ( bigTrend.pushes.elementAt(lastPushIndex-1).pushEnd - bigTrend.pushes.elementAt(lastPushIndex-1).pushStart +1 ) + ( bigTrend.pushes.elementAt(lastPushIndex-3).pushEnd - bigTrend.pushes.elementAt(lastPushIndex-3).pushStart +1 );
				if (bigTrend.pushes.elementAt(lastPushIndex).direction == Constants.DIRECTION_UP )
				{
					if ((lastTwo> 48 ) || (( lastTwo > 36 )  &&  (lastTwo > lastTwoPre )))
					{
						bigTrend.direction = Constants.DIRT_UP_SEC_2;
						return bigTrend;
					}
				}
				else
				{
					if (( lastTwoPre > 48 ) || (( lastTwoPre > 36 ) && ( lastTwoPre > lastTwo )))
					{
						bigTrend.direction = Constants.DIRT_UP_SEC_2;
						return bigTrend;
					}
				}
			}

			
			
			// 2.  check to see if there is a large price change
			QuoteData lPoint, hPoint;
			int ind = lastPushIndex;
			if ( lastPush.direction == Constants.DIRECTION_UP )
				ind = lastPushIndex - 1;
					
			lPoint = bigTrend.pushes.elementAt(ind).getPushLow(quotes);
			if (( ind - 2 ) >= 0 )
			{
				Push prevLowPush = bigTrend.pushes.elementAt(ind - 2);
				Push prevHighPush = bigTrend.pushes.elementAt( ind - 1);
				QuoteData prevLowPushLow = prevLowPush.getPushLow(quotes);
				if (( prevLowPushLow.low < lPoint.low) && ( lPoint.pos - prevLowPushLow.pos < trendQualifyPeriod ))
				//if (( prevLowPushLow.low < lPoint.low) && ( prevHighPush.pushEnd - prevHighPush.pushStart < trendQualifyPeriod ))
				{
					lPoint = prevLowPushLow;
				}
			}

			hPoint = Utility.getHigh( quotes, lPoint.pos+1, lastbar);
			double totalAvgBarSize = BarUtil.averageBarSize(quotes);

			if ( lPoint.pos != lastbar )
			{	
				//if ((( hPoint.high - lPoint.low) > 8 * totalAvgBarSize ) && ( lastbar - hPoint.pos < trendQualifyPeriod ))
				if ((( hPoint.high - lPoint.low) > 8 * totalAvgBarSize ) && ( hPoint.pos > lastTrendyPush.pushStart ))
				{
					bigTrend.direction = Constants.DIRT_UP_REVERSAL;
					//bigTrend.start = ((Push)bigTrend.pushes.elementAt(lastPushDownIndex)).pushStart;
					return bigTrend;
				}
			}
				
			
			// see if we can find a relatively strong spike up
//			Push push = Utility.findLargestConsectiveUpBars(quotes, bigTrend.pushes.elementAt(ind).pushStart, lastbar);
			Push push = Utility.findLargestConsectiveUpBars(quotes, lPoint.pos, lastbar);
			if ( push != null )
			{
				double totalPushSize = quotes[push.pushEnd].close - quotes[push.pushStart].open;
				int numOfBar = push.pushEnd - push.pushStart + 1;
				double avgPushSize = totalPushSize/numOfBar;
				
				if(((( numOfBar == 2 ) && (avgPushSize > 1.2 * totalAvgBarSize)) || (( numOfBar == 3 ) && (avgPushSize > totalAvgBarSize)) ||
			       (( numOfBar >= 4 ) && (avgPushSize > 0.9 * totalAvgBarSize)) || (( numOfBar >= 5 ) && (avgPushSize > 0.8 * totalAvgBarSize))))
				{
					boolean brokenHigherHigh = false;
				
					for ( int j = lastbar; (j > bigTrend.pushes.elementAt(ind).pushStart+3) && (brokenHigherHigh == false) ; j--)
					{
						PushList pushList = Pattern.findPast2Lows2(quotes, bigTrend.pushes.elementAt(ind).pushStart, j);
						if (( pushList.phls != null ) && ( pushList.phls.length > 0 ))
						{
							PushHighLow p = pushList.phls[pushList.phls.length-1];
							for ( int l = p.curPos+1; l < lastbar; l++)
							{
								if (( quotes[l].high > p.pullBack.high) && ( push.pushStart > p.pullBack.pos))
								{
									brokenHigherHigh = true;
									break;
								}
							}	
							break;
						}
					}
						
					for (int k = push.pushEnd+1; k <= lastbar; k++)
					{
						if ( quotes[k].high > quotes[ bigTrend.pushes.elementAt(ind).pushStart].high)
						{
							brokenHigherHigh = true;
							break;
						}
					}
	
	
					if ( brokenHigherHigh == true )
					{
						bigTrend.direction = Constants.DIRT_UP_REVERSAL;
						return bigTrend;
					}
				}
			}

		}

		
		// calculate support resistence
		/*
		if ( lastPush.direction == Constants.DIRECTION_UP )
		{
			if ( lastPushIndex - 1 >= 0 )
			{
				bigTrend.resistence1 = bigTrend.pushes.elementAt(lastPushIndex).getPushHigh(quotes);
				bigTrend.support1 = bigTrend.pushes.elementAt(lastPushIndex-1).getPushLow(quotes);
			}
			
			if ( lastPushIndex - 3 >= 0 ) 
			{
				if ( bigTrend.pushes.elementAt(lastPushIndex-2).getPushHigh(quotes).high > bigTrend.resistence1.high )
					bigTrend.resistence1 = bigTrend.pushes.elementAt(lastPushIndex-2).getPushHigh(quotes);
			
				if ( bigTrend.pushes.elementAt(lastPushIndex-3).getPushLow(quotes).low < bigTrend.support1.low )
					bigTrend.support1 = bigTrend.pushes.elementAt(lastPushIndex-3).getPushLow(quotes);
			}
		}
		else
		{
			if ( lastPushIndex - 1 >= 0 )
			{
				bigTrend.support1 = bigTrend.pushes.elementAt(lastPushIndex).getPushLow(quotes);
				bigTrend.resistence1 = bigTrend.pushes.elementAt(lastPushIndex-1).getPushHigh(quotes);
			}
			
			if ( lastPushIndex - 3 >= 0 ) 
			{
				if ( bigTrend.pushes.elementAt(lastPushIndex-2).getPushLow(quotes).low < bigTrend.support1.low )
					bigTrend.support1 = bigTrend.pushes.elementAt(lastPushIndex-2).getPushLow(quotes);
			
				if ( bigTrend.pushes.elementAt(lastPushIndex-3).getPushHigh(quotes).high > bigTrend.resistence1.high )
					bigTrend.resistence1 = bigTrend.pushes.elementAt(lastPushIndex-3).getPushHigh(quotes);
			}
			
		}*/

		
		
		return bigTrend;
	}

    boolean inMarkActiveHour( int hour){
    	if ( active_hours_only == false ){
    		return true;
    	}
    	else{
	    	Instrument ins = getInstrument();
	    	if (((ins.getRegion1() == Constants.AMS) || (ins.getRegion2() == Constants.AMS))  && (hour >= 6) && (hour <= 16 ))
	    		return true;
	    	
	    	if (((ins.getRegion1() == Constants.EMEA) || (ins.getRegion2() == Constants.EMEA))  && (hour >= 1) && (hour <= 11 ))
	    		return true;
	
	    	if (((ins.getRegion1() == Constants.APJ) || (ins.getRegion2() == Constants.APJ))  && ((hour >= 18) || ((hour >= 0 ) && (hour <= 4 ))))
	    		return true;
	
	    	return false;
    	}
    }

	
	
	
	
}


