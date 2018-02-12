package tao.trading.strategy;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.BreakOut;
import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MACD;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.Reversal123;
import tao.trading.Trade;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.TrendLine;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

public class SF301TradeManager extends TradeManager2 implements Serializable
{
	HashMap<String, QuoteData>[] qts = new HashMap[TOTAL_CHARTS];
	boolean CNT = false;
	int TARGET_PROFIT_SIZE;
    String TIME_ZONE;
    String TRADES;
	boolean oneMinutChart = false;
	String tradeZone1, tradeZone2;
	double minTarget1, minTarget2;
	HashMap<Integer, Integer> profitTake = new HashMap<Integer, Integer>();
	String currentTime;
	int DEFAULT_RETRY = 0;
	boolean test = false;
	boolean larger_timeframe_restriction = false;
	int QUICK_PROFIT = 25;
	int LOCKIN_PROFIT = 40;
	int SHALLOW_PROFIT_REVERSAL = 40;
	protected SF301TradeManager largerTimeFrameTraderManager, smallTimeFrameTraderManager;
	boolean breakOutCalculated = false;
	boolean toBreakOutUp = false;
	boolean toBreakOutDown = false;
	boolean timeUp = false;
	int FIXED_STOP_REV,FIXED_STOP_SLM,FIXED_STOP_PBK, FIXED_STOP_RVS, FIXED_STOP_20B;
	Trade REVTrade, PBKTrade, SLMTrade, RVSTrade;
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");

	public Vector<Integer> detectHistory = new Vector<Integer>();

	// important switch
	boolean prremptive_limit = false;
	boolean breakout_limit = false;
	boolean reverse_trade = false; // reverse after stopped out
	boolean rsc_trade = false; // do a rsc trade instead of rst if there is
								// consective 3 bars
	boolean reverse_after_profit = false; // reverse after there is a profit
	boolean after_target_reversal = false;

	boolean LIMIT_ORDER = true;
	
	
	public SF301TradeManager()
	{
		super();
	}

	public SF301TradeManager(String account, EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super(account, client, contract, symbolIndex, logger);
	}

	public QuoteData[] getQuoteData()
	{
		Object[] quotes = this.qts.values().toArray();
		Arrays.sort(quotes);
		return Arrays.copyOf(quotes, quotes.length, QuoteData[].class);
	}

	boolean inTrade( String T )
	{
		if ( TRADES.indexOf(T) != -1 )
			return true;
		else
			return false;
	}
	
	boolean inREVTrade(){ return inTrade("R"); }
	boolean inSLMTrade(){ return inTrade("S"); }
	boolean inRVSTrade(){ return inTrade("V"); }
	boolean inPBKTrade(){ return inTrade("P"); }
	boolean in20BTrade(){ return inTrade("2"); }
	boolean inRBKTrade(){ return inTrade("K"); }
	
	
	public void closePositionByMarket(int posSize, double currentPrice)
	{
		String action = trade.action.equals(Constants.ACTION_BUY) ? Constants.ACTION_SELL : Constants.ACTION_BUY;

		// this is to place the real order
		placeMktOrder(action, posSize);

		trade.remainingPositionSize -= posSize;

		if (trade.remainingPositionSize == 0)
		{
			if (trade.stopId != 0)
				cancelOrder(trade.stopId);
			tradeHistory.add(trade);
			trade = null;
		}
	}

	public void closePositionByLimit(String time, double limitPrice, int positionSize)
	{
		if (trade.profitTake1 == true )
		{
			cancelOrder( trade.targetId);
		}
		
		String action = trade.action.equals("BUY") ? "SELL" : "BUY";
		trade.targetId = placeLmtOrder(action, limitPrice, positionSize, null);
		trade.profitTake1 = true;

	}

	
	public void cancelTargets()
	{
		if (MODE == Constants.REAL_MODE)
		{
			if (trade.targetId1 != 0)
			{
				cancelOrder(trade.targetId1);
				trade.targetId1 = 0;
			}
			if (trade.targetId2 != 0)
			{
				cancelOrder(trade.targetId2);
				trade.targetId2 = 0;
			}
		}
		else if (MODE == Constants.TEST_MODE)
		{
			trade.targetId1 = trade.targetId2 = 0;
		}
	}

	
	public void cancelStop()
	{
		if (MODE == Constants.REAL_MODE)
		{
			if (trade.stopId != 0)
			{
				cancelOrder(trade.stopId);
				trade.stopId = 0;
			}
		}
		else if (MODE == Constants.TEST_MODE)
		{
			trade.stopId = 0;
		}
	}
	
	
	
	
	public void createOpenTrade(String tradeType, String action)
	{
		Trade ttrade = new Trade(symbol);
		trade.type = tradeType;
		trade.action = action;
		trade.POSITION_SIZE = POSITION_SIZE;
		trade.status = Constants.STATUS_OPEN;
	}
	
	public Trade createTrade(String tradeType, String action, int fixedStop)
	{
		Trade t = new Trade(symbol);
		t.type = tradeType;
		t.action = action;
		t.POSITION_SIZE = POSITION_SIZE;
		t.status = Constants.STATUS_OPEN;
		return t;
	}

	

	

	// /////////////////////////////////////////////////////////
	//
	// Check Stop
	//
	// /////////////////////////////////////////////////////////
	public void checkOrderFilled(int orderId, int filled)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if (orderId == trade.stopId)
		{
			logger.warning(symbol + " " + CHART + " minute " + filled + " stopped out ");
			trade.stoppedOutPos = ((QuoteData) quotes[lastbar]).pos;
			trade.stopId = 0;

			cancelTargets();
			
			removeTrade();
			//processAfterHitStopLogic_c();

		}
		else if ((orderId == trade.limitId1) && ( trade.limitPos1Filled == 0 ))  // avoid sometime same message get sent twoice
		{
			logger.warning(symbol + " " + CHART + " minute " + " limit order: " + orderId + " " + filled + " filled");

			trade.limitPos1Filled = trade.limitPos1;
			trade.entryPrice = trade.limitPrice1;
			trade.remainingPositionSize = trade.limitPos1; //+= filled;
			trade.entryTime = quotes[lastbar].time;
			//trade.entryPos = lastbar;
			//trade.entryPosL = lastbarL;

			//if (trade.stopId != 0)
			//	cancelOrder(trade.stopId);

			// calculate stop here
			//trade.stop = trade.limit1Stop;
			//if (Constants.ACTION_SELL.equals(trade.action))
			//	trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			//else if (Constants.ACTION_BUY.equals(trade.action))
			//	trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

			//trade.remainingPositionSize = trade.positionSize;
			trade.status = Constants.STATUS_PLACED;

		
		}
		else if ((orderId == trade.limitId2)&& ( trade.limitPos2Filled == 0 ))
		{
			logger.warning(symbol + " " + CHART + " minute " + " limit order2: " + orderId + " " + filled + " filled");
			
			trade.limitPos2Filled = trade.limitPos2;
			trade.entryPrice = trade.limitPrice2;
			trade.remainingPositionSize += filled;
			trade.entryTime = quotes[lastbar].time;
			//trade.entryPos = lastbar;
			//trade.entryPosL = lastbarL;

			if (trade.stopId != 0)
				cancelOrder(trade.stopId);

			// calculate stop here
			trade.stop = trade.limit2Stop;
			if (Constants.ACTION_SELL.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			else if (Constants.ACTION_BUY.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

			if (trade.remainingPositionSize == trade.POSITION_SIZE)
				trade.status = Constants.STATUS_PLACED;
		}
		else if (orderId == trade.targetId1)
		{
			logger.warning(symbol + " " + CHART + " minute target1 filled, " + filled + " closed @ " + trade.targetPrice);
			trade.targetId1 = 0;
			//trade.targetReached = true;
			//trade.remainingPositionSize -= filled;

			cancelStop();
			cancelProfits();
			
			/*
			logger.warning(symbol + " " + CHART + " remainning position is " + trade.remainingPositionSize);
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}*/
			removeTrade();
			return;
		}
		else if (orderId == trade.takeProfit1Id)
		{
			logger.warning(symbol + " " + CHART + " minute take profit 1 filled, " + filled + " closed @ " + trade.targetPrice);
			trade.takeProfit1Id = 0;
			trade.remainingPositionSize -= trade.takeProfit1PosSize;

			cancelStop();
			
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else
			{
				removeTrade();
			}
			
			return;
		}
		else if (orderId == trade.takeProfit2Id)
		{
			logger.warning(symbol + " " + CHART + " minute take profit 1 filled, " + filled + " closed @ " + trade.targetPrice);
			trade.takeProfit2Id = 0;
			trade.remainingPositionSize -= trade.takeProfit2PosSize;

			cancelStop();
			
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else
			{
				removeTrade();
			}
			
			return;
		}
	}


	public void checkStopTarget(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check entry limit;
			if ((trade.limitId1 != 0) )
			{
				if (data.high >= trade.limitPrice1)
				{
					logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
					if (trade.recordOpened == false)
					{
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
					}
					AddOpenRecord(data.time, Constants.ACTION_SELL, trade.limitPos1, trade.limitPrice1);
					trade.limitPos1Filled = trade.limitPos1;
	
					trade.entryPrice = trade.limitPrice1;
					trade.remainingPositionSize += trade.limitPos1Filled;
					trade.entryTime =  quotes[lastbar].time;
					//trade.entryPos = lastbar;
					//trade.entryPosL = lastbarL;
	
					// calculate stop here
					trade.stop = trade.limit1Stop;
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.limitId1 = 0;
	
					if (trade.remainingPositionSize == trade.POSITION_SIZE)
						trade.status = Constants.STATUS_PLACED;
				}
				
				int placeTimePos = Utility.findPositionByHour( quotes, trade.limit1PlacedTime, Constants.FRONT_TO_BACK);
				if (( lastbar - placeTimePos ) > 32 )
				{
					logger.warning(symbol + " " + CHART + " limit order placed too long, trade cancelled");
					trade = null;
					return;
				}

			}

			if ((trade.limitId2 != 0) && (data.high >= trade.limitPrice2))
			{
				logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice2 + " filled " + ((QuoteData) quotes[lastbar]).time);
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.limitPos2, trade.limitPrice2);
				trade.limitPos2Filled = trade.limitPos2;

				trade.entryPrice = trade.limitPrice2;
				trade.remainingPositionSize += trade.limitPos2Filled;
				trade.entryTime = quotes[lastbar].time;
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				if (trade.stopId != 0)
					cancelOrder(trade.stopId);
				// calculate stop here
				trade.stop = trade.limit2Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				trade.limitId2 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;
			}

			// check stop;
			if ((trade.stopId != 0) && (data.high > trade.stop))
			{
				logger.warning(symbol + " " + CHART + " minute stopped out @ data.high:" + data.high + " " + data.time + " stop is" + trade.stop);
				trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);
				trade.stopId = 0;

				trade.preStopPrice = trade.stop;
				cancelTargets();

				//removeTrade();
				processAfterHitStopLogic_c();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.low < trade.targetPrice1))
			{
				logger.info(symbol + " target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				cancelStop();
				cancelProfits();
				
				//trade.remainingPositionSize -= trade.targetPos1;
				//if (trade.remainingPositionSize <= 0)
				removeTrade();
				return;

			}

			if ((trade.takeProfit1Id != 0) && (data.low < trade.takeProfit1Price))
			{
				logger.info(symbol + " take profit 1 hit, close " + trade.takeProfit1PosSize + " @ " + trade.takeProfit1Price);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos1, trade.targetPrice1);
				trade.takeProfit1Id = 0;

				cancelStop();
				trade.remainingPositionSize = trade.remainingPositionSize - trade.takeProfit1PosSize;
				
				if (trade.remainingPositionSize > 0)
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				else
				{
					removeTrade();
				}
			}
			
			if ((trade.takeProfit2Id != 0) && (data.low < trade.takeProfit2Price))
			{
				logger.info(symbol + " take profit 2 hit, close " + trade.takeProfit2PosSize + " @ " + trade.takeProfit2Price);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos2, trade.targetPrice2);
				trade.takeProfit2Id = 0;

				cancelStop();
				trade.remainingPositionSize = trade.remainingPositionSize - trade.takeProfit2PosSize;
				
				if (trade.remainingPositionSize > 0)
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				else
				{
					removeTrade();
				}
			}
			

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// check entry limit;
			if ((trade.limitId1 != 0) && (data.low <= trade.limitPrice1))
			{
				logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.limitPos1, trade.limitPrice1);
				trade.limitPos1Filled = trade.limitPos1;

				trade.entryPrice = trade.limitPrice1;
				trade.remainingPositionSize += trade.limitPos1Filled;
				trade.entryTime = quotes[lastbar].time;
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

			if ((trade.limitId2 != 0) && (data.high <= trade.limitPrice2))
			{
				logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice2 + " filled " + ((QuoteData) quotes[lastbar]).time);
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.limitPos2, trade.limitPrice2);
				trade.limitPos2Filled = trade.limitPos2;

				trade.entryPrice = trade.limitPrice2;
				trade.remainingPositionSize += trade.limitPos2Filled;
				trade.entryTime = quotes[lastbar].time;
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				if (trade.stopId != 0)
					cancelOrder(trade.stopId);
				// calculate stop here
				trade.stop = trade.limit2Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
						trade.remainingPositionSize, null);
				trade.limitId2 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;

			}

			// check stop;
			if ((trade.stopId != 0) && (data.low < trade.stop))
			{
				logger.warning(symbol + " " + CHART + " minute stopped out @ data.low:" + data.low + " " + data.time);
				trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.stop);
				trade.stopId = 0;

				trade.preStopPrice = trade.stop;
				cancelTargets();

				processAfterHitStopLogic_c();
				//removeTrade();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.high > trade.targetPrice1))
			{
				logger.info(symbol + " target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				cancelStop();
				cancelProfits();
				
				//trade.remainingPositionSize -= trade.targetPos1;
				//if (trade.remainingPositionSize <= 0)
				removeTrade();
				return;
			}

			if ((trade.takeProfit1Id != 0) && (data.high > trade.takeProfit1Price))
			{
				logger.info(symbol + " take profit 1 hit, close " + trade.takeProfit1PosSize + " @ " + trade.takeProfit1Price);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos1, trade.targetPrice1);
				trade.takeProfit1Id = 0;

				cancelStop();
				trade.remainingPositionSize = trade.remainingPositionSize - trade.takeProfit1PosSize;
				
				if (trade.remainingPositionSize > 0)
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				else
				{
					removeTrade();
				}
			}
			
			if ((trade.takeProfit2Id != 0) && (data.high > trade.takeProfit2Price))
			{
				logger.info(symbol + " take profit 2 hit, close " + trade.takeProfit2PosSize + " @ " + trade.takeProfit2Price);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos2, trade.targetPrice2);
				trade.takeProfit2Id = 0;

				cancelStop();
				trade.remainingPositionSize = trade.remainingPositionSize - trade.takeProfit2PosSize;
				
				if (trade.remainingPositionSize > 0)
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				else
				{
					removeTrade();
				}
			}
		}
	}



	
	void processAfterHitStopLogic_c()
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		Object[] quotesL = this.largerTimeFrameTraderManager.getQuoteData();

		double prevStop = trade.stop;
		String prevAction = trade.action;
		String prevType = trade.type;
		boolean preStopAdjusted = trade.stopAdjusted;
		
		removeTrade();
		if ((reverse_trade == false) || (prevType == Constants.TRADE_CNT) || (preStopAdjusted == true ))
		{
			return;
		}

		if (Constants.ACTION_SELL.equals(prevAction))
		{
				//int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				//QuoteData lowAfterEntry = Utility.getLow(quotes, tradeEntryPos, lastbar);

				logger.warning(symbol + " " + CHART + " place reverse order at " + ((QuoteData) quotes[lastbar]).time + " @ " + prevStop);
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
				trade.detectTime = quotes[lastbar].time;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = trade.price = prevStop;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;

				CreateTradeRecord(trade.type, Constants.ACTION_BUY);
				AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);

				trade.stop = trade.price - FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());
				return;
			}
			else if (Constants.ACTION_BUY.equals(prevAction))
			{
				logger.warning(symbol + " " + CHART + " place reverse order at " + ((QuoteData) quotes[lastbar]).time + " @ " + prevStop);
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
				trade.detectTime = quotes[lastbar].time;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = trade.price = prevStop;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;

				CreateTradeRecord(trade.type, Constants.ACTION_SELL);
				AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);

				trade.stop = trade.price + FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());
				return;
		}

	}

	
	
	
	
	protected void setMktTrade(String type, String action, int positionSize, double price)
	{
		trade.type = type;
		trade.action = action;
		trade.POSITION_SIZE = positionSize;

		trade.price = price;
		trade.entryPrice = trade.price;

		logger.warning(symbol + " " + CHART + " place market " + action + " order");
		trade.orderId = placeMktOrder(action, trade.POSITION_SIZE);

		trade.status = Constants.STATUS_PLACED;

		trade.remainingPositionSize = trade.POSITION_SIZE;

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
				checkTriggerMarketSell(price);
			}
			else if (Constants.ACTION_BUY.equals( trade.action))
			{
				checkTriggerMarketBuy(price);
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
	public void checkRBKTradeSetup2(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
	/*
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 5);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 5);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		*/

		//if ((direction == Constants.DIRECTION_UP) && (quotesL[lastbarL].low > ema20L[lastbarL]))
		if (quotesL[lastbarL].high > ema20L[lastbarL])
		{
			//int startL = upPos;
			int pushStartL = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 5);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 5;

			pushStartL = Utility.getLow( quotesL, pushStartL-5, pushStartL).pos;
			
			PushHighLow[] phls = Pattern.findPast2Highs(quotesL, pushStartL, lastbarL );
			//PushHighLow phl = findLastNHigh( quotes, pushStartL, lastbarL, 1 );

			if ( phls != null )
			{
				if ( phls.length > 0 )
				{
					// 1.  at least three with MCAD diverage
					// wide range, requires a MACD diverage
					PushHighLow phl = phls[0];
					for ( int i = phl.prePos+1 ; i < lastbarL; i++)
					{
						if ( quotesL[i].high > quotesL[lastbarL].high)
							return;
					}

					MACD[] macd = Indicator.calculateMACD( quotesL );
					int negatives = 0;
					for ( int j = phl.prePos; j <= phl.curPos; j++)
					{
						if ( macd[j].histogram < 0 )
							negatives ++;
					}
					
					
					if ( negatives > 0 )
					{
						//System.out.println(symbol + " " + CHART + " " + " trend starting point is " + ((QuoteData)quotesL[pushStartL]).time);
						System.out.print(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " lastbar: " + quotesL[lastbarL].time);
/*			    	    System.out.println(symbol + " " + CHART + " " + data.time + " " +
								 ((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
								 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
								 " pullback@" + phl.pullBack.time);
	*/					
						
						if (( data.close - quotesL[phl.prePos].high) > 5 * PIP_SIZE)
								return;
						
			    	    Trade tt = createTrade(Constants.TRADE_REV, Constants.ACTION_BUY, FIXED_STOP_REV);
						tt.detectTime = quotesL[lastbarL].time;
						tt.pushStartL = pushStartL;
						tt.pullBackPos = phl.pullBack.pos;
						tt.POSITION_SIZE = POSITION_SIZE;
						if (prevTradeExist(tt.type, tt.pullBackPos))
							return;

						tt.phl = phl;
						tt.status = Constants.STATUS_OPEN;
						tt.triggerPosL = lastbarL;
						tt.triggerPos = lastbar;
						if ( getTrade() == null )
							//enterMarketPosition(tt, quotesL[phl.prePos].high);
							enterMarketPosition(tt, data.close);
					}
				}
			}
		}
		else if (quotesL[lastbarL].low < ema20L[lastbarL])
		{
			//int startL = downPos;
			int pushStartL = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 5);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 5;
			
			pushStartL = Utility.getHigh( quotesL, pushStartL-5, pushStartL).pos;

			PushHighLow[] phls = Pattern.findPast2Lows(quotesL, pushStartL, lastbarL );
			//PushHighLow phl = findLastNLow( quotes, pushStartL, lastbarL, 1 );
			
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
				if (data.time.equals("20110610  02:03:00"))
					System.out.println("here");
				
				if ( phls.length > 0 )
				{
					// 1.  at least three with MCAD diverage
					PushHighLow phl = phls[0];
					for ( int i = phl.prePos+1 ; i < lastbarL; i++)
					{
						if ( quotesL[i].low < quotesL[lastbarL].low)
							return;
					}

					
					
					MACD[] macd = Indicator.calculateMACD( quotesL );
					int positive = 0;
					for ( int j = phl.prePos; j <= phl.curPos; j++)
					{
						if ( macd[j].histogram > 0 )
							positive ++;
					}
					
					if ( positive > 0 )
					{
						if (( quotesL[phl.prePos].low - data.close) > 5 * PIP_SIZE)
							return;

						System.out.print(symbol + " " + CHART + " " + " sell detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " lastbar: " + quotesL[lastbarL].time);

						Trade tt = createTrade(Constants.TRADE_REV, Constants.ACTION_SELL, FIXED_STOP_REV);
						tt.detectTime = quotesL[lastbarL].time;
						tt.pushStartL = pushStartL;
						tt.pullBackPos = phl.pullBack.pos;
						tt.POSITION_SIZE = POSITION_SIZE;
						if (prevTradeExist(tt.type, tt.pullBackPos))
							return;
						
						tt.phl = phl;
						logger.warning(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time );
						tt.status = Constants.STATUS_OPEN;
						tt.triggerPosL = lastbarL;
						tt.triggerPos = lastbar;
						//enterMarketPosition(tt, quotesL[phl.prePos].low);
						enterMarketPosition(tt, data.close);
						return;
					}
				}
			}
		}
	}

	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public void checkRBKTradeSetup(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 5);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 5);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_UP) && (quotesL[lastbarL].low > ema20L[lastbarL]))
		{
			int startL = upPos;
			int pushStartL = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 5);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 5;

			pushStartL = Utility.getLow( quotesL, pushStartL-5, pushStartL).pos;
			
			PushHighLow[] phls = Pattern.findPast2Highs(quotesL, pushStartL, lastbarL );
			//PushHighLow phl = findLastNHigh( quotes, pushStartL, lastbarL, 1 );

			if ( phls != null )
			{
				if ( phls.length > 0 )
				{
					// 1.  at least three with MCAD diverage
					// wide range, requires a MACD diverage
					PushHighLow phl = phls[0];
					for ( int i = phl.prePos+1 ; i < lastbarL; i++)
					{
						if ( quotesL[i].high > quotesL[lastbarL].high)
							return;
					}

					MACD[] macd = Indicator.calculateMACD( quotesL );
					int negatives = 0;
					for ( int j = phl.prePos; j <= phl.curPos; j++)
					{
						if ( macd[j].histogram < 0 )
							negatives ++;
					}
					
					
					if ( negatives > 0 )
					{
						//System.out.println(symbol + " " + CHART + " " + " trend starting point is " + ((QuoteData)quotesL[pushStartL]).time);
						System.out.print(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " lastbar: " + quotesL[lastbarL].time);
/*			    	    System.out.println(symbol + " " + CHART + " " + data.time + " " +
								 ((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
								 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
								 " pullback@" + phl.pullBack.time);
	*/					
						
						if (( data.close - quotesL[phl.prePos].high) > 5 * PIP_SIZE)
							return;

						Trade tt = createTrade(Constants.TRADE_REV, Constants.ACTION_BUY, FIXED_STOP_REV);
						tt.detectTime = quotesL[lastbarL].time;
						tt.pushStartL = pushStartL;
						tt.pullBackPos = phl.pullBack.pos;
						tt.POSITION_SIZE = POSITION_SIZE;
						if (prevTradeExist(tt.type, tt.pullBackPos))
							return;

						tt.phl = phl;
						tt.status = Constants.STATUS_OPEN;
						tt.triggerPosL = lastbarL;
						tt.triggerPos = lastbar;
						if ( getTrade() == null )
							enterMarketPosition(tt, quotesL[phl.prePos].high);
					}
				}
			}
		}
		else if ((direction == Constants.DIRECTION_DOWN) && (quotesL[lastbarL].high < ema20L[lastbarL]))
		{
			int startL = downPos;
			int pushStartL = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 5);
			if (pushStartL == Constants.NOT_FOUND)
				pushStartL = 5;
			
			pushStartL = Utility.getHigh( quotesL, pushStartL-5, pushStartL).pos;

			PushHighLow[] phls = Pattern.findPast2Lows(quotesL, pushStartL, lastbarL );
			//PushHighLow phl = findLastNLow( quotes, pushStartL, lastbarL, 1 );
			
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
				if (data.time.equals("20110610  02:03:00"))
					System.out.println("here");
				
				if ( phls.length > 0 )
				{
					// 1.  at least three with MCAD diverage
					PushHighLow phl = phls[0];
					for ( int i = phl.prePos+1 ; i < lastbarL; i++)
					{
						if ( quotesL[i].low < quotesL[lastbarL].low)
							return;
					}

					
					
					MACD[] macd = Indicator.calculateMACD( quotesL );
					int positive = 0;
					for ( int j = phl.prePos; j <= phl.curPos; j++)
					{
						if ( macd[j].histogram > 0 )
							positive ++;
					}
					
					if ( positive > 0 )
					{
						if (( quotesL[phl.prePos].low - data.close) > 5 * PIP_SIZE)
							return;

						System.out.print(symbol + " " + CHART + " " + " sell detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " lastbar: " + quotesL[lastbarL].time);

						Trade tt = createTrade(Constants.TRADE_REV, Constants.ACTION_SELL, FIXED_STOP_REV);
						tt.detectTime = quotesL[lastbarL].time;
						tt.pushStartL = pushStartL;
						tt.pullBackPos = phl.pullBack.pos;
						tt.POSITION_SIZE = POSITION_SIZE;
						if (prevTradeExist(tt.type, tt.pullBackPos))
							return;
						
						tt.phl = phl;
						logger.warning(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time );
						tt.status = Constants.STATUS_OPEN;
						tt.triggerPosL = lastbarL;
						tt.triggerPos = lastbar;
						enterMarketPosition(tt, quotesL[phl.prePos].low);
						return;
					}
				}
			}
		}
	}

	
	
	

	
	public Trade checkChangeOfDirection(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		//double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		
		if (( quotesL[lastbarL-1].open > quotesL[lastbarL-1].close) && ( ema20L[lastbarL-2] > ema50L[lastbarL-2]))
		{
			if (( quotesL[lastbarL-1].open - quotesL[lastbarL-1].close) < FIXED_STOP / 2 * PIP_SIZE )
				return null;
			
			int start = Pattern.findLastMACrossUp(ema20L, ema50L, lastbarL-2, 5);
			
			if ( lastbarL - start < 48 )
				return null;

			PushHighLow[] phls = Pattern.findPast2Highs(quotesL, start, lastbarL-2 );

			if ( phls != null ) 
			{
				System.out.println(symbol + " Change of Direction SELL detect at " + quotesL[lastbarL-1].time);
			}
		}
		else if (( quotesL[lastbarL-1].open < quotesL[lastbarL-1].close) && ( ema20L[lastbarL-2] < ema50L[lastbarL-2]))
		{
			if (( quotesL[lastbarL-1].close - quotesL[lastbarL-1].open ) < FIXED_STOP / 2 * PIP_SIZE )
				return null;
			
			int start = Pattern.findLastMACrossDown(ema20L, ema50L, lastbarL-2, 5);
			
			if ( lastbarL - start < 48 )
				return null;

			PushHighLow[] phls = Pattern.findPast2Lows(quotesL, start, lastbarL-2 );

			if ( phls != null ) 
			{
				System.out.println(symbol + " Change of Direction BUY detect at " + quotesL[lastbarL-1].time);
			}
		}
		return null;
	}

	
	
	
	
	public Trade checkREVTradeSetup(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		QuoteData[] quotesS = smallTimeFrameTraderManager.getQuoteData();
		int lastbarS = quotesS.length - 1;
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 5);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 5);

	
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
						System.out.println(symbol + " " + CHART + " " + " trend starting point is " + ((QuoteData)quotesL[pushStartL]).time);
						logger.warning(symbol + " " + CHART + " " + " sell detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time);
			    	    System.out.println(symbol + " " + CHART + 
								 ((QuoteData)quotesL[ phl.prePos]).time + "@" + ((QuoteData)quotesL[ phl.prePos]).high + "  -  " +
								 ((QuoteData)quotesL[ phl.curPos]).time + "@" + ((QuoteData)quotesL[ phl.curPos]).high + 
								 " pullback@" + phl.pullBack.time);

			    	    Trade tt = createTrade(Constants.TRADE_REV, Constants.ACTION_SELL, FIXED_STOP_REV);
						tt.detectTime = quotesL[lastbarL].time;
						tt.pushStartL = pushStartL;
						tt.pullBackPos = phl.pullBack.pos;
						tt.POSITION_SIZE = POSITION_SIZE;
						if (prevTradeExist(tt.type, tt.pullBackPos))
							return null;

						tt.phl = phl;
						tt.status = Constants.STATUS_OPEN;
						tt.triggerPosL = lastbarL;
						tt.triggerPos = lastbar;
						
						tt.detectPosS = lastbarS;
						//trade = tt;
						//enterMarketPosition(quotesL[phl.prePos].high);
						//enterLimitPosition1(Constants.ACTION_SELL, POSITION_SIZE, quotes[phl.prePos].high + 10 * PIP_SIZE);
						return tt;
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
						Trade tt = createTrade(Constants.TRADE_REV, Constants.ACTION_BUY, FIXED_STOP_REV);
						tt.detectTime = quotesL[lastbarL].time;
						tt.pushStartL = pushStartL;
						tt.pullBackPos = phl.pullBack.pos;
						tt.POSITION_SIZE = POSITION_SIZE;
						if (prevTradeExist(tt.type, tt.pullBackPos))
							return null;
						
						tt.phl = phl;
						logger.warning(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time );
						tt.status = Constants.STATUS_OPEN;
						tt.triggerPosL = lastbarL;
						tt.triggerPos = lastbar;

						tt.detectPosS = lastbarS;
						//trade = tt;
						//enterMarketPosition(quotesL[phl.prePos].low);
						//enterLimitPosition1(Constants.ACTION_BUY, POSITION_SIZE, quotes[phl.prePos].high - 10 * PIP_SIZE);
						return tt;
					}
				}
			}
		}
		return null;
	}
	

	
	
	
	
	public Trade checkSLMTradeSetup(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{
			int startL = downPos;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 3);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
			int pushStartL = Utility.getHigh( quotesL, downPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getLow( quotesL, startL, downPos).pos;
			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last push down was at " + ((QuoteData)quotesL[pullBackStart]).time);

			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[pullBackStart]).time);


			Trade tt = createTrade(Constants.TRADE_SLM, Constants.ACTION_SELL, FIXED_STOP_SLM);
			tt.detectTime = quotes[lastbar].time;
			tt.pushStartL = pushStartL;
			tt.pullBackStartL = pullBackStart; 
			tt.pullBackPos = pullBackStart;
			tt.POSITION_SIZE = POSITION_SIZE;
			//trade.lastHighLow = thisHighLow;
			
			if ((((QuoteData) quotesL[lastbarL]).low > ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).low > ema20L[lastbarL - 1]))
			{
				//logger.warning(symbol + " " + CHART + " triggered above 20MA, remove trade");
				//trade = null;
				return null;
			}

			pullBackStart = Utility.findPositionByHour( quotes, quotesL[tt.pullBackStartL].time, Constants.FRONT_TO_BACK);
			PushHighLow[] phls = Pattern.findPast2Highs(quotes, pullBackStart, lastbar );
			if ( phls != null )
			{
				// need at least 2 up
				int consecUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 2);
				if ( consecUp < pullBackStart + 10 )
					return null;

				if ( phls.length >2 )
				{
					// 1.  at least three with MCAD diverage
					// 2.  many push ups with low badwidth
					double lowest = Utility.getLow(quotes, pullBackStart, lastbar ).low;
					
					if ( quotes[lastbar].high - lowest < 1.85 * FIXED_STOP * PIP_SIZE )
					{
						if (( phls.length > 5 ))
						{	
							if (prevTradeExist(tt.type, tt.pullBackPos))
								return null;
							logger.warning(symbol + " " + CHART + " " + " sell detected 2 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[tt.pullBackPos]).time);
							if ( getTrade() == null )
								enterMarketPosition(tt, quotes[phls[0].prePos].high);
							return tt;
						}
					}
				}
			}
		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			int startL = upPos;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 3);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			int pushStartL = Utility.getLow( quotesL, upPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getHigh( quotesL, startL, upPos).pos;
			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last push down was at " + ((QuoteData)quotesL[pullBackStart]).time);


			Trade tt = createTrade(Constants.TRADE_SLM, Constants.ACTION_BUY, FIXED_STOP_SLM);
			tt.detectTime = quotes[lastbar].time;
			tt.pushStartL = pushStartL;
			tt.pullBackStartL = pullBackStart; 
			tt.pullBackPos = pullBackStart; 
			tt.POSITION_SIZE = POSITION_SIZE;
			//trade.lastHighLow = thisHighLow;

			
			if ((((QuoteData) quotesL[lastbarL]).high < ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).high < ema20L[lastbarL - 1]))
			{
				//logger.warning(symbol + " " + CHART + " triggered below 20MA, remove trade");
				//trade = null;
				return null;
			}

			pullBackStart = Utility.findPositionByHour( quotes, quotesL[tt.pullBackStartL].time, Constants.FRONT_TO_BACK);
			// 2.  to check if there is a opportunity to fade from a larger time
			//if ( trade.limit1Placed == true )
			//	return;
			
			
			PushHighLow[] phls = Pattern.findPast2Lows(quotes, pullBackStart, lastbar );
			if ( phls != null )
			{
				// need at least 2 up
				int consecDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2);
				if ( consecDown < pullBackStart + 10 )
					return null;
				//System.out.println(symbol + " " + CHART + " consective upabove 20MA is at " + quotes[consecUp].time);
				
				/*
				for ( int i = 0; i < phls.length; i++)
				{
		    	    System.out.println(symbol + " " + CHART + " last pull back " + i + "    " +
					 ((QuoteData)quotes[ phls[i].prePos]).time + "@" + ((QuoteData)quotes[ phls[i].prePos]).high + "  -  " +
					 ((QuoteData)quotes[ phls[i].curPos]).time + "@" + ((QuoteData)quotes[ phls[i].curPos]).high + 
					 " pullback@" + phls[i].pullBack.time);
				}*/
				
				if ( phls.length >2 )
				{
					// 1.  at least three with MCAD diverage
					// 2.  many push ups with low badwidth
					double highest = Utility.getHigh(quotes, pullBackStart, lastbar ).high;
					
					if ( highest - quotes[lastbar].low < 1.85 * FIXED_STOP * PIP_SIZE )
					{
						
						if (( phls.length > 5 ))
						{	
							if (prevTradeExist(tt.type, tt.pullBackPos))
								return null;
							logger.warning(symbol + " " + CHART + " " + " buy detected 2" + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[tt.pullBackPos]).time);
							if ( getTrade() == null )
								enterMarketPosition(tt, quotes[phls[0].prePos].low);
							return tt;
						}
					}
				}
			}
		}
		return null;
	}


	public Trade checkRVSTradeSetup(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{
			int startL = downPos;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;

			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 3);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
			int pushStartL = Utility.getHigh( quotesL, downPos2-3, startL).pos;

			int pullBackStart = Utility.getLow( quotesL, startL, downPos).pos;

			PushHighLow lastPHLL = null;
			for ( int i = lastbarL; i > lastbarL-5; i--)
			{
				lastPHLL = findLastHighWOWOLow( quotesL, lastbarL-15, i, 5 );
				if (lastPHLL != null )
					break;
			}
			
			if ( lastPHLL == null )
				return null;
			
			if ( quotesL[lastbarL].high > lastPHLL.pullBack.high )
			{
				int high = Utility.findPositionByHour( quotes, lastPHLL.pullBack.time, Constants.FRONT_TO_BACK);
				int low = Utility.getLow( quotes, high, lastbar).pos;
				
				PushHighLow[] phls15 = Pattern.findPast2Lows(quotes, high, low );
				if ( phls15.length >= 3 )
				{
					Trade tt = createTrade(Constants.TRADE_RVS, Constants.ACTION_BUY, FIXED_STOP_RVS);
					tt.detectTime = quotes[lastbar].time;
					tt.pushStartL = pushStartL;
					tt.pullBackStartL = pullBackStart; 
					tt.pullBackPos = pullBackStart;
					tt.POSITION_SIZE = POSITION_SIZE;

					tt.type = Constants.TRADE_RVS;
					if (prevTradeExist(tt.type, tt.pullBackPos))
						return null;

					logger.warning(symbol + " " + CHART + " " + " reverse detected 1" + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[tt.pullBackPos]).time);
					if ( getTrade() == null );
						enterMarketPosition(tt, data.close/*lastPHLL.pullBack.high*/);
					return tt;
				}
			}
		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			int startL = upPos;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;

			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 3);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			int pushStartL = Utility.getLow( quotesL, upPos2-3, startL).pos;

			int pullBackStart = Utility.getHigh( quotesL, startL, upPos).pos;

			PushHighLow lastPHLL = null;
			for ( int i = lastbarL; i > lastbarL-5; i--)
			{
				lastPHLL = findLastHighWOWOHigh( quotesL, lastbarL-15, i, 5 );
				if (lastPHLL != null )
					break;
			}
			if ( lastPHLL == null )
				return null;
			
			//System.out.println ( "last phll found at " + quotesL[lastPHLL.prePos].time + " " + quotesL[lastPHLL.curPos].time + " " + lastPHLL.pullBack.time);
			
			if ( quotesL[lastbarL].low < lastPHLL.pullBack.low )
			{
				int low = Utility.findPositionByMinute( quotes, lastPHLL.pullBack.time, Constants.FRONT_TO_BACK);
				//System.out.println ( "low found at " + quotes[low].time);
				int high = Utility.getHigh( quotes, low, lastbar).pos;
				
				PushHighLow[] phls15 = Pattern.findPast2Highs(quotes, low, high );
				if ( phls15.length >= 3 )
				{
					Trade tt = createTrade(Constants.TRADE_RVS, Constants.ACTION_SELL, FIXED_STOP_RVS);
					tt.detectTime = quotes[lastbar].time;
					tt.pushStartL = pushStartL;
					tt.pullBackStartL = pullBackStart; 
					tt.pullBackPos = pullBackStart; 
					tt.POSITION_SIZE = POSITION_SIZE;

					tt.type = Constants.TRADE_RVS;
					if (prevTradeExist(tt.type, tt.pullBackPos))
						return null;

					logger.warning(symbol + " " + CHART + " " + " reverse detected 1" + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[tt.pullBackPos]).time);
					if ( getTrade() == null )
						enterMarketPosition(tt, data.close/*lastPHLL.pullBack.low*/);
					return tt;
				}
			}
		}		
		return null;
	}

	
	
	
	
	public Trade check20MABreakOut(QuoteData data, QuoteData[] quotesL)
	{
		//QuoteData[] quotes = getQuoteData();
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		if (( quotesL[start].low > ema20L[start]) || ( quotesL[start].high < ema20L[start]))
			start--;
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, start, 1);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, start, 1);
	
		if ( lastUpPos > lastDownPos)
		{	
			start = lastUpPos;
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastUpPos, 2);
			if ( prevDownPos == Constants.NOT_FOUND )
				return null;
			
			start = prevDownPos;
			while ( quotesL[start].low <= ema20L[start] )
				start++;

			//while ( quotesL[start].high > ema20L[start] )  // make sure all highs counted in , not just the break out ones
			//	start--;
			//start = start+1;

			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i < lastbarL; i++)
			{
				if ( quotesL[i].high > quotesL[lastbarL].high)
					return null;
				if ( quotesL[i].low < ema20L[i])
					touch20MA++;
			}
			if ( touch20MA == 0 )
				return null;

			QuoteData pullback = Utility.getLow( quotesL, start+1, lastbarL-1);
			
			if (( quotesL[lastbarL].high - pullback.low) > FIXED_STOP * 1.5 * PIP_SIZE )
				return null;
			
			
			logger.info(symbol + " " + CHART + " " + " break up detected at " + quotesL[lastbarL].time + " firstPushUp: " + quotesL[start].time + " pullback: " + pullback.time );
			Trade tt = createTrade(Constants.TRADE_20B, Constants.ACTION_BUY, FIXED_STOP_20B);
			tt.detectTime = quotesL[lastbarL].time;
			tt.pushStartL = start;
			tt.pullBackPos = start;//pullback.pos;//start;  // this is to identify the previous trade only
			tt.POSITION_SIZE = POSITION_SIZE;
			if (prevTradeExist(tt.type, tt.pullBackPos))
				return null;
			
			tt.triggerPos = lastbarL;
			tt.stop = adjustPrice( pullback.low, Constants.ADJUST_TYPE_DOWN);
			if ( getTrade() == null )
				enterMarketPosition( tt, Utility.getHigh( quotesL, start+1, lastbarL-1).high);
			return tt;

		}	
		else if ( lastDownPos > lastUpPos )
		{	
			start = lastDownPos;
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPos, 2);
			if ( prevUpPos == Constants.NOT_FOUND )
				return null;
			
			start = prevUpPos;
			while ( quotesL[start].high >= ema20L[start] )
				start++;

			//while ( quotesL[start].low < ema20L[start] )  // make sure all lows counted in , not just the break out ones
			//	start--;
			//start = start+1;
			
			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i < lastbarL; i++)
			{
				if ( quotesL[i].low < quotesL[lastbarL].low)
					return null;
				if ( quotesL[i].high > ema20L[i])
					touch20MA++;
			}
			if ( touch20MA == 0 )
				return null;

			QuoteData pullback = Utility.getHigh( quotesL, start+1, lastbarL-1);
			
			if (( pullback.high - quotesL[lastbarL].low ) > FIXED_STOP * 1.5 * PIP_SIZE )
				return null;
			
			
			logger.info(symbol + " " + CHART + " " + " break up detected at " + quotesL[lastbarL].time + " firstPushDown: " + quotesL[start].time + " pullback: " + pullback.time );
			Trade tt = createTrade(Constants.TRADE_20B, Constants.ACTION_SELL, FIXED_STOP_20B);
			tt.detectTime = quotesL[lastbarL].time;
			tt.pushStartL = start;
			tt.pullBackPos = start;//pullback.pos;//start;   // this is to identify previous trade only
			tt.POSITION_SIZE = POSITION_SIZE;
			if (prevTradeExist(tt.type, tt.pullBackPos))
				return null;
			
			tt.triggerPos = lastbarL;
			tt.stop = adjustPrice( pullback.high, Constants.ADJUST_TYPE_UP);
			if ( getTrade() == null )
				enterMarketPosition( tt, Utility.getLow( quotesL, start+1, lastbarL-1).low);
			return tt;

		}	
		
		return null;
	}

	
	
	public Trade check20MABreakOut_org(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		//int lastbar = quotes.length - 1;
		//double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		//double[] ema100L = Indicator.calculateEMA(quotesL, 100);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		
		int start = lastbarL;

			lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, start, 1);
			lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, start, 1);
	
			if ( lastUpPos > lastDownPos)
			{	
				start = lastUpPos;
				while (( quotesL[start].low > ema20L[start]) && ( start > 0 ))
				{	
					//System.out.println(start);
					start--;
				}
				
				prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, start, 2);
				prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, start, 2);
				if ( prevDownPos > prevUpPos )
				{
					direction = Constants.DIRECTION_UP;
				}
			}	
			else if ( lastDownPos > lastUpPos)
			{	
				start = lastDownPos;
				while (( quotesL[start].high < ema20L[start]) && ( start > 0 ))
				{
					//System.out.println(start);
					start--;
				}

				prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, start, 2);
				prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, start, 2);
				if ( prevUpPos > prevDownPos)
				{
					direction = Constants.DIRECTION_DOWN;
				}
			}
			
			
		//if (lastUpPos > lastDownPos)
		//	direction = Constants.DIRECTION_UP;
		//else if (lastDownPos > lastUpPos)
		//	direction = Constants.DIRECTION_DOWN;
		
		//if ( direction == Constants.DIRECTION_UP )
		//	System.out.println("last directin up detected at " + quotesL[lastbarL].time);
		//if ( direction == Constants.DIRECTION_DOWN )
			//System.out.println("last directin down detected at " + quotesL[lastDownPos].time);
		
		if (direction == Constants.DIRECTION_UP) 
		{
			int startL = lastUpPos;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;
			QuoteData firstPushUp = Utility.getHigh(quotesL, startL, lastUpPos);
			QuoteData firstPullBack = Utility.getLow( quotesL, firstPushUp.pos, lastbarL-1);
			if ( firstPullBack.low > ema20L[firstPullBack.pos])
				return  null;
			
			//logger.fine(symbol + " " + CHART + " " + " first push up is " + firstPushUp.time + " first pull back is " + firstPullBack.time );
			
			// 3. check if the first push is not too far
			if ( firstPushUp.high - firstPullBack.low > FIXED_STOP_20B * 1.5 * PIP_SIZE )
				return  null;
			//logger.fine(symbol + " " + CHART + " " + " push < 1.5 pip size" );
			
			// 4. check if the first breakout
			if ( quotesL[lastbarL].high < firstPushUp.high )
				return null; 
			//logger.info(symbol + " " + CHART + " " + " first higher than push is " + quotesL[lastbarL].time );
			for ( int i = firstPushUp.pos+1; i < lastbarL; i++)
			{
				if ( quotesL[i].high > firstPushUp.high )
					return null;
			}
			
			Trade tt = createTrade(Constants.TRADE_20B, Constants.ACTION_BUY, FIXED_STOP_20B);
			tt.detectTime = quotesL[lastbarL].time;
			tt.pushStartL = firstPushUp.pos;
			tt.pullBackPos = firstPullBack.pos;
			tt.POSITION_SIZE = POSITION_SIZE;
			if (prevTradeExist(tt.type, tt.pullBackPos))
				return null;
			
			logger.warning(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + firstPushUp.time );
			tt.triggerPos = lastbarL;
			//tt.stop = adjustPrice( firstPullBack.low, Constants.ADJUST_TYPE_DOWN);  works better with default stop??
			if ( getTrade() == null )
				enterMarketPosition(tt,firstPushUp.high);
			return tt;
			
		}
		else if (direction == Constants.DIRECTION_DOWN)
		{
			// 1. Find first touch 20MA
			//boolean touched20MA = false;
			//for ( int i = lastDownPos+1; i <= lastbarL; i++)
			//	if (quotesL[i].high > ema20L[i])
			//		touched20MA = true;
			
			//if (touched20MA == false)
			//	return null;
			/*
			boolean allowed = false;
			if (ema50L[lastbarL] < ema100L[lastbarL])
			{
				start = Pattern.findLastMACrossDown(ema50L, ema100L);
				if ( start != Constants.NOT_FOUND )
				for ( int i = lastbarL; i > start; i--)
				{	
					PushHighLow[] phls = Pattern.findPast2Lows(quotesL, start - 10, i );
					if ( phls != null )
					{
						if ( phls.length >= 2 )
						{
							int touch20MAcount = 0;
							for ( int j = 0; j < phls.length; j++)
							{
								if (phls[j].pullBack.high > ema50L[phls[j].pullBack.pos])
									touch20MAcount++;
							}
						
							if ( touch20MAcount >= 2 )
							{	
								allowed = true;
								break;
							}
						}
						break;
					}
				}

				if ( allowed == false )
					return null;
			}*/
				
			int startL = lastDownPos;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;
			QuoteData firstPushDown = Utility.getLow(quotesL, startL, lastDownPos);
			QuoteData firstPullBack = Utility.getHigh( quotesL, firstPushDown.pos, lastbarL-1);
			if ( firstPullBack.high < ema20L[firstPullBack.pos])
				return  null;
			
			// 2. check if prevPos is down
			//int prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL-1, 2);
			//int prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL-1, 2);
			//if ( prevDownPos > prevUpPos )
			//	return null;
			//logger.fine(symbol + " " + CHART + " " + " first push down is " + firstPushDown.time + " first pull back is " + firstPullBack.time );
			
			// 3. check if the first push is not too far
			if ( firstPullBack.high - firstPushDown.low > FIXED_STOP_20B * 1.5 * PIP_SIZE )
				return  null;
			//logger.fine(symbol + " " + CHART + " " + " push < 1.5 pip size" );
			
			// 4. check if the first breakout
			if ( quotesL[lastbarL].low > firstPushDown.low )
				return null; 
			//logger.info(symbol + " " + CHART + " " + " first lower than push is " + quotesL[lastbarL].time );
			for ( int i = firstPushDown.pos+1; i < lastbarL; i++)
			{
				if ( quotesL[i].low < firstPushDown.low )
					return null;
			}
			
			Trade tt = createTrade(Constants.TRADE_20B, Constants.ACTION_SELL, FIXED_STOP_20B);
			tt.detectTime = quotesL[lastbarL].time;
			tt.pushStartL = firstPushDown.pos;
			tt.pullBackPos = firstPullBack.pos;
			tt.POSITION_SIZE = POSITION_SIZE;
			if (prevTradeExist(tt.type, tt.pullBackPos))
				return null;
			
			logger.warning(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + firstPushDown.time );
			tt.triggerPos = lastbarL;
			//tt.stop = adjustPrice( firstPullBack.high, Constants.ADJUST_TYPE_UP);
			if ( getTrade() == null )
				enterMarketPosition(tt,firstPushDown.low);
			return tt;

		}
		
		return null;
	}
	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public Trade checkPullBackTradeSetup(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		double[] ema100L = Indicator.calculateEMA(quotesL, 100);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{

			if (ema50L[lastbarL] > ema100L[lastbarL])
			{
				boolean allowed = false;
				int start = Pattern.findLastMACrossUp(ema50L, ema100L);
				if ( start != Constants.NOT_FOUND )
				{
					for ( int i = lastbarL; i > start; i--)
					{	
						PushHighLow[] phls = Pattern.findPast2Highs(quotesL, start - 10, i );
						if ( phls != null )
						{
							if ( phls.length >= 2 )
							{
								int touch20MAcount = 0;
								for ( int j = 0; j < phls.length; j++)
								{
									if (phls[j].pullBack.low < ema50L[phls[j].pullBack.pos])
										touch20MAcount++;
								}
							
								if ( touch20MAcount >= 2 )
								{	
									allowed = true;
									break;
								}
							}
							break;
						}
					}
				}

				if ( allowed == false )
					return null;
			}
			
			//if (ema50L[lastbarL] > ema100L[lastbarL])
			//	return null;
			
			int startL = downPos;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 3);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
			int pushStartL = Utility.getHigh( quotesL, downPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getLow( quotesL, startL, downPos).pos;
			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last push down was at " + ((QuoteData)quotesL[pullBackStart]).time);

			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[pullBackStart]).time);


			Trade tt = createTrade(Constants.TRADE_PBK, Constants.ACTION_SELL, FIXED_STOP_PBK);
			tt.detectTime = quotes[lastbar].time;
			tt.pushStartL = pushStartL;
			tt.pullBackStartL = pullBackStart; 
			tt.pullBackPos = pullBackStart;
			tt.POSITION_SIZE = POSITION_SIZE;
			
			if ((((QuoteData) quotesL[lastbarL]).low > ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).low > ema20L[lastbarL - 1]))
			{
				//logger.warning(symbol + " " + CHART + " triggered above 20MA, remove trade");
				//trade = null;
				return null;
			}

			if (prevTradeExist(tt.type, tt.pullBackPos))
				return null;


			pullBackStart = Utility.findPositionByHour( quotes, quotesL[tt.pullBackStartL].time, Constants.FRONT_TO_BACK);
			PushHighLow[] phls = Pattern.findPast2Highs(quotes, pullBackStart, lastbar );
			if ( phls != null )
			{
				// need at least 2 up
				int consecUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 2); ///?????
				if ( consecUp < pullBackStart + 10 )  ////?????
					return null;

				if ( phls.length >2 )
				{
					// 1.  at least three with MCAD diverage
					// 2.  many push ups with low badwidth
					double lowest = Utility.getLow(quotes, pullBackStart, lastbar ).low;
					
					if ( quotes[lastbar].high - lowest > 1.85 * FIXED_STOP * PIP_SIZE )//????
					{
						// wide range, requires a MACD diverage
						PushHighLow phl = phls[0];
						MACD[] macd = Indicator.calculateMACD( quotes );
						int negatives = 0;
						for ( int j = phl.prePos; j <= phl.curPos; j++)
						{
							if ( macd[j].histogram < 0 )
								negatives ++;
							//System.out.print(macd[j].macd + "   ");
						}
						//System.out.println();
						
						if ( negatives >= 2 )
						{
							if ( phl.curPos - phl.prePos > 48 )////?????
							{
								// check if there is 3 ups within this period
								int begin = phl.prePos;
								int low = Utility.getLow( quotes, begin, lastbar).pos;
								
								PushHighLow[] phls15 = Pattern.findPast2Lows(quotes, begin, low );
								if ( phls15.length >= 3 )
								{
									//trade.type = Constants.TRADE_REVERSAL;
									//if (prevTradeExist(trade.type, trade.pullBackPos))
										return null;

									
									//logger.warning(symbol + " " + CHART + " " + " reverse detected 1" + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
									//trade.action = Constants.ACTION_BUY;
									//enterMarketPosition(quotes[phl.prePos].high);
									//return trade;
								}
							}

							logger.warning(symbol + " " + CHART + " " + " sell detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[tt.pullBackPos]).time);
							tt.phl = phl;
							tt.status = Constants.STATUS_OPEN;
							tt.detectTime = quotes[lastbar].time;
							//enterMarketPosition(quotes[phl.prePos].high);
							//enterLimitPosition1(Constants.ACTION_BUY, POSITION_SIZE, quotes[phl.prePos].high - 10 * PIP_SIZE);
							return tt;
						}
					}
				}
			}
		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{

			if (ema50L[lastbarL] < ema100L[lastbarL])
			{
				boolean allowed = false;
				int start = Pattern.findLastMACrossDown(ema50L, ema100L);
				if ( start != Constants.NOT_FOUND )
				{
					for ( int i = lastbarL; i > start; i--)
					{	
						PushHighLow[] phls = Pattern.findPast2Lows(quotesL, start - 10, i );
						if ( phls != null )
						{
							if ( phls.length >= 2 )
							{
								int touch20MAcount = 0;
								for ( int j = 0; j < phls.length; j++)
								{
									if (phls[j].pullBack.high > ema50L[phls[j].pullBack.pos])
										touch20MAcount++;
								}
							
								if ( touch20MAcount >= 2 )
								{	
									allowed = true;
									break;
								}
							}
							break;
						}
					}
				}

				if ( allowed == false )
					return null;
			}

			
			
			
			
			//			if (ema50L[lastbarL] < ema100L[lastbarL])
			//				return null;

			int startL = upPos;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 3);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			int pushStartL = Utility.getLow( quotesL, upPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getHigh( quotesL, startL, upPos).pos;
			logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last push down was at " + ((QuoteData)quotesL[pullBackStart]).time);

			Trade tt = createTrade(Constants.TRADE_PBK, Constants.ACTION_BUY, FIXED_STOP_PBK);
			tt.detectTime = quotes[lastbar].time;
			tt.pushStartL = pushStartL;
			tt.pullBackStartL = pullBackStart; 
			tt.pullBackPos = pullBackStart; 
			tt.POSITION_SIZE = POSITION_SIZE;
			//trade.lastHighLow = thisHighLow;

			
			if ((((QuoteData) quotesL[lastbarL]).high < ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).high < ema20L[lastbarL - 1]))
			{
				//logger.warning(symbol + " " + CHART + " triggered below 20MA, remove trade");
				//trade = null;
				return null;
			}

			if (prevTradeExist(tt.type, tt.pullBackPos))
				return null;

			
			pullBackStart = Utility.findPositionByHour( quotes, quotesL[tt.pullBackStartL].time, Constants.FRONT_TO_BACK);
			// 2.  to check if there is a opportunity to fade from a larger time
			//if ( trade.limit1Placed == true )
			//	return;
			
			
			PushHighLow[] phls = Pattern.findPast2Lows(quotes, pullBackStart, lastbar );
			if ( phls != null )
			{
				// need at least 2 up
				int consecDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2);
				if ( consecDown < pullBackStart + 10 )
					return null;
				//System.out.println(symbol + " " + CHART + " consective upabove 20MA is at " + quotes[consecUp].time);
				
				/*
				for ( int i = 0; i < phls.length; i++)
				{
		    	    System.out.println(symbol + " " + CHART + " last pull back " + i + "    " +
					 ((QuoteData)quotes[ phls[i].prePos]).time + "@" + ((QuoteData)quotes[ phls[i].prePos]).high + "  -  " +
					 ((QuoteData)quotes[ phls[i].curPos]).time + "@" + ((QuoteData)quotes[ phls[i].curPos]).high + 
					 " pullback@" + phls[i].pullBack.time);
				}*/
				
				if ( phls.length >2 )
				{
					// 1.  at least three with MCAD diverage
					// 2.  many push ups with low badwidth
					double highest = Utility.getHigh(quotes, pullBackStart, lastbar ).high;
					
					if ( highest - quotes[lastbar].low > 1.85 * FIXED_STOP * PIP_SIZE )
					{
						// wide range, requires a MACD diverage
						PushHighLow phl = phls[0];
						MACD[] macd = Indicator.calculateMACD( quotes );
						int positive = 0;
						for ( int j = phl.prePos; j <= phl.curPos; j++)
						{
							if ( macd[j].histogram > 0 )
								positive ++;
							//System.out.print(macd[j].macd + "   ");
						}
						//System.out.println();
						
						if ( positive >= 2 )
						{
							// check worth reversal
							
							if ( phl.curPos - phl.prePos > 48 )
							{
								// check if there is 3 ups within this period
								int low = phl.prePos;
								int high = Utility.getHigh( quotes, low, lastbar).pos;
								
								PushHighLow[] phls15 = Pattern.findPast2Highs(quotes, low, high );
								if ( phls15.length >= 3 )
								{
									//trade.type = Constants.TRADE_REVERSAL;
									//if (prevTradeExist(trade.type, trade.pullBackPos))
										return null;

									//logger.warning(symbol + " " + CHART + " " + " reverse detected 1" + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
									//trade.action = Constants.ACTION_SELL;
									//enterMarketPosition(quotes[phl.prePos].low);
									//return trade;
								}
							}
							
							
							logger.warning(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[tt.pullBackPos]).time);
							tt.phl = phl;
							tt.status = Constants.STATUS_OPEN;
							tt.detectTime = quotes[lastbar].time;
							//enterMarketPosition(quotes[phl.prePos].low);
							//enterLimitPosition1(Constants.ACTION_BUY, POSITION_SIZE, quotes[phl.prePos].high - 10 * PIP_SIZE);
							return tt;
						}
					}
				}
			}
		}
		return null;
	}



	
	
	
	
	public Trade checkPullBackTradeSetup2(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		double[] ema100L = Indicator.calculateEMA(quotesL, 100);
		
	
		//int lastMAConsectiveUp = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, lastbarL, 48);
		int lastMAConsectiveUp = Pattern.findLastMAConsectiveUp(ema20L, ema50L, 36);
		//if ( lastMAConsectiveUp != -1 )
		//	System.out.println("lastConsectiveUp=" + quotesL[lastMAConsectiveUp].time); 
		//int lastMAConsectiveDown = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, lastbarL, 48);
		int lastMAConsectiveDown = Pattern.findLastMAConsectiveDown(ema20L, ema50L, 36);
		//if ( lastMAConsectiveDown != -1 )
			//System.out.println(" lastConsectiveDown=" + quotesL[lastMAConsectiveDown].time); 
		
		
		if ( lastMAConsectiveUp > lastMAConsectiveDown)
		{
			// potential up trend;
			int upStart = Pattern.findLastMACrossUp(ema20L, ema50L, lastMAConsectiveUp, 5);
			if ( upStart == Constants.NOT_FOUND )
				upStart = 0;
			QuoteData highestPoint = Utility.getHigh( quotesL, upStart, lastbarL-1);
			//System.out.println("highest point=" + highestPoint.time); 
			
			// check if this is the highest point
			PushHighLow[] phls = Pattern.findPast1Lows(quotesL,highestPoint.pos, lastbarL );
			if ( phls != null )
			{
				if ( phls.length >= 2 )
				{
					PushHighLow phl = phls[0];
					MACD[] macd = Indicator.calculateMACD( quotes );
					int positive = 0;
					for ( int j = phl.prePos; j <= phl.curPos; j++)
					{
						if ( macd[j].histogram > 0 )
							positive ++;
						//System.out.print(macd[j].macd + "   ");
					}

					if ((positive > 0) || ((phl.pullBack.high - quotesL[phl.prePos].low) > FIXED_STOP * 1.2 ))
					{
						warning(" trade buy detected at " + quotesL[lastbarL].time);
						Trade tt = createTrade(Constants.TRADE_PBK, Constants.ACTION_BUY, FIXED_STOP_PBK);
						tt.detectTime = quotes[lastbar].time;
						tt.pushStartL = tt.pullBackStartL = highestPoint.pos;
						tt.POSITION_SIZE = POSITION_SIZE;
						tt.pullBackPos = phls[0].pullBack.pos;
						//trade.lastHighLow = thisHighLow;

						if (prevTradeExist(tt.type, tt.pullBackPos))
							return null;

						tt.phl = phl;
						tt.status = Constants.STATUS_OPEN;
					}
				}
			}
		}
		else if ( lastMAConsectiveUp < lastMAConsectiveDown)
		{
			// potential up trend;
			int downStart = Pattern.findLastMACrossDown(ema20L, ema50L, lastMAConsectiveUp, 5);
			if ( downStart == Constants.NOT_FOUND )
				downStart = 0;
			QuoteData lowestPoint = Utility.getHigh( quotesL, downStart, lastbarL-1);
			//System.out.println("highest point=" + highestPoint.time); 
			
			// check if this is the highest point
			PushHighLow[] phls = Pattern.findPast1Highs(quotesL,lowestPoint.pos, lastbarL );
			if ( phls != null )
			{
				if ( phls.length >= 2 )
				{
					PushHighLow phl = phls[0];
					MACD[] macd = Indicator.calculateMACD( quotes );
					int negative = 0;
					for ( int j = phl.prePos; j <= phl.curPos; j++)
					{
						if ( macd[j].histogram < 0 )
							negative ++;
						//System.out.print(macd[j].macd + "   ");
					}

					if ((negative > 0) || ((quotesL[phl.prePos].high - phl.pullBack.low ) > FIXED_STOP * 1.2 ))
					{
						warning(" trade buy detected at " + quotesL[lastbarL].time);
						Trade tt = createTrade(Constants.TRADE_PBK, Constants.ACTION_SELL, FIXED_STOP_PBK);
						tt.detectTime = quotes[lastbar].time;
						tt.pushStartL = tt.pullBackStartL = lowestPoint.pos;
						tt.POSITION_SIZE = POSITION_SIZE;
						tt.pullBackPos = phls[0].pullBack.pos;
						//trade.lastHighLow = thisHighLow;

						if (prevTradeExist(tt.type, tt.pullBackPos))
							return null;

						tt.phl = phl;
						tt.status = Constants.STATUS_OPEN;
					}
				}
			}
		}
		
		return null;
	}


	
	
	
	
	
	
	
	
	
	
	
	
	
	public Trade HigherHighLowerLowSetupOrg(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);

		int lastConsectiveUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 5);
		int lastConsectiveDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 5);

		if (lastConsectiveUp > lastConsectiveDown)
		{
			MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, lastbar);
			/*
			 * logger.warning(symbol + " " + CHART + " trend is up " +
			 * data.time); logger.warning(symbol + " " + CHART +
			 * " last consecitveAbove20MA is " +
			 * ((QuoteData)quotes[lastConsectiveUp]).time);
			 * logger.warning(symbol + " " + CHART +
			 * " last consecitveBelow20MA is " +
			 * ((QuoteData)quotes[lastConsectiveDown]).time);
			 * logger.warning(symbol + " " + CHART +
			 * " last 20MA touch up low is " + mat.lowest );
			 */
			if (data.low < mat.low.low)
			{
				// requires two higher high
				int lastlow = mat.low.pos;
				while (((QuoteData) quotes[lastlow]).low < ema20[lastlow])
					lastlow--;
				// now lastlow is above again
				MATouch mat2 = Pattern.findLastMATouchUp(quotes, ema20, lastlow);
				if (!(mat.low.low > mat2.low.low))
					return null;

				// last check large data chart
				trade = new Trade(symbol);
				logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time);
				logger.warning(symbol + " " + CHART + " last 20MA touch up low is " + mat.low.time);

				trade.type = Constants.TRADE_RST;
				trade.action = Constants.ACTION_SELL;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.detectTime = ((QuoteData) quotes[lastbar]).time;
				trade.detectPrice = mat.low.low;
				trade.detectPos = lastbar;
				trade.status = Constants.STATUS_OPEN;

				// place order
				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				trade.price = mat.low.low;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				// AddTradeOpenRecord( trade.type, data.time,
				// Constants.ACTION_SELL, trade.positionSize, data.close);
				AddTradeOpenRecord(trade.type, ((QuoteData) quotes[lastbar]).time, Constants.ACTION_SELL, trade.POSITION_SIZE, mat.low.low);

				trade.stop = Utility.getHigh(quotes, mat.low.pos, lastbar).high;
				if (trade.stop - mat.low.low > 14 * PIP_SIZE)
					trade.stop = mat.low.low + 14 * PIP_SIZE;

				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return trade;
			}

		}
		else if (lastConsectiveUp < lastConsectiveDown)
		{
			MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, lastbar);

			if (data.high > mat.high.high)
			{
				// requires two higher high
				int lasthigh = mat.high.pos;
				while (((QuoteData) quotes[lasthigh]).high > ema20[lasthigh])
					lasthigh--;
				// now lastlow is above again
				MATouch mat2 = Pattern.findLastMATouchDown(quotes, ema20, lasthigh);
				if (!(mat.high.high < mat2.high.high))
					return null;

				trade = new Trade(symbol);

				logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time);
				logger.warning(symbol + " " + CHART + " last 20MA touch down high is " + mat.high.time);

				trade.type = Constants.TRADE_RST;
				trade.action = Constants.ACTION_BUY;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.detectTime = ((QuoteData) quotes[lastbar]).time;
				trade.detectPrice = mat.high.high;
				trade.detectPos = lastbar;
				trade.status = Constants.STATUS_OPEN;

				// place order
				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				trade.price = mat.high.high;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				// AddTradeOpenRecord( trade.type, data.time,
				// Constants.ACTION_BUY, trade.positionSize, data.close);
				AddTradeOpenRecord(trade.type, ((QuoteData) quotes[lastbar]).time, Constants.ACTION_BUY, trade.POSITION_SIZE, mat.high.high);

				trade.stop = Utility.getLow(quotes, mat.high.pos, lastbar).high;
				if (mat.high.high - trade.stop > 14 * PIP_SIZE)
					trade.stop = mat.high.high - 14 * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());
				return trade;

			}

		}
		return null;

	}




	
	
	public Trade checkHigherHighLowerLowSetup_new(QuoteData data, QuoteData[] quotesS, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		int lastbarS = quotesS.length - 1;
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{
			int startL = downPos;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 3);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
			int pushStartL = Utility.getHigh( quotesL, downPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getLow( quotesL, startL, downPos).pos;
			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last push down was at " + ((QuoteData)quotesL[pullBackStart]).time);

			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[pullBackStart]).time);

			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
			trade.detectTime = quotes[lastbar].time;
			trade.pushStartL = pushStartL;
			trade.pullBackStartL = pullBackStart; 
			trade.pullBackPos = pullBackStart;
			trade.POSITION_SIZE = POSITION_SIZE;
			//trade.lastHighLow = thisHighLow;
			
			if ((((QuoteData) quotesL[lastbarL]).low > ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).low > ema20L[lastbarL - 1]))
			{
				//logger.warning(symbol + " " + CHART + " triggered above 20MA, remove trade");
				//trade = null;
				return null;
			}

			if (prevTradeExist(trade.type, trade.pullBackPos))
				return null;


			pullBackStart = Utility.findPositionByHour( quotes, quotesL[trade.pullBackStartL].time, Constants.FRONT_TO_BACK);
			PushHighLow[] phls = Pattern.findPast2Highs(quotes, pullBackStart, lastbar );
			if ( phls != null )
			{
				// need at least 2 up
				int consecUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 2);
				if ( consecUp < pullBackStart + 10 )
					return null;

				if ( phls.length >2 )
				{
					// 1.  at least three with MCAD diverage
					// 2.  many push ups with low badwidth
					double lowest = Utility.getLow(quotes, pullBackStart, lastbar ).low;
					
					if ( quotes[lastbar].high - lowest > 1.85 * FIXED_STOP * PIP_SIZE )
					{
						// wide range, requires a MACD diverage
						PushHighLow phl = phls[0];
						MACD[] macd = Indicator.calculateMACD( quotes );
						int negatives = 0;
						for ( int j = phl.prePos; j <= phl.curPos; j++)
						{
							if ( macd[j].histogram < 0 )
								negatives ++;
							//System.out.print(macd[j].macd + "   ");
						}
						//System.out.println();
						
						if ( negatives >= 2 )
						{
							if ( phl.curPos - phl.prePos > 48 )
							{
								// check if there is 3 ups within this period
								int begin = phl.prePos;
								int low = Utility.getLow( quotes, begin, lastbar).pos;
								
								PushHighLow[] phls15 = Pattern.findPast2Lows(quotes, begin, low );
								if ( phls15.length >= 3 )
								{
									logger.warning(symbol + " " + CHART + " " + " reverse detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
									trade.action = Constants.ACTION_BUY;
									enterMarketPosition(quotes[phl.prePos].high);

									return trade;
								}
							}

							logger.warning(symbol + " " + CHART + " " + " sell detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
							trade.entryPosS = lastbarS;
							trade.status = Constants.STATUS_OPEN;
							//enterMarketPosition(quotes[phl.curPos].high);
							//enterLimitPosition1(Constants.ACTION_SELL, POSITION_SIZE, quotes[phl.prePos].high + 10 * PIP_SIZE);
							return trade;
						}
					}
					else
					{
						if ( phls.length > 5 )
						{	
							logger.warning(symbol + " " + CHART + " " + " sell detected 2 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
							enterMarketPosition(quotes[phls[0].prePos].high);
							return trade;
						}
					}
				}
			}
		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			int startL = upPos;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 3);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			int pushStartL = Utility.getLow( quotesL, upPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getHigh( quotesL, startL, upPos).pos;
			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last push down was at " + ((QuoteData)quotesL[pullBackStart]).time);


			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
			trade.detectTime = quotes[lastbar].time;
			trade.pushStartL = pushStartL;
			trade.pullBackStartL = pullBackStart; 
			trade.pullBackPos = pullBackStart; 
			trade.POSITION_SIZE = POSITION_SIZE;
			//trade.lastHighLow = thisHighLow;

			
			if ((((QuoteData) quotesL[lastbarL]).high < ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).high < ema20L[lastbarL - 1]))
			{
				//logger.warning(symbol + " " + CHART + " triggered below 20MA, remove trade");
				//trade = null;
				return null;
			}

			if (prevTradeExist(trade.type, trade.pullBackPos))
				return null;

			
			pullBackStart = Utility.findPositionByHour( quotes, quotesL[trade.pullBackStartL].time, Constants.FRONT_TO_BACK);
			// 2.  to check if there is a opportunity to fade from a larger time
			//if ( trade.limit1Placed == true )
			//	return;
			
			
			PushHighLow[] phls = Pattern.findPast2Lows(quotes, pullBackStart, lastbar );
			if ( phls != null )
			{
				// need at least 2 up
				int consecDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2);
				if ( consecDown < pullBackStart + 10 )
					return null;
				//System.out.println(symbol + " " + CHART + " consective upabove 20MA is at " + quotes[consecUp].time);
				
				/*
				for ( int i = 0; i < phls.length; i++)
				{
		    	    System.out.println(symbol + " " + CHART + " last pull back " + i + "    " +
					 ((QuoteData)quotes[ phls[i].prePos]).time + "@" + ((QuoteData)quotes[ phls[i].prePos]).high + "  -  " +
					 ((QuoteData)quotes[ phls[i].curPos]).time + "@" + ((QuoteData)quotes[ phls[i].curPos]).high + 
					 " pullback@" + phls[i].pullBack.time);
				}*/
				
				if ( phls.length >2 )
				{
					// 1.  at least three with MCAD diverage
					// 2.  many push ups with low badwidth
					double highest = Utility.getHigh(quotes, pullBackStart, lastbar ).high;
					
					if ( highest - quotes[lastbar].low > 1.85 * FIXED_STOP * PIP_SIZE )
					{
						// wide range, requires a MACD diverage
						PushHighLow phl = phls[0];
						MACD[] macd = Indicator.calculateMACD( quotes );
						int positive = 0;
						for ( int j = phl.prePos; j <= phl.curPos; j++)
						{
							if ( macd[j].histogram > 0 )
								positive ++;
							//System.out.print(macd[j].macd + "   ");
						}
						//System.out.println();
						
						if ( positive >= 2 )
						{
							// check worth reversal
							if ( phl.curPos - phl.prePos > 48 )
							{
								// check if there is 3 ups within this period
								int low = phl.prePos;
								int high = Utility.getHigh( quotes, low, lastbar).pos;
								
								PushHighLow[] phls15 = Pattern.findPast2Highs(quotes, low, high );
								if ( phls15.length >= 3 )
								{
									logger.warning(symbol + " " + CHART + " " + " reverse detected 1" + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
									trade.action = Constants.ACTION_SELL;
									enterMarketPosition(quotes[phl.prePos].low);
									return trade;
								}
							}
							
							
							logger.warning(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
							trade.entryPosS = lastbarS;
							trade.status = Constants.STATUS_OPEN;
							//enterMarketPosition(quotes[phl.curPos].low);
							//enterLimitPosition1(Constants.ACTION_BUY, POSITION_SIZE, quotes[phl.prePos].high - 10 * PIP_SIZE);
							return trade;
						}
					}
					else
					{
						if ( phls.length > 5 )
						{	
							logger.warning(symbol + " " + CHART + " " + " buy detected 2" + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
							enterMarketPosition(quotes[phls[0].prePos].low);
							return trade;
						}
					}
				}
			}
		}
		return null;
	}


	
	
	
	
	
	
	
	
	
	public Trade checkHigherHighLowerLowSetup3(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{
			int startL = downPos;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 3);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
			int pushStartL = Utility.getHigh( quotesL, downPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getLow( quotesL, startL, downPos).pos;
			

			tao.trading.SlimJim sj = calculateSlimJim3( quotesL, lastbarL-1, 10, FIXED_STOP );

			if (( sj != null ) && ( quotesL[lastbarL].high > sj.slimJimHigh ))
			{
				createOpenTrade(Constants.TRADE_SLM, Constants.ACTION_SELL);
				trade.detectTime = quotes[lastbar].time;
				trade.pushStartL = pushStartL;
				trade.pullBackStartL = pullBackStart; 
				trade.pullBackPos = pullBackStart;
				trade.POSITION_SIZE = POSITION_SIZE;
						
				if (prevTradeExist(trade.type, trade.pullBackPos))
					return null;

				logger.warning(symbol + " " + CHART + " " + " slim sjim sell detected " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
				logger.warning(symbol + " " + CHART + " " + " slim sjim size is " + (int)((sj.slimJimHigh - sj.slimJimLow)/PIP_SIZE) );
				
				enterMarketPosition(sj.slimJimHigh);
				//double price = adjustPrice(quotes[phls[0].prePos].high, Constants.ADJUST_TYPE_UP);
				//enterLimitPosition1(Constants.ACTION_SELL, POSITION_SIZE, price);
				return trade;
			}
		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			int startL = upPos;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 3);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			int pushStartL = Utility.getLow( quotesL, upPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getHigh( quotesL, startL, upPos).pos;
			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last push down was at " + ((QuoteData)quotesL[pullBackStart]).time);


			tao.trading.SlimJim sj = calculateSlimJim3( quotesL, lastbarL-1, 10, (FIXED_STOP) );

			if (( sj != null ) && ( quotesL[lastbarL].low < sj.slimJimLow ))
			{
				createOpenTrade(Constants.TRADE_SLM, Constants.ACTION_BUY);
				trade.detectTime = quotes[lastbar].time;
				trade.pushStartL = pushStartL;
				trade.pullBackStartL = pullBackStart; 
				trade.pullBackPos = pullBackStart; 
				trade.POSITION_SIZE = POSITION_SIZE;
						
				if (prevTradeExist(trade.type, trade.pullBackPos))
					return null;

				logger.warning(symbol + " " + CHART + " " + " slim jim buy detected " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[trade.pullBackPos]).time);
				logger.warning(symbol + " " + CHART + " " + " slim sjim size is " + (int)((sj.slimJimHigh - sj.slimJimLow)/PIP_SIZE) );
				
				enterMarketPosition(sj.slimJimLow);
				//double price = adjustPrice(quotes[phls[0].prePos].high, Constants.ADJUST_TYPE_UP);
				//enterLimitPosition1(Constants.ACTION_SELL, POSITION_SIZE, price);
				return trade;
			}
		}

			
		return null;
	}
	

	
	
	
	
	
	
	
	public Trade checkHigherHighLowerLowSetup2(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 5);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 5);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{
			int startL = downPos;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 3);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
			int pushStartL = Utility.getHigh( quotesL, downPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getLow( quotesL, startL, downPos).pos;
			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last push down was at " + ((QuoteData)quotesL[pullBackStart]).time);

			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " push start:" + ((QuoteData)quotesL[pushStartL]).time + " pullbackstart:" + ((QuoteData)quotesL[pullBackStart]).time);


			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
			trade.detectTime = quotes[lastbar].time;
			trade.pushStartL = pushStartL;
			trade.pullBackStartL = pullBackStart; 
			trade.pullBackPos = pullBackStart;
			trade.POSITION_SIZE = POSITION_SIZE;
			//trade.lastHighLow = thisHighLow;
			
			if ( quotesL[lastbarL].low < quotesL[lastbarL-1].low)
			{
				trade.pullBackPos = lastbarL;
				if (prevTradeExist(trade.type, trade.pullBackPos))
					return null;
				
				enterMarketPosition(quotesL[lastbarL-1].low);
				return trade;
			}

		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			int startL = upPos;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;
			//logger.warning(symbol + " " + CHART + " " + " startL is " + ((QuoteData)quotesL[startL]).time);

			//int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 1);
			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 3);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			int pushStartL = Utility.getLow( quotesL, upPos2-3, startL).pos;
			//logger.warning(symbol + " " + CHART + " " + " push start is " + ((QuoteData)quotesL[pushStartL]).time);

			int pullBackStart = Utility.getHigh( quotesL, startL, upPos).pos;
			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last push down was at " + ((QuoteData)quotesL[pullBackStart]).time);


			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
			trade.detectTime = quotes[lastbar].time;
			trade.pushStartL = pushStartL;
			trade.pullBackStartL = pullBackStart; 
			trade.pullBackPos = pullBackStart; 
			trade.POSITION_SIZE = POSITION_SIZE;
			//trade.lastHighLow = thisHighLow;

			if ( quotesL[lastbarL].high > quotesL[lastbarL-1].high)
			{
				trade.pullBackPos = lastbarL;
				if (prevTradeExist(trade.type, trade.pullBackPos))
					return null;

				enterMarketPosition(quotesL[lastbarL-1].high);
				return trade;
			}
			
		}
		return null;
	}
	
	





	

	
	public Trade checkBreakingPullBackSetup(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbarL = quotesL.length - 1;
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);     // 5 = 60 
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);     // 5 = 60 
		double[] ema68L = Indicator.calculateEMA(quotesL, 68);     // 5 = 60 

		int lastConsecitveDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2 );
		int lastConsecitveUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 2 );
		
		if ( ema20L[lastbarL] < ema68L[lastbarL])  
		{	
			lastConsecitveDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastConsecitveUp, 2 );

			if ( lastConsecitveDown == Constants.NOT_FOUND )
				return null;
			
			int pullbackStart = Utility.getLow(quotes, lastConsecitveDown-10, lastConsecitveDown).pos;
			System.out.println("Pullback start is " + quotes[pullbackStart].time);
			for ( int i = lastbar; i > pullbackStart; i--)
			{	
				PushHighLow phl_pre = findLastNHigh( quotes, pullbackStart, i, 2 );
				if ( phl_pre != null )
				{
					System.out.println("Last pullback from up is " + phl_pre.pullBack.time);
					if ( data.low < phl_pre.pullBack.low )       
					{	
						for ( int j = phl_pre.pullBack.pos+1; j < lastbar; j++ )
							if ( quotes[j].low < phl_pre.pullBack.low)
								return null;

						trade = new Trade(symbol);
						
						logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time );
						logger.warning(symbol + " " + CHART + "last high between " + ((QuoteData)quotes[phl_pre.prePos]).time + "@" + ((QuoteData)quotes[phl_pre.prePos]).high + "  -  " + ((QuoteData)quotes[phl_pre.curPos]).time + "@" + ((QuoteData)quotes[phl_pre.curPos]).high + " pullback@" + phl_pre.pullBack ); 

						// enter half position now and enter half at a limit order
						logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_JPY;
						trade.action = Constants.ACTION_SELL;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = data.time;
						trade.detectPrice = phl_pre.pullBack.low;

						if (previousTradeExist(trade.type,Constants.ACTION_SELL)  )
						{
							logger.info(symbol + " " + CHART + " previous trade exist " + data.time);
							return null;
						}

						// place market order
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
						trade.price = phl_pre.pullBack.low;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;
						
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
						AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
						logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

						trade.stop = Utility.getHigh( quotes, pullbackStart, lastbar).high;
						if ( trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
						
						return trade;
					}
					break;
				}
			}
		}
		else if ( ema20L[lastbarL] < ema68L[lastbarL])  
		{	
			lastConsecitveUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastConsecitveDown, 2 );

			if ( lastConsecitveUp == Constants.NOT_FOUND )
				return null;
					
			int pullbackStart = Utility.getHigh(quotes, lastConsecitveUp-10, lastConsecitveUp).pos;
			System.out.println("Pullback start is " + quotes[pullbackStart].time);
			for ( int i = lastbar; i > pullbackStart; i--)
			{	
				PushHighLow phl_pre = findLastNLow( quotes, pullbackStart, i, 2 );
				if ( phl_pre != null )
				{
					System.out.println("Last pullback from low is " + phl_pre.pullBack.time);
					if ( data.high > phl_pre.pullBack.high )       
					{	
						for ( int j = phl_pre.pullBack.pos+1; j < lastbar; j++ )
							if ( quotes[j].high > phl_pre.pullBack.high)
								return null;
						
						trade = new Trade(symbol);

						logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time );
						logger.warning(symbol + " " + CHART + " last low between " + ((QuoteData)quotes[phl_pre.prePos]).time + "-" + ((QuoteData)quotes[phl_pre.curPos]).time + " pullback@" + phl_pre.pullBack ); 

						logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_JPY;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = data.time;
						trade.detectPrice = phl_pre.pullBack.high;

						if (previousTradeExist(trade.type,Constants.ACTION_BUY) )
							return null;	

						// place market order
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = phl_pre.pullBack.high;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						trade.recordOpened = true;
						AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
						//AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.positionSize, trade.price);
						logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

						trade.stop = Utility.getLow( quotes, pullbackStart, lastbar).low;
						if ( trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new

						return trade;

					}
					break;
				}
			}
		}

		return null;
			
	}

	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public boolean checkBigTimeFramePullBackFromGoingDown(Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		if ((((QuoteData) quotes[lastbar]).low > ema20[lastbar]) || (((QuoteData) quotes[lastbar]).high < ema20[lastbar]))
			return false;

		int begin = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 3);
		Object[] matouches = Pattern.findMATouchUpsFromGoingDown(quotes, ema20, begin, lastbar);

		if ((matouches != null) && (matouches.length <= 1))
			return true;
		else
			return false;

	}

	public boolean checkBigTimeFramePullBackFromGoingUp(Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		if ((((QuoteData) quotes[lastbar]).low > ema20[lastbar]) || (((QuoteData) quotes[lastbar]).high < ema20[lastbar]))
			return false;

		int begin = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 3);
		Object[] matouches = Pattern.findMATouchDownsFromGoingUp(quotes, ema20, begin, lastbar);

		if ((matouches != null) && (matouches.length <= 1))
			return true;
		else
			return false;

	}

	public Trade CrossSetup(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);
		// int hr = new Integer(data.time.substring(9,12).trim());
		// String min = data.time.substring(13,15);

		// int lastMACrossUp = Pattern.findLastMACrossUp(ema20, ema50, 12);
		// int lastMACrossDown = Pattern.findLastMACrossDown(ema20, ema50, 12);
		// int lastPriceConsectiveUp =
		// Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar-2, 3);
		// int lastPriceConsectiveDown =
		// Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar -2, 3);
		// int lastConsectiveUp = Pattern.findLastPriceConsectiveAboveMA(
		// quotes, ema20, 5);
		// int lastConsectiveDown = Pattern.findLastPriceConsectiveBelowMA(
		// quotes, ema20, 5);
		int lastPriceConsectiveUpL = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		int lastPriceConsectiveDownL = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);

		if ((((QuoteData) quotes[lastbar]).high < ema20[lastbar]) && (((QuoteData) quotes[lastbar - 1]).high < ema20[lastbar - 1]))
		{
			int pos = lastbar;
			while (((QuoteData) quotes[pos]).low < ema20[pos])
				pos--;

			// now it is the touch point
			// 1. look for the first place that it was below MA
			int begin = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, pos, 3);

			// 2. check how many "up" peaks in between
			Object[] peaks = Pattern.findPastHighPeaks2AboveMA(quotes, ema20, begin, pos);

			if ((peaks == null) || (peaks.length < 2))
				return null;

			// if ( ema20L[lastbarL]> ema50L[lastbarL])
			if (lastPriceConsectiveUpL > lastPriceConsectiveDownL)
				return null;

			trade = new Trade(symbol);
			logger.warning(symbol + " " + CHART + " minute cross to low detected at " + data.time);
			trade.type = Constants.TRADE_CRS;
			trade.action = Constants.ACTION_SELL;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.detectTime = ((QuoteData) quotes[lastbar]).time;
			trade.detectPos = lastbar;
			trade.status = Constants.STATUS_OPEN;

			return trade;
		}
		else if ((((QuoteData) quotes[lastbar]).low > ema20[lastbar]) && (((QuoteData) quotes[lastbar - 1]).low > ema20[lastbar - 1]))
		{
			int pos = lastbar;
			while (((QuoteData) quotes[pos]).high > ema20[pos])
				pos--;

			// now it is the touch point
			// 1. look for the first place that it was below MA
			int begin = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, pos, 3);

			// 2. check how many "up" peaks in between
			Object[] peaks = Pattern.findPastLowPeaks2BelowMA(quotes, ema20, begin, pos);

			if ((peaks == null) || (peaks.length < 2))
				return null;

			// if ( ema20L[lastbarL]< ema50L[lastbarL])
			if (lastPriceConsectiveUpL < lastPriceConsectiveDownL)
				return null;

			trade = new Trade(symbol);
			logger.warning(symbol + " " + CHART + " minute cross to high detected at " + data.time);
			trade.type = Constants.TRADE_CRS;
			trade.action = Constants.ACTION_BUY;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.detectTime = ((QuoteData) quotes[lastbar]).time;
			trade.detectPos = lastbar;
			trade.status = Constants.STATUS_OPEN;

			return trade;
		}

		return null;
	}



	public Trade HigherHighLowerLowRSTSetup(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);

		int lastConsectiveUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 5);
		int lastConsectiveDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 5);

		if (lastConsectiveUp > lastConsectiveDown)
		{
			int begin = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar - 3, 5);

			MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, lastbar);
			/*
			 * logger.warning(symbol + " " + CHART + " trend is up " +
			 * data.time); logger.warning(symbol + " " + CHART +
			 * " last consecitveAbove20MA is " +
			 * ((QuoteData)quotes[lastConsectiveUp]).time);
			 * logger.warning(symbol + " " + CHART +
			 * " last consecitveBelow20MA is " +
			 * ((QuoteData)quotes[lastConsectiveDown]).time);
			 * logger.warning(symbol + " " + CHART +
			 * " last 20MA touch up low is " + mat.lowest );
			 */
			if (data.low < mat.low.low)
			{
				// requires two higher high
				int lastlow = mat.low.pos;
				while (((QuoteData) quotes[lastlow]).low < ema20[lastlow])
					lastlow--;
				// now lastlow is above again
				MATouch mat2 = Pattern.findLastMATouchUp(quotes, ema20, lastlow);
				if (!(mat.low.low > mat2.low.low))
					return null;

				// last check large data chart
				trade = new Trade(symbol);
				logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time);
				logger.warning(symbol + " " + CHART + " last 20MA touch up low is " + mat.low.time);

				trade.type = Constants.TRADE_RST;
				trade.action = Constants.ACTION_SELL;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.detectTime = ((QuoteData) quotes[lastbar]).time;
				trade.detectPrice = mat.low.low;
				trade.detectPos = lastbar;
				trade.status = Constants.STATUS_OPEN;

				trade.entryPrice = Utility.getHigh(quotes, begin, lastbar).high;
				return trade;

			}

		}
		else if (lastConsectiveUp < lastConsectiveDown)
		{
			int begin = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar - 3, 5);
			MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, lastbar);

			if (data.high > mat.high.high)
			{
				// requires two higher high
				int lasthigh = mat.high.pos;
				while (((QuoteData) quotes[lasthigh]).high > ema20[lasthigh])
					lasthigh--;
				// now lastlow is above again
				MATouch mat2 = Pattern.findLastMATouchDown(quotes, ema20, lasthigh);
				if (!(mat.high.high < mat2.high.high))
					return null;

				trade = new Trade(symbol);

				logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time);
				logger.warning(symbol + " " + CHART + " last 20MA touch down high is " + mat.high.time);

				trade.type = Constants.TRADE_RST;
				trade.action = Constants.ACTION_BUY;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.detectTime = ((QuoteData) quotes[lastbar]).time;
				trade.detectPrice = mat.high.high;
				trade.detectPos = lastbar;
				trade.status = Constants.STATUS_OPEN;

				trade.entryPrice = Utility.getLow(quotes, begin, lastbar).low;
				return trade;

			}

		}
		return null;

	}

	public void HigherHighLowerLowRSTSetupEntry(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +
		// trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if (data.close > trade.entryPrice)
			{
				// place order
				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				trade.price = data.close;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				// AddTradeOpenRecord( trade.type, data.time,
				// Constants.ACTION_SELL, trade.positionSize, data.close);
				AddTradeOpenRecord(trade.type, ((QuoteData) quotes[lastbar]).time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);

				trade.stop = data.close + 14 * PIP_SIZE;

				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return;
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if (data.close < trade.entryPrice)
			{
				// place order
				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				trade.price = data.close;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				// AddTradeOpenRecord( trade.type, data.time,
				// Constants.ACTION_BUY, trade.positionSize, data.close);
				AddTradeOpenRecord(trade.type, ((QuoteData) quotes[lastbar]).time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);

				trade.stop = data.close - 14 * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());
				return;

			}
		}
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Trade Entry
	//
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void trackTradeEntry(QuoteData data, QuoteData[] quotesS, QuoteData[] quotesL)
	{
		if ((MODE == Constants.TEST_MODE) /*&& Constants.STATUS_PLACED.equals(trade.status)*/)
			checkStopTarget(data);

		if (trade != null)
		{
			/*if (Constants.TRADE_REV.equals(trade.type))
			{
				if (Constants.ACTION_SELL.equals(trade.action))
				{
					trackReversalTradeSell( data.low, quotesS, quotesL);
				}
				else if (Constants.ACTION_BUY.equals(trade.action))
				{
					trackReversalTradeBuy( data.high, quotesS, quotesL);
				}
				return;
			}*/
			
			if (Constants.TRADE_PBK.equals(trade.type))
			{
				if (Constants.ACTION_SELL.equals(trade.action))
				{
					trackPullBackTradeSell( data.low, quotesS, quotesL);
				}
				else if (Constants.ACTION_BUY.equals(trade.action))
				{
					trackPullBackTradeBuy( data.high, quotesS, quotesL);
				}
				return;
			}
		}
	}


	
	public void trackREVTradeEntry(QuoteData data, QuoteData[] quotesS, QuoteData[] quotesL)
	{
		//if ((MODE == Constants.TEST_MODE) /*&& Constants.STATUS_PLACED.equals(trade.status)*/)
			//checkStopTarget(data);
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		
		int triggerPosL = REVTrade.phl.curPos;
		int prevPosL = REVTrade.phl.prePos;
		
		if ( lastbarL == triggerPosL )
			return;

		if (Constants.ACTION_SELL.equals(REVTrade.action))
		{
			if (( quotesL[triggerPosL].high - quotesL[prevPosL].high) > 5 * PIP_SIZE )
			{
				REVTrade = null;
				return;
			}
			
			
			if  (quotesL[lastbarL].low < quotesL[lastbarL-1].low )
			{
				double high = Utility.getHigh( quotesL, triggerPosL, lastbarL).high;
				if (( high - quotesL[prevPosL].high) > 5 * PIP_SIZE )
				{
					REVTrade = null;
					return;
				}
				else
				{
					logger.info(symbol + " " + CHART + " REVERSE trade traggered 1 sell at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
					REVTrade.stop = Utility.getHigh( quotesL, triggerPosL, lastbarL-1).high;
					if ( getTrade() == null )
					{
						trade = REVTrade;
						enterMarketPosition(quotesL[lastbarL-1].low);
					}
					REVTrade = null;
					return;
				}
			}

			int triggerPos = Utility.findPositionByHour(quotesL, REVTrade.phl.pullBack.time, 2 );
			// this is on 15 min
			for ( int i = lastbar; i > triggerPos + 2; i--)
			{	
				PushHighLow phl = Pattern.findLastNHigh(quotes, triggerPos, i, 1 );
				if ( phl!= null )
				{
					//logger.info(symbol + " " + CHART + " trade entry push detected at " + quotes[phl.curPos].time );
					if  (( lastbar >= phl.curPos + 1 ) && ( quotes[lastbar].low < quotes[lastbar-1].low ))
					{
						logger.info(symbol + " " + CHART + " REVERSE trade traggered 2 sell at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						//trade.stop = adjustPrice(Utility.getHigh(quotes,triggerPos, lastbar).high, Constants.ADJUST_TYPE_UP);
						if ( getTrade() == null )
						{
							trade = REVTrade;
							enterMarketPosition(quotes[lastbar-1].low);
						}
						REVTrade = null;
						return;
					}
				}
				
			}
		}
		else if (Constants.ACTION_BUY.equals(REVTrade.action))
		{
			if (( quotesL[prevPosL].low - quotesL[triggerPosL].low ) > 5 * PIP_SIZE )
			{
				REVTrade = null;
				return;
			}

			if  (quotesL[lastbarL].high > quotesL[lastbarL-1].high )
			{
				double low = Utility.getLow( quotesL, triggerPosL, lastbarL).low;
				if (( quotesL[prevPosL].high - low) > 5 * PIP_SIZE )
				{
					REVTrade = null;
					return;
				}
				else
				{
					logger.info(symbol + " " + CHART + " REVERSE trade traggered 1 buy at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
					REVTrade.stop = Utility.getLow( quotesL, triggerPosL, lastbarL-1).low;
					if ( getTrade() == null )
					{
						trade = REVTrade;
						enterMarketPosition(quotesL[lastbarL-1].high);
					}
					REVTrade = null;
					return;
				}
			}

			int triggerPos = Utility.findPositionByHour(quotesL, REVTrade.phl.pullBack.time, 2 );

			for ( int i = lastbar; i > triggerPos + 2; i--)
			{	
				PushHighLow phl = Pattern.findLastNLow(quotes, triggerPos, i, 1 );
				if ( phl!= null )
				{
					//logger.info(symbol + " " + CHART + " trade entry push detected at " + quotes[phl.curPos].time );
					if  (( lastbar >= phl.curPos + 1 ) && ( quotes[lastbar].high > quotes[lastbar-1].high ))
					{
						logger.info(symbol + " " + CHART + " REVERSE trade traggered 2 buy at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						if ( getTrade() == null )
						{
							trade = REVTrade;
							enterMarketPosition(quotes[lastbar-1].high);
						}
						REVTrade = null;
						return;
					}
				}
			}
		}
	}
			

	public void trackREVTradeEntry2(QuoteData data, QuoteData[] quotesS, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		int lastbarS = quotesS.length - 1;
		
		int triggerPosL = REVTrade.phl.curPos;
		int prevPosL = REVTrade.phl.prePos;
		
		if (Constants.ACTION_SELL.equals(REVTrade.action))
		{
			// look for first 3 bars after the break, if it is big, we wait for the next push
			/*
			int firstLow = REVTrade.entryPosS +1;
			
			while (( quotesS[firstLow].high > quotes[firstLow-1].high) && ( firstLow < lastbarS))
				firstLow ++;
			
			if (( firstLow == lastbarS ) || ( lastbarS == (REVTrade.entryPosS + 1)))
				return;  // haven't see the first one
			
			info("first low after entry is " + quotesS[firstLow].time);
			
			QuoteData highAfterEntry = Utility.getHigh( quotesS, REVTrade.entryPosS, firstLow-1);
			
			if (( highAfterEntry.high - REVTrade.triggerPrice) < 8 * PIP_SIZE )
			{
				// enter trade;
				trade = REVTrade;
				info("enter trade at first low @ " + quotesS[firstLow].close );
				enterMarketPosition(quotesS[firstLow].close);
			}
			else
			{
				// wait for the second push
				for ( int i = firstLow; i <= lastbarS; i++)
				{	
					PushHighLow phl = Pattern.findLastNHigh(quotes, REVTrade.detectPosS, i, 2 );
					if ( phl != null )
					{
						trade = REVTrade;
						info("enter trade at second push @ " + quotesS[phl.curPos].high );
						enterMarketPosition(quotesS[phl.curPos].high);
						
					}
				}
			}*/

		}
		else if (Constants.ACTION_BUY.equals(REVTrade.action))
		{
			
		}
	}

	public void trackREVTradeEntry1(QuoteData data, QuoteData[] quotesS, QuoteData[] quotesL)
	{
		//if ((MODE == Constants.TEST_MODE) /*&& Constants.STATUS_PLACED.equals(trade.status)*/)
			//checkStopTarget(data);
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		
		int triggerPosL = REVTrade.phl.curPos;
		int prevPosL = REVTrade.phl.prePos;
		
		if ( lastbarL == triggerPosL )
			return;

		if (Constants.ACTION_SELL.equals(REVTrade.action))
		{
			if ((lastbarL == triggerPosL + 1 ) || (lastbarL == triggerPosL + 2 ))
			{	
				if  (quotesL[lastbarL].low < quotesL[lastbarL-1].low )
				{
					double high = Utility.getHigh( quotesL, triggerPosL, lastbarL).high;
					if (( high - quotesL[prevPosL].high) > 5 * PIP_SIZE )
					{
						REVTrade = null;
						return;
					}
					else
					{
						logger.info(symbol + " " + CHART + " REVERSE trade traggered 1 sell at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						REVTrade.stop = Utility.getHigh( quotesL, triggerPosL, lastbarL-1).high;
						if ( getTrade() == null )
							enterMarketPosition(REVTrade, quotesL[lastbarL-1].low);
						REVTrade = null;
						return;
					}
				}
			}

			int triggerPos = Utility.findPositionByHour(quotesL, REVTrade.phl.pullBack.time, 2 );
			// this is on 15 min
			for ( int i = lastbar; i > triggerPos + 2; i--)
			{	
				PushHighLow phl = Pattern.findLastNHigh(quotes, triggerPos, i, 1 );
				if ( phl!= null )
				{
					//logger.info(symbol + " " + CHART + " trade entry push detected at " + quotes[phl.curPos].time );
					if  (( lastbar >= phl.curPos + 1 ) && ( quotes[lastbar].low < quotes[lastbar-1].low ))
					{
						logger.info(symbol + " " + CHART + " REVERSE trade traggered 2 sell at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						//trade.stop = adjustPrice(Utility.getHigh(quotes,triggerPos, lastbar).high, Constants.ADJUST_TYPE_UP);
						if ( getTrade() == null )
							enterMarketPosition(REVTrade, quotes[lastbar-1].low);
						REVTrade = null;
						return;
					}
				}
				
			}
		}
		else if (Constants.ACTION_BUY.equals(REVTrade.action))
		{
			if ((lastbarL == triggerPosL + 1 ) || (lastbarL == triggerPosL + 2 ))
			{
				if  (quotesL[lastbarL].high > quotesL[lastbarL-1].high )
				{
					double low = Utility.getLow( quotesL, triggerPosL, lastbarL).low;
					if (( quotesL[prevPosL].high - low) > 5 * PIP_SIZE )
					{
						REVTrade = null;
						return;
					}
					else
					{
						logger.info(symbol + " " + CHART + " REVERSE trade traggered 1 buy at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						REVTrade.stop = Utility.getLow( quotesL, triggerPosL, lastbarL-1).low;
						if ( getTrade() == null )
							enterMarketPosition(REVTrade, quotesL[lastbarL-1].high);
						REVTrade = null;
						return;
					}
				}
			}

			int triggerPos = Utility.findPositionByHour(quotesL, REVTrade.phl.pullBack.time, 2 );

			for ( int i = lastbar; i > triggerPos + 2; i--)
			{	
				PushHighLow phl = Pattern.findLastNLow(quotes, triggerPos, i, 1 );
				if ( phl!= null )
				{
					//logger.info(symbol + " " + CHART + " trade entry push detected at " + quotes[phl.curPos].time );
					if  (( lastbar >= phl.curPos + 1 ) && ( quotes[lastbar].high > quotes[lastbar-1].high ))
					{
						logger.info(symbol + " " + CHART + " REVERSE trade traggered 2 buy at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						if ( getTrade() == null )
							enterMarketPosition(REVTrade,quotes[lastbar-1].high);
						REVTrade = null;
						return;
					}
				}
			}
		}
	}

	
	

	
	
	
	
	
	
	

	public void trackPBKTradeEntry(QuoteData data, QuoteData[] quotesS, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int prevPos = PBKTrade.phl.prePos;
		int triggerPos = PBKTrade.phl.curPos;
		
		if (Constants.ACTION_SELL.equals(PBKTrade.action))
		{
			if (quotesL[lastbarL].low < quotesL[PBKTrade.pullBackStartL].low) 
			{
				logger.info(symbol + " " + CHART + " price return to below pull back low, trade missed");
				PBKTrade = null;
				return;
			}
	
			int touchPos = 	Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 2);
			while ( quotesL[touchPos].high < ema20L[touchPos])
				touchPos--;
			int touchPosBegin = touchPos;
			while ( quotesL[touchPosBegin].high > ema20L[touchPosBegin])
				touchPosBegin--;
			QuoteData lastTouch20MA = Utility.getHigh(quotesL, touchPosBegin+1, touchPos);
			if (( lastTouch20MA.pos > PBKTrade.pushStartL ) && ( quotesL[lastbarL].high > lastTouch20MA.high ))
			{
				System.out.println(symbol + " " + CHART + " breaks last low, trade cancelled");
				PBKTrade = null;
				return;
				
			}
	
			
			for ( int i = lastbarL; i > PBKTrade.pushStartL + 2; i--)
			{
				PushHighLow[] phls = Pattern.findPast2Lows(quotesL, PBKTrade.pushStartL, i );
				if ( phls != null )
				{
					if ( quotesL[lastbarL].high > phls[0].pullBack.high )
					{
						System.out.println(symbol + " " + CHART + " breaks last high, trade cancelled");
						PBKTrade = null;
						return;
					}
					break;
				}
			}
			
	
			
			System.out.println(symbol + " " + CHART + " trigger position is at " + quotes[triggerPos].time);
			if (lastbar == triggerPos + 2 )
			{
				// now is the third bar;
				if (( quotes[triggerPos].high - quotes[prevPos].high) < 5 * PIP_SIZE )
				{
					if  (( quotes[triggerPos+1].high - quotes[triggerPos].high) < 3 * PIP_SIZE )
					{
						logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						if ( getTrade() == null )
							enterMarketPosition(PBKTrade, quotes[lastbar].close);
						PBKTrade = null;
						return;
					}
				}
			}
			
			
			for ( int i = triggerPos + 2; i <= lastbar; i++)
			{	
				PushHighLow phl = Pattern.findLastNHigh(quotes, triggerPos, i, 1 );
				if ( phl!= null )
				{
					logger.info(symbol + " " + CHART + " trade entry push detected at " + quotes[phl.curPos].time );
					if  (( lastbar >= phl.curPos + 1 ) && ( quotes[lastbar].low < quotes[lastbar-1].low ))
					{
						logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar-1].low + " @ " + quotes[lastbar].time);
						//trade.stop = adjustPrice(Utility.getHigh(quotes,triggerPos, lastbar).high, Constants.ADJUST_TYPE_UP);
						if ( getTrade() == null )
							enterMarketPosition(PBKTrade, quotes[lastbar].close);
						PBKTrade = null;
						return;
					}
				}
				
			}
	
			// enter at the break of lower low 
			/*
			for ( int i = lastbar-2; i > triggerPos; i--)
			{
				if ( quotes[i+1].low > quotes[i].low )
				{
					for ( int j = i+2; j < lastbar; j++ )
					{
						if 	( quotes[j].low < quotes[i].low)
							break;	// already passed
					}
					
					if ( quotes[lastbar].low < quotes[i].low)
					{
						logger.info(symbol + " " + CHART + " trade entered with breaking lower high at " + quotes[i].high + " @ " + quotes[lastbar].time);
						enterMarketPosition(quotes[lastbar].close);
						trade.status = Constants.STATUS_PLACED;
						return;
					}
					break;
				}
			}*/
		}
		else if (Constants.ACTION_BUY.equals(PBKTrade.action))
		{
			if (quotesL[lastbarL].high > quotesL[PBKTrade.pullBackStartL].high ) 
			{
				logger.info(symbol + " " + CHART + " price return to above pull back high, trade missed");
				PBKTrade = null;
				return;
				
			}

			int touchPos = 	Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
			while ( quotesL[touchPos].low > ema20L[touchPos])
				touchPos--;
			int touchPosBegin = touchPos;
			while ( quotesL[touchPosBegin].low < ema20L[touchPosBegin])
				touchPosBegin--;
			QuoteData lastTouch20MA = Utility.getLow(quotesL, touchPosBegin+1, touchPos);
			if (( lastTouch20MA.pos > PBKTrade.pushStartL ) && ( quotesL[lastbarL].low < lastTouch20MA.low ))
			{
				System.out.println(symbol + " " + CHART + " breaks last low, trade cancelled");
				PBKTrade = null;
				return;
				
			}
			
			
			/*
			for ( int i = trade.phl.prePos+1; i < trade.phl.curPos; i++)
			{
				if (quotes[i].low < quotes[trade.phl.prePos].low)
				{
					triggerPos = i;
					break;
				}
			}
			//System.out.println("Trigger pos is " + quotes[triggerPos].time);
			//System.out.println("current pos is " + quotes[lastbar].time);
			
			if (lastbar == triggerPos + 1 )
			{
				if (((quotes[prevPos].low - quotes[triggerPos].low) < 5 * PIP_SIZE ) && ( quotes[lastbar].high > quotes[triggerPos].high ))
				{
					logger.info(symbol + " " + CHART + " first trigger height is " + (quotes[prevPos].low - quotes[triggerPos].low)/PIP_SIZE + " pips");
					enterMarketPosition(quotes[triggerPos].high);
					return;
				}
				else
				{
					System.out.println(symbol + " " + CHART + " first trigger height is " + (quotes[prevPos].low - quotes[triggerPos].low)/PIP_SIZE + " does not qualify");
				}
			}
			
			if (lastbar == triggerPos + 2 )
			{
				// now is the third bar;
				if (( quotes[prevPos].low - quotes[triggerPos].low ) < 5 * PIP_SIZE )
				{
					if  (( quotes[triggerPos].low - quotes[triggerPos+1].low ) < 3 * PIP_SIZE )
					{
						logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						enterMarketPosition(quotes[lastbar].close);
						trade.status = Constants.STATUS_PLACED;
						return;
					}
				}
			}*/
			
			
			if (lastbar == triggerPos + 2 )
			{
				// now is the third bar;
				if (( quotes[prevPos].low - quotes[triggerPos].low ) < 5 * PIP_SIZE )
				{
					if  (( quotes[triggerPos].low - quotes[triggerPos+1].low ) < 3 * PIP_SIZE )
					{
						logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						if ( getTrade() == null )
							enterMarketPosition(PBKTrade, quotes[lastbar].close);
						PBKTrade = null;
						return;
					}
				}
			}

			
			// enter at the turn after second push
			for ( int i = triggerPos + 2; i <= lastbar; i++)
			{	
				PushHighLow phl = Pattern.findLastNLow(quotes, triggerPos, i, 1 );
				if ( phl!= null )
				{
					logger.info(symbol + " " + CHART + " trade entry push detected at " + quotes[phl.curPos].time );
					if  (( lastbar >= phl.curPos + 1 ) && ( quotes[lastbar].high > quotes[lastbar-1].high ))
					{
						logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar-1].high + " @ " + quotes[lastbar].time);
						if ( getTrade() == null )
							enterMarketPosition(PBKTrade, quotes[lastbar].close);
						PBKTrade = null;
						return;
					}
				}
				
			}
			
			// enter at the break of lower low 
			/*
			for ( int i = lastbar-2; i > triggerPos; i--)
			{
				if ( quotes[i+1].high < quotes[i].high )
				{
					for ( int j = i+2; j < lastbar; j++ )
					{
						if 	( quotes[j].high > quotes[i].high)
							break;	// already passed
					}
					
					if ( quotes[lastbar].high > quotes[i].high)
					{
						logger.info(symbol + " " + CHART + " trade entered with breaking lower high at " + quotes[i].high + " @ " + quotes[lastbar].time);
						trade.stop = adjustPrice(Utility.getLow(quotes,triggerPos, lastbar).low, Constants.ADJUST_TYPE_DOWN);
						enterMarketPosition(quotes[lastbar].close);
						trade.status = Constants.STATUS_PLACED;
						return;
					}
					
					break;
				}
			}*/
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	
	
	
	public void trackPullBackTradeSell(double price, QuoteData[] quotesS, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int prevPos = trade.phl.prePos;
		int triggerPos = trade.phl.curPos;
		
		if (price < quotesL[trade.pullBackStartL].low) 
		{
			logger.info(symbol + " " + CHART + " price return to below pull back low, trade missed");
			trade = null;
			return;
			
		}

		int touchPos = 	Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 2);
		while ( quotesL[touchPos].high < ema20L[touchPos])
			touchPos--;
		int touchPosBegin = touchPos;
		while ( quotesL[touchPosBegin].high > ema20L[touchPosBegin])
			touchPosBegin--;
		QuoteData lastTouch20MA = Utility.getHigh(quotesL, touchPosBegin+1, touchPos);
		if (( lastTouch20MA.pos > trade.pushStartL ) && ( quotesL[lastbarL].high > lastTouch20MA.high ))
		{
			System.out.println(symbol + " " + CHART + " breaks last low, trade cancelled");
			trade = null;
			return;
			
		}

		
		for ( int i = lastbarL; i > trade.pushStartL + 2; i--)
		{
			PushHighLow[] phls = Pattern.findPast2Lows(quotesL, trade.pushStartL, i );
			if ( phls != null )
			{
				if ( quotesL[lastbarL].high > phls[0].pullBack.high )
				{
					System.out.println(symbol + " " + CHART + " breaks last high, trade cancelled");
					trade = null;
					return;
				}
				break;
			}
		}
		

		
		System.out.println(symbol + " " + CHART + " trigger position is at " + quotes[triggerPos].time);
		if (lastbar == triggerPos + 2 )
		{
			// now is the third bar;
			if (( quotes[triggerPos].high - quotes[prevPos].high) < 5 * PIP_SIZE )
			{
				if  (( quotes[triggerPos+1].high - quotes[triggerPos].high) < 3 * PIP_SIZE )
				{
					logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
					enterMarketPosition(quotes[lastbar].close);
					trade.status = Constants.STATUS_PLACED;
					return;
				}
			}
		}
		
		
		for ( int i = triggerPos + 2; i <= lastbar; i++)
		{	
			PushHighLow phl = Pattern.findLastNHigh(quotes, triggerPos, i, 1 );
			if ( phl!= null )
			{
				logger.info(symbol + " " + CHART + " trade entry push detected at " + quotes[phl.curPos].time );
				if  (( lastbar >= phl.curPos + 1 ) && ( quotes[lastbar].low < quotes[lastbar-1].low ))
				{
					logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar-1].low + " @ " + quotes[lastbar].time);
					//trade.stop = adjustPrice(Utility.getHigh(quotes,triggerPos, lastbar).high, Constants.ADJUST_TYPE_UP);
					enterMarketPosition(quotes[lastbar].close);
					trade.status = Constants.STATUS_PLACED;
					return;
				}
			}
			
		}

		// enter at the break of lower low 
		/*
		for ( int i = lastbar-2; i > triggerPos; i--)
		{
			if ( quotes[i+1].low > quotes[i].low )
			{
				for ( int j = i+2; j < lastbar; j++ )
				{
					if 	( quotes[j].low < quotes[i].low)
						break;	// already passed
				}
				
				if ( quotes[lastbar].low < quotes[i].low)
				{
					logger.info(symbol + " " + CHART + " trade entered with breaking lower high at " + quotes[i].high + " @ " + quotes[lastbar].time);
					enterMarketPosition(quotes[lastbar].close);
					trade.status = Constants.STATUS_PLACED;
					return;
				}
				break;
			}
		}*/
	}


	
	public void trackPullBackTradeBuy(double price, QuoteData[] quotesS, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int prevPos = trade.phl.prePos;
		int triggerPos = trade.phl.curPos;
		
		if (price > quotesL[trade.pullBackStartL].high ) 
		{
			logger.info(symbol + " " + CHART + " price return to above pull back high, trade missed");
			trade = null;
			return;
			
		}

		int touchPos = 	Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
		while ( quotesL[touchPos].low > ema20L[touchPos])
			touchPos--;
		int touchPosBegin = touchPos;
		while ( quotesL[touchPosBegin].low < ema20L[touchPosBegin])
			touchPosBegin--;
		QuoteData lastTouch20MA = Utility.getLow(quotesL, touchPosBegin+1, touchPos);
		if (( lastTouch20MA.pos > trade.pushStartL ) && ( quotesL[lastbarL].low < lastTouch20MA.low ))
		{
			System.out.println(symbol + " " + CHART + " breaks last low, trade cancelled");
			trade = null;
			return;
			
		}
		
		
		/*
		for ( int i = trade.phl.prePos+1; i < trade.phl.curPos; i++)
		{
			if (quotes[i].low < quotes[trade.phl.prePos].low)
			{
				triggerPos = i;
				break;
			}
		}
		//System.out.println("Trigger pos is " + quotes[triggerPos].time);
		//System.out.println("current pos is " + quotes[lastbar].time);
		
		if (lastbar == triggerPos + 1 )
		{
			if (((quotes[prevPos].low - quotes[triggerPos].low) < 5 * PIP_SIZE ) && ( quotes[lastbar].high > quotes[triggerPos].high ))
			{
				logger.info(symbol + " " + CHART + " first trigger height is " + (quotes[prevPos].low - quotes[triggerPos].low)/PIP_SIZE + " pips");
				enterMarketPosition(quotes[triggerPos].high);
				return;
			}
			else
			{
				System.out.println(symbol + " " + CHART + " first trigger height is " + (quotes[prevPos].low - quotes[triggerPos].low)/PIP_SIZE + " does not qualify");
			}
		}
		
		if (lastbar == triggerPos + 2 )
		{
			// now is the third bar;
			if (( quotes[prevPos].low - quotes[triggerPos].low ) < 5 * PIP_SIZE )
			{
				if  (( quotes[triggerPos].low - quotes[triggerPos+1].low ) < 3 * PIP_SIZE )
				{
					logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
					enterMarketPosition(quotes[lastbar].close);
					trade.status = Constants.STATUS_PLACED;
					return;
				}
			}
		}*/
		
		
		if (lastbar == triggerPos + 2 )
		{
			// now is the third bar;
			if (( quotes[prevPos].low - quotes[triggerPos].low ) < 5 * PIP_SIZE )
			{
				if  (( quotes[triggerPos].low - quotes[triggerPos+1].low ) < 3 * PIP_SIZE )
				{
					logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
					enterMarketPosition(quotes[lastbar].close);
					trade.status = Constants.STATUS_PLACED;
					return;
				}
			}
		}

		
		// enter at the turn after second push
		for ( int i = triggerPos + 2; i <= lastbar; i++)
		{	
			PushHighLow phl = Pattern.findLastNLow(quotes, triggerPos, i, 1 );
			if ( phl!= null )
			{
				logger.info(symbol + " " + CHART + " trade entry push detected at " + quotes[phl.curPos].time );
				if  (( lastbar >= phl.curPos + 1 ) && ( quotes[lastbar].high > quotes[lastbar-1].high ))
				{
					logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar-1].high + " @ " + quotes[lastbar].time);
					enterMarketPosition(quotes[lastbar].close);
					trade.status = Constants.STATUS_PLACED;
					return;
				}
			}
			
		}
		
		// enter at the break of lower low 
		/*
		for ( int i = lastbar-2; i > triggerPos; i--)
		{
			if ( quotes[i+1].high < quotes[i].high )
			{
				for ( int j = i+2; j < lastbar; j++ )
				{
					if 	( quotes[j].high > quotes[i].high)
						break;	// already passed
				}
				
				if ( quotes[lastbar].high > quotes[i].high)
				{
					logger.info(symbol + " " + CHART + " trade entered with breaking lower high at " + quotes[i].high + " @ " + quotes[lastbar].time);
					trade.stop = adjustPrice(Utility.getLow(quotes,triggerPos, lastbar).low, Constants.ADJUST_TYPE_DOWN);
					enterMarketPosition(quotes[lastbar].close);
					trade.status = Constants.STATUS_PLACED;
					return;
				}
				
				break;
			}
		}*/
		
		
		
		
	}

	
	
	
	
	
	


	synchronized void checkTriggerMarketSell(double price)
	{
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if ( trade.limitEntryOrderPlaced == true )
			return;

		if (( trade.entryPullBackLow != 0 ) && ( price < trade.entryPullBackLow))
		{
			/*
			logger.warning(symbol + " " + CHART + "!!!!!!!!" + ((QuoteData)quotes[lastbar]).time + " detected below last low - "  );
    	    logger.warning(symbol + " " + CHART + " last pull back is " +
					 ((QuoteData)quotes[trade.phl_cur.prePos]).time + "@" +
					 ((QuoteData)quotes[trade.phl_cur.prePos]).high + "  -  " +
					 ((QuoteData)quotes[trade.phl_cur.curPos]).time + "@" +
					 ((QuoteData)quotes[trade.phl_cur.curPos]).high + " pullback@" +
					 trade.phl_cur.pullBack.time);
			*/

			int entryPullBackPos = 	Utility.findPositionByMinute(quotes, trade.entryPullBackTime, Constants.BACK_TO_FRONT );
			for (int j = entryPullBackPos + 1; j < lastbar; j++)
			{
				if ( quotes[j].low < trade.entryPullBackLow)
				{
					logger.warning(symbol + " " + CHART + quotes[j].time + " below pullback low of "
							+ trade.entryPullBackTime + " , trade missed");
					trade = null;
					return;
				}
			}

			double highest = Utility.getHigh(quotes, entryPullBackPos, lastbar).high;
			if ((highest - price ) > 40 * PIP_SIZE)
			{
				logger.warning(symbol + " " + CHART + " entry too wide ");
				trade = null;
				return;
			}
			
			
			// remove all limit order
			cancelOrder(trade.limitId1);
			cancelOrder(trade.limitId2);
			trade.limitId1 = trade.limitId2 = 0;

			trade.pullBackPos = entryPullBackPos;  // required for history

			if (( LIMIT_ORDER == false ) || ( MODE == Constants.TEST_MODE ))
			{
				// place market order
				logger.warning(symbol + " " + CHART + " sell order triggered at " + quotes[lastbar].time);
				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				trade.price = trade.entryPullBackLow;//(MODE == Constants.TEST_MODE)?trade.entryPullBackLow:price;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = quotes[lastbar].time;
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
				// AddTradeOpenRecord( trade.type,data.time, Constants.ACTION_SELL, trade.positionSize, trade.price);

				// re-addjust stop
				cancelOrder(trade.stopId);
				//trade.stop = Utility.getHigh(quotes, trade.phl_cur.pullBack.pos, lastbar).high;
				//if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
					trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);
			}
			else if ( trade.limitEntryOrderPlaced == true ) 
			{
				// limit order
				trade.limitPrice1 = adjustPrice(trade.entryPullBackLow, Constants.ADJUST_TYPE_UP);
				trade.limitPos1 = trade.POSITION_SIZE;
				logger.warning(symbol + " " + CHART + " place limite entry order of " + trade.limitPrice1 + " on " + trade.limitPos1);
				trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);

				trade.limit1Stop = Utility.getHigh(quotes, entryPullBackPos, lastbar).high;
				if (trade.limit1Stop - trade.limitPrice1 > FIXED_STOP * PIP_SIZE)
					trade.limit1Stop = trade.limitPrice1 + FIXED_STOP * PIP_SIZE;

				trade.limit1Stop = adjustPrice(trade.limit1Stop, Constants.ADJUST_TYPE_UP);
				trade.limitEntryOrderPlaced = true;

			}
			return;
		}

	}


	
	
	
	synchronized void checkTriggerMarketBuy(double price)
	{
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if ( trade.limitEntryOrderPlaced == true )
			return;

		if (( trade.entryPullBackHigh != 0 ) && ( price > trade.entryPullBackHigh))
		{
			/*check
			 * logger.warning("@@@@@@@@@@@@@-" + data.time); for (
			 * int j = 0; j < phls.length; j++) { PushHighLow ppp =
			 * phls[j]; logger.warning(symbol + " " + CHART +
			 * "@@@this high between " +
			 * ((QuoteData)quotes[ppp.prePos]).time + "@" +
			 * ((QuoteData)quotes[ppp.prePos]).high + "  -  " +
			 * ((QuoteData)quotes[ppp.curPos]).time + "@" +
			 * ((QuoteData)quotes[ppp.curPos]).high + " pullback@" +
			 * ppp.pullBack.time ); }
			 */

			int entryPullBackPos = 	Utility.findPositionByMinute(quotes, trade.entryPullBackTime, Constants.BACK_TO_FRONT );
			for (int j = entryPullBackPos + 1; j < lastbar; j++)
			{
				if ( quotes[j].high > trade.entryPullBackHigh)
				{
					logger.warning(symbol + " " + CHART + " " + ((QuoteData) quotes[j]).time + " high > pull back high of "
							+ trade.entryPullBackTime + " trade missed");
					trade = null;
					return;
				}
			}

			double lowest = Utility.getLow(quotes, entryPullBackPos, lastbar).low;
			if ((price - lowest) > 40 * PIP_SIZE)
			{
				logger.warning(symbol + " " + CHART + " entry too wide ");
				trade = null;
				return;
			}

			cancelOrder(trade.limitId1);
			cancelOrder(trade.limitId2);
			trade.limitId1 = trade.limitId2 = 0;

			trade.pullBackPos = entryPullBackPos;  // required for history

			if (( LIMIT_ORDER == false ) || ( MODE == Constants.TEST_MODE ))
			{
				// place order
				logger.warning(symbol + " " + CHART + " buy order triggered at " + quotes[lastbar].time);
				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				trade.price = trade.entryPullBackHigh;//(MODE == Constants.TEST_MODE)?trade.entryPullBackHigh:price;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = quotes[lastbar].time;
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord( quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
				// AddTradeOpenRecord( trade.type,data.time,
				// Constants.ACTION_BUY, trade.positionSize,
				// trade.price);

				//trade.stop = Utility.getLow(quotes, trade.phl_cur.pullBack.pos, lastbar).low;
				//if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
					trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);
			}
			else if ( trade.limitEntryOrderPlaced == false)
			{
				// limit order
				trade.limitPrice1 = adjustPrice(trade.entryPullBackHigh, Constants.ADJUST_TYPE_DOWN);
				trade.limitPos1 = trade.POSITION_SIZE;
				logger.warning(symbol + " " + CHART + " place limite entry order of " + trade.limitPrice1 + " on " + trade.limitPos1);
				trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);

				trade.limit1Stop = Utility.getLow(quotes, entryPullBackPos, lastbar).low;
				if (trade.limitPrice1 - trade.limit1Stop > FIXED_STOP * PIP_SIZE)
						trade.limit1Stop = trade.limitPrice1 - FIXED_STOP * PIP_SIZE;

				trade.limit1Stop = adjustPrice(trade.limit1Stop, Constants.ADJUST_TYPE_DOWN);
				trade.limitEntryOrderPlaced = true;

			}

		}
		// end of if, need to break the for loop
		return;

	}

	
	
	
	synchronized void checkTriggerMarketSell1(double price)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		QuoteData[] quotesL = this.largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;

		if ( price < quotesL[lastbarL-1].low )
		{
			int detectPos = Utility.findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			for (int j = detectPos; j < lastbarL-1; j++)
			{
				if ( quotesL[j+1].low < quotesL[j].low )
				{
					logger.warning(symbol + " " + CHART + " trade missed");
					trade = null;
					return;
				}
			}

			// place market order
			logger.warning(symbol + " " + CHART + " sell order triggered at " + quotes[lastbar].time);
			trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
			trade.price = (MODE == Constants.TEST_MODE)?quotesL[lastbarL-1].low:price;
			trade.entryPrice = trade.price;
			trade.status = Constants.STATUS_PLACED;
			trade.remainingPositionSize = trade.POSITION_SIZE;
			trade.entryTime = quotes[lastbar].time;

			if (trade.recordOpened == false)
			{
				CreateTradeRecord(trade.type, Constants.ACTION_SELL);
				trade.recordOpened = true;
			}
			AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);

			// re-addjust stop
			cancelOrder(trade.stopId);
			//trade.stop = Utility.getHigh(quotes, trade.phl_cur.pullBack.pos, lastbar).high;
			//if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
				trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

			trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
			logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
			trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);

			return;
		}

	}

	
	
	synchronized void checkTriggerMarketBuy1(double price)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		QuoteData[] quotesL = this.largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;

		if ( price > quotesL[lastbarL-1].high )
		{
			int detectPos = Utility.findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			for (int j = detectPos; j < lastbarL-1; j++)
			{
				if ( quotesL[j+1].high > quotesL[j].high )
				{
					logger.warning(symbol + " " + CHART + " trade missed");
					trade = null;
					return;
				}
			}

			// place order
			trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
			trade.price = (MODE == Constants.TEST_MODE)?quotesL[lastbarL-1].high:price;
			trade.entryPrice = trade.price;
			trade.status = Constants.STATUS_PLACED;
			trade.remainingPositionSize = trade.POSITION_SIZE;
			trade.entryTime = quotes[lastbar].time;
			//trade.entryPos = lastbar;
			//trade.entryPosL = lastbarL;

			if (trade.recordOpened == false)
			{
				CreateTradeRecord(trade.type, Constants.ACTION_BUY);
				trade.recordOpened = true;
			}
			AddOpenRecord( quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
			// AddTradeOpenRecord( trade.type,data.time,
			// Constants.ACTION_BUY, trade.positionSize,
			// trade.price);

			//trade.stop = Utility.getLow(quotes, trade.phl_cur.pullBack.pos, lastbar).low;
			//if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
				trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

			trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
			logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
			trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);

		}

		// end of if, need to break the for loop
		return;

	}

	
	
	
	
	
	
	
	
	
	
	
	
	boolean previousTradeExist(String tradeType, int pullBackPos)
	{
		Vector<Trade> previousTrade = findPreviousTrade(tradeType);
		if (previousTrade.size() > 0)
		{
			Iterator it = previousTrade.iterator();
			while (it.hasNext())
			{
				Trade t = (Trade) it.next();
				String detectDay = t.detectTime.substring(0,8);
				String detectHour = t.detectTime.substring(9,12).trim();
				String detectMin = t.detectTime.substring(13,15);

				int hour = new Integer(detectHour);
				int minute = new Integer(detectMin);
				minute = hour * 60 + minute;
				String tz1= "";
				if (( minute >= 90) && (minute <= 420))
					tz1 = "E";
				else if (( minute >= 1080) && (minute <= 1380))
					tz1 = "A";
				
				
				String detectDay2 = trade.detectTime.substring(0,8);
				String detectHour2 = trade.detectTime.substring(9,12).trim();
				String detectMin2 = trade.detectTime.substring(13,15);

				hour = new Integer(detectHour2);
				minute = new Integer(detectMin2);
				minute = hour * 60 + minute;
				String tz2= "";
				if (( minute >= 90) && (minute <= 420))
					tz2 = "E";
				else if (( minute >= 1080) && (minute <= 1380))
					tz2 = "A";
				
				if (detectDay.equals(detectDay2)&&(tz1.equals(tz2))&& (( t.action.equals(this.trade.action))))
				{
					if ( pullBackPos != -1 )
					{
						if (t.pullBackPos == pullBackPos)
							return true;
					}
					else
					{	
						//logger.warning(symbol + " " + CHART + " find previous trade, trade should not be entered");
						return true;
					}
				}
			}
		}

		return false;

	}

	
	boolean previousTradeExist(String tradeType, String action)
	{
		Vector<Trade> previousTrade = findPreviousTrade(tradeType);
		if (previousTrade.size() > 0)
		{
			Iterator it = previousTrade.iterator();
			while (it.hasNext())
			{
				Trade t = (Trade) it.next();
				String detectDay = t.detectTime.substring(0,8);
				String detectHour = t.detectTime.substring(9,12).trim();
				String detectMin = t.detectTime.substring(13,15);

				int hour = new Integer(detectHour);
				int minute = new Integer(detectMin);
				minute = hour * 60 + minute;
				String tz1= "";
				if (( minute >= 90) && (minute <= 420))
					tz1 = "E";
				else if (( minute >= 1080) && (minute <= 1380))
					tz1 = "A";
				
				
				String detectDay2 = trade.detectTime.substring(0,8);
				String detectHour2 = trade.detectTime.substring(9,12).trim();
				String detectMin2 = trade.detectTime.substring(13,15);

				hour = new Integer(detectHour2);
				minute = new Integer(detectMin2);
				minute = hour * 60 + minute;
				String tz2= "";
				if (( minute >= 90) && (minute <= 420))
					tz2 = "E";
				else if (( minute >= 1080) && (minute <= 1380))
					tz2 = "A";

				if (detectDay.equals(detectDay2)&& tz1.equals(tz2) && t.action.equals(action))
				{
					//logger.warning(symbol + " " + CHART + " find previous trade, trade should not be entered");
					return true;
				}
			}
		}

		return false;

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
	public void trackTradeTarget(QuoteData data, Object[] quotesS, QuoteData[] quotesL)
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data);

		if (trade != null)
			if ( trade.status.equals( Constants.STATUS_OPEN))
				checkTradeOpenStatus(data);
			else if  (trade.status.equals( Constants.STATUS_PLACED))
				exit123_new( data, quotesL);
			
			
			// exit_higher_high_lower_low( data, quotesL, trade.entryPosL );

			//exit123_3_c
			//exit123_3_c_new(data, quotesL, trade.entryPosL);
			
			//exit123_3_c_org_2(data, quotesL);
		    //exit123_3_c_org_movingstop(data, quotesL);

		// exit123_3_c_1m_works_but_no_diffience( data, quotesS, quotesL);
		// exit123_org( data, quotesS, quotesL);
		// exit123_peak( data, quotesS, quotesL);
		 //exit123_pullback_20ma(data, quotesS, quotesL);
	}


	protected void checkTradeOpenStatus(QuoteData data)
	{
		 try 
		 {
			 Date now = (Date)IBDataFormatter.parse( data.time) ;
			 Date tradeEntryTime = (Date)IBDataFormatter.parse( trade.entryTime);
			 
			 if (( now.getTime() - tradeEntryTime.getTime()) > 75 * 60000 )
			 {
			  	 logger.warning( symbol + " " + CHART + " has not been filled in 60 minutes, trade missed" );
				 setTrade(null);
				 return;
			 }
		 } 
		 catch (ParseException e)
		 {
			  System.out.println("Exception :"+e);  
		  	  logger.warning( symbol + " " + CHART + " failed to parse " + data.time + " " + trade.entryTime + " " + e.getMessage());
		}  
	}
	

	
	public void exit123_3_c_org(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (lastbarL < tradeEntryPosL + 2)
			return;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (trade.entryPrice - data.low > FIXED_STOP * PIP_SIZE) 
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (inExitingTime(data.time))
				{
				}

				// normal exit stragety, this is to use the small timeframe
				/*
				if ( trade.lastHighLow == 0)
					trade.reachExitBench = true;
				else if ( data.low < trade.lastHighLow )
				{
					//logger.warning(symbol + " " + CHART + " previous low taken out at " + data.time );
					trade.reachExitBench = true;
				}
				if ( trade.reachExitBench == false )
					return;*/
					
				//double[] ema20 = Indicator.calculateEMA(quotes, 20);

				PushHighLow[] phls = Pattern.findPast2Lows(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
					double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;

					if ((trade.profitTake1 == false) && /*( phl_cur.pullBack.high > phl_pre.pullBack.high) &&*/ ( pullback_cur > pullback_pre * 1.4 ) )
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 8 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					/*
					if ((trade.profitTake1 == false) && ( quotes[phl_pre.prePos].low - quotes[phl_cur.prePos].low > FIXED_STOP /2 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on high break out fixed stop " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					/*
					if ((trade.profitTake1 == false) && ( quotes[phl_pre.prePos].low - quotes[phl_pre.curPos].low > FIXED_STOP /2 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					// normal exit stragety, this is to use the small timeframe
					/*
					if ( trade.entryPrice - data.low > 2 * FIXED_STOP * PIP_SIZE )//data.low < trade.lastHighLow )
						trade.reachExitBench = true;
					if ( trade.reachExitBench == false )
						return;

					double moveStop = adjustPrice( phl_cur.pullBack.high, Constants.ADJUST_TYPE_UP);
					if ( trade.stop != moveStop )
					{	
						cancelOrder(trade.stopId);
						trade.stop = moveStop;
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					}*/
				}

				// this is to use the larger timeframe
				int top = Utility.getHigh(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNLow(quotesL, top, lastbarL, 1);
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
					if (trade.stop != stop)
					{
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (inExitingTime(data.time))
				{
				}

				// normal exit stragety, this is to use the small time frame
/*				if ( trade.lastHighLow == 0)
					trade.reachExitBench = true;
				else if ( data.high > trade.lastHighLow )
				{
					//logger.warning(symbol + " " + CHART + " previous high taken out at " + data.time );
					trade.reachExitBench = true;
				}
				if ( trade.reachExitBench == false )
					return;
*/
				int exitStartPos = Utility.getLow(quotes, tradeEntryPos-5, tradeEntryPos).pos;
				//System.out.println(data.time + " Exit Starting pos is " + quotes[exitStartPos].time);
				PushHighLow[] phls = Pattern.findPast2Highs(quotes, exitStartPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					//System.out.println("double phls detect at " + data.time);
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
					double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;

					if ((trade.profitTake1 == false ) && /*(phl_cur.pullBack.low < phl_pre.pullBack.low) && */( pullback_cur > pullback_pre * 1.4 ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 8 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					/*
					if ((trade.profitTake1 == false) && ( quotes[phl_cur.prePos].high - quotes[phl_pre.prePos].high > FIXED_STOP /2 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 10 pip break out" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);

						trade.profitTake1 = true;
						return;
					}
					
					/*
					if ((trade.profitTake1 == false) && ( quotes[phl_cur.prePos].high - quotes[phl_pre.prePos].high > FIXED_STOP /2 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 10 pip break out" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);

						trade.profitTake1 = true;
						return;
					}*/
					
					/*
					if ( data.high - trade.entryPrice > 2 * FIXED_STOP * PIP_SIZE)//data.high > trade.lastHighLow )
						trade.reachExitBench = true;
					if ( trade.reachExitBench == false )
						return;

					double moveStop = adjustPrice( phl_cur.pullBack.low, Constants.ADJUST_TYPE_DOWN);
					if ( trade.stop != moveStop )
					{	
						cancelOrder(trade.stopId);
						trade.stop = moveStop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					}*/
				}

				// this is to use the larger time frame
				int bottom = Utility.getLow(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNHigh(quotesL, bottom, lastbarL, 1);
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
					if (trade.stop != stop)
					{
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
					}
				}
			}
		}
	}
	

	public void exit123(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (lastbarL < tradeEntryPosL + 2)
			return;

		String hr = data.time.substring(9,12).trim();
		String min = data.time.substring(13,15);
		int hour = new Integer(hr);
		int minute = new Integer(min);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (trade.entryPrice - data.low > FIXED_STOP * PIP_SIZE) 
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}

			if ( trade.stopAdjusted == true )
			{
				if (trade.targetPlaced1 == false )
				{
					//double[] ema20 = Indicator.calculateEMA(quotes, 20);
					PushHighLow[] phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL);
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl_cur = phls[0];
						PushHighLow phl_pre = phls[1];
	
						double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotesL[phl_cur.prePos]).low;
						double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotesL[phl_pre.prePos]).low;
	
						if ( ( pullback_cur > pullback_pre * 1.4 ) /*( phl_cur.pullBack.high > phl_pre.pullBack.high) &&*/  )
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
							
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl_cur.prePos]).low - 8 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							return;
						}
	
						if ( pullback_cur > FIXED_STOP * 3 * PIP_SIZE )
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 3 fixed stop " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							return;
						}
	
						double stop = adjustPrice(phl_cur.pullBack.high, Constants.ADJUST_TYPE_DOWN);
						if ( stop < trade.stop )
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
						}
					}
				}
			}
				
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					int exitStartPosL = Utility.getLow(quotesL, tradeEntryPosL-5, tradeEntryPosL).pos;
					PushHighLow[] phls = Pattern.findPast2Highs(quotesL, exitStartPosL, lastbarL);
					if ((phls != null) && (phls.length >=2))
					{
						//System.out.println("double phls detect at " + data.time);
						PushHighLow phl_cur = phls[0];
						PushHighLow phl_pre = phls[1];
	
						double pullback_cur =  ((QuoteData)quotesL[phl_cur.prePos]).high - phl_cur.pullBack.low;
						double pullback_pre =  ((QuoteData)quotesL[phl_pre.prePos]).high - phl_pre.pullBack.low;
	
						if ( ( pullback_cur > pullback_pre * 1.4 ) )
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl_cur.prePos]).high + 8 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}
	
						if (pullback_cur > FIXED_STOP * 1.75 * PIP_SIZE )
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop" + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}
	
						double stop = adjustPrice(phl_cur.pullBack.low, Constants.ADJUST_TYPE_UP);
						if ( stop > trade.stop )
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						}
					}
				}
			}
		}
	}


	
	
	public void exit123_new(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (lastbarL < tradeEntryPosL + 2)
			return;

		String hr = data.time.substring(9,12).trim();
		String min = data.time.substring(13,15);
		int hour = new Integer(hr);
		int minute = new Integer(min);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (trade.entryPrice - data.low > trade.FIXED_STOP * PIP_SIZE) 
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + data.time);
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					//double[] ema20 = Indicator.calculateEMA(quotes, 20);
					PushHighLow[] phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL);
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
						MACD[] macd = Indicator.calculateMACD( quotesL );
						int positive = 0;
						for ( int j = phl.prePos; j <= phl.curPos; j++)
						{
							if ( macd[j].histogram > 0 )
								positive ++;
							//System.out.print(macd[j].macd + "   ");
						}
						//System.out.println();
						
						if ( positive >= 2 )
						{
							/*
							int prevNegative = 0;
							for ( int j = phls[1].prePos; j <= phls[1].curPos; j++)
							{
								if ( macd[j].histogram < 0 )
									prevNegative ++;
							}
							logger.warning(symbol + " " + CHART + " " + " prev Negative is " + prevNegative);
							
							if ( prevNegative > 2 )*/
							{
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).low - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}
					}

					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl = phls[0];
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
						if ( stop < trade.stop )
						{
							
							System.out.println(symbol + " " + CHART + " " + data.time + " move stop to " + stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > trade.FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + data.time);
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					int exitStartPosL = Utility.getLow(quotesL, tradeEntryPosL-5, tradeEntryPosL).pos;
					PushHighLow[] phls = Pattern.findPast2Highs(quotesL, exitStartPosL, lastbarL);
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
						MACD[] macd = Indicator.calculateMACD( quotesL );
						int negatives = 0;
						for ( int j = phl.prePos; j <= phl.curPos; j++)
						{
							if ( macd[j].histogram < 0 )
								negatives ++;
							//System.out.print(macd[j].macd + "   ");
						}
						//System.out.println();
						
						if ( negatives >= 2 )
						{
							System.out.println("pre=" + quotesL[phl.prePos].time + " cur=" + quotesL[phl.curPos].time);
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit sell on MACD diverage " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).high + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}
					}
					
					if ((phls != null) && (phls.length > 0))
					{	
						PushHighLow phl = phls[0];
						double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
						if ( stop > trade.stop )
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						}
					}
				}
			}
		}
	}
	
	
	
	
	
	
	
	


	public static PushHighLow findLastNHigh20(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastHigh = Utility.getHigh(quotes, lastbar - 20, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).high < lastHigh.high)
			return null; // not the highest

		if ((lastbar - lastHigh.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos, lastbar);
			phl.pullBack = Utility.getLow(quotes, lastHigh.pos, lastbar);
			return phl;
		}
		else
			return null;

	}

	public static PushHighLow findLastNLow20(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastLow = Utility.getLow(quotes, lastbar - 20, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).low > lastLow.low)
			return null; // not the lowest

		if ((lastbar - lastLow.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos, lastbar);
			phl.pullBack = Utility.getHigh(quotes, lastLow.pos, lastbar);
			return phl;
		}
		else
			return null;

	}

	public static PushHighLow findLastNHigh(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).high < lastHigh.high)
			return null; // not the highest

		if ((lastbar - lastHigh.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos, lastbar);
			phl.pullBack = Utility.getLow(quotes, lastHigh.pos, lastbar);
			return phl;
		}
		else
			return null;

	}

	public static PushHighLow findLastNLow(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastLow = Utility.getLow(quotes, start, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).low > lastLow.low)
			return null; // not the lowest

		if ((lastbar - lastLow.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos, lastbar);
			phl.pullBack = Utility.getHigh(quotes, lastLow.pos, lastbar);
			return phl;
		}
		else
			return null;

	}

	public static PushHighLow findLastNHigh2(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).high < lastHigh.high)
			return null; // not the highest

		if ((lastbar - lastHigh.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos, lastbar);
			phl.pullBack = Utility.getLow(quotes, lastHigh.pos + 1, lastbar); // we
																				// do
																				// not
																				// want
																				// to
																				// the
																				// first
																				// bar
																				// to
																				// be
																				// the
																				// pull
																				// back
			return phl;
		}
		else
			return null;

	}

	public static PushHighLow findLastNLow2(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastLow = Utility.getLow(quotes, start, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).low > lastLow.low)
			return null; // not the lowest

		if ((lastbar - lastLow.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos, lastbar);
			phl.pullBack = Utility.getHigh(quotes, lastLow.pos + 1, lastbar); // we
																				// do
																				// not
																				// want
																				// to
																				// the
																				// first
																				// bar
																				// to
																				// be
																				// the
																				// pull
																				// back
			return phl;
		}
		else
			return null;

	}

	void resetHighEntryQualifyPrice(Trade trade, Object[] quotes, int reachEntryQualifyPricePos, int lastbar)
	{
		QuoteData qdata = Utility.getHigh(quotes, reachEntryQualifyPricePos, lastbar);
		trade.entryQualifyPrice = qdata.high;
		trade.entryQualifyPricePos = lastbar;
		trade.entryQualifyCount++;

	}

	void resetLowEntryQualifyPrice(Trade trade, Object[] quotes, int reachEntryQualifyPricePos, int lastbar)
	{
		QuoteData qdata = Utility.getLow(quotes, reachEntryQualifyPricePos, lastbar);
		trade.entryQualifyPrice = qdata.low;
		trade.entryQualifyPricePos = lastbar;
		trade.entryQualifyCount++;

	}

	int findNumLows20(Object[] quotes, int begin, int end, int pullbackgap)
	{
		int count = 0;
		while (end > begin)
		{
			PushHighLow phl = findLastNLow20(quotes, begin, end, pullbackgap);
			if (phl != null)
			{
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}

		return count;
	}

	int findNumHighs20(Object[] quotes, int begin, int end, int pullbackgap)
	{
		int count = 0;
		while (end > begin)
		{
			PushHighLow phl = findLastNHigh20(quotes, begin, end, pullbackgap);
			if (phl != null)
			{
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}

		return count;
	}

	int findNumLows(Object[] quotes, int begin, int end, int pullbackgap)
	{
		int count = 0;
		while (end > begin)
		{
			PushHighLow phl = findLastNLow(quotes, begin, end, pullbackgap);
			if (phl != null)
			{
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}

		return count;
	}

	int findNumHighs(Object[] quotes, int begin, int end, int pullbackgap)
	{
		int count = 0;
		while (end > begin)
		{
			PushHighLow phl = findLastNHigh(quotes, begin, end, pullbackgap);
			if (phl != null)
			{
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}

		return count;
	}

	private int checkTradeHistoy(String action)
	{
		int count = 0;
		Iterator it = tradeHistory.iterator();
		while (it.hasNext())
		{
			Trade t = (Trade) it.next();
			if (t.action.equals(action))
				count++;
		}

		return count;
	}

	public QuoteData findLastPullBackLow(Object[] quotes, int begin, int end)
	{
		int pos = end;

		for (int i = end; i > begin; i--)
		{
			if (((QuoteData) quotes[pos - 1]).low > ((QuoteData) quotes[pos]).low)
			{
				return Utility.getLow(quotes, pos, end);
			}
		}

		return null;
	}

	public QuoteData findLastPullBackHigh(Object[] quotes, int begin, int end)
	{
		int pos = end;

		for (int i = end; i > begin; i--)
		{
			if (((QuoteData) quotes[pos - 1]).high < ((QuoteData) quotes[pos]).low)
			{
				return Utility.getHigh(quotes, pos, end);
			}
		}

		return null;
	}

	private boolean findTrendlineBreakToUp(QuoteData data, Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		// if there is a ma cross over, then the cross direction is the new
		// direction
		int lastConsectiveUp = Pattern.findLastMAConsectiveUp(ema20, ema50, 72);
		int lastConsectiveDown = Pattern.findLastMAConsectiveDown(ema20, ema50, 72);

		// make sure I dont get caught up on a major reversal
		if (lastConsectiveDown > lastConsectiveUp)
		{
			// the last trend is down
			if (data.close > ema20[lastbar])
			{
				for (int i = lastbar; i > 0; i--)
				{
					if (((QuoteData) quotes[i]).low > ema20[i])
					{
						int pos = i;
						while (((QuoteData) quotes[pos]).low < ema20[pos])
							pos--;

						int pos2 = pos;
						while (((QuoteData) quotes[pos2]).low > ema20[pos])
							pos2--;

						// find the last highest point
						QuoteData lastHigh = Utility.getHigh(quotes, pos2, pos);
						if (data.close > lastHigh.high)
							return true;
					}
				}
			}
		}

		return false;

	}

	private boolean findTrendlineUpBreakDown(QuoteData data, Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		// if there is a ma cross over, then the cross direction is the new
		// direction
		int lastConsectiveUp = Pattern.findLastMAConsectiveUp(ema20, ema50, 72);
		int lastConsectiveDown = Pattern.findLastMAConsectiveDown(ema20, ema50, 72);

		// make sure I dont get caught up on a major reversal
		if (lastConsectiveUp > lastConsectiveDown)
		{
			logger.warning(symbol + " " + CHART + " larger timeframe check - up trend, check for break down");
			// the last trend is up
			if (data.close < ema20[lastbar])
			{
				logger.warning(symbol + " " + CHART + " larger timeframe check - close below 20MA");
				// the last bar is down, so to verify not to caught up a
				// downside 1-2-3 break
				for (int i = lastbar; i > 0; i--)
				{
					if (((QuoteData) quotes[i]).low < ema20[i])
					{
						int pos = i;
						while (((QuoteData) quotes[pos]).low < ema20[pos])
							pos--;

						int pos2 = pos;
						while (((QuoteData) quotes[pos2]).low > ema20[pos])
							pos2--;

						logger.warning(symbol + " " + CHART + " larger timeframe check - last low is between close below 20MA");

						// find the last highest point
						QuoteData lastHigh = Utility.getHigh(quotes, pos2, pos);
						if (data.close > lastHigh.high)
							return true;
					}
				}
			}
		}

		return false;

	}

	// private Vector<QuoteData> findTrendlineDownBreakingToUpSide( Object[]
	// quotes, int begin, int end)
	private void findTrendlineDownBreakingToUpSide(Object[] quotes, int begin, int end)
	{
		/*
		 * Reversal_123 reversal123 = new Reversal_123( symbol, logger );
		 *
		 * int lastbar = quotes.length -1; double[] ema20 =
		 * Indicator.calculateEMA(quotes, 20);
		 *
		 * Reversal123 r = reversal123.calculateUp123_20MA( quotes, ema20); if (
		 * r != null ) { logger.info(symbol + " reversal 123 UP start at " +
		 * ((QuoteData) quotes[r.startpos]).time); logger.info(symbol +
		 * " reversal 123 UP support at " + ((QuoteData)
		 * quotes[r.supportpos]).time); logger.info(symbol +
		 * " reversal 123 UP break at " + ((QuoteData)
		 * quotes[r.breakpos]).time);
		 *
		 * }
		 *
		 * return true;
		 */

		/*
		 * Vector peaks = new Vector(); double[] ema20 =
		 * Indicator.calculateEMA(quotes, 20);
		 *
		 * int pos = end; while ( pos > begin ) { int peakbegin = pos; int
		 * peakend = pos;
		 *
		 * while (peakbegin > begin + 1) { if ((((QuoteData)
		 * quotes[peakbegin]).high > ema20[peakbegin]) && (((QuoteData)
		 * quotes[peakbegin-1]).high < ema20[peakbegin-1])) { // turning point
		 * to down peakbegin = peakbegin -1; QuoteData p =
		 * Utility.getHigh(quotes, peakbegin, peakend); peaks.add(p); break; }
		 * peakbegin--; }
		 *
		 * pos = peakbegin -1;
		 *
		 * }
		 *
		 * return peaks;
		 */
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		TrendLine reversal_123 = new TrendLine(symbol, logger);

		/*
		 * for ( int i = lastbar-20; i < lastbar - 2; i++) { Reversal123 r1 =
		 * reversal_123.calculateDown123_20MA_2(quotes, i, ema20, ema50);
		 * Reversal123 r2 = reversal_123.calculateDown123_20MA_2(quotes, i+1,
		 * ema20, ema50); Reversal123 r3 =
		 * reversal_123.calculateDown123_20MA_2(quotes, i+2, ema20, ema50);
		 *
		 * if (( r1.a == r2.a ) && ( r2.a != r3.a )) { // logger.info( symbol +
		 * " " + CHART + " down trend line - start point:" +
		 * ((QuoteData)quotes[r.startpos]).time + " a:" + r.a); logger.info(
		 * symbol + " " + CHART + " down trend line break detect at " +
		 * ((QuoteData)quotes[i+2]).time ); } }
		 */
		Reversal123 r1 = reversal_123.calculateDown123_20MA_2(quotes, lastbar, ema20, ema50);
		if (r1 != null)
		{
			for (int i = lastbar - 1; i > lastbar - 50; i--)
			{
				Reversal123 r0 = reversal_123.calculateDown123_20MA_2(quotes, i, ema20, ema50);
				if (r0 != null)
					// if ( r1 != null )
					// logger.info( symbol + " " + CHART + " a" + "=" + r1.a +
					// " " + ((QuoteData)quotes[i]).time);
					if (r1.a != r0.a)
					{
						logger.info(symbol + " " + CHART + " trend change detected at " + ((QuoteData) quotes[i]).time);
						return;
					}
			}
		}

	}

	Object[] findLastNPushesHigh(Object[] quotes, int start, int width)
	{
		int lastbar = quotes.length - 1;

		PushHighLow phl_cur = findLastNHigh(quotes, start, lastbar, width);
		if (phl_cur == null)
			return null;

		Vector<PushHighLow> highlows = new Vector<PushHighLow>();
		highlows.add(phl_cur);

		int pos = phl_cur.prePos;
		while (pos > start)
		{
			for (int i = pos; i > start; i--)
			{
				PushHighLow phl = findLastNHigh(quotes, start, i, width);
				if (phl != null)
				{
					highlows.add(phl);
					pos = phl.prePos;
					continue;
				}
			}
		}

		return highlows.toArray();
	}

	Object[] findLastNPushesLow(Object[] quotes, int start, int width)
	{
		int lastbar = quotes.length - 1;

		PushHighLow phl_cur = findLastNLow(quotes, start, lastbar, width);
		if (phl_cur == null)
			return null;

		Vector<PushHighLow> highlows = new Vector<PushHighLow>();
		highlows.add(phl_cur);

		int pos = phl_cur.prePos;
		while (pos > start)
		{
			for (int i = pos; i > start; i--)
			{
				PushHighLow phl = findLastNLow(quotes, start, i, width);
				if (phl != null)
				{
					highlows.add(phl);
					pos = phl.prePos;
					continue;
				}
			}
		}

		return highlows.toArray();
	}

	int determineTrendByHigherHigh(Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		if (((QuoteData) quotes[lastbar]).close > ema50[lastbar])
		{
			for (int i = lastbar; i > lastbar - 25; i--)
			{
				PushHighLow phl_cur = findLastNHigh(quotes, i - 25, i, 2);

				if (phl_cur != null)
				{
					double currentLow = Utility.getLow(quotes, phl_cur.curPos + 1, lastbar).low;

					if (currentLow > phl_cur.pullBack.low)
						return Constants.DIRECTION_UP;

					break;
				}
			}
		}
		else if (((QuoteData) quotes[lastbar]).close < ema50[lastbar])
		{
			for (int i = lastbar; i > lastbar - 25; i--)
			{
				PushHighLow phl_cur = findLastNLow(quotes, i - 25, i, 2);

				if (phl_cur != null)
				{
					double currentHigh = Utility.getHigh(quotes, phl_cur.curPos + 1, lastbar).high;

					if (currentHigh < phl_cur.pullBack.high)
						return Constants.DIRECTION_DOWN;

					break;
				}
			}
		}

		return Constants.DIRECTION_UNKNOWN;
	}

	int determineTrendByHigherHigh2(Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		int lastNHigh = -1;
		int lastNLow = -1;
		PushHighLow phl_cur = null;
		for (int i = lastbar; i > lastbar - 25; i--)
		{
			phl_cur = findLastNHigh(quotes, i - 25, i, 2);
			if (phl_cur != null)
			{
				lastNHigh = i;
				break;
			}
		}

		for (int i = lastbar; i > lastbar - 25; i--)
		{
			phl_cur = findLastNLow(quotes, i - 25, i, 2);

			if (phl_cur != null)
			{
				lastNLow = i;
				break;
			}
		}

		if (lastNHigh > lastNLow)
		{
			double currentLow = Utility.getLow(quotes, phl_cur.curPos + 1, lastbar).low;

			if (currentLow > phl_cur.pullBack.low)
				return Constants.DIRECTION_UP;
		}
		else if (lastNHigh < lastNLow)
		{
			double currentHigh = Utility.getHigh(quotes, phl_cur.curPos + 1, lastbar).high;

			if (currentHigh < phl_cur.pullBack.high)
				return Constants.DIRECTION_DOWN;

		}

		return Constants.DIRECTION_UNKNOWN;
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

	public int countingCrossOvers(double[] ema1, double[] ema2, int begin, int end)
	{
		int count = 0;

		for (int i = begin + 1; i <= end; i++)
		{
			if (((ema1[i] > ema2[i]) && (ema1[i - 1] < ema2[i - 1])) || ((ema1[i] < ema2[i]) && (ema1[i - 1] > ema2[i - 1])))
				count++;
		}

		return count;
	}

	public void calculateInitialBreakOut()
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		toBreakOutUp = toBreakOutDown = false;

		int aboveMA = 0;
		int belowMA = 0;

		for (int i = lastbar - 3; i > lastbar - 30; i--)
		{
			if (((QuoteData) quotes[i]).low > ema20[i])
				aboveMA++;
			if (((QuoteData) quotes[i]).high < ema20[i])
				belowMA++;
		}

		if ((aboveMA < 5) && (belowMA > 10))
			toBreakOutUp = true;

		if ((belowMA < 5) && (aboveMA > 10))
			toBreakOutDown = true;

	}

	public boolean inExitingTime(String timeStr)
	{
		return false;
		/*
		int hr = new Integer(timeStr.substring(9, 12).trim());
		// String min = timeStr.substring(13,15);

		if (hr >= 11)
			return true;
		else
			return false;
		*/
	}


	public boolean checkDetectHistoy(int detectPos)
	{
		int count = 0;
		Iterator it = detectHistory.iterator();
		while (it.hasNext())
		{
			Integer pos = (Integer) it.next();
			if ( pos == detectPos )
				return true;
		}

		return false;
	}

	
	public static boolean EF_ifFirstSecondPullBackUp(QuoteData[] quotes, double[] ema20, int numOfBarsCrossOver, int numOfBarsBreakout )
	{
		int pos = Pattern.findPriceCross20MAUp(quotes, ema20, numOfBarsCrossOver);
		
		//if ( pos == Constants.NOT_FOUND )
			//return true;
		
		Vector<BreakOut> breakouts = Pattern.find20MABreakoutUps(quotes, ema20, pos);
		
		int sum = 0;
		int biggestWidth = 0;
		Iterator<BreakOut> it = breakouts.iterator();
		while( it.hasNext())
		{
			BreakOut bo = (BreakOut)it.next();
			if ( bo.highWidth >= numOfBarsBreakout)
				sum++;
			if ( bo.highWidth > biggestWidth)
				biggestWidth = bo.highWidth;
		}

		if (( biggestWidth > 30 ) || ( sum > 4 ))
			return false;
		else
			return true;
			
	}
	
	
	
	public static boolean EF_ifFirstSecondPullBackDown(QuoteData[] quotes, double[] ema20, int numOfBarsCrossOver, int numOfBarsBreakout )
	{
		int pos = Pattern.findPriceCross20MADown(quotes, ema20, numOfBarsCrossOver);

		//if ( pos == Constants.NOT_FOUND )
		//	return true;
		
		Vector<BreakOut> breakouts = Pattern.find20MABreakoutDowns(quotes, ema20, pos);
		
		int sum = 0;
		int biggestWidth = 0;
		Iterator<BreakOut> it = breakouts.iterator();
		while( it.hasNext())
		{
			BreakOut bo = (BreakOut)it.next();
			//System.out.println("breakouts: " + bo.width);
			if ( bo.highWidth >= numOfBarsBreakout)
				sum++;
			if ( bo.highWidth > biggestWidth)
				biggestWidth = bo.highWidth;
		}
		
		if (( biggestWidth > 30 ) || ( sum > 4 ))
			return false;
		else
			return true;
			
	}

	PushHighLow findLastPullBack( QuoteData[] quotes )
	{
		int lastbar = quotes.length - 1;
		PushHighLow phl_up = null;
		PushHighLow phl_down = null;
		
		for ( int i = lastbar-1; i > 0; i--)
		{
			// looking for the first bar that has higher/lower on both side and the right side is higher/lower than the left
			// look for ups
			int left =  Constants.NOT_FOUND;
			for ( int j = i -1; j> 0; j--)
			{
				if (quotes[j].high > quotes[i].high)
				{
					left = j;
					while( quotes[left-1].high > quotes[left].high)
						left--;
					break;
				}
			}
			
			int right = Constants.NOT_FOUND;
			QuoteData q = Utility.getHigh(quotes, i+1, lastbar);
			if ( q != null )
				right = q.pos;
			
			if ((left != Constants.NOT_FOUND) && (right != Constants.NOT_FOUND))
			{
				if ( quotes[right].high > quotes[left].high)
				{
					phl_up = new PushHighLow(left, right);
					QuoteData pullback = Utility.getLow(quotes, left+1, right-1);
					phl_up.pullBack = pullback;
					phl_up.direction = Constants.DIRECTION_UP;
				}
			}
			
			
			// look for downs
			left =  Constants.NOT_FOUND;
			for ( int j = i -1; j> 0; j--)
			{
				if (quotes[j].low < quotes[i].low)
				{
					left = j;
					while( quotes[left-1].low < quotes[left].low)
						left--;
					break;
				}
			}
			
			right = Constants.NOT_FOUND;
			q = Utility.getLow(quotes, i+1, lastbar);
			if ( q != null )
				right = q.pos;
			
			if ((left != Constants.NOT_FOUND) && (right != Constants.NOT_FOUND))
			{
				if ( quotes[right].low < quotes[left].low)
				{
					phl_down = new PushHighLow(left, right);
					QuoteData pullback = Utility.getHigh(quotes, left+1, right-1);
					phl_down.pullBack = pullback;
					phl_down.direction = Constants.DIRECTION_DOWN;
				}
			}

			if (( phl_up != null ) && ( phl_down != null))
				return null;
			
			if ( phl_up != null )
				return phl_up;
			else if ( phl_down != null )
				return phl_down;
		}

		return null;

	}

	
	public static PushHighLow findLastHighWOWOHigh( Object[] quotes, int start, int lastbar, int n )
	{
		//return findLastNHigh_v2( quotes, start, lastbar, n );
		
		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

		if (( lastbar - lastHigh.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos,lastbar);
			phl.pullBack = Utility.getLow( quotes, lastHigh.pos, lastbar); 
			return phl;
		}
		else
			return null;
		
	}

	public static PushHighLow findLastHighWOWOLow( Object[] quotes, int start, int lastbar, int n )
	{
		//return findLastNHigh_v2( quotes, start, lastbar, n );
		
		QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		if (( lastbar - lastLow.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos,lastbar);
			phl.pullBack = Utility.getHigh( quotes, lastLow.pos, lastbar); 
			return phl;
		}
		else
			return null;
		
	}


	void enterMarketPosition(Trade tt, double price)
	{
		trade = tt;
		super.enterLimitPosition1(price);
	}


}


