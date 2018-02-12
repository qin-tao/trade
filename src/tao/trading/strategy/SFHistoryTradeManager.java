package tao.trading.strategy;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.BreakOut;
import tao.trading.Constants;
import tao.trading.Cup;
import tao.trading.Indicator;
import tao.trading.Pattern;
import tao.trading.Peak;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.dao.PushHighLow;
import tao.trading.strategy.util.Utility;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.TradeOrder;
import com.ib.client.TradeOrder2;
import com.ib.client.TradeRecord;



public class SFHistoryTradeManager extends TradeManager2 implements Serializable
{
	HashMap<String, QuoteData> qts = new HashMap<String, QuoteData>(1000);
	int MODE = Constants.REAL_MODE;
	boolean CNT= false; 
	int CHART;
	int TARGET_PROFIT_SIZE;
	double PIP_SIZE;
	boolean oneMinutChart = false;
	int POSITION_SIZE;
	double FIXED_STOP;
	String tradeZone1, tradeZone2;
	double minTarget1, minTarget2;
	HashMap<Integer, Integer> profitTake = new HashMap<Integer, Integer>();
	Vector<Trade> tradeHistory = new Vector<Trade>();
	String portofolioReport = null;
	double totalProfit = 0;
	String currentTime;
	Vector<TradeOrder2> tradeOrders = new Vector<TradeOrder2>();
	double exchangeRate;
	String currency;
	int DEFAULT_RETRY = 1;
	boolean test = false;
	
	public SFHistoryTradeManager()
	{
		super();
	}
	
	public SFHistoryTradeManager(String account, EClientSocket client, Contract contract, int symbolIndex)
	{
		super( account, client, contract, symbolIndex );
		
	   // m_client.reqMktData(199, m_contract, null, false);

	}

	public Object[] getQuoteData()
	{
		Object[] quotes = this.qts.values().toArray();
	    Arrays.sort(quotes);
	    return quotes;
	}


	//////////////////////////////////////////////////////////////////////////////////////
	public int placeMktOrder( String action, int quantity)
	{
		if ( MODE == Constants.TEST_MODE )
			return 97;

		logger.warning( symbol + " " + CHART + " MIN " + " to place market " + action + " order");
		return super.placeMktOrder(action, quantity);
			
	}
	
	public int placeStopOrder( String action, double stopPrice, int posSize, String ocaGroup)
	{
		if ( MODE == Constants.TEST_MODE )
			return 99;

		logger.warning(symbol + " " + CHART + " MIN " + " to place stop " + action + " order "  + posSize + " " + stopPrice);
		return super.placeStopOrder(action, stopPrice, posSize, ocaGroup);

	}

	public int placeLmtOrder(String action, double limitPrice, int posSize, String ocaGroup)
	{
		if ( MODE == Constants.TEST_MODE )
			return 98;

		logger.warning( symbol + " " + CHART + " MIN " + " to place limit " + action + " order "  + posSize + " " + limitPrice);
		return super.placeLmtOrder( action, limitPrice, posSize, ocaGroup);
	}
	///////////////////////////////////////////////////////////////////////////////////

	
	
	
	public void closePositionByMarket( int posSize, double currentPrice )
	{
		String action = trade.action.equals("BUY") ? "SELL" : "BUY";
	
		// this is to place the real order
		placeMktOrder(action, posSize);

		trade.remainingPositionSize -= posSize;
		
		if ( trade.remainingPositionSize == 0 )
		{
			if ( trade.stopId != 0 )
				cancelOrder(trade.stopId);
			tradeHistory.add(trade);
			trade = null;
		}
		
	}
	
	public void closePositionByLimit( double limitPrice, int positionSize)
	{
		String action = trade.action.equals("BUY") ? "SELL" : "BUY";
		trade.closeId = placeLmtOrder( action, limitPrice, positionSize, null);
		
	}
	
	public void cancelOrder( int orderId)
	{
		if ( MODE == Constants.REAL_MODE )
		{
			logger.warning( symbol + " " + CHART + " MIN " + " cancel order "  + orderId );
			super.cancelOrder(orderId);
		}
	}
	

	
	
}
