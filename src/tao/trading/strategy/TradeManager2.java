package tao.trading.strategy;

import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.Command;
import tao.trading.CommandAction;
import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.Instrument;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.Push;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.TradePosition;
import tao.trading.dao.MABreakOutList;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.dao.ReturnToMADAO;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.tm.Strategy;
import tao.trading.strategy.tm.TradeManagerInf;
import tao.trading.strategy.tm.TradeReport;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.Order;
import com.ib.client.TradeOrder;
import com.ib.client.TradeOrder2;
import com.ib.client.TradeRecord;
import com.thoughtworks.xstream.XStream;

public abstract class TradeManager2 {
	public static String OUTPUT_DIR;
	
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
	protected SimpleDateFormat DateFormatter = new SimpleDateFormat("yyyyMMdd");
	protected SimpleDateFormat InternalDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm");
	protected SimpleDateFormat ShortDateFormatter = new SimpleDateFormat("MMdd:HH");
	protected String IB_ACCOUNT;
	protected int MODE;
	protected Strategy strategy;
	protected Instrument instrument;
	protected TradeManagerInf[] tradeManager;
	HashMap<String, QuoteData>[] qts = new HashMap[Constants.TOTAL_CHARTS];
	HashMap<Double, Integer> profitTake = new HashMap<Double, Integer>();
	protected SimpleDateFormat OrderDateFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss"); // FORMAT:
																								// 20060505
																								// 08:00:00
																								// {time
																								// zone}
	protected Logger logger = Logger.getLogger("TradeManager2");
	protected Trade trade;
	int CHART;
	int TIME_ZONE;
	protected EClientSocket m_client;
	protected Contract m_contract;
	protected int m_clientId;
	protected String orderIdFile = "orderId.txt";
	protected String symbol;
	protected int priceType;
	protected Vector<Trade> tradeHistory = new Vector<Trade>();
	int tradeSequence = 0;
	String portofolioReport = null;
	protected double totalProfit = 0;
	protected double totalUnrealizedProfit = 0;
	protected double totalTrade = 0;
	Vector<TradeOrder> tradeOrders = new Vector<TradeOrder>();
	Vector<TradeOrder2> tradeOrder2s = new Vector<TradeOrder2>();
	protected double exchangeRate;
	protected String currency;
	protected double PIP_SIZE;
	protected int FIXED_STOP;
	protected int PARM1,PARM2;
	int DEFAULT_PROFIT_TARGET = 400;
	boolean breakEven = false;
	int stopKickIn;
	int tickId;
	boolean tickStarted;
	boolean STOP_LIMIT = true;
	protected int POSITION_SIZE;
	boolean LIMIT_ORDER;
	protected boolean timeout;
	protected String lastEvents;
	int entryMode;
	protected boolean enabled;
	Object entryObject;
	Object setup;
	boolean manual_override = false;
	protected int realtime_count = 0;

	public static boolean PAUSE_TRADING, PREEMPTIVE_REVERSAL;

	
	
	
	// abstract methods implemented by subclasses
	public abstract void process( int req_id, QuoteData data );
	//public abstract void processTickEvent( int req_id, QuoteData data );
	public abstract void checkOrderFilled(int orderId, int filled);

	
	public TradeManager2() {

	}

	public TradeManager2(EClientSocket client, int clientId, Logger logger) {
		m_client = client;
		m_clientId = clientId;
		this.logger = logger;

	}

	public TradeManager2(String account, EClientSocket client, Contract contract, int clientId) {
		IB_ACCOUNT = account;
		m_contract = contract;
		m_client = client;
		m_clientId = clientId;

		//orderIdFile = account + "_orderId.txt";

		if (contract != null)
			symbol = m_contract.m_symbol + "." + m_contract.m_currency;

	}

	public String getSymbol() {
		return symbol;
	}

	public String getAccount() {
		return IB_ACCOUNT;
	}

	public QuoteData[] getQuoteData(int CHART) {
		Object[] quotes = this.qts[CHART].values().toArray();
		Arrays.sort(quotes);
		return Arrays.copyOf(quotes, quotes.length, QuoteData[].class);
	}

	public double getPIP_SIZE() {
		return PIP_SIZE;
	}

	public int getPOSITION_SIZE() {
		return POSITION_SIZE;
	}

	public int getFIXED_STOP() {
		return FIXED_STOP;

	}

	public void setPositionSize(int tradeSize){
	   	this.POSITION_SIZE = tradeSize;
	}

	public void setStopSize(int stopSize){
	   	this.FIXED_STOP = stopSize;
	}

	public void setParm1(int p1){
	   	this.PARM1 = p1;
	}

	public void setParm2(int p2){
	   	this.PARM2 = p2;
	}

	public int getParm1() {
		return PARM1;
	}

	public int getParm2() {
		return PARM2;
	}
	
	protected void error(String s) {
		logger.severe(symbol + " " + strategy.STRATEGY_NAME + " " +  s);
	}

	protected void warning(String s) {
		logger.warning(symbol + " " + strategy.STRATEGY_NAME + " " + s);
	}

	protected void info(String s) {
		logger.info(symbol + " " + strategy.STRATEGY_NAME + " " +  s);
	}

	protected void config(String s) {
		logger.info(symbol + " " + strategy.STRATEGY_NAME + " " +  s);
	}

	protected void fine(String s) {
		logger.fine(symbol + " " + strategy.STRATEGY_NAME + " " +  s);
	}

	protected void finer(String s) {
		logger.finer(symbol + " " + strategy.STRATEGY_NAME + " " +  s);
	}

	protected void finest(String s) {
		logger.finest(symbol + " " + strategy.STRATEGY_NAME + " " +  s);
	}
	
	public String getPortofolioReport() {
		return portofolioReport;
	}

	public void setEnable(boolean enable) {
		this.enabled = enable;
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// Basic Order Function
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	synchronized public int getOrderId() {
		int orderId = readOrderId();
		logger.info("OrderId: " + orderId);
		writeOrderId(orderId + 1);
		return orderId;
	}

	public void writeOrderId(int orderId) {
		try {
			FileWriter fstream = new FileWriter(orderIdFile);
			BufferedWriter out = new BufferedWriter(fstream);
			String st = (new Integer(orderId)).toString();
			out.write(st);
			// Close the output stream
			out.close();
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
	}

	public int readOrderId() {
		BufferedReader in;
		String read = null;
		try {
			in = new BufferedReader(new FileReader(orderIdFile));// open a
			read = in.readLine();
			in.close();// safley close the BufferedReader after use
		} catch (IOException e) {
			logger.severe("Error reading order Id:" + e);
			e.printStackTrace();
		}
		return new Integer(read);
	}

	public Order createOrder() {
		Order order = new Order();
		order.m_orderId = getOrderId();
		order.m_account = IB_ACCOUNT;
		order.m_clientId = m_clientId;
		return order;
	}

	public Trade getTrade() {
		return trade;
	}

	public void setTrade(Trade t) {
		this.trade = t;
	}

	/**************************************************************************************************************************
	 * Place Market Order
	 * 
	 * @param action
	 * @param quantity
	 * @return
	 **************************************************************************************************************************/
	public int placeMktOrder(String action, int quantity) {
		logger.warning(symbol + " to place market order:  mode=" + MODE);
		if (MODE != Constants.REAL_MODE)
			return 96;

		Order order = createOrder();
		order.m_action = action;
		order.m_totalQuantity = quantity;
		order.m_orderType = "MKT";

		m_client.placeOrder(order.m_orderId, m_contract, order);
		logger.warning(order.m_account + " " + symbol + " orderId:" + order.m_orderId + " market " + action + " order placed " + quantity);

		return order.m_orderId;
	}

	/**************************************************************************************************************************
	 * Place Limit Order
	 * 
	 * @param action
	 * @param quantity
	 * @return
	 **************************************************************************************************************************/
	public int placeLmtOrder(String action, double limitPrice, int posSize, String ocaGroup) {
		logger.warning("Mode:" + MODE + " " + symbol + " place limit order" + action + " " + limitPrice + " " + posSize);
		if (MODE != Constants.REAL_MODE)
			return 97;

		Order order = createOrder();
		order.m_action = action;
		order.m_lmtPrice = limitPrice;
		order.m_totalQuantity = posSize;
		order.m_orderType = "LMT";
		order.m_tif = "GTC";

		if (ocaGroup != null) {
			order.m_ocaGroup = ocaGroup;
			order.m_ocaType = 1; // 1 = CANCEL_WITH_BLOCK, 2 =
									// REDUCE_WITH_BLOCK, 3 = REDUCE_NON_BLOCK
		}

		m_client.placeOrder(order.m_orderId, m_contract, order);
		logger.warning(symbol + " " + " orderId:" + order.m_orderId + " limit " + action + " order placed " + posSize + " " + limitPrice);

		return order.m_orderId;

	}

	/**************************************************************************************************************************
	 * Place Stop Market Order
	 * 
	 * @param action
	 * @param quantity
	 * @return
	 **************************************************************************************************************************/
	public void createStopOrder(double stopPrice) {
		cancelStop();
		trade.stop = stopPrice;
		if ( trade.stop != 0 ){
			trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, stopPrice,
					trade.remainingPositionSize, null);
		}
	}

	public int placeStopOrder(String action, double stopPrice, int posSize, String ocaGroup) {
		return placeStopOrder(action, stopPrice, posSize, ocaGroup, null);
	}

	public int placeStopOrder(String action, double stopPrice, int posSize, String ocaGroup, String goodTillDate) {
		
		warning("place stop order " + action + " " + stopPrice + " " + posSize);

		if (MODE != Constants.REAL_MODE)
			return 98;

		if (posSize == 0) {
			warning("placeStopOrder: positionsize = 0, order not placed");
			return 0;
		}

		if (stopPrice == 0) {
			warning("placeStopOrder: stop price = 0, order not placed");
			return 0;
		}

		Order stopOrder = createOrder();
		stopOrder.m_action = action;
		stopOrder.m_orderType = "STP";
		stopOrder.m_auxPrice = stopPrice;
		stopOrder.m_totalQuantity = posSize;
		stopOrder.m_tif = "GTC";
		if (goodTillDate != null)
			stopOrder.m_goodTillDate = goodTillDate;

		// stopOrder.m_outsideRth = true; // to trigger outside regular trading
		// hour
		if (ocaGroup != null) {
			stopOrder.m_ocaGroup = ocaGroup;
			stopOrder.m_ocaType = 1; // 1 = CANCEL_WITH_BLOCK, 2 =
										// REDUCE_WITH_BLOCK, 3 =
										// REDUCE_NON_BLOCK
		}

		m_client.placeOrder(stopOrder.m_orderId, m_contract, stopOrder);
		warning(" orderId:" + stopOrder.m_orderId + " stop " + action + " order placed " + stopPrice + " " + posSize + " good till " + goodTillDate);

		return stopOrder.m_orderId;
	}

	/**************************************************************************************************************************
	 * Place Stop Limit Order
	 * 
	 * @param action
	 * @param quantity
	 * @return
	 **************************************************************************************************************************/
	public int placeStopLimitOrder(String action, double stopPrice, double limitPrice, int posSize, String ocaGroup) {
		if (MODE != Constants.REAL_MODE)
			return 99;

		Order stopOrder = createOrder();
		stopOrder.m_action = action;
		stopOrder.m_orderType = "STPLMT";
		stopOrder.m_auxPrice = stopPrice;
		stopOrder.m_lmtPrice = limitPrice;
		stopOrder.m_totalQuantity = posSize;
		stopOrder.m_tif = "GTC";
		// stopOrder.m_outsideRth = true; // to trigger outside regular trading
		// hour

		// stopOrder.m_outsideRth = true; // to trigger outside regular trading
		// hour
		if (ocaGroup != null) {
			stopOrder.m_ocaGroup = ocaGroup;
			stopOrder.m_ocaType = 1; // 1 = CANCEL_WITH_BLOCK, 2 =
										// REDUCE_WITH_BLOCK, 3 =
										// REDUCE_NON_BLOCK
		}

		m_client.placeOrder(stopOrder.m_orderId, m_contract, stopOrder);
		logger.warning(symbol + " " + CHART + " orderId:" + stopOrder.m_orderId + " stop limit " + action + " order placed " + " stop price:"
				+ stopPrice + " limitPrice:" + limitPrice);

		return stopOrder.m_orderId;
	}

	/**************************************************************************************************************************
	 * Place Trailing Order
	 * 
	 * @param action
	 * @param quantity
	 * @return
	 **************************************************************************************************************************/
	public int placeTrailOrder(String action, int positionSize, double trailamount, String ocaGroup) {
		Order trailOrder = createOrder();

		trailOrder.m_action = action;
		trailOrder.m_orderType = "TRAIL";// "STP";
		trailOrder.m_auxPrice = trailamount;
		trailOrder.m_totalQuantity = positionSize;
		trailOrder.m_tif = "GTC";

		if (ocaGroup != null) {
			trailOrder.m_ocaGroup = ocaGroup;
			trailOrder.m_ocaType = 1; // 1 = CANCEL_WITH_BLOCK, 2 =
										// REDUCE_WITH_BLOCK, 3 =
										// REDUCE_NON_BLOCK
		}

		m_client.placeOrder(trailOrder.m_orderId, m_contract, trailOrder);
		logger.info(m_contract.m_symbol + "." + m_contract.m_currency + " trail stop order placed:" + trailOrder.m_action + " " + trade.POSITION_SIZE
				+ " " + trade.stop);

		return trailOrder.m_orderId;

	}

	// this is for "close all position"
	protected void closePosition(Contract contract, String action, int positionSize) {
		Order order = createOrder();
		order.m_action = action;
		order.m_totalQuantity = positionSize;
		order.m_orderType = "MKT";

		m_client.placeOrder(order.m_orderId, contract, order);
	}

	public void cancelAllOrders() {
		cancelLimits();
		cancelTargets();
		cancelStop();
		cancelStopMarkets();
	}

	public void cancelLimits() {

		if (MODE == Constants.REAL_MODE) {
			if (trade.limitId1 != 0) {
				info("cancel limit1 " + trade.limitId1);
				cancelOrder(trade.limitId1);
			}
			if (trade.limitId2 != 0) {
				info("cancel limit2 " + trade.limitId2);
				cancelOrder(trade.limitId2);
			}
		}

		trade.limitId1 = trade.limitId2 = 0;

		for (int i = 0; i < trade.TOTAL_LIMITS; i++) {
			if (trade.limits[i] != null) {
				info("cancel limits " + trade.limits[i].orderId);
				cancelOrder(trade.limits[i].orderId);
				trade.limits[i].orderId = 0;
			}
		}
	}

	public void cancelTargets() {
		if (trade.targetId != 0) {
			info("cancel target " + trade.targetId);
			cancelOrder(trade.targetId);
		}
		if (trade.targetId1 != 0) {
			info("cancel target1 " + trade.targetId1);
			cancelOrder(trade.targetId1);
		}
		if (trade.targetId2 != 0) {
			info("cancel target2 " + trade.targetId2);
			cancelOrder(trade.targetId2);
		}
		trade.targetId1 = trade.targetId2 = trade.targetId = 0;

		for (int i = 0; i < trade.TOTAL_TARGETS; i++) {
			if (trade.targets[i] != null) {
				info("cancel targets " + trade.targets[i].orderId);
				cancelOrder(trade.targets[i].orderId);
				trade.targets[i].orderId = 0;
			}
		}
	}

	public void cancelStopMarkets() {

		if (MODE == Constants.REAL_MODE) {
			if (trade.stopMarketId != 0) {
				info("cancel stop market " + trade.stopMarketId);
				cancelOrder(trade.stopMarketId);
			}
		}
		trade.stopMarketId = 0;
	}

	public void cancelProfits() {
		info("cancel profit");
		if (MODE == Constants.REAL_MODE) {
			if (trade.takeProfit1Id != 0) {
				info("cancel profit " + trade.takeProfit1Id);
				cancelOrder(trade.takeProfit1Id);
			}
			if (trade.takeProfit2Id != 0) {
				info("cancel profit " + trade.takeProfit2Id);
				cancelOrder(trade.takeProfit2Id);
			}
		}
		trade.takeProfit1Id = trade.takeProfit2Id = 0;
	}

	public void cancelStop() {
		if (MODE == Constants.REAL_MODE) {
			if (trade.stopId != 0) {
				info("cancel stop " + trade.stopId);
				cancelOrder(trade.stopId);
			}
		}
		trade.stopId = 0;
	}

	protected void cancelOrder(int orderId) {
		if (MODE == Constants.REAL_MODE) {
			if (orderId != 0) {
				warning("cancel order: " + orderId);
				m_client.cancelOrder(orderId);
			}
		}
	}

	
	public void cancelLimitByPrice( double price ) {
		for (int i = 0; i < trade.TOTAL_LIMITS; i++) {
			if ((trade.limits[i] != null) && ( trade.limits[i].orderId != 0 ) && ( trade.limits[i].price == price )){
				info("cancel limits " + trade.limits[i].orderId);
				cancelOrder(trade.limits[i].orderId);
				trade.limits[i].orderId = 0;
				trade.limits[i] = null; // added
			}
		}
	}

	public void cancelTargetByPrice( double price ) {
		for (int i = 0; i < trade.TOTAL_TARGETS; i++) {
			if ((trade.targets[i] != null) && ( trade.targets[i].orderId != 0 ) && ( trade.targets[i].price == price )){
				info("cancel targets " + trade.targets[i].orderId);
				cancelOrder(trade.targets[i].orderId);
				trade.targets[i].orderId = 0;
				trade.targets[i] = null; // added
			}
		}
	}
	
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// Advanced Order Function
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void closePositionByMarket(int posSize, double currentPrice) {
		closePositionByMarket( posSize, currentPrice, true);
	}

	public void closePositionByMarket(int posSize, double currentPrice, boolean realPlace) {
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		String action = trade.action.equals(Constants.ACTION_BUY) ? Constants.ACTION_SELL : Constants.ACTION_BUY;
		warning("close position by market " + action);

		// this is to place the real order
		if ( realPlace == true )
			trade.orderId = placeMktOrder(action, posSize); 

		trade.remainingPositionSize -= posSize;

		if (trade.remainingPositionSize == 0) {
			trade.stop = 0;
			if (trade.stopId != 0) {
				warning("cancel remaining stop order");
				cancelOrder(trade.stopId);
			}
			AddCloseRecord(quotes[lastbar].time, action, posSize, currentPrice);
			getTradeHistory().add(trade);
			trade = null;
		}
	}
	
	
	
	
	public void closePositionByLimit(double limitPrice, int positionSize) {
		String action = trade.action.equals("BUY") ? "SELL" : "BUY";
		trade.closeId = placeLmtOrder(action, limitPrice, positionSize, null);

	}

	protected void beep() {
		Toolkit.getDefaultToolkit().beep();
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// Price Adjustment
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public double adjustPrice(double price, int adjustType) {
		if (priceType == 0)
			priceType = instrument.getPriceType();
		// System.out.println("price is " + price + " priceType: " + priceType +
		// " adjustType: " + adjustType);

		String priceStr = new Double(price).toString();
		int strSize = priceStr.length();
		int dotpos = priceStr.indexOf(".");
		String decimalPortion = priceStr.substring(dotpos + 1);

		if (priceType == Constants.PRICE_TYPE_4) {
			if ((decimalPortion.length() == 6) && ("25".equals(priceStr.substring(strSize - 2)) || "75".equals(priceStr.substring(strSize - 2)))) {
				// convert 0.000025/0.000075 to 0.00005
				double pricemin = 0.000025;
				if (adjustType == Constants.ADJUST_TYPE_DOWN)
					price = price - pricemin;
				else if (adjustType == Constants.ADJUST_TYPE_UP)
					price = price + pricemin;

				BigDecimal bd = new BigDecimal(price);
				bd = bd.setScale(5, BigDecimal.ROUND_HALF_UP);
				// System.out.println("price BigDecimal is " + bd);
				return bd.doubleValue();

			} else if ((decimalPortion.length() == 5) && ("5".equals(priceStr.substring(strSize - 1)))) {
				// 0.00005 return as is
				return price;
			} else {
				NumberFormat formatter = new DecimalFormat("###.####");
				return new Double(formatter.format(price));
			}

		} else if (priceType == Constants.PRICE_TYPE_3) {
			double pricemin = 0.00005;

			// convert 0.00005 to 0.0001
			if ((decimalPortion.length() == 5) && ("5".equals(priceStr.substring(strSize - 1)))) {
				if (adjustType == Constants.ADJUST_TYPE_DOWN)
					price = price - pricemin;
				else if (adjustType == Constants.ADJUST_TYPE_UP)
					price = price + pricemin;

				BigDecimal bd = new BigDecimal(price);
				bd = bd.setScale(4, BigDecimal.ROUND_HALF_UP);
				return bd.doubleValue();
			}

			// we have invalid price here e.g. 120.203
			NumberFormat formatter = new DecimalFormat("###.####");
			return new Double(formatter.format(price));
		} else if (priceType == Constants.PRICE_TYPE_2) {
			// convert 0.0025/0.0075 to 0.005/0.01
			if ((decimalPortion.length() == 4) && (("25".equals(priceStr.substring(strSize - 2)) || "75".equals(priceStr.substring(strSize - 2))))) {
				double pricemin = 0.0025;
				if (adjustType == Constants.ADJUST_TYPE_DOWN)
					price = price - pricemin;
				else if (adjustType == Constants.ADJUST_TYPE_UP)
					price = price + pricemin;

				BigDecimal bd = new BigDecimal(price);
				bd = bd.setScale(3, BigDecimal.ROUND_HALF_UP);
				return bd.doubleValue();

			}
			if (decimalPortion.length() == 2)
				return price;

			if ((decimalPortion.length() == 3) && ("5".equals(priceStr.substring(strSize - 1))))
				return price;

			// we have invalid price here e.g. 120.203
			NumberFormat formatter = new DecimalFormat("###.##");
			return new Double(formatter.format(price));
		} else if (priceType == Constants.PRICE_TYPE_40) {
			NumberFormat formatter = new DecimalFormat("###.####");
			return new Double(formatter.format(price));
		}

		return price;
	}

	double inProfit(QuoteData data) {
		if (Constants.ACTION_BUY.equals(trade.action)) {
			return (data.close - trade.entryPrice);
		} else if (Constants.ACTION_SELL.equals(trade.action)) {
			return (trade.entryPrice - data.close);
		} else
			return 0;

	}

	/*
	 * public void AdjustStopTargetOrders( String ocaId ) { if (
	 * trade.remainingPositionSize == 0 ) { // cancel the stop order if (
	 * this.trade.stopId != 0 ) m_client.cancelOrder(this.trade.stopId); if (
	 * this.trade.targetId != 0 ) m_client.cancelOrder(this.trade.targetId);
	 * trade = null; } else { m_client.cancelOrder(this.trade.stopId);
	 * m_client.cancelOrder(this.trade.targetId); if
	 * (Constants.ACTION_SELL.equals(trade.action)) { trade.stopId =
	 * placeStopOrder(Constants.ACTION_BUY, trade.stop,
	 * trade.remainingPositionSize, ocaId); trade.targetId =
	 * placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice,
	 * trade.remainingPositionSize, ocaId); } else if
	 * (Constants.ACTION_BUY.equals(trade.action)) { trade.stopId =
	 * placeStopOrder(Constants.ACTION_SELL, trade.stop,
	 * trade.remainingPositionSize, ocaId); trade.targetId =
	 * placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice,
	 * trade.remainingPositionSize, ocaId); } } }
	 */

	/*
	 * public void AdjustStopOrders( String ocaId ) { if (
	 * trade.remainingPositionSize == 0 ) { // cancel the stop order if (
	 * this.trade.stopId != 0 ) m_client.cancelOrder(this.trade.stopId); trade =
	 * null; } else { m_client.cancelOrder(this.trade.stopId); if
	 * (Constants.ACTION_SELL.equals(trade.action)) { trade.stopId =
	 * placeStopOrder(Constants.ACTION_BUY, trade.stop,
	 * trade.remainingPositionSize, ocaId); } else if
	 * (Constants.ACTION_BUY.equals(trade.action)) { trade.stopId =
	 * placeStopOrder(Constants.ACTION_SELL, trade.stop,
	 * trade.remainingPositionSize, ocaId); } } }
	 */

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// Trade History
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public Trade createOpenTrade(String tradeType, String action) {
		trade = new Trade(symbol);
		trade.type = tradeType;
		trade.action = action;
		trade.POSITION_SIZE = POSITION_SIZE;
		trade.PIP_SIZE = PIP_SIZE;
		trade.FIXED_STOP = FIXED_STOP;
		trade.status = Constants.STATUS_OPEN;
		return trade;
	}

	public Trade createOpenTrade(String tradeType, String action, String option, int posSize, double triggerPrice) {
		trade = new Trade(symbol);
		trade.type = tradeType;
		trade.action = action;
		trade.options = option;
		trade.POSITION_SIZE = posSize;
		trade.triggerPrice = triggerPrice;
		trade.PIP_SIZE = PIP_SIZE;
		trade.status = Constants.STATUS_OPEN;
		return trade;
	}

	/*
	 * protected void setMktTrade(String type, String action, int positionSize,
	 * double price) { trade.type = type; trade.action = action;
	 * trade.positionSize = positionSize;
	 * 
	 * trade.price = price; trade.entryPrice = trade.price;
	 * 
	 * logger.warning(symbol + " " + CHART + " place market " + action +
	 * " order"); trade.orderId = placeMktOrder(action, trade.positionSize);
	 * 
	 * trade.status = Constants.STATUS_PLACED;
	 * 
	 * trade.remainingPositionSize = trade.positionSize;
	 * 
	 * }
	 */

	public void removeTrade() {
		if (MODE == Constants.REAL_MODE) {
			// m_client.cancelMktData(tickId);
			// tickStarted = false;

			if (trade.limitId1 != 0)
				cancelOrder(trade.limitId1);

			if (trade.limitId2 != 0)
				cancelOrder(trade.limitId2);

			if (trade.targetId != 0)
				cancelOrder(trade.targetId);

			if (trade.stopMarketId != 0)
				cancelOrder(trade.stopMarketId);

			if (trade.stopId != 0)
				cancelOrder(trade.stopId);

		}

		trade.status = Constants.STATUS_CLOSED;
		trade.sequence = tradeSequence++;
		tradeHistory.add(trade);
		trade = null;
		// System.out.println("TradeManager2:" + symbol + " trade removed");

		// removeTradeData();

	}

	protected Vector<Trade> findPreviousTrade(String tradeType) {
		if (tradeType == null) {
			if (getTradeHistory().size() == 0) {
				return null;
			} else {
				return getTradeHistory();
			}
		}

		Vector<Trade> tr = new Vector();
		Iterator it = getTradeHistory().iterator();
		while (it.hasNext()) {
			Trade t = (Trade) it.next();
			if (t.type.equals(tradeType))
				tr.add(t);
		}

		return tr;
	}

	public Vector<Trade> getTradeHistory() {
		return tradeHistory;
	}

	protected boolean previousTradeExist(String tradeType, String action) {
		Vector<Trade> previousTrade = findPreviousTrade(tradeType);
		if (previousTrade.size() > 0) {
			Iterator it = previousTrade.iterator();
			while (it.hasNext()) {
				Trade t = (Trade) it.next();

				if (t.action.equals(action))
					return true;
			}
		}

		return false;

	}

	protected boolean previousTradeExist(String tradeType, String action, int pullBackPos) {
		Vector<Trade> previousTrade = findPreviousTrade(tradeType);
		if (previousTrade.size() > 0) {
			Iterator it = previousTrade.iterator();
			while (it.hasNext()) {
				Trade t = (Trade) it.next();

				if (t.action.equals(action)) {
					if (t.pullBackPos == pullBackPos)
						return true;
				}
			}
		}

		return false;

	}

	protected boolean prevTradeExist(String tradeType, int pullBackPos) {
		Vector<Trade> previousTrade = findPreviousTrade(tradeType);
		if (previousTrade.size() > 0) {
			Iterator it = previousTrade.iterator();
			while (it.hasNext()) {
				Trade t = (Trade) it.next();

				if (t.pullBackPos == pullBackPos)
					return true;
			}
		}

		return false;

	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// Report
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	protected String getPortfolioReport() {
		return portofolioReport;
	}

	protected void AddTradeOpenRecord(String tradeType, String time, String action, int posSize, double price) {
		if (MODE == Constants.REAL_MODE)
			return;

		TradeRecord tr = new TradeRecord(time, action, price, posSize);
		tr.tradeType = tradeType;
		TradeOrder to = new TradeOrder(tr);
		tradeOrders.add(to);
	}

	protected void AddTradeCloseRecord(String time, String action, int posSize, double price) {
		if (MODE == Constants.REAL_MODE)
			return;

		tradeOrders.lastElement().close.add(new TradeRecord(time, action, price, posSize));
	}

	public void report(double currentPrice) {
		NumberFormat formatter = new DecimalFormat("######.##");
		portofolioReport = "";
		totalUnrealizedProfit = totalProfit = totalTrade = 0;
		StringBuffer sb = new StringBuffer();

		int size = tradeOrders.size();
		if (size == 0)
			return;

		sb.append(symbol + " " + CHART + "MIN: " + currentPrice + "\n");
		for (int i = 0; i < size; i++) {
			TradeRecord open = (TradeRecord) tradeOrders.elementAt(i).open;
			sb.append(open.tradeType + " " + open.time + " " + open.action + " " + open.size + " " + open.price + "\n");
			int openSize = open.size;

			if (tradeOrders.elementAt(i).close.size() > 0) {
				Iterator<TradeRecord> it = tradeOrders.elementAt(i).close.iterator();
				while (it.hasNext()) {
					double realizedProfit = 0;
					TradeRecord t = (TradeRecord) it.next();
					if (Constants.ACTION_BUY.equals(open.action))
						realizedProfit = (t.price - open.price) * t.size;
					else if (Constants.ACTION_SELL.equals(open.action))
						realizedProfit = (open.price - t.price) * t.size;

					sb.append("    " + t.time + " " + t.action + " " + t.size + " " + t.price + "  " + "  Realized Profit: "
							+ formatter.format(realizedProfit) + "\n");
					totalProfit += realizedProfit;
					openSize -= t.size;
				}
			}

			if (openSize > 0) {
				// open has not been closed yet
				double unrealizedProfit = 0;
				if (Constants.ACTION_BUY.equals(open.action))
					unrealizedProfit = (currentPrice - open.price) * openSize;
				else if (Constants.ACTION_SELL.equals(open.action))
					unrealizedProfit = (open.price - currentPrice) * openSize;
				sb.append("Unrealized Profit: " + formatter.format(unrealizedProfit) + "\n");
				totalProfit += unrealizedProfit;
				totalUnrealizedProfit += unrealizedProfit;
			}
		}
		sb.append(symbol + " Loss/Profit: " + formatter.format(totalProfit) + " " + currency + "   " + formatter.format(totalProfit / exchangeRate)
				+ " USD\n");

		totalProfit = totalProfit / exchangeRate;
		totalUnrealizedProfit = totalUnrealizedProfit / exchangeRate;
		totalTrade = size;
		portofolioReport = sb.toString();

	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// Report 2, allow multiple entry
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	protected void CreateTradeRecord(String tradeType, String action) {
		if (MODE == Constants.REAL_MODE)
			return;

		TradeOrder2 to2 = new TradeOrder2(tradeType, action);
		tradeOrder2s.add(to2);
	}

	protected void AddOpenRecord(String time, String action, int posSize, double price) {
		if (MODE == Constants.REAL_MODE)
			return;

		if (trade.recordOpened == false) {
			CreateTradeRecord(trade.type, trade.action);
			trade.recordOpened = true;
		}

		TradeRecord tr = new TradeRecord(time, action, price, posSize);
		if (tradeOrder2s.size() > 0)
			tradeOrder2s.lastElement().open.add(tr);
	}

	public void AddCloseRecord(String time, String action, int posSize, double price) {
		if ((MODE == Constants.REAL_MODE) || (MODE == Constants.SIGNAL_MODE ))
			return;

		TradeRecord tr = new TradeRecord(time, action, price, posSize);
		tradeOrder2s.lastElement().close.add(tr);
	}

	public String report2(double currentPrice) {
		NumberFormat formatter = new DecimalFormat("######.##");
		portofolioReport = "";
		totalUnrealizedProfit = totalProfit = totalTrade = 0;
		StringBuffer sb = new StringBuffer();

		int tradeSize = tradeOrder2s.size();
		if (tradeSize == 0)
			return "";

		sb.append(symbol + " : " + currentPrice + "\n");
		for (int i = 0; i < tradeSize; i++) {
			// TradeRecord open = (TradeRecord)tradeOrders.elementAt(i).open;
			// sb.append( open.tradeType + " " + open.time + " " + open.action +
			// " " + open.size + " " + open.price +"\n");
			double[] lot = new double[10000]; // each lot is 1000, so the total
												// is 10,000,000
			int openSize = 0;
			if (tradeOrder2s.elementAt(i).open.size() > 0) {
				Iterator<TradeRecord> it = tradeOrder2s.elementAt(i).open.iterator();
				while (it.hasNext()) {
					TradeRecord open = (TradeRecord) it.next();
					sb.append(tradeOrder2s.elementAt(i).tradeType + " " + open.time + " " + open.action + " " + open.size + " " + open.price + "\n");
					int size = open.size / 1000;

					for (int j = openSize; j < openSize + size; j++)
						lot[j] = open.price;
					openSize += size;
				}
			}

			int closedSize = 0;
			if (tradeOrder2s.elementAt(i).close.size() > 0) {
				Iterator<TradeRecord> it = tradeOrder2s.elementAt(i).close.iterator();
				while (it.hasNext()) {
					double realizedProfit = 0;
					TradeRecord t = (TradeRecord) it.next();
					int size = t.size / 1000;
					if (Constants.ACTION_BUY.equals(tradeOrder2s.elementAt(i).tradeAction)) {
						double profit = 0;
						for (int j = closedSize; j < closedSize + size; j++) {
							profit += t.price - lot[j];
						}
						realizedProfit = profit * 1000;
						closedSize += size;
					} else if (Constants.ACTION_SELL.equals(tradeOrder2s.elementAt(i).tradeAction)) {
						double profit = 0;
						for (int j = closedSize; j < closedSize + size; j++) {
							profit += lot[j] - t.price;
						}
						realizedProfit = profit * 1000;
						closedSize += size;
					}

					sb.append("    " + t.time + " " + t.action + " " + t.size + " " + t.price + "  " + "  Realized Profit: "
							+ formatter.format(realizedProfit) + "\n");
					totalProfit += realizedProfit;
				}
			}

			if (openSize - closedSize > 0) {
				// open has not been closed yet
				double unrealizedProfit = 0;
				if (Constants.ACTION_BUY.equals(tradeOrder2s.elementAt(i).tradeAction)) {
					for (int j = closedSize; j < openSize; j++) {
						unrealizedProfit += currentPrice - lot[j];
					}

					unrealizedProfit = unrealizedProfit * 1000;
				} else if (Constants.ACTION_SELL.equals(tradeOrder2s.elementAt(i).tradeAction)) {
					// unrealizedProfit = ( open.price - currentPrice ) *
					// openSize;
					for (int j = closedSize; j < openSize; j++) {
						unrealizedProfit += lot[j] - currentPrice;
					}

					unrealizedProfit = unrealizedProfit * 1000;
				}

				sb.append("Unrealized Profit: " + formatter.format(unrealizedProfit) + "\n");
				totalProfit += unrealizedProfit;
				totalUnrealizedProfit += unrealizedProfit;

			}
		}

		sb.append(symbol + " Total Profit: " + formatter.format(totalProfit) + " " + currency + "   " + formatter.format(totalProfit / exchangeRate)
				+ " USD\n");

		totalProfit = totalProfit / exchangeRate;
		totalUnrealizedProfit = totalUnrealizedProfit / exchangeRate;
		portofolioReport = sb.toString();
		totalTrade = tradeSize;

		return portofolioReport;

	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// Algorithms, should not really be here
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/*
	 * public int getDirectionBy20MA( Object[] quotesL ) { double[] ema20L =
	 * Indicator.calculateEMA(quotesL, 20);
	 * 
	 * int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
	 * int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);
	 * 
	 * if ( upPos > downPos ) return Constants.DIRECTION_UP; else if ( downPos >
	 * upPos ) return Constants.DIRECTION_DOWN; else return
	 * Constants.DIRECTION_UNKNOWN;
	 * 
	 * }
	 * 
	 * 
	 * public tao.trading.SlimJim findSlimJimWithMinBars( QuoteData[] quotes,
	 * int lastbar, int slimJimBars, int height ) { tao.trading.SlimJim sj =
	 * calculateSlimJim3( quotes, lastbar, slimJimBars, height );
	 * 
	 * // to see if it is the maximum tao.trading.SlimJim sj1 =
	 * calculateSlimJim3( quotes, lastbar, slimJimBars, height ); while ( sj1 !=
	 * null ) { sj = sj1; slimJimBars++; sj1 = calculateSlimJim3( quotes,
	 * lastbar, slimJimBars, height ); }
	 * 
	 * return sj; }
	 * 
	 * 
	 * public tao.trading.SlimJim calculateSlimJim3( Object[] quotes, int
	 * lastbar, int bars, int height ) { tao.trading.SlimJim sj = new
	 * tao.trading.SlimJim();
	 * 
	 * for ( int i = lastbar-1; i > lastbar-1-bars; i--) { if
	 * ((((QuoteData)quotes[i]).high - ((QuoteData)quotes[i]).low) > height *
	 * PIP_SIZE ) return null;
	 * 
	 * if (((QuoteData)quotes[i]).high > sj.slimJimHigh) { sj.slimJimHigh =
	 * ((QuoteData)quotes[i]).high; sj.slimJimHighPos = i; }
	 * 
	 * if (((QuoteData)quotes[i]).low < sj.slimJimLow) { sj.slimJimLow =
	 * ((QuoteData)quotes[i]).low; sj.slimJimLowPos = i; } }
	 * 
	 * if (sj.slimJimHigh - sj.slimJimLow < height * PIP_SIZE ) // note the
	 * range above is open-close= { //logger.warning(symbol + " " + CHART +
	 * " slim jim detected at " + ((QuoteData)quotes[lastbar]).time);
	 * sj.slimJimStartPos = lastbar-1-bars+1; return sj; }
	 * 
	 * return null;
	 * 
	 * }
	 */

	// abstract public boolean inExitingTime( String time);

	public void saveTradeData() {
		String TRADE_DATA_FILE = OUTPUT_DIR + "ser/" + symbol + ".ser";

		if (MODE != Constants.REAL_MODE)
			return;

		try // open file
		{
			if ((trade != null) /*
								 * && ( trade.status.equals(
								 * Constants.STATUS_PLACED))
								 */) {
				ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(TRADE_DATA_FILE));
				output.writeObject(trade);
				output.close();
			} else {
				File f = new File(TRADE_DATA_FILE);
				if (f.exists())
					f.delete();
			}
		} catch (Exception e) {
			logger.warning("Exception occured during saving " + symbol + " " + CHART + " data " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void saveTradeData(XStream xstream) {
		String TRADE_DATA_FILE = OUTPUT_DIR + "ser/" + symbol + ".xml";

		if (MODE == Constants.TEST_MODE)
			return;

		try // open file
		{
			if (trade != null) {
				String xml = xstream.toXML(trade);
				// Writer out = new BufferedWriter(new
				// FileWriter(TRADE_DATA_FILE));
				Writer out = new OutputStreamWriter(new FileOutputStream(TRADE_DATA_FILE));
				out.write(xml);
				out.close();

			} else {
				File f = new File(TRADE_DATA_FILE);
				if (f.exists())
					f.delete();
			}
		} catch (Exception e) {
			logger.warning("Exception occured during saving " + symbol + " " + CHART + " data " + e.getMessage());
			e.printStackTrace();
		}
	}

	void removeTradeData() {
		String TRADE_DATA_FILE = OUTPUT_DIR + symbol + ".ser";

		if (MODE == Constants.REAL_MODE) {
			try {
				File f = new File(TRADE_DATA_FILE);
				if (f.exists())
					f.delete();
			} catch (Exception e) {
				logger.warning("Exception occured during removing " + TRADE_DATA_FILE + " " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	void loadTradeData() {
		String TRADE_DATA_FILE = OUTPUT_DIR + "ser/" + symbol + ".ser";
		info("load serial file: " + TRADE_DATA_FILE);

		try {
			ObjectInputStream input = new ObjectInputStream(new FileInputStream(TRADE_DATA_FILE));
			trade = (Trade) input.readObject();

			warning(" trade loaded startTime:" + trade.getFirstBreakOutStartTime() + " firstBreakOutTime:" + trade.getFirstBreakOutTime()
					+ " touch20MATime:" + trade.getTouch20MATime() + " entryTime:" + trade.getEntryTime());

			input.close();
		} catch (FileNotFoundException ex) {
			// e.printStackTrace(); ignore excetion as file does not exist is
			// normal
			return; // end of file was reached
		} catch (Exception e) {
			e.printStackTrace();
			warning("Load Trade Data error:" + e.getMessage());
		}
	}

	public void loadTradeData(XStream xstream) {
		String TRADE_DATA_FILE = OUTPUT_DIR + "ser/" + symbol + ".xml";
		info("load serial file: " + TRADE_DATA_FILE);

		StringBuffer xml = new StringBuffer();

		try {
			BufferedReader input = new BufferedReader(new FileReader(TRADE_DATA_FILE));
			String line = null;
			/*
			 * readLine is a bit quirky : it returns the content of a line MINUS
			 * the newline. it returns null only for the END of the stream. it
			 * returns an empty String if two newlines appear in a row.
			 */
			while ((line = input.readLine()) != null) {
				xml.append(line);
			}

			trade = (Trade) xstream.fromXML(xml.toString());

			input.close();

		} catch (FileNotFoundException ex) {
			// e.printStackTrace(); ignore excetion as file does not exist is
			// normal
			return; // end of file was reached
		} catch (Exception e) {
			e.printStackTrace();
			warning("Load Trade Data error:" + e.getMessage());
		}
	}

	protected void enterMarketPosition(Trade t) {
		this.trade = t;

		if (MODE == Constants.SIGNAL_MODE)
			return;

		enterMarketPosition(t.entryPrice, 0);
	}

	protected void enterMarketPosition(double price) {
		if (MODE == Constants.SIGNAL_MODE)
			return;

		enterMarketPosition(price, 0);
	}

	// additional position usually is for saving a trade
	protected void enterMarketPosition(double price, int additionalPosition) {
		QuoteData[] quotesD = getQuoteData(Constants.CHART_DAILY);
		int lastbarD = quotesD.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
		int lastbar5 = quotes5.length - 1;
		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;

		trade.entryPrice = price;

		/*
		 * if ( trade.getCurrentPositionSize() > 0 ) trade.remainingPositionSize
		 * = trade.getCurrentPositionSize();
		 */

		int positionSize = trade.POSITION_SIZE/* - trade.remainingPositionSize*/;
		logger.warning(symbol + " place market " + trade.action + " " + positionSize + " " + price + " at " + quotes15[lastbar15].time);
		trade.orderId = placeMktOrder(trade.action, positionSize + additionalPosition); 

		trade.status = Constants.STATUS_FILLED;

		trade.setEntryTime(quotes15[lastbar15].time);
		trade.entryTime1 = quotes1[lastbar1].time;
		trade.entryTime5 = quotes5[lastbar5].time;
		trade.entryTime15 = quotes15[lastbar15].time;
		trade.entryTime60 = quotes60[lastbar60].time;
		trade.entryTime240 = quotes240[lastbar240].time;
		trade.entryTimeD = quotesD[lastbarD].time;
		trade.remainingPositionSize = positionSize;

		AddOpenRecord(quotes15[lastbar15].time, trade.action, positionSize, trade.entryPrice);

		if (Constants.ACTION_SELL.equals(trade.action)) {
			if (trade.stop == 0)
				trade.stop = adjustPrice(trade.entryPrice + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);

			if (trade.targetPrice == 0)
				trade.targetPrice = adjustPrice(trade.entryPrice - DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
		} else {
			if (trade.stop == 0) // this is default
				trade.stop = adjustPrice(trade.entryPrice - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);

			if (trade.targetPrice == 0)
				trade.targetPrice = adjustPrice(trade.entryPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_UP);
		}

		// String oca = new Long(new Date().getTime()).toString();
		logger.warning(symbol + " place stop order " + trade.stop + " positionSize " + trade.POSITION_SIZE);
		trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
				positionSize, null);// new
		logger.warning(symbol + " stop order " + trade.stopId + " " + trade.stop + " placed");

		// temporilary disable target order
		/*
		if (trade.targetPrice != 0) {
			if (trade.targetPos == 0)
				trade.targetPos = trade.POSITION_SIZE;
			logger.warning(symbol + " place target order " + trade.targetPrice + " " + trade.targetPos);
			trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY,
					trade.targetPrice, trade.targetPos, null);
			logger.warning(symbol + " target order " + trade.targetPrice + " " + trade.targetPos + " placed");
		}*/

		// clean all the limit orders
		for (int i = 0; i < trade.TOTAL_LIMITS; i++) {
			if (trade.limits[i] != null) {
				if (MODE == Constants.SIGNAL_MODE)
					trade.limits[i].orderId = 0;
				else if (MODE == Constants.REAL_MODE) {
					if (trade.limits[i].orderId != 0)
						cancelOrder(trade.limits[i].orderId);
				}
			}
		}

		return;
	}

	// add addtional position, do not change original price and time ( mostly a
	// average up )
	public void enterMarketPositionAdditional(double price, int additionalPosition) {
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		logger.warning(symbol + " " + CHART + " place additional market " + trade.action + " " + additionalPosition + " " + price + " at "
				+ quotes[lastbar].time);
		trade.orderId = placeMktOrder(trade.action, additionalPosition); // additional
																			// positions
																			// are
																			// to
																			// cover
																			// reverse
																			// positions

		trade.status = Constants.STATUS_PLACED;
		trade.POSITION_SIZE += additionalPosition;
		trade.remainingPositionSize += additionalPosition;
		AddOpenRecord(quotes[lastbar].time, trade.action, additionalPosition, price);

		cancelStop();
		// keep original stop
		// String oca = new Long(new Date().getTime()).toString();
		trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
				trade.remainingPositionSize, null);// new
		logger.warning(symbol + " " + CHART + " stop order " + trade.stopId + " " + trade.stop + " placed");

		if ((trade.targetId != 0) && (trade.targetPrice != 0)) {
			cancelOrder(trade.targetId);
			// keep original target
			trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY,
					trade.targetPrice, trade.remainingPositionSize, null);
		}

		return;
	}

	
	public int enterMarketPositionMulti(double price, int positionSize) {
		if ((MODE == Constants.REAL_MODE) && (manual_override == true)) {
			logger.warning(symbol + " manual override is enable, order is not placed");
			return Constants.ERROR;
		}

		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		trade.entryPrice = price;

		logger.warning(symbol + " place market " + trade.action + " " + positionSize + " " + price + " at " + quotes[lastbar].time);
		trade.orderId = placeMktOrder(trade.action, positionSize); // additional
																	// positions
																	// are to
																	// cover
																	// reverse
																	// positions

		trade.status = Constants.STATUS_FILLED;

		trade.setEntryTime(quotes[lastbar].time);
		trade.addFilledPosition(price, positionSize, quotes[lastbar].time);
		trade.remainingPositionSize += positionSize;

		AddOpenRecord(quotes[lastbar].time, trade.action, positionSize, trade.entryPrice);

		trade.stop = trade.getStopPrice();
		trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
				trade.remainingPositionSize, null);

		return trade.orderId;
	}

	public int enterIncrementalMarketPosition(double price, int positionSize, String time) {
		if ((MODE == Constants.REAL_MODE) && (manual_override == true)) {
			logger.warning(symbol + " manual override is enable, order is not placed");
			return Constants.ERROR;
		}

		trade.entryPrice = price;

		logger.warning(symbol + " place market " + trade.action + " " + positionSize + " " + price + " at " + time);
		trade.orderId = placeMktOrder(trade.action, positionSize); // additional
																	// positions
																	// are to
																	// cover
																	// reverse
																	// positions

		trade.status = Constants.STATUS_PARTIAL_FILLED;

		trade.setEntryTime(time);
		trade.addFilledPosition(price, positionSize, time);
		trade.remainingPositionSize += positionSize;

		AddOpenRecord(time, trade.action, positionSize, trade.entryPrice);

		if (trade.stop != 0) {
			cancelStop();
			trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
					trade.remainingPositionSize, null);
		}

		return trade.orderId;
	}

	public void enterLimitPosition1(double price, int size) {
		enterLimitPosition1(price, size, 0);
	}

	public void enterLimitPosition1(double price, int size, double stop) {
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		if (trade.limitId1 != 0)
			cancelOrder(trade.limitId1);
		trade.limitPrice1 = price;
		trade.limitPos1 = size;
		trade.limitId1 = placeLmtOrder(trade.action, trade.limitPrice1, trade.limitPos1, null);

		trade.limit1Stop = stop;
		if (Constants.ACTION_SELL.equals(trade.action)) {
			if (stop == 0)
				trade.limit1Stop = adjustPrice(trade.limitPrice1 + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);

			// trade.targetPrice = adjustPrice(trade.limitPrice1 -
			// DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
		} else {
			if (stop == 0)
				trade.limit1Stop = adjustPrice(trade.limitPrice1 - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);

			// trade.targetPrice = adjustPrice(trade.limitPrice1 +
			// DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
		}

		trade.limitPos1Filled = 0; // need to reset otherwise if there is a
									// previous one the order fill won't trigger
		logger.warning(symbol + " " + CHART + " limit order1 " + trade.action + " placed at " + trade.limitPrice1 + " stop is " + trade.limit1Stop);

		// trade.limit1Placed = true;
		// trade.limit1PlacedTime = quotes[lastbar].time;
		trade.status = Constants.STATUS_PLACED;
	}

	void enterLimitPosition2(double price, int size) {
		enterLimitPosition2(price, size, 0);
	}

	void enterLimitPosition2(double price, int size, double stop) {
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		if (trade.limitId2 != 0)
			cancelOrder(trade.limitId2);
		trade.limitPrice2 = price;
		trade.limitPos2 = size;
		trade.limitId2 = placeLmtOrder(trade.action, trade.limitPrice2, trade.limitPos2, null);

		trade.limit2Stop = stop;
		if (Constants.ACTION_SELL.equals(trade.action)) {
			if (stop == 0)
				trade.limit2Stop = adjustPrice(trade.limitPrice2 + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);

			trade.targetPrice = adjustPrice(trade.limitPrice2 - DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
		} else {
			if (stop == 0)
				trade.limit2Stop = adjustPrice(trade.limitPrice2 - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);

			trade.targetPrice = adjustPrice(trade.limitPrice2 + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
		}

		trade.limitPos2Filled = 0; // need to reset otherwise if there is a
									// previous one the order fill won't trigger
		logger.warning(symbol + " " + CHART + " limit order2 " + trade.action + " placed at " + trade.limitPrice2 + " stop is " + trade.limit2Stop);

		// trade.limit1Placed = true;
		// trade.limit1PlacedTime = quotes[lastbar].time;
		trade.status = Constants.STATUS_PLACED;

	}

	void enterStopLimitPosition(double stopPrice, double limitPrice) {
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		trade.limitPrice1 = limitPrice;
		trade.limitPos1 = trade.POSITION_SIZE;
		trade.limitId1 = placeStopLimitOrder(trade.action, stopPrice, limitPrice, trade.POSITION_SIZE, null);

		trade.limit1Placed = true;
		trade.limit1PlacedTime = quotes[lastbar].time;
		trade.status = Constants.STATUS_OPEN;
	}

	protected void enterStopMarketPosition(String goodUntil) {
		trade.stopMarketId = placeStopOrder(trade.action, trade.stopMarketStopPrice, trade.POSITION_SIZE, null, goodUntil);
	}

	void labelPositions(QuoteData[] quotes) {
		for (int i = 0; i < quotes.length; i++)
			quotes[i].pos = i;
	}

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// trend start with first 24 bars on the direction;
	// trendQualifyPeriod = 48
	// until I see a 24 consective bar on the other side, I shall consider the
	// trend has NOT changed
	//
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////

	BigTrend determineBigTrend(QuoteData[] quotes) {
		int trendQualifyPeriod = 48;

		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		BigTrend bigTrend = new BigTrend();

		int prevMAConsectiveUp = 0, prevMAConsectiveCrossUp = 0, prevMAConsectiveDown = 0, prevMAConsectiveCrossDown = 0;
		int downDuration = 0, upDuration = 0;
		int start = 0;

		int end = lastbar;
		while (end >= 50) {
			if ((ema20[end] > ema50[end]) && (ema20[end - 1] > ema50[end - 1])) {
				start = end - 2;
				while ((start >= 50) && !((ema20[start] < ema50[start]) && (ema20[start - 1] < ema50[start - 1]))) {
					start--;
				}

				if (start < 50) {
					Push p = new Push(Constants.DIRECTION_UP, 0, end);
					bigTrend.pushes.insertElementAt(p, 0);
					bigTrend.start = 0;
					break;
				} else {
					Push p = new Push(Constants.DIRECTION_UP, start + 1, end);
					bigTrend.pushes.insertElementAt(p, 0);
					end = start;

				}
			} else if ((ema20[end] < ema50[end]) && (ema20[end - 1] < ema50[end - 1])) {
				start = end - 2;
				while ((start >= 50) && !((ema20[start] > ema50[start]) && (ema20[start - 1] > ema50[start - 1]))) {
					start--;
				}

				if (start < 50) {
					Push p = new Push(Constants.DIRECTION_DOWN, 0, end);
					bigTrend.pushes.insertElementAt(p, 0);
					bigTrend.start = 0;
					break;
				} else {
					Push p = new Push(Constants.DIRECTION_DOWN, start + 1, end);
					bigTrend.pushes.insertElementAt(p, 0);
					end = start;
				}

			} else
				end--;
		}

		// find the last push that > trendQualifyPeriod

		int lastPushIndex = bigTrend.pushes.size() - 1;
		bigTrend.lastPushIndex = lastPushIndex;

		Push lastTrendyPush = null;
		Push lastPush = bigTrend.pushes.elementAt(lastPushIndex);

		for (int i = lastPushIndex; i >= 0; i--) {
			Push p = bigTrend.pushes.elementAt(i);
			if (p.duration > trendQualifyPeriod) {
				lastTrendyPush = p;
				bigTrend.lastTrendyPushIndex = i;
				break;
			}
		}

		// Determine trend by 20/50 length
		/*
		 * int trendDirection = 0; int trendDuration = 0; int lastTrendyIndex48
		 * = 0; // trend last at least 48 hours for ( int i = 1; i <=
		 * lastPushIndex; i++) { if ( bigTrend.pushes.elementAt(i).duration >
		 * bigTrend.pushes.elementAt(i-1).duration) { trendDirection =
		 * bigTrend.pushes.elementAt(i).direction; trendDuration =
		 * bigTrend.pushes.elementAt(i).duration;
		 * 
		 * if ( trendDuration >= 48 ) lastTrendyIndex48 = i; } else {
		 * trendDirection = bigTrend.pushes.elementAt(i-1).direction;
		 * trendDuration = bigTrend.pushes.elementAt(i-1).duration;
		 * 
		 * if ( trendDuration >= 48 ) lastTrendyIndex48 = i-1; } }
		 */

		if ((bigTrend.lastTrendyPushIndex == lastPushIndex) || (bigTrend.lastTrendyPushIndex == (lastPushIndex - 1)))
			bigTrend.isTrendy = true;

		if ((lastTrendyPush != null) && (lastTrendyPush.direction == Constants.DIRECTION_UP)) {
			bigTrend.direction = Constants.DIRT_UP;

			// 1. check if last two add up made a difference
			/*
			 * int lastDownPushIndex =
			 * bigTrend.getLastPushIndex(Constants.DIRECTION_DOWN); if (
			 * lastDownPushIndex >= lastTrendPushIndex + 3 ) { if ((
			 * bigTrend.pushes.elementAt(
			 * lastDownPushIndex).getPushLow(quotes).low <
			 * bigTrend.pushes.elementAt( lastDownPushIndex - 2
			 * ).getPushLow(quotes).low) && ( bigTrend.pushes.elementAt(
			 * lastDownPushIndex-1).getPushHigh(quotes).high <
			 * bigTrend.pushes.elementAt( lastDownPushIndex - 3
			 * ).getPushHigh(quotes).high)) { bigTrend.direction =
			 * Constants.DIRT_DOWN_SEC_2; return bigTrend; } }
			 */

			if (lastPushIndex >= bigTrend.lastTrendyPushIndex + 3) {
				int lastTwo = (bigTrend.pushes.elementAt(lastPushIndex).pushEnd - bigTrend.pushes.elementAt(lastPushIndex).pushStart + 1)
						+ (bigTrend.pushes.elementAt(lastPushIndex - 2).pushEnd - bigTrend.pushes.elementAt(lastPushIndex - 2).pushStart + 1);
				int lastTwoPre = (bigTrend.pushes.elementAt(lastPushIndex - 1).pushEnd - bigTrend.pushes.elementAt(lastPushIndex - 1).pushStart + 1)
						+ (bigTrend.pushes.elementAt(lastPushIndex - 3).pushEnd - bigTrend.pushes.elementAt(lastPushIndex - 3).pushStart + 1);
				if (bigTrend.pushes.elementAt(lastPushIndex).direction == Constants.DIRECTION_DOWN) {
					if ((lastTwo > 48) || ((lastTwo > 36) && (lastTwo > lastTwoPre))) {
						bigTrend.direction = Constants.DIRT_DOWN_SEC_2;
						return bigTrend;
					}
				} else {
					if ((lastTwoPre > 48) || ((lastTwoPre > 36) && (lastTwoPre > lastTwo))) {
						bigTrend.direction = Constants.DIRT_DOWN_SEC_2;
						return bigTrend;
					}
				}
			}

			// 2. check to see if there is a large price change
			QuoteData lPoint, hPoint;
			int ind = lastPushIndex;
			if (lastPush.direction == Constants.DIRECTION_DOWN)
				ind = lastPushIndex - 1;

			// Determin the latest highest point
			hPoint = bigTrend.pushes.elementAt(ind).getPushHigh(quotes);
			if ((ind - 2) >= 0) {
				Push prevHighPush = bigTrend.pushes.elementAt(ind - 2);
				Push prevLowPush = bigTrend.pushes.elementAt(ind - 1);
				QuoteData prevHighPushHigh = prevHighPush.getPushHigh(quotes);
				if ((prevHighPushHigh.high > hPoint.high) && (hPoint.pos - prevHighPushHigh.pos < trendQualifyPeriod)) {
					hPoint = prevHighPushHigh;
				}
			}

			lPoint = Utility.getLow(quotes, hPoint.pos + 1, lastbar);
			double totalAvgBarSize = BarUtil.averageBarSize(quotes);

			if (hPoint.pos != lastbar) {
				// if (((hPoint.high - lPoint.low) > 8 * totalAvgBarSize ) && (
				// lastbar - lPoint.pos < trendQualifyPeriod ))
				if (((hPoint.high - lPoint.low) > 8 * totalAvgBarSize) && (lPoint.pos > lastTrendyPush.pushStart)) {
					bigTrend.direction = Constants.DIRT_DOWN_REVERSAL;
					// bigTrend.start =
					// ((Push)bigTrend.pushes.elementAt(lastPushDownIndex)).pushStart;
					return bigTrend;
				}
			}

			// see if we can find a relatively strong spike up
			Push push = Utility.findLargestConsectiveDownBars(quotes, hPoint.pos, lastbar);
			// Push push = Utility.findLargestConsectiveDownBars(quotes,
			// bigTrend.pushes.elementAt(ind).pushStart, lastbar);
			if (push != null) {
				double totalPushSize = quotes[push.pushStart].open - quotes[push.pushEnd].close;
				int numOfBar = push.pushEnd - push.pushStart + 1;
				double avgPushSize = totalPushSize / numOfBar;

				if ((((numOfBar == 2) && (avgPushSize > 1.2 * totalAvgBarSize)) || ((numOfBar == 3) && (avgPushSize > totalAvgBarSize))
						|| ((numOfBar >= 4) && (avgPushSize > 0.9 * totalAvgBarSize)) || ((numOfBar >= 5) && (avgPushSize > 0.8 * totalAvgBarSize)))) {
					boolean brokenLowerLow = false;

					for (int j = lastbar; (j > bigTrend.pushes.elementAt(ind).pushStart + 3) && (brokenLowerLow == false); j--) {
						PushList pushList = Pattern.findPast2Highs2(quotes, bigTrend.pushes.elementAt(ind).pushStart, j);
						if ((pushList.phls != null) && (pushList.phls.length > 0)) {
							for (int k = 0; (k < pushList.phls.length) && (brokenLowerLow == false); k++) {
								PushHighLow p = pushList.phls[pushList.phls.length - 1];
								for (int l = p.curPos + 1; l < lastbar; l++) {
									if ((quotes[l].low < p.pullBack.low) && (push.pushStart > p.pullBack.pos)) {
										brokenLowerLow = true;
										break;
									}
								}
							}
						}
					}

					for (int k = push.pushEnd + 1; k <= lastbar; k++) {
						if (quotes[k].low < quotes[bigTrend.pushes.elementAt(ind).pushStart].low) {
							brokenLowerLow = true;
							break;
						}
					}

					if (brokenLowerLow == true) {
						bigTrend.direction = Constants.DIRT_DOWN_REVERSAL;
						return bigTrend;
					}
				}
			}
		} else if ((lastTrendyPush != null) && (lastTrendyPush.direction == Constants.DIRECTION_DOWN)) {
			bigTrend.direction = Constants.DIRT_DOWN;

			// 1. check if last two add up made a difference
			/*
			 * int lastUpPushIndex =
			 * bigTrend.getLastPushIndex(Constants.DIRECTION_UP); if (
			 * lastUpPushIndex >= lastTrendPushIndex + 3 ) { if ((
			 * bigTrend.pushes.elementAt(
			 * lastUpPushIndex).getPushHigh(quotes).high >
			 * bigTrend.pushes.elementAt( lastUpPushIndex - 2
			 * ).getPushHigh(quotes).high) && ( bigTrend.pushes.elementAt(
			 * lastUpPushIndex-1).getPushLow(quotes).low >
			 * bigTrend.pushes.elementAt( lastUpPushIndex - 3
			 * ).getPushLow(quotes).low)) { bigTrend.direction =
			 * Constants.DIRT_UP_SEC_2; return bigTrend; } }
			 */

			if (lastPushIndex >= bigTrend.lastTrendyPushIndex + 3) {
				int lastTwo = (bigTrend.pushes.elementAt(lastPushIndex).pushEnd - bigTrend.pushes.elementAt(lastPushIndex).pushStart + 1)
						+ (bigTrend.pushes.elementAt(lastPushIndex - 2).pushEnd - bigTrend.pushes.elementAt(lastPushIndex - 2).pushStart + 1);
				int lastTwoPre = (bigTrend.pushes.elementAt(lastPushIndex - 1).pushEnd - bigTrend.pushes.elementAt(lastPushIndex - 1).pushStart + 1)
						+ (bigTrend.pushes.elementAt(lastPushIndex - 3).pushEnd - bigTrend.pushes.elementAt(lastPushIndex - 3).pushStart + 1);
				if (bigTrend.pushes.elementAt(lastPushIndex).direction == Constants.DIRECTION_UP) {
					if ((lastTwo > 48) || ((lastTwo > 36) && (lastTwo > lastTwoPre))) {
						bigTrend.direction = Constants.DIRT_UP_SEC_2;
						return bigTrend;
					}
				} else {
					if ((lastTwoPre > 48) || ((lastTwoPre > 36) && (lastTwoPre > lastTwo))) {
						bigTrend.direction = Constants.DIRT_UP_SEC_2;
						return bigTrend;
					}
				}
			}

			// 2. check to see if there is a large price change
			QuoteData lPoint, hPoint;
			int ind = lastPushIndex;
			if (lastPush.direction == Constants.DIRECTION_UP)
				ind = lastPushIndex - 1;

			lPoint = bigTrend.pushes.elementAt(ind).getPushLow(quotes);
			if ((ind - 2) >= 0) {
				Push prevLowPush = bigTrend.pushes.elementAt(ind - 2);
				Push prevHighPush = bigTrend.pushes.elementAt(ind - 1);
				QuoteData prevLowPushLow = prevLowPush.getPushLow(quotes);
				if ((prevLowPushLow.low < lPoint.low) && (lPoint.pos - prevLowPushLow.pos < trendQualifyPeriod))
				// if (( prevLowPushLow.low < lPoint.low) && (
				// prevHighPush.pushEnd - prevHighPush.pushStart <
				// trendQualifyPeriod ))
				{
					lPoint = prevLowPushLow;
				}
			}

			hPoint = Utility.getHigh(quotes, lPoint.pos + 1, lastbar);
			double totalAvgBarSize = BarUtil.averageBarSize(quotes);

			if (lPoint.pos != lastbar) {
				// if ((( hPoint.high - lPoint.low) > 8 * totalAvgBarSize ) && (
				// lastbar - hPoint.pos < trendQualifyPeriod ))
				if (((hPoint.high - lPoint.low) > 8 * totalAvgBarSize) && (hPoint.pos > lastTrendyPush.pushStart)) {
					bigTrend.direction = Constants.DIRT_UP_REVERSAL;
					// bigTrend.start =
					// ((Push)bigTrend.pushes.elementAt(lastPushDownIndex)).pushStart;
					return bigTrend;
				}
			}

			// see if we can find a relatively strong spike up
			// Push push = Utility.findLargestConsectiveUpBars(quotes,
			// bigTrend.pushes.elementAt(ind).pushStart, lastbar);
			Push push = Utility.findLargestConsectiveUpBars(quotes, lPoint.pos, lastbar);
			if (push != null) {
				double totalPushSize = quotes[push.pushEnd].close - quotes[push.pushStart].open;
				int numOfBar = push.pushEnd - push.pushStart + 1;
				double avgPushSize = totalPushSize / numOfBar;

				if ((((numOfBar == 2) && (avgPushSize > 1.2 * totalAvgBarSize)) || ((numOfBar == 3) && (avgPushSize > totalAvgBarSize))
						|| ((numOfBar >= 4) && (avgPushSize > 0.9 * totalAvgBarSize)) || ((numOfBar >= 5) && (avgPushSize > 0.8 * totalAvgBarSize)))) {
					boolean brokenHigherHigh = false;

					for (int j = lastbar; (j > bigTrend.pushes.elementAt(ind).pushStart + 3) && (brokenHigherHigh == false); j--) {
						PushList pushList = Pattern.findPast2Lows2(quotes, bigTrend.pushes.elementAt(ind).pushStart, j);
						if ((pushList.phls != null) && (pushList.phls.length > 0)) {
							PushHighLow p = pushList.phls[pushList.phls.length - 1];
							for (int l = p.curPos + 1; l < lastbar; l++) {
								if ((quotes[l].high > p.pullBack.high) && (push.pushStart > p.pullBack.pos)) {
									brokenHigherHigh = true;
									break;
								}
							}
							break;
						}
					}

					for (int k = push.pushEnd + 1; k <= lastbar; k++) {
						if (quotes[k].high > quotes[bigTrend.pushes.elementAt(ind).pushStart].high) {
							brokenHigherHigh = true;
							break;
						}
					}

					if (brokenHigherHigh == true) {
						bigTrend.direction = Constants.DIRT_UP_REVERSAL;
						return bigTrend;
					}
				}
			}

		}

		// calculate support resistence
		/*
		 * if ( lastPush.direction == Constants.DIRECTION_UP ) { if (
		 * lastPushIndex - 1 >= 0 ) { bigTrend.resistence1 =
		 * bigTrend.pushes.elementAt(lastPushIndex).getPushHigh(quotes);
		 * bigTrend.support1 =
		 * bigTrend.pushes.elementAt(lastPushIndex-1).getPushLow(quotes); }
		 * 
		 * if ( lastPushIndex - 3 >= 0 ) { if (
		 * bigTrend.pushes.elementAt(lastPushIndex-2).getPushHigh(quotes).high >
		 * bigTrend.resistence1.high ) bigTrend.resistence1 =
		 * bigTrend.pushes.elementAt(lastPushIndex-2).getPushHigh(quotes);
		 * 
		 * if (
		 * bigTrend.pushes.elementAt(lastPushIndex-3).getPushLow(quotes).low <
		 * bigTrend.support1.low ) bigTrend.support1 =
		 * bigTrend.pushes.elementAt(lastPushIndex-3).getPushLow(quotes); } }
		 * else { if ( lastPushIndex - 1 >= 0 ) { bigTrend.support1 =
		 * bigTrend.pushes.elementAt(lastPushIndex).getPushLow(quotes);
		 * bigTrend.resistence1 =
		 * bigTrend.pushes.elementAt(lastPushIndex-1).getPushHigh(quotes); }
		 * 
		 * if ( lastPushIndex - 3 >= 0 ) { if (
		 * bigTrend.pushes.elementAt(lastPushIndex-2).getPushLow(quotes).low <
		 * bigTrend.support1.low ) bigTrend.support1 =
		 * bigTrend.pushes.elementAt(lastPushIndex-2).getPushLow(quotes);
		 * 
		 * if (
		 * bigTrend.pushes.elementAt(lastPushIndex-3).getPushHigh(quotes).high >
		 * bigTrend.resistence1.high ) bigTrend.resistence1 =
		 * bigTrend.pushes.elementAt(lastPushIndex-3).getPushHigh(quotes); }
		 * 
		 * }
		 */

		return bigTrend;
	}

	BigTrend determineBigTrend2(QuoteData[] quotes) {
		// this is to be expected on 240 min chart
		int lastbar = quotes.length - 1;
		double[] ema5 = Indicator.calculateEMA(quotes, 5);
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		BigTrend bigTrend = new BigTrend();
		bigTrend.direction = Constants.DIRT_UNKNOWN;
		int trendQualifyPeriod = 12;

		/*
		 * int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20,
		 * lastbar, 2); int lastDownPos =
		 * Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2);
		 * 
		 * int trendStartPos = -1; if ( lastUpPos > lastDownPos) {
		 * bigTrend.direction = Constants.DIRT_UP;
		 * 
		 * // a trend is 6 consective up on the other side or 12 consecitve up
		 * or on the other side trendStartPos =
		 * Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 6);
		 * 
		 * if ( trendStartPos == Constants.NOT_FOUND ) trendStartPos =
		 * Pattern.findLastPriceConsectiveBelowOrAtMA(quotes, ema20, lastbar,
		 * 12);
		 * 
		 * 
		 * } else if ( lastDownPos > lastUpPos) { bigTrend.direction =
		 * Constants.DIRT_DOWN;
		 * 
		 * // a trend is 6 consective up on the other side or 12 consecitve up
		 * or on the other side trendStartPos =
		 * Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 6);
		 * 
		 * //if ( trendStartPos == Constants.NOT_FOUND ) trendStartPos =
		 * Pattern.findLastPriceConsectiveAboveOrAtMA(quotes, ema20, lastbar,
		 * 12); }
		 */

		int prevMAConsectiveUp = 0, prevMAConsectiveCrossUp = 0, prevMAConsectiveDown = 0, prevMAConsectiveCrossDown = 0;
		int downDuration = 0, upDuration = 0;
		int start = 0;

		// calculate the wave
		int end = lastbar;
		while (end >= 20) {
			if ((ema5[end] > ema20[end]) && (ema5[end - 1] > ema20[end - 1])) {
				start = end - 2;
				while ((start >= 20) && !((ema5[start] < ema20[start]) && (ema5[start - 1] < ema20[start - 1]))) {
					start--;
				}

				Push p = new Push(Constants.DIRECTION_UP, start + 1, end);
				bigTrend.pushes.insertElementAt(p, 0);
				end = start;

			} else if ((ema5[end] < ema20[end]) && (ema5[end - 1] < ema20[end - 1])) {
				start = end - 2;
				while ((start >= 20) && !((ema5[start] > ema20[start]) && (ema5[start - 1] > ema20[start - 1]))) {
					start--;
				}

				Push p = new Push(Constants.DIRECTION_DOWN, start + 1, end);
				bigTrend.pushes.insertElementAt(p, 0);
				end = start;

			} else
				end--;
		}

		int pushSize = bigTrend.pushes.size();
		if (pushSize < 1)
			return bigTrend;

		Push lastPush = bigTrend.pushes.lastElement();
		int lastPushInd = bigTrend.pushes.size() - 1;

		// Rule 1: find the last push that > trendQualifyPeriod
		if (lastPush.duration >= trendQualifyPeriod) {
			bigTrend.lastTrendyPushIndex = pushSize - 1;
			if (lastPush.direction == Constants.DIRECTION_UP) {
				bigTrend.direction = Constants.DIRT_UP;
				if (lastPushInd > 1)
					bigTrend.resistance = Utility.getHigh(quotes, bigTrend.pushes.elementAt(lastPushInd - 1).pushStart,
							bigTrend.pushes.elementAt(lastPushInd).pushEnd);
				else
					bigTrend.resistance = Utility.getHigh(quotes, 0, bigTrend.pushes.elementAt(lastPushInd).pushEnd);
			} else {
				bigTrend.direction = Constants.DIRT_DOWN;
				if (lastPushInd > 1)
					bigTrend.support = Utility.getLow(quotes, bigTrend.pushes.elementAt(lastPushInd - 1).pushStart,
							bigTrend.pushes.elementAt(lastPushInd).pushEnd);
				else
					bigTrend.support = Utility.getLow(quotes, 0, bigTrend.pushes.elementAt(lastPushInd).pushEnd);
			}

		}
		// if the last push is < trendQualifyPeriod, then if the previous one is
		// > trendQualifyPeriod, the direction is the pevious one
		else if ((pushSize >= 2) && (bigTrend.pushes.elementAt(pushSize - 2).duration >= trendQualifyPeriod)) {
			bigTrend.lastTrendyPushIndex = pushSize - 2;
			if (bigTrend.pushes.elementAt(pushSize - 2).direction == Constants.DIRECTION_UP) {
				bigTrend.direction = Constants.DIRT_UP;
				bigTrend.resistance = Utility.getHigh(quotes, bigTrend.pushes.elementAt(pushSize - 2).pushStart, lastbar - 1);

				// if there is a discrption on the price, trend might change
				MATouch lastTouch = MABreakOutAndTouch.findLastMATouchFromAbove(quotes, ema20, lastbar);
				// MATouch lastTouch = Pattern.findLastMATouchFromAbove(quotes,
				// ema20, bigTrend.pushes.elementAt(pushSize-2).pushEnd-1);
				if ((lastTouch != null) && (lastTouch.touchEnd < lastbar)) {
					QuoteData latestLow = Utility.getLow(quotes, lastTouch.touchEnd + 1, lastbar);
					if (latestLow.low < (lastTouch.low.low - FIXED_STOP * PIP_SIZE)) {
						bigTrend.direction = Constants.DIRT_DOWN_REVERSAL;
						return bigTrend;
					}
				}

			} else {
				bigTrend.direction = Constants.DIRT_DOWN;
				bigTrend.support = Utility.getLow(quotes, bigTrend.pushes.elementAt(pushSize - 2).pushStart, lastbar - 1);

				MATouch lastTouch = MABreakOutAndTouch.findLastMATouchFromBelow(quotes, ema20, lastbar);
				// MATouch lastTouch = Pattern.findLastMATouchFromBelow(quotes,
				// ema20, bigTrend.pushes.elementAt(pushSize-2).pushEnd-1);
				if ((lastTouch != null) && (lastTouch.touchEnd < lastbar)) {
					QuoteData latestHigh = Utility.getHigh(quotes, lastTouch.touchEnd + 1, lastbar);
					if (latestHigh.high > (lastTouch.high.high + FIXED_STOP * PIP_SIZE)) {
						bigTrend.direction = Constants.DIRT_UP_REVERSAL;
						return bigTrend;
					}
				}
			}
		}
		// if the last two all < trendQualifyPeriod, and the last third one is >
		// trendQualifyPeriod, then the direction is the last third, assuming it
		// will continue
		else if ((pushSize >= 3) && (bigTrend.pushes.elementAt(pushSize - 3).duration >= trendQualifyPeriod)) {
			// we assume this is the resume of the trend although the last push
			// has not reqch the trend qualifier
			if (bigTrend.pushes.elementAt(pushSize - 3).direction == Constants.DIRECTION_UP) {
				bigTrend.direction = Constants.DIRT_UP;
				bigTrend.resistance = Utility.getHigh(quotes, bigTrend.pushes.elementAt(pushSize - 3).pushStart, lastbar - 1);
			} else {
				bigTrend.direction = Constants.DIRT_DOWN;
				bigTrend.support = Utility.getLow(quotes, bigTrend.pushes.elementAt(pushSize - 3).pushStart, lastbar - 1);
			}

		}

		// Rule 2: if a first push followed by a diverge, and then the second
		// push is significantly less than the first push, then the trend is
		// about to change
		if ((lastPush.duration < trendQualifyPeriod) && (pushSize >= 4)) {
			if ((bigTrend.pushes.elementAt(lastPushInd - 1).duration < bigTrend.pushes.elementAt(lastPushInd - 3).duration * 0.8)
					&& (bigTrend.pushes.elementAt(lastPushInd - 3).duration > 1.2 * trendQualifyPeriod)) {
				if (bigTrend.pushes.elementAt(lastPushInd - 1).direction == Constants.DIRECTION_UP) {
					bigTrend.direction = Constants.DIRT_DOWN_SEC_2;
				} else {
					bigTrend.direction = Constants.DIRT_UP_SEC_2;
				}
			}

		}

		return bigTrend;
	}

	BigTrend determineBigTrend_v3(QuoteData[] quotes) {
		int TREND_QUALIFY_PERIOD = 48;

		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		BigTrend bigTrend = new BigTrend();

		// int prevMAConsectiveUp = 0, prevMAConsectiveCrossUp = 0,
		// prevMAConsectiveDown = 0, prevMAConsectiveCrossDown = 0;
		// int downDuration = 0, upDuration = 0;
		int start = 0;

		// calculate UPs and DOWNs
		int end = lastbar;
		while (end >= 50) {
			if ((ema20[end] > ema50[end]) && (ema20[end - 1] > ema50[end - 1])) {
				start = end - 2;
				while ((start >= 50) && !((ema20[start] < ema50[start]) && (ema20[start - 1] < ema50[start - 1]))) {
					start--;
				}

				if (start < 50) {
					Push p = new Push(Constants.DIRT_UP, 0, end);
					bigTrend.pushes.insertElementAt(p, 0);
					bigTrend.start = 0;
					break;
				} else {
					Push p = new Push(Constants.DIRT_UP, start + 1, end);
					bigTrend.pushes.insertElementAt(p, 0);
					end = start;

				}
			} else if ((ema20[end] < ema50[end]) && (ema20[end - 1] < ema50[end - 1])) {
				start = end - 2;
				while ((start >= 50) && !((ema20[start] > ema50[start]) && (ema20[start - 1] > ema50[start - 1]))) {
					start--;
				}

				if (start < 50) {
					Push p = new Push(Constants.DIRT_DOWN, 0, end);
					bigTrend.pushes.insertElementAt(p, 0);
					bigTrend.start = 0;
					break;
				} else {
					Push p = new Push(Constants.DIRT_DOWN, start + 1, end);
					bigTrend.pushes.insertElementAt(p, 0);
					end = start;
				}

			} else
				end--;
		}

		// find the last push that > trendQualifyPeriod
		Push lastTrendyPush = null;
		int lastPushIndex = bigTrend.pushes.size() - 1;
		bigTrend.lastPushIndex = lastPushIndex;
		Push lastPush = bigTrend.pushes.elementAt(lastPushIndex);

		for (int i = lastPushIndex; i >= 0; i--) {
			Push p = bigTrend.pushes.elementAt(i);
			if ((p.duration > TREND_QUALIFY_PERIOD) && (bigTrend.lastTrendyPushIndex == 0)) {
				lastTrendyPush = p;
				bigTrend.lastTrendyPushIndex = i;
			}

			if ((p.duration > 2 * TREND_QUALIFY_PERIOD) && (bigTrend.lastReallyTrendyPushIndex == 0)) {
				bigTrend.lastReallyTrendyPushIndex = i;
			}
		}

		// if (( bigTrend.lastTrendPushIndex == lastPushIndex ) || (
		// bigTrend.lastTrendPushIndex == (lastPushIndex-1)))
		// bigTrend.isTrendy = true;

		// Rule 1, if there are more than 2 small contract
		if (bigTrend.lastTrendyPushIndex != 0) {
			if (bigTrend.lastTrendyPushIndex == lastPushIndex) {
				// it is on that trend,
				bigTrend.direction = bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt;
			} else if (bigTrend.lastTrendyPushIndex == (lastPushIndex - 1)) {
				// first first reversal breakout
				/*
				 * if (( bigTrend.lastReallyTrendyPushIndex != 0 ) &&
				 * !bigTrend.pushes
				 * .elementAt(bigTrend.lastTrendyPushIndex).dirt.
				 * equals(bigTrend.
				 * pushes.elementAt(bigTrend.lastReallyTrendyPushIndex).dirt ))
				 * { // if it is against very large trend, the if the large
				 * trend is finished, looking to fade if
				 * (Constants.DIRT_UP.equals(
				 * bigTrend.pushes.elementAt(bigTrend.
				 * lastReallyTrendyPushIndex).dirt)) bigTrend.direction =
				 * Constants.DIRT_DOWN_FADING; else if
				 * (Constants.DIRT_DOWN.equals(
				 * bigTrend.pushes.elementAt(bigTrend
				 * .lastReallyTrendyPushIndex).dirt)) bigTrend.direction =
				 * Constants.DIRT_UP_FADING;
				 * 
				 * } else
				 */
				{
					if (Constants.DIRT_UP.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
						bigTrend.direction = Constants.DIRT_UP_PULLBACK;
					else if (Constants.DIRT_DOWN.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
						bigTrend.direction = Constants.DIRT_DOWN_PULLBACK;
				}

			} else if (bigTrend.lastTrendyPushIndex == (lastPushIndex - 2)) {
				if (Constants.DIRT_UP.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
					bigTrend.direction = Constants.DIRT_UP_PULLBACK_RESUME;
				else if (Constants.DIRT_DOWN.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
					bigTrend.direction = Constants.DIRT_DOWN_PULLBACK_RESUME;

				// trend resume after diverage
			} else if (bigTrend.lastTrendyPushIndex == (lastPushIndex - 3)) {
				if (Constants.DIRT_UP.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
					bigTrend.direction = Constants.DIRT_UP_SMALL_2;
				else if (Constants.DIRT_DOWN.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
					bigTrend.direction = Constants.DIRT_DOWN_SMALL_2;
			} else if (bigTrend.lastTrendyPushIndex <= (lastPushIndex - 4)) {
				if (Constants.DIRT_UP.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt)) {
					bigTrend.direction = Constants.DIRT_DOWN_FADING;
					if (Constants.DIRT_DOWN.equals(lastPush.dirt)) {
						Push prevDown = bigTrend.pushes.elementAt(bigTrend.lastPushIndex - 2);
						bigTrend.support = Utility.getLow(quotes, prevDown.pushStart, prevDown.pushEnd);
					} else {
						Push prevDown = bigTrend.pushes.elementAt(bigTrend.lastPushIndex - 2);
						bigTrend.support = Utility.getLow(quotes, prevDown.pushStart, prevDown.pushEnd);
					}
				} else if (Constants.DIRT_DOWN.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt)) {
					bigTrend.direction = Constants.DIRT_UP_FADING;
					if (Constants.DIRT_UP.equals(lastPush.dirt)) {
						Push prevUp = bigTrend.pushes.elementAt(bigTrend.lastPushIndex - 2);
						bigTrend.resistance = Utility.getHigh(quotes, prevUp.pushStart, prevUp.pushEnd);
					} else {
						Push prevUp = bigTrend.pushes.elementAt(bigTrend.lastPushIndex - 1);
						bigTrend.resistance = Utility.getHigh(quotes, prevUp.pushStart, prevUp.pushEnd);
					}
				}
			}
		}

		/*
		 * if (( lastTrendyPush != null ) && ( lastTrendyPush.direction ==
		 * Constants.DIRECTION_UP )) { bigTrend.direction = Constants.DIRT_UP;
		 * 
		 * // 1. check if last two add up made a difference /* int
		 * lastDownPushIndex =
		 * bigTrend.getLastPushIndex(Constants.DIRECTION_DOWN); if (
		 * lastDownPushIndex >= lastTrendPushIndex + 3 ) { if ((
		 * bigTrend.pushes.elementAt( lastDownPushIndex).getPushLow(quotes).low
		 * < bigTrend.pushes.elementAt( lastDownPushIndex - 2
		 * ).getPushLow(quotes).low) && ( bigTrend.pushes.elementAt(
		 * lastDownPushIndex-1).getPushHigh(quotes).high <
		 * bigTrend.pushes.elementAt( lastDownPushIndex - 3
		 * ).getPushHigh(quotes).high)) { bigTrend.direction =
		 * Constants.DIRT_DOWN_SEC_2; return bigTrend; } }
		 */

		/*
		 * if ( lastPushIndex >= bigTrend.lastTrendyPushIndex + 3 ) { int
		 * lastTwo = ( bigTrend.pushes.elementAt(lastPushIndex).pushEnd -
		 * bigTrend.pushes.elementAt(lastPushIndex).pushStart +1 ) + (
		 * bigTrend.pushes.elementAt(lastPushIndex-2).pushEnd -
		 * bigTrend.pushes.elementAt(lastPushIndex-2).pushStart + 1 ); int
		 * lastTwoPre = ( bigTrend.pushes.elementAt(lastPushIndex-1).pushEnd -
		 * bigTrend.pushes.elementAt(lastPushIndex-1).pushStart +1 ) + (
		 * bigTrend.pushes.elementAt(lastPushIndex-3).pushEnd -
		 * bigTrend.pushes.elementAt(lastPushIndex-3).pushStart +1 ); if
		 * (bigTrend.pushes.elementAt(lastPushIndex).direction ==
		 * Constants.DIRECTION_DOWN ) { if ((lastTwo> 48 ) || (( lastTwo > 36 )
		 * && (lastTwo > lastTwoPre ))) { bigTrend.direction =
		 * Constants.DIRT_DOWN_SEC_2; return bigTrend; } } else { if ((
		 * lastTwoPre > 48 ) || (( lastTwoPre > 36 ) && ( lastTwoPre > lastTwo
		 * ))) { bigTrend.direction = Constants.DIRT_DOWN_SEC_2; return
		 * bigTrend; } } }
		 * 
		 * 
		 * // 2. check to see if there is a large price change QuoteData lPoint,
		 * hPoint; int ind = lastPushIndex; if ( lastPush.direction ==
		 * Constants.DIRECTION_DOWN ) ind = lastPushIndex - 1;
		 * 
		 * // Determin the latest highest point hPoint =
		 * bigTrend.pushes.elementAt(ind).getPushHigh(quotes); if (( ind - 2 )
		 * >= 0 ) { Push prevHighPush = bigTrend.pushes.elementAt(ind - 2); Push
		 * prevLowPush = bigTrend.pushes.elementAt( ind - 1); QuoteData
		 * prevHighPushHigh = prevHighPush.getPushHigh(quotes); if ((
		 * prevHighPushHigh.high > hPoint.high) && ( hPoint.pos -
		 * prevHighPushHigh.pos < TREND_QUALIFY_PERIOD )) { hPoint =
		 * prevHighPushHigh; } }
		 * 
		 * lPoint = Utility.getLow( quotes, hPoint.pos+1, lastbar); double
		 * totalAvgBarSize = Utility.averageBarSize(quotes);
		 * 
		 * if ( hPoint.pos != lastbar ) { //if (((hPoint.high - lPoint.low) > 8
		 * * totalAvgBarSize ) && ( lastbar - lPoint.pos < trendQualifyPeriod ))
		 * if (((hPoint.high - lPoint.low) > 8 * totalAvgBarSize ) && (
		 * lPoint.pos > lastTrendyPush.pushStart )) { bigTrend.direction =
		 * Constants.DIRT_DOWN_REVERSAL; //bigTrend.start =
		 * ((Push)bigTrend.pushes.elementAt(lastPushDownIndex)).pushStart;
		 * return bigTrend; } }
		 * 
		 * 
		 * // see if we can find a relatively strong spike up Push push =
		 * Utility.findLargestConsectiveDownBars(quotes, hPoint.pos, lastbar);
		 * //Push push = Utility.findLargestConsectiveDownBars(quotes,
		 * bigTrend.pushes.elementAt(ind).pushStart, lastbar); if ( push != null
		 * ) { double totalPushSize = quotes[push.pushStart].open -
		 * quotes[push.pushEnd].close; int numOfBar = push.pushEnd -
		 * push.pushStart + 1; double avgPushSize = totalPushSize/numOfBar;
		 * 
		 * if(((( numOfBar == 2 ) && (avgPushSize > 1.2 * totalAvgBarSize)) ||
		 * (( numOfBar == 3 ) && (avgPushSize > totalAvgBarSize)) || (( numOfBar
		 * >= 4 ) && (avgPushSize > 0.9 * totalAvgBarSize)) || (( numOfBar >= 5
		 * ) && (avgPushSize > 0.8 * totalAvgBarSize)))) { boolean
		 * brokenLowerLow = false;
		 * 
		 * for ( int j = lastbar; (j >
		 * bigTrend.pushes.elementAt(ind).pushStart+3) && (brokenLowerLow ==
		 * false) ; j--) { PushList pushList = Pattern.findPast2Highs2(quotes,
		 * bigTrend.pushes.elementAt(ind).pushStart, j); if (( pushList.phls !=
		 * null ) && ( pushList.phls.length > 0 )) { for ( int k = 0;
		 * (k<pushList.phls.length) && (brokenLowerLow == false); k++) {
		 * PushHighLow p = pushList.phls[pushList.phls.length-1]; for ( int l =
		 * p.curPos+1; l < lastbar; l++) { if (( quotes[l].low < p.pullBack.low)
		 * && ( push.pushStart > p.pullBack.pos)) { brokenLowerLow = true;
		 * break; } } } } }
		 * 
		 * for (int k = push.pushEnd+1; k <= lastbar; k++) { if ( quotes[k].low
		 * < quotes[bigTrend.pushes.elementAt(ind).pushStart].low) {
		 * brokenLowerLow = true; break; } }
		 * 
		 * 
		 * if ( brokenLowerLow == true ) { bigTrend.direction =
		 * Constants.DIRT_DOWN_REVERSAL; return bigTrend; } } } } else if ((
		 * lastTrendyPush != null ) && ( lastTrendyPush.direction ==
		 * Constants.DIRECTION_DOWN )) { bigTrend.direction =
		 * Constants.DIRT_DOWN;
		 * 
		 * // 1. check if last two add up made a difference /* int
		 * lastUpPushIndex = bigTrend.getLastPushIndex(Constants.DIRECTION_UP);
		 * if ( lastUpPushIndex >= lastTrendPushIndex + 3 ) { if ((
		 * bigTrend.pushes.elementAt( lastUpPushIndex).getPushHigh(quotes).high
		 * > bigTrend.pushes.elementAt( lastUpPushIndex - 2
		 * ).getPushHigh(quotes).high) && ( bigTrend.pushes.elementAt(
		 * lastUpPushIndex-1).getPushLow(quotes).low >
		 * bigTrend.pushes.elementAt( lastUpPushIndex - 3
		 * ).getPushLow(quotes).low)) { bigTrend.direction =
		 * Constants.DIRT_UP_SEC_2; return bigTrend; } }
		 */

		/*
		 * if ( lastPushIndex >= bigTrend.lastTrendyPushIndex + 3 ) { int
		 * lastTwo = ( bigTrend.pushes.elementAt(lastPushIndex).pushEnd -
		 * bigTrend.pushes.elementAt(lastPushIndex).pushStart +1 ) + (
		 * bigTrend.pushes.elementAt(lastPushIndex-2).pushEnd -
		 * bigTrend.pushes.elementAt(lastPushIndex-2).pushStart + 1 ); int
		 * lastTwoPre = ( bigTrend.pushes.elementAt(lastPushIndex-1).pushEnd -
		 * bigTrend.pushes.elementAt(lastPushIndex-1).pushStart +1 ) + (
		 * bigTrend.pushes.elementAt(lastPushIndex-3).pushEnd -
		 * bigTrend.pushes.elementAt(lastPushIndex-3).pushStart +1 ); if
		 * (bigTrend.pushes.elementAt(lastPushIndex).direction ==
		 * Constants.DIRECTION_UP ) { if ((lastTwo> 48 ) || (( lastTwo > 36 ) &&
		 * (lastTwo > lastTwoPre ))) { bigTrend.direction =
		 * Constants.DIRT_UP_SEC_2; return bigTrend; } } else { if (( lastTwoPre
		 * > 48 ) || (( lastTwoPre > 36 ) && ( lastTwoPre > lastTwo ))) {
		 * bigTrend.direction = Constants.DIRT_UP_SEC_2; return bigTrend; } } }
		 * 
		 * 
		 * 
		 * // 2. check to see if there is a large price change QuoteData lPoint,
		 * hPoint; int ind = lastPushIndex; if ( lastPush.direction ==
		 * Constants.DIRECTION_UP ) ind = lastPushIndex - 1;
		 * 
		 * lPoint = bigTrend.pushes.elementAt(ind).getPushLow(quotes); if (( ind
		 * - 2 ) >= 0 ) { Push prevLowPush = bigTrend.pushes.elementAt(ind - 2);
		 * Push prevHighPush = bigTrend.pushes.elementAt( ind - 1); QuoteData
		 * prevLowPushLow = prevLowPush.getPushLow(quotes); if ((
		 * prevLowPushLow.low < lPoint.low) && ( lPoint.pos - prevLowPushLow.pos
		 * < TREND_QUALIFY_PERIOD )) //if (( prevLowPushLow.low < lPoint.low) &&
		 * ( prevHighPush.pushEnd - prevHighPush.pushStart < trendQualifyPeriod
		 * )) { lPoint = prevLowPushLow; } }
		 * 
		 * hPoint = Utility.getHigh( quotes, lPoint.pos+1, lastbar); double
		 * totalAvgBarSize = Utility.averageBarSize(quotes);
		 * 
		 * if ( lPoint.pos != lastbar ) { //if ((( hPoint.high - lPoint.low) > 8
		 * * totalAvgBarSize ) && ( lastbar - hPoint.pos < trendQualifyPeriod ))
		 * if ((( hPoint.high - lPoint.low) > 8 * totalAvgBarSize ) && (
		 * hPoint.pos > lastTrendyPush.pushStart )) { bigTrend.direction =
		 * Constants.DIRT_UP_REVERSAL; //bigTrend.start =
		 * ((Push)bigTrend.pushes.elementAt(lastPushDownIndex)).pushStart;
		 * return bigTrend; } }
		 * 
		 * 
		 * // see if we can find a relatively strong spike up // Push push =
		 * Utility.findLargestConsectiveUpBars(quotes,
		 * bigTrend.pushes.elementAt(ind).pushStart, lastbar); Push push =
		 * Utility.findLargestConsectiveUpBars(quotes, lPoint.pos, lastbar); if
		 * ( push != null ) { double totalPushSize = quotes[push.pushEnd].close
		 * - quotes[push.pushStart].open; int numOfBar = push.pushEnd -
		 * push.pushStart + 1; double avgPushSize = totalPushSize/numOfBar;
		 * 
		 * if(((( numOfBar == 2 ) && (avgPushSize > 1.2 * totalAvgBarSize)) ||
		 * (( numOfBar == 3 ) && (avgPushSize > totalAvgBarSize)) || (( numOfBar
		 * >= 4 ) && (avgPushSize > 0.9 * totalAvgBarSize)) || (( numOfBar >= 5
		 * ) && (avgPushSize > 0.8 * totalAvgBarSize)))) { boolean
		 * brokenHigherHigh = false;
		 * 
		 * for ( int j = lastbar; (j >
		 * bigTrend.pushes.elementAt(ind).pushStart+3) && (brokenHigherHigh ==
		 * false) ; j--) { PushList pushList = Pattern.findPast2Lows2(quotes,
		 * bigTrend.pushes.elementAt(ind).pushStart, j); if (( pushList.phls !=
		 * null ) && ( pushList.phls.length > 0 )) { PushHighLow p =
		 * pushList.phls[pushList.phls.length-1]; for ( int l = p.curPos+1; l <
		 * lastbar; l++) { if (( quotes[l].high > p.pullBack.high) && (
		 * push.pushStart > p.pullBack.pos)) { brokenHigherHigh = true; break; }
		 * } break; } }
		 * 
		 * for (int k = push.pushEnd+1; k <= lastbar; k++) { if ( quotes[k].high
		 * > quotes[ bigTrend.pushes.elementAt(ind).pushStart].high) {
		 * brokenHigherHigh = true; break; } }
		 * 
		 * 
		 * if ( brokenHigherHigh == true ) { bigTrend.direction =
		 * Constants.DIRT_UP_REVERSAL; return bigTrend; } } }
		 * 
		 * }
		 */

		return bigTrend;
	}

	/**
	 * @param returns
	 *            history of 20/50 corossing and the current position at 20MA
	 * @return
	 */
	BigTrend determineBigTrend_240(QuoteData[] quotes) {
		int TREND_QUALIFY_PERIOD = 48;

		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		BigTrend bigTrend = new BigTrend();

		int start = 0;

		// calculate UPs and DOWNs
		int end = lastbar;
		while (end >= 50) {
			if ((ema20[end] > ema50[end]) && (ema20[end - 1] > ema50[end - 1])) {
				start = end - 2;
				while ((start >= 50) && !((ema20[start] < ema50[start]) && (ema20[start - 1] < ema50[start - 1]))) {
					start--;
				}

				if (start < 50) {
					Push p = new Push(Constants.DIRT_UP, 0, end);
					bigTrend.pushes.insertElementAt(p, 0);
					bigTrend.start = 0;
					break;
				} else {
					Push p = new Push(Constants.DIRT_UP, start + 1, end);
					bigTrend.pushes.insertElementAt(p, 0);
					end = start;

				}
			} else if ((ema20[end] < ema50[end]) && (ema20[end - 1] < ema50[end - 1])) {
				start = end - 2;
				while ((start >= 50) && !((ema20[start] > ema50[start]) && (ema20[start - 1] > ema50[start - 1]))) {
					start--;
				}

				if (start < 50) {
					Push p = new Push(Constants.DIRT_DOWN, 0, end);
					bigTrend.pushes.insertElementAt(p, 0);
					bigTrend.start = 0;
					break;
				} else {
					Push p = new Push(Constants.DIRT_DOWN, start + 1, end);
					bigTrend.pushes.insertElementAt(p, 0);
					end = start;
				}

			} else
				end--;
		}

		// find the last push that > trendQualifyPeriod
		Push lastTrendyPush = null;
		int lastPushIndex = bigTrend.pushes.size() - 1;
		bigTrend.lastPushIndex = lastPushIndex;
		Push lastPush = bigTrend.pushes.elementAt(lastPushIndex);

		for (int i = lastPushIndex; i >= 0; i--) {
			Push p = bigTrend.pushes.elementAt(i);
			if ((p.duration > TREND_QUALIFY_PERIOD) && (bigTrend.lastTrendyPushIndex == 0)) {
				lastTrendyPush = p;
				bigTrend.lastTrendyPushIndex = i;
			}

			if ((p.duration > 2 * TREND_QUALIFY_PERIOD) && (bigTrend.lastReallyTrendyPushIndex == 0)) {
				bigTrend.lastReallyTrendyPushIndex = i;
			}
		}

		// if (( bigTrend.lastTrendPushIndex == lastPushIndex ) || (
		// bigTrend.lastTrendPushIndex == (lastPushIndex-1)))
		// bigTrend.isTrendy = true;

		// Rule 1, if there are more than 2 small contract
		if (bigTrend.lastTrendyPushIndex != 0) {
			if (bigTrend.lastTrendyPushIndex == lastPushIndex) {
				// it is on that trend,
				bigTrend.direction = bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt;
			} else if (bigTrend.lastTrendyPushIndex == (lastPushIndex - 1)) {
				// first first reversal breakout
				/*
				 * if (( bigTrend.lastReallyTrendyPushIndex != 0 ) &&
				 * !bigTrend.pushes
				 * .elementAt(bigTrend.lastTrendyPushIndex).dirt.
				 * equals(bigTrend.
				 * pushes.elementAt(bigTrend.lastReallyTrendyPushIndex).dirt ))
				 * { // if it is against very large trend, the if the large
				 * trend is finished, looking to fade if
				 * (Constants.DIRT_UP.equals(
				 * bigTrend.pushes.elementAt(bigTrend.
				 * lastReallyTrendyPushIndex).dirt)) bigTrend.direction =
				 * Constants.DIRT_DOWN_FADING; else if
				 * (Constants.DIRT_DOWN.equals(
				 * bigTrend.pushes.elementAt(bigTrend
				 * .lastReallyTrendyPushIndex).dirt)) bigTrend.direction =
				 * Constants.DIRT_UP_FADING;
				 * 
				 * } else
				 */
				{
					if (Constants.DIRT_UP.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
						bigTrend.direction = Constants.DIRT_UP_PULLBACK;
					else if (Constants.DIRT_DOWN.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
						bigTrend.direction = Constants.DIRT_DOWN_PULLBACK;
				}

			} else if (bigTrend.lastTrendyPushIndex == (lastPushIndex - 2)) {
				if (Constants.DIRT_UP.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
					bigTrend.direction = Constants.DIRT_UP_PULLBACK_RESUME;
				else if (Constants.DIRT_DOWN.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
					bigTrend.direction = Constants.DIRT_DOWN_PULLBACK_RESUME;

				// trend resume after diverage
			} else if (bigTrend.lastTrendyPushIndex == (lastPushIndex - 3)) {
				if (Constants.DIRT_UP.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
					bigTrend.direction = Constants.DIRT_UP_SMALL_2;
				else if (Constants.DIRT_DOWN.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt))
					bigTrend.direction = Constants.DIRT_DOWN_SMALL_2;
			} else if (bigTrend.lastTrendyPushIndex <= (lastPushIndex - 4)) {
				if (Constants.DIRT_UP.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt)) {
					bigTrend.direction = Constants.DIRT_DOWN_FADING;
					if (Constants.DIRT_DOWN.equals(lastPush.dirt)) {
						Push prevDown = bigTrend.pushes.elementAt(bigTrend.lastPushIndex - 2);
						bigTrend.support = Utility.getLow(quotes, prevDown.pushStart, prevDown.pushEnd);
					} else {
						Push prevDown = bigTrend.pushes.elementAt(bigTrend.lastPushIndex - 2);
						bigTrend.support = Utility.getLow(quotes, prevDown.pushStart, prevDown.pushEnd);
					}
				} else if (Constants.DIRT_DOWN.equals(bigTrend.pushes.elementAt(bigTrend.lastTrendyPushIndex).dirt)) {
					bigTrend.direction = Constants.DIRT_UP_FADING;
					if (Constants.DIRT_UP.equals(lastPush.dirt)) {
						Push prevUp = bigTrend.pushes.elementAt(bigTrend.lastPushIndex - 2);
						bigTrend.resistance = Utility.getHigh(quotes, prevUp.pushStart, prevUp.pushEnd);
					} else {
						Push prevUp = bigTrend.pushes.elementAt(bigTrend.lastPushIndex - 1);
						bigTrend.resistance = Utility.getHigh(quotes, prevUp.pushStart, prevUp.pushEnd);
					}
				}
			}
		}

		// current position
		int lastUp5Pos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 5);
		int lastDown5Pos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 5);

		return bigTrend;
	}

	BigTrend calculateTrend2(QuoteData[] quotes) {
		// this is to be expected on 240 min chart
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double averageBarSize = BarUtil.averageBarSize(quotes);
		boolean deepPullBack = false;
		int pushTip = 0;

		BigTrend bigTrend = new BigTrend();
		bigTrend.direction = Constants.DIRT_UNKNOWN;

		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 5); // 5
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 5);

		// looking for first 5MA cross that has more than 5 bars
		if (lastUpPos > lastDownPos) {
			bigTrend.direction = Constants.DIRT_UP;
			bigTrend.state = BigTrend.STATE_UP;
			// System.out.println("Trend is up");

			if (lastDownPos == Constants.NOT_FOUND)
				lastDownPos = 0;
			int start = lastDownPos;
			while ((start < lastbar) && !((quotes[start].low > ema20[start]) && (quotes[start + 1].low > ema20[start + 1])))
				start++;
			bigTrend.start = start;
			// System.out.println("Trend starts:" + quotes[start].time);

			// run push analysis
			int pushStart = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 1);
			pushStart = Utility.getLow(quotes, (pushStart - 5 > 0) ? pushStart - 5 : 0, (pushStart + 2 > lastbar) ? lastbar : pushStart + 2).pos;
			for (int i = lastbar; i > start + 1; i--) {
				PushList pushList = Pattern.findPast1Highs1(quotes, pushStart, i);

				if ((pushList != null) && (pushList.phls != null) && (pushList.phls.length > 0)) {
					bigTrend.pushList = pushList;
					// System.out.println("Last push detected at " +
					// quotes[i].time);
					pushTip = i;
					// check each one System.out.println("find high @" +
					// data.time);
					for (int j = 0; j < pushList.phls.length; j++) {
						if ((quotes[pushList.phls[j].prePos].high - pushList.phls[j].pullBack.low) > 3.5 * averageBarSize) {
							// System.out.println("Deep pullback detected at:" +
							// pushList.phls[j].pullBack.time);
							bigTrend.state = BigTrend.STATE_UP_DIVERAGE;
							deepPullBack = true;
							break;
						}
					}
					break;
				}
			}

			//
			// if it has not reach any push, to see if there's any large pull
			// back that would change the trend
			if (lastbar > pushTip) {
				QuoteData highestPoint = Utility.getHigh(quotes, start, lastbar);
				QuoteData latestLow = Utility.getLow(quotes, highestPoint.pos + 1, lastbar);
				if ((latestLow != null) && ((highestPoint.high - latestLow.low) > 3.5 * averageBarSize)) {
					// System.out.println("Deep pullback2 detected at:" +
					// latestLow.time);
					bigTrend.state = BigTrend.STATE_UP_DIVERAGE;
					deepPullBack = true;
				}
			}

		} else if (lastDownPos > lastUpPos) {
			bigTrend.direction = Constants.DIRT_DOWN;
			bigTrend.state = BigTrend.STATE_DOWN;
			// System.out.println("Trend is down");

			if (lastUpPos == Constants.NOT_FOUND)
				lastUpPos = 0;
			int start = lastUpPos;
			while ((start < lastbar) && !((quotes[start].high < ema20[start]) && (quotes[start + 1].high < ema20[start + 1])))
				start++;
			bigTrend.start = start;
			// System.out.println("Trend starts:" + quotes[start].time);

			// run push analysis
			int pushStart = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 1);
			if (pushStart == Constants.NOT_FOUND)
				return null;
			pushStart = Utility.getHigh(quotes, (pushStart - 5 > 0) ? pushStart - 5 : 0, (pushStart + 2 > lastbar) ? lastbar : pushStart + 2).pos;
			// System.out.println("Push Start:" + quotes[pushStart].time);
			for (int i = lastbar; i > start + 1; i--) {
				PushList pushList = Pattern.findPast1Lows1(quotes, pushStart, i);

				if ((pushList != null) && (pushList.phls != null) && (pushList.phls.length > 0)) {
					bigTrend.pushList = pushList;
					pushTip = i;
					// check each one System.out.println("find high @" +
					// data.time);
					for (int j = 0; j < pushList.phls.length; j++) {
						if ((pushList.phls[j].pullBack.high - quotes[pushList.phls[j].prePos].low) > 3.5 * averageBarSize) {
							// System.out.println("Deep pullback detected at:" +
							// pushList.phls[j].pullBack.time);
							bigTrend.state = BigTrend.STATE_DOWN_DIVERAGE;
							deepPullBack = true;
							break;
						}
					}
					break;
				}
			}

			//
			// if it has not reach any push, to see if there's any large pull
			// back that would change the trend
			if (lastbar > pushTip) {
				QuoteData lowestPoint = Utility.getLow(quotes, start, lastbar);
				QuoteData latestHigh = Utility.getHigh(quotes, lowestPoint.pos + 1, lastbar);
				if ((latestHigh != null) && ((latestHigh.high - lowestPoint.low) > 3.5 * averageBarSize)) {
					// System.out.println("Deep pullback2 detected at:" +
					// latestHigh.time);
					bigTrend.state = BigTrend.STATE_DOWN_DIVERAGE;
					deepPullBack = true;
				}
			}

		}

		// System.out.println("BigTrend State:" + bigTrend.state);
		return bigTrend;
	}

	BigTrend calculateTrend(QuoteData[] quotes) {
		// this is to be expected on 240 min chart
		int lastbar = quotes.length - 1;
		double[] ema5 = Indicator.calculateEMA(quotes, 5);
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double averageBarSize = BarUtil.averageBarSize(quotes);
		boolean deepPullBack = false;
		int pushTip = 0;

		BigTrend bigTrend = new BigTrend();
		bigTrend.direction = Constants.DIRT_UNKNOWN;

		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 5); // 5
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 5);

		// looking for first 5MA cross that has more than 5 bars
		if (lastUpPos > lastDownPos) {
			bigTrend.direction = Constants.DIRT_UP;
			bigTrend.state = BigTrend.STATE_UP;
			// System.out.println("Trend is up");

			if (lastDownPos == Constants.NOT_FOUND)
				lastDownPos = 0;
			int start = lastDownPos;
			while ((start < lastbar) && !((quotes[start].low > ema20[start]) && (quotes[start + 1].low > ema20[start + 1])))
				start++;
			bigTrend.start = start;
			// System.out.println("Trend starts:" + quotes[start].time);

			// run push analysis
			int pushStart = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 1);
			pushStart = Utility.getLow(quotes, (pushStart - 5 > 0) ? pushStart - 5 : 0, (pushStart + 2 > lastbar) ? lastbar : pushStart + 2).pos;
			for (int i = lastbar; i > start + 1; i--) {
				PushList pushList = Pattern.findPast1Highs1(quotes, pushStart, i);

				if ((pushList != null) && (pushList.phls != null) && (pushList.phls.length > 0)) {
					bigTrend.pushList = pushList;
					// System.out.println("Last push detected at " +
					// quotes[i].time);
					pushTip = i;
					// check each one System.out.println("find high @" +
					// data.time);
					for (int j = 0; j < pushList.phls.length; j++) {
						if ((quotes[pushList.phls[j].prePos].high - pushList.phls[j].pullBack.low) > 3.5 * averageBarSize) {
							// System.out.println("Deep pullback detected at:" +
							// pushList.phls[j].pullBack.time);
							bigTrend.state = BigTrend.STATE_UP_DIVERAGE;
							deepPullBack = true;
							break;
						}
					}
					break;
				}
			}

			//
			// if it has not reach any push, to see if there's any large pull
			// back that would change the trend
			if (lastbar > pushTip) {
				QuoteData highestPoint = Utility.getHigh(quotes, start, lastbar);
				QuoteData latestLow = Utility.getLow(quotes, highestPoint.pos + 1, lastbar);
				if ((latestLow != null) && ((highestPoint.high - latestLow.low) > 3.5 * averageBarSize)) {
					// System.out.println("Deep pullback2 detected at:" +
					// latestLow.time);
					bigTrend.state = BigTrend.STATE_UP_DIVERAGE;
					deepPullBack = true;
				}
			}

		} else if (lastDownPos > lastUpPos) {
			bigTrend.direction = Constants.DIRT_DOWN;
			bigTrend.state = BigTrend.STATE_DOWN;
			// System.out.println("Trend is down");

			if (lastUpPos == Constants.NOT_FOUND)
				lastUpPos = 0;
			int start = lastUpPos;
			while ((start < lastbar) && !((quotes[start].high < ema20[start]) && (quotes[start + 1].high < ema20[start + 1])))
				start++;
			bigTrend.start = start;
			// System.out.println("Trend starts:" + quotes[start].time);

			// run push analysis
			int pushStart = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 1);
			if (pushStart == Constants.NOT_FOUND)
				return null;
			pushStart = Utility.getHigh(quotes, (pushStart - 5 > 0) ? pushStart - 5 : 0, (pushStart + 2 > lastbar) ? lastbar : pushStart + 2).pos;
			// System.out.println("Push Start:" + quotes[pushStart].time);
			for (int i = lastbar; i > start + 1; i--) {
				PushList pushList = Pattern.findPast1Lows1(quotes, pushStart, i);

				if ((pushList != null) && (pushList.phls != null) && (pushList.phls.length > 0)) {
					bigTrend.pushList = pushList;
					pushTip = i;
					// check each one System.out.println("find high @" +
					// data.time);
					for (int j = 0; j < pushList.phls.length; j++) {
						if ((pushList.phls[j].pullBack.high - quotes[pushList.phls[j].prePos].low) > 3.5 * averageBarSize) {
							// System.out.println("Deep pullback detected at:" +
							// pushList.phls[j].pullBack.time);
							bigTrend.state = BigTrend.STATE_DOWN_DIVERAGE;
							deepPullBack = true;
							break;
						}
					}
					break;
				}
			}

			//
			// if it has not reach any push, to see if there's any large pull
			// back that would change the trend
			if (lastbar > pushTip) {
				QuoteData lowestPoint = Utility.getLow(quotes, start, lastbar);
				QuoteData latestHigh = Utility.getHigh(quotes, lowestPoint.pos + 1, lastbar);
				if ((latestHigh != null) && ((latestHigh.high - lowestPoint.low) > 3.5 * averageBarSize)) {
					// System.out.println("Deep pullback2 detected at:" +
					// latestHigh.time);
					bigTrend.state = BigTrend.STATE_DOWN_DIVERAGE;
					deepPullBack = true;
				}
			}

		}

		// System.out.println("BigTrend State:" + bigTrend.state);
		return bigTrend;
	}

	public boolean calculateSmoothGoingHigh(QuoteData[] quotes, int type, int start) {
		int lastbar = quotes.length - 1;
		boolean reverseCandidate = false;

		for (int i = lastbar; i > start + 1; i--) {
			PushList pushList = null;
			if (type == Constants.DIRECTION_UP)
				pushList = Pattern.findPast1Highs1(quotes, start, i);
			else if (type == Constants.DIRECTION_DOWN)
				pushList = Pattern.findPast1Lows1(quotes, start, i);

			if ((pushList != null) && (pushList.phls != null) && (pushList.phls.length > 0)) {
				System.out.println(pushList.toString(quotes, PIP_SIZE));
				int pushSize = pushList.phls.length;

				int numbOfBigPullBacks = 0;
				int numbOfRSTPullBacks = 0;
				int numbOfBigBreakOuts = 0;
				for (int j = 0; j < pushList.phls.length; j++) {
					if ((pushList.phls[j].pullBackRatio > 0.68) && (pushList.phls[j].pullBackSize > FIXED_STOP * PIP_SIZE / 2))
						numbOfBigPullBacks++;

					if ((pushList.phls[j].pullBackRatio > 1) && (pushList.phls[j].pullBackSize > FIXED_STOP * PIP_SIZE / 2))
						numbOfRSTPullBacks++;

					if (pushList.phls[j].breakOutSize > FIXED_STOP * PIP_SIZE / 2)
						numbOfBigBreakOuts++;
				}

				if (pushSize == 3) {
					if (((numbOfBigPullBacks >= 1) || (numbOfRSTPullBacks >= 1)) && (numbOfBigBreakOuts <= 1))
						reverseCandidate = true;
				}

				if (pushSize >= 4) {
					if ((numbOfBigPullBacks >= 1) || (numbOfRSTPullBacks >= 1))
						reverseCandidate = true;
				}
				break;
			}
		}

		return reverseCandidate;
	}

	void placeBreakEvenStop() {
		if (trade.breakEvenStopPlaced == false) {
			cancelOrder(trade.stopId);

			if (trade.action.equals(Constants.ACTION_BUY)) {
				trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			} else if (trade.action.equals(Constants.ACTION_SELL)) {
				trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			}

			trade.breakEvenStopPlaced = true;
		}
	}

	protected int takePartialProfit(double price, int size) {
		TradePosition tp = new TradePosition(price, size, null);

		boolean found = false;
		Iterator<TradePosition> it = trade.takeProfitQueue.iterator();
		while (it.hasNext()) {
			TradePosition tpq = it.next();
			if (tp.equals(tpq)) {
				found = true;
				break;
			}
		}

		it = trade.takeProfitHistory.iterator();
		while (it.hasNext()) {
			TradePosition tpq = it.next();
			if (tp.equals(tpq)) {
				found = true;
				break;
			}
		}

		if (!found)
			trade.takeProfitQueue.add(tp);

		if (trade.takeProfitPartialId == 0) {
			TradePosition tr = trade.takeProfitQueue.poll();
			if (tr != null) {
				trade.takeProfitHistory.add(tr);

				trade.takeProfitPartialPrice = tr.price;
				trade.takeProfitPartialPosSize = tr.position_size;

				String action = Constants.ACTION_BUY;
				if (trade.action.equals(Constants.ACTION_BUY))
					action = Constants.ACTION_SELL;

				trade.takeProfitPartialId = placeLmtOrder(action, trade.takeProfitPartialPrice, trade.takeProfitPartialPosSize, null);
				warning("take partial profit " + action + " order placed@ " + trade.takeProfitPartialPrice + " " + trade.takeProfitPartialPosSize);
				return trade.takeProfitPartialId;
			}
		}

		return 0;
	}

	protected void enterPartialPosition(double price, int size) {
		TradePosition tp = new TradePosition(price, size, null);

		boolean found = false;
		Iterator<TradePosition> it = trade.enterPositionQueue.iterator();
		while (it.hasNext()) {
			TradePosition tpq = it.next();
			if (tp.equals(tpq)) {
				found = true;
				break;
			}
		}

		if (found)
			return; // already place

		tp.orderId = placeLmtOrder(trade.action, price, size, null);
		warning("place partial position entry " + trade.action + " order placed@ " + price + " " + size);

		trade.enterPositionQueue.add(tp);

	}

	public void closeReverseTrade(boolean reverse, double reversePrice, int reverse_size) {
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action)) {
			// reverse;
			AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
			if (reverse == false) {
				closePositionByMarket(trade.remainingPositionSize, reversePrice);
			} else {
				cancelOrder(trade.stopId);
				cancelTargets();

				logger.warning(symbol + " " + CHART + " reverse opportunity detected");
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
		} else if (Constants.ACTION_BUY.equals(trade.action)) {
			AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
			if (reverse == false) {
				closePositionByMarket(trade.remainingPositionSize, reversePrice);
			} else {
				cancelOrder(trade.stopId);
				cancelTargets();

				logger.warning(symbol + " " + CHART + " reverse opportunity detected");
				int prevPosionSize = trade.remainingPositionSize;
				removeTrade();

				logger.warning(symbol + " " + CHART + " reverse opportunity detected");
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
				trade.detectTime = quotes[lastbar].time;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = reversePrice;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;

				enterMarketPosition(reversePrice, prevPosionSize);
			}
		}
	}

	boolean LESS_VALUE(double value, double compareable) {
		if ((value > 0) && (value < compareable))
			return true;
		else
			return false;
	}

	/*
	 * protected boolean takeProfitHistory(double price) { return
	 * profitTake.containsKey(price); }
	 * 
	 * protected void addProfitHistory(double price, int size) {
	 * profitTake.put(price, size); }
	 */

	public TradeReport getTradeReport() {
		TradeReport tr = new TradeReport();
		tr.setTradeReport(portofolioReport);
		tr.setTotalTrade((int) totalTrade);
		tr.setTotalProfit(totalProfit);
		tr.setTotalUnrealizedProfit(totalUnrealizedProfit);

		return tr;

	}

	protected Command loadCommand(String COMMAND_FILE, String account) {
		// String COMMAND_FILE = "./SF302.cmd";

		try {
			File f = new File(COMMAND_FILE);
			FileInputStream file = new FileInputStream(f);
			ObjectInputStream input = new ObjectInputStream(file);

			Command cmd = (Command) input.readObject();

			System.out.println("External Command Received:" + cmd.toString());
			// logger.warning("External Command Received:" + cmd.toString());

			input.close();

			// remove the command file;
			f.delete();

			return cmd;

		} catch (FileNotFoundException ex) {
			// System.out.println("External Command file not found");
			// e.printStackTrace(); ignore excetion as file does not exist is
			// normal
		} catch (Exception e) {
			// logger.warning("Load Command error:" + e.getMessage());
			e.printStackTrace();
		}

		return null;
	}

	/*
	public void saveAllTradeData(XStream xstream) {
		if (MODE == Constants.TEST_MODE)
			return;

		for (int i = 0; i < TOTAL_SYMBOLS; i++) {
			String symbol = tradeManager[i].getSymbol();
			String TRADE_DATA_FILE = OUTPUT_DIR + "/" + strategy.getStrategyName() + "/" + symbol + ".xml";

			try // open file
			{
				if (tradeManager[i].getTrade() != null) {
					String xml = xstream.toXML(tradeManager[i].getTrade());
					// Writer out = new BufferedWriter(new
					// FileWriter(TRADE_DATA_FILE));
					Writer out = new OutputStreamWriter(new FileOutputStream(TRADE_DATA_FILE));
					out.write(xml);
					out.close();
				} else {
					File f = new File(TRADE_DATA_FILE);
					if (f.exists())
						f.delete();
				}
			} catch (Exception e) {
				e.printStackTrace();
				logger.warning("Exception occured during saving " + symbol + " data " + e.getMessage());
			}
		}
	}

	public void loadAllTradeData(XStream xstream) {
		System.out.println(strategy.getStrategyName() + " load trade data, OUTPUT DIR=" + OUTPUT_DIR);
		for (int i = 0; i < TOTAL_SYMBOLS; i++) {
			String symbol = tradeManager[i].getSymbol();
			String TRADE_DATA_FILE = OUTPUT_DIR + "/" + strategy.getStrategyName() + "/" + symbol + ".xml";

			StringBuffer xml = new StringBuffer();

			try {
				BufferedReader input = new BufferedReader(new FileReader(TRADE_DATA_FILE));
				String line = null;
				/*
				 * readLine is a bit quirky : it returns the content of a line
				 * MINUS the newline. it returns null only for the END of the
				 * stream. it returns an empty String if two newlines appear in
				 * a row.
				 */
/*				while ((line = input.readLine()) != null) {
					xml.append(line);
				}

				Trade t = (Trade) xstream.fromXML(xml.toString());
				t.positionUpdated = true;

				tradeManager[i].setTrade(t);

				input.close();

			} catch (FileNotFoundException ex) {
				// e.printStackTrace(); ignore excetion as file does not exist
				// is normal
				return; // end of file was reached
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public String getTradeEvents(Trade t) {
		StringBuffer sb = new StringBuffer();
		Iterator<TradeEvent> it2 = t.events.iterator();

		while (it2.hasNext()) {
			TradeEvent ev = it2.next();
			sb.append(ev.toString() + "\n");
		}

		return sb.toString();
	}

	public String listTradeEvents() {
		StringBuffer sb = new StringBuffer();
		sb.append(trade.symbol + " " + trade.action + " " + trade.entryPrice + " " + trade.POSITION_SIZE + " " + trade.status + "\n");
		sb.append(getTradeEvents(trade));

		return sb.toString();
	}

/*
	
	public void readExternalCommand() {
		Command cmd = loadCommand("./" + strategy.getStrategyName() + ".cmd", IB_ACCOUNT);

		if (cmd != null) {
			if (cmd.getCmd() == CommandAction.remove) {
				for (int i = 0; i < TOTAL_SYMBOLS; i++) {
					if ((cmd.getSymbol().indexOf(tradeManager[i].getSymbol()) != -1)) {
						if (tradeManager[i].getTrade() != null) {
							tradeManager[i].cancelAllOrders();
							tradeManager[i].removeTrade();
							// tradeManager[i].trade = null;
						}
					}
				}
			}
		}
	}

	public void checkOrderFilled(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId,
			double lastFillPrice, int clientId, String whyHeld) {
		for (int i = 0; i < TOTAL_SYMBOLS; i++) {
			if (tradeManager[i].getTrade() != null)
				tradeManager[i].checkOrderFilled(orderId, filled);
		}
	}

	public void listAllTradeEvent() {
		for (int i = 0; i < TOTAL_SYMBOLS; i++) {
			Iterator it = tradeManager[i].getTradeHistory().iterator();
			while (it.hasNext()) {
				Trade t = (Trade) it.next();

				Iterator it2 = t.events.iterator();
				while (it2.hasNext()) {
					String eventStr = (String) it2.next();
					System.out.println(eventStr);

				}
			}
		}

	}

	public String getTradeStatus() {
		StringBuffer TradeOpp = new StringBuffer();
		TradeOpp.append(strategy.getStrategyName() + "\n");
		for (int i = 0; i < TOTAL_SYMBOLS; i++) {
			Trade t = tradeManager[i].getTrade();
			if ((t != null) && t.status.equals(Constants.STATUS_DETECTED)) {
				TradeOpp.append("DETECTED " + t.symbol + " " + t.action + "\n");
				TradeOpp.append(t.listTradeEvents());
			}
		}

		for (int i = 0; i < TOTAL_SYMBOLS; i++) {
			Trade t = tradeManager[i].getTrade();
			if ((t != null) && t.status.equals(Constants.STATUS_FILLED)) {
				TradeOpp.append("FILLED " + t.symbol + " " + t.action + "\n");
				TradeOpp.append(t.listTradeEvents());
			}
		}

		for (int i = 0; i < TOTAL_SYMBOLS; i++) {
			Trade t = tradeManager[i].getTrade();
			if ((t != null) && t.status.equals(Constants.STATUS_OPEN)) {
				TradeOpp.append("OPEN " + t.symbol + " " + t.action + "\n");
				TradeOpp.append(t.listTradeEvents());
			}
		}

		return TradeOpp.toString();

		/*
		 * 
		 * 
		 * 
		 * StringBuffer TradeOpp = new StringBuffer(STRATEGY_NAME + "\n\n");
		 * 
		 * for ( int i = 0; i < TOTAL_SYMBOLS; i++) { Trade t =
		 * tradeManager[i].getTrade(); if ( t != null ) {
		 * //TradeOpp.append(tradeManager[i].symbol + " detected " +
		 * tradeManager[i].trade.detectTime + " " + tradeManager[i].trade.action
		 * + "\n" ); TradeOpp.append(tradeManager[i].getSymbol() + " " +
		 * t.action + " " + t.status + "\n");
		 * TradeOpp.append(tradeManager[i].getTradeEvents(t) + "\n"); } }
		 * 
		 * return TradeOpp.toString();
		 */
//	}

	public Instrument getInstrument() {
		return instrument;
	}

/*	public void closeAllPositionsByLimit() {

		logger.info("Close all positions by limit orders");
		// to set all target as current price
		for (int i = 0; i < TOTAL_SYMBOLS; i++) {
			Trade trade = tradeManager[i].getTrade();
			String symbol = tradeManager[i].getSymbol();
			if (trade != null) {
				logger.warning(tradeManager[i].getSymbol() + " trade exists");
				if ((trade.status == Constants.STATUS_OPEN) || (trade.status == Constants.STATUS_STOPPEDOUT)
						|| (trade.status == Constants.STATUS_PLACED)) {
					logger.warning(tradeManager[i].getSymbol() + " trade status open");
					tradeManager[i].cancelLimits();
					tradeManager[i].cancelStop();
					tradeManager[i].setTrade(null);
				} else if (trade.status == Constants.STATUS_FILLED) {
					logger.warning(symbol + "." + " trade status placed");
					tradeManager[i].cancelTargets();
					tradeManager[i].cancelStop();

					trade.status = Constants.STATUS_EXITING;

					QuoteData[] quotes = tradeManager[i].getQuoteData(Constants.CHART_15_MIN);
					int lastbar = quotes.length - 1;
					double targetPrice = quotes[lastbar].close;
					logger.warning(symbol + "." + " last close is " + targetPrice);

					createTradeTargetOrder(trade.remainingPositionSize, targetPrice);
				}
			}
		}
	}*/

	/*
	public void saveTradeSignals(String outPutDir) {
		String TRADE_TRACE_FILE = outPutDir + "/" + "Signal_" + strategy.getStrategyName() + ".txt";

		try // open file
		{
			Writer out = new OutputStreamWriter(new FileOutputStream(TRADE_TRACE_FILE));
			out.write(getTradeStatus());
			out.close();
		} catch (Exception e) {
			logger.warning("Exception occured during saving trade tracks" + e.getMessage());
			e.printStackTrace();
		}

	}*/

	/*
	 * public void createTradeTargetOrder( double quantity, double price ) { if
	 * (( trade != null ) && trade.status.equals(Constants.STATUS_FILLED)) {
	 * QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN); int lastbar =
	 * quotes.length - 1;
	 * 
	 * if (Math.abs(quotes[lastbar].close - price) > 200 * PIP_SIZE) {
	 * warning(" set Target, incorrect target price " + price ); return; }
	 * 
	 * warning(" set Target " + price + " position size "
	 * +(int)(trade.remainingPositionSize*quantity)); if ( trade.targetId != 0 )
	 * cancelOrder(trade.targetId);
	 * 
	 * trade.targetPrice = price; trade.targetPos =
	 * (int)(trade.remainingPositionSize*quantity); trade.targetId =
	 * placeLmtOrder
	 * (Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL
	 * :Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, null); } else
	 * { System.out.println(symbol + " Set Target, trade does not exist"); } }
	 */

	public void createTradePlaceLimit(String action, int quantity, double price) {

		logger.info("Create limit trade for" + symbol + " " + action + " " + quantity + " " + price);
		if (trade == null) {
			/*QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
			int lastbar60 = quotes60.length - 1;
			logger.info("quotes 60 length is " + quotes60.length);
			if ((Math.abs(price - quotes60[lastbar60].close) > 100 * getPIP_SIZE())) {
				logger.severe("Input CSV: Incorrect price for LIMIT " + getSymbol() + " " + action + " " + quantity + " " + price);
				return;
			}*/

			int posSize = quantity;//(int) (quantity * getPOSITION_SIZE());
			trade = createOpenTrade(Constants.TRADE_MA, action, null, posSize, price);

			trade.detectTime = IBDataFormatter.format(new java.util.Date());

			enterLimitPositionMulti(posSize, price);

			logger.info(getSymbol() + action + " " + posSize + " " + price + " " + " placed");
			return;
		}

		logger.severe("Error creating trade for " + symbol + " " + action + quantity + price);

	}

	public boolean targetOrderExists( int posSize, double price){
		for (int i = 0; i < Trade.TOTAL_TARGETS; i++) {
			if ((trade.targets[i] != null) && ( trade.targets[i].price == price ) && ( trade.targets[i].position_size == posSize )){
				return true;
			}
		}
		return false;
	}
	
	
	public void createTradeTargetOrder(int posSize, double price) {

		logger.info("Create target order:" + price + " " + posSize);
		if (trade != null) // && trade.status.equals(Constants.STATUS_FILLED))
		{
			QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
			int lastbar = quotes.length - 1;

			// if ( trade.targetId != 0 )
			// cancelTargets();

			// trade.targetPrice = price;
			// trade.targetPos = (int)(POSITION_SIZE*quantity);
			// trade.targetId =
			// placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY,
			// trade.targetPrice, trade.targetPos, null);

			int targetPos = posSize;// (int)(POSITION_SIZE*quantity);
			TradePosition target = new TradePosition(price, targetPos, null);

			/*
			for (int i = 0; i < Trade.TOTAL_TARGETS; i++) {
				if ((trade.targets[i] != null) && trade.targets[i].equals(target)){
					logger.info("target already exists, do nothing");
					return;
				}
			}*/

			/*
			if (Constants.ACTION_BUY.equals(trade.action)
					&& ((price > quotes[lastbar].close + 200 * PIP_SIZE) || (price < quotes[lastbar].close - 5 * PIP_SIZE))) {
				System.out.println("buy target price " + price + " exceeds closing price + 200 or -5 pip, closing price:" + quotes[lastbar].close);
				return;
			} else if (Constants.ACTION_SELL.equals(trade.action)
					&& ((price < quotes[lastbar].close - 200 * PIP_SIZE) || (price > quotes[lastbar].close + 5 * PIP_SIZE))) {
				System.out.println("sell target price " + price + " exceeds closing price - 200 or +5 pip, closing price:" + quotes[lastbar].close);
				return;
			}*/

			for (int i = 0; i < Trade.TOTAL_TARGETS; i++) {
				if (trade.targets[i] == null) {
					target.orderId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, price,
							targetPos, null);
					trade.targets[i] = target;
					return;
				}
			}
		} else {
			System.out.println(symbol + " set Target, trade does not exist");
		}
	}

	public int enterLimitPositionMulti(int quantity, double price) {
		if (trade != null) // && !trade.status.equals(Constants.STATUS_FILLED))
		{
			int entryPos = quantity;
			TradePosition limit = new TradePosition(price, entryPos, null);

			/*
			for (int i = 0; i < Trade.TOTAL_LIMITS; i++) {
				if ((trade.limits[i] != null) && trade.limits[i].equals(limit))
					return Constants.ERROR; // order already placed
			}*/

			/*
			 * if (Constants.ACTION_BUY.equals(trade.action) && (price >
			 * quotes[lastbar].close + 10 * PIP_SIZE )) {
			 * System.out.println("buy price > close price + 5 pip_size, price:"
			 * + price + " close:" + quotes[lastbar].close +
			 * " seems error...TRADE IS NOT PLACED"); return Constants.ERROR; }
			 * else if (Constants.ACTION_SELL.equals(trade.action) && (price <
			 * quotes[lastbar].close - 10 * PIP_SIZE )) {
			 * System.out.println("sell price > close price - 5 pip_size, price:"
			 * + price + " close:" + quotes[lastbar].close +
			 * " seems error...TRADE IS NOT PLACED"); return Constants.ERROR; }
			 */

			for (int i = 0; i < Trade.TOTAL_LIMITS; i++) {
				if (trade.limits[i] == null) {
					limit.orderId = placeLmtOrder(trade.action, price, entryPos, null);
					trade.limits[i] = limit;
					return limit.orderId;
				}
			}
			System.out.println(symbol + " no limit order space found");
		} else {
			System.out.println(symbol + " Set Limit, trade does not exist");
		}

		return Constants.ERROR;
	}

	public void setStrategy(Strategy s){
		strategy = s;
	}

	
	
	
	/*************************************************************************************************
	 * 
	 * 
	 * 		Entries
	 * 
	 * @param data
	 *************************************************************************************************/
	public void trackPullbackEntryUsingConfluenceIndicator60(QuoteData data)
	{
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
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0)){
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotes60[lastPush.prePos].high - lastPush.pullBack.low;

				if (( lastPush != null ) && ( data.low < lastPush.pullBack.low )){
					double triggerPrice = lastPush.pullBack.low;
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
					enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					cancelLimits();
					return;
				}

				if ( lastbar60 == pushList.end )
				{	
					int pullBackSize = (int)(lastPush.pullBackSize/PIP_SIZE);

					//TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH, quotes60[lastbar60].time);
					//tv.addNameValue("High Price", new Double(quotes60[lastPush.prePos].high).toString());
					//tv.addNameValue("NumPush", new Integer(pushSize).toString());
					//tv.addNameValue("PullBackSize", new Integer(pullBackSize).toString());
					//trade.addTradeEvent(tv);
					//double avgBarSize60 = BarUtil.averageBarSize2(quotes60);
					
					if (lastPush.pullBackSize > 2 * FIXED_STOP * PIP_SIZE)
					{
						/*
						double triggerPrice = adjustPrice(quotes60[lastPush.prePos].high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						//warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
						
						if ( trade.limit1Placed == false )
						{
							warning("Peak trade sell triggered on large pull back  @ " + quotes60[lastbar60].time + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.curPos].time );
							enterLimitPositionMulti( POSITION_SIZE/2, triggerPrice );
							trade.limit1Placed = true;
						}
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
				double triggerPrice = quotes60[startL].low;
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
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
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
					enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					cancelLimits();

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
						double triggerPrice = adjustPrice(quotes60[lastPush.prePos].low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						//enterMarketPosition(triggerPrice);
						//enterPartialPosition( triggerPrice, POSITION_SIZE/2);
						//warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
						/*
						if ( trade.limit1Placed == false )
						{
							warning("Peak trade buy triggered on large pull back  @ " + quotes60[lastbar60].time + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.curPos].time );
							enterLimitPositionMulti( POSITION_SIZE/2, triggerPrice );
							trade.limit1Placed = true;
						}
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
				double triggerPrice = quotes60[startL].high;

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
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();

				return;
			}
		}
		

		return;
	}

	public void clearTradeReport(){
		tradeOrders = new Vector<TradeOrder>();
		tradeOrder2s = new Vector<TradeOrder2>();
	}
	

	
	/*************************************************************************************************
	 * 
	 * 
	 * 		Entries
	 * 
	 * @param data
	 *************************************************************************************************/
	
	

}
