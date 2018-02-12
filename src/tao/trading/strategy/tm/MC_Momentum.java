package tao.trading.strategy.tm;

import java.util.ArrayList;
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
import tao.trading.dao.MABreakOutList;
import tao.trading.dao.MomentumDAO;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.dao.PushTrendDAO;
import tao.trading.dao.ReturnToMADAO;
import tao.trading.entry.PullbackConfluenceEntry;
import tao.trading.setup.BarTrendDetector;
import tao.trading.setup.BarTrendBarDTO;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.MomentumDetector;
import tao.trading.setup.PushSetup;
import tao.trading.setup.SlimJim;
import tao.trading.setup.SlimJimDTO;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;

import com.ib.client.EClientSocket;

public class MC_Momentum extends TradeManager2
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
	int realtime_count = 0;
	int trigger_count = 0;
	boolean average_up = false;
	PullbackConfluenceEntry pullbackConfluenceEntry;
	
	public MC_Momentum()
	{
		super();
	}
	
/*	public MC_240_Momentum(String ib_account, EClientSocket m_client, int symbol_id, Instrument instrument, int mode, int tradeSize, int stopSize, HashMap<String, Double> exchangeRate )
	{
		super(ib_account, m_client, instrument.getContract(), symbol_id);
		this.IB_ACCOUNT = ib_account;
		this.instrument = instrument;
		this.symbol = instrument.getSymbol();
	   	this.POSITION_SIZE = tradeSize;
	   	this.FIXED_STOP = stopSize;
	   	this.logger = Logger.getLogger("MC:"+symbol);
	   	this.PIP_SIZE = instrument.getPIP_SIZE();
	   	this.exchangeRate = exchangeRate.get(instrument.getContract().m_currency);
	   	this.currency = instrument.getContract().m_currency;
	   	super.MODE = mode;
	}*/

	public MC_Momentum(String ib_account, EClientSocket m_client, int symbol_id, Instrument instrument, Strategy stragety, HashMap<String, Double> exchangeRate )
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

	public void setPositionSize(int tradeSize){
	   	this.POSITION_SIZE = tradeSize;
	}

	public void setStopSize(int stopSize){
	   	this.FIXED_STOP = stopSize;
	}


	
	public void setStrategy(Strategy s){
		super.strategy = s;
	}
	
	/*****************************************************************************************************************************
	 * 
	 * 
	 * Static Methods
	 * 
	 * 
	 *****************************************************************************************************************************/
	public void process( int req_id, QuoteData data ){
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

		//System.out.println(req_id);
		//if ( req_id != 514 )   //500
			//return;
		if (req_id == getInstrument().getId_realtime()){
			if (!((trade != null) && ((trade.status.equals(Constants.STATUS_FILLED)||trade.status.equals(Constants.STATUS_PARTIAL_FILLED)))))
			{
				//detect(data);
				//detect_pullback_momentum( data );
				//detect_straight_momentum( data );
				//detect_push_pullback(data);  // original MC
				//detect_trend_pullback2(data);  // this is what worked
				//detect_trend_momentum(data);  // trend change momentum

				
				//detect_bar_trend_change(data);  //<--
				detect_bar_trend_change2(data);
				//detect_bar_breakout(data);   // <--
				//detect_slimjim_breakout(data);
			}

			if (( trade != null) && trade.status.equals(Constants.STATUS_FILLED)){
				manage(data );
			}

			/*if ( trade != null ){  
				trade.lastClosePrice = data.close;

				//String hr = data.time.substring(9,12).trim();
				//String min = data.time.substring(13,15);
				//int hour = new Integer(hr);
				//int minute = new Integer(min);
			
				/*
				if (trade.status.equals(Constants.STATUS_DETECTED))// || trade.status.equals(Constants.STATUS_PARTIAL_FILLED) || trade.status.equals(Constants.STATUS_FILLED))
				{
					entry(data );
				}
				else if (trade.status.equals(Constants.STATUS_FILLED)) 
				{
					manage(data );
				}*/
				
				//return;
			//}
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


    
    
		public Trade detect_bar_trend_change2(QuoteData data)
		{
			//QuoteData[] quotesL = getQuoteData(Constants.CHART_DAILY);
			QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
			//int lastbarL = quotesL.length - 1;
			int lastbar = quotes.length - 1;


			QuoteData[] quotes_1 = new QuoteData[quotes.length - 1];
			System.arraycopy(quotes, 0, quotes_1, 0, quotes.length - 1);
			BarTrendDetector td = new BarTrendDetector(quotes_1, PIP_SIZE);
			BarTrendBarDTO btdd; 
			
			if ( (( btdd = td.detectTrend(quotes[lastbar])) != null )){
				String detectStr = btdd.direction + " detected: begin" + quotes[btdd.begin].time + " end" + quotes[btdd.end].time+ " " + btdd.triggerPrice;

				//System.out.println(detectStr) ;
				//super.strategy.sentAlertEmailTest(AlertOption.trigger, detectStr, trade);
				
				//BarTrendDetectorDTO tbddL = tdL.getTrendDirection2();
				if (( btdd.direction == Constants.DIRECTION_DOWN) /*&& (tbddL != null ) && ( tbddL.direction == Constants.DIRECTION_DOWN )*/){

					if ( findTradeHistory( Constants.ACTION_SELL, btdd.triggerPrice) != null )
						return null;
					
					QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
					int lastbar1 = quotes1.length - 1;
					boolean missed = false;
					if (lastbar1 < 3){ 
						missed = true;
					}else{
						int s = Utility.findPositionByHour(quotes1, quotes[btdd.triggerPos].time, Constants.BACK_TO_FRONT );
						if (s == Constants.NOT_FOUND)
							s = 0;
						for ( int j = s; j < lastbar1; j++){
							if (quotes1[j].low < btdd.triggerPrice){
								missed = true;
								break;
							}
						}
					}

					if (missed ){
						Trade t = new Trade();
						t.action = Constants.ACTION_SELL;
						t.triggerPrice = btdd.triggerPrice;
						tradeHistory.add(t);
						return null;
					}
					else{
						//System.out.println("Trigger count: " + trigger_count++ + " " + btdd.triggerPrice);
						
						
						if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY))){
								warning(data.time + " reverse detected, exit trade");
								//AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
								closePositionByMarket( trade.remainingPositionSize, data.close);
						}
		
						if ( trade == null ){	
							//if ( findTradeHistory( Constants.ACTION_SELL, quotesL[tbddL.begin].time) != null )
							//	return null;
							
							System.out.println("Break uptrend detected at " + data.time + " SELL " + btdd.triggerPrice ) ;
							//*System.out.println("Last Push" + quotesL[lastPush.prePos].time + " " + quotesL[lastPush.prePos].high + quotesL[lastPush.curPos].time + " " + quotesL[lastPush.curPos].high ); 
							
							/*System.out.println("TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT");
							ArrayList<BarTrendDetectorDTO> trendSorters =  td.sortTrendChange();
							for ( BarTrendDetectorDTO t: trendSorters ){
								System.out.println(t.direction + " " + quotes[t.begin].time + " " + quotes[t.end].time);
							}*/
							
							
							createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_SELL);
							trade.status = Constants.STATUS_DETECTED;
							trade.detectTime = quotes[lastbar].time;
							trade.trendBar = btdd.trendBar;
							//trade.setFirstBreakOutStartTime(quotesL[tbddL.begin].time);// for history lookup only
							//trade.pullBackStartTime = quotesL[tbddL.begin].time;
			
							trade.triggerPrice = btdd.triggerPrice;
							enterMarketPosition(trade.triggerPrice);
							super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.triggerPrice, trade);
							return trade;
						}
					}
		
				}
				else if (( btdd.direction == Constants.DIRECTION_UP) /*&& (tbddL != null ) && ( tbddL.direction == Constants.DIRECTION_UP )*/){
					
					if ( findTradeHistory( Constants.ACTION_BUY, btdd.triggerPrice) != null )
						return null;

					QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
					int lastbar1 = quotes1.length - 1;
					boolean missed = false;
					if (lastbar1 < 3){ 
						missed = true;
					}else{
						int s = Utility.findPositionByHour(quotes1, quotes[btdd.triggerPos].time, Constants.BACK_TO_FRONT );
						if (s == Constants.NOT_FOUND)
							s = 0;
						for ( int j = s; j < lastbar1; j++){
							if (quotes1[j].high > btdd.triggerPrice){
								missed = true;
								break;
							}
						}
					}

					
					if (missed ){
						Trade t = new Trade();
						t.action = Constants.ACTION_BUY;
						t.triggerPrice = btdd.triggerPrice;
						tradeHistory.add(t);
						return null;
					}
					else{
						if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL))){
							warning(data.time + " reverse detected, exit trade");
							//AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
							closePositionByMarket( trade.remainingPositionSize, data.close);
						}
			
						if ( trade == null ){	
							//if ( findTradeHistory( Constants.ACTION_BUY, quotesL[tbddL.begin].time) != null )
							//	return null;
							
							System.out.println("Break downtrend detected at " + data.time + " BUY " + btdd.triggerPrice ) ;
							ArrayList<BarTrendBarDTO> ts = td.bars;
							for (BarTrendBarDTO b: ts ){ 
								//System.out.println(b.direction + " begin:" + quotes[b.begin].time + " end:" + quotes[b.end].time);
							}
							BarTrendDetector td1 = new BarTrendDetector(quotes, PIP_SIZE);
							BarTrendBarDTO btdd1 = td.detectTrendChange3();
		
							createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_BUY);
							trade.status = Constants.STATUS_DETECTED;
							trade.detectTime = quotes[lastbar].time;
							trade.trendBar = btdd.trendBar;
							//trade.setFirstBreakOutStartTime(quotesL[tbddL.begin].time);// for history lookup only
							//trade.pullBackStartTime = quotesL[pushStartL].time;
				
							trade.triggerPrice = btdd.triggerPrice;
							enterMarketPosition(trade.triggerPrice);
							super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.triggerPrice, trade);
							return trade;
						}
					}
				}
			}
			return null;
		}
    
    
    
	public Trade detect_bar_trend_change(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_DAILY);
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		//int lastbarL = quotesL.length - 1;
		int lastbar = quotes.length - 1;
		boolean missed = false;

		BarTrendDetector tbddL = new BarTrendDetector(quotesL, PIP_SIZE);
		BarTrendDetector td = new BarTrendDetector(quotes, PIP_SIZE);
		//double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		/*if ( td.trendDetected() == true ){
			BarTrendDetectorDTO lastTrend = td.getLastTrend();
			String detectStr = lastTrend.direction + " detected: begin" + quotes[lastTrend.begin].time + " end" + quotes[lastTrend.end].time+ " " + lastTrend.triggerPrice;
			super.strategy.sentAlertEmailTest(AlertOption.trigger, detectStr, trade);
		}*/
		
		//if ( (( btdd = td.trendDetected2()) != null )){
		//if ( (( btdd = td.detectTrendChange()) != null )){
		//if ( (( btdd = td.detectTrendChange2()) != null )){
		BarTrendBarDTO btdd; 
		if ( (( btdd = td.detectTrendChange3()) != null )){
			String detectStr = btdd.direction + " detected: begin" + quotes[btdd.begin].time + " end" + quotes[btdd.end].time+ " " + btdd.triggerPrice;
			//System.out.println(detectStr) ;
			
			//BarTrendDetectorDTO tbddL = tdL.getTrendDirection2();
			if (( btdd.direction == Constants.DIRECTION_DOWN) && ( tbddL.getLastTrend().direction == Constants.DIRECTION_DOWN )){

				if ( findTradeHistory( Constants.ACTION_SELL, btdd.triggerPrice) != null )
					return null;
				
				QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
				int lastbar5 = quotes5.length - 1;
				if ((lastbar5 < 2) || (quotes5[lastbar5-1].low < btdd.triggerPrice)){ 
					missed = true;
				}

				if (missed ){
					Trade t = new Trade();
					t.action = Constants.ACTION_SELL;
					t.triggerPrice = btdd.triggerPrice;
					tradeHistory.add(t);
					return null;
				}
				else{
					if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY))){
							warning(data.time + " reverse detected, exit trade");
							//AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
							closePositionByMarket( trade.remainingPositionSize, data.close);
					}
	
					if ( trade == null ){	
						//if ( findTradeHistory( Constants.ACTION_SELL, quotesL[tbddL.begin].time) != null )
						//	return null;
						
						System.out.println("Break uptrend detected at " + data.time + " SELL " + btdd.triggerPrice ) ;
						//*System.out.println("Last Push" + quotesL[lastPush.prePos].time + " " + quotesL[lastPush.prePos].high + quotesL[lastPush.curPos].time + " " + quotesL[lastPush.curPos].high ); 
						/*ArrayList<BarTrendDetectorDTO> trendDetectors =  td.trend_detectors;
						ArrayList<BarTrendDetectorDTO> trendSorters =  td.trend_sorters;
						for ( BarTrendDetectorDTO t: trendSorters ){
							System.out.println("dir=" + t.direction + " " + td.original_quotes[trendDetectors.get(t.begin).begin].time + " " + td.original_quotes[trendDetectors.get(t.end).end].time);
						}*/
						
						
						createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_SELL);
						trade.status = Constants.STATUS_DETECTED;
						trade.triggerPrice =  btdd.triggerPrice;
						trade.detectTime = quotes[lastbar].time;
						trade.trendBar = btdd.trendBar;
						//trade.setFirstBreakOutStartTime(quotesL[tbddL.begin].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[tbddL.begin].time;
		
						trade.triggerPrice = btdd.triggerPrice;
						enterMarketPosition(trade.triggerPrice);
						super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.triggerPrice, trade);
						
						if (MODE == Constants.SIGNAL_MODE){
							trade = null;
							return null;
						}
						else{
							return trade;
						}
					}
				}
	
			}
			else if (( btdd.direction == Constants.DIRECTION_UP) && ( tbddL.getLastTrend().direction == Constants.DIRECTION_UP )){
				
				if ( findTradeHistory( Constants.ACTION_BUY, btdd.triggerPrice) != null )
					return null;

				QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
				int lastbar5 = quotes5.length - 1;
				if ((lastbar5 < 2) || (quotes5[lastbar5-1].high > btdd.triggerPrice)){ 
					missed = true;
				}

				
				if (missed ){
					Trade t = new Trade();
					t.action = Constants.ACTION_BUY;
					t.triggerPrice = btdd.triggerPrice;
					tradeHistory.add(t);
					return null;
				}
				else{
					if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL))){
						warning(data.time + " reverse detected, exit trade");
						//AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);
					}
		
					if ( trade == null ){	
						//if ( findTradeHistory( Constants.ACTION_BUY, quotesL[tbddL.begin].time) != null )
						//	return null;
						
						/*System.out.println("Break downtrend detected at " + data.time + " BUY " + btdd.triggerPrice ) ;
						ArrayList<BarTrendDetectorDTO> trendDetectors =  td.trend_detectors;
						ArrayList<BarTrendDetectorDTO> trendSorters =  td.trend_sorters;
						for ( BarTrendDetectorDTO t: trendSorters ){
							System.out.println("dir=" + t.direction + " " + td.original_quotes[trendDetectors.get(t.begin).begin].time + " " + td.original_quotes[trendDetectors.get(t.end).end].time);
						}*/

						
						
						
						
						createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_BUY);
						trade.status = Constants.STATUS_DETECTED;
						trade.triggerPrice =  btdd.triggerPrice;
						trade.detectTime = quotes[lastbar].time;
						trade.trendBar = btdd.trendBar;
						//trade.setFirstBreakOutStartTime(quotesL[tbddL.begin].time);// for history lookup only
						//trade.pullBackStartTime = quotesL[pushStartL].time;
			
						trade.triggerPrice = btdd.triggerPrice;
						enterMarketPosition(trade.triggerPrice);
						super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.triggerPrice, trade);

						if (MODE == Constants.SIGNAL_MODE){
							trade = null;
							return null;
						}
						else{
							return trade;
						}
					}
				}
			}
		}
		return null;
	}

    
    
	public Trade detect_bar_breakout(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_DAILY);
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;

		BarTrendDetector tbddL = new BarTrendDetector(quotesL, PIP_SIZE);
		BarTrendDetector td = new BarTrendDetector(quotes, PIP_SIZE);
		//td.printTrend(quotes);
		
		
		BarTrendBarDTO btdd; 
		if ( (( btdd = td.detectBreakOut2()) != null )){
			//String detectStr = btdd.direction + " detected: triggerBarSize" + quotes[btdd.begin].time + " end" + quotes[btdd.end].time+ " " + btdd.triggerPrice;
			//System.out.println(detectStr) ;
			//super.strategy.sentAlertEmailTest(AlertOption.trigger, detectStr, trade);
			
			if (( btdd.direction == Constants.DIRECTION_DOWN) /*&& ( tbddL.getLastTrend().direction == Constants.DIRECTION_DOWN )*/){

				if ( findTradeHistory( Constants.ACTION_SELL, btdd.triggerPrice) != null )
					return null;
				if ( findTradeHistory( btdd.begin, btdd.end) != null )
					return null;
				//if (lastbar - findLatestTradeFromTradeHistory( Constants.ACTION_SELL) <= 24)
				//	return null;
					
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY))){
					System.out.println(data.time + " reverse detected, exit trade");
						//AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);
				}

				if (Math.abs(data.close - btdd.triggerPrice) > (10 * PIP_SIZE)){
					Trade t = new Trade();
					t.action = Constants.ACTION_SELL;
					t.triggerPrice = btdd.triggerPrice;
					tradeHistory.add(t);
					return null;
					//btdd.triggerPrice = data.close;
				}
				
				if ( trade == null ){	
					//if ( findTradeHistory( Constants.ACTION_SELL, quotesL[tbddL.begin].time) != null )
					//	return null;
					
					System.out.println("Break Down detected at " + data.time + " SELL " + btdd.triggerPrice + " triggerBarSize:" + (int)(btdd.triggerBarSize/PIP_SIZE) + " avg4BarSize:" + (int)(btdd.avg4BarSize/PIP_SIZE)  ) ;
					
					createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.detectPosStart = btdd.begin;
					trade.detectPosEnd = btdd.end;
					//trade.trendBar = btdd.trendBar;
					//trade.setFirstBreakOutStartTime(quotesL[tbddL.begin].time);// for history lookup only
					//trade.pullBackStartTime = quotesL[tbddL.begin].time;
	
					trade.triggerPrice = btdd.triggerPrice;
					trade.triggerPos = lastbar;
					enterMarketPosition(trade.triggerPrice);
					//super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + triggerPrice, trade);
					return trade;
				}
			}
			else if (( btdd.direction == Constants.DIRECTION_UP) /*&& ( tbddL.getLastTrend().direction == Constants.DIRECTION_UP )*/){
				
				if ( findTradeHistory( Constants.ACTION_BUY, btdd.triggerPrice) != null )
					return null;
				if ( findTradeHistory( btdd.begin, btdd.end) != null )
					return null;
				//if (lastbar - findLatestTradeFromTradeHistory( Constants.ACTION_BUY) <= 24)
				//	return null;

				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL))){
					System.out.println(data.time + " reverse detected, exit trade");
					//AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
					closePositionByMarket( trade.remainingPositionSize, data.close);
				}
	
				if (Math.abs(data.close - btdd.triggerPrice) > (10 * PIP_SIZE)){
					Trade t = new Trade();
					t.action = Constants.ACTION_BUY;
					t.triggerPrice = btdd.triggerPrice;
					tradeHistory.add(t);
					return null;
					//btdd.triggerPrice = data.close;
				}
				
				if ( trade == null ){	
					//if ( findTradeHistory( Constants.ACTION_BUY, quotesL[tbddL.begin].time) != null )
					//	return null;
					
					System.out.println("Break Up detected at " + data.time + " BUY " + btdd.triggerPrice + " triggerBarSize:" + (int)(btdd.triggerBarSize/PIP_SIZE) + " avg4BarSize:" + (int)(btdd.avg4BarSize/PIP_SIZE)  ) ;
					ArrayList<BarTrendBarDTO> ts = td.bars;
					for (BarTrendBarDTO b: ts ){ 
						//System.out.println(b.direction + " begin:" + quotes[b.begin].time + " end:" + quotes[b.end].time);
					}
					BarTrendDetector td1 = new BarTrendDetector(quotes, PIP_SIZE);
					BarTrendBarDTO btdd1 = td.detectTrendChange3();

					createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.detectPosStart = btdd.begin;
					trade.detectPosEnd = btdd.end;
					//trade.trendBar = btdd.trendBar;
					//trade.setFirstBreakOutStartTime(quotesL[tbddL.begin].time);// for history lookup only
					//trade.pullBackStartTime = quotesL[pushStartL].time;
		
					trade.triggerPrice = btdd.triggerPrice;
					trade.triggerPos = lastbar;
					enterMarketPosition(trade.triggerPrice);
					//super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + triggerPrice, trade);
					return trade;
				}
			}
		}
		return null;
	}


	public Trade detect_slimjim_breakout(QuoteData data)
	{
		//QuoteData[] quotesL = getQuoteData(Constants.CHART_DAILY);
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;

		//BarTrendDetector tbddL = new BarTrendDetector(quotesL, PIP_SIZE);
		BarTrendDetector td = new BarTrendDetector(quotes, PIP_SIZE);
		//td.printTrend(quotes);
		
		int slimJimRange = 36;
		SlimJimDTO slimjim = null;
		double triggerPrice = 0;
		double entryPrice = 0;
		double avgBarSize = BarUtil.averageBarSize(quotes);

		slimjim = SlimJim.findSlimJim(quotes, lastbar-1-slimJimRange, lastbar-1);
		if (( slimjim != null ) && ( slimjim.range < 3.5 * avgBarSize )){
			//System.out.println(data.time + " slim jim detected");
			
			if ( data.close < (quotes[slimjim.lowestPoint].low /*- 0.75 * avgBarSize*/ )){
				triggerPrice = quotes[slimjim.lowestPoint].low;
				entryPrice = triggerPrice; /*- 0.75 * avgBarSize*/;		
				
				if ( findTradeHistory( Constants.ACTION_SELL, triggerPrice) != null )
					return null;
				
				//if ( findTradeHistory( btdd.begin, btdd.end) != null )
				//	return null;

				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY))){
					System.out.println(data.time + " reverse detected, exit trade");
					//AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
					closePositionByMarket( trade.remainingPositionSize, data.close);
				}

				if ( trade == null ){	
					System.out.println("Break Down detected at " + data.time + " SELL " + entryPrice ) ;
					
					createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					//trade.detectPosStart = btdd.begin;
					//trade.detectPosEnd = btdd.end;
					//trade.trendBar = btdd.trendBar;
					//trade.setFirstBreakOutStartTime(quotesL[tbddL.begin].time);// for history lookup only
					//trade.pullBackStartTime = quotesL[tbddL.begin].time;
	
					trade.triggerPrice = triggerPrice;
					trade.triggerPos = lastbar;
					
					QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
					int lastbar5 = quotes5.length - 1;
					if ( quotes5[lastbar5-1].low < entryPrice ){
						fine("first breakout low missed at " + quotes5[lastbar5-1].time);
						removeTrade();
						return null;
					}

					enterMarketPosition(entryPrice);
					//super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + triggerPrice, trade);
					return trade;
				}
			}
		}
		return null;
	}

	
	
	
	
	public Trade detect_bar_breakout2(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_DAILY);
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;

		BarTrendDetector tbddL = new BarTrendDetector(quotesL, PIP_SIZE);
		BarTrendDetector td = new BarTrendDetector(quotes, PIP_SIZE);
		//td.printTrend(quotes);
		
		
		BarTrendBarDTO btdd; 
		if ( (( btdd = td.detectBreakOut2()) != null )){
			//String detectStr = btdd.direction + " detected: triggerBarSize" + quotes[btdd.begin].time + " end" + quotes[btdd.end].time+ " " + btdd.triggerPrice;
			//System.out.println(detectStr) ;
			//super.strategy.sentAlertEmailTest(AlertOption.trigger, detectStr, trade);
			
			if (( btdd.direction == Constants.DIRECTION_DOWN) /*&& ( tbddL.getLastTrend().direction == Constants.DIRECTION_DOWN )*/){

				if ( findTradeHistory( Constants.ACTION_SELL, btdd.triggerPrice) != null )
					return null;
				if ( findTradeHistory( btdd.begin, btdd.end) != null )
					return null;
				//if (lastbar - findLatestTradeFromTradeHistory( Constants.ACTION_SELL) <= 24)
				//	return null;
					
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY))){
					System.out.println(data.time + " reverse detected, exit trade");
						//AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);
				}

				if (Math.abs(data.close - btdd.triggerPrice) > (10 * PIP_SIZE)){
					Trade t = new Trade();
					t.action = Constants.ACTION_SELL;
					t.triggerPrice = btdd.triggerPrice;
					tradeHistory.add(t);
					return null;
					//btdd.triggerPrice = data.close;
				}
				
				if ( trade == null ){	
					//if ( findTradeHistory( Constants.ACTION_SELL, quotesL[tbddL.begin].time) != null )
					//	return null;
					
					System.out.println("Break Down detected at " + data.time + " SELL " + btdd.triggerPrice + " triggerBarSize:" + (int)(btdd.triggerBarSize/PIP_SIZE) + " avg4BarSize:" + (int)(btdd.avg4BarSize/PIP_SIZE)  ) ;
					
					createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.detectPosStart = btdd.begin;
					trade.detectPosEnd = btdd.end;
					//trade.trendBar = btdd.trendBar;
					//trade.setFirstBreakOutStartTime(quotesL[tbddL.begin].time);// for history lookup only
					//trade.pullBackStartTime = quotesL[tbddL.begin].time;
	
					trade.triggerPrice = btdd.triggerPrice;
					trade.triggerPos = lastbar;
					enterMarketPosition(trade.triggerPrice);
					//super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + triggerPrice, trade);
					return trade;
				}
			}
			else if (( btdd.direction == Constants.DIRECTION_UP) /*&& ( tbddL.getLastTrend().direction == Constants.DIRECTION_UP )*/){
				
				if ( findTradeHistory( Constants.ACTION_BUY, btdd.triggerPrice) != null )
					return null;
				if ( findTradeHistory( btdd.begin, btdd.end) != null )
					return null;
				//if (lastbar - findLatestTradeFromTradeHistory( Constants.ACTION_BUY) <= 24)
				//	return null;

				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL))){
					System.out.println(data.time + " reverse detected, exit trade");
					//AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
					closePositionByMarket( trade.remainingPositionSize, data.close);
				}
	
				if (Math.abs(data.close - btdd.triggerPrice) > (10 * PIP_SIZE)){
					Trade t = new Trade();
					t.action = Constants.ACTION_BUY;
					t.triggerPrice = btdd.triggerPrice;
					tradeHistory.add(t);
					return null;
					//btdd.triggerPrice = data.close;
				}
				
				if ( trade == null ){	
					//if ( findTradeHistory( Constants.ACTION_BUY, quotesL[tbddL.begin].time) != null )
					//	return null;
					
					System.out.println("Break Up detected at " + data.time + " BUY " + btdd.triggerPrice + " triggerBarSize:" + (int)(btdd.triggerBarSize/PIP_SIZE) + " avg4BarSize:" + (int)(btdd.avg4BarSize/PIP_SIZE)  ) ;
					ArrayList<BarTrendBarDTO> ts = td.bars;
					for (BarTrendBarDTO b: ts ){ 
						//System.out.println(b.direction + " begin:" + quotes[b.begin].time + " end:" + quotes[b.end].time);
					}
					BarTrendDetector td1 = new BarTrendDetector(quotes, PIP_SIZE);
					BarTrendBarDTO btdd1 = td.detectTrendChange3();

					createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.detectPosStart = btdd.begin;
					trade.detectPosEnd = btdd.end;
					//trade.trendBar = btdd.trendBar;
					//trade.setFirstBreakOutStartTime(quotesL[tbddL.begin].time);// for history lookup only
					//trade.pullBackStartTime = quotesL[pushStartL].time;
		
					trade.triggerPrice = btdd.triggerPrice;
					trade.triggerPos = lastbar;
					enterMarketPosition(trade.triggerPrice);
					//super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + triggerPrice, trade);
					return trade;
				}
			}
		}
		return null;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
    
    
    
	public Trade detect_push_pullback_starting_close_on_otherside( QuoteData data )
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int start, end;
		if (BarUtil.isUpBar(quotesL[lastbarL-2]) && (quotesL[lastbarL-2].close > ema20L[lastbarL-2]) && BarUtil.isDownBar(quotesL[lastbarL-1]))
		{
			// check if this is first or second occurence
			int barCloseBelow = lastbarL- 3;
			while (!(quotesL[barCloseBelow].close < ema20L[barCloseBelow]))
				barCloseBelow --;
			
			int pastOccurences = 0;
			int pushStart = barCloseBelow;
			while ( quotesL[pushStart-1].low < quotesL[pushStart].low)
				pushStart--;
			//while ( )
			
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			start = end = lastbarL-2;
			while (BarUtil.isUpBar(quotesL[start-1]) || (quotesL[start-1].low < quotesL[start].low))
				start--;

			double aveSize = BarUtil.averageBarSize(quotesL);
			boolean bigBar = false;
			for ( int i = start; i <= end; i++)
				if ( BarUtil.barSize(quotesL[i]) > aveSize )
					bigBar = true;

			if ( !bigBar )
				return null;

			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			fine("pullback BUY detected at " + data.time + " start:" + quotesL[start].time + " " + quotesL[start].low + " end:" + quotesL[end].time + " " + quotesL[end].high);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotesL[lastbarL].time;
			trade.setFirstBreakOutStartTime(quotesL[start].time);
			trade.setFirstBreakOutTime(quotesL[end].time);
			
			return trade;
		}	
		else if (BarUtil.isDownBar(quotesL[lastbarL-2]) && (quotesL[lastbarL-2].close < ema20L[lastbarL-2]) && BarUtil.isUpBar(quotesL[lastbarL-1]))
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			start = end = lastbarL-2;
			while (BarUtil.isDownBar(quotesL[start-1]) || (quotesL[start-1].high > quotesL[start].high))
				start--;
			
			double aveSize = BarUtil.averageBarSize(quotesL);
			boolean bigBar = false;
			for ( int i = start; i <= end; i++)
				if ( BarUtil.barSize(quotesL[i]) > aveSize )
					bigBar = true;

			if ( !bigBar )
				return null;
			
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[start].time) != null )
				return null;
			
			fine("pullback SELL detected at " + data.time + " start:" + quotesL[start].time + " " + quotesL[start].high + " end:" + quotesL[end].time + " " + quotesL[end].low);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotesL[lastbarL].time;
			trade.setFirstBreakOutStartTime(quotesL[start].time);
			trade.setFirstBreakOutTime(quotesL[end].time);
			
			return trade;
		}	
		
		return null;
	}

    
    
    
    
	public Trade detect_trend_pullback(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;

		PushTrendDAO pushTrend = PushSetup.getPushTrend( quotesL, getPIP_SIZE() );
		
		//if ( pushTrend != null )
			//System.out.println( data.time + " " + getSymbol() + " " + pushTrend.toString(quotesL, getPIP_SIZE()));
		
		if (( pushTrend != null) && ( pushTrend.getDirection() == Constants.DIRECTION_UP )){

			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			PushList upPush = pushTrend.getCurrentTrend();
			int endPos = upPush.end;
			/*
			int highestPoint = Utility.getHigh(quotesL, endPos, lastbarL).pos;
			
			if ( highestPoint == lastbarL - 2 ){
				if ( findTradeHistory( Constants.ACTION_BUY, quotesL[highestPoint].time) != null )
					return null;

				debug( data.time + " " + getSymbol() + " " + pushTrend.toString(quotesL, getPIP_SIZE()));
				info("pullback BUY detected at " + data.time + " last trend peak was " + quotesL[highestPoint].time) ;
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotesL[lastbarL].time;
				trade.setFirstBreakOutStartTime(quotesL[highestPoint].time);
				trade.setFirstBreakOutTime(quotesL[highestPoint].time);
				trade.setPullBackStartTime(quotesL[highestPoint].time);
				trade.phl = upPush.getPushHighLow(upPush.getNumOfPushes()-1);
				return trade;
			}*/
			
			
			if ( BarUtil.isDownBar(quotesL[lastbarL-1])){
				
				for ( int i = endPos+1; i< lastbarL-1; i++)
					if (BarUtil.isDownBar(quotesL[i]))
						return null;

			/*int firstPullBack = endPos+1;
			while ( firstPullBack < lastbarL) {
				if ( quotesL[firstPullBack].high < quotesL[firstPullBack-1].high) 
					break;
				else
					firstPullBack++;
			}
			
			if ( firstPullBack != lastbarL-1 )
				return null;

			{*/
				QuoteData highestPoint = Utility.getHigh(quotesL, endPos, lastbarL-1);
				if ( highestPoint == null )
					highestPoint = quotesL[lastbarL-1];
				// verify if this is a breakout
				/*for ( int j = highestPoint.pos-1; j >= (((highestPoint.pos-31)>=0)?(highestPoint.pos-31):0); j-- )
					if ( quotesL[j].high > highestPoint.high)
						return null;
				*/
				PushHighLow lastPush = upPush.getPushHighLow(upPush.getNumOfPushes()-1);
				double lastBreakOutSize = (highestPoint.high - quotesL[lastPush.prePos].high)/getPIP_SIZE();
				//System.out.println( "last breakout:" + (highestPoint.high - quotesL[trade.phl.prePos].high)/getPIP_SIZE());
				//if (lastBreakOutSize < FIXED_STOP/3 )
					//return null;
				if ( upPush.getNumOfPushes() >= 2 ){
					PushHighLow prevLastPush = upPush.getPushHighLow(upPush.getNumOfPushes()-2);
					if (lastPush.pullBack.low < prevLastPush.pullBack.low )
						return null;
				}
				
				if ( findTradeHistory( Constants.ACTION_BUY, highestPoint.time) != null )
					return null;

				//BigTrend bt = TrendAnalysis.checkBigTrend( getQuoteData(Constants.CHART_240_MIN));
				//if (!( bt.getTrend() == Constants.DIRECTION_UP))
				//	return null;

				//System.out.println( data.time + " " + getSymbol() + " " + pushTrend.toString(quotesL, getPIP_SIZE()));
				//System.out.println( "Setup PushBegin:" + lastPush.pullBack.time + lastPush.pullBack.low);
				info("pullback BUY detected at " + data.time + " last trend peak was " + highestPoint.time) ;
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(highestPoint.time);
				trade.setFirstBreakOutTime(highestPoint.time);
				trade.setPullBackStartTime(highestPoint.time);
				trade.phl = lastPush;
				
				if ( realtime_count == 1) {  // triggers on the first bar, not accurate
					removeTrade();
					return null;
				}

				return trade;
			}
		}
		else if (( pushTrend != null) && ( pushTrend.getDirection() == Constants.DIRECTION_DOWN )){
			
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			PushList downPush = pushTrend.getCurrentTrend();
			int endPos = downPush.end;
			
			/*
			int lowestPoint = Utility.getLow(quotesL, endPos, lastbarL).pos;
			
			if ( lowestPoint == lastbarL - 2 ){
				if ( findTradeHistory( Constants.ACTION_SELL, quotesL[lowestPoint].time) != null )
					return null;

				//warning("pullback BUY detected at " + data.time + " start:" + quotesL[start].time + " " + quotesL[start].low + " end:" + quotesL[end].time + " " + quotesL[end].high);
				debug( data.time + " " + getSymbol() + " " + pushTrend.toString(quotesL, getPIP_SIZE()));
				info("pullback SELL detected at " + data.time + " last trend peak was " + quotesL[lowestPoint].time) ;
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotesL[lastbarL].time;
				trade.setFirstBreakOutStartTime(quotesL[lowestPoint].time);
				trade.setFirstBreakOutTime(quotesL[lowestPoint].time);
				trade.setPullBackStartTime(quotesL[lowestPoint].time);
				trade.phl = downPush.getPushHighLow(downPush.getNumOfPushes()-1);
				return trade;
			}*/
			
			
			if ( BarUtil.isUpBar(quotesL[lastbarL-1])){
				for ( int i = endPos+1; i< lastbarL-1; i++)
					if (BarUtil.isUpBar(quotesL[i]))
						return null;
				
			/*
			int firstPullBack = endPos+1;
			while ( firstPullBack < lastbarL) {
				if ( quotesL[firstPullBack].low > quotesL[firstPullBack-1].low) 
					break;
				else
					firstPullBack++;
			}
			
			if ( firstPullBack != lastbarL-1 )
				return null;

			{*/
				QuoteData lowestPoint = Utility.getLow(quotesL, endPos, lastbarL-1);
				if ( lowestPoint == null )
					lowestPoint = quotesL[lastbarL-1];
				// verify if this is a breakout
				/*for ( int j = lowestPoint.pos-1; j>=(((lowestPoint.pos-31)>=0)?(lowestPoint.pos-31):0); j-- )
					if ( quotesL[j].low < lowestPoint.low)
						return null;
				*/
				if ( findTradeHistory( Constants.ACTION_SELL, lowestPoint.time) != null )
					return null;

				PushHighLow lastPush = downPush.getPushHighLow(downPush.getNumOfPushes()-1);
				double lastBreakOutSize = (quotesL[lastPush.prePos].low - lowestPoint.low)/getPIP_SIZE();
				//if (lastBreakOutSize < FIXED_STOP/3 )
					//return null;
				if ( downPush.getNumOfPushes() >= 2 ){
					PushHighLow prevLastPush = downPush.getPushHighLow(downPush.getNumOfPushes()-2);
					if (lastPush.pullBack.high > prevLastPush.pullBack.high )
						return null;
				}
				
				if ( findTradeHistory( Constants.ACTION_SELL, lowestPoint.time) != null )
					return null;

				//BigTrend bt = TrendAnalysis.checkBigTrend( getQuoteData(Constants.CHART_240_MIN));
				//if (!( bt.getTrend() == Constants.DIRECTION_DOWN))
				//	return null;

				
				//warning("pullback BUY detected at " + data.time + " start:" + quotesL[start].time + " " + quotesL[start].low + " end:" + quotesL[end].time + " " + quotesL[end].high);
				System.out.println( data.time + " " + getSymbol() + " " + pushTrend.toString(quotesL, getPIP_SIZE()));
				System.out.println( "Setup PushBegin:" + lastPush.pullBack.time + lastPush.pullBack.high);
				info("pullback SELL detected at " + data.time + " last trend peak was " + lowestPoint.time) ;
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(lowestPoint.time);
				trade.setFirstBreakOutTime(lowestPoint.time);
				trade.setPullBackStartTime(lowestPoint.time);
				trade.phl = lastPush;

				if ( realtime_count == 1) {  // triggers on the first bar, not accurate
					removeTrade();
					return null;
				}
				return trade;
			}
		}

		
		return null;
		
	}

	
	
	public Trade detect_trend_pullback2(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;

		PushTrendDAO pushTrend = PushSetup.getPushTrend2( quotesL, getPIP_SIZE() );
		
		//if ( pushTrend != null )
			//System.out.println( data.time + " " + getSymbol() + " " + pushTrend.toString(quotesL, getPIP_SIZE()));
		
		if (( pushTrend != null) && ( pushTrend.getDirection() == Constants.DIRECTION_UP )){

			//debug("MC check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			PushList upPush = pushTrend.getCurrentTrend();
			int endPos = upPush.end;
			
			if ( BarUtil.isDownBar(quotesL[lastbarL-1]) || 
					((lastbarL-1) >= upPush.end) && (quotesL[lastbarL-1].high < quotesL[lastbarL-2].high)){

	//		if (/*((lastbarL-1) >= upPush.end) &&*/ ( BarUtil.isDownBar(quotesL[lastbarL-1]) || (quotesL[lastbarL-1].high < quotesL[lastbarL].high))){
	//			if ( BarUtil.isDownBar(quotesL[lastbarL-1])){
	//				for ( int i = endPos+1; i< lastbarL-1; i++)  
	//				if (BarUtil.isDownBar(quotesL[i]))
	//					return null;
		//		}

			/*int firstPullBack = endPos+1;
			while ( firstPullBack < lastbarL) {
				if ( quotesL[firstPullBack].high < quotesL[firstPullBack-1].high) 
					break;
				else
					firstPullBack++;
			}
			
			if ( firstPullBack != lastbarL-1 )
				return null;

			{*/
				QuoteData highestPoint = Utility.getHigh(quotesL, endPos, lastbarL-1);
				if ( highestPoint == null )
					highestPoint = quotesL[lastbarL-1];
				// verify if this is a breakout
				/*for ( int j = highestPoint.pos-1; j >= (((highestPoint.pos-31)>=0)?(highestPoint.pos-31):0); j-- )
					if ( quotesL[j].high > highestPoint.high)
						return null;
				*/
				PushHighLow lastPush = upPush.getPushHighLow(upPush.getNumOfPushes()-1);
				//double lastBreakOutSize = (highestPoint.high - quotesL[lastPush.prePos].high)/getPIP_SIZE();
				//System.out.println( "last breakout:" + (highestPoint.high - quotesL[trade.phl.prePos].high)/getPIP_SIZE());
				//if (lastBreakOutSize < FIXED_STOP/3 )
					//return null;
				if ( upPush.getNumOfPushes() >= 2 ){
					PushHighLow prevLastPush = upPush.getPushHighLow(upPush.getNumOfPushes()-2);
					if (lastPush.pullBack.low < prevLastPush.pullBack.low )
						return null;
				}
				
				if ( findTradeHistory( Constants.ACTION_BUY, highestPoint.time) != null )
					return null;

				//System.out.println( data.time + " " + getSymbol() + " " + pushTrend.toString(quotesL, getPIP_SIZE()));
				//System.out.println( "Setup PushBegin:" + lastPush.pullBack.time + lastPush.pullBack.low);
				System.out.println("pullback BUY detected at " + data.time + " last trend peak was " + highestPoint.time + " " + highestPoint.high + " pushEnd:" + quotesL[endPos].time + " " + endPos + " " + (lastbarL-1) + " " + lastPush.curPos) ;
				info("triggering L bar is " + quotesL[lastbarL-1].time);
				System.out.println("Last Push" + quotesL[lastPush.prePos].time + " " + quotesL[lastPush.prePos].high + quotesL[lastPush.curPos].time + " " + quotesL[lastPush.curPos].high ); 
				
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(highestPoint.time);
				trade.setFirstBreakOutTime(highestPoint.time);
				trade.setPullBackStartTime(highestPoint.time);
				//trade.phl = lastPush;
				
				if ( realtime_count == 1) {  // triggers on the first bar, not accurate
					removeTrade();
					return null;
				}

				/*
				// try to remove the first pullback breakout
				if (( pushTrend.getCurrentTrend().getNumOfPushes() == 1 ) && (( pushTrend.getLastTrend() != null ) && (pushTrend.getLastTrend().getNumOfPushes() >= 3))){
					PushHighLow lastTrendPush = pushTrend.getCurrentTrend().phls[0];
					if ((float)(Math.round(10*lastTrendPush.breakOutSize /getPIP_SIZE())/10.0f) < 5.0 ){
						info("trend is too small, removed");
						removeTrade();
						return null;
					}
				}*/

				return trade;
			}

		}
		else if (( pushTrend != null) && ( pushTrend.getDirection() == Constants.DIRECTION_DOWN )){
			
			//debug("MC check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			PushList downPush = pushTrend.getCurrentTrend();
			int endPos = downPush.end;
			
			if ( BarUtil.isUpBar(quotesL[lastbarL-1]) || 
					((lastbarL-1) >= downPush.end) && (quotesL[lastbarL-1].low > quotesL[lastbarL-2].low)){

			
			
			//if (/*((lastbarL-1) >= downPush.end) &&*/ ( BarUtil.isUpBar(quotesL[lastbarL-1]) || (quotesL[lastbarL-1].low > quotesL[lastbarL-2].low))){
			//	if ( BarUtil.isUpBar(quotesL[lastbarL-1])){
			//	for ( int i = endPos+1; i< lastbarL-1; i++)
			//		if (BarUtil.isUpBar(quotesL[i]))
			//			return null;
			//	}
				
			/*
			int firstPullBack = endPos+1;
			while ( firstPullBack < lastbarL) {
				if ( quotesL[firstPullBack].low > quotesL[firstPullBack-1].low) 
					break;
				else
					firstPullBack++;
			}
			
			if ( firstPullBack != lastbarL-1 )
				return null;

			{*/
				QuoteData lowestPoint = Utility.getLow(quotesL, endPos, lastbarL-1);
				if ( lowestPoint == null )
					lowestPoint = quotesL[lastbarL-1];
				// verify if this is a breakout
				/*for ( int j = lowestPoint.pos-1; j>=(((lowestPoint.pos-31)>=0)?(lowestPoint.pos-31):0); j-- )
					if ( quotesL[j].low < lowestPoint.low)
						return null;
				*/
				if ( findTradeHistory( Constants.ACTION_SELL, lowestPoint.time) != null )
					return null;

				PushHighLow lastPush = downPush.getPushHighLow(downPush.getNumOfPushes()-1);
				double lastBreakOutSize = (quotesL[lastPush.prePos].low - lowestPoint.low)/getPIP_SIZE();
				//if (lastBreakOutSize < FIXED_STOP/3 )
					//return null;
				if ( downPush.getNumOfPushes() >= 2 ){
					PushHighLow prevLastPush = downPush.getPushHighLow(downPush.getNumOfPushes()-2);
					if (lastPush.pullBack.high > prevLastPush.pullBack.high )
						return null;
				}
				
				if ( findTradeHistory( Constants.ACTION_SELL, lowestPoint.time) != null )
					return null;

				
				//System.out.println("pullback SELL detected at " + data.time + " last trend peak was " + lowestPoint.time + " " + lowestPoint.low + " pushEnd:" + quotesL[endPos].time + " " + endPos + " " + (lastbarL-1) + " " + lastPush.curPos) ;
				//System.out.println("Current Trend:" + pushTrend.getCurrentTrend().toString(quotesL, getPIP_SIZE()));
				//System.out.println("Prev Trend:" + pushTrend.getLastTrend().toString(quotesL, getPIP_SIZE()));
				
				info("triggering L bar is " + quotesL[lastbarL-1].time);
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(lowestPoint.time);
				trade.setFirstBreakOutTime(lowestPoint.time);
				trade.setPullBackStartTime(lowestPoint.time);
				trade.phl = lastPush;

				
				// Filters
				if ( realtime_count == 1) {  // triggers on the first bar, not accurate
					removeTrade();
					return null;
				}
				
				/*
				// try to remove the first pullback breakout
				if (( pushTrend.getCurrentTrend().getNumOfPushes() == 1 ) && (( pushTrend.getLastTrend() != null ) && (pushTrend.getLastTrend().getNumOfPushes() >= 3))){
					PushHighLow lastTrendPush = pushTrend.getCurrentTrend().phls[0];
					if ((float)(Math.round(10*lastTrendPush.breakOutSize /getPIP_SIZE())/10.0f) < 5.0 ){
						info("trend is too small, removed");
						removeTrade();
						return null;
					}
				}*/
				
				return trade;
			}
		}

		return null;
	}

	
	
	public Trade detect_trend_momentum(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbar = quotes.length - 1;

		BarTrendBarDTO btdd = BarTrendDetector.detect(quotes);

		if ( btdd.direction == Constants.TREND_UP_BREAK ){
			BarTrendBarDTO btddL = BarTrendDetector.detect(quotesL);

			//debug("MC check buy");
			if ( btddL.direction == Constants.TREND_DOWN ){
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY))){
						warning(data.time + " reverse detected, exit trade");
						//AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);
				}
				
				if ( trade == null ){	
					if ( findTradeHistory( Constants.ACTION_SELL, btddL.trendBar) != null ){
						System.out.println("duplicate found");
						return null;
					}
					info("Break uptrend detected at " + data.time + " SELL " + data.close ) ;
					//*System.out.println("Last Push" + quotesL[lastPush.prePos].time + " " + quotesL[lastPush.prePos].high + quotesL[lastPush.curPos].time + " " + quotesL[lastPush.curPos].high ); 
					
					createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.trendBar = btddL.trendBar;
					//trade.setFirstBreakOutStartTime(quotes[startL].time);// for history lookup only
					//trade.pullBackStartTime = quotesL[pushStartL].time;
	
					double triggerPrice = data.close;
					enterMarketPosition(triggerPrice);
					//super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + triggerPrice, trade);
					return trade;
				}
			}

		}
		else if ( btdd.direction == Constants.TREND_DOWN_BREAK ){
			
			BarTrendBarDTO btddL = BarTrendDetector.detect(quotesL);

			//debug("MC check buy");
			if ( btddL.direction == Constants.TREND_UP ){
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL))){
					warning(data.time + " reverse detected, exit trade");
					//AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
					closePositionByMarket( trade.remainingPositionSize, data.close);
				}
				//debug("MC check sell");
				//if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				//	return null;
	
				if ( findTradeHistory( Constants.ACTION_BUY, btddL.trendBar) != null )
					return null;
	
				if ( trade == null ){	
	
					
						//System.out.println("pullback SELL detected at " + data.time + " last trend peak was " + lowestPoint.time + " " + lowestPoint.low + " pushEnd:" + quotesL[endPos].time + " " + endPos + " " + (lastbarL-1) + " " + lastPush.curPos) ;
						//System.out.println("Current Trend:" + pushTrend.getCurrentTrend().toString(quotesL, getPIP_SIZE()));
						//System.out.println("Prev Trend:" + pushTrend.getLastTrend().toString(quotesL, getPIP_SIZE()));
					info("Break downtrend detected at " + data.time + " BUY " + data.close) ;
						
					createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.trendBar = btddL.trendBar;
					//trade.setFirstBreakOutStartTime(quotes[startL].time);// for history lookup only
					//trade.pullBackStartTime = quotesL[pushStartL].time;
		
					double triggerPrice = data.close;
					enterMarketPosition(triggerPrice);
					//super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + triggerPrice, trade);
					return trade;
				}
			}
		}

		return null;
	}


	
	
	public Trade detect_trend_at_least_1_pullback_bar(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;

		PushTrendDAO pushTrend = PushSetup.getPushTrend2( quotesL, getPIP_SIZE() );
		
		if (( pushTrend != null) && ( pushTrend.getDirection() == Constants.DIRECTION_UP )){

			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			PushList upPush = pushTrend.getCurrentTrend();
			PushHighLow lastPush = upPush.getPushHighLow(upPush.getNumOfPushes()-1);
			int peakPos = lastPush.curPos;
			
			//if ( BarUtil.isDownBar(quotesL[lastbarL-1]) || 
			//		((lastbarL-1) >= upPush.end) && (quotesL[lastbarL-1].high < quotesL[lastbarL-2].high)){

			if (((lastbarL-1) > peakPos) && ( BarUtil.isDownBar(quotesL[lastbarL-1]) || (quotesL[lastbarL-1].high < quotesL[lastbarL].high))){
				
				/*if ( BarUtil.isDownBar(quotesL[lastbarL-1])){
					for ( int i = peakPos+1; i< lastbarL-1; i++)  
					if (BarUtil.isDownBar(quotesL[i]))
						return null;
				}*/
				
				if ( findTradeHistory( Constants.ACTION_BUY, quotesL[peakPos].time) != null )
					return null;

				if ( upPush.getNumOfPushes() >= 2 ){
					PushHighLow prevLastPush = upPush.getPushHighLow(upPush.getNumOfPushes()-2);
					if (lastPush.pullBack.low < prevLastPush.pullBack.low )
						return null;
				}

				//System.out.println( data.time + " " + getSymbol() + " " + pushTrend.toString(quotesL, getPIP_SIZE()));
				//System.out.println( "Setup PushBegin:" + lastPush.pullBack.time + lastPush.pullBack.low);
				System.out.println("Buy detected, triggering peakbar is " + quotesL[peakPos].time + " lastPushStart:" + lastPush.pullBack.time);
				
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotesL[peakPos].time);
				trade.setFirstBreakOutTime(quotesL[peakPos].time);
				trade.setPullBackStartTime(quotesL[peakPos].time);
				trade.phl = lastPush;
				
				if ( realtime_count == 1) {  // triggers on the first bar, not accurate
					removeTrade();
					return null;
				}

				/*
				// try to remove the first pullback breakout
				if (( pushTrend.getCurrentTrend().getNumOfPushes() == 1 ) && (( pushTrend.getLastTrend() != null ) && (pushTrend.getLastTrend().getNumOfPushes() >= 3))){
					PushHighLow lastTrendPush = pushTrend.getCurrentTrend().phls[0];
					if ((float)(Math.round(10*lastTrendPush.breakOutSize /getPIP_SIZE())/10.0f) < 5.0 ){
						info("trend is too small, removed");
						removeTrade();
						return null;
					}
				}*/

				return trade;
			}

		}
		else if (( pushTrend != null) && ( pushTrend.getDirection() == Constants.DIRECTION_DOWN )){
			
			//debug("MC check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			PushList downPush = pushTrend.getCurrentTrend();
			PushHighLow lastPush = downPush.getPushHighLow(downPush.getNumOfPushes()-1);
			int peakPos = lastPush.curPos;
			
			//if ( BarUtil.isUpBar(quotesL[lastbarL-1]) || 
			//		((lastbarL-1) >= downPush.end) && (quotesL[lastbarL-1].low > quotesL[lastbarL-2].low)){

			
			
			if (((lastbarL-1) > peakPos) && ( BarUtil.isUpBar(quotesL[lastbarL-1]) || (quotesL[lastbarL-1].low > quotesL[lastbarL-2].low))){
				
				/*if ( BarUtil.isUpBar(quotesL[lastbarL-1])){
				for ( int i = endPos+1; i< lastbarL-1; i++)
					if (BarUtil.isUpBar(quotesL[i]))
						return null;
				}*/
				
				if ( findTradeHistory( Constants.ACTION_SELL, quotesL[peakPos].time) != null )
					return null;

				if ( downPush.getNumOfPushes() >= 2 ){
					PushHighLow prevLastPush = downPush.getPushHighLow(downPush.getNumOfPushes()-2);
					if (lastPush.pullBack.high > prevLastPush.pullBack.high )
						return null;
				}

				//System.out.println("pullback SELL detected at " + data.time + " last trend peak was " + lowestPoint.time + " " + lowestPoint.low + " pushEnd:" + quotesL[endPos].time + " " + endPos + " " + (lastbarL-1) + " " + lastPush.curPos) ;
				//System.out.println("Current Trend:" + pushTrend.getCurrentTrend().toString(quotesL, getPIP_SIZE()));
				System.out.println("Sell detected, triggering peakbar is " + quotesL[peakPos].time + " lastPushStart:" + lastPush.pullBack.time);
				
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotesL[peakPos].time);
				trade.setFirstBreakOutTime(quotesL[peakPos].time);
				trade.setPullBackStartTime(quotesL[peakPos].time);
				trade.phl = lastPush;

				
				// Filters
				if ( realtime_count == 1) {  // triggers on the first bar, not accurate
					removeTrade();
					return null;
				}
				
				/*
				// try to remove the first pullback breakout
				if (( pushTrend.getCurrentTrend().getNumOfPushes() == 1 ) && (( pushTrend.getLastTrend() != null ) && (pushTrend.getLastTrend().getNumOfPushes() >= 3))){
					PushHighLow lastTrendPush = pushTrend.getCurrentTrend().phls[0];
					if ((float)(Math.round(10*lastTrendPush.breakOutSize /getPIP_SIZE())/10.0f) < 5.0 ){
						info("trend is too small, removed");
						removeTrade();
						return null;
					}
				}*/
				
				return trade;
			}
		}

		return null;
	}


	

	
	
	
	
	
	public Trade detect_push_pullback( QuoteData data )
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int start, end;
		if (BarUtil.isUpBar(quotesL[lastbarL-2]) && (quotesL[lastbarL-2].close > ema20L[lastbarL-2]) 
				&& ( BarUtil.isDownBar(quotesL[lastbarL-1]) || ( quotesL[lastbarL-1].high < quotesL[lastbarL-2].high ))) 
		{
			// make sure this is the first pullback after the 20MA
			int touch20MAPos = lastbarL-2;
			boolean hasLowPoint = false;
			while (!( quotesL[touch20MAPos].low < ema20L[touch20MAPos])){
				if (quotesL[touch20MAPos].high < quotesL[touch20MAPos-1].high){
					hasLowPoint = true;
					break;
				}
				touch20MAPos--;
			}
			
			if ( hasLowPoint)
				return null;
			
			//if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
			//	return null;

			start = end = lastbarL-2;
			while (BarUtil.isUpBar(quotesL[start-1]) || (quotesL[start-1].low < quotesL[start].low))
				start--;

			double aveSize = BarUtil.averageBarSize(quotesL);
			boolean bigBar = false;
			for ( int i = start; i <= end; i++)
				if ( BarUtil.barSize(quotesL[i]) > 1.5 * aveSize )
					bigBar = true;
			if ((end > start ) && (( quotesL[end].close - quotesL[start].open ) > (aveSize * (end - start+1))))
				bigBar = true;
			
			if ( !bigBar )
				return null;

			//double breakOutSize = quotesL[end].high - quotesL[start].low;
			//if ( breakOutSize < 60 * PIP_SIZE )
			//	return null;
			
	//		if 	( tradeHistory.size() >= 3 )
	//			return null;
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			warning("pullback BUY detected at " + data.time + " start:" + quotesL[start].time + " " + quotesL[start].low + " end:" + quotesL[end].time + " " + quotesL[end].high);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotesL[lastbarL].time;
			trade.setFirstBreakOutStartTime(quotesL[start].time);
			trade.setFirstBreakOutTime(quotesL[end].time);
			
			return trade;
		}	
		else if (BarUtil.isDownBar(quotesL[lastbarL-2]) && (quotesL[lastbarL-2].close < ema20L[lastbarL-2]) 
				&& ( BarUtil.isUpBar(quotesL[lastbarL-1]) || ( quotesL[lastbarL-1].low > quotesL[lastbarL-2].low ))) 
		{
			// make sure this is the first pullback after the 20MA
			int touch20MAPos = lastbarL-2;
			boolean hasHighPoint = false;
			while (!( quotesL[touch20MAPos].high > ema20L[touch20MAPos])){
				if (quotesL[touch20MAPos].low > quotesL[touch20MAPos-1].low){
					hasHighPoint = true;
					break;
				}
				touch20MAPos--;
			}
			
			if ( hasHighPoint)
				return null;

			//if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
			//	return null;

			start = end = lastbarL-2;
			while (BarUtil.isDownBar(quotesL[start-1]) || (quotesL[start-1].high > quotesL[start].high))
				start--;
			
			double aveSize = BarUtil.averageBarSize(quotesL);
			boolean bigBar = false;
			for ( int i = start; i <= end; i++)
				if ( BarUtil.barSize(quotesL[i]) > 1.5 * aveSize )
					bigBar = true;
			if  ((end > start ) && (( quotesL[start].open - quotesL[end].close ) > (aveSize * (end - start+1))))
				bigBar = true;

			if ( !bigBar )
				return null;
			
			//double breakOutSize = quotesL[end].high - quotesL[start].low;
			//if ( breakOutSize < 60 * PIP_SIZE )
			//	return null;
			
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[start].time) != null )
				return null;
			
			warning("pullback SELL detected at " + data.time + " start:" + quotesL[start].time + " " + quotesL[start].high + " end:" + quotesL[end].time + " " + quotesL[end].low);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotesL[lastbarL].time;
			trade.setFirstBreakOutStartTime(quotesL[start].time);
			trade.setFirstBreakOutTime(quotesL[end].time);
			
			return trade;
		}	
		
		return null;
	}



	
	
	
	public Trade detect( QuoteData data )
	{
		//return detect_straight_momentum( data );
		return detect_pullback_momentum( data );
	}
	

	public Trade detect_straight_momentum( QuoteData data ){
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;

		//return checkTrendBreakBigChart(data);
		if (BarUtil.isUpBar(quotes[lastbar-1])){
			MomentumDAO momentum = MomentumDetector.detect_strong_momentum(quotes, Constants.DIRECTION_UP,lastbar);
			if ( momentum != null ){
				if ( findTradeHistory( Constants.ACTION_BUY, momentum.startTime ) != null )
					return null;

		    	warning(symbol + " " + data.time + " Momentum DOWN detected " + momentum.startTime + " " + quotes[momentum.startPos].open + " " + 
		    			momentum.endTime + " " + quotes[momentum.endPos].close + " " + (int)(momentum.size/PIP_SIZE));
				QuoteData lowest = Utility.getLow( quotes, momentum.endPos, lastbar-1);
				createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setPullBackStartTime(momentum.startTime);
				trade.setFirstBreakOutStartTime(momentum.startTime);
				
				double triggerPrice = data.close;
				enterMarketPosition(triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart60 " + trade.action + " " + triggerPrice, trade);

				return trade;
				
			}
		} else if (BarUtil.isDownBar(quotes[lastbar-1])){
			MomentumDAO momentum = MomentumDetector.detect_strong_momentum(quotes, Constants.DIRECTION_DOWN,lastbar);
			if ( momentum != null ){
				if ( findTradeHistory( Constants.ACTION_SELL, momentum.startTime ) != null )
					return null;

		    	warning(symbol + " " + data.time + " Momentum UP detected " + momentum.startTime + " " + quotes[momentum.startPos].open + " " + 
		    			momentum.endTime + " " + quotes[momentum.endPos].close + " " + (int)(momentum.size/PIP_SIZE));
				QuoteData highest = Utility.getHigh( quotes, momentum.endPos, lastbar-1);
				createOpenTrade(Constants.TRADE_MMT, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setPullBackStartTime(momentum.startTime);
				trade.setFirstBreakOutStartTime(momentum.startTime);

				double triggerPrice = data.close;
				enterMarketPosition(triggerPrice);
				super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " chart60 " + trade.action + " " + triggerPrice, trade);

			}
		}

		
		
		return null;
	}
	

	public Trade detect_pullback_momentum( QuoteData data )
	{
		QuoteData[] quotes= getQuoteData(Constants.CHART_240_MIN);
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		PushHighLow lastPushUp=null;
		PushHighLow lastPushDown=null;
		int peakBar = lastbar-3;

		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotes[lastbar-2].low < ema20[lastbar-2]) || ( quotes[lastbar - 3].low < ema20[lastbar - 3])))
		{
			int startL = downPos;
			while ( quotes[startL].high < ema20[startL])
				startL--;

			int startPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, startL, 3);
			if (startPos == Constants.NOT_FOUND)
				startPos = 3;
			int pushStart = Utility.getHigh( quotes, startPos-3, startL).pos;
			//int pullBackStart = Utility.getLow( quotes, startL, downPos).pos;

			PushList pushListDown = PushSetup.getDown2PushList( quotes, pushStart, peakBar);
			if ( pushListDown != null ){
				PushHighLow[] phls = pushListDown.phls;
				if ( phls != null ){
					int lastPushIndex = phls.length - 1;
					lastPushDown = phls[phls.length - 1];

					int peakPoint = lastPushDown.curPos;
					while (( quotes[peakPoint+1].low < quotes[peakPoint].low ) && ( peakPoint < lastbar-1))
						peakPoint++;

					if ( peakPoint < lastbar-11 )
						return null;

					// remaining should not be lower than peak
					for ( int i = peakPoint +1; i <= lastbar; i++)
						if ( quotes[i].low < quotes[peakPoint].low )
							return null;
					
					if ( findTradeHistory( Constants.ACTION_SELL,quotes[peakPoint].time) != null )
						return null;

			    	warning(symbol + " pullback from low detected " + data.time + " lastLow:" + quotes[peakPoint].time );
					createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.setPullBackStartTime(quotes[peakPoint].time);
					trade.setFirstBreakOutStartTime(quotes[peakPoint].time);
					return trade;
				}
			}
		}
		else if ((direction == Constants.DIRECTION_UP)
				&& (( quotes[lastbar-2].high > ema20[lastbar-2]) || ( quotes[lastbar-3].high > ema20[lastbar-3])))
		{
			int startL = upPos;
			while ( quotes[startL].low > ema20[startL])
				startL--;

			int startPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, startL, 3);
			if (startPos == Constants.NOT_FOUND)
				startPos = 3;
			int pushStart = Utility.getLow( quotes, startPos-3, startL).pos;
			//int pullBackStart = Utility.getHigh( quotes, startL, upPos).pos;

			PushList pushListUp = PushSetup.getUp2PushList( quotes, pushStart, peakBar);
			if ( pushListUp != null ){
				PushHighLow[] phls = pushListUp.phls;
				if ( phls != null ){
					int lastPushIndex = phls.length - 1;
					lastPushUp = phls[phls.length - 1];

					int peakPoint = lastPushUp.curPos;
					while (( quotes[peakPoint+1].high > quotes[peakPoint].high ) && ( peakPoint < lastbar-1))
						peakPoint++;

					if ( peakPoint < lastbar-11 )
						return null;

					for ( int i = peakPoint +1; i <= lastbar; i++)
						if ( quotes[i].high > quotes[peakPoint].high ) 
							return null;
					
					if ( findTradeHistory( Constants.ACTION_BUY,quotes[peakPoint].time) != null )
						return null;

			    	warning(symbol + " pullback from high detected " + data.time + " lastHigh:" + quotes[peakPoint].time );
					createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.setPullBackStartTime(quotes[peakPoint].time);
					trade.setFirstBreakOutStartTime(quotes[peakPoint].time);
					return trade;
				}
			}
		}

		/*
		PushHighLow lastPushUp=null;
		int pushStartL = Pattern.findLastPriceConsectiveBelowMA(quotes, ema_20, peakBarL, 2);
		if (pushStartL == Constants.NOT_FOUND)
			pushStartL = 2;
		pushStartL = Utility.getLow( quotes, pushStartL-2, pushStartL).pos;
		PushList pushListUp = PushSetup.getUp2PushList( quotes, pushStartL, peakBarL);
		if ( pushListUp != null ){
			PushHighLow[] phls = pushListUp.phls;
			if ( phls != null ){
				int lastPushIndex = phls.length - 1;
				lastPushUp = phls[phls.length - 1];
			}
		}
		
		PushHighLow lastPushDown=null;
		int pushStartH = Pattern.findLastPriceConsectiveAboveMA(quotes, ema_20, peakBarL, 2);
		if (pushStartH == Constants.NOT_FOUND)
			pushStartH = 2;
		pushStartH = Utility.getHigh( quotes, pushStartH-2, pushStartH).pos;
		PushList pushListDown = PushSetup.getDown2PushList( quotes, pushStartH, peakBarL);
		if ( pushListDown != null ){
			PushHighLow[] phls = pushListDown.phls;
			if ( phls != null ){
				int lastPushIndex = phls.length - 1;
				lastPushDown = phls[phls.length - 1];
			}
		}
		
		if (( lastPushUp != null ) && (( lastPushDown == null) || ( lastPushUp.curPos > lastPushDown.curPos))){ 
			int peakPoint = lastPushUp.curPos;
			while (( quotes[peakPoint+1].high > quotes[peakPoint].high ) && ( peakPoint < lastbar-1))
				peakPoint++;

			if ( peakPoint < lastbar-11 )
				return null;

			for ( int i = peakPoint +1; i <= lastbar; i++)
				if ( quotes[i].high > quotes[peakPoint].high ) 
					return null;
			
			if ( findTradeHistory( Constants.ACTION_BUY,quotes[peakPoint].time) != null )
				return null;

	    	warning(symbol + " pullback from high detected " + data.time + " lastHigh:" + quotes[peakPoint].time );
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotes[lastbar].time;
			trade.setPullBackStartTime(quotes[peakPoint].time);
			trade.setFirstBreakOutStartTime(quotes[peakPoint].time);
			return trade;
		} 
		
		if (( lastPushDown != null ) && (( lastPushUp == null) || ( lastPushDown.curPos > lastPushUp.curPos))){ 
			int peakPoint = lastPushDown.curPos;
			while (( quotes[peakPoint+1].low < quotes[peakPoint].low ) && ( peakPoint < lastbar-1))
				peakPoint++;

			if ( peakPoint < lastbar-11 )
				return null;

			for ( int i = peakPoint +1; i <= lastbar; i++)
				if ( quotes[i].low < quotes[peakPoint].low )
					return null;
			
			if ( findTradeHistory( Constants.ACTION_SELL,quotes[peakPoint].time) != null )
				return null;

	    	warning(symbol + " pullback from low detected " + data.time + " lastLow:" + quotes[peakPoint].time );
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotes[lastbar].time;
			trade.setPullBackStartTime(quotes[peakPoint].time);
			trade.setFirstBreakOutStartTime(quotes[peakPoint].time);
			return trade;
		} */

		return null;
	}

	
	
	
	public void entry( QuoteData data )
	{
		if ((MODE == Constants.TEST_MODE) && ((trade.status.equals(Constants.STATUS_FILLED) || trade.status.equals(Constants.STATUS_PARTIAL_FILLED))))
			checkStopTarget(data,0,0);

		//if ( trade.type.equals(Constants.TRADE_RM))
		//	trade_entry_mode(data);
		
		if ((trade != null) && (trade.type.equals(Constants.TRADE_PBK))){
			//track_PBK_entry_base(data);  // original MC
			entry_break_LL_HH(data);// method 1, current
			//track_PBK_entry_using_momentum(data); // method does not work
			track_PBK_entry_using_momentum2(data); // method 2
		
			
			/*if ( pullbackConfluenceEntry == null )
				pullbackConfluenceEntry = new PullbackConfluenceEntry(this);
			pullbackConfluenceEntry.entry_manage(data, this);*/
		}

	}
		
		
	public void manage( QuoteData data )
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data,0,0);

		if ( trade != null )
		{	
			if ( trade.status.equals(Constants.STATUS_OPEN))
				checkTradeExpiring_ByTime(data);
			
			if (( trade != null ) && ( trade.status.equals(Constants.STATUS_FILLED))){
				//exit123_new9c4_123( data );  
				//exit123_new9c4( data );  
				//exit123_close_monitor( data );

			}		
		}
		
	}
	
	
	
	public void track_PBK_entry_base(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		
		int pushStart = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
		int pushEnd = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );

		if (Constants.ACTION_BUY.equals(trade.action))
		{
			pushEnd = Utility.getHigh( quotesL, pushStart, pushEnd).pos;
			double pushSizeL = quotesL[pushEnd].high - quotesL[pushStart].low;
			
			int pullbackStart = Utility.findPositionByHour(quotes, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pullbackStart = Utility.getHigh( quotes, pullbackStart-4<0?0:pullbackStart-4, pullbackStart+4>lastbar?lastbar:pullbackStart+4).pos;
			
			QuoteData pullbackLow = Utility.getLow(quotes, pullbackStart, lastbar);
			double pullBackSize = quotesL[pushEnd].high - pullbackLow.low ;
			if  ((pullBackSize > 0.7 * pushSizeL)  && (pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)){
				warning("pullback low is" + pullbackLow.time + " " + pullbackLow.low + " pull back too deep, trade removed");
				removeTrade();
				return;
			}
			
			if (( lastbarL > pushEnd + 1 ) && BarUtil.isUpBar(quotesL[lastbarL-1]) && (quotesL[lastbarL].high > quotesL[lastbarL-1].high ))
			{
				double triggerPrice = quotesL[lastbarL-1].high;
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}

			if ( lastbarL-pushEnd >=3 ){
				PushList pushList = PushSetup.getDown2PushList(quotes, pullbackStart, lastbar);
				if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
				{	
					int pushSize = pushList.phls.length;
					PushHighLow[] phls = pushList.phls;
					lastPush = pushList.phls[pushList.phls.length - 1];
					if ( pushSize > 1 )
						prevPush = pushList.phls[pushSize - 2];
	
					if (( lastPush != null ) && ( data.high > lastPush.pullBack.high ))
					{
						if ( realtime_count < 10 ){
							removeTrade();
							return;
						}
						
						//TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_LOWER_LOW, quotes[lastbar].time);
						//trade.addTradeEvent(tv);
	
						double triggerPrice = lastPush.pullBack.high;  
						//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
						//enterMarketPosition(triggerPrice);
						int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
						
						warning("Break LL triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.high );
						enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
						cancelLimits();
	
						return;
					}
				}
			}
			
			if ( data.high > quotes[pullbackStart].high )
			{
				if ( realtime_count < 10 ){
					removeTrade();
					return;
				}

				double triggerPrice = quotes[pullbackStart].high;

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();

				return;
			}
		}
		else if (Constants.ACTION_SELL.equals(trade.action))
		{
			pushEnd = Utility.getLow( quotesL, pushStart, pushEnd).pos;
			double pushSizeL = quotesL[pushStart].high - quotesL[pushEnd].low ;
			
			int pullbackStart = Utility.findPositionByHour(quotes, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pullbackStart = Utility.getLow( quotes, pullbackStart-4<0?0:pullbackStart-4, pullbackStart+4>lastbar?lastbar:pullbackStart+4).pos;
			
			QuoteData pullbackHigh = Utility.getHigh(quotes, pullbackStart, lastbar);
			double pullBackSize = pullbackHigh.high - quotesL[pushEnd].low  ;
			if  ((pullBackSize > 0.7 * pushSizeL)  && (pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)){
				warning("pullback high is" + pullbackHigh.time + " " + pullbackHigh.high + " pull back too deep, trade removed");
				removeTrade();
				return;
			}
				
			if (( lastbarL > pushEnd + 1 ) && BarUtil.isDownBar(quotesL[lastbarL-1]) && (quotesL[lastbarL].low < quotesL[lastbarL-1].low ))
			{
				double triggerPrice = quotesL[lastbarL-1].low;
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}

			if ( lastbarL-pushEnd >=3 ){
				PushList pushList = PushSetup.getUp2PushList(quotes, pullbackStart, lastbar);
				if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
				{	
					int pushSize = pushList.phls.length;
					PushHighLow[] phls = pushList.phls;
					lastPush = pushList.phls[pushList.phls.length - 1];
					if ( pushSize > 1 )
						prevPush = pushList.phls[pushSize - 2];
	
					if (( lastPush != null ) && ( data.low < lastPush.pullBack.low ))
					{
						if ( realtime_count < 10 ){
							removeTrade();
							return;
						}
						
						//TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_LOWER_LOW, quotes[lastbar].time);
						//trade.addTradeEvent(tv);
	
						double triggerPrice = lastPush.pullBack.low; 
	
						//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
						//enterMarketPosition(triggerPrice);
						int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
						
						warning("Breakout HH base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
						enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
						cancelLimits();
	
						return;
					}
				}
			}
			
			if ( data.low < quotes[pullbackStart].low )
			{
				if ( realtime_count < 10 ){
					removeTrade();
					return;
				}

				double triggerPrice = quotes[pullbackStart].low;

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();

				return;
			}
		}
		

		return;
	}


	// using strong push
	public void track_PBK_entry_using_momentum2(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
	
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
					System.out.println(symbol + " " + data.time + " Up Bar detected:" + quotes60[strongBarEnd].time + " " + quotes60[strongBarEnd].high);
					
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

					System.out.println(symbol + " " + data.time + " Strong Up Bar detected:" + quotes60[strongBarBegin].time + " " + quotes60[strongBarEnd].time + " " + quotes60[strongBarEnd].high);
					double triggerPrice = quotes60[strongBarEnd].high;
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					cancelLimits(); 
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
					removeTrade();
					return;
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
				enterMarketPositionMulti(entryPrice,remainingToBeFilledPos);
				cancelLimits();
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

					System.out.println(symbol + " " + data.time + " String Down Bar detected:" + quotes60[strongBarBegin].time + " " + quotes60[strongBarEnd].time + " " + quotes60[strongBarEnd].low);
					double triggerPrice = quotes60[strongBarEnd].low;
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					cancelLimits(); 
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

			if ( data.low < quotes60[pullBackStart].low ){
				double entryPrice = quotes60[pullBackStart].low;
				if ( quotes15[lastbar15-1].low < entryPrice ){
					removeTrade();
					return;
				}
	
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				//warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].low );
				enterMarketPositionMulti(entryPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
		}
	}

	
	
	
	public void track_PBK_entry_using_momentum(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
	
		//int detectPos = Utility.findPositionByHour(quotesL, trade.detectTime, Constants.BACK_TO_FRONT );
	
		if (Constants.ACTION_BUY.equals(trade.action)){
			int pushStart = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
			int pushEnd = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pushEnd = Utility.getHigh( quotesL, pushStart, pushEnd).pos;
			//double pushSizeL = quotesL[pushEnd].high - quotesL[pushStart].low;

			if ( lastbarL - pushEnd >= 3 ){
				int lastDownBarEnd = lastbar60;
				while (!BarUtil.isDownBar(quotes60[lastDownBarEnd]))
					lastDownBarEnd--;
				int lastDownBarBegin = lastDownBarEnd;
				while (BarUtil.isDownBar(quotes60[lastDownBarBegin]))
					lastDownBarBegin--;
				lastDownBarBegin++;
				
				if ( data.high > quotes60[lastDownBarBegin].high ){
					double entryPrice = quotes60[lastDownBarBegin].high;
					if ( quotes15[lastbar15-1].high > entryPrice ){
						removeTrade();
						return;
					}
					enterMarketPositionMulti(entryPrice, POSITION_SIZE);
					return;
				}
			}
			
			if ( data.high > quotesL[pushEnd].high ){
				double entryPrice = quotesL[pushEnd].high;
				if ( quotes15[lastbar15-1].high > entryPrice ){
					removeTrade();
					return;
				}

				double triggerPrice = quotesL[pushEnd].high;
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
			
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			int pushStart = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
			int pushEnd = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pushEnd = Utility.getLow( quotesL, pushStart, pushEnd).pos;
			//double pushSizeL = quotesL[pushStart].high - quotesL[pushEnd].low ;

			if ( lastbarL - pushEnd >= 3 ){
				int lastUpBarEnd = lastbar60;
				while (!BarUtil.isUpBar(quotes60[lastUpBarEnd]))
					lastUpBarEnd--;
				int lastUpBarBegin = lastUpBarEnd;
				while (BarUtil.isUpBar(quotes60[lastUpBarBegin]))
					lastUpBarBegin--;
				lastUpBarBegin++;
				
				if ( data.low < quotes60[lastUpBarBegin].low ){
					double entryPrice = quotes60[lastUpBarBegin].low;
					if ( quotes15[lastbar15-1].low < entryPrice ){
						removeTrade();
						return;
					}
					enterMarketPositionMulti(entryPrice, POSITION_SIZE);
					return;
				}
			}

			if ( data.low < quotesL[pushEnd].low ){
				double entryPrice = quotesL[pushEnd].low;
				if ( quotes15[lastbar15-1].low < entryPrice ){
					removeTrade();
					return;
				}
	
				double triggerPrice = quotesL[pushEnd].low ;
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				//warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
		}
	}

	public void entry_break_LL_HH(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar240 = quotes240.length - 1;
		int lastbar60 = quotes60.length - 1;
		int lastbar15 = quotes15.length - 1;
		int startPos60 = 0;
		double[] ema = Indicator.calculateEMA(quotes60, 20);
			
		int detect240 = Utility.findPositionByHour(quotes240, trade.detectTime, Constants.BACK_TO_FRONT );
		
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
						removeTrade();
						return;
					}

					warning("Trade entry_HH_LL: Trade buy entered at " + data.time + " " + trade.entryPrice);
					//enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
			    	enterMarketPosition(trade.entryPrice);
					return;
				}
			}

			int startPos240 = Utility.findPositionByHour(quotes240, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			double startPrice = quotes240[startPos240].high;
			if ( data.high > startPrice ){
				trade.entryPrice = startPrice;
				if ( quotes15[lastbar15-1].high > trade.entryPrice ){
					removeTrade();
					return;
				}
				warning("Trade entry_HH_LL higher than start price: Trade entered at " + data.time + " " + startPrice);
				//enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
		    	enterMarketPosition(trade.entryPrice);
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
						removeTrade();
						return;
					}
					warning("Trade entry_HH_LL: Trade sell entered at " + data.time + " " + trade.entryPrice);
					//enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
			    	enterMarketPosition(trade.entryPrice);
					return;
				}
			}

			int startPos240 = Utility.findPositionByHour(quotes240, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			double startPrice = quotes240[startPos240].low;
			if ( data.low < startPrice ){
				trade.entryPrice = startPrice;
				if ( quotes15[lastbar15-1].low < trade.entryPrice ){
					removeTrade();
					return;
				}
				warning("Trade entry_HH_LL sell lower than start price: Trade entered at " + data.time + " " + startPrice);
				//enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
		    	enterMarketPosition(trade.entryPrice);
				return;
			}
		}
	}


	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	private void createPlaceTrade(String action, double detectPrice, String detectTime){

		createOpenTrade(Constants.TRADE_PBK, action);
		trade.entryPrice = detectPrice;
		trade.detectTime = detectTime;
		
    	enterMarketPosition(trade.entryPrice);
		trade.status = Constants.STATUS_PLACED;
		
	}
	
	
	
	
    public Trade checkMomentumChange(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;

		int lastba240r = quotes240.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes240, 20);

		// to review
		int prevDowns = Pattern.findLastPriceConsectiveBelowOrAtMA(quotes240, ema20, lastba240r, 2);
		int prevUps = Pattern.findLastPriceConsectiveAboveOrAtMA(quotes240, ema20, lastba240r, 2);
		
		if ( prevUps > prevDowns ) 
		{
			ConsectiveBars consecDown = Utility.findLastConsectiveDownBars(quotes240);
			if ( consecDown != null )
			{
				double consecDownSize = quotes240[consecDown.getBegin()].high - quotes240[consecDown.getEnd()].low;
				double avgSize = BarUtil.averageBarSize(quotes240);
				
				if ((( consecDown.getSize() == 1 ) && ( consecDownSize > 2 * avgSize ))
				  ||(( consecDown.getSize() == 2 ) && ( consecDownSize > 3 * avgSize ))
				  ||(( consecDown.getSize() == 3 ) && ( consecDownSize > 4 * avgSize ))
				  ||(( consecDown.getSize() == 4 ) && ( consecDownSize > consecDown.getSize() * 1.5 * avgSize )))
				{
					if (( trade != null ) && ( Constants.ACTION_SELL.equals(trade.action)))
						return trade;

					if ( findTradeHistory( Constants.ACTION_SELL, quotes240[consecDown.getBegin()].time) != null )
						return null;
					
					warning("Sell detected at " + quotes240[lastbar240].time);
					createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes240[lastbar240].time;
					trade.setFirstBreakOutStartTime(quotes240[consecDown.getBegin()].time);
					trade.setFirstBreakOutTime(quotes240[consecDown.getBegin()].time);

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes240[lastbar240].time);
					trade.addTradeEvent(tv);
					return trade;
					
				}
			}
		}
		else if ( prevUps < prevDowns ) 
		{
			ConsectiveBars consecUp = Utility.findLastConsectiveUpBars(quotes240);
			if ( consecUp != null )
			{
				double consecUpSize = quotes240[consecUp.getEnd()].high - quotes240[consecUp.getBegin()].low;
				double avgSize = BarUtil.averageBarSize(quotes240);
				
				if ((( consecUp.getSize() == 1 ) && ( consecUpSize > 2 * avgSize ))
				  ||(( consecUp.getSize() == 2 ) && ( consecUpSize > 3 * avgSize ))
				  ||(( consecUp.getSize() == 3 ) && ( consecUpSize > 4 * avgSize ))
				  ||(( consecUp.getSize() == 4 ) && ( consecUpSize > consecUp.getSize() * 1.5 * avgSize )))
				{
					if (( trade != null ) && ( Constants.ACTION_BUY.equals(trade.action)))
						return trade;

					if ( findTradeHistory( Constants.ACTION_BUY, quotes240[consecUp.getBegin()].time) != null )
						return null;
					
					warning("Buy detected at " + quotes240[lastbar240].time);
					createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes240[lastbar240].time;
					trade.setFirstBreakOutStartTime(quotes240[consecUp.getBegin()].time);
					trade.setFirstBreakOutTime(quotes240[consecUp.getBegin()].time);

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes240[lastbar240].time);
					trade.addTradeEvent(tv);
					return trade;
					
				}
			}
		}
		
		return null;
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

		info("stopped out @ " + trade.stop + " " + trade.remainingPositionSize + " " + time);
		AddCloseRecord(time, Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);
		trade.stopId = 0;
		trade.status = Constants.STATUS_STOPPEDOUT;
		trade.closeTime = time;

		TradeEvent tv = new TradeEvent(TradeEvent.TRADE_STOPPEDOUT, time);
		tv.addNameValue("Stopped Price", new Double(trade.stop).toString());
		trade.addTradeEvent(tv);
		
		cancelAllOrders();
		removeTrade();
		return;
	}


	public void checkOrderFilled(int orderId, int filled)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		//QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;

		if (orderId == trade.stopId){
			OrderFilled_stopId(quotes[lastbar].time);
			return;
		}
		else if ((orderId == trade.limitId1) && ( trade.limitPos1Filled == 0 ))  // avoid sometime same message get sent twoice
		{
			warning("limit order: " + orderId + " " + filled + " filled");
			trade.limitId1 = 0;

			CreateTradeRecord(trade.type, trade.action);
			AddOpenRecord(quotes[lastbar].time, trade.action, trade.limitPos1, trade.limitPrice1);

			trade.limitPos1Filled = trade.limitPos1;
			trade.entryPrice = trade.limitPrice1;
			trade.remainingPositionSize += trade.limitPos1; //+= filled;
			trade.setEntryTime(quotes[lastbar].time);

			// calculate stop here
			trade.stop = trade.limit1Stop;
			String oca = new Long(new Date().getTime()).toString();
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, oca);
				if ( trade.targetPrice != 0 )
				{	
					trade.targetPos = trade.POSITION_SIZE;
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, oca);
				}

			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, oca);
				if ( trade.targetPrice != 0 )
				{	
					trade.targetPos = trade.POSITION_SIZE;
					trade.targetId = placeLmtOrder(Constants.ACTION_SELL.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, oca);
				}
			}
			
			trade.openOrderDurationInHour = 0;
			trade.status = Constants.STATUS_PLACED;
		
		}
		else if ((orderId == trade.stopMarketId) && ( trade.stopMarketPosFilled == 0 ))  // avoid sometime same message get sent twoice
		{
			warning("stop market order: " + orderId + " " + filled + " filled");
			trade.stopMarketId = 0;

			CreateTradeRecord(trade.type, trade.action);
			AddOpenRecord(quotes[lastbar].time, trade.action, trade.POSITION_SIZE, trade.stopMarketStopPrice);

			trade.stopMarketPosFilled = trade.POSITION_SIZE;
			trade.entryPrice = trade.stopMarketStopPrice;
			trade.remainingPositionSize = trade.POSITION_SIZE; //+= filled;
			trade.setEntryTime(quotes[lastbar].time);

			// calculate stop here
			if ( trade.stop != 0 )
			if (Constants.ACTION_SELL.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			else if (Constants.ACTION_BUY.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			
			trade.openOrderDurationInHour = 0;
			trade.status = Constants.STATUS_PLACED;
		
		}
		else if ((orderId == trade.targetId1) && (trade.target1Reached == false))
		{
			warning( "target1 filled, " + " price: " + trade.targetPrice1);
			trade.targetId1 = 0;
			trade.target1Reached = true;
			trade.remainingPositionSize -= trade.targetPos1;
			cancelStop();
			
			warning(" remainning position is " + trade.remainingPositionSize);
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else
			{
				cancelTargets();
				removeTrade();
			}
			return;
		}
		else if ((orderId == trade.targetId2)&& (trade.target2Reached == false))
		{
			warning( "target2 filled, " + " price: " + trade.targetPrice2);
			trade.targetId2 = 0;
			trade.target2Reached = true;
			trade.remainingPositionSize -= trade.targetPos2;
			cancelStop();
			
			info(" remainning position is " + trade.remainingPositionSize);
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else
			{
				cancelTargets();
				removeTrade();
			}
			return;
		}
		else if (orderId == trade.targetId)
		{
			warning( "target filled, " + " price: " + trade.targetPrice);
			trade.targetId = 0;
			trade.targetReached = true;
			trade.remainingPositionSize -= filled;
			cancelStop();
			
			warning("remainning position is " + trade.remainingPositionSize);
			if (trade.remainingPositionSize > 0)
			{
				String oca = new Long(new Date().getTime()).toString();

				if (Constants.ACTION_SELL.equals(trade.action))
				{
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
		
					trade.targetPrice = adjustPrice(trade.targetPrice - DEFAULT_PROFIT_TARGET * PIP_SIZE,  Constants.ADJUST_TYPE_DOWN);
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.remainingPositionSize, oca);
				}
				else if (Constants.ACTION_BUY.equals(trade.action))
				{
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

					trade.targetPrice = adjustPrice(trade.targetPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE,  Constants.ADJUST_TYPE_DOWN);
					trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.remainingPositionSize, oca);
				}
			}
			else
			{
				cancelTargets();
				removeTrade();
			}
			return;
		}
		
		
		// check entry limit;
		for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
		{
			TradePosition limit = trade.limits[i];
			if (( limit != null ) && (orderId == limit.orderId)) 
			{
				info(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
				limit.orderId = 0;
				limit.filled = true;

				if (trade.recordOpened == false){
					CreateTradeRecord(trade.type, Constants.ACTION_SELL.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				
				
				if ( trade.getEntryTime() == null )
					trade.setEntryTime(quotes[lastbar].time);
				trade.addFilledPosition(limit.price, limit.position_size, quotes[lastbar].time);
				trade.remainingPositionSize += limit.position_size;
				trade.status = Constants.STATUS_PARTIAL_FILLED;
				
				AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, limit.position_size, limit.price);
				
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
				tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
				trade.addTradeEvent(tv);
				
			}
		}

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
		//QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		//int lastbarL = quotesL.length - 1;
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
					trade.addFilledPosition(limit.price, limit.position_size, quotes[lastbar].time);
					trade.remainingPositionSize += limit.position_size;
					trade.status = Constants.STATUS_PARTIAL_FILLED;
					
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, limit.position_size, limit.price);
					
						

					//trade.stop = trade.getStopPrice();
					//trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.getCurrentPositionSize(), null);
					// calculate stop here
					/*
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);

					trade.status = Constants.STATUS_PARTIAL_FILLED;
					trade.openOrderDurationInHour = 0;

					if ( trade.averageUp )
					{
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
			}

			

			for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
			{
				TradePosition target = trade.targets[i];
				if (( target != null ) && ( target.orderId != 0 ) && (data.low < target.price))
				{
					warning("target hit, close " + target.position_size + " @ " + target.price);
					AddCloseRecord(data.time, Constants.ACTION_BUY, target.position_size, target.price);
					trade.targets[i].orderId = 0;

					cancelStop();
					trade.remainingPositionSize -= target.position_size;
					if (trade.remainingPositionSize <= 0)
					{
						cancelAllOrders();
						removeTrade();
						return;
					}
					else
					{
						if ( trade.stop != 0 )
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
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
					trade.addFilledPosition(limit.price, limit.position_size, quotes[lastbar].time);
					trade.remainingPositionSize += limit.position_size;
					trade.status = Constants.STATUS_PARTIAL_FILLED;
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, limit.position_size, limit.price);

					
					//trade.stop = trade.getStopPrice();
					//trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.getCurrentPositionSize(), null);

					/*
					// calculate stop here
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

					trade.openOrderDurationInHour = 0;

					if ( trade.averageUp )
					{
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
			}

			// check stop;


			
			for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
			{
				TradePosition target = trade.targets[i];
				if (( target != null ) && ( target.orderId != 0 ) && (data.high > target.price))
				{
					warning("target hit, close " + target.position_size + " @ " + target.price);
					AddCloseRecord(data.time, Constants.ACTION_SELL, target.position_size, target.price);
					trade.targets[i].orderId = 0;

					cancelStop();
					trade.remainingPositionSize -= target.position_size;
					if (trade.remainingPositionSize <= 0)
					{
						cancelAllOrders();
						removeTrade();
						return;
					}
					else
					{
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					}
					return;
				}
			}

		}
	}


/*	
	public void checkStopTarget(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check entry limit;
			if ((trade.limitId1 != 0) && (data.high >= trade.limitPrice1) && (trade.limitPos1Filled == 0 ))
			{
				warning(" limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.limitPos1, trade.limitPrice1);
				trade.limitPos1Filled = trade.limitPos1;

				trade.entryPrice = trade.limitPrice1;
				trade.remainingPositionSize += trade.limitPos1Filled;
				trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				trade.stop = trade.limit1Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				trade.limitId1 = 0;

				trade.status = Constants.STATUS_PLACED;
				trade.openOrderDurationInHour = 0;
				
			}

			if ((trade.limitId2 != 0) && (data.high >= trade.limitPrice2))
			{
				// this is for partial entry
				warning(" limit order of " + trade.limitPrice2 + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.limitPos2, trade.limitPrice2);
				trade.limitPos2Filled = trade.limitPos2;

				trade.remainingPositionSize += trade.limitPos2Filled;

				// calculate stop here
				trade.stop = trade.limit2Stop;
				if ( trade.stop == 0 )  
					trade.stop = adjustPrice(trade.limitPrice2 + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				trade.limitId2 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;
			}

			
			if ((trade.stopMarketId != 0) && (data.low <= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.POSITION_SIZE;

				trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.POSITION_SIZE;
				trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
				trade.status = Constants.STATUS_PLACED;
			}

			
			// check stop;
			if ((trade.stopId != 0) && (data.high > trade.stop))
			{
				info("stopped out @ " + trade.stop + " " + data.time);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);
				//trade.stoppedOutPos = lastbar;
				trade.stopId = 0;

				cancelTargets();

				boolean reversequalified = true;
				//if (mainProgram.isNoReverse(symbol ))
					reversequalified = false;
					
				//processAfterHitStopLogic_c();
				removeTrade();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.low < trade.targetPrice1))
			{
				info("target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				cancelStop();
				
				trade.remainingPositionSize -= trade.targetPos1;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);

				return;

			}
			else if ((trade.targetId2 != 0) && (data.low < trade.targetPrice2))
			{
				info("target2 hit, close " + trade.targetPos2 + " @ " + trade.targetPrice2);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos2, trade.targetPrice2);
				trade.targetId2 = 0;

				cancelStop();
				
				trade.remainingPositionSize -= trade.targetPos2;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				return;

			}
			else if ((trade.targetId != 0) && (data.low < trade.targetPrice))
			{
				warning("target hit, close " + trade.targetPos + " @ " + trade.targetPrice);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.targetPrice);
				trade.targetId = 0;

				cancelStop();
				trade.remainingPositionSize -= trade.targetPos;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
				{
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				}
				return;

			}

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// check entry limit;
			if ((trade.limitId1 != 0) && (data.low <= trade.limitPrice1) && (trade.limitPos1Filled == 0 ))
			{
				warning(" limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.limitPos1, trade.limitPrice1);
				trade.limitPos1Filled = trade.limitPos1;

				trade.entryPrice = trade.limitPrice1;
				trade.remainingPositionSize += trade.limitPos1Filled;
				trade.setEntryTime(quotes[lastbar].time);
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				// calculate stop here
				trade.stop = trade.limit1Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
						trade.remainingPositionSize, null);
				trade.limitId1 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;

			}

			// this is for partial entry
			if ((trade.limitId2 != 0) && (data.high <= trade.limitPrice2))
			{
				warning(" limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.limitPos2, trade.limitPrice2);
				trade.limitPos1Filled = trade.limitPos2;

				trade.remainingPositionSize += trade.limitPos2Filled;
				trade.setEntryTime(quotes[lastbar].time);
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				// calculate stop here
				trade.stop = trade.limit2Stop;
				if ( trade.stop == 0 )  
					trade.stop = adjustPrice(trade.limitPrice2 - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
						trade.remainingPositionSize, null);
				trade.limitId2 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;
			}

			if ((trade.stopMarketId != 0) && (data.high >= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.POSITION_SIZE;

				trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.POSITION_SIZE;
				trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
				trade.status = Constants.STATUS_PLACED;
			}

			// check stop;
			if ((trade.stopId != 0) && (data.low < trade.stop))
			{
				info("stopped out @ " + trade.stop + " " + data.time);
				//trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.stop);
				trade.stopId = 0;
				//trade.stoppedOutPos = lastbar;

				cancelTargets();
				//processAfterHitStopLogic_c();
				removeTrade();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.high > trade.targetPrice1))
			{
				info("target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				cancelStop();
				
				trade.remainingPositionSize -= trade.targetPos1;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				return;
			}
			else if ((trade.targetId2 != 0) && (data.high > trade.targetPrice2))
			{
				info("target2 hit, close " + trade.targetPos2 + " @ " + trade.targetPrice2);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos2, trade.targetPrice2);
				trade.targetId2 = 0;

				cancelStop();
				
				trade.remainingPositionSize -= trade.targetPos2;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				return;
			}
			else if ((trade.targetId != 0) && (data.high > trade.targetPrice))
			{
				warning("target hit, close " + trade.targetPos + " @ " + trade.targetPrice);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.targetPrice);
				trade.targetId = 0;

				cancelStop();
				removeTrade();
				return;
			}

		}
	}

*/

	
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

		    if (previousTradeExist(Constants.TRADE_PBK, Constants.ACTION_BUY, start60))
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
						createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);

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


	
	
	
	
	
	
	
	
	
	
	
	public void trackPullBackEntry(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		
		double avgBarSize240 = BarUtil.averageBarSize(quotes240);
		double avgBarSize60 = BarUtil.averageBarSize(quotesL);
		int start240 = Utility.findPositionByHour(quotes240, trade.pullBackStartTime, 2 );
		int detect240 = Utility.findPositionByHour(quotes240, trade.detectTime, 2 );
		boolean slowDown = false;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// look at large timeframe 240
			if ( lastbar240 == detect240 )
			{
				if ((quotesL[lastbar240-1].high - quotesL[lastbar240-2].high) < 5 * PIP_SIZE )
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
				
			}
			
			
			// look at smaller timeframe 60
			int startL = Utility.findPositionByHour(quotesL, trade.pullBackStartTime, 2 );
			startL = Utility.getLow( quotesL, startL-4, startL+4).pos;
			//System.out.println("startL:" + quotesL[startL].time + " " + startL);
			
			// entry at tips
			PushList pushList = PushSetup.getUp2PushList(quotesL, startL, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotesL[lastPush.prePos].high - lastPush.pullBack.low;
				double triggerPrice = quotesL[lastPush.prePos].high;

				if ( lastbarL == pushList.end )
				{	
					// three scenarios
					// big diverages
					if ( lastPush.pullBackSize > 2 * avgBarSize60)
					{
						warning("2 sell triggered: pullbackSize=" + lastPush.pullBackSize + " " +  triggerPrice + quotesL[lastbarL].time + " averageSize:" + avgBarSize60);
						enterMarketPosition(triggerPrice);
						return;
						//enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}
					
					// last did not break out much
				
					if (( prevPush != null ) && ( prevPush.breakOutSize <= 8 * PIP_SIZE ))
					{
						warning("2.1 sell triggered " + triggerPrice + quotesL[lastbarL].time);
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
							warning("3 sell triggered " + triggerPrice + quotesL[lastbarL].time);
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

			int highestPoint = Utility.getHigh( quotesL, startL, lastbarL).pos;

			// enter trade if it fall below the starting point
			if ( data.low < quotesL[startL].low )
			{
				if ((quotesL[highestPoint].high - quotesL[startL].low) > 2.5 * FIXED_STOP * PIP_SIZE )
				{
					removeTrade();
					return;
				}

				double triggerPrice = quotesL[startL].low;
				warning("Breakout base sell triggered at " + data.time + " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].low );
				enterMarketPosition(triggerPrice);
				return;
			}
			
			// enter trade when start to resume the move
			Push anyDownPush = TrendAnalysis.detectLatestMomentum( quotesL, highestPoint, lastbarL-1, Constants.DIRECTION_DOWN, 1.5 * FIXED_STOP * PIP_SIZE );
			if (( anyDownPush != null ) && ( anyDownPush.pushEnd < lastbarL-1))  
			{
				boolean pushDetected = false;
				for ( int j = anyDownPush.pushEnd + 1; j < lastbarL; j++ )
				{
					if ((quotesL[j].low > quotesL[anyDownPush.pushEnd].low) || ( BarUtil.isUpBar(quotesL[j])))
					{
						pushDetected = true;
						break;
					}
				}
				
				if (( pushDetected ) && ( quotesL[lastbarL].low < quotesL[anyDownPush.pushEnd].high))
				{
					warning("Resuming trend sell triggered at " + data.time );
					enterMarketPosition(quotesL[anyDownPush.pushEnd].low);
					return;
				}
			}
			
			return;

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// look at large timeframe 240
			if ( lastbar240 == detect240 )
			{
				if ((quotesL[lastbar240-2].low - quotesL[lastbar240-1].low) < 5 * PIP_SIZE )
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
				
			}
			

			
			
			// entry at tips
			int startL = Utility.findPositionByHour(quotesL, trade.pullBackStartTime, 2 );
			startL = Utility.getHigh( quotesL, startL-4, startL+4).pos;

			PushList pushList = PushSetup.getDown2PushList(quotesL, startL, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));

				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				double triggerPrice = quotesL[lastPush.prePos].low;

				if ( lastbarL == pushList.end )
				{	
					
					if ( lastPush.pullBackSize > 2 * avgBarSize60)
					{
						warning("2 buy triggered: pullbackSize=" + lastPush.pullBackSize + " " +  triggerPrice + quotesL[lastbarL].time +  " averageSize:" + avgBarSize60);
						enterMarketPosition(triggerPrice);
						return;
						//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}

					if (( prevPush != null ) && ( prevPush.breakOutSize <= 8 * PIP_SIZE ))
					{
						warning("2.1 buy triggered " + triggerPrice + quotesL[lastbarL].time);
						enterMarketPosition(triggerPrice);
						return;
						//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}
					
					
					if ( pushSize >= 3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize > 5 * PIP_SIZE )
						{
							warning("3 buy triggered " + triggerPrice + quotesL[lastbarL].time);
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
			
			int lowestPoint = Utility.getLow( quotesL, startL, lastbarL).pos;
			
			if ( data.high > quotesL[startL].high )
			{
				if ((quotesL[startL].high - quotesL[lowestPoint].low) > 2.5 * FIXED_STOP * PIP_SIZE )
				{
					removeTrade();
					return;
				}

				double triggerPrice = quotesL[startL].high;
				System.out.println("Breakout base buy triggered at " + data.time +  " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].high );
				enterMarketPosition(triggerPrice);
				return;
				//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
			}

			Push anyUpPush = TrendAnalysis.detectLatestMomentum( quotesL, lowestPoint, lastbarL-1, Constants.DIRECTION_UP, 1.5 * FIXED_STOP * PIP_SIZE );
			
			if (( anyUpPush != null ) && ( anyUpPush.pushEnd < lastbarL-1))  
			{
				boolean pushDetected = false;
				for ( int j = anyUpPush.pushEnd + 1; j < lastbarL; j++ )
				{
					if ((quotesL[j].high < quotesL[anyUpPush.pushEnd].high) || ( BarUtil.isDownBar(quotesL[j])))
					{
						pushDetected = true;
						break;
					}
				}
				
				if (( pushDetected ) && ( quotesL[lastbarL].high > quotesL[anyUpPush.pushEnd].high))
				{
					warning("Resuming trend buy triggered at " + data.time );
					enterMarketPosition(quotesL[anyUpPush.pushEnd].high);
					return;
				}
			}
		}
		

		return;
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

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Trade Target
	//
	//
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void trackTradeTarget(QuoteData data, int field, double currPrice)
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data,0,0);

		if ( trade != null )
		{	
			if ( trade.status.equals(Constants.STATUS_OPEN))
				checkTradeExpiring_ByTime(data);
			
			if (( trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED)))
			{
				//exit123_new9c4_123( data );  
				exit123_new9c4( data );  
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
				if((trade.limitPrice1 - lowAfterDetect ) > 1.5 * FIXED_STOP * PIP_SIZE )
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
				if (( highAfterDetect - trade.limitPrice1) > 1.5 * FIXED_STOP * PIP_SIZE )
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
	
	
	
	
	
	
	
	
	
	
	
	

	public void exit123_close_monitor( QuoteData data )
	{
		QuoteData[] quote15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quote15.length - 1;
		
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		
		//QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);;
		//int lastbar5 = quotes5.length - 1;
		
		//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		//double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		//QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		//int lastbar1 = quotes1.length - 1;
		//double avgSize1 = BarUtil.averageBarSizeOpenClose( quotes1 );

		MATouch[] maTouches = null;

		
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
			String prevAction = trade.action;
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
		double avgSize60 = BarUtil.averageBarSizeOpenClose( quotes60 );
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

			if (!trade.reach2FixedStop && (profitInPip > 2 * FIXED_STOP)){
				warning("place break even order");
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
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
			if (profitInPip > FIXED_STOP )
				trade.reach1FixedStop = true;

			if (!trade.reach2FixedStop && (profitInPip > 2 * FIXED_STOP)){
				warning("place break even order");
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
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
		

		
		
		if (lastbar60 < tradeEntryPos60 + 2)
		return;

		/*********************************************************************
		 *  status: detect peaks
		 *********************************************************************/
		if (Constants.ACTION_SELL.equals(trade.action)){
			int pushStart = (lastbar60 - tradeEntryPos60 > 4)? tradeEntryPos60+4: lastbar60;
			pushStart = Utility.getHigh(quotes60, tradeEntryPos60-12, pushStart).pos;
			//PushList pushList = PushSetup.getDown2PushList(quotes60, pushStart, lastbar60);
			PushList pushList = PushSetup.findPastLowsByX(quotes60, pushStart, lastbar60, 1);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0)){
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes60[lastPush.prePos].low;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				double pullback = phl.pullBack.high - quotes60[phl.prePos].low;
				int peakwidth = phl.curPos - phl.prePos;
				//pushList.calculatePushMACD( quotes60);
				//int positive = phls[lastPushIndex].pullBackMACDPositive;

				/*TradeEvent tv = new TradeEvent(TradeEvent.NEW_LOW, quotes60[lastbar60].time);
				tv.addNameValue("Low Price", new Double(quotes60[phl.prePos].low).toString());
				tv.addNameValue("NumPush", new Integer(numOfPush).toString());
				trade.addTradeEvent(tv);
				super.strategy.sentAlertEmail(AlertOption.new_low, trade.symbol + " " + trade.action + " pushNum" + numOfPush + " " + quotes60[phl.prePos].low, trade);
				
				if ( timePassed  > 60 ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new low after 60 hours " + quotes60[phl.prePos].low, trade);
				}
				if ( weekday == Calendar.FRIDAY ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new low on friday " + quotes60[phl.prePos].low, trade );
				}*/
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				/*if (!trade.finalPeakExisting &&( pullback  > 2 * FIXED_STOP * PIP_SIZE) && ( timePassed  > 84 ) && ( peakwidth < 12 )) 
				{
					double exitTargetPrice =  adjustPrice((triggerPrice - FIXED_STOP/2 * PIP_SIZE ), Constants.ADJUST_TYPE_DOWN);
					createTradeTargetOrder( trade.remainingPositionSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}*/
				if (!trade.finalPeakExisting &&  ( timePassed  > 24 ) && ( phl.pullBack.high > trade.triggerPrice )){ 
					closePositionByMarket( trade.remainingPositionSize, triggerPrice);
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
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
				//pushList.calculatePushMACD( quotes60);
				//int negatives = phls[lastPushIndex].pullBackMACDNegative;

				/*TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH, quotes60[lastbar60].time);
				tv.addNameValue("High Price", new Double(quotes60[phl.prePos].high).toString());
				tv.addNameValue("NumPush", new Integer(numOfPush).toString());
				super.strategy.sentAlertEmail(AlertOption.new_high, trade.symbol + " " + trade.action + " pushNum" + numOfPush + " " + quotes60[phl.prePos].high, trade);

				if ( timePassed  > 60 ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new high after 60 hours " + quotes60[phl.prePos].high, trade);
				}
				if ( weekday == Calendar.FRIDAY ){ 
					super.strategy.sentAlertEmail(AlertOption.take_profit, trade.symbol + " new high on friday " + quotes60[phl.prePos].high, trade);
				}*/
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				
				/*if (!trade.finalPeakExisting && ( pullback  > 2 * FIXED_STOP * PIP_SIZE) && ( timePassed  > 84 ) && ( peakwidth < 12 )) 
				{
					double exitTargetPrice =  adjustPrice((triggerPrice + FIXED_STOP/2 * PIP_SIZE ), Constants.ADJUST_TYPE_UP);
					createTradeTargetOrder( trade.remainingPositionSize, exitTargetPrice );
					trade.finalPeakExisting = true;
				}*/
				if (!trade.finalPeakExisting &&  ( timePassed  > 24 ) && ( phl.pullBack.low < trade.triggerPrice )){ 
					closePositionByMarket( trade.remainingPositionSize, triggerPrice);
					return;
				}
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

					/*
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}*/

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
			if (trade.reach2FixedStop == false)
			{
				if/* (((( Constants.DIRT_UP.equals(bt.direction) ||  Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
					&& ((trade.entryPrice - quotes[lastbar].low) >  FIXED_STOP * PIP_SIZE))
					||*/ ((trade.entryPrice - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE) 	
				{
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
	
	protected Trade findTradeHistory(String action, double triggerPrice)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ( t.action.equals(action) && (triggerPrice == t.triggerPrice))
				return t;
		}

		return null;

	}

	protected Trade findTradeHistory(int detectPosStart, int detectPosEnd)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ((( detectPosStart >= t.detectPosStart) && ( detectPosStart <= t.detectPosEnd )) ||
			     (( detectPosEnd >= t.detectPosStart) && ( detectPosEnd <= t.detectPosEnd )))	
			return t;
		}

		return null;

	}

	protected int findLatestTradeFromTradeHistory(String action)
	{
		int triggerPos = 0;
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext()){
			Trade t = it.next();
			if ( t.action.equals(action) && (t.triggerPos > triggerPos))
				triggerPos = t.triggerPos;
		}

		return triggerPos;

	}

	
	
	protected Trade findTradeHistory(String action, String type, String detectTime)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ( t.action.equals(action) && t.type.equals(type) && t.detectTime.equals(detectTime))
				return t;
		}

		return null;

	}

	protected Trade findTradeHistory(String action, QuoteData trendBar)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ( t.action.equals(action) && (trendBar.equals(t.trendBar)))
				return t;
		}

		return null;

	}

	protected Trade findTradeHistory(String detectTime)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if (detectTime.equals(t.detectTime))
				return t;
		}

		return null;

	}
	
	protected boolean findTradeHistoryWithTrendStartingTime(String action, String trendStartTime)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		int count = 0;
		while (it.hasNext())
		{
			Trade t = it.next();
			if ( t.action.equals(action) && (trendStartTime.equals(t.trendStartTime)))
				count++;
		}

		if ( count > 1 )
			return true;
		else
			return false;
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


	public void trackEntryUsingConfluenceIndicator(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		
		ReturnToMADAO returnToMA = (ReturnToMADAO)trade.setupDAO;
		double[] ema = Indicator.calculateEMA(quotes240, returnToMA.getMa());
		
		System.out.println("traking trade:" + trade.symbol);
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			//warning( "PullbackSTartTime :" + trade.getPullBackStartTime());
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;
			//warning( "Pullback start position is :" + quotes60[startL].time);

			if ( lastbar240 == returnToMA.getEndPos() + 2 )
			{
				if ((quotes240[returnToMA.getEndPos() + 1].open > ema[returnToMA.getEndPos() + 1]) && BarUtil.isUpBar(quotes240[returnToMA.getEndPos() + 1]))
				{
					removeTrade();
					return;
				}
			}
			/*
			else if ( lastbar240 > returnToMA.getEndPos() + 3 )
			{
				for ( int i = returnToMA.getEndPos() +3; i < lastbar240 - 3; i++)
				{
					if ((quotes240[i].low > ema[i]) && ( quotes240[i+1].low > ema[i+1]) && ( quotes240[i+2].low > ema[i+2] ))
					{
						info(quotes240[lastbar240].time + " Two bars above, trade removed" );
						trade = null;
						return;
					}
				}
			}*/
			
			// run push setup
			PushList pushList = PushSetup.getUp2PushList( quotes60, startL, lastbar60 );
			//if ( pushList != null )
				//warning( trade.action + " EntryPushes:" + pushList.toString());
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
					double triggerPrice = lastPush.pullBack.low;// data.close

					if ( realtime_count < 3 )
					{
						removeTrade();
						return;
					}
					
					warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					warning("LastPush: " + pushList.toString(quotes60, PIP_SIZE));
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
					//double avgBarSize60 = BarUtil.averageBarSize2(quotes60);
					
					if (lastPush.pullBackSize > 2 * FIXED_STOP * PIP_SIZE)
					{
						double triggerPrice = adjustPrice(quotes60[lastPush.prePos].high, Constants.ADJUST_TYPE_UP);
						//enterMarketPosition(triggerPrice);
						//warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
						//createTradeLimitOrder( POSITION_SIZE/2, triggerPrice );
						//createTradeLimitOrder( POSITION_SIZE/2, triggerPrice + 0.5 * FIXED_STOP * PIP_SIZE );
						
						/* tempoilary disable this
						IncrementalEntry ie = new IncrementalEntry(Constants.ACTION_SELL, Constants.CHART_60_MIN, quotes60[startL].time, POSITION_SIZE/2);
						trade.entryMode = Constants.ENTRY_MODE_INCREMENTAL;
						trade.entryDAO = ie;		
						return;*/
					}
					else if (lastPush.pullBackSize > FIXED_STOP * PIP_SIZE)
					{
						double totalPullBack = quotes60[lastPush.prePos].high - quotes60[startL].low;
						if ( lastPush.pullBackSize > 0.618 * totalPullBack )
						{
							/* tempoilary disable this
							IncrementalEntry ie = new IncrementalEntry(Constants.ACTION_SELL, Constants.CHART_60_MIN, quotes60[startL].time, POSITION_SIZE/2);
							trade.entryMode = Constants.ENTRY_MODE_INCREMENTAL;
							trade.entryDAO = ie;		
							return;*/
						}
					}
				}
			}
			
			if ( data.low < quotes60[startL].low )
			{
				double triggerPrice = data.close;//quotes60[startL].low;

				if ( realtime_count < 3 )
				{
					removeTrade();
					return;
				}
				/*
				QuoteData[] quotes = getQuoteData(Constants.CHART_1_MIN);
				int lastbar = quotes.length - 1;
				if (quotes[lastbar-1].low < triggerPrice) 
				{
					warning( "Remove trade 11:" + quotes[lastbar].time);
					removeTrade();
					return;
				}*/
				
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time );
				enterMarketPosition(triggerPrice);
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
			//warning( "PullbackSTartTime :" + trade.getPullBackStartTime());
			startL = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;
			//warning( "Pullback start position is :" + quotes60[startL].time);
			// entry at tips

			if ( lastbar240 == returnToMA.getEndPos() + 2 )
			{
				if ((quotes240[returnToMA.getEndPos() + 1].open < ema[returnToMA.getEndPos() + 1]) && BarUtil.isDownBar(quotes240[returnToMA.getEndPos() + 1]))
				{	
					removeTrade();
					return;
				}
			}
			/*
			else if ( lastbar240 > returnToMA.getEndPos() + 3 )
			{
				for ( int i = returnToMA.getEndPos() +3; i < lastbar240 - 3; i++)
				{
					if ((quotes240[i].high < ema[i]) && ( quotes240[i+1].high < ema[i+1]) && ( quotes240[i+2].high < ema[i+2] ))
					{
						info(quotes240[lastbar240].time + " Two bars below, trade removed" );
						trade = null;
						return;
					}
				}
			}*/

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
					double triggerPrice = lastPush.pullBack.high;  // data.close
					if ( realtime_count < 3 )
					{
						removeTrade();
						return;
					}
					
					warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
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
					//double avgBarSize60 = BarUtil.averageBarSize2(quotes60);
					
					if ( lastPush.pullBackSize > 2 * FIXED_STOP * PIP_SIZE)
					{
						double triggerPrice = adjustPrice(quotes60[lastPush.prePos].low, Constants.ADJUST_TYPE_DOWN);
						//warning("Pullback trade sell triggered on large pull back  @ " + quotes60[lastbar60].time );
						//enterMarketPosition(triggerPrice);
						//enterPartialPosition( triggerPrice, POSITION_SIZE/2);
						//warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
						//createTradeLimitOrder( POSITION_SIZE/2, triggerPrice );
						//createTradeLimitOrder( POSITION_SIZE/2, triggerPrice - 0.5 * FIXED_STOP * PIP_SIZE );

						/* tempoilary disable this
						IncrementalEntry ie = new IncrementalEntry(Constants.ACTION_BUY, Constants.CHART_60_MIN, quotes60[startL].time, POSITION_SIZE/2);
						trade.entryMode = Constants.ENTRY_MODE_INCREMENTAL;
						trade.entryDAO = ie;		
						return;*/
					}
					else if (lastPush.pullBackSize > FIXED_STOP * PIP_SIZE)
					{
						double totalPullBack = quotes60[startL].high - quotes60[lastPush.prePos].low ;
						if ( lastPush.pullBackSize > 0.618 * totalPullBack )
						{
							/* tempoilary disable this
							IncrementalEntry ie = new IncrementalEntry(Constants.ACTION_BUY, Constants.CHART_60_MIN, quotes60[startL].time, POSITION_SIZE/2);
							trade.entryMode = Constants.ENTRY_MODE_INCREMENTAL;
							trade.entryDAO = ie;		
							return;*/
						}
					}

				}
			}
			
			if ( data.high > quotes60[startL].high )
			{
				double triggerPrice = data.close;//quotes60[startL].high;
				if ( realtime_count < 3 )
				{
					removeTrade();
					return;
				}/*
				QuoteData[] quotes = getQuoteData(Constants.CHART_1_MIN);
				int lastbar = quotes.length - 1;
				if (quotes[lastbar-1].high > triggerPrice) 
				{
					warning( "Remove trade 22:" + quotes[lastbar].time);
					removeTrade();
					return;
				}*/

				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time );
				enterMarketPosition(triggerPrice);
				return;
			}

			
		}
		

		return;
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
	


}


