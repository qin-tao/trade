package tao.trading.strategy;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.BreakOut;
import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.Peak;
import tao.trading.QuoteData;
import tao.trading.Reversal123;
import tao.trading.Trade;
import tao.trading.dao.PushHighLow;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.TrendLine;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

import java.util.Arrays;

public class SF108bTradeManager_bak extends TradeManager2 implements Serializable
{
	HashMap<String, QuoteData> qts = new HashMap<String, QuoteData>(1000);
	boolean CNT = false;
	int TARGET_PROFIT_SIZE;
    int TIME_ZONE;
	double PIP_SIZE;
	boolean oneMinutChart = false;
	int POSITION_SIZE;
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
	protected SF108bTradeManager_bak largerTimeFrameTraderManager;
	boolean breakOutCalculated = false;
	boolean toBreakOutUp = false;
	boolean toBreakOutDown = false;
	boolean timeUp = false;
	public Vector<Integer> detectHistory = new Vector<Integer>();

	// important switch
	boolean prremptive_limit = false;
	boolean breakout_limit = false;
	boolean reverse_trade = false; // reverse after stopped out
	boolean rsc_trade = false; // do a rsc trade instead of rst if there is
								// consective 3 bars
	boolean reverse_after_profit = false; // reverse after there is a profit
	boolean after_target_reversal = false;

	int BREAK_EVEN_POINT = 20;

	int ENTRY_LARGE_PULLBACK = 12;
	int ENTRY_LARGE_BREAKOUT = 20;
	int EXIT_LARGE_PULLBACK = 10;
	int EXIT_LARGE_BREAKOUT = 20;

	boolean LIMIT_ORDER = false;

	public SF108bTradeManager_bak()
	{
		super();
	}

	public SF108bTradeManager_bak(String account, EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super(account, client, contract, symbolIndex, logger);
	}

	public QuoteData[] getQuoteData()
	{
		Object[] quotes = this.qts.values().toArray();
		Arrays.sort(quotes);
		return Arrays.copyOf(quotes, quotes.length, QuoteData[].class);
	}

	
	public boolean inTradingTime( int hour, int min )
	{
		//return true;
	
		if ( timeUp )
			return false;
		
		int minute = hour * 60 + min;
		if (( minute >= 90) && (minute <= 420))
			return true;
		else
			return false;
		
/*		switch ( TIME_ZONE)
		{
			case 23:  	if (( minute >= 90) && (minute <= 420))
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

	public void closePositionByLimit(double limitPrice, int positionSize)
	{
		String action = trade.action.equals("BUY") ? "SELL" : "BUY";
		trade.closeId = placeLmtOrder(action, limitPrice, positionSize, null);

	}

	public void cancelOrder(int orderId)
	{
		if (MODE == Constants.REAL_MODE)
		{
			if (orderId != 0)
			{
				logger.warning(symbol + " " + CHART + " MIN " + " cancel order " + orderId);
				super.cancelOrder(orderId);
			}
		}
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

			cancelTarget();

			processAfterHitStopLogic_c();

		}
		else if ((orderId == trade.limitId1) && ( trade.limitPos1Filled == 0 ))
		{
			logger.warning(symbol + " " + CHART + " minute " + " limit order: " + orderId + " " + filled + " filled");

			trade.limitPos1Filled = trade.limitPos1;
			trade.entryPrice = trade.limitPrice1;
			trade.remainingPositionSize = trade.limitPos1; //+= filled;
			trade.entryTime = quotes[lastbar].time;
			//trade.entryPos = lastbar;
			//trade.entryPosL = lastbarL;

			if (trade.stopId != 0)
				cancelOrder(trade.stopId);

			// calculate stop here
			trade.stop = trade.limit1Stop;
			if (Constants.ACTION_SELL.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			else if (Constants.ACTION_BUY.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

			if (trade.remainingPositionSize == trade.POSITION_SIZE)
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
			//trade.targetReached = true;
			//trade.remainingPositionSize -= filled;
			//trade.targetId1 = 0;

			cancelOrder(trade.stopId);
			if ( trade.targetId2 != 0 )
				cancelOrder(trade.targetId2);
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
		else if (orderId == trade.targetId2)
		{
			logger.warning(symbol + " " + CHART + " minute target2 filled, " + filled + " closed @ " + trade.targetPrice);

			cancelOrder(trade.stopId);
			if ( trade.targetId1 != 0 )
				cancelOrder(trade.targetId1);

			removeTrade();
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
			if ((trade.limitId1 != 0) && (data.high >= trade.limitPrice1))
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
				logger.warning(symbol + " " + CHART + " minute stopped out @ data.high:" + data.high + " " + data.time);
				trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);
				trade.stopId = 0;

				trade.preStopPrice = trade.stop;
				cancelTarget();

				processAfterHitStopLogic_c();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.low < trade.targetPrice1))
			{
				logger.info(symbol + " target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				trade.remainingPositionSize -= trade.targetPos1;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				return;
			}

			/*
			if ((trade.targetId2 != 0) && (data.low < trade.targetPrice2))
			{
				logger.info(symbol + " target2 hit, close " + trade.targetPos2 + " @ " + trade.targetPrice2);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos2, trade.targetPrice2);
				trade.targetId2 = 0;

				trade.remainingPositionSize -= trade.targetPos2;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				return;
			}*/

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
				cancelTarget();

				processAfterHitStopLogic_c();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.high > trade.targetPrice1))
			{
				logger.info(symbol + " target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				trade.remainingPositionSize -= trade.targetPos1;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				return;
			}

			if ((trade.targetId2 != 0) && (data.high > trade.targetPrice2))
			{
				logger.info(symbol + " target2 hit, close " + trade.targetPos2 + " @ " + trade.targetPrice2);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos2, trade.targetPrice2);
				trade.targetId2 = 0;

				trade.remainingPositionSize -= trade.targetPos2;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				return;
			}
		}
	}


	public void cancelTarget()
	{
		if (MODE == Constants.REAL_MODE)
		{
			if (trade.targetId != 0)
			{
				cancelOrder(trade.targetId);
				trade.targetId = 0;
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
			trade.stop = 0;
		}
	}

	void processAfterHitStopLogic_c()
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		Object[] quotesL = this.largerTimeFrameTraderManager.getQuoteData();

		if ((reverse_trade == false) || (trade.type == Constants.TRADE_CNT))
		{
			removeTrade();
			return;
		}

		if ((trade.type == Constants.TRADE_RST) || (trade.type == Constants.TRADE_RSC)) 
		{
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				int tradeEntryPos = findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				QuoteData lowAfterEntry = Utility.getLow(quotes, tradeEntryPos, lastbar);

				// if ((trade.price - lowAfterEntry.low) > 32 * PIP_SIZE)
				// return;

				// if ( trade.adjustStop == 0 ) // stopped out without moving
				// stop
				{
					// if ( lowAfterEntry.low >
					// ((QuoteData)quotes[trade.pullBackPos]).low)
					{
						// did not break higher high/lower low, reverse the
						// trade
						logger.warning(symbol + " " + CHART + " place reverse order at " + ((QuoteData) quotes[lastbar]).time + " @ " + trade.stop);
						setMktTrade(Constants.TRADE_CNT, Constants.ACTION_BUY, POSITION_SIZE, trade.stop);
						trade.lowHighAfterEntry = lowAfterEntry;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						//trade.entryPos = lastbar;
						//trade.profitTakeStartPos = lowAfterEntry.pos;

						if (MODE == Constants.TEST_MODE)
							AddTradeOpenRecord(trade.type, trade.entryTime, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.entryPrice);

						trade.stop = lowAfterEntry.low;
						if ((trade.entryPrice - trade.stop) > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);

						logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return;
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				// if ( direction != Constants.DIRECTION_DOWN)
				// return;

				int tradeEntryPos = findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				QuoteData highAfterEntry = Utility.getHigh(quotes, tradeEntryPos, lastbar);

				// if ((highAfterEntry.high - trade.price ) > 32 * PIP_SIZE)
				// return;
				// if ( trade.adjustStop == 0 ) // stopped out without profit
				{
					// if ( highAfterEntry.high <
					// ((QuoteData)quotes[trade.pullBackPos]).high)
					{
						setMktTrade(Constants.TRADE_CNT, Constants.ACTION_SELL, POSITION_SIZE, trade.stop);
						trade.lowHighAfterEntry = highAfterEntry;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						//trade.entryPos = lastbar;
						//trade.profitTakeStartPos = highAfterEntry.pos;

						if (MODE == Constants.TEST_MODE)
							AddTradeOpenRecord(trade.type, trade.entryTime, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.entryPrice);

						trade.stop = highAfterEntry.high;
						if ((trade.stop - trade.entryPrice) > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.entryPrice + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);

						logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return;

					}
				}
			}
		}

		removeTrade();
		return;
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

		if (field == 1) // bid
		{
			checkTriggerMarketSell(price);
		}
		else if (field == 2) // ask
		{
			checkTriggerMarketBuy(price);
		}

	}

	// //////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Entry Setups
	//
	//
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////
	public Trade checkHigherHighSetup(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		// return checkRSTSetup2( data, minCrossBar, quotesL, quotesS );
		// if (!findTrade( Constants.TRADE_CNT ) && !findTrade(
		// Constants.TRADE_RST ) )
		// return HigherHighLowerLowSetupOrg( data, minCrossBar, quotesL,
		// quotesS );

		/*
		 * Trade lastTrade = findPreviousTrade(Constants.TRADE_RST ); if (
		 * lastTrade != null ) { Object[] quotes = getQuoteData(); int lastbar =
		 * quotes.length - 1;
		 *
		 * if ( lastTrade.entryPos + 5 > lastbar ) return null; }
		 *
		 * return HigherHighLowerLowSetup2( data, minCrossBar, quotesL, quotesS
		 * );
		 */
		// return HigherHighLowerLowRSTSetup( data, minCrossBar, quotesL,
		// quotesS );
		// return HigherHighLowerLowSetup2( data, minCrossBar, quotesL, quotesS
		// );
		// else
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


	// this has not been finished, there's some problem with
	// findPastHighPeaksAboveMA
	public Trade checkHigherHighLowerLowSetup3(QuoteData data, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);

		calculateInitialBreakOut();

		if ((toBreakOutUp == false) && (toBreakOutDown == false))
			return null;

		/*
		 * Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
		 * int preTrades = previousTrade.size(); if ( preTrades > 0 ) return
		 * null;
		 */

		// detect to see if there is a change of direction in the last 4-6 hours
		// Direction direction = calculatDirectionByCount2( quotes, ema20, 20);
		// if (data.close > ema20[lastbar])
		if ((this.toBreakOutDown == true) && (ema20L[lastbarL] < ema50L[lastbarL]))
		{
			int above20MA = lastbar;
			while (((QuoteData) quotes[above20MA]).low <= ema20[above20MA])
				above20MA--;

			logger.warning(symbol + " " + CHART + " touch calculate start " + ((QuoteData) quotes[above20MA]).time);
			MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, above20MA - 42, above20MA);

			if ((mat != null) && (mat.low != null))
			{
				if (data.low < mat.low.low)
				{
					for (int i = mat.low.pos; i < lastbar; i++)
					{
						if (((QuoteData) quotes[i]).low < mat.low.low)
							return null;
					}

					logger.warning(symbol + " " + CHART + " last touch up low is " + ((QuoteData) quotes[mat.low.pos]).time + "@" + mat.low.low);

					QuoteData high = Utility.getHigh(quotes, mat.low.pos, lastbar);
					if (high.high - mat.low.low > 40 * PIP_SIZE)
					{
						logger.warning(symbol + " " + CHART + " pull back too large, missed the boat");
						return null;
					}

					// if (((QuoteData)quotes[lastbar-1]).low < mat.low.low )
					// return null;
					trade = new Trade(symbol);
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
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
					logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);

					trade.stop = Utility.getHigh(quotes, mat.touchBegin, lastbar).high;
					if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
						trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																												// Long(oca).toString());

					return trade;
				}
			}
		}
		else if ((this.toBreakOutUp == true) && (ema20L[lastbarL] > ema50L[lastbarL]))
		{
			int below20MA = lastbar;
			while (((QuoteData) quotes[below20MA]).high >= ema20[below20MA])
				below20MA--;

			MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, below20MA - 42, below20MA);

			if ((mat != null) && (mat.high != null))
			{
				if (data.high > mat.high.high)
				{
					for (int i = mat.high.pos; i < lastbar; i++)
					{
						if (((QuoteData) quotes[i]).high > mat.high.high)
							return null;
					}

					logger.warning(symbol + " " + CHART + " last touch up high is " + ((QuoteData) quotes[mat.high.pos]).time + "@" + mat.high.high);

					// to see if we already missed the opportunity
					QuoteData low = Utility.getLow(quotes, mat.high.pos, lastbar);
					if (mat.high.high - low.low > 40 * PIP_SIZE)
					{
						logger.warning(symbol + " " + CHART + " pull back too large, missed the boat");
						return null;
					}

					// if (((QuoteData)quotes[lastbar-1]).high > mat.high.high )
					// return null;
					trade = new Trade(symbol);
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
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
					logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);

					trade.stop = Utility.getLow(quotes, mat.touchBegin, lastbar).low;
					if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
						trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																												// Long(oca).toString());

					return trade;
				}
			}
		}

		return null;
	}

	// this has not been finished, there's some problem with
	// findPastHighPeaksAboveMA
	public Trade checkHigherHighLowerLowSetup3a(QuoteData data, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);

		calculateInitialBreakOut();

		if ((toBreakOutUp == false) && (toBreakOutDown == false))
			return null;

		/*
		 * Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
		 * int preTrades = previousTrade.size(); if ( preTrades > 0 ) return
		 * null;
		 */

		// detect to see if there is a change of direction in the last 4-6 hours
		// Direction direction = calculatDirectionByCount2( quotes, ema20, 20);
		// if (data.close > ema20[lastbar])
		if ((this.toBreakOutDown == true) && (ema20L[lastbarL] < ema50L[lastbarL]))
		{
			int above20MA = lastbar;
			while (((QuoteData) quotes[above20MA]).low <= ema20[above20MA])
				above20MA--;

			int start = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, above20MA, 3);
			for (int i = lastbar; i > start; i--)
			{
				PushHighLow phl_cur = findLastNHigh(quotes, start, i, 2);
				if (phl_cur != null)
				{
					if (data.low < phl_cur.pullBack.low)
					{
						Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
						if (previousTrade.size() > 0)
						{
							Iterator it = previousTrade.iterator();
							while (it.hasNext())
							{
								Trade t = (Trade) it.next();
								if (t.pullBackPos == phl_cur.pullBack.pos)
									return null;
							}
						}

						for (int j = phl_cur.pullBack.pos + 1; j < lastbar; j++)
						{
							if (((QuoteData) quotes[j]).low < phl_cur.pullBack.low)
								return null;
						}

						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_SELL;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_cur.pullBack.low;
						trade.detectPos = lastbar;
						trade.status = Constants.STATUS_OPEN;
						trade.pullBackPos = phl_cur.pullBack.pos;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
						trade.price = phl_cur.pullBack.low;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;
						AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
						logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);

						trade.stop = Utility.getHigh(quotes, phl_cur.pullBack.pos, lastbar).high;
						if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return trade;
					}
				}
			}
		}
		else if ((this.toBreakOutUp == true) && (ema20L[lastbarL] > ema50L[lastbarL]))
		{
			int below20MA = lastbar;
			while (((QuoteData) quotes[below20MA]).high >= ema20[below20MA])
				below20MA--;

			int start = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, below20MA, 3);
			for (int i = lastbar; i > start; i--)
			{
				PushHighLow phl_cur = findLastNLow(quotes, start, i, 2);
				if (phl_cur != null)
				{
					if (data.high > phl_cur.pullBack.high)
					{
						Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
						if (previousTrade.size() > 0)
						{
							Iterator it = previousTrade.iterator();
							while (it.hasNext())
							{
								Trade t = (Trade) it.next();
								if (t.pullBackPos == phl_cur.pullBack.pos)
									return null;
							}
						}

						for (int j = phl_cur.pullBack.pos + 1; j < lastbar; j++)
						{
							if (((QuoteData) quotes[j]).high > phl_cur.pullBack.high)
								return null;
						}

						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_cur.pullBack.high;
						trade.detectPos = lastbar;
						trade.status = Constants.STATUS_OPEN;
						trade.pullBackPos = phl_cur.pullBack.pos;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = phl_cur.pullBack.high;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;
						AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
						logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);

						trade.stop = Utility.getLow(quotes, phl_cur.pullBack.pos, lastbar).low;
						if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return trade;
					}
				}
			}
		}

		return null;
	}

	// 1/31/2011, a higher high /lower low break must be combined with 20MA pull
	// back on the bigger time frame
	public Trade checkHigherHighLowerLowSetup3b(QuoteData data, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		// double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;

		if ((direction == Constants.DIRECTION_DOWN) && (((QuoteData) quotesL[lastbarL]).high > ema20L[lastbarL]))
		{
			// int start = lastbar - 36;
			int start = Utility.getLow(quotes, lastbar - 40, lastbar - 3).pos;

			for (int i = lastbar; i > start; i--)
			{
				PushHighLow phl_cur = Pattern.findLastNHigh(quotes, start, i, 2);
				if (phl_cur != null)
				{
					if (data.low < phl_cur.pullBack.low)
					{
						Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
						if (previousTrade.size() > 0)
						{
							Iterator it = previousTrade.iterator();
							while (it.hasNext())
							{
								Trade t = (Trade) it.next();
								if (t.pullBackPos == phl_cur.pullBack.pos)
									return null;
							}
						}

						for (int j = phl_cur.pullBack.pos + 1; j < lastbar; j++)
						{
							if (((QuoteData) quotes[j]).low < phl_cur.pullBack.low)
								return null;
						}

						if ((((QuoteData) quotesL[lastbarL]).low > ema20L[lastbarL])
								&& (((QuoteData) quotesL[lastbarL - 1]).low > ema20L[lastbarL - 1]))
							return null;

						logger.warning(symbol + " " + CHART + " starting point is " + ((QuoteData) quotes[start]).time);

						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_SELL;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_cur.pullBack.low;
						trade.detectPos = lastbar;
						trade.status = Constants.STATUS_OPEN;
						trade.pullBackPos = phl_cur.pullBack.pos;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
						trade.price = phl_cur.pullBack.low;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						// logger.warning(symbol + " " + CHART +
						// " trade detected SELL at " + data.time + "@" +
						// trade.price);
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						AddOpenRecord(data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
						AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);

						trade.stop = Utility.getHigh(quotes, phl_cur.pullBack.pos, lastbar).high;
						if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return trade;
					}
				}
			}
		}
		else if ((direction == Constants.DIRECTION_UP) && (((QuoteData) quotesL[lastbarL]).low < ema20L[lastbarL]))
		{
			int start = Utility.getHigh(quotes, lastbar - 40, lastbar - 3).pos;
			// int start = lastbar - 36;

			for (int i = lastbar; i > start; i--)
			{
				PushHighLow phl_cur = Pattern.findLastNLow(quotes, start, i, 2);
				if (phl_cur != null)
				{
					if (data.high > phl_cur.pullBack.high)
					{
						Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
						if (previousTrade.size() > 0)
						{
							Iterator it = previousTrade.iterator();
							while (it.hasNext())
							{
								Trade t = (Trade) it.next();
								if (t.pullBackPos == phl_cur.pullBack.pos)
									return null;
							}
						}

						for (int j = phl_cur.pullBack.pos + 1; j < lastbar; j++)
						{
							if (((QuoteData) quotes[j]).high > phl_cur.pullBack.high)
								return null;
						}

						if ((((QuoteData) quotesL[lastbarL]).high < ema20L[lastbarL])
								&& (((QuoteData) quotesL[lastbarL - 1]).high < ema20L[lastbarL - 1]))
							return null;

						logger.warning(symbol + " " + CHART + " starting point is " + ((QuoteData) quotes[start]).time);

						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_cur.pullBack.high;
						trade.detectPos = lastbar;
						trade.status = Constants.STATUS_OPEN;
						trade.pullBackPos = phl_cur.pullBack.pos;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = phl_cur.pullBack.high;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						// logger.warning(symbol + " " + CHART +
						// " trade detected BUY at " + data.time + "@" +
						// trade.price );
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						AddOpenRecord(data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
						AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);

						trade.stop = Utility.getLow(quotes, phl_cur.pullBack.pos, lastbar).low;
						if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return trade;
					}
				}
			}
		}

		return null;
	}

	public Trade checkHigherHighLowerLowSetup3b2(QuoteData data, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		// double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;

		if ((direction == Constants.DIRECTION_DOWN) && (((QuoteData) quotesL[lastbarL]).high > ema20L[lastbarL]))
		{
			// int start = lastbar - 36;
			int start = Utility.getLow(quotes, lastbar - 40, lastbar - 3).pos;

			PushHighLow phl_cur = findLastNHigh(quotes, start, lastbar, 2);
			if (phl_cur == null)
				return null;

			int preHigh = phl_cur.prePos;
			for (int i = preHigh; i > start; i--)
			{
				PushHighLow phl_pre = findLastNHigh(quotes, start, i, 2);
				if (phl_pre != null)
				{
					if ((phl_cur.pullBack.low < phl_pre.pullBack.low)
							&& (((QuoteData) quotes[phl_cur.curPos]).high > ((QuoteData) quotes[phl_pre.curPos]).high))
					{
						if ((((QuoteData) quotesL[lastbarL]).low > ema20L[lastbarL])
								&& (((QuoteData) quotesL[lastbarL - 1]).low > ema20L[lastbarL - 1]))
							return null;

						if (previousTradeExist(phl_cur.pullBack.pos, Constants.ACTION_SELL))
							return null;

						logger.warning(symbol + " " + CHART + " starting point is " + ((QuoteData) quotes[start]).time);

						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_SELL;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_cur.pullBack.low;
						trade.detectPos = lastbar;
						trade.status = Constants.STATUS_OPEN;
						trade.pullBackPos = phl_cur.pullBack.pos;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
						trade.price = phl_cur.pullBack.low;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						// logger.warning(symbol + " " + CHART +
						// " trade detected SELL at " + data.time + "@" +
						// trade.price);
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						AddOpenRecord(data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
						AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);

						trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return trade;
					}
				}
			}
		}
		else if ((direction == Constants.DIRECTION_UP) && (((QuoteData) quotesL[lastbarL]).low < ema20L[lastbarL]))
		{
			int start = Utility.getHigh(quotes, lastbar - 40, lastbar - 3).pos;
			PushHighLow phl_cur = findLastNLow(quotes, start, lastbar, 2);
			if (phl_cur == null)
				return null;

			int preLow = phl_cur.prePos;
			for (int i = preLow; i > start; i--)
			{
				PushHighLow phl_pre = findLastNLow(quotes, start, i, 2);
				if (phl_pre != null)
				{
					if ((phl_cur.pullBack.high > phl_pre.pullBack.high)
							&& (((QuoteData) quotes[phl_cur.curPos]).low < ((QuoteData) quotes[phl_pre.curPos]).low))
					{
						if ((((QuoteData) quotesL[lastbarL]).high < ema20L[lastbarL])
								&& (((QuoteData) quotesL[lastbarL - 1]).high < ema20L[lastbarL - 1]))
							return null;

						logger.warning(symbol + " " + CHART + " starting point is " + ((QuoteData) quotes[start]).time);

						if (previousTradeExist(phl_cur.pullBack.pos, Constants.ACTION_BUY))
							return null;

						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_cur.pullBack.high;
						trade.detectPos = lastbar;
						trade.status = Constants.STATUS_OPEN;
						trade.pullBackPos = phl_cur.pullBack.pos;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = phl_cur.pullBack.high;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						// logger.warning(symbol + " " + CHART +
						// " trade detected BUY at " + data.time + "@" +
						// trade.price );
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						AddOpenRecord(data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
						AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);

						trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return trade;
					}
				}
			}
		}

		return null;
	}

	public void createOpenTrade(String tradeType, String action)
	{
		trade = new Trade(symbol);
		trade.type = tradeType;
		trade.action = action;
		trade.POSITION_SIZE = POSITION_SIZE;
		trade.status = Constants.STATUS_OPEN;
	}

	// 1/31/2011, a higher high /lower low break must be combined with 20MA pull
	// back on the bigger time frame
	// 2/1/2011, add position before hiting the turnning point
	public Trade checkHigherHighLowerLowSetup3c(QuoteData data, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		// double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;

		if ((direction == Constants.DIRECTION_DOWN) && (((QuoteData) quotesL[lastbarL]).high > ema20L[lastbarL]))
		{
			int start = Utility.getLow(quotes, lastbar - 60, lastbar - 3).pos;

			for (int i = lastbar; i > start; i--)
			{
				PushHighLow[] phls = Pattern.findPast2Highs(quotes, start, i);

				if ((phls != null) && (phls.length >= 2))
				{
					PushHighLow phl_cur = phls[0];
					Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
					if (previousTrade.size() > 0)
					{
						Iterator it = previousTrade.iterator();
						while (it.hasNext())
						{
							Trade t = (Trade) it.next();
							if (t.pullBackPos == phl_cur.pullBack.pos)
								return null;
						}
					}

					if (phl_cur.curPos == lastbar)
					{
						// touched 20MA currently and on the top, time to scale
						// in some position
						// find past push ups
						if (data.high > phl_cur.pullBack.low + 40 * PIP_SIZE)
						{
							createOpenTrade(Constants.TRADE_RST, Constants.ACTION_BUY);
						}
					}

					if (data.low < phl_cur.pullBack.low)
					{
						for (int j = phl_cur.pullBack.pos + 1; j < lastbar; j++)
						{
							if (((QuoteData) quotes[j]).low < phl_cur.pullBack.low)
								return null;
						}

						if ((((QuoteData) quotesL[lastbarL]).low > ema20L[lastbarL])
								&& (((QuoteData) quotesL[lastbarL - 1]).low > ema20L[lastbarL - 1]))
							return null;

						logger.warning("@@@@@@@@@@@@@");
						for (int j = 0; j < phls.length; j++)
						{
							PushHighLow phl = phls[j];
							logger.warning(symbol + " " + CHART + "@@@this high between " + ((QuoteData) quotes[phl.prePos]).time + "@"
									+ ((QuoteData) quotes[phl.prePos]).high + "  -  " + ((QuoteData) quotes[phl.curPos]).time + "@"
									+ ((QuoteData) quotes[phl.curPos]).high + " pullback@" + phl.pullBack.time);
						}

						createOpenTrade(Constants.TRADE_RST, Constants.ACTION_SELL);
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_cur.pullBack.low;
						trade.detectPos = lastbar;
						trade.pullBackPos = phl_cur.pullBack.pos;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
						trade.price = phl_cur.pullBack.low;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						AddOpenRecord(data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
						// AddTradeOpenRecord( trade.type,data.time,
						// Constants.ACTION_SELL, trade.positionSize,
						// trade.price);

						trade.stop = Utility.getHigh(quotes, phl_cur.pullBack.pos, lastbar).high;
						if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return trade;
					}
					return null;
				}
			}
		}
		else if ((direction == Constants.DIRECTION_UP) && (((QuoteData) quotesL[lastbarL]).low < ema20L[lastbarL]))
		{
			int start = Utility.getHigh(quotes, lastbar - 60, lastbar - 3).pos;
			// logger.warning(symbol + " " + CHART +
			// " lower low calculation starting point is " +
			// ((QuoteData)quotes[start]).time);
			for (int i = lastbar; i > start; i--)
			{
				PushHighLow[] phls = Pattern.findPast2Lows(quotes, start, i);

				if ((phls != null) && (phls.length >= 2))
				{
					PushHighLow phl_cur = phls[0];
					Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
					if (previousTrade.size() > 0)
					{
						Iterator it = previousTrade.iterator();
						while (it.hasNext())
						{
							Trade t = (Trade) it.next();
							if (t.pullBackPos == phl_cur.pullBack.pos)
								return null;
						}
					}

					if (phl_cur.curPos == lastbar)
					{
						if (data.low < phl_cur.pullBack.high - 40 * PIP_SIZE)
						{
						}
					}

					// logger.info("data high=" + data.high + " " + data.time);
					if (data.high > phl_cur.pullBack.high)
					{
						for (int j = phl_cur.pullBack.pos + 1; j < lastbar; j++)
						{
							if (((QuoteData) quotes[j]).high > phl_cur.pullBack.high)
							{
								// logger.warning(symbol + " " + CHART +
								// " this high between " +
								// ((QuoteData)quotes[phl_cur.prePos]).time +
								// "@" +
								// ((QuoteData)quotes[phl_cur.prePos]).high +
								// "  -  " +
								// ((QuoteData)quotes[phl_cur.curPos]).time +
								// "@" +
								// ((QuoteData)quotes[phl_cur.curPos]).high +
								// " pullback@" + phl_cur.pullBack.time );
								// logger.warning(symbol + " " + CHART +
								// " had previous higher than the pull back, trade abandented - "
								// + data.time );
								return null;
							}
						}

						if ((((QuoteData) quotesL[lastbarL]).high < ema20L[lastbarL])
								&& (((QuoteData) quotesL[lastbarL - 1]).high < ema20L[lastbarL - 1]))
							return null;

						logger.warning("@@@@@@@@@@@@@-" + data.time);
						for (int j = 0; j < phls.length; j++)
						{
							PushHighLow ppp = phls[j];
							logger.warning(symbol + " " + CHART + "@@@this high between " + ((QuoteData) quotes[ppp.prePos]).time + "@"
									+ ((QuoteData) quotes[ppp.prePos]).high + "  -  " + ((QuoteData) quotes[ppp.curPos]).time + "@"
									+ ((QuoteData) quotes[ppp.curPos]).high + " pullback@" + ppp.pullBack.time);
						}

						createOpenTrade(Constants.TRADE_RST, Constants.ACTION_BUY);
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_cur.pullBack.high;
						trade.detectPos = lastbar;
						trade.pullBackPos = phl_cur.pullBack.pos;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = phl_cur.pullBack.high;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						AddOpenRecord(data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
						AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);

						trade.stop = Utility.getLow(quotes, phl_cur.pullBack.pos, lastbar).low;
						if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());

						return trade;
					}

					return null;
				}
			}
		}

		return null;
	}

	public Trade checkHigherHighLowerLowSetup3d(QuoteData data, QuoteData[] quotesL)
	{
		//System.out.println(data.time);
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		//double[] ema20 = Indicator.calculateEMA(quotes, 20);
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
			// list of filters
			
			//if ( EF_ifFirstSecondPullBackDown(quotesL, ema20L, 3, 2 ) == false)
			//	return null;
			
			

			//if 	( checkDetectHistoy(lastbarL) == true )
			//	return null;
			
			int downBegin = downPos -1;
			while ((downBegin >= 0) && (((QuoteData)quotesL[downBegin]).high < ema20L[downBegin]))
				downBegin--;

			if ( downBegin > 0 )
			{
				//System.out.println("DownBegin:" + quotesL[downBegin].time);
				// check 1: to see if it did not make a newer high/low
				/*
				double thisLow = Utility.getLow(quotesL, downBegin, downPos).low;

				// is there a chunk before this
				int upPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, downBegin-1, 3);
				int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, downBegin-1, 3);

				if ( downPos2 > upPos2 )
				{
					int downBegin2 = downPos2 -1;
					while (( downBegin2 >= 0) && (((QuoteData)quotesL[downBegin2]).high < ema20L[downBegin2]))
						downBegin2--;

					if ( downBegin2 > 0 )
					{
						double lastLow = Utility.getLow(quotesL, downBegin2, downPos2+1).low;

						if ( thisLow > lastLow )
						{
							//logger.warning(symbol + " " + CHART + " " + " this low did not break new low, trade cancelled ");
							//logger.warning(symbol + " " + CHART + " " + " last high is between " + ((QuoteData)quotesL[upBegin2]).time + ((QuoteData)quotesL[upPos2+1]).time );
							//logger.warning(symbol + " " + CHART + " " + " this high is between " + ((QuoteData)quotesL[upBegin]).time + ((QuoteData)quotesL[upPos+1]).time );
							return null;
						}
					}
				}
				
				if (data.time.equals("20110308  02:00:00"))
				{
					System.out.println("here");
				}
				// check 2, is there a reverse of trend, helps, not but that much
				int lowPos = Utility.getLow( quotesL, downBegin, lastbarL).pos;
				PushHighLow[] highs = Pattern.findPastHighs_no_need_highest(quotesL, lowPos, lastbarL);
				if ( highs != null )
				{
					for ( int i = 0; i < highs.length; i++)
					{
						if ( highs[i].curPos > highs[i].prePos + 4 )
						{
							logger.warning(symbol + " " + CHART + " " + " reversal potential detected, trade removed");
							trade = null;
							return null;
						}
					}
				}*/
			}

			/*
				// check the number of pull backs
				boolean pullBackRequired = false;
				QuoteData lastLow = Utility.getLow(quotesL, downBegin, lastbarL);
				for ( int i = lastLow.pos; i < lastbarL; i++)
				{
					if (((QuoteData)quotesL[i]).close > ((QuoteData)quotesL[i]).open)  // up green bar
					{
					    int j = i;
					    while (((j+1) <= lastbarL) && (((QuoteData)quotesL[j+1]).close > ((QuoteData)quotesL[j+1]).open))
					    	j++;
					    double extend = ((QuoteData)quotesL[j]).high - ((QuoteData)quotesL[i]).low;

					    if ((j > i) && ( extend > 30 * PIP_SIZE ))
					    {
							logger.warning(symbol + " " + CHART + " " + " down begin " + ((QuoteData)quotesL[downBegin]).time);
							logger.warning(symbol + " " + CHART + " " + " last low " + lastLow.time);
							logger.warning(symbol + " " + CHART + " " + " strong pull back up detect between " + ((QuoteData)quotesL[i]).time + " - " + ((QuoteData)quotesL[j]).time + ":" + extend/PIP_SIZE + " pips");
					    	pullBackRequired = true;
					    	break;
					    }

					}
				}

				if ( pullBackRequired )
				{
					for (int s = lastbarL; s > lastLow.pos; s--)
					{
						PushHighLow phl_cur = Pattern.findLastNHigh(quotesL, lastLow.pos, s, 1);
						if (phl_cur != null)
						{
							logger.warning(symbol + " " + CHART + " " + " pull back requirement met");
							pullBackRequired = false;
							break;
						}
					}

				}

				if ( pullBackRequired == true )
					return null;
					 	
			}*/


			logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last down open is at " + ((QuoteData)quotesL[downPos]).time);
			createOpenTrade(Constants.TRADE_RST, Constants.ACTION_SELL);
			trade.detectTime = quotes[lastbar].time;
	        return trade;

		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			//if ( EF_ifFirstSecondPullBackUp(quotesL, ema20L, 3, 2 ) == false)
			//	return null;

			//if 	( checkDetectHistoy(lastbarL) == true )
			//	return null;

			int upBegin = upPos -1;
			while ((upBegin >= 0) && (((QuoteData)quotesL[upBegin]).low > ema20L[upBegin]))
				upBegin--;


			if ( upBegin > 0 )
			{
				// check 1: to see if it did not make a newer high/low
				/*
				double thisHigh = Utility.getHigh(quotesL, upBegin, upPos).high;

				// is there a chunk before this
				int upPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, upBegin-1, 3);
				int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, upBegin-1, 3);

				if ( upPos2 > downPos2 )
				{
					int upBegin2 = upPos2 -1;
					while (( upBegin2 >= 0) && (((QuoteData)quotesL[upBegin2]).low > ema20L[upBegin2]))
					    upBegin2--;

					if ( upBegin2 > 0 )
					{
						QuoteData lastHigh = Utility.getHigh(quotesL, upBegin2, upPos2+1);

						if ( thisHigh < lastHigh.high )
						{
							//logger.warning(symbol + " " + CHART + " " + " this high did not break new high, trade cancelled ");
							//logger.warning(symbol + " " + CHART + " " + " last high is between " + ((QuoteData)quotesL[upBegin2]).time + ((QuoteData)quotesL[upPos2+1]).time );
							//logger.warning(symbol + " " + CHART + " " + " this high is between " + ((QuoteData)quotesL[upBegin]).time + ((QuoteData)quotesL[upPos+1]).time );
							return null;
						}
					}
				}*/
				

				// check 2, is there a reverse of trend
				/*
				int highPos = Utility.getHigh( quotesL, upBegin, lastbarL).pos;
				PushHighLow[] lows = Pattern.findPast2Lows_no_need_lowest(quotesL, highPos, lastbarL);
				if ( lows != null )
				{
					for ( int i = 0; i < lows.length; i++)
					{
						if ( lows[i].curPos > lows[i].prePos + 4 )
						{
							logger.warning(symbol + " " + CHART + " " + " reversal potential detected, trade removed");
							return null;
						}
					}
				}*/

			}

					/*
					// counting if it is a large push from the top
					boolean pullBackRequired = false;
					QuoteData lastHigh = Utility.getHigh(quotesL, upBegin, lastbarL);
					for ( int i = lastHigh.pos; i < lastbarL; i++)
					{
						if (((QuoteData)quotesL[i]).close < ((QuoteData)quotesL[i]).open)
						{
						    int j = i;
						    while (((j+1) <= lastbarL) && (((QuoteData)quotesL[j+1]).close < ((QuoteData)quotesL[j+1]).open))
						    	j++;
						    double extend = ((QuoteData)quotesL[i]).high - ((QuoteData)quotesL[j]).low;

						    if ((j > i) &&( extend > 30 * PIP_SIZE ))
						    {
								logger.warning(symbol + " " + CHART + " " + " strong pull back detect between " + ((QuoteData)quotesL[i]).time + " - " + ((QuoteData)quotesL[j]).time + ":" + extend/PIP_SIZE + " pips");
						    	pullBackRequired = true;
						    	break;
						    }

						}
					}

					if ( pullBackRequired )
					{
						for (int s = lastbarL; s > lastHigh.pos; s--)
						{
							PushHighLow phl_cur = Pattern.findLastNLow(quotesL, lastHigh.pos, s, 1);
							if (phl_cur != null)
							{
								pullBackRequired = false;
								break;
							}
						}

					}

					if ( pullBackRequired == true )
						return null;
						
				}
			}*/

			logger.warning(symbol + " " + CHART + " " + " buy detected " + data.time + " last up open is at " + ((QuoteData)quotesL[upPos]).time);
			createOpenTrade(Constants.TRADE_RST, Constants.ACTION_BUY);
			trade.detectTime = quotes[lastbar].time;
			return trade;
		}

		return null;
	}









	public Trade checkHigherHighLowerLowSetup4(QuoteData data, Object[] quotesL)
	{
			Object[] quotes = getQuoteData();
			int lastbar = quotes.length - 1;
			int lastbarL = quotesL.length - 1;
			double[] ema20L = Indicator.calculateEMA(quotesL, 20);
			double[] ema50L = Indicator.calculateEMA(quotesL, 50);

			int direction = Constants.DIRECTION_UNKNOWN;
			int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 1);
			int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 1);

			if (upPos > downPos)
				direction = Constants.DIRECTION_UP;
			else if (downPos > upPos)
				direction = Constants.DIRECTION_DOWN;

			if ((direction == Constants.DIRECTION_DOWN)
					&& ((((QuoteData) quotesL[lastbarL]).high > ema20L[lastbarL]) || (((QuoteData) quotesL[lastbarL - 1]).high > ema20L[lastbarL - 1])))
			{

				logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last down open is at " + ((QuoteData)quotesL[downPos]).time);
				createOpenTrade(Constants.TRADE_RST, Constants.ACTION_SELL);
				//trade.detectPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 3);
				trade.detectPos = lastbar;
				//detectHistory.add(lastbarL);
		        return trade;

			}
			else if ((direction == Constants.DIRECTION_UP)
					&& ((((QuoteData) quotesL[lastbarL]).low < ema20L[lastbarL]) || (((QuoteData) quotesL[lastbarL - 1]).low < ema20L[lastbarL - 1])))
			{

				logger.warning(symbol + " " + CHART + " " + " buy detected " + data.time + " last up open is at " + ((QuoteData)quotesL[upPos]).time);
				createOpenTrade(Constants.TRADE_RST, Constants.ACTION_BUY);
				//trade.detectPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 3);
				trade.detectPos = lastbar;
				//detectHistory.add(lastbarL);
				return trade;
			}

			return null;
	}

	public Trade checkHigherHighLowerLowSetup5(QuoteData data, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);
		// double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		// double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		int direction = getDirectionBy20MA(quotesL);

		/*
		 * int direction = Constants.DIRECTION_UNKNOWN; if ( ema20L[lastbarL] >
		 * ema50L[lastbarL]) direction = Constants.DIRECTION_UP; else if (
		 * ema20L[lastbarL] < ema50L[lastbarL]) direction =
		 * Constants.DIRECTION_DOWN;
		 */

		if ((((QuoteData) quotes[lastbar]).low > ema20[lastbar]) && (direction == Constants.DIRECTION_DOWN))
		{
			int maCrossOverPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 5);

			if (maCrossOverPos == Constants.NOT_FOUND)
				return null;

			if (lastbar - maCrossOverPos < 10) // too little bars
				return null;

			PushHighLow phl_cur = findLastNHigh(quotes, maCrossOverPos, lastbar, 5);
			if (phl_cur != null)
			{

				// logger.warning(symbol + " " + CHART +
				// " 20MA cross up starts at " +
				// ((QuoteData)quotes[maCrossOverPos]).time );
				if ((((QuoteData) quotes[phl_cur.prePos]).high - phl_cur.pullBack.low) > 10 * PIP_SIZE)
				{
					logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time);
					logger.warning(symbol + " " + CHART + "this high between " + ((QuoteData) quotes[phl_cur.prePos]).time + "@"
							+ ((QuoteData) quotes[phl_cur.prePos]).high + "  -  " + ((QuoteData) quotes[phl_cur.curPos]).time + "@"
							+ ((QuoteData) quotes[phl_cur.curPos]).high + " pullback@" + phl_cur.pullBack);

					// if ( checkBigTimeFramePullBackFromGoingDown( quotesL) ==
					// false )
					// return null;

					trade = new Trade(symbol);
					trade.type = Constants.TRADE_RST;
					trade.action = Constants.ACTION_SELL;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.detectTime = ((QuoteData) quotes[lastbar]).time;
					trade.detectPrice = phl_cur.pullBack.low;
					trade.detectPos = lastbar;
					trade.status = Constants.STATUS_OPEN;

					// place order
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
					trade.price = data.close; // phl_cur.pullBack.low;
					trade.entryPrice = trade.price;
					trade.status = Constants.STATUS_PLACED;
					trade.remainingPositionSize = trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;
					trade.entryPos = lastbar;
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
					logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);

					// trade.stop = Utility.getHigh(quotes, maCrossOverPos,
					// lastbar ).high;
					// if ( trade.price - trade.stop > FIXED_STOP * PIP_SIZE )
					// trade.stop = trade.price - FIXED_STOP * PIP_SIZE;
					trade.stop = data.close + FIXED_STOP * PIP_SIZE;

					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																												// Long(oca).toString());

					return trade;

				}
			}
		}
		else if ((((QuoteData) quotes[lastbar]).high < ema20[lastbar]) && (direction == Constants.DIRECTION_UP))
		{
			int maCrossOverPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 5);

			if (maCrossOverPos == Constants.NOT_FOUND)
				return null;

			if (lastbar - maCrossOverPos < 10) // too little bars
				return null;

			PushHighLow phl_cur = findLastNLow(quotes, maCrossOverPos, lastbar, 5);
			if (phl_cur != null)
			{
				// logger.warning(symbol + " " + CHART +
				// " 20MA cross down starts at " +
				// ((QuoteData)quotes[maCrossOverPos]).time );
				// if ( data.low < phl_cur.pullBack.low )
				if ((phl_cur.pullBack.high - ((QuoteData) quotes[phl_cur.prePos]).low) > 10 * PIP_SIZE)
				{
					// if ( checkBigTimeFramePullBackFromGoingUp( quotesL) ==
					// false )
					// return null;

					logger.warning(symbol + " " + CHART + " minute Higher high break up BUY at " + data.time);
					logger.warning(symbol + " " + CHART + "this high between " + ((QuoteData) quotes[phl_cur.prePos]).time + "@"
							+ ((QuoteData) quotes[phl_cur.prePos]).low + "  -  " + ((QuoteData) quotes[phl_cur.curPos]).time + "@"
							+ ((QuoteData) quotes[phl_cur.curPos]).low + " pullback@" + phl_cur.pullBack);

					trade = new Trade(symbol);
					trade.type = Constants.TRADE_RST;
					trade.action = Constants.ACTION_BUY;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.detectTime = ((QuoteData) quotes[lastbar]).time;
					trade.detectPrice = phl_cur.pullBack.high;
					trade.detectPos = lastbar;
					trade.status = Constants.STATUS_OPEN;

					// place order
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
					trade.price = data.close; // phl_cur.pullBack.low;
					trade.entryPrice = trade.price;
					trade.status = Constants.STATUS_PLACED;
					trade.remainingPositionSize = trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;
					trade.entryPos = lastbar;
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
					logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);

					// trade.stop = Utility.getHigh(quotes, maCrossOverPos,
					// lastbar ).high;
					// if ( trade.price - trade.stop > FIXED_STOP * PIP_SIZE )
					// trade.stop = trade.price - FIXED_STOP * PIP_SIZE;
					trade.stop = data.close - FIXED_STOP * PIP_SIZE;

					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																												// Long(oca).toString());

					return trade;

				}
				return null;
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

	public Trade ResumeAfterPullbackSetup(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		// double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		// double[] ema50L = Indicator.calculateEMA(quotesL, 20);
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
		// int lastPriceConsectiveUpL =
		// Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		// int lastPriceConsectiveDownL =
		// Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);

		if (((QuoteData) quotes[lastbar - 1]).low > ema20[lastbar - 1])
		{
			int crossUp = 0;
			if (ema20[lastbar] < ema50[lastbar])
			{
				// current 20MA is below, need to make sure this is a short pull
				// back
				int crossDown = Pattern.findLastMACrossDown(ema20, ema50);
				if (lastbar - crossDown > 20) // too large a pull back
					return null;

				crossUp = Pattern.findLastMACrossUp(ema20, ema50, crossDown, 10);
			}
			else
			{
				crossUp = Pattern.findLastMACrossUp(ema20, ema50);
			}

			if (lastbar - crossUp < 36)
				return null;

			Object[] peaks = Pattern.findPastHighPeaksAboveMA(quotes, ema20, crossUp, lastbar);

			if ((peaks == null) || (peaks.length < 3))
				return null;

			// logger.warning(symbol + " " + CHART + " cross up is at " +
			// ((QuoteData)quotes[crossUp]).time );

			int lastPeakPos = peaks.length - 1;
			if (((Peak) peaks[lastPeakPos]).pullbackStartPos != 0)
				return null; // already passed

			/*
			 * for ( int i = lastPeakPos; i >=0; i--) { logger.warning(symbol +
			 * " " + CHART + " peak " + i + " high between " +
			 * ((QuoteData)quotes[((Peak)peaks[i]).highlowStartPos]).time + "-"
			 * + ((QuoteData)quotes[((Peak)peaks[i]).highlowEndPos]).time );
			 * logger.warning(symbol + " " + CHART + " peak " + i +
			 * " pullbacks btw " +
			 * ((QuoteData)quotes[((Peak)peaks[i]).pullbackStartPos]).time + "-"
			 * + ((QuoteData)quotes[((Peak)peaks[i]).pullbackEndPos]).time ); }
			 */

			Peak last2 = (Peak) peaks[lastPeakPos - 2];
			Peak last = (Peak) peaks[lastPeakPos - 1];

			double low2 = Utility.getLow(quotes, last2.pullbackStartPos, last2.pullbackEndPos).low;
			double low = Utility.getLow(quotes, last.pullbackStartPos, last.pullbackEndPos).low;

			// if ( low < low2 - 15 * PIP_SIZE )
			// return null; // pull back too large

			// needs to have quotes close below 20MA on last2
			int below = 0;
			for (int i = last.pullbackStartPos; i <= last.pullbackEndPos; i++)
			{
				if (((QuoteData) quotes[i]).high < ema20[i])
				{
					below++;
				}
			}
			if (below < 1) // need to have min 2 bars below
			{
				logger.warning(symbol + " " + CHART + " last peak has less than 2 below 20MA");
				return null;
			}

			if (below > 15) // need to have min 2 bars below
			{
				logger.warning(symbol + " " + CHART + " too many peaks, trend might have changed");
				return null;
			}

			// need to have no quotes close below 20MA on last
			/*
			 * int below2 = 0; for ( int i = last2.pullbackStartPos; i<=
			 * last2.pullbackEndPos; i++) { if ( ((QuoteData)quotes[i]).high <
			 * ema20[i]) { below2++; } } if ( below2 > 1 ) {
			 * logger.warning(symbol + " " + CHART +
			 * " last-1 peak has more than 1  below 20MA"); return null; }
			 */

			trade = new Trade(symbol);
			logger.warning(symbol + " " + CHART + " minute cross to low detected at " + data.time);
			trade.type = Constants.TRADE_PBK;
			trade.action = Constants.ACTION_BUY;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.detectTime = ((QuoteData) quotes[lastbar]).time;
			trade.detectPos = lastbar;
			// trade.status = Constants.STATUS_OPEN;

			trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
			trade.price = data.close;
			trade.entryPrice = trade.price;
			trade.status = Constants.STATUS_PLACED;
			trade.remainingPositionSize = trade.POSITION_SIZE;
			trade.entryTime = ((QuoteData) quotes[lastbar]).time;
			trade.entryPos = lastbar;
			// AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL,
			// trade.positionSize, data.close);
			AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);

			trade.stop = Utility.getLow(quotes, last.pullbackStartPos, last.pullbackEndPos).low;

			trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
			logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
			trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																										// Long(oca).toString());

			return trade;
		}

		return null;
	}

	public Trade MicroDoubleTopPullbackSetup(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 20);

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
		// int lastPriceConsectiveUpL =
		// Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		// int lastPriceConsectiveDownL =
		// Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);

		int trend = Constants.TREND_UNKNOWN;

		if (ema20L[lastbarL] > ema50L[lastbarL])
		{
			int lastMACrossUp = Pattern.findLastMACrossUp(ema20L, ema50L, 12);
			if ((lastbarL - lastMACrossUp) > 12)
			{
				trend = Constants.TREND_UP;
			}
			else
			{
				int lastMACrossDown = Pattern.findLastMACrossDown(ema20L, ema50L, lastMACrossUp, 12);
				if (lastMACrossUp - lastMACrossDown > 20)
				{
					trend = Constants.TREND_DOWN;
				}
			}
		}
		else if (ema20L[lastbarL] < ema50L[lastbarL])
		{
			int lastMACrossDown = Pattern.findLastMACrossDown(ema20L, ema50L, 12);
			if ((lastbarL - lastMACrossDown) > 12)
			{
				trend = Constants.TREND_DOWN;
			}
			else
			{
				int lastMACrossUp = Pattern.findLastMACrossUp(ema20L, ema50L, lastMACrossDown, 12);
				if (lastMACrossDown - lastMACrossUp > 20)
				{
					trend = Constants.TREND_UP;
				}
			}
		}

		if (((QuoteData) quotes[lastbar - 1]).low > ema20[lastbar - 1])
		{
			int crossUp = 0;
			if (ema20[lastbar] < ema50[lastbar])
			{
				// current 20MA is below, need to make sure this is a short pull
				// back
				int crossDown = Pattern.findLastMACrossDown(ema20, ema50);
				if (lastbar - crossDown > 20) // too large a pull back
					return null;

				crossUp = Pattern.findLastMACrossUp(ema20, ema50, crossDown, 10);
			}
			else
			{
				crossUp = Pattern.findLastMACrossUp(ema20, ema50);
			}

			if (lastbar - crossUp < 36)
				return null;

			Object[] peaks = Pattern.findPastHighPeaksAboveMA(quotes, ema20, crossUp, lastbar);

			if ((peaks == null) || (peaks.length < 3))
				return null;

			// logger.warning(symbol + " " + CHART + " cross up is at " +
			// ((QuoteData)quotes[crossUp]).time );

			int lastPeakPos = peaks.length - 1;
			if (((Peak) peaks[lastPeakPos]).pullbackStartPos != 0)
				return null; // already passed

			/*
			 * for ( int i = lastPeakPos; i >=0; i--) { logger.warning(symbol +
			 * " " + CHART + " peak " + i + " high between " +
			 * ((QuoteData)quotes[((Peak)peaks[i]).highlowStartPos]).time + "-"
			 * + ((QuoteData)quotes[((Peak)peaks[i]).highlowEndPos]).time );
			 * logger.warning(symbol + " " + CHART + " peak " + i +
			 * " pullbacks btw " +
			 * ((QuoteData)quotes[((Peak)peaks[i]).pullbackStartPos]).time + "-"
			 * + ((QuoteData)quotes[((Peak)peaks[i]).pullbackEndPos]).time ); }
			 */

			Peak last2 = (Peak) peaks[lastPeakPos - 2];
			Peak last = (Peak) peaks[lastPeakPos - 1];

			double low2 = Utility.getLow(quotes, last2.pullbackStartPos, last2.pullbackEndPos).low;
			double low = Utility.getLow(quotes, last.pullbackStartPos, last.pullbackEndPos).low;

			// if ( low < low2 - 15 * PIP_SIZE )
			// return null; // pull back too large

			// needs to have quotes close below 20MA on last2
			int below = 0;
			for (int i = last.pullbackStartPos; i <= last.pullbackEndPos; i++)
			{
				if (((QuoteData) quotes[i]).high < ema20[i])
				{
					below++;
				}
			}
			if (below < 1) // need to have min 2 bars below
			{
				logger.warning(symbol + " " + CHART + " last peak has less than 2 below 20MA");
				return null;
			}

			if (below > 15) // need to have min 2 bars below
			{
				logger.warning(symbol + " " + CHART + " too many peaks, trend might have changed");
				return null;
			}

			// need to have no quotes close below 20MA on last
			/*
			 * int below2 = 0; for ( int i = last2.pullbackStartPos; i<=
			 * last2.pullbackEndPos; i++) { if ( ((QuoteData)quotes[i]).high <
			 * ema20[i]) { below2++; } } if ( below2 > 1 ) {
			 * logger.warning(symbol + " " + CHART +
			 * " last-1 peak has more than 1  below 20MA"); return null; }
			 */

			trade = new Trade(symbol);
			logger.warning(symbol + " " + CHART + " minute cross to low detected at " + data.time);
			trade.type = Constants.TRADE_PBK;
			trade.action = Constants.ACTION_BUY;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.detectTime = ((QuoteData) quotes[lastbar]).time;
			trade.detectPos = lastbar;
			// trade.status = Constants.STATUS_OPEN;

			trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
			trade.price = data.close;
			trade.entryPrice = trade.price;
			trade.status = Constants.STATUS_PLACED;
			trade.remainingPositionSize = trade.POSITION_SIZE;
			trade.entryTime = ((QuoteData) quotes[lastbar]).time;
			trade.entryPos = lastbar;
			// AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL,
			// trade.positionSize, data.close);
			AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);

			trade.stop = Utility.getLow(quotes, last.pullbackStartPos, last.pullbackEndPos).low;

			trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
			logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
			trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																										// Long(oca).toString());

			return trade;
		}

		return null;
	}

	public Trade checkTopSetup(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		// int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		// double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		// double[] ema50L = Indicator.calculateEMA(quotesL, 50);

		int SecondWidth = 2;
		int FirstWidth = 1;
		int BufferWidth = 5;

		// int upPos = Pattern.findLastMAConsectiveUp(ema20, ema50, 10);
		// int downPos = Pattern.findLastMAConsectiveDown(ema20, ema50, 10);

		if (((QuoteData) quotes[lastbar]).low > ema20[lastbar])
		{
			if (ema20[lastbar] < ema50[lastbar])
				return null;

			int crossUp = Pattern.findLastMACrossUp(ema20, ema50); // at least
																	// for 2
																	// hours +

			int crossdown = 0;
			int crossup1 = 0;
			if (crossUp > lastbar - 48)
			{
				crossdown = Pattern.findLastMACrossDown(ema20, ema50, crossUp - 1, 2);
				// logger.warning(symbol + " " + CHART + " crossdown= " +
				// crossdown );
				if ((crossUp - crossdown) > 24)
					return null;

				crossup1 = Pattern.findLastMACrossUp(ema20, ema50, crossdown - 1, 2);
				// logger.warning(symbol + " " + CHART + " crossup1= " +
				// crossup1 );
				if ((crossdown - crossup1) < 36)
					return null;
			}

			PushHighLow phl_cur = findLastNHigh(quotes, crossUp, lastbar, 4);
			if (phl_cur == null)
				return null;

			// last check large data chart
			trade = new Trade(symbol);
			double entryQualifyPrice = ((QuoteData) quotes[phl_cur.prePos]).high;

			logger.warning(symbol + " " + CHART + " last 20/50 cross up is at " + ((QuoteData) quotes[crossUp]).time);
			logger.warning(symbol + " " + CHART + " last 20/50 cross down is at " + ((QuoteData) quotes[crossdown]).time);
			logger.warning(symbol + " " + CHART + " last 20/50 cross up1 is at " + ((QuoteData) quotes[crossup1]).time);

			logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time);
			logger.warning(symbol + " " + CHART + "this high between " + ((QuoteData) quotes[phl_cur.prePos]).time + "@"
					+ ((QuoteData) quotes[phl_cur.prePos]).high + "  -  " + ((QuoteData) quotes[phl_cur.curPos]).time + "@"
					+ ((QuoteData) quotes[phl_cur.curPos]).high + " pullback@" + phl_cur.pullBack);
			// logger.warning(symbol + " " + CHART + "last high between " +
			// ((QuoteData)quotes[phl_pre.prePos]).time + "@" +
			// ((QuoteData)quotes[phl_pre.prePos]).high + "  -  " +
			// ((QuoteData)quotes[phl_pre.curPos]).time + "@" +
			// ((QuoteData)quotes[phl_pre.curPos]).high + " pullback@" +
			// phl_pre.pullBack );
			logger.warning(symbol + " " + CHART + " entry Qualify Price is " + entryQualifyPrice);

			trade.type = Constants.TRADE_TOP;
			trade.action = Constants.ACTION_SELL;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.detectTime = ((QuoteData) quotes[lastbar]).time;
			trade.detectPrice = ((QuoteData) quotes[phl_cur.prePos]).high;
			trade.detectPos = lastbar;
			trade.prePos = phl_cur.prePos;
			trade.pullBackPos = phl_cur.pullBack.pos;
			trade.status = Constants.STATUS_OPEN;
			trade.entryQualifyPrice = entryQualifyPrice;
			trade.entryQualifyPricePos = phl_cur.curPos;// lastbarS;//;
			trade.reEnter = 1;
			return trade;
		}/*
		 * else if (((QuoteData)quotes[lastbar]).high < ema20[lastbar] ) { if (
		 * ema20[lastbar] > ema50[lastbar] ) return null;
		 *
		 * int crossDown = Pattern.findLastMACrossDown(ema20, ema50, 24); // at
		 * least for 2 hours +
		 *
		 * if ( crossDown > lastbar - 48 ) return null;
		 *
		 * PushHighLow phl_cur = findLastNLow( quotes, crossDown, lastbar, 4 );
		 * if ( phl_cur == null ) return null;
		 *
		 * // last check large data chart trade = new Trade(symbol); double
		 * entryQualifyPrice = ((QuoteData)quotes[phl_cur.prePos]).low;
		 *
		 * logger.warning(symbol + " " + CHART + " last 20/50 cross down is at "
		 * + ((QuoteData)quotes[crossDown]).time );
		 *
		 * logger.warning(symbol + " " + CHART +
		 * " minute Lower low break up BUY at " + data.time );
		 * logger.warning(symbol + " " + CHART + "this low between " +
		 * ((QuoteData)quotes[phl_cur.prePos]).time + "@" +
		 * ((QuoteData)quotes[phl_cur.prePos]).low + "  -  " +
		 * ((QuoteData)quotes[phl_cur.curPos]).time + "@" +
		 * ((QuoteData)quotes[phl_cur.curPos]).low + " pullback@" +
		 * phl_cur.pullBack ); //logger.warning(symbol + " " + CHART +
		 * "last high between " + ((QuoteData)quotes[phl_pre.prePos]).time + "@"
		 * + ((QuoteData)quotes[phl_pre.prePos]).high + "  -  " +
		 * ((QuoteData)quotes[phl_pre.curPos]).time + "@" +
		 * ((QuoteData)quotes[phl_pre.curPos]).high + " pullback@" +
		 * phl_pre.pullBack ); logger.warning(symbol + " " + CHART +
		 * " entry Qualify Price is " + entryQualifyPrice );
		 *
		 * trade.type = Constants.TRADE_TOP; trade.action =
		 * Constants.ACTION_BUY; trade.positionSize = POSITION_SIZE;
		 * trade.detectTime = ((QuoteData)quotes[lastbar]).time;
		 * trade.detectPrice = ((QuoteData)quotes[phl_cur.prePos]).low;
		 * trade.detectPos = lastbar; trade.prePos = phl_cur.prePos;
		 * trade.pullBackPos = phl_cur.pullBack.pos; trade.status =
		 * Constants.STATUS_OPEN; trade.entryQualifyPrice = entryQualifyPrice;
		 * trade.entryQualifyPricePos = phl_cur.curPos;//lastbarS;//;
		 * trade.reEnter = 1; return trade; }
		 */
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
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data);

		if (trade != null)
		{
			if (Constants.TRADE_RST.equals(trade.type))
				trackHigherHighLowerLowEntry(data, quotesL);
		}
	}

	void enterSellPosition(QuoteData data, int lastbar, int lastbarL)
	{
		logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
		trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
		trade.price = data.close;
		trade.entryPrice = data.close;
		// trade.triggerHighLow = Utility.getHigh( quotes, detectPos, lastbar);
		trade.status = Constants.STATUS_PLACED;
		trade.remainingPositionSize = trade.POSITION_SIZE;
		trade.entryTime = data.time;
		trade.entryPos = lastbar;
		trade.entryPosL = lastbarL;
		// trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
		if (MODE == Constants.TEST_MODE)
			AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);

		trade.stop = data.close + 2 * FIXED_STOP * PIP_SIZE;
		trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
		logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
		trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																									// Long(oca).toString());

		return;

	}

	void enterBuyPosition(QuoteData data, int lastbar, int lastbarL)
	{
		logger.warning(symbol + " " + CHART + " place market buy order at " + data.time);
		trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
		trade.price = data.close;
		trade.entryPrice = data.close;
		// trade.triggerHighLow = Utility.getLow( quotes, detectPos, lastbar);
		trade.status = Constants.STATUS_PLACED;
		trade.position = Constants.POSITION_LONG;
		trade.remainingPositionSize = trade.POSITION_SIZE;
		trade.entryTime = data.time;
		trade.entryPos = lastbar;
		trade.entryPosL = lastbarL;
		// trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
		if (MODE == Constants.TEST_MODE)
			AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);

		// calculate and place stop order
		// long oca = new Date().getTime();
		// trade.oca = oca;
		trade.stop = data.close - 2 * FIXED_STOP * PIP_SIZE;
		trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
		logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
		trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																									// Long(oca).toString());
	}

	public void trackPullBackTradeEntry(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +
		// trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData) quotes[lastbar]).low < ((QuoteData) quotes[lastbar - 1]).low)
					&& (((QuoteData) quotes[lastbar]).high < ((QuoteData) quotes[lastbar - 1]).high))
			{
				// place order
				logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
				trade.price = ((QuoteData) quotes[lastbar - 1]).low;
				trade.entryPrice = trade.price;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

				if (MODE == Constants.REAL_MODE)
				{
					// trade.price = adjustPrice( trade.price,
					// Constants.ADJUST_TYPE_UP);
					// trade.orderId = placeLmtOrder(Constants.ACTION_SELL,
					// trade.price, trade.positionSize, null);
					// trade.orderPlaced = true;
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				}
				else if (MODE == Constants.TEST_MODE)
				{
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
				}

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				trade.stop = Utility.getHigh(quotes, trade.detectPos, lastbar).high;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return;

			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData) quotes[lastbar]).high > ((QuoteData) quotes[lastbar - 1]).high)
					&& (((QuoteData) quotes[lastbar]).low > ((QuoteData) quotes[lastbar - 1]).low))
			{
				logger.warning(symbol + " " + CHART + " place market buy order");
				trade.price = ((QuoteData) quotes[lastbar - 1]).high;
				trade.entryPrice = data.close;
				trade.position = Constants.POSITION_LONG;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

				if (MODE == Constants.REAL_MODE)
				{
					// trade.price = adjustPrice( trade.price,
					// Constants.ADJUST_TYPE_DOWN);
					// trade.orderId = placeLmtOrder(Constants.ACTION_BUY,
					// trade.price, trade.positionSize, null);
					// trade.orderPlaced = true;
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				}
				else if (MODE == Constants.TEST_MODE)
				{
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
				}

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				trade.stop = Utility.getLow(quotes, trade.detectPos, lastbar).low;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return;

			}
		}
	}

	public void trackRSCTradeEntry2(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +
		// trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			// if (((QuoteData)quotes[lastbar]).low < trade.rscEntryPrice )
			if (((QuoteData) quotes[lastbar]).high > ((QuoteData) quotes[lastbar - 1]).high)
			{
				// place order
				logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
				trade.price = ((QuoteData) quotes[lastbar - 1]).low;
				trade.entryPrice = trade.price;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				// trade.stop = Utility.getHigh(quotes, trade.detectPos,
				// lastbar).high;
				trade.stop = trade.price + FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				trade.profitStartingPointing = Utility.getHigh(quotes, trade.detectPos, lastbar).pos;
				return;

			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			// if (((QuoteData)quotes[lastbar]).high > trade.rscEntryPrice )
			if (((QuoteData) quotes[lastbar]).low < ((QuoteData) quotes[lastbar - 1]).low)
			{
				logger.warning(symbol + " " + CHART + " place market buy order");
				trade.price = data.close;// ((QuoteData)quotes[lastbar-1]).high;
				trade.entryPrice = data.close;
				trade.position = Constants.POSITION_LONG;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				// trade.stop = Utility.getLow(quotes, trade.detectPos,
				// lastbar).low;
				trade.stop = trade.price - FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				trade.profitStartingPointing = Utility.getLow(quotes, trade.detectPos, lastbar).pos;
				return;

			}
		}
	}

	public void trackCrossEntry(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +
		// trade.detectTime);
		Object[] quotes = getQuoteData();
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbar = quotes.length - 1;

		if (lastbar == trade.detectPos)
			return;

		int touch20MAPos = -1;

		for (int i = trade.detectPos + 1; i < lastbar; i++)
		{
			if ((((QuoteData) quotes[i]).low < ema20[i]) && (((QuoteData) quotes[i]).high > ema20[i]))
			{
				touch20MAPos = i;
				break;
			}
		}

		if (touch20MAPos == -1)
			return;

		for (int i = touch20MAPos + 2; i <= lastbar; i++)
		{
			if ((((QuoteData) quotes[i - 1]).high < ema20[i - 1]) && (((QuoteData) quotes[i - 2]).high < ema20[i - 2]))
			{
				// first breakout downside
				if (trade.action.equals(Constants.ACTION_SELL))
				{
					// place order
					logger.warning(symbol + " " + CHART + " cross sell triggered at " + data.time);
					trade.price = data.close;
					trade.entryPrice = trade.price;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;
					trade.entryPos = lastbar;

					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);

					trade.remainingPositionSize = trade.POSITION_SIZE;
					trade.status = Constants.STATUS_PLACED;

					// trade.stop = Utility.getHigh(quotes, trade.detectPos,
					// lastbar).high;
					trade.stop = trade.price + FIXED_STOP * PIP_SIZE;
					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																												// Long(oca).toString());

					return;

				}
				else if (trade.action.equals(Constants.ACTION_BUY))
				{
					trade = null;
					return;
				}
			}
			else if ((((QuoteData) quotes[i - 1]).low > ema20[i - 1]) && (((QuoteData) quotes[i - 2]).low > ema20[i - 2]))
			{
				if (trade.action.equals(Constants.ACTION_BUY))
				{
					// place order
					logger.warning(symbol + " " + CHART + " cross buy triggered at " + data.time);
					trade.price = data.close;
					trade.entryPrice = data.close;
					trade.position = Constants.POSITION_LONG;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;
					trade.entryPos = lastbar;

					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);

					trade.remainingPositionSize = trade.POSITION_SIZE;
					trade.status = Constants.STATUS_PLACED;

					// trade.stop = Utility.getLow(quotes, trade.detectPos,
					// lastbar).low;
					trade.stop = trade.price - FIXED_STOP * PIP_SIZE;
					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																												// Long(oca).toString());

					trade.profitStartingPointing = Utility.getLow(quotes, trade.detectPos, lastbar).pos;

				}
				else if (trade.action.equals(Constants.ACTION_SELL))
				{
					trade = null;
					return;
				}

			}
		}
	}

	public void trackNormalPullBackEntry(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +
		// trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData) quotes[lastbar]).low < ((QuoteData) quotes[lastbar - 1]).low)
					&& (((QuoteData) quotes[lastbar]).high < ((QuoteData) quotes[lastbar - 1]).high))
			{
				// place order
				logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
				trade.price = ((QuoteData) quotes[lastbar - 1]).low;
				trade.entryPrice = trade.price;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

				if (MODE == Constants.REAL_MODE)
				{
					// trade.price = adjustPrice( trade.price,
					// Constants.ADJUST_TYPE_UP);
					// trade.orderId = placeLmtOrder(Constants.ACTION_SELL,
					// trade.price, trade.positionSize, null);
					// trade.orderPlaced = true;
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				}
				else if (MODE == Constants.TEST_MODE)
				{
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
				}

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				// trade.stop = Utility.getHigh(quotes, trade.detectPos,
				// lastbar).high;
				trade.stop = trade.price + 20 * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return;

			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData) quotes[lastbar]).high > ((QuoteData) quotes[lastbar - 1]).high)
					&& (((QuoteData) quotes[lastbar]).low > ((QuoteData) quotes[lastbar - 1]).low))
			{
				logger.warning(symbol + " " + CHART + " place market buy order");
				trade.price = ((QuoteData) quotes[lastbar - 1]).high;
				trade.entryPrice = data.close;
				trade.position = Constants.POSITION_LONG;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

				if (MODE == Constants.REAL_MODE)
				{
					// trade.price = adjustPrice( trade.price,
					// Constants.ADJUST_TYPE_DOWN);
					// trade.orderId = placeLmtOrder(Constants.ACTION_BUY,
					// trade.price, trade.positionSize, null);
					// trade.orderPlaced = true;
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				}
				else if (MODE == Constants.TEST_MODE)
				{
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
				}

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				// trade.stop = Utility.getLow(quotes, trade.detectPos,
				// lastbar).low;
				trade.stop = trade.price - 20 * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return;

			}
		}
	}

	public void trackHigherHighLowerLowEntry_bak(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +  trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		//double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData) quotesL[lastbarL]).low > ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).low > ema20L[lastbarL - 1]))
			{
				//logger.warning(symbol + " " + CHART + " triggered above 20MA, remove trade");
				trade = null;
				return;
			}

			int start = Utility.getLow(quotes, lastbar - 36, lastbar - 3).pos;

			for (int i = lastbar; i > start; i--)
			{
				/*
				 PushHighLow[] phls = Pattern.findPast2Highs(quotes, start, i );
				 if (( phls != null) && ( phls.length >= 1 ))
				 {
					 PushHighLow  phl_cur = phls[0];  */
				 // logger.warning(symbol + " " + CHART + "@@@this high between " +
				 // ((QuoteData)quotes[phl_cur.prePos]).time + "@" + ((QuoteData)quotes[phl_cur.prePos]).high + "  -  " +
				 // ((QuoteData)quotes[phl_cur.curPos]).time + "@" + ((QuoteData)quotes[phl_cur.curPos]).high + " pullback@" +  phl_cur.pullBack.time );

				PushHighLow phl_cur = Pattern.findLastNHigh(quotes, start, i, 2);
				if (phl_cur != null)
				{
					// check 1: large pull back
					if ((phl_cur.curPos == lastbar) && (prremptive_limit == true))
					{
						if ((((QuoteData) quotes[phl_cur.prePos]).high - phl_cur.pullBack.low > this.ENTRY_LARGE_PULLBACK * PIP_SIZE) && (trade.limit1Placed == false))
						{
							if (previousTradeExist(phl_cur.pullBack.pos))
								return;

							logger.warning(symbol + " " + CHART + " detected for limit order " + data.time);
							trade.pullBackPos = phl_cur.pullBack.pos;

							trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limitPos1 = trade.POSITION_SIZE / 2;
							trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);

							trade.limit1Stop = adjustPrice(trade.limitPrice1 + 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limit1Placed = true;
							return;

						}
					}

					// check 2: large break outs
					if ((phl_cur.curPos == lastbar) && (breakout_limit == true))
					{
						// if the pull back is larger than 20 pips, put one
						// limit positions
						if ((data.high - (((QuoteData) quotes[phl_cur.prePos]).low) > ENTRY_LARGE_BREAKOUT * PIP_SIZE) && (trade.limit2Placed == false))
						{
							if (previousTradeExist(phl_cur.pullBack.pos))
								return;

							logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
							trade.pullBackPos = phl_cur.pullBack.pos;

							trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limitPos1 = trade.POSITION_SIZE / 2;
							trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
							logger.warning(symbol + " " + CHART + " limit order 1 place " + trade.limitPrice1 + " " + trade.limitPos1 + " " + data.time);

							trade.limit1Stop = adjustPrice(trade.limitPrice1 + 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP);

							trade.limitPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 25 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limitPos2 = trade.POSITION_SIZE / 2;
							trade.limitId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
							logger.warning(symbol + " " + CHART + " limit order 2 place " + trade.limitPrice2 + " " + trade.limitPos2 + " " + data.time);

							trade.limit2Stop = adjustPrice(trade.limitPrice2 + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limit2Placed = true;

							return;
						}
					}

					// check 3: small exhausting breakouts
					if (data.low < phl_cur.pullBack.low)
					{
						// logger.warning(symbol + " " + CHART + "!!!!!!!! detected below last low - " + data.time );
						if (previousTradeExist(phl_cur.pullBack.pos))
							return;

						/*
						 * logger.warning("@@@@@@@@@@@@@" ); for ( int j = 0; j
						 * < phls.length; j++) { PushHighLow phl = phls[j];
						 * logger.warning(symbol + " " + CHART +
						 * "@@@this high between " +
						 * ((QuoteData)quotes[phl.prePos]).time + "@" +
						 * ((QuoteData)quotes[phl.prePos]).high + "  -  " +
						 * ((QuoteData)quotes[phl.curPos]).time + "@" +
						 * ((QuoteData)quotes[phl.curPos]).high + " pullback@" +
						 * phl.pullBack.time ); }
						 */

						for (int j = phl_cur.pullBack.pos + 1; j < lastbar; j++)
						{
							if (((QuoteData) quotes[j]).low < phl_cur.pullBack.low)
							{
								logger.warning(symbol + " " + CHART + ((QuoteData) quotes[j]).time + " below pullback low of "
										+ ((QuoteData) quotes[phl_cur.pullBack.pos]).time + " , trade missed");
								trade = null;
								return;
							}
						}

						double highest = Utility.getLow(quotes, lastbar - 24, lastbar).high;
						if ((highest - data.low) > 40 * PIP_SIZE)
						{
							logger.warning(symbol + " " + CHART + " entry too wide ");
							trade = null;
							return;
						}

						// remove all limit order
						cancelOrder(trade.limitId1);
						cancelOrder(trade.limitId2);
						trade.limitId1 = trade.limitId2 = 0;

						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.pullBackPos = phl_cur.pullBack.pos;

						int tradeSize = trade.POSITION_SIZE - trade.remainingPositionSize;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, tradeSize);
						trade.price = phl_cur.pullBack.low;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize += tradeSize;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						if ((((QuoteData) quotesL[lastbarL]).low > ema20L[lastbarL])
								&& (((QuoteData) quotesL[lastbarL - 1]).low > ema20L[lastbarL - 1]))

						logger.warning(symbol + " " + CHART + " L:low" + ((QuoteData) quotesL[lastbarL]).low + " ema20L:" + ema20L[lastbarL]);
						logger.warning(symbol + " " + CHART + " L:low" + ((QuoteData) quotesL[lastbarL - 1]).low + " ema20L:" + ema20L[lastbarL - 1]);

						logger.warning(symbol + " " + CHART + " trend starts at " + ((QuoteData) quotes[start]).time);
						logger.warning(symbol + " " + CHART + "@@@this high between " + ((QuoteData) quotes[phl_cur.prePos]).time + "@"
								+ ((QuoteData) quotes[phl_cur.prePos]).high + "  -  " + ((QuoteData) quotes[phl_cur.curPos]).time + "@"
								+ ((QuoteData) quotes[phl_cur.curPos]).high + " pullback@" + phl_cur.pullBack.time);
						logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);

						if (trade.recordOpened == false)
						{
							CreateTradeRecord(trade.type, Constants.ACTION_SELL);
							trade.recordOpened = true;
						}
						AddOpenRecord(data.time, Constants.ACTION_SELL, tradeSize, trade.price);
						// AddTradeOpenRecord( trade.type,data.time, Constants.ACTION_SELL, trade.positionSize, trade.price);

						// re-addjust stop
						cancelOrder(trade.stopId);
						trade.stop = Utility.getHigh(quotes, phl_cur.pullBack.pos, lastbar).high;
						if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());
						return;
					}

					// end of if, need to break the for loop
					return;
					// }
				}
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData) quotesL[lastbarL]).high < ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).high < ema20L[lastbarL - 1]))
			{
				logger.warning(symbol + " " + CHART + " triggered above 20MA, remove trade");
				trade = null;
				return;
			}

			int start = Utility.getHigh(quotes, lastbar - 36, lastbar - 3).pos;

			for (int i = lastbar; i > start; i--)
			{
				/*
				 * PushHighLow[] phls = Pattern.findPast2Lows(quotes, start, i
				 * );
				 *
				 * if (( phls != null) && ( phls.length >= 1 )) { PushHighLow
				 * phl_cur = phls[0];
				 */
				PushHighLow phl_cur = Pattern.findLastNLow(quotes, start, i, 2);
				if (phl_cur != null)
				{
					// check 1: large pull back
					if ((phl_cur.curPos == lastbar) && (prremptive_limit == true))
					{
						// if the pull back is larger than 20 pips, put one
						// limit positions
						if ((phl_cur.pullBack.high - ((QuoteData) quotes[phl_cur.prePos]).low > 19 * PIP_SIZE) && (trade.limit1Placed == false))
						{
							if (previousTradeExist(phl_cur.pullBack.pos))
								return;

							logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
							trade.pullBackPos = phl_cur.pullBack.pos;

							// place order public int placeLmtOrder(String
							// action, double limitPrice, int posSize, String
							// ocaGroup)
							trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.limitPos1 = trade.POSITION_SIZE / 2;
							trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);
							logger.warning(symbol + " " + CHART + " limit order place " + trade.limitPrice1 + " " + trade.limitPos1 + " " + data.time);

							trade.limit1Stop = adjustPrice(trade.limitPrice1 - 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limit1Placed = true;
							return;

						}
					}

					// check 2: large break outs
					if ((phl_cur.curPos == lastbar) && (breakout_limit == true))
					{
						// if the pull back is larger than 20 pips, put one
						// limit positions
						if (((((QuoteData) quotes[phl_cur.prePos]).low - data.low) > 10 * PIP_SIZE) && (trade.limit2Placed == false))
						{
							if (previousTradeExist(phl_cur.pullBack.pos))
								return;

							logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
							trade.pullBackPos = phl_cur.pullBack.pos;

							// place order public int placeLmtOrder(String
							// action, double limitPrice, int posSize, String
							// ocaGroup)
							trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 20 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.limitPos1 = trade.POSITION_SIZE / 2;
							trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);
							logger.warning(symbol + " " + CHART + " limit order 1 place " + trade.limitPrice1 + " " + trade.limitPos1 + " "
									+ data.time);

							trade.limit1Stop = adjustPrice(trade.limitPrice1 - 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP);

							trade.limitPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 25 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.limitPos2 = trade.POSITION_SIZE / 2;
							trade.limitId2 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice2, trade.limitPos2, null);
							logger.warning(symbol + " " + CHART + " limit order 2 place " + trade.limitPrice2 + " " + trade.limitPos2 + " "
									+ data.time);

							trade.limit2Stop = adjustPrice(trade.limitPrice2 - 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limit2Placed = true;

							trade.limit1Placed = true;
							return;

						}
					}

					// check 3: small exhausting breakouts

					if (data.high > phl_cur.pullBack.high)
					{
						// logger.warning(symbol + " " + CHART +
						// "!!!!!!!! detected above last high - " + data.time );
						if (previousTradeExist(phl_cur.pullBack.pos))
							return;

						/*
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

						for (int j = phl_cur.pullBack.pos + 1; j < lastbar; j++)
						{
							if (((QuoteData) quotes[j]).high > phl_cur.pullBack.high)
							{
								logger.warning(symbol + " " + CHART + " trade missed");
								trade = null;
								return;
							}
						}

						double lowest = Utility.getLow(quotes, lastbar - 24, lastbar).low;
						if ((data.high - lowest) > 40 * PIP_SIZE)
						{
							logger.warning(symbol + " " + CHART + " entry too wide ");
							trade = null;
							return;
						}

						cancelOrder(trade.limitId1);
						cancelOrder(trade.limitId2);
						trade.limitId1 = trade.limitId2 = 0;

						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.pullBackPos = phl_cur.pullBack.pos;

						int tradeSize = trade.POSITION_SIZE - trade.remainingPositionSize;

						// place order
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = phl_cur.pullBack.high;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						logger.warning(symbol + " " + CHART + " trend starts at " + ((QuoteData) quotes[start]).time);
						logger.warning(symbol + " " + CHART + "@@@this low between " + ((QuoteData) quotes[phl_cur.prePos]).time + "@"
								+ ((QuoteData) quotes[phl_cur.prePos]).low + "  -  " + ((QuoteData) quotes[phl_cur.curPos]).time + "@"
								+ ((QuoteData) quotes[phl_cur.curPos]).low + " pullback@" + phl_cur.pullBack.time);
						logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);

						if (trade.recordOpened == false)
						{
							CreateTradeRecord(trade.type, Constants.ACTION_BUY);
							trade.recordOpened = true;
						}
						AddOpenRecord(data.time, Constants.ACTION_BUY, tradeSize, trade.price);
						// AddTradeOpenRecord( trade.type,data.time,
						// Constants.ACTION_BUY, trade.positionSize,
						// trade.price);

						trade.stop = Utility.getLow(quotes, phl_cur.pullBack.pos, lastbar).low;
						if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());
					}

					// end of if, need to break the for loop
					return;
				}
				// }
			}
		}
	}





	
	
	public void trackHigherHighLowerLowEntry(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +  trade.detectTime);
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		//double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		double[] ema100 = Indicator.calculateEMA(quotes, 100);
		double[] ema200 = Indicator.calculateEMA(quotes, 200);

		if ( trade.limit1Placed == true )
			return;

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			//if (ema100[lastbar]> ema200[lastbar])
			if ((((QuoteData) quotesL[lastbarL]).low > ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).low > ema20L[lastbarL - 1]))
			{
				logger.warning(symbol + " " + CHART + " triggered above 20MA, remove trade");
				trade = null;
				return;
			}

			
			//int start = Utility.getLow(quotes, lastbar - 36, lastbar - 3).pos;
			int detectpos = findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			int start = Utility.getLow(quotes, detectpos-10, detectpos-1).pos;

			
			
			/*int detectpos = findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			int start = Utility.getLow(quotes, detectpos - 5, detectpos).pos;
			if ( trade.entryPullBackLow == 0 )
			{
				trade.entryPullBackLow = quotes[start].low;
				trade.entryPullBackTime = quotes[start].time;
			}*/
			
			for (int s = lastbar; s > start; s--)
			{
				PushHighLow phl_cur = Pattern.findLastNHigh(quotes, start, s, 2);
				if (phl_cur != null)
				{
					/*
					logger.warning(symbol + " " + CHART + "@@@this entry low between " +
							 ((QuoteData)quotes[phl_cur.prePos]).time + "@" + ((QuoteData)quotes[phl_cur.prePos]).high + "  -  " +
							 ((QuoteData)quotes[phl_cur.curPos]).time + "@" + ((QuoteData)quotes[phl_cur.curPos]).high + " pullback@" +
							 phl_cur.pullBack.time );
				*/

					if (previousTradeExist(phl_cur.pullBack.pos))
						return;

					if ( phl_cur.curPos == lastbar )
					{
						if ((prremptive_limit == true) && (( quotes[phl_cur.prePos].high - phl_cur.pullBack.low )  > FIXED_STOP * 1.5 * PIP_SIZE))
						{
							/*
							logger.warning(symbol + " " + CHART + " detected for limit order " + data.time);
							trade.pullBackPos = phl_cur.pullBack.pos;

							trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limitPos1 = trade.positionSize;
							trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);

							trade.limit1Stop = adjustPrice(trade.limitPrice1 + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limit1Placed = true;
							return;*/
							
							trade.orderId = placeMktOrder(Constants.ACTION_SELL, POSITION_SIZE);
							trade.price = quotes[phl_cur.prePos].high;
							trade.entryPrice = trade.price;
							trade.status = Constants.STATUS_PLACED;
							trade.POSITION_SIZE = POSITION_SIZE;
							trade.remainingPositionSize = POSITION_SIZE;
							trade.entryTime = quotes[lastbar].time;

							if (trade.recordOpened == false)
							{
								CreateTradeRecord(trade.type, Constants.ACTION_SELL);
								trade.recordOpened = true;
							}
							AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, trade.price);

							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

							trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
							logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);
							return;

						}

						if (breakout_limit == true)
						{	
							for ( int i = phl_cur.prePos; i > start; i--)
							{
								PushHighLow phl_pre = Pattern.findLastNHigh( quotes, start, i, 3 );
								if (( phl_pre != null ))//&& (prremptive_limit == true))
								{
	
									// check 2: large break outs
									if (((((QuoteData) quotes[phl_pre.prePos]).high - phl_pre.pullBack.low)  > ENTRY_LARGE_PULLBACK * PIP_SIZE))
									{
										// if the pull back is larger than 20 pips, put one
										// limit positions
										if ((data.high - (((QuoteData) quotes[phl_cur.prePos]).low) > ENTRY_LARGE_BREAKOUT * PIP_SIZE) && (trade.limit2Placed == false))
										{
											logger.warning(symbol + " " + CHART + " detected for limit order " + data.time);
											trade.pullBackPos = phl_cur.pullBack.pos;
	
											trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + PIP_SIZE, Constants.ADJUST_TYPE_UP);
											trade.limitPos1 = trade.POSITION_SIZE;
											trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
	
											trade.limit1Stop = adjustPrice(trade.limitPrice1 + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
											trade.limit1Placed = true;
											return;
										}
									}
									
									break;
								}
							}
						}
					}
					else
					{
						trade.entryPullBackLow = phl_cur.pullBack.low;
						trade.entryPullBackTime = phl_cur.pullBack.time;
						break;
					}
				}
			}

			checkTriggerMarketSell(data.close);
			return;

		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{

			//if (ema100[lastbar]< ema200[lastbar])
			if ((((QuoteData) quotesL[lastbarL]).high < ema20L[lastbarL]) && (((QuoteData) quotesL[lastbarL - 1]).high < ema20L[lastbarL - 1]))
			{
				logger.warning(symbol + " " + CHART + " triggered below 20MA, remove trade");
				trade = null;
				return;
			}

			//int start = Utility.getHigh(quotes, lastbar - 36, lastbar - 3).pos;
			int detectpos = findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			int start = Utility.getHigh(quotes, detectpos-10, detectpos-1).pos;

			
			/*int detectpos = findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			int start = Utility.getHigh(quotes, detectpos - 5, detectpos).pos;
			if ( trade.entryPullBackHigh == 0 )
			{
				trade.entryPullBackHigh = quotes[start].high;
				trade.entryPullBackTime = quotes[start].time;
			}*/

			for (int s = lastbar; s > start; s--)
			{
				PushHighLow phl_cur = Pattern.findLastNLow(quotes, start, s, 2);
				if (phl_cur != null)
				{
					/*logger.warning(symbol + " " + CHART + "@@@this entry low between " +
							 ((QuoteData)quotes[phl_cur.prePos]).time + "@" + ((QuoteData)quotes[phl_cur.prePos]).high + "  -  " +
							 ((QuoteData)quotes[phl_cur.curPos]).time + "@" + ((QuoteData)quotes[phl_cur.curPos]).high + " pullback@" +
							 phl_cur.pullBack.time );
					*/
					if (previousTradeExist(phl_cur.pullBack.pos))
						return;

					if ( phl_cur.curPos == lastbar )
					{
						for ( int i = phl_cur.prePos; i > start; i--)
						{
							PushHighLow phl_pre = Pattern.findLastNLow( quotes, start, i, 3 );
							if (( phl_pre != null ))//&& (prremptive_limit == true))
							{
								if ((prremptive_limit == true)&&(((QuoteData) quotes[phl_pre.prePos]).low - ((QuoteData) quotes[phl_pre.curPos]).low > ENTRY_LARGE_BREAKOUT * PIP_SIZE))
								{
									logger.warning(symbol + " " + CHART + " detected for buy limit order for large pull backs" + data.time);
									trade.pullBackPos = phl_cur.pullBack.pos;

									// place order public int placeLmtOrder(String
									// action, double limitPrice, int posSize, String
									// ocaGroup)
									trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
									trade.limitPos1 = trade.POSITION_SIZE;
									trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);
									logger.warning(symbol + " " + CHART + " limit order place " + trade.limitPrice1 + " " + trade.limitPos1 + " " + data.time);

									trade.limit1Stop = adjustPrice(trade.limitPrice1 - 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
									trade.limit1Placed = true;
									return;

								}

								// check 2: large break outs
								if ((breakout_limit == true) &&(phl_pre.pullBack.high - ((QuoteData) quotes[phl_pre.prePos]).low > ENTRY_LARGE_PULLBACK * PIP_SIZE))
								{
									logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
									trade.pullBackPos = phl_cur.pullBack.pos;

									trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
									trade.limitPos1 = trade.POSITION_SIZE;
									trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);
									logger.warning(symbol + " " + CHART + " limit order place " + trade.limitPrice1 + " " + trade.limitPos1 + " " + data.time);

									trade.limit1Stop = adjustPrice(trade.limitPrice1 - 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
									trade.limit1Placed = true;
									return;
								}

								break;
							}
						}
					}
					else
					{
						trade.entryPullBackHigh = phl_cur.pullBack.high;
						trade.entryPullBackTime = phl_cur.pullBack.time;
						break;
					}
				}
			}

			checkTriggerMarketBuy(data.close);
			return;
		}
	}

	
	
	


	synchronized void checkTriggerMarketSell(double price)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if ( trade.limitEntryOrderPlaced == true )
			return;

		if (( trade.entryPullBackLow != 0 ) && ( price < trade.entryPullBackLow))
		//if ( price < trade.phl_cur.pullBack.low)
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

			int entryPullBackPos = 	findPositionByMinute(quotes, trade.entryPullBackTime, Constants.BACK_TO_FRONT );
			for (int j = entryPullBackPos + 1; j < lastbar; j++)
			{
				if ( quotes[j].low < trade.entryPullBackLow)
				{
					logger.warning(symbol + " " + CHART + quotes[lastbar].time + " " + quotes[j].time + " below pullback low of "
							+ trade.entryPullBackTime + " , trade missed");
					trade = null;
					return;
				}
			}

			double highest = Utility.getLow(quotes, lastbar - 24, lastbar).high;
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
				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				trade.price = (MODE == Constants.TEST_MODE)?trade.entryPullBackLow:price;
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

				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
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
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if ( trade.limitEntryOrderPlaced == true )
			return;

		if (( trade.entryPullBackHigh != 0 ) && ( price > trade.entryPullBackHigh))
		//if ( price > trade.phl_cur.pullBack.high)
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

			int entryPullBackPos = 	findPositionByMinute(quotes, trade.entryPullBackTime, Constants.BACK_TO_FRONT );
			for (int j = entryPullBackPos + 1; j < lastbar; j++)
			{
				if ( quotes[j].high > trade.entryPullBackHigh)
				{
					logger.warning(symbol + " " + CHART + " " + quotes[lastbar].time + " " + quotes[j].time + " high > pull back high of "
							+ trade.entryPullBackTime + " trade missed");
					trade = null;
					return;
				}
			}

			double lowest = Utility.getLow(quotes, lastbar - 24, lastbar).low;
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
				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				trade.price = (MODE == Constants.TEST_MODE)?trade.entryPullBackHigh:price;
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
			int detectPos = findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
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
			int detectPos = findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
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

	
	
	
	
	
	
	
	
	
	
	
	
	boolean previousTradeExist(int pullBackPos)
	{
		Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
		if (previousTrade.size() > 0)
		{
			Iterator it = previousTrade.iterator();
			while (it.hasNext())
			{
				Trade t = (Trade) it.next();
				if ((t.pullBackPos == pullBackPos) || ( t.action.equals(this.trade.action)))
				{
					//logger.warning(symbol + " " + CHART + " find previous trade, trade should not be entered");
					return true;
				}
			}
		}

		return false;

	}

	boolean previousTradeExist(int pullBackPos, String action)
	{
		Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
		if (previousTrade.size() > 0)
		{
			Iterator it = previousTrade.iterator();
			while (it.hasNext())
			{
				Trade t = (Trade) it.next();
				if ((t.pullBackPos == pullBackPos) || (t.action.equals(action)))
				{
					logger.warning(symbol + " " + CHART + " find previous trade, trade should not be entered");
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
	/*
	 * public void trackReversalTarget(QuoteData data, Vector<QuoteData> qtsM,
	 * Vector<QuoteData> qtsL) { logger.info(symbol + " track trade entry " +
	 * trade.detectTime); double[] ema20M = Indicator.calculateEMA(qtsM, 20);
	 * Object[] quotesM = qtsM.toArray(); int lastbarM = quotesM.length-1;
	 *
	 * double[] ema20L = Indicator.calculateEMA(qtsL, 20); double[] ema40L =
	 * Indicator.calculateEMA(qtsL, 40); Object[] quotesL = qtsL.toArray(); int
	 * lastbarL = quotesL.length-1;
	 *
	 * logger.info("trade detectionTime is " + trade.detectTime);
	 * logger.info("trade entryTime is " + trade.entryTime); int detectPos =
	 * Utility.getQuotePositionByMinute( quotesM, trade.detectTime); int
	 * entryPos = Utility.getQuotePositionByMinute( quotesM, trade.entryTime);
	 * logger.info("trade detection position is " + detectPos);
	 * logger.info("trade entryTime position is " + entryPos);
	 *
	 * double avgSizeM = Utility.getAverage(quotesM);
	 *
	 * // exit at extrem, reversal signal if
	 * (Constants.ACTION_SELL.equals(trade.action)) { //Vector<BreakOut>
	 * breakouts = Pattern.findMABreakoutUps(quotesM, ema20M, detectPos);
	 *
	 * ////////////////////////////////// // first part, take initial profit
	 * //////////////////////////////////
	 *
	 * logger.info(symbol + " track trade - check taking partial profit"); if((
	 * trade.initProfitTaken == false ) && (((QuoteData)quotesM[lastbarM]).high
	 * > ((QuoteData)quotesM[lastbarM-1]).high)) { logger.info(symbol +
	 * " track trade - check cup");
	 *
	 * // take profit if there is large diverage Cup cup =
	 * Pattern.downCup(symbol, quotesM, ema20M, entryPos, lastbarM, 0, 0,
	 * avgSizeM * 3, false);
	 *
	 * if (( cup != null ) && ( cup.pullBackWidth <=12 )) {
	 * System.out.println(symbol + " 3x down cup detected at " + data.time +
	 * " exit trade"); logger.info(symbol + " 3x down cup detected at " +
	 * data.time + " exit trade");
	 *
	 * takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L )); return; }
	 *
	 * logger.info(symbol + " track trade - check partical profit"); // take
	 * some profit if there is a windfall double maxBar = 0; int pos =
	 * lastbarM-1; while (((QuoteData)quotesM[pos-1]).high <
	 * ((QuoteData)quotesM[pos]).high ) { if (((QuoteData)quotesM[pos]).open -
	 * ((QuoteData)quotesM[pos]).close > maxBar) maxBar =
	 * ((QuoteData)quotesM[pos]).open - ((QuoteData)quotesM[pos]).close; pos--;
	 * }
	 *
	 * if (( maxBar > 3 * avgSizeM ) && ( trade.takeProfit.get(pos) == null )) {
	 * logger.info(symbol + " windfall profit detect at " + data.time );
	 * System.out.println(symbol + " windfall profit detect at " + data.time );
	 * takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L )); return; }
	 *
	 * logger.info(symbol + " track trade - check 50 pips profit"); // take some
	 * profit if it hits 50 pips if ( inProfit(data) > 50 * PIP_SIZE ) //&&
	 * (trade.takeProfit.containsValue(0) == false)) { System.out.println(symbol
	 * + " default profit detect at " + data.time );
	 * takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L )); return; } }
	 *
	 * /////////////////////////////////////////// // second part, see if we can
	 * move the stop ///////////////////////////////////////////
	 * logger.info(symbol + " track trade - check moving stops"); if((
	 * trade.initProfitTaken == true ) &&
	 * ((((QuoteData)quotesL[lastbarL-1]).high < ema20L[lastbarL-1]) &&
	 * (((QuoteData)quotesL[lastbarL-2]).high >= ema20L[lastbarL-2]))) { // find
	 * the last close below 20MA int begin = lastbarL-2; while
	 * (((QuoteData)quotesL[begin]).high < ema20L[begin]) begin--;
	 *
	 * double lastHigh = Utility.getHigh(quotesL, begin, lastbarL-1).high; if (
	 * trade.stop != lastHigh) { trade.stop = lastHigh; logger.info(symbol +
	 * " move stop to " + trade.stop);
	 *
	 * if ( trade.stopId != 0 ) cancelOrder(trade.stopId);
	 *
	 * trade.stopId = placeStopOrder( Constants.ACTION_BUY, adjustPrice(
	 * trade.stop, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize,
	 * null); } } } else if (Constants.ACTION_BUY.equals(trade.action)) {
	 * //logger.info(symbol + " to move stop "); // adjust inital stop and
	 * perhaps take initial profit /* // check if no at a potential reversal
	 * point Vector<Wave> waves = Pattern.upWave( symbol, quotesM, entryPos,
	 * 2*avgSizeM );
	 *
	 * Iterator it = waves.iterator();
	 *
	 * logger.info("Wave test: "); while ( it.hasNext()) { Wave wave =
	 * (Wave)it.next(); logger.info("push: " + wave.push + " pullback: " +
	 * wave.pullback); }
	 */

	// take profit if there is large diverage
	/*
	 * logger.info(symbol + " track trade - check taking partial profit"); if((
	 * trade.initProfitTaken == false ) && (((QuoteData)quotesM[lastbarM]).low <
	 * ((QuoteData)quotesM[lastbarM-1]).low)) { logger.info(symbol +
	 * " track trade - check cup"); Cup cup = Pattern.upCup(symbol, quotesM,
	 * ema20M, entryPos, lastbarM, 0, 0, avgSizeM * 3, false);
	 *
	 * if (( cup != null ) && ( cup.pullBackWidth <=12 )) {
	 * System.out.println(symbol + " 3x up cup detected at " + data.time +
	 * " exit trade"); logger.info(symbol + " 3x up cup detected at " +
	 * data.time + " exit trade"); takeProfit(calculateTrendStrength(quotesL,
	 * ema20L, ema40L )); return; }
	 *
	 * // take some profit if there is a windfall logger.info(symbol +
	 * " track trade - check partical profit"); double maxBar = 0; int pos =
	 * lastbarM-1; while (((QuoteData)quotesM[pos-1]).high <
	 * ((QuoteData)quotesM[pos]).high ) { if (((QuoteData)quotesM[pos]).open -
	 * ((QuoteData)quotesM[pos]).close > maxBar) maxBar =
	 * ((QuoteData)quotesM[pos]).open - ((QuoteData)quotesM[pos]).close; pos--;
	 * }
	 *
	 * if (( maxBar > 3 * avgSizeM ) && ( trade.takeProfit.get(pos) == null )) {
	 * /* logger.info(symbol + " windfall profit detect at " + data.time /*+
	 * " take profit on " + windFallProfitSize
	 *//*
		 * ); /* System.out.println(symbol + " windfall profit detect at " +
		 * data.time /*+ " take profit on " + windFallProfitSize
		 *//*
			 * ); /* takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L
			 * )); return; }
			 *
			 * // take some profit if it hits 50 pips logger.info(symbol +
			 * " track trade - check 50 pips profit"); if (( inProfit(data) > 50
			 * * PIP_SIZE ) && (trade.takeProfit.containsValue(0) == false)) {
			 * logger.info(symbol + " default profit detect at " + data.time );
			 * System.out.println(symbol + " default profit detect at " +
			 * data.time ); takeProfit(calculateTrendStrength(quotesL, ema20L,
			 * ema40L )); return; } }
			 *
			 * /////////////////////////////////////////// // second part, see
			 * if we can move the stop
			 * /////////////////////////////////////////// logger.info(symbol +
			 * " track trade - check moving stops"); if(( trade.initProfitTaken
			 * == true ) && ((((QuoteData)quotesL[lastbarL-1]).low >
			 * ema20L[lastbarL-1]) && (((QuoteData)quotesL[lastbarL-2]).low <=
			 * ema20L[lastbarL-2]))) { // find the last close above 20MA int
			 * begin = lastbarL-2; while (((QuoteData)quotesL[begin]).low <
			 * ema20L[begin]) begin--;
			 *
			 * double lastLow = Utility.getLow(quotesL, begin, lastbarL-1).low;
			 * if ( trade.stop != lastLow) { trade.stop = lastLow;
			 * logger.info(symbol + " move stop to " + trade.stop);
			 *
			 * if ( trade.stopId != 0 ) cancelOrder(trade.stopId);
			 *
			 * trade.stopId = placeStopOrder( Constants.ACTION_SELL,
			 * adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN),
			 * trade.remainingPositionSize, null); } } } }
			 */

	public void trackReversalTarget2(QuoteData data, Vector<QuoteData> qts)
	{
		logger.info(symbol + " track trade entry " + trade.detectTime);
		double[] ema20 = Indicator.calculateEMA(qts, 20);
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// Will be looking for reversal on the other side;

			// /////////////////////////////////////////
			// second part, see if we can move the stop
			// /////////////////////////////////////////
			logger.info(symbol + " track trade - check moving stops");
			if ((((QuoteData) quotes[lastbar - 1]).high < ema20[lastbar - 1]) && (((QuoteData) quotes[lastbar - 2]).high >= ema20[lastbar - 2]))
			{
				// find the last close below 20MA
				int begin = lastbar - 2;
				while (((QuoteData) quotes[begin]).high < ema20[begin])
					begin--;

				double lastHigh = Utility.getHigh(quotes, begin, lastbar - 1).high + 5 * PIP_SIZE;
				if (trade.stop != lastHigh)
				{
					trade.stop = lastHigh;
					logger.info(symbol + " move stop to " + trade.stop);

					if (trade.stopId != 0)
						cancelOrder(trade.stopId);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP),
							trade.remainingPositionSize, null);
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// /////////////////////////////////////////
			// second part, see if we can move the stop
			// /////////////////////////////////////////
			if ((((QuoteData) quotes[lastbar - 1]).low > ema20[lastbar - 1]) && (((QuoteData) quotes[lastbar - 2]).low <= ema20[lastbar - 2]))
			{
				// find the last close above 20MA
				int begin = lastbar - 2;
				while (((QuoteData) quotes[begin]).low < ema20[begin])
					begin--;

				double lastLow = Utility.getLow(quotes, begin, lastbar - 1).low - 5 * PIP_SIZE;
				if (trade.stop != lastLow)
				{
					trade.stop = lastLow;
					logger.info(symbol + " move stop to " + trade.stop);

					if (trade.stopId != 0)
						cancelOrder(trade.stopId);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN),
							trade.remainingPositionSize, null);
				}
			}
		}
	}

	public void trackTradeTarget(QuoteData data, Object[] quotesS, QuoteData[] quotesL)
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data);

		if (trade != null)
			// exit_higher_high_lower_low( data, quotesL, trade.entryPosL );

			//exit123_3_c
			//exit123_3_c_new(data, quotesL, trade.entryPosL);
			exit123_3_c_org(data, quotesL);

		// exit123_3_c( data );

		// exit123_3_c_1m_works_but_no_diffience( data, quotesS, quotesL);
		// exit123_org( data, quotesS, quotesL);
		// exit123_peak( data, quotesS, quotesL);
		 //exit123_pullback_20ma(data, quotesS, quotesL);
	}

	public void takeInitialProfit(QuoteData data, int ti, Vector<QuoteData> qts)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length - 1;
		if (trade.initProfitTaken == false)
		{
			// check first return > risk
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				if ((((QuoteData) quotes[lastbar]).high > ((QuoteData) quotes[lastbar - 1]).high)
						&& (((QuoteData) quotes[lastbar]).low > ((QuoteData) quotes[lastbar - 1]).low))
				{
					if ((trade.entryPrice - data.close) > (1.5 * trade.risk))
					{
						logger.info(symbol + " " + ti + " minutes take initial profit");
						System.out.println(symbol + " " + ti + " minutes take initial profit");
						closePositionByMarket((int) (0.25 * trade.POSITION_SIZE), data.close);
						AdjustStopTargetOrders((new Date()).toString());
						trade.initProfitTaken = true;
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				if ((((QuoteData) quotes[lastbar]).high < ((QuoteData) quotes[lastbar - 1]).high)
						&& (((QuoteData) quotes[lastbar]).low < ((QuoteData) quotes[lastbar - 1]).low))
				{
					if ((data.close - trade.entryPrice) > (1.5 * trade.risk))
					{
						logger.info(symbol + " " + ti + " minutes take initial profit");
						System.out.println(symbol + " " + ti + " minutes take initial profit");
						closePositionByMarket((int) (0.25 * trade.POSITION_SIZE), data.close);
						AdjustStopTargetOrders((new Date()).toString());
						trade.initProfitTaken = true;
					}
				}
			}

		}

	}


	public void exit123_3_c_org(QuoteData data, QuoteData[] quotesL)
	{
		int lastbarL = quotesL.length - 1;
		
		int tradeEntryPosL = findPositionByHour(quotesL, trade.entryTime, 2 );

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
				if (trade.entryPrice - data.low > BREAK_EVEN_POINT * PIP_SIZE) 
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
				QuoteData[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;
				double[] ema20 = Indicator.calculateEMA(quotes, 20);

				int tradeEntryPos = findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				PushHighLow[] phls = Pattern.findPast2Lows(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
					double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;

					
					if ((trade.profitTake1 == false) && ( pullback_cur > pullback_pre * 1.4 ) && ( pullback_cur > FIXED_STOP * 	PIP_SIZE))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " 1.4 pull back, time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " " + FIXED_STOP * 1.75 + " pips pullback, time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

				}

				/*
				if (( quotes[lastbar].high < ema20[lastbar]) && ( quotes[lastbar-1].high < ema20[lastbar-1]))
				{
					MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, tradeEntryPos, lastbar);
					double stop = adjustPrice(mat.high.high, Constants.ADJUST_TYPE_UP);
					
					if (stop != trade.stop)
					{
						logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );

						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
					}
				}*/


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

						// logger.warning(symbol + " " + CHART + " trail profit - move stop to " + trade.stop + " " + data.time );
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
						//trade.adjustStop++;

					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > BREAK_EVEN_POINT * PIP_SIZE)
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
				QuoteData[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;
				double[] ema20 = Indicator.calculateEMA(quotes, 20);

				int tradeEntryPos = findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				PushHighLow[] phls = Pattern.findPast2Highs(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
					double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;

					
					if ((trade.profitTake1 == false ) && ( pullback_cur > pullback_pre * 1.4 )&& ( pullback_cur > FIXED_STOP * 	PIP_SIZE))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " 1.4 pull back, time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " " + FIXED_STOP * 1.75 + " pips pullback, time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}
				}

				/*
				if (( quotes[lastbar].low > ema20[lastbar]) && ( quotes[lastbar-1].low > ema20[lastbar-1]))
				{
					MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, tradeEntryPos, lastbar);
					double stop = adjustPrice(mat.low.low, Constants.ADJUST_TYPE_DOWN);
					
					if (stop != trade.stop)
					{
						logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );

						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
					}
				}*/


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
	
	
	public void exit123_3_c_org_newwww_latest(QuoteData data, QuoteData[] quotesL)
	{
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int tradeEntryPosL = findPositionByHour(quotesL, trade.entryTime, Constants.BACK_TO_FRONT );
		int tradeEntryPos = findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			
			/*
			if (trade.stopAdjusted == false)
			{
				if (trade.entryPrice - data.low > FIXED_STOP * 1.5 * PIP_SIZE)
				{
					logger.info(symbol + " " + CHART + " move stop to break even " + " " + data.time );

					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else*/
			
			
			// check to move stop
			if (( quotes[lastbar].high < ema20[lastbar]) && ( quotes[lastbar-1].high < ema20[lastbar-1]))
			{
				MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, tradeEntryPos, lastbar);
				double stop = adjustPrice(mat.high.high, Constants.ADJUST_TYPE_UP);
				
				if (stop < trade.stop)
				{
					logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );

					cancelOrder(trade.stopId);
					trade.stop = stop;
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
				}
			}

			PushHighLow[] phls = Pattern.findPast2Lows(quotes, tradeEntryPos, lastbar);
			if ((phls != null) && (phls.length >=2))
			{
				PushHighLow phl_cur = phls[0];
				PushHighLow phl_pre = phls[1];

				double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
				double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;

				if ((trade.profitTake1 == false) && ( pullback_cur > pullback_pre * 1.4 ))
				{
					// exit when making a new high/low at exi
					logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

					trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
					trade.targetPos1 = trade.remainingPositionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
							+ data.time);

					trade.profitTake1 = true;
					return;
				}
				
				if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 ))
				{
					// exit when making a new high/low at exi
					logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

					trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
					trade.targetPos1 = trade.remainingPositionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
							+ data.time);

					trade.profitTake1 = true;
					return;
				}

			}


			{
				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					/*
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// exit when making a new high/low at exi
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
						}
						/*
						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((phl_cur.pullBack.high - ((QuoteData) quotes[phl_cur.prePos]).low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 15 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// check 2: large break outs
							if (((((QuoteData) quotes[phl_cur.prePos]).low - data.low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								if (previousTradeExist(phl_cur.pullBack.pos))
									return;

								logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
								trade.pullBackPos = phl_cur.pullBack.pos;

								// place order public int placeLmtOrder(String
								// action, double limitPrice, int posSize,
								// String ocaGroup)
								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;

							}
							// check 3: small exhausting breakouts
						}*/
				//	}
				}

				// this is to use the larger timeframe
				int top = Utility.getHigh(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNLow(quotesL, top, lastbarL, 1);
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
					if (stop < trade.stop)
					{
						logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );

						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
						//trade.adjustStop++;

					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// check to move stop
			if (( quotes[lastbar].low > ema20[lastbar]) && ( quotes[lastbar-1].low > ema20[lastbar-1]))
			{
				MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, tradeEntryPos, lastbar);
				double stop = adjustPrice(mat.low.low, Constants.ADJUST_TYPE_DOWN);
				
				if (stop > trade.stop)
				{
					logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );

					cancelOrder(trade.stopId);
					trade.stop = stop;
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
				}
			}

			PushHighLow[] phls = Pattern.findPast2Highs(quotes, tradeEntryPos, lastbar);
			if ((phls != null) && (phls.length >=2))
			{
				PushHighLow phl_cur = phls[0];
				PushHighLow phl_pre = phls[1];

				double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
				double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;

				if ((trade.profitTake1 == false ) && ( pullback_cur > pullback_pre * 1.4 ))
				{
					// exit when making a new high/low at exi
					logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

					trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
					trade.targetPos1 = trade.remainingPositionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
							+ data.time);

					trade.profitTake1 = true;
					return;
				}
				
				if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 ))
				{
					// exit when making a new high/low at exi
					logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

					trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
					trade.targetPos1 = trade.remainingPositionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
							+ data.time);

					trade.profitTake1 = true;
					return;
				}

			}

			/*
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > FIXED_STOP * 1.5 * PIP_SIZE)
				{
					logger.info(symbol + " " + CHART + " move stop to break even " + " " + data.time );
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else*/
			{
				/*


				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Highs(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
							logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);




							/*
							if ((((QuoteData) quotes[phl_cur.prePos]).high - phl_cur.pullBack.low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 15 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((data.high - (((QuoteData) quotes[phl_cur.prePos]).low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large breakouts" + data.time);

								trade.targetPrice1 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}*/
					//	}
					//}
				//}

				// normal exit stragety
				// this is to use the small time frame

				// this is to use the larger time frame
				int bottom = Utility.getLow(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNHigh(quotesL, bottom, lastbarL, 1);
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
					if (stop > trade.stop)
					{
						logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );
						cancelOrder(trade.stopId);

						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);

					}
					return;
				}
			}
		}
	}


	
	public void exit123_3_c_org_bak(QuoteData data, QuoteData[] quotesL)
	{
		int lastbarL = quotesL.length - 1;
		
		int tradeEntryPosL = findPositionByHour(quotesL, trade.entryTime, 2 );

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
				if (trade.entryPrice - data.low > BREAK_EVEN_POINT * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					// logger.warning(symbol + " " + CHART +
					// " trail profit - move stop to " + trade.stop + " " +
					// data.time );
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					/*
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// exit when making a new high/low at exi
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
						}
						/*
						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((phl_cur.pullBack.high - ((QuoteData) quotes[phl_cur.prePos]).low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 15 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// check 2: large break outs
							if (((((QuoteData) quotes[phl_cur.prePos]).low - data.low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								if (previousTradeExist(phl_cur.pullBack.pos))
									return;

								logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
								trade.pullBackPos = phl_cur.pullBack.pos;

								// place order public int placeLmtOrder(String
								// action, double limitPrice, int posSize,
								// String ocaGroup)
								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;

							}
							// check 3: small exhausting breakouts
						}*/
				//	}
				}

				// normal exit stragety
				// this is to use the small timeframe
				QuoteData[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;

				int tradeEntryPos = findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				PushHighLow[] phls = Pattern.findPast2Lows(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
					double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;

					if ((trade.profitTake1 == false) && ( pullback_cur > pullback_pre * 1.4 ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}
				}


				// this is to use the larger timeframe
				int top = Utility.getHigh(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				for (int i = lastbarL; i > top; i--)
				{
					PushHighLow phl = findLastNLow(quotesL, top, i, 1);
					if (phl != null)
					{
						if (i == lastbarL)
						{
							if ((trade.profitTake2 == false)
									&& (phl.pullBack.high - (((QuoteData) quotesL[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
								// large pull back, lock in profit
								trade.targetPrice2 = adjustPrice(data.close, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);// new
								trade.profitTake2 = true;
								return;
							}

							double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
							if (trade.stop != stop)
							{
								cancelOrder(trade.stopId);
								trade.stop = stop;

								// logger.warning(symbol + " " + CHART + " trail profit - move stop to " + trade.stop + " " + data.time );
								trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
								trade.adjustStop++;

							}
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > BREAK_EVEN_POINT * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				/*


				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Highs(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
							logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);




							/*
							if ((((QuoteData) quotes[phl_cur.prePos]).high - phl_cur.pullBack.low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 15 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((data.high - (((QuoteData) quotes[phl_cur.prePos]).low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large breakouts" + data.time);

								trade.targetPrice1 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}*/
					//	}
					//}
				//}

				// normal exit stragety
				// this is to use the small time frame
				QuoteData[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;

				int tradeEntryPos = findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				PushHighLow[] phls = Pattern.findPast2Highs(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
					double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;

					if ((trade.profitTake1 == false ) && ( pullback_cur > pullback_pre * 1.4 ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}
				}


				// this is to use the larger time frame
				int bottom = Utility.getLow(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				for (int i = lastbarL; i > bottom; i--)
				{
					PushHighLow phl = findLastNHigh(quotesL, bottom, i, 1);
					if (phl != null)
					{
						if (i == lastbarL)
						{
							if ((trade.profitTake2 == false)
									&& ((((QuoteData) quotesL[phl.prePos]).high - phl.pullBack.low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
								trade.targetPrice2 = adjustPrice(data.close, Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.remainingPositionSize;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);// new
																																			// Long(oca).toString());
								trade.profitTake2 = true;
								return;
							}

							double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
							if (trade.stop != stop)
							{
								cancelOrder(trade.stopId);
								trade.stop = stop;

								trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
								trade.adjustStop++;

							}
							return;
						}
					}
				}
			}
		}
	}


	public void exit123_peak(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		if (trade.entryPos + 2 > lastbar)
			return;

		if (Constants.ACTION_SELL.equals(trade.action))
		{

			// exit 1: close when breaking higher high/lower low
			if ((trade.price - data.close) < 15 * PIP_SIZE)
				return;

			Object[] peaks = Pattern.findPastLowPeaksBelowMA(quotes, ema20, trade.entryPos, lastbar);

			if ((peaks == null) || (peaks.length < 2))
				return;

			int lastPeakPos = peaks.length - 1;
			if (((Peak) peaks[lastPeakPos]).pullbackStartPos == 0)
			{
				QuoteData lastPullbackHigh = Utility.getHigh(quotes, ((Peak) peaks[lastPeakPos - 1]).pullbackStartPos,
						((Peak) peaks[lastPeakPos - 1]).pullbackEndPos);

				if ((lastPullbackHigh.high - data.close) >= 60 * PIP_SIZE)
				{
					logger.warning(symbol + " " + CHART + " profit exceeds 60 pips, close trade " + data.time + " @ " + lastPullbackHigh);
					AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
					closePositionByMarket(trade.remainingPositionSize, data.close);
					// removeTrade();
				}
				return;
			}

			// logger.warning(symbol + " " + CHART + " trade target peak = " +
			// peaks.length );

			for (int i = lastPeakPos; i >= 0; i--)
			{
				logger.warning(symbol + " " + CHART + " peak " + i + " lows between " + ((QuoteData) quotes[((Peak) peaks[i]).highlowStartPos]).time
						+ "-" + ((QuoteData) quotes[((Peak) peaks[i]).highlowEndPos]).time);
				logger.warning(symbol + " " + CHART + " peak " + i + " pullbacks btw "
						+ ((QuoteData) quotes[((Peak) peaks[i]).pullbackStartPos]).time + "-"
						+ ((QuoteData) quotes[((Peak) peaks[i]).pullbackEndPos]).time);
			}

			QuoteData lastPullbackHigh = Utility.getHigh(quotes, ((Peak) peaks[lastPeakPos - 1]).pullbackStartPos,
					((Peak) peaks[lastPeakPos - 1]).pullbackEndPos);
			QuoteData thisPullbackHigh = Utility.getHigh(quotes, ((Peak) peaks[lastPeakPos]).pullbackStartPos,
					((Peak) peaks[lastPeakPos]).pullbackEndPos);

			if ((thisPullbackHigh.high > lastPullbackHigh.high) && (thisPullbackHigh.pos == lastbar))
			{
				logger.warning(symbol + " " + CHART + " break last high, close trade " + data.time + " @ " + lastPullbackHigh);
				AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
				closePositionByMarket(trade.remainingPositionSize, data.close);
				// removeTrade();
				return;
			}

			// exit 2: exit partial profit when it hit three times in one peak;

			// move stop
			/*
			 * double stop = adjustPrice( mat.high.high,
			 * Constants.ADJUST_TYPE_UP); if ( trade.stop != stop ) {
			 * cancelOrder( trade.stopId); trade.stop = stop;
			 *
			 * logger.warning(symbol + " " + CHART + " last 20MA touch is " +
			 * mat.high.time ); logger.warning(symbol + " " + CHART +
			 * " adjusted stop is " + stop); trade.stopId =
			 * placeStopOrder(Constants.ACTION_BUY, stop,
			 * trade.remainingPositionSize, null); trade.adjustStop++;
			 *
			 * } }
			 */
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ((data.close - trade.price) < 15 * PIP_SIZE)
				return;

			Object[] peaks = Pattern.findPastHighPeaksAboveMA(quotes, ema20, trade.entryPos, lastbar);

			if ((peaks == null) || (peaks.length < 2))
				return;

			int lastPeakPos = peaks.length - 1;
			if (((Peak) peaks[lastPeakPos]).pullbackStartPos == 0)
			{
				QuoteData lastPullbackLow = Utility.getLow(quotes, ((Peak) peaks[lastPeakPos - 1]).pullbackStartPos,
						((Peak) peaks[lastPeakPos - 1]).pullbackEndPos);

				if ((data.close - lastPullbackLow.low) >= 60 * PIP_SIZE)
				{
					logger.warning(symbol + " " + CHART + " profit exceeds 60 pips " + data.time + " @ " + lastPullbackLow);
					AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
					closePositionByMarket(trade.remainingPositionSize, data.close);
					// removeTrade();
				}
				return;
			}

			// logger.warning(symbol + " " + CHART + " trade target peak = " +
			// peaks.length );
			/*
			 * for ( int i = lastPeakPos; i >=0; i--) { logger.warning(symbol +
			 * " " + CHART + " peak " + i + " lows between " +
			 * ((QuoteData)quotes[((Peak)peaks[i]).highlowStartPos]).time + "-"
			 * + ((QuoteData)quotes[((Peak)peaks[i]).highlowEndPos]).time );
			 * logger.warning(symbol + " " + CHART + " peak " + i +
			 * " pullbacks btw " +
			 * ((QuoteData)quotes[((Peak)peaks[i]).pullbackStartPos]).time + "-"
			 * + ((QuoteData)quotes[((Peak)peaks[i]).pullbackEndPos]).time ); }
			 */

			QuoteData lastPullbackLow = Utility.getLow(quotes, ((Peak) peaks[lastPeakPos - 1]).pullbackStartPos,
					((Peak) peaks[lastPeakPos - 1]).pullbackEndPos);
			QuoteData thisPullbackLow = Utility.getLow(quotes, ((Peak) peaks[lastPeakPos]).pullbackStartPos,
					((Peak) peaks[lastPeakPos]).pullbackEndPos);

			if ((thisPullbackLow.low < lastPullbackLow.low) && (thisPullbackLow.pos == lastbar))
			{
				logger.warning(symbol + " " + CHART + " break last low, close trade " + data.time + " @ " + lastPullbackLow);
				AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
				closePositionByMarket(trade.remainingPositionSize, data.close);
				// removeTrade();
				return;
			}

			/*
			 * double stop = adjustPrice( mat.low.low,
			 * Constants.ADJUST_TYPE_DOWN); // move stop if ( trade.stop != stop
			 * ) { cancelOrder( trade.stopId ); trade.stop = stop;
			 *
			 * logger.warning(symbol + " " + CHART + " last 20MA touch is " +
			 * mat.low.time ); logger.warning(symbol + " " + CHART +
			 * " adjusted stop is " + stop); trade.stopId =
			 * placeStopOrder(Constants.ACTION_SELL, stop,
			 * trade.remainingPositionSize, null); trade.adjustStop++; }
			 *
			 * return;
			 */
		}
	}

	public void exit123_pullback_20ma(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		// int lastbarS = quotesS.length -1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			for (int i = lastbar; i > trade.entryPos; i--)
			{
				MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, trade.entryPos, i);

				if ((mat != null) && (mat.high != null))
				{
					// logger.warning(symbol + " " + CHART +
					// " last 20MAAAAAAAA touch is " + mat.high.time );
					if (data.high > mat.high.high)
					{
						// logger.warning(symbol + " " + CHART +
						// " last 20MA touch is " + mat.high.time );
						// logger.warning(symbol + " " + CHART +
						// " break 20MA, close trade @" + data.time );
						AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
						closePositionByMarket(trade.remainingPositionSize, data.close);
						// removeTrade();
						return;
					}

					double stop = adjustPrice(mat.high.high, Constants.ADJUST_TYPE_UP);
					if (trade.stop != stop)
					{
						// do not move stop until them move out of the slim jim
						// if ( trade.stop > this.slimJimLow)
						// return;

						cancelOrder(trade.stopId);
						trade.stop = stop;

						// logger.warning(symbol + " " + CHART +
						// " last 20MA touch is " + mat.high.time );
						// logger.warning(symbol + " " + CHART +
						// " trail profit - move stop to " + trade.stop + " " +
						// data.time );
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
						trade.adjustStop++;

					}
					return;
				}
			}
			/*
			 * double profitInStake = Math.abs(data.close - mat.highest );
			 *
			 * if ( profitInStake > 40 * PIP_SIZE ) { logger.warning(symbol +
			 * " " + CHART + " take profit, the pull back > " + LOCKIN_PROFIT +
			 * " pips"); AddTradeCloseRecord( data.time, Constants.ACTION_BUY,
			 * trade.remainingPositionSize, data.close); closePositionByMarket(
			 * trade.remainingPositionSize, data.close);
			 *
			 * }
			 */
			/*
			 *
			 * for ( int i = lastbar; i > top; i--) { PushHighLow phl =
			 * findLastNLow( quotes, top, i, 1 ); if ( phl != null ) { if ( i ==
			 * lastbar ) { // take partical profit if the pull back is great
			 * than 25pips if (phl.pullBack.high -
			 * (((QuoteData)quotes[phl.prePos]).low ) > LOCKIN_PROFIT * PIP_SIZE
			 * ) { logger.warning(symbol + " " + CHART +
			 * " take profit, last pull back is " +
			 * ((QuoteData)quotes[phl.prePos]).low + " @ " +
			 * ((QuoteData)quotes[phl.prePos]).time ); logger.warning(symbol +
			 * " " + CHART + " take profit, the pull back > " + LOCKIN_PROFIT +
			 * " pips"); AddTradeCloseRecord( data.time, Constants.ACTION_BUY,
			 * trade.remainingPositionSize, data.close); closePositionByMarket(
			 * trade.remainingPositionSize, data.close); //removeTrade();
			 * return; } }
			 *
			 * return; } }
			 */
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.entryPos + 2 > lastbar)
				return;

			for (int i = lastbar; i > trade.entryPos; i--)
			{
				MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, trade.entryPos, i);

				if ((mat != null) && (mat.low != null))
				{
					if (data.low < mat.low.low)
					{
						// logger.warning(symbol + " " + CHART +
						// " last 20MA touch is " + mat.low.time );
						// logger.warning(symbol + " " + CHART +
						// " break 20MA, close trade @" + data.time );
						AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePositionByMarket(trade.remainingPositionSize, data.close);
						// removeTrade();
						return;
					}

					double stop = adjustPrice(mat.low.low, Constants.ADJUST_TYPE_DOWN);
					// move stop
					if (trade.stop != stop)
					{
						// do not move stop until them move out of the slim jim
						// if ( trade.stop < this.slimJimHigh)
						// return;

						cancelOrder(trade.stopId);
						trade.stop = stop;

						// logger.warning(symbol + " " + CHART +
						// " last 20MA touch between " +
						// ((QuoteData)quotes[mat.touchBegin]).time + "-" +
						// ((QuoteData)quotes[mat.touchEnd]).time );
						logger.warning(symbol + " " + CHART + " trail profit - move stop to " + trade.stop + " " + data.time);
						// logger.warning(symbol + " " + CHART +
						// " adjusted stop is " + stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						trade.adjustStop++;
					}

					return;
				}

			}

			/*
			 * double profitInStake = 0; if ( mat.lowest !=
			 * MATouch.DEFAULT_LOWEST ) Math.abs(data.close - mat.lowest );
			 *
			 * if ( profitInStake > 40 * PIP_SIZE ) { logger.warning(symbol +
			 * " " + CHART + " take profit, the pull back > " + LOCKIN_PROFIT +
			 * " pips"); AddTradeCloseRecord( data.time, Constants.ACTION_SELL,
			 * trade.remainingPositionSize, data.close); closePositionByMarket(
			 * trade.remainingPositionSize, data.close);
			 *
			 * }
			 */

			/*
			 * for ( int i = lastbar; i > bottom; i--) { PushHighLow phl =
			 * findLastNHigh( quotes, bottom, i, 1 ); if ( phl != null ) {
			 * //logger.warning(symbol + " " + CHART + "this high between " +
			 * ((QuoteData)quotes[phl.prePos]).time + "@" +
			 * ((QuoteData)quotes[phl.prePos]).high + "  -  " +
			 * ((QuoteData)quotes[phl.curPos]).time + "@" +
			 * ((QuoteData)quotes[phl.curPos]).high + " pullback@" +
			 * phl.pullBack );
			 *
			 * if ( i == lastbar ) { if ((((QuoteData)quotes[phl.prePos]).high -
			 * phl.pullBack.low ) > LOCKIN_PROFIT * PIP_SIZE ) {
			 * logger.warning(symbol + " " + CHART +
			 * " take profit, last pull back is " +
			 * ((QuoteData)quotes[phl.prePos]).high + " @ " +
			 * ((QuoteData)quotes[phl.prePos]).time ); logger.warning(symbol +
			 * " " + CHART + " take profit, the pull back > " + LOCKIN_PROFIT +
			 * " pips"); AddTradeCloseRecord( data.time, Constants.ACTION_SELL,
			 * trade.remainingPositionSize, data.close); closePositionByMarket(
			 * trade.remainingPositionSize, data.close); //removeTrade();
			 * return; } } return; } }
			 */
		}
	}

	public void exit123_3_c_1m_works_but_no_diffience(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarS = quotesS.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.profitTake2Triggered != true)
			{
				if (trade.entryPos + 2 > lastbar)
					return;

				int top;

				if (Constants.TRADE_RST.equals(trade.type))
					top = Utility.getHigh(quotes, trade.entryPos, lastbar).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					top = trade.lowHighAfterEntry.pos;
				else if (Constants.TRADE_RSC.equals(trade.type))
					top = trade.profitStartingPointing;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}

				PushHighLow phl = findLastNLow(quotes, top, lastbar, 1);
				if (phl != null)
				{
					if (phl.prePos > trade.entryPos)
					{
						// move stop
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_UP);
						if (trade.stop != stop)
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;

							logger.warning(symbol + " " + CHART + " adjusted stop is " + stop);
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							trade.adjustStop++;

						}
					}

					// take partical profit if the pull back is great than
					// 25pips
					if ((trade.profitTake2 == false) && (phl.pullBack.high - (((QuoteData) quotes[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
					{
						logger.warning(symbol + " " + CHART + " take profit triggered at " + data.time);
						trade.profitTake2Triggered = true;
						trade.profitTake2 = true;
					}
				}
			}

			if (trade.profitTake2Triggered == true)
			{
				if ((((QuoteData) quotesS[lastbarS]).high > ((QuoteData) quotesS[lastbarS - 1]).high))
				{
					logger.warning(symbol + " " + CHART + " take profit executed at " + data.time);
					if (MODE == Constants.TEST_MODE)
						AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);

					closePositionByMarket(trade.remainingPositionSize, data.close);
					// trade.profitTake2 = true;
					// trade.profitTake2Triggered = false;

					// large pull back, lock in profit
					/*
					 * trade.targetPrice = data.close; // TODO: this could be
					 * better trade.targetPrice = adjustPrice( data.close,
					 * Constants.ADJUST_TYPE_DOWN);
					 *
					 * trade.targetPositionSize = trade.remainingPositionSize;
					 *
					 * trade.targetId = placeLmtOrder(Constants.ACTION_BUY,
					 * trade.targetPrice, trade.targetPositionSize, null);//new
					 * Long(oca).toString()); trade.profitTake2 = true;
					 * trade.profitTake2Triggered = false;
					 */
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.profitTake2Triggered != true)
			{
				if (trade.entryPos + 2 > lastbar)
					return;

				int bottom;
				if (Constants.TRADE_RST.equals(trade.type))
					bottom = Utility.getLow(quotes, trade.entryPos, lastbar).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					bottom = trade.lowHighAfterEntry.pos;
				else if (Constants.TRADE_RSC.equals(trade.type))
					bottom = trade.profitStartingPointing;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}

				PushHighLow phl = findLastNHigh(quotes, bottom, lastbar, 1);
				if (phl != null)
				{
					if (phl.prePos > trade.entryPos)
					{
						double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_DOWN);
						// move stop
						if (trade.stop != stop)
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;

							logger.warning(symbol + " " + CHART + " adjusted stop is " + stop);
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
							trade.adjustStop++;
						}
					}

					if ((trade.profitTake2 == false) && ((((QuoteData) quotes[phl.prePos]).high - phl.pullBack.low) > 25 * PIP_SIZE))
					{
						logger.warning(symbol + " " + CHART + " take profit triggered at " + data.time);
						trade.profitTake2Triggered = true;
						trade.profitTake2 = true;
					}
				}
			}

			if (trade.profitTake2Triggered == true)
			{
				if ((((QuoteData) quotesS[lastbarS]).low < ((QuoteData) quotesS[lastbarS - 1]).low))
				{
					logger.warning(symbol + " " + CHART + " take profit executed at " + data.time);
					if (MODE == Constants.TEST_MODE)
						AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);

					closePositionByMarket(trade.remainingPositionSize, data.close);
					// trade.profitTake2 = true;
					// trade.profitTake2Triggered = false;

					/*
					 * trade.targetPrice = data.close; trade.targetPrice =
					 * adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
					 *
					 * trade.targetPositionSize = trade.remainingPositionSize;
					 *
					 * trade.targetId = placeLmtOrder(Constants.ACTION_SELL,
					 * trade.targetPrice, trade.targetPositionSize, null);//new
					 * Long(oca).toString()); trade.profitTake2 = true;
					 * trade.profitTake2Triggered = false;
					 */
					return;
				}
			}
		}
	}

	public void exit123_3_c_1m_works_but_no_diffience_bak(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarS = quotesS.length - 1;

		/*
		 * // if quick profit target set, cancel target if it has not bee a
		 * quick profit if (Constants.TRADE_CNT.equals(trade.type) && ( lastbar
		 * - trade.entryPos > 8 ) && ( trade.targetReached == false)) {
		 * trade.targetPrice = 0; trade.targetPositionSize = 0;
		 *
		 * if ( MODE == Constants.REAL_MODE) cancelOrder( trade.targetId); }
		 */

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.profitTake2Triggered != true)
			{
				if (trade.entryPos + 2 > lastbar)
					return;

				int top;

				if (Constants.TRADE_RST.equals(trade.type))
					top = Utility.getHigh(quotes, trade.entryPos, lastbar).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					top = trade.lowHighAfterEntry.pos;
				else if (Constants.TRADE_RSC.equals(trade.type))
					top = trade.profitStartingPointing;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}

				for (int i = lastbar; i > top; i--)
				{
					PushHighLow phl = findLastNLow(quotes, top, i, 1);
					if (phl != null)
					{
						if (i == lastbar)
						{
							// take partical profit if the pull back is great
							// than 25pips
							if ((trade.profitTake2 == false)
									&& (phl.pullBack.high - (((QuoteData) quotes[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								trade.profitTake2Triggered = true;
								trade.profitTake2 = true;
								break;
							}

							/*
							 * this limits the profit, might not want this for (
							 * int j = i-1; j > trade.profitTakeStartPos; j--) {
							 * PushHighLow phl_pre = findLastNLow( quotes,
							 * trade.profitTakeStartPos, j, 2 ); if ( phl_pre !=
							 * null ) { double low =
							 * ((QuoteData)quotes[phl_pre.prePos]).low;
							 *
							 * QuoteData afterLow = Utility.getLow( quotes,
							 * phl_pre.curPos, i-1); if ( low - afterLow.low <
							 * 2.5 * PIP_SIZE ) { trade.profitTake2Triggered =
							 * true; break; } }
							 *
							 * }
							 */

						}

						/*
						 * // take profit if it is > 30 pips from the last pull
						 * back // make less money this way if (
						 * trade.takePartialProfit == false ) { if (
						 * phl.pullBack.high - data.close > 30 * PIP_SIZE ) {
						 * logger.warning(symbol + " " + CHART +
						 * " take profit, last pull back is " +
						 * ((QuoteData)quotes[phl.prePos]).low + " @ " +
						 * ((QuoteData)quotes[phl.prePos]).time );
						 * logger.warning(symbol + " " + CHART +
						 * " take partical profit to lock in 30 pips");
						 *
						 * AddTradeCloseRecord( data.time, Constants.ACTION_BUY,
						 * trade.remainingPositionSize/2, data.close);
						 * closePositionByMarket( trade.remainingPositionSize/2,
						 * data.close); trade.takePartialProfit = true; } }
						 */

						// take profit if it breaks higher-high/lower-low
						if (data.high > phl.pullBack.high)
						{
							int lastTradeEntryPos = trade.entryPos;
							double lastTradEentryPrice = trade.entryPrice;
							String lastTradeType = trade.type;
							logger.warning(symbol + " " + CHART + " minute target hit for breaking lower low " + data.time);

							AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
							closePositionByMarket(trade.remainingPositionSize, data.close);

							// check to see if it worth reversal
							if (lastTradeType.equals(Constants.TRADE_RST) && (reverse_after_profit == true))
							{
								int lastbarL = quotesL.length - 1;
								double[] ema20L = Indicator.calculateEMA(quotesL, 20);
								double[] ema50L = Indicator.calculateEMA(quotesL, 50);

								if (ema20L[lastbarL] > ema50L[lastbarL])
								{
									QuoteData lowAfterEntry = Utility.getLow(quotes, lastTradeEntryPos, lastbar);
									if (lastTradEentryPrice - lowAfterEntry.low < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
									{
										trade = new Trade(symbol);
										trade.type = Constants.TRADE_CNT;
										trade.action = Constants.ACTION_BUY;
										trade.POSITION_SIZE = POSITION_SIZE;
										trade.lowHighAfterEntry = lowAfterEntry;

										logger.warning(symbol + " " + CHART + " place market reverse buy order");
										trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
										trade.price = data.close;// phl.pullBack.high;
										trade.entryPrice = trade.price;
										trade.status = Constants.STATUS_PLACED;
										trade.remainingPositionSize = trade.POSITION_SIZE;
										trade.entryTime = data.time;// ((QuoteData)quotes[lastbar]).time;
										trade.entryPos = lastbar;
										if (MODE == Constants.TEST_MODE)
											AddTradeOpenRecord(trade.type, trade.entryTime, Constants.ACTION_BUY, trade.POSITION_SIZE,
													trade.entryPrice);

										trade.stop = lowAfterEntry.low;
										if ((trade.entryPrice - trade.stop) > FIXED_STOP * PIP_SIZE)
											trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;

										trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);

										logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
										trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																																	// Long(oca).toString());
									}
								}
							}
							return;
						}
					}
				}
			}

			if (trade.profitTake2Triggered == true)
			{
				if ((((QuoteData) quotesS[lastbarS]).high > ((QuoteData) quotesS[lastbarS - 1]).high))
				{
					if (MODE == Constants.TEST_MODE)
						AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);

					closePositionByMarket(trade.remainingPositionSize, data.close);
					// trade.profitTake2 = true;
					// trade.profitTake2Triggered = false;

					// large pull back, lock in profit
					/*
					 * trade.targetPrice = data.close; // TODO: this could be
					 * better trade.targetPrice = adjustPrice( data.close,
					 * Constants.ADJUST_TYPE_DOWN);
					 *
					 * trade.targetPositionSize = trade.remainingPositionSize;
					 *
					 * trade.targetId = placeLmtOrder(Constants.ACTION_BUY,
					 * trade.targetPrice, trade.targetPositionSize, null);//new
					 * Long(oca).toString()); trade.profitTake2 = true;
					 * trade.profitTake2Triggered = false;
					 */
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.profitTake2Triggered != true)
			{
				if (trade.entryPos + 2 > lastbar)
					return;

				int bottom;
				if (Constants.TRADE_RST.equals(trade.type))
					bottom = Utility.getLow(quotes, trade.entryPos, lastbar).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					bottom = trade.lowHighAfterEntry.pos;
				else if (Constants.TRADE_RSC.equals(trade.type))
					bottom = trade.profitStartingPointing;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}

				for (int i = lastbar; i > bottom; i--)
				{
					PushHighLow phl = findLastNHigh(quotes, bottom, i, 1);
					if (phl != null)
					{
						// logger.warning(symbol + " " + CHART +
						// "this high between " +
						// ((QuoteData)quotes[phl.prePos]).time + "@" +
						// ((QuoteData)quotes[phl.prePos]).high + "  -  " +
						// ((QuoteData)quotes[phl.curPos]).time + "@" +
						// ((QuoteData)quotes[phl.curPos]).high + " pullback@" +
						// phl.pullBack );

						if (i == lastbar)
						{
							if ((trade.profitTake2 == false) && ((((QuoteData) quotes[phl.prePos]).high - phl.pullBack.low) > 25 * PIP_SIZE))
							{
								trade.profitTake2Triggered = true;
								trade.profitTake2 = true;
								break;
							}

							/*
							 * take quick profit, this limits the profit for (
							 * int j = i-1; j > bottom; j--) { PushHighLow
							 * phl_pre = findLastNHigh( quotes, bottom, j, 2 );
							 * if ( phl_pre != null ) { double high =
							 * ((QuoteData)quotes[phl_pre.prePos]).high;
							 *
							 * QuoteData afterHigh = Utility.getHigh( quotes,
							 * phl_pre.curPos, i-1); if ( afterHigh.high - high
							 * < 2.5 * PIP_SIZE ) { logger.warning(symbol + " "
							 * + CHART + " minute last high was " +
							 * ((QuoteData)quotes[phl_pre.prePos]).time +
							 * " after high was " +
							 * ((QuoteData)quotes[afterHigh.pos]).time +
							 * " difference is less than 2.5 pips, take profit"
							 * ); trade.profitTake2Triggered = true; break; } }
							 *
							 * }
							 */
						}

						/*
						 * take profit if the push is > 30 pips, does not add
						 * profit if ( trade.takePartialProfit == false ) { if (
						 * data.close - phl.pullBack.low > 30 * PIP_SIZE ) {
						 * logger.warning(symbol + " " + CHART +
						 * " take profit, last pull back is " +
						 * ((QuoteData)quotes[phl.prePos]).high + " @ " +
						 * ((QuoteData)quotes[phl.prePos]).time );
						 * logger.warning(symbol + " " + CHART +
						 * " take partical profit to lock in 30 pips");
						 *
						 * AddTradeCloseRecord( data.time,
						 * Constants.ACTION_SELL, trade.remainingPositionSize/2,
						 * data.close); closePositionByMarket(
						 * trade.remainingPositionSize/2, data.close);
						 * trade.takePartialProfit = true; } }
						 */

						if (data.low < phl.pullBack.low)
						{
							int lastTradeEntryPos = trade.entryPos;
							double lastTradEentryPrice = trade.entryPrice;
							String lastTradeType = trade.type;
							// double lastPullbackHigh =
							// ((QuoteData)quotes[trade.pullBackPos]).high;
							logger.warning(symbol + " " + CHART + " minute target hit for breaking higher high at " + data.time);
							if (MODE == Constants.TEST_MODE)
								AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);

							closePositionByMarket(trade.remainingPositionSize, data.close);

							/*
							 * else if ( MODE == Constants.REAL_MODE) {
							 * trade.targetPrice = phl.pullBack.low;
							 * trade.targetPrice = adjustPrice( data.close,
							 * Constants.ADJUST_TYPE_UP);
							 * trade.targetPositionSize =
							 * trade.remainingPositionSize;
							 *
							 * trade.targetId =
							 * placeLmtOrder(Constants.ACTION_SELL,
							 * trade.targetPrice, trade.targetPositionSize,
							 * null);//new Long(oca).toString());
							 */

							// check to see if it worth reversal
							if (lastTradeType.equals(Constants.TRADE_RST) && (reverse_after_profit == true))
							{
								// enter a reverse trade only if following the
								// biger trend
								int lastbarL = quotesL.length - 1;
								double[] ema20L = Indicator.calculateEMA(quotesL, 20);
								double[] ema50L = Indicator.calculateEMA(quotesL, 50);

								if (ema20L[lastbarL] < ema50L[lastbarL])
								{
									QuoteData highAfterEntry = Utility.getHigh(quotes, lastTradeEntryPos, lastbar);
									if (highAfterEntry.high - lastTradEentryPrice < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
									{
										trade = new Trade(symbol);
										trade.type = Constants.TRADE_CNT;
										trade.action = Constants.ACTION_SELL;
										trade.POSITION_SIZE = POSITION_SIZE;
										trade.lowHighAfterEntry = highAfterEntry;

										logger.warning(symbol + " " + CHART + " place market reverse sell order");
										trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
										trade.price = data.close;// phl.pullBack.low;
										trade.entryPrice = trade.price;
										trade.status = Constants.STATUS_PLACED;
										trade.remainingPositionSize = trade.POSITION_SIZE;
										trade.entryTime = data.time;// ((QuoteData)quotes[lastbar]).time;
										trade.entryPos = lastbar;
										if (MODE == Constants.TEST_MODE)
											AddTradeOpenRecord(trade.type, trade.entryTime, Constants.ACTION_SELL, trade.POSITION_SIZE,
													trade.entryPrice);

										trade.stop = highAfterEntry.high;
										if ((trade.stop - trade.entryPrice) > FIXED_STOP * PIP_SIZE)
											trade.stop = trade.entryPrice + FIXED_STOP * PIP_SIZE;

										trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);

										logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
										trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																																	// Long(oca).toString());
									}
								}
							}
							return;
						}
					}
				}
			}

			if (trade.profitTake2Triggered == true)
			{
				if ((((QuoteData) quotesS[lastbarS]).low < ((QuoteData) quotesS[lastbarS - 1]).low))
				{
					if (MODE == Constants.TEST_MODE)
						AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);

					closePositionByMarket(trade.remainingPositionSize, data.close);
					// trade.profitTake2 = true;
					// trade.profitTake2Triggered = false;

					/*
					 * trade.targetPrice = data.close; trade.targetPrice =
					 * adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
					 *
					 * trade.targetPositionSize = trade.remainingPositionSize;
					 *
					 * trade.targetId = placeLmtOrder(Constants.ACTION_SELL,
					 * trade.targetPrice, trade.targetPositionSize, null);//new
					 * Long(oca).toString()); trade.profitTake2 = true;
					 * trade.profitTake2Triggered = false;
					 */
					return;
				}
			}
		}
	}

	public void exit123_3_c(QuoteData data)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.entryPos + 2 > lastbar)
				return;

			int top;

			if (Constants.TRADE_CNT.equals(trade.type))
				top = trade.lowHighAfterEntry.pos;
			else
				top = Utility.getHigh(quotes, trade.entryPos, lastbar).pos;

			for (int i = lastbar; i > top; i--)
			{
				PushHighLow phl = findLastNLow(quotes, top, i, 1);
				if (phl != null)
				{
					if (i == lastbar)
					{
						if ((trade.profitTake2 == false) && (phl.pullBack.high - (((QuoteData) quotes[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
						{
							logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
							// large pull back, lock in profit
							trade.targetPrice = data.close; // TODO: this could
															// be better
							trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_DOWN);

							trade.targetPositionSize = trade.remainingPositionSize;

							trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);// new
																																	// Long(oca).toString());
							trade.profitTake2 = true;
							return;
						}

					}

					// logger.warning(symbol + " exit low detected at  " +
					// ((QuoteData)quotes[phl.prePos]).time + "@" +
					// ((QuoteData)quotes[phl.prePos]).low + "  -  " +
					// ((QuoteData)quotes[phl.curPos]).time + "@" +
					// ((QuoteData)quotes[phl.curPos]).low + " pullback:" +
					// ((QuoteData)quotes[phl.pullBack.pos]).time +"@" +
					// phl.pullBack.high );
					/*
					 * not worth fixing if ( phl.pullBack.pos == phl.prePos )
					 * phl.pullBack = Utility.getHigh(quotes, phl.prePos+1,
					 * phl.curPos);
					 */
					if (!trade.profitTake1 && (data.high > phl.pullBack.high))
					{
						int lastTradeEntryPos = trade.entryPos;
						double lastTradEentryPrice = trade.entryPrice;
						String lastTradeType = trade.type;
						logger.warning(symbol + " " + CHART + " minute target hit for breaking lower low " + data.time);

						if (MODE == Constants.TEST_MODE)
							AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
						closePositionByMarket(trade.remainingPositionSize, data.close);

						/*
						 * if ( MODE == Constants.TEST_MODE) {
						 * AddTradeCloseRecord( data.time, Constants.ACTION_BUY,
						 * trade.remainingPositionSize, data.close);
						 * closePositionByMarket( trade.remainingPositionSize,
						 * data.close); } else if ( MODE == Constants.REAL_MODE)
						 * { trade.targetPrice = phl.pullBack.high;
						 * trade.targetPrice = adjustPrice( data.close,
						 * Constants.ADJUST_TYPE_DOWN); trade.targetPositionSize
						 * = trade.remainingPositionSize;
						 *
						 * trade.targetId = placeLmtOrder(Constants.ACTION_BUY,
						 * trade.targetPrice, trade.targetPositionSize,
						 * null);//new Long(oca).toString()); trade.profitTake1
						 * = true; }
						 */

						// check to see if it worth reversal
						if (after_target_reversal == true)
						{
							if (lastTradeType.equals(Constants.TRADE_RST) && (phl.curPos - phl.prePos > 2))
							{
								QuoteData lowAfterEntry = Utility.getLow(quotes, lastTradeEntryPos, lastbar);

								if (lastTradEentryPrice - lowAfterEntry.low < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
								{
									trade = new Trade(symbol);
									trade.type = Constants.TRADE_CNT;
									trade.action = Constants.ACTION_BUY;
									trade.POSITION_SIZE = POSITION_SIZE;
									trade.lowHighAfterEntry = lowAfterEntry;

									logger.warning(symbol + " " + CHART + " place market reverse buy order");
									trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
									trade.price = phl.pullBack.high;
									trade.entryPrice = trade.price;
									trade.status = Constants.STATUS_PLACED;
									trade.remainingPositionSize = trade.POSITION_SIZE;
									trade.entryTime = ((QuoteData) quotes[lastbar]).time;
									trade.entryPos = lastbar;
									if (MODE == Constants.TEST_MODE)
										AddTradeOpenRecord(trade.type, trade.entryTime, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.entryPrice);

									trade.stop = lowAfterEntry.low;
									if ((trade.entryPrice - trade.stop) > FIXED_STOP * PIP_SIZE)
										trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;

									trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);

									logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
									trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																																// Long(oca).toString());
								}
							}
						}
					}
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.entryPos + 2 > lastbar)
				return;

			int bottom;
			if (Constants.TRADE_CNT.equals(trade.type))
				bottom = trade.lowHighAfterEntry.pos;
			else
				bottom = Utility.getLow(quotes, trade.entryPos, lastbar).pos;

			for (int i = lastbar; i > bottom; i--)
			{
				PushHighLow phl = findLastNHigh(quotes, bottom, i, 1);
				if (phl != null)
				{
					// logger.warning(symbol + " " + CHART +
					// "this high between " +
					// ((QuoteData)quotes[phl.prePos]).time + "@" +
					// ((QuoteData)quotes[phl.prePos]).high + "  -  " +
					// ((QuoteData)quotes[phl.curPos]).time + "@" +
					// ((QuoteData)quotes[phl.curPos]).high + " pullback@" +
					// phl.pullBack );

					if (i == lastbar)
					{
						if ((trade.profitTake2 == false) && ((((QuoteData) quotes[phl.prePos]).high - phl.pullBack.low) > 25 * PIP_SIZE))
						{
							logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
							trade.targetPrice = data.close;
							trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_UP);

							trade.targetPositionSize = trade.remainingPositionSize;

							trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);// new
																																		// Long(oca).toString());
							trade.profitTake2 = true;
							return;
						}

					}

					// logger.warning(symbol + " exit highs detected at  " +
					// ((QuoteData)quotes[phl.prePos]).time + "@" +
					// ((QuoteData)quotes[phl.prePos]).high + "  -  " +
					// ((QuoteData)quotes[phl.curPos]).time + "@" +
					// ((QuoteData)quotes[phl.curPos]).high + " pullback:" +
					// ((QuoteData)quotes[phl.pullBack.pos]).time +"@" +
					// phl.pullBack.low );
					/*
					 * not worth fixing if ( phl.pullBack.pos == phl.prePos )
					 * phl.pullBack = Utility.getLow(quotes, phl.prePos+1,
					 * phl.curPos);
					 */
					if (!trade.profitTake1 && (data.low < phl.pullBack.low))
					{
						int lastTradeEntryPos = trade.entryPos;
						double lastTradEentryPrice = trade.entryPrice;
						String lastTradeType = trade.type;
						logger.warning(symbol + " " + CHART + " minute target hit for breaking higher high at " + data.time);

						if (MODE == Constants.TEST_MODE)
							AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePositionByMarket(trade.remainingPositionSize, data.close);

						/*
						 * if ( MODE == Constants.TEST_MODE) {
						 * AddTradeCloseRecord( data.time,
						 * Constants.ACTION_SELL, trade.remainingPositionSize,
						 * data.close); closePositionByMarket(
						 * trade.remainingPositionSize, data.close); } else if (
						 * MODE == Constants.REAL_MODE) { trade.targetPrice =
						 * phl.pullBack.low; trade.targetPrice = adjustPrice(
						 * data.close, Constants.ADJUST_TYPE_UP);
						 * trade.targetPositionSize =
						 * trade.remainingPositionSize;
						 *
						 * //trade.targetId =
						 * placeLmtOrder(Constants.ACTION_SELL,
						 * trade.targetPrice, trade.targetPositionSize,
						 * null);//new Long(oca).toString()); trade.targetId =
						 * placeMktOrder(Constants.ACTION_SELL,
						 * trade.targetPositionSize); trade.profitTake1 = true;
						 * }
						 */

						if (after_target_reversal == true)
						{
							// check to see if it worth reversal
							if (lastTradeType.equals(Constants.TRADE_RST) && (phl.curPos - phl.prePos > 2))
							{
								QuoteData highAfterEntry = Utility.getHigh(quotes, lastTradeEntryPos, lastbar);

								if (highAfterEntry.high - lastTradEentryPrice < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
								{
									trade = new Trade(symbol);
									trade.type = Constants.TRADE_CNT;
									trade.action = Constants.ACTION_SELL;
									trade.POSITION_SIZE = POSITION_SIZE;
									trade.lowHighAfterEntry = highAfterEntry;

									logger.warning(symbol + " " + CHART + " place market reverse sell order");
									trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
									trade.price = phl.pullBack.low;
									trade.entryPrice = trade.price;
									trade.status = Constants.STATUS_PLACED;
									trade.remainingPositionSize = trade.POSITION_SIZE;
									trade.entryTime = ((QuoteData) quotes[lastbar]).time;
									trade.entryPos = lastbar;
									if (MODE == Constants.TEST_MODE)
										AddTradeOpenRecord(trade.type, trade.entryTime, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.entryPrice);

									trade.stop = highAfterEntry.high;
									if ((trade.stop - trade.entryPrice) > FIXED_STOP * PIP_SIZE)
										trade.stop = trade.entryPrice + FIXED_STOP * PIP_SIZE;

									trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);

									logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
									trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																																// Long(oca).toString());
								}
							}
						}

						return;
					}
				}
			}
		}
	}





	public void exit123_3_c_old(QuoteData data, Object[] quotesL, int tradeEntryPosL)
	{
		int lastbarL = quotesL.length - 1;

		if ((trade == null) || (tradeEntryPosL == 0))
		{
			logger.severe(symbol + " " + CHART + " no trade status set!");
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

					// logger.warning(symbol + " " + CHART +
					// " trail profit - move stop to " + trade.stop + " " +
					// data.time );
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{

				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					/*
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// exit when making a new high/low at exi
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
						}
						/*
						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((phl_cur.pullBack.high - ((QuoteData) quotes[phl_cur.prePos]).low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 15 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// check 2: large break outs
							if (((((QuoteData) quotes[phl_cur.prePos]).low - data.low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								if (previousTradeExist(phl_cur.pullBack.pos))
									return;

								logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
								trade.pullBackPos = phl_cur.pullBack.pos;

								// place order public int placeLmtOrder(String
								// action, double limitPrice, int posSize,
								// String ocaGroup)
								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;

							}
							// check 3: small exhausting breakouts
						}*/
				//	}
				}

				// normal exit stragety
				// this is to use the small timeframe
				Object[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;


				PushHighLow[] phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
					double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;

					if ((trade.profitTake1 == false) && ( pullback_cur > pullback_pre * 1.4 ))// && (pullback_cur > 18 * PIP_SIZE))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = POSITION_SIZE/2;//trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					/*
					if ((trade.profitTake3 == false) && ( pullback_cur > FIXED_STOP * 1.8 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

						trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos2 = POSITION_SIZE/2;//trade.remainingPositionSize;
						trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
								+ data.time);

						trade.profitTake3 = true;
						return;
					}*/
				}


				// this is to use the larger timeframe
				int top = Utility.getHigh(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				for (int i = lastbarL; i > top; i--)
				{
					PushHighLow phl = findLastNLow(quotesL, top, i, 1);
					if (phl != null)
					{
						if (i == lastbarL)
						{
							if ((trade.profitTake2 == false)
									&& (phl.pullBack.high - (((QuoteData) quotesL[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
								// large pull back, lock in profit
								trade.targetPrice = data.close;
								trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_DOWN);
								trade.targetPositionSize = trade.remainingPositionSize;
								trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);// new
								trade.profitTake2 = true;
								return;
							}

							double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
							if (trade.stop != stop)
							{
								cancelOrder(trade.stopId);
								trade.stop = stop;

								// logger.warning(symbol + " " + CHART + " trail profit - move stop to " + trade.stop + " " + data.time );
								trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
								trade.adjustStop++;

							}
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
					trade.stop = trade.entryPrice;

					// logger.warning(symbol + " " + CHART +
					// " trail profit - move stop to " + trade.stop + " " +
					// data.time );
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				/*


				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Highs(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
							logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);




							/*
							if ((((QuoteData) quotes[phl_cur.prePos]).high - phl_cur.pullBack.low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 15 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((data.high - (((QuoteData) quotes[phl_cur.prePos]).low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large breakouts" + data.time);

								trade.targetPrice1 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}*/
					//	}
					//}
				//}

				// normal exit stragety
				// this is to use the small time frame
				Object[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;

				PushHighLow[] phls = Pattern.findPast2Highs(quotes, trade.entryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
					double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;

					if ((trade.profitTake1 == false ) && ( pullback_cur > pullback_pre * 1.4 ))//&&(pullback_cur > 18 * PIP_SIZE))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = POSITION_SIZE/2;//trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					/*
					// ADDED
					if ((trade.profitTake3 == false ) && ( pullback_cur > FIXED_STOP * 1.8 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

						trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos2 = POSITION_SIZE/2;//trade.remainingPositionSize;
						trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
								+ data.time);

						trade.profitTake3 = true;
						return;
					}
					*/
				}


				// this is to use the larger time frame
				int bottom = Utility.getLow(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				for (int i = lastbarL; i > bottom; i--)
				{
					PushHighLow phl = findLastNHigh(quotesL, bottom, i, 1);
					if (phl != null)
					{
						// logger.warning(symbol + " " + CHART +
						// " take profit high between " +
						// ((QuoteData)quotes[phl.prePos]).time + "@" +
						// ((QuoteData)quotes[phl.prePos]).high + "  -  " +
						// ((QuoteData)quotes[phl.curPos]).time + "@" +
						// ((QuoteData)quotes[phl.curPos]).high + " pullback@" +
						// phl.pullBack );

						if (i == lastbarL)
						{
							if ((trade.profitTake2 == false)
									&& ((((QuoteData) quotesL[phl.prePos]).high - phl.pullBack.low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
								trade.targetPrice = data.close;
								trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_UP);

								trade.targetPositionSize = trade.remainingPositionSize;

								trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);// new
																																			// Long(oca).toString());
								trade.profitTake2 = true;
								return;
							}

							double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
							if (trade.stop != stop)
							{
								cancelOrder(trade.stopId);
								trade.stop = stop;

								// logger.warning(symbol + " " + CHART +
								// " trail profit - move stop to " + trade.stop
								// + " " + data.time );
								trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
								trade.adjustStop++;

							}
							return;
						}
					}
				}
			}
		}
	}

	public void exit123_3_c(QuoteData data, Object[] quotesL, int tradeEntryPosL)
	{
		int lastbarL = quotesL.length - 1;
		int EXIT_INCREMENT = POSITION_SIZE/2;

		if ((trade == null) || (tradeEntryPosL == 0))
		{
			logger.severe(symbol + " " + CHART + " no trade status set!");
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

					// logger.warning(symbol + " " + CHART +  trail profit - move stop to " + trade.stop + " " + data.time );
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					/*
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// exit when making a new high/low at exi
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
						}
						/*
						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((phl_cur.pullBack.high - ((QuoteData) quotes[phl_cur.prePos]).low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 15 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// check 2: large break outs
							if (((((QuoteData) quotes[phl_cur.prePos]).low - data.low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								if (previousTradeExist(phl_cur.pullBack.pos))
									return;

								logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
								trade.pullBackPos = phl_cur.pullBack.pos;

								// place order public int placeLmtOrder(String
								// action, double limitPrice, int posSize,
								// String ocaGroup)
								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;

							}
							// check 3: small exhausting breakouts
						}*/
				//	}
				}

				// normal exit stragety
				// this is to use the small timeframe
				Object[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;

				PushHighLow[] phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar);
				if (phls != null)
				{
					if (phls.length >=1)
					{
						PushHighLow phl_cur = phls[0];

						double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;

						// first to check
						if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * PIP_SIZE * 1.8  ))
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " to exit on large pull back" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

							trade.profitTake1 = true;
							return;
						}
					}
				}/*
					else if  (phls.length >=2)
					{
						PushHighLow phl_cur = phls[0];
						PushHighLow phl_pre = phls[1];

						//double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
						//double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;
						double breakout_pre = ((QuoteData)quotes[phl_pre.prePos]).low - ((QuoteData)quotes[phl_pre.curPos]).low;

						// large break out
						if ((trade.profitTake1 == false) && ( breakout_pre > FIXED_STOP * 0.7 ))
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on break out" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.curPos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

							trade.profitTake1 = true;
							return;
						}
					}
				}
				else
				{
					phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar-1);
					if ((phls != null) && (phls.length >=1))
					{
						if ((trade.profitTake2 == false) && (data.low < ((QuoteData)quotes[lastbar-1]).low))
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " take partial profit on large breakouts" + data.time);

							AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
							closePositionByMarket(POSITION_SIZE/2, data.close);
							trade.profitTake2 = true;
							return;
						}
					}
				}*/


				// this is to use the larger timeframe, this is the last exit
				int top = Utility.getHigh(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				for (int i = lastbarL; i > top; i--)
				{
					PushHighLow phl = findLastNLow(quotesL, top, i, 1);
					if (phl != null)
					{
						if (i == lastbarL)
						{
							if (trade.profitTake2 == false)
									//&& (phl.pullBack.high - (((QuoteData) quotesL[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								double pullBackSize = phl.pullBack.high - ((QuoteData) quotesL[phl.prePos]).low;
								double entry = Utility.getHigh(quotes, trade.entryPos -5, trade.entryPos+5).high;
								double totalProfit = trade.entryPrice - ((QuoteData) quotesL[phl.prePos]).low;

								if ( pullBackSize > totalProfit * 0.6 )
								{
									logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
									// large pull back, lock in profit
									trade.targetPrice = data.close;
									trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_DOWN);
									trade.targetPositionSize = trade.remainingPositionSize;
									trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);// new
									trade.profitTake2 = true;
									return;
								}
							}

							double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
							if (trade.stop != stop)
							{
								cancelOrder(trade.stopId);
								trade.stop = stop;

								// logger.warning(symbol + " " + CHART + " trail profit - move stop to " + trade.stop + " " + data.time );
								trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
								trade.adjustStop++;

							}
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
					trade.stop = trade.entryPrice;

					// logger.warning(symbol + " " + CHART +
					// " trail profit - move stop to " + trade.stop + " " +
					// data.time );
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				/*
				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Highs(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
							logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);


							if ((((QuoteData) quotes[phl_cur.prePos]).high - phl_cur.pullBack.low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 15 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((data.high - (((QuoteData) quotes[phl_cur.prePos]).low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large breakouts" + data.time);

								trade.targetPrice1 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}
						}
					}
				}*/


				// normal exit stragety
				// this is to use the small time frame
				Object[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;

				PushHighLow[] phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar);
				if (phls != null)
				{
					if (phls.length >=1)
					{
						PushHighLow phl_cur = phls[0];

						double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;

						// first to check
						if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * PIP_SIZE * 1.8  ))
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " to exit on large pull back" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

							trade.profitTake1 = true;
							return;
						}
					}
				}




				// this is to use the larger time frame
				int bottom = Utility.getLow(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				for (int i = lastbarL; i > bottom; i--)
				{
					PushHighLow phl = findLastNHigh(quotesL, bottom, i, 1);
					if (phl != null)
					{
						// logger.warning(symbol + " " + CHART +
						// " take profit high between " +
						// ((QuoteData)quotes[phl.prePos]).time + "@" +
						// ((QuoteData)quotes[phl.prePos]).high + "  -  " +
						// ((QuoteData)quotes[phl.curPos]).time + "@" +
						// ((QuoteData)quotes[phl.curPos]).high + " pullback@" +
						// phl.pullBack );

						if (i == lastbarL)
						{
							if (trade.profitTake2 == false)
								//	&& ((((QuoteData) quotesL[phl.prePos]).high - phl.pullBack.low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								double pullBackSize = ((QuoteData) quotesL[phl.prePos]).high - phl.pullBack.low;
								double entry = Utility.getLow(quotes, trade.entryPos-5, trade.entryPos+5).low;
								double totalProfit = ((QuoteData) quotesL[phl.prePos]).high - entry;

								if ( pullBackSize > totalProfit * 0.6 )
								{
									logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
									trade.targetPrice = data.close;
									trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_UP);

									trade.targetPositionSize = trade.remainingPositionSize;

									trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);// new
																																				// Long(oca).toString());
									trade.profitTake2 = true;
									return;
								}
							}

							double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
							if (trade.stop != stop)
							{
								cancelOrder(trade.stopId);
								trade.stop = stop;

								// logger.warning(symbol + " " + CHART +
								// " trail profit - move stop to " + trade.stop
								// + " " + data.time );
								trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
								trade.adjustStop++;

							}
							return;
						}
					}
				}
			}
		}
	}




	public void exit123_3_c_bak(QuoteData data)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		/*
		 * // if quick profit target set, cancel target if it has not bee a
		 * quick profit if (Constants.TRADE_CNT.equals(trade.type) && ( lastbar
		 * - trade.entryPos > 8 ) && ( trade.targetReached == false)) {
		 * trade.targetPrice = 0; trade.targetPositionSize = 0;
		 *
		 * if ( MODE == Constants.REAL_MODE) cancelOrder( trade.targetId); }
		 */

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.entryPos + 2 > lastbar)
				return;

			int top;

			if (Constants.TRADE_RST.equals(trade.type))
				top = Utility.getHigh(quotes, trade.entryPos, lastbar).pos;
			else if (Constants.TRADE_CNT.equals(trade.type))
				top = trade.lowHighAfterEntry.pos;
			else
			{
				logger.severe(symbol + " " + CHART + " no trade status set!");
				return;
			}

			for (int i = lastbar; i > top; i--)
			{
				PushHighLow phl = findLastNLow(quotes, top, i, 1);
				if (phl != null)
				{
					if (i == lastbar)
					{
						if ((trade.profitTake2 == false) && (phl.pullBack.high - (((QuoteData) quotes[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
						{
							logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
							// large pull back, lock in profit
							trade.targetPrice = data.close; // TODO: this could
															// be better
							trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_DOWN);

							trade.targetPositionSize = trade.remainingPositionSize;

							trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);// new
																																	// Long(oca).toString());
							trade.profitTake2 = true;
							return;
						}

					}

					// logger.warning(symbol + " exit low detected at  " +
					// ((QuoteData)quotes[phl.prePos]).time + "@" +
					// ((QuoteData)quotes[phl.prePos]).low + "  -  " +
					// ((QuoteData)quotes[phl.curPos]).time + "@" +
					// ((QuoteData)quotes[phl.curPos]).low + " pullback:" +
					// ((QuoteData)quotes[phl.pullBack.pos]).time +"@" +
					// phl.pullBack.high );
					/*
					 * not worth fixing if ( phl.pullBack.pos == phl.prePos )
					 * phl.pullBack = Utility.getHigh(quotes, phl.prePos+1,
					 * phl.curPos);
					 */
					if (!trade.profitTake1 && (data.high > phl.pullBack.high))
					{
						logger.warning(symbol + " " + CHART + " minute target hit for breaking lower low " + data.time);
						if (MODE == Constants.TEST_MODE)
						{
							AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
							closePositionByMarket(trade.remainingPositionSize, data.close);
							return;
						}
						else if (MODE == Constants.REAL_MODE)
						{
							trade.targetPrice = phl.pullBack.high;
							trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_DOWN);
							trade.targetPositionSize = trade.remainingPositionSize;

							trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);// new
																																	// Long(oca).toString());
							trade.profitTake1 = true;
							return;
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.entryPos + 2 > lastbar)
				return;

			int bottom;
			if (Constants.TRADE_RST.equals(trade.type))
				bottom = Utility.getLow(quotes, trade.entryPos, lastbar).pos;
			else if (Constants.TRADE_CNT.equals(trade.type))
				bottom = trade.lowHighAfterEntry.pos;
			else
			{
				logger.severe(symbol + " " + CHART + " no trade status set!");
				return;
			}

			for (int i = lastbar; i > bottom; i--)
			{
				PushHighLow phl = findLastNHigh(quotes, bottom, i, 1);
				if (phl != null)
				{
					// logger.warning(symbol + " " + CHART +
					// "this high between " +
					// ((QuoteData)quotes[phl.prePos]).time + "@" +
					// ((QuoteData)quotes[phl.prePos]).high + "  -  " +
					// ((QuoteData)quotes[phl.curPos]).time + "@" +
					// ((QuoteData)quotes[phl.curPos]).high + " pullback@" +
					// phl.pullBack );

					if (i == lastbar)
					{
						if ((trade.profitTake2 == false) && ((((QuoteData) quotes[phl.prePos]).high - phl.pullBack.low) > 25 * PIP_SIZE))
						{
							logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
							trade.targetPrice = data.close;
							trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_UP);

							trade.targetPositionSize = trade.remainingPositionSize;

							trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);// new
																																		// Long(oca).toString());
							trade.profitTake2 = true;
							return;
						}

					}

					// logger.warning(symbol + " exit highs detected at  " +
					// ((QuoteData)quotes[phl.prePos]).time + "@" +
					// ((QuoteData)quotes[phl.prePos]).high + "  -  " +
					// ((QuoteData)quotes[phl.curPos]).time + "@" +
					// ((QuoteData)quotes[phl.curPos]).high + " pullback:" +
					// ((QuoteData)quotes[phl.pullBack.pos]).time +"@" +
					// phl.pullBack.low );
					/*
					 * not worth fixing if ( phl.pullBack.pos == phl.prePos )
					 * phl.pullBack = Utility.getLow(quotes, phl.prePos+1,
					 * phl.curPos);
					 */
					if (!trade.profitTake1 && (data.low < phl.pullBack.low))
					{
						logger.warning(symbol + " " + CHART + " minute target hit for breaking higher high at " + data.time);
						if (MODE == Constants.TEST_MODE)
						{
							AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
							closePositionByMarket(trade.remainingPositionSize, data.close);
							return;
						}
						else if (MODE == Constants.REAL_MODE)
						{
							trade.targetPrice = phl.pullBack.low;
							trade.targetPrice = adjustPrice(data.close, Constants.ADJUST_TYPE_UP);
							trade.targetPositionSize = trade.remainingPositionSize;

							trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);// new
																																		// Long(oca).toString());
							trade.profitTake1 = true;
							return;
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

	@Override
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


}

