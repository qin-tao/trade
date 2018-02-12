package tao.trading.strategy;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import tao.trading.Constants;
import tao.trading.QuoteData;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

// this is to add "reverse" trad entry
// When should take a CNT trade
// 1.  makes new high, but it is on the top of the height/low, no resistence above/below
//     it is small from last resistence
// 2.  trade with strong 3 bar break out should enter on the first pull back
// 3.  trade that bounce 20MA, but "not crossed" and make a new high/low, should take as a CNT
//     exit:  once they bouce off cross 20MA, then make a new high/low, time to get all position out

public class SF108 implements EWrapper
{
	String IB_ACCOUNT = "DU31237";
	private static Logger logger;
	protected EClientSocket m_client = new EClientSocket(this);
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
	protected SF108TradeManager[][] tradeManager;
	TradeManager closeAccountTradeManager;
	Contract[] contracts;
	int[] id_history;
	boolean[] quotes_received; 
	int[] id_realtime;
	boolean[] id_realtime_started;
	boolean[] quoteFirstTime;
	int totalSymbolIndex = 0;
	int connectionId = 100;
	String IB_ID;
	boolean timeout= false;
	boolean closeAccount = false;
	int TOTAL_SYMBOLS = 11;
	int TOTAL_TRADES = 7;
	int TRADE_1M = 0;
	int TRADE_3M = 1;
	int TRADE_5M = 2;
	int TRADE_10M = 3;
	int TRADE_15M = 4;
	int TRADE_30M = 5;
	int TRADE_60M = 6;
	int MODE = Constants.REAL_MODE;
	double accOpenBalance = 0;
	Vector<String> historyData = new Vector<String>();
	HashMap<String, Double> exchangeRate = new HashMap<String, Double>();
	String saveRealTimeFile = "realtimeData.csv";
	String saveHistoryFile = "historyData.csv";
	String outputDir;
	boolean historySaved;
	QuoteData prevData[];
	String reportFileName;
	boolean stop_loss = false;
	
	public static void main(String[] args)
	{
		//Test DU31237 7496 81 EUR 80000 100000 120000 C:/trade/data/101/historyData.csv C:/trade/data/101/realtimeData.csv report.txt
		if ( "Test".equalsIgnoreCase(args[0]))
    	{
    		SF108 open;
    		String account = args[1];
    		int port = new Integer(args[2]);
    		String conn_id = args[3];
    		String market = args[4];
    		int lot1 = new Integer(args[5]);
    		int lot2 = new Integer(args[6]);
    		int lot3 = new Integer(args[7]);
    		String histFileName = args[8];
    		String realFileName = args[9];
    		String reportFileName = args[10];

    		open = new SF108(account, port, conn_id, market, Constants.TEST_MODE, lot1, lot2, lot3, null);
    		open.reportFileName = reportFileName;
    		open.readHistoryData( histFileName );
    		open.readRealTimeData( realFileName );
			open.report();
    		return;

    	}
		else	// real mode 
    	{	
    		//DU99006 7496 72 EUR 80000 100000 120000 330 C:/trade/data/101/historyData.csv C:/trade/data/101/realtimeData.csv report.txt
    		SF108 open;
    		String account = args[0];
    		int port = new Integer(args[1]);
    		String conn_id = args[2];
    		String market = args[3];
    		int lot1 = new Integer(args[4]);
    		int lot2 = new Integer(args[5]);
    		int lot3 = new Integer(args[6]);
    		int runningTime = new Integer(args[7]);
    		String save = args[8];
    		String logdir = args[9];

    		open = new SF108(account, port, conn_id, market, Constants.REAL_MODE, lot1, lot2, lot3, logdir);
    		open.run();
    		
			for ( int i = 0; i < runningTime; i++ )
			{	
				//Thread.sleep(60000);	// running time in milli seconds
				open.sleep(1);
				open.requestAccountUpdate();
				open.report();
			}
		
			open.timeout = true;
			logger.warning(args[1] + " trading time is up");
		
			if (save.equalsIgnoreCase("S"))
			{
				int s = 0;
				for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
				{
					if ( open.tradeManager[i][open.TRADE_5M].trade != null )
					{
						open.tradeManager[i][open.TRADE_5M].saveTradeData();
						s++;
					}
				}
				logger.info(s + " trades saved");
			}
			else
			{	
				logger.info("close all trades");
				open.closeAllPositionsByLimit();
				open.sleep(5);
				//Thread.sleep(300000);
			
				if ( !open.allPositionClosed())
				{
					logger.info("Not all positions have been closed, close them by market");
					open.closeAllPositionsByMarket();
				}
			}
			
			logger.warning("Trading system shut down");
			System.exit(0);
    	}
	}

	
	public void run() {
		
		// this requests account update so we can calculate the portfolio
		m_client.reqAccountUpdates(true,Constants.ACCOUNT);  		
		
		// this starts real time bar
		m_client.reqCurrentTime();
		
	}

	
	public boolean allPositionClosed()
	{
		boolean allpositionclosed = true;
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			for ( int j = 0; j < TRADE_15M;  j++ )
			{
				if ( tradeManager[i][j].trade != null )
				{
					logger.info("trade " + i + " " + j + " not closed");
					allpositionclosed = false;
				}
			}
		}
		
		return allpositionclosed;
			
	}

	
	public void closeAllPositionsByLimit() {
		
		logger.info("Close all positions by limit orders");
		
		// to set all target as current price
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			for ( int j = 0; j < TRADE_15M;  j++ )
			{
				if ( tradeManager[i][j].trade != null )
				{
					if (( tradeManager[i][j].trade.status == Constants.STATUS_OPEN ) || ( tradeManager[i][j].trade.status == Constants.STATUS_STOPPEDOUT )) 
					{
						tradeManager[i][j].trade = null;
						continue;
					}
	
					if ( tradeManager[i][j].trade.targetId != 0 )
					{
						logger.info(tradeManager[i][j].symbol + " " + tradeManager[i][j].CHART + " cancel existing target order");
						tradeManager[i][j].cancelTarget();
					}
					
					Object[] quotes = tradeManager[i][j].getQuoteData();
					int lastbar = quotes.length -1;
					
					tradeManager[i][j].trade.status = Constants.STATUS_EXITING;
					tradeManager[i][j].trade.targetPrice = ((QuoteData)quotes[lastbar]).close;
					
					String tradeAction = null;
					if (Constants.ACTION_BUY.equals(tradeManager[i][j].trade.action ))
					{
						tradeManager[i][j].trade.targetPrice = tradeManager[i][j].adjustPrice( tradeManager[i][j].trade.targetPrice, Constants.ADJUST_TYPE_UP);
						tradeAction = Constants.ACTION_SELL;
					}
					else if (Constants.ACTION_SELL.equals(tradeManager[i][j].trade.action ))
					{
						tradeManager[i][j].trade.targetPrice = tradeManager[i][j].adjustPrice( tradeManager[i][j].trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
						tradeAction = Constants.ACTION_BUY;
					}
					
					tradeManager[i][j].trade.targetPositionSize = tradeManager[i][j].trade.remainingPositionSize;
					logger.warning(tradeManager[i][j].symbol + " " + tradeManager[i][j].CHART + " place limit target " + tradeAction + " order of " + tradeManager[i][j].trade.targetPositionSize + " @ " + ((QuoteData)quotes[lastbar]).close);
					tradeManager[i][j].trade.targetId = tradeManager[i][j].placeLmtOrder(tradeAction, tradeManager[i][j].trade.targetPrice, tradeManager[i][j].trade.targetPositionSize, null);//new Long(oca).toString());
				}
			}
		}	
	}


	public void closeAllPositionsByMarket() {
		
		logger.warning("Close all positions by market orders");
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			for ( int j = 0; j < TRADE_15M;  j++ )
			{
				if ( tradeManager[i][j].trade != null )
				{
					tradeManager[i][j].cancelStop();
					tradeManager[i][j].cancelTarget();
				}
			}
		}	

		closeAccount = true;
        closeAccountTradeManager = new TradeManager(m_client, 499, null );
		m_client.reqAccountUpdates(true,Constants.ACCOUNT);		
		m_client.reqAccountUpdates(false,Constants.ACCOUNT);	

		sleep(1);  // sleep 1  minute
		logger.warning("All position closed");

	}
	
	
	public void requestAccountUpdate()
	{
		m_client.reqAccountUpdates(true,Constants.ACCOUNT);		
		m_client.reqAccountUpdates(false,Constants.ACCOUNT);	
	}

	
	public SF108( String account, int port, String ib_id, String trade_market, int mode, int lot1, int lot2, int lot3, String outputdir)
	{
		String summaryLogFile = "sf108_"+ account + "_" + trade_market + "_summary.log";
		String detailLogFile = "sf108_"+ account + "_" + trade_market + "_detail.log";
		
		if ( outputdir != null )
		{
    		this.outputDir = outputdir;

    		summaryLogFile = outputDir + summaryLogFile;
			detailLogFile = outputDir + detailLogFile;
    		saveRealTimeFile = outputDir + saveRealTimeFile;
    		saveHistoryFile = outputDir + saveHistoryFile;
    		reportFileName = outputDir + reportFileName;
		}
		
		try
		{
			// this logger logs everything
			boolean append = true;

			FileHandler handler = new FileHandler(detailLogFile, append);
			handler.setFormatter(new SimpleFormatter());

			// this logger creates trade summary
			FileHandler handler2 = new FileHandler(summaryLogFile, append);
			handler2.setLevel(Level.WARNING);
			handler2.setFormatter(new SimpleFormatter());

			// Add to the desired logger
			logger = Logger.getLogger("SF108_" + account);
			logger.addHandler(handler);
			logger.addHandler(handler2);
			
		} 
		catch (IOException e)
		{
			logger.severe(e.getMessage());
			e.printStackTrace();
		}

		IB_ACCOUNT = account;
	   	IB_ID = ib_id;
	   	MODE = mode;
	   	/*
	   	exchangeRate.put("EUR",0.787373);
	   	exchangeRate.put("GBP",0.643396);
	   	exchangeRate.put("CHF",1.025718);
	   	exchangeRate.put("CAD",1.058030);
	   	exchangeRate.put("AUD",1.127114);
	   	exchangeRate.put("NZD",1.422731);
	   	exchangeRate.put("JPY",84.59720);
	   	exchangeRate.put("USD",1.0);
	   	*/
	   	exchangeRate.put("EUR",0.74738415545);
	   	exchangeRate.put("GBP",0.633593106507);
	   	exchangeRate.put("CHF",0.95987713572);
	   	exchangeRate.put("CAD",1.00655265780);
	   	exchangeRate.put("AUD",1.001532344487);
	   	exchangeRate.put("NZD",1.325334);
	   	exchangeRate.put("JPY",83.73105584861425);
	   	exchangeRate.put("USD",1.0);
	   	
		id_history = new int[20];//{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
		id_realtime = new int[20];//]{500,501,502,503,504,505,506,507,508,509,510,511,512,513,514,515,516,517,518,519,520,521,522,523};

		TOTAL_SYMBOLS = 7;
		initialize();
		createContract(  0, 500, "EUR", "CASH", "IDEALPRO", "USD", true,  lot2, 0.0001, 20, Constants.PRICE_TYPE_4,Constants.TIME_ZONE_23);
		createContract(  1, 501, "EUR", "CASH", "IDEALPRO", "JPY", true,  lot2,   0.01, 20, Constants.PRICE_TYPE_2,Constants.TIME_ZONE_12);
		//createContract(  2, 502, "EUR", "CASH", "IDEALPRO", "CAD", true,  lot2, 0.0001, 20, Constants.PRICE_TYPE_3,TIME_ZONE2);
		createContract(  3, 503, "EUR", "CASH", "IDEALPRO", "GBP", true,  lot2, 0.0001, 20, Constants.PRICE_TYPE_4,Constants.TIME_ZONE_23);
		createContract(  4, 504, "EUR", "CASH", "IDEALPRO", "CHF", true,  lot2, 0.0001, 20, Constants.PRICE_TYPE_4,Constants.TIME_ZONE_23);
		//createContract(  5, 505, "EUR", "CASH", "IDEALPRO", "NZD", false, lot2, 0.0001, 20, Constants.PRICE_TYPE_3,TIME_ZONE2);
		//createContract(  6, 506, "EUR", "CASH", "IDEALPRO", "AUD", false, lot2, 0.0001, 20, Constants.PRICE_TYPE_4,TIME_ZONE2);
		//createContract(  7, 507, "GBP", "CASH", "IDEALPRO", "AUD", false, lot1, 0.0001, 22, Constants.PRICE_TYPE_4,TIME_ZONE2);
		//createContract(  8, 508, "GBP", "CASH", "IDEALPRO", "NZD", false, lot1, 0.0001, 22, Constants.PRICE_TYPE_3,TIME_ZONE2);
		createContract(  9, 509, "GBP", "CASH", "IDEALPRO", "USD", true,  lot1, 0.0001, 22, Constants.PRICE_TYPE_4,Constants.TIME_ZONE_23);
		//createContract( 10, 510, "GBP", "CASH", "IDEALPRO", "CAD", true,  lot1, 0.0001, 22, Constants.PRICE_TYPE_3,TIME_ZONE2);
		//createContract( 11, 511, "GBP", "CASH", "IDEALPRO", "CHF", true,  lot1, 0.0001, 22, Constants.PRICE_TYPE_4,TIME_ZONE2);
		createContract( 12, 512, "GBP", "CASH", "IDEALPRO", "JPY", true,  lot1,   0.01, 22, Constants.PRICE_TYPE_2,Constants.TIME_ZONE_12);
		createContract( 13, 513, "CHF", "CASH", "IDEALPRO", "JPY", false, lot3,   0.01, 18, Constants.PRICE_TYPE_2,Constants.TIME_ZONE_12);
		//createContract( 14, 514, "USD", "CASH", "IDEALPRO", "JPY", false, lot2,   0.01, 20, Constants.PRICE_TYPE_2,Constants.TIME_ZONE_13);
		//createContract( 15, 515, "USD", "CASH", "IDEALPRO", "CAD", true,  lot2, 0.0001, 20, Constants.PRICE_TYPE_3,Constants.TIME_ZONE_3);
		//createContract( 16, 516, "USD", "CASH", "IDEALPRO", "CHF", true,  lot2, 0.0001, 20, Constants.PRICE_TYPE_4,Constants.TIME_ZONE_23);
		//createContract( 17, 517, "CAD", "CASH", "IDEALPRO", "JPY", true,  lot2,   0.01, 20, Constants.PRICE_TYPE_2,Constants.TIME_ZONE_13);
		//createContract( 18, 518, "AUD", "CASH", "IDEALPRO", "USD", true,  lot2, 0.0001, 20, Constants.PRICE_TYPE_4,TIME_ZONE2);

		prevData = new QuoteData[TOTAL_SYMBOLS];

		if (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.RECORD_MODE )) 
        {
			m_client.eConnect("127.0.0.1" , port, new Integer(IB_ID));
			m_client.setServerLogLevel(5);
			logger.warning("SF108b started in " + MODE + " mode at port " + port);
        }

	}
	
	@Override
	public void error(Exception e) {
		logger.severe("E1: " + e.getMessage());
		e.printStackTrace();
	}

	@Override
	public void error(String str) {
		logger.severe("E2: " + str);
	}

	@Override
	public void error(int id, int errorCode, String errorMsg) {
		logger.severe("E3: " + id + " Error Code:" + errorCode + " " + errorMsg);
	}

	@Override
	public void accountDownloadEnd(String accountName)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void bondContractDetails(int reqId, ContractDetails contractDetails)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void contractDetails(int reqId, ContractDetails contractDetails)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void contractDetailsEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void currentTime(long time)
	{
		String currentTime = IBDataFormatter.format(new Date( time*1000));
		logger.info("CurrnetTime:" + currentTime );

		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
	    	m_client.reqHistoricalData( id_history[i], contracts[i],  
	    			currentTime, //"20091015 16:26:44",
	                //"86400 S", "1 min", "MIDPOINT", 1, 1);	// 300 bars               
            		"3 D", "1 min", "MIDPOINT", 1, 1);	// 300 bars               
		}
		
	}
	
	
	@Override
	public void deltaNeutralValidation(int reqId, UnderComp underComp)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execDetails(int reqId, Contract contract, Execution execution)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execDetailsEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fundamentalData(int reqId, String data)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalData(int reqId, String date, double open, double high, double low, double close,
			int volume, int count, double WAP, boolean hasGaps)
	{
		//System.out.println("id:" + reqId + " Date:" + date + " Open:" + open + " High" + high + " Low" + low + " Close:" + close + " Volumn:" + volume + " count:" + count + 
		//		" WAP:" + WAP + " hasGaps:" + hasGaps);
		if (( MODE == Constants.RECORD_MODE ) || ( MODE == Constants.REAL_MODE )) 
			historyData.add(reqId + "," + date + "," + open + "," + high + "," + low + "," + close + "," + 
					volume + "," + count + ","  + WAP + "," + hasGaps );
		
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if (reqId == id_history[i])
			{
				if (date.indexOf("finished-") == -1)
				{
					QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
				
					createOrUpdateQuotes(tradeManager[i][TRADE_1M].qts, Constants.CHART_1_MIN, data );
					createOrUpdateQuotes(tradeManager[i][TRADE_3M].qts, Constants.CHART_3_MIN, data );
					createOrUpdateQuotes(tradeManager[i][TRADE_5M].qts, Constants.CHART_5_MIN, data );
					createOrUpdateQuotes(tradeManager[i][TRADE_10M].qts, Constants.CHART_10_MIN, data );
					createOrUpdateQuotes(tradeManager[i][TRADE_15M].qts, Constants.CHART_15_MIN, data );
					createOrUpdateQuotes(tradeManager[i][TRADE_30M].qts, Constants.CHART_30_MIN, data );
					createOrUpdateQuotes(tradeManager[i][TRADE_60M].qts, Constants.CHART_60_MIN, data );
					
				}
				else
				{
					quotes_received[i] = true;
					if (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.RECORD_MODE ))  
					{
						if (!skipSymbol(i))
						{
							m_client.reqRealTimeBars(id_realtime[i], contracts[i], 60, "MIDPOINT", true);
							// m_client.reqMktData(199, m_contract, null, false);
							logger.info(contracts[i].m_symbol + "." + contracts[i].m_currency + " started");
						}
					}

						
				}
				break;
			}
		}
		
		if (( historySaved == false) && (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.RECORD_MODE )))
		{
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
				if ( quotes_received[i] == false )
					return;
				
			// only save when everything is in place
			saveHistoryData();
			historySaved = true;
		}
	}

	
	@Override
	public void managedAccounts(String accountsList)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void nextValidId(int orderId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void openOrder(int orderId, Contract contract, Order order, OrderState orderState)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void openOrderEnd()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice,
			int permId, int parentId, double lastFillPrice, int clientId, String whyHeld)
	{
		if ("Filled".equals(status)) //&& (orderId > Constants.ORDER_ID_STOP_OFFSET))
		{
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				for ( int j = 0; j < TOTAL_TRADES; j++ )
				{	
					//tradeManager[i][j].checkStopTriggered(orderId);
					tradeManager[i][j].checkOrderFilled(orderId, filled);
				}
			}
		}
		
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{
		String timeStr = IBDataFormatter.format(new Date(time*1000));
		String hr = timeStr.substring(9,12).trim();
		String min = timeStr.substring(13,15);
		String sec = timeStr.substring(16,18);

		//logger.info(reqId + " Time:" + timeStr + " Open:" + open + " High" + high + " Low" + low + " Close:" + close);
		if ((( MODE == Constants.REAL_MODE )||( MODE == Constants.RECORD_MODE )) && !timeout )
			saveRealTimeData(reqId + "," + time + "," + open + "," + high + "," + low + "," + close + "," + volume + "," + wap + "," + count);
		
		if ( MODE == Constants.RECORD_MODE )
			return;

		int hour = new Integer(hr);
		int second = new Integer(sec);
		int minute = new Integer(min);
		
		// this will set the maximum loss as -1000
		if (stop_loss == true )
		{
			if (( timeout == false )&& ( second == 0 ) && ( report() == true ))
			{
				//if ( MODE == Constants.REAL_MODE ) 
				{
					logger.warning("Stop loss triggered, halt trading new positions");
					timeout = true;
					//closeAllPositionsByMarket();
					//sleep(2);
				}
				//System.exit(0);
			}
		}

		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if (reqId == id_realtime[i])
			{
				if (skipSymbol(i))
					return;

				tradeManager[i][TRADE_1M].currentTime = timeStr;
				//tradeManager[i][TRADE_3M].currentTime = timeStr;
				tradeManager[i][TRADE_5M].currentTime = timeStr;
				//tradeManager[i][TRADE_10M].currentTime = timeStr;
				//tradeManager[i][TRADE_15M].currentTime = timeStr;
				tradeManager[i][TRADE_30M].currentTime = timeStr;
				//tradeManager[i][TRADE_60M].currentTime = timeStr;
				
				QuoteData data = new QuoteData(timeStr, open, high, low, close, 0, 0, wap, false);

				createOrUpdateQuotes(tradeManager[i][TRADE_1M].qts, Constants.CHART_1_MIN, data );
				//createOrUpdateQuotes(tradeManager[i][TRADE_3M].qts, Constants.CHART_3_MIN, data );
				createOrUpdateQuotes(tradeManager[i][TRADE_5M].qts, Constants.CHART_5_MIN, data );
				//createOrUpdateQuotes(tradeManager[i][TRADE_10M].qts, Constants.CHART_10_MIN, data );
				//createOrUpdateQuotes(tradeManager[i][TRADE_15M].qts, Constants.CHART_15_MIN, data );
				createOrUpdateQuotes(tradeManager[i][TRADE_30M].qts, Constants.CHART_30_MIN, data );
				//createOrUpdateQuotes(tradeManager[i][TRADE_60M].qts, Constants.CHART_60_MIN, data );

				
				//if ( i != 1)
				//return;

				// 5 minute Trade
				if ((tradeManager[i][TRADE_5M].trade == null) && (tradeManager[i][TRADE_5M].inTradingTime(hour, minute)))  
				{
					tradeManager[i][TRADE_5M].trade = tradeManager[i][TRADE_5M].checkHigherHighSetup( data, 20, tradeManager[i][TRADE_30M].getQuoteData(), tradeManager[i][TRADE_1M].getQuoteData());
				}

				if ( tradeManager[i][TRADE_5M].trade != null )  
				{
					if (tradeManager[i][TRADE_5M].inTradingTime(hour, minute) && (tradeManager[i][TRADE_5M].trade.status.equals(Constants.STATUS_OPEN)))
						tradeManager[i][TRADE_5M].trackTradeEntry( data,tradeManager[i][TRADE_1M].getQuoteData(), tradeManager[i][TRADE_30M].getQuoteData());
					else if (tradeManager[i][TRADE_5M].trade.status.equals(Constants.STATUS_PLACED))
						tradeManager[i][TRADE_5M].trackTradeTarget(data, tradeManager[i][TRADE_1M].getQuoteData(), tradeManager[i][TRADE_30M].getQuoteData());

					tradeManager[i][TRADE_5M].report(data.close);

				}  
				

				/* 15 minute Trade
				if ( !timeout && tradeManager[i][TRADE_15M].trade == null )  
				{
					tradeManager[i][TRADE_15M].trade = tradeManager[i][TRADE_15M].checkHigherHighSetup( data, 20, tradeManager[i][TRADE_60M].getQuoteData(), tradeManager[i][TRADE_1M].getQuoteData());
				}
				if ( tradeManager[i][TRADE_5M].trade != null )  
				{
					if (!timeout && (tradeManager[i][TRADE_15M].trade.status.equals(Constants.STATUS_OPEN)))
						tradeManager[i][TRADE_15M].trackReveralTradeEntry_bak( data,tradeManager[i][TRADE_60M].getQuoteData());
					else if (tradeManager[i][TRADE_15M].trade.status.equals(Constants.STATUS_PLACED))
						tradeManager[i][TRADE_15M].trackTradeTarget(data );

					tradeManager[i][TRADE_15M].report(data.close);

				}  */

				break;
			}
		}
	}

	
	@Override
	public void receiveFA(int faDataType, String xml)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance,
			String benchmark, String projection, String legsStr)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerDataEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerParameters(String xml)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints,
			double impliedFuture, int holdDays, String futureExpiry, double dividendImpact,
			double dividendsToExpiry)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickGeneric(int tickerId, int tickType, double value)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickPrice(int tickerId, int field, double price, int canAutoExecute)
	{
		/*
		System.out.println("tickPrice: tickId=" + tickerId + " field:" + field + " price:" + price + " canAutoExecute:" + canAutoExecute);
		
		Trade trade = null;
		
		//if ( field == 4 )
		{
			trade = tradeManager.getTrade();
			if ( trade == null )
			{
				if (( Constants.ACTION_SELL.equals(command))&& ( field == 2 )) // ask
				{
					// ask price < last bar.low
					if (chart_period == 1)
					{
						sellTrigger( price, quotes1m);
					}
					else if (chart_period == 3)
					{
						sellTrigger( price, quotes3m);
					}
				}
				else if (( Constants.ACTION_BUY.equals(command)) && ( field == 1))
				{
					// bid price > lastbar.high
					if (chart_period == 1)
					{
						buyTrigger( price, quotes1m);
					}
					else if (chart_period == 3)
					{
						buyTrigger( price, quotes3m);
					}
				}
			}
			else
			{
				if ( trade.action.equals(Constants.ACTION_SELL))
				{
					if ( price > trade.stop )
					{
						tradeManager.closeTrade();
						m_messagePanel.add("Trade " + symbol + " stopped out");
					}
				}
				else if ( trade.action.equals(Constants.ACTION_BUY))
				{
					if ( price < trade.stop )
					{
						tradeManager.closeTrade();
						m_messagePanel.add("Trade " + symbol + " stopped out");
					}
				}
			}
		}*/
	}

	@Override
	public void tickSize(int tickerId, int field, int size)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickSnapshotEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickString(int tickerId, int tickType, String value)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountTime(String timeStamp)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountValue(String key, String value, String currency, String accountName)
	{
		// TODO Auto-generated method stub
		if ("NetLiquidation".equals(key))
		{
			if (accOpenBalance == 0 )
			{
				// first one
				logger.warning("AccountName:" + accountName + " open balance is " + value + " " + currency);
				accOpenBalance = new Double(value);
			}
			else 
			{
				double balance = new Double( value );
				if ( balance < accOpenBalance * 0.95 )
				{
					logger.warning("AccountName:" + accountName + " is below 5% of the open account value, close all positions" );
					timeout = true;
					closeAllPositionsByMarket();
					System.exit(0);
				}
			}
		}
		
	}

	@Override
	public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side,
			double price, int size)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue,
			double averageCost, double unrealizedPNL, double realizedPNL, String accountName)
	{
		// close each position
		if (( position != 0 ) && ( closeAccount == true ))
		{
			logger.warning("To close this position: Contract:" + contract.m_symbol + "." + contract.m_currency + " position size: " + position );

			contract.m_exchange = "IDEALPRO";
			closeAccountTradeManager.closePosition( contract, position>0?Constants.ACTION_SELL:Constants.ACTION_BUY, Math.abs(position));
		}
		
	}

	@Override
	public void connectionClosed()
	{
		// TODO Auto-generated method stub
		
	}


	private void createContract(int idHist, int idReal, String symbol, String type, String exchange, String currency, boolean minuteSupport, int tradeSize, double pipsize, int stopSize, int pricetype, 
			int timeZone )
	{
		Contract contract = contracts[totalSymbolIndex];
    	contract.m_conId = connectionId++;
    	contract.m_symbol = symbol;
        contract.m_secType = type;
        contract.m_exchange = exchange;
        contract.m_currency = currency;
       
        double exchangerate = exchangeRate.get(currency);
        
        id_history[totalSymbolIndex] = idHist;
        id_realtime[totalSymbolIndex] = idReal;
        
       	tradeManager[totalSymbolIndex][0] = new SF108TradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][0].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][0].CHART = Constants.CHART_1_MIN;
       	tradeManager[totalSymbolIndex][0].TARGET_PROFIT_SIZE = 20;
       	tradeManager[totalSymbolIndex][0].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][0].priceType = pricetype;
       	tradeManager[totalSymbolIndex][0].POSITION_SIZE = 250000;
       	tradeManager[totalSymbolIndex][0].oneMinutChart = minuteSupport;
       	tradeManager[totalSymbolIndex][0].FIXED_STOP = 7;
       	tradeManager[totalSymbolIndex][0].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][0].currency = currency;
       	tradeManager[totalSymbolIndex][0].exchangeRate = exchangerate;
                        	
       	tradeManager[totalSymbolIndex][1] = new SF108TradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][1].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][1].CHART = Constants.CHART_3_MIN;
       	tradeManager[totalSymbolIndex][1].TARGET_PROFIT_SIZE = 35;
       	tradeManager[totalSymbolIndex][1].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][1].priceType = pricetype;
       	tradeManager[totalSymbolIndex][1].POSITION_SIZE = 125000;
       	tradeManager[totalSymbolIndex][1].FIXED_STOP = 10;
       	tradeManager[totalSymbolIndex][1].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][1].currency = currency;
       	tradeManager[totalSymbolIndex][1].exchangeRate = exchangerate;
        	
       	tradeManager[totalSymbolIndex][2] = new SF108TradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][2].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][2].CHART = Constants.CHART_5_MIN;
       	tradeManager[totalSymbolIndex][2].TARGET_PROFIT_SIZE = 50;
       	tradeManager[totalSymbolIndex][2].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][2].priceType = pricetype;
       	tradeManager[totalSymbolIndex][2].POSITION_SIZE = tradeSize;//200000;//tradeSize;//50000;//200000;
       	tradeManager[totalSymbolIndex][2].FIXED_STOP = stopSize;
       	tradeManager[totalSymbolIndex][2].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][2].currency = currency;
       	tradeManager[totalSymbolIndex][2].exchangeRate = exchangerate;
       	tradeManager[totalSymbolIndex][2].tickId = idReal + 100;
        	
       	tradeManager[totalSymbolIndex][3] = new SF108TradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][3].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][3].CHART = Constants.CHART_10_MIN;
       	tradeManager[totalSymbolIndex][3].TARGET_PROFIT_SIZE = 35;
       	tradeManager[totalSymbolIndex][3].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][3].priceType = pricetype;
       	tradeManager[totalSymbolIndex][3].POSITION_SIZE = 50000;
       	tradeManager[totalSymbolIndex][3].FIXED_STOP = 21;
       	tradeManager[totalSymbolIndex][3].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][3].currency = currency;
       	tradeManager[totalSymbolIndex][3].exchangeRate = exchangerate;
        
       	tradeManager[totalSymbolIndex][4] = new SF108TradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][4].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][4].CHART = Constants.CHART_15_MIN;
       	tradeManager[totalSymbolIndex][4].TARGET_PROFIT_SIZE = 40;
       	tradeManager[totalSymbolIndex][4].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][4].priceType = pricetype;
       	tradeManager[totalSymbolIndex][4].POSITION_SIZE = tradeSize;
       	tradeManager[totalSymbolIndex][4].FIXED_STOP = 28;
       	tradeManager[totalSymbolIndex][4].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][4].currency = currency;
       	tradeManager[totalSymbolIndex][4].exchangeRate = exchangerate;

       	tradeManager[totalSymbolIndex][5] = new SF108TradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][6] = new SF108TradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       
       	tradeManager[totalSymbolIndex][2].largerTimeFrameTraderManager = tradeManager[totalSymbolIndex][6];

       	// increase one for next
        totalSymbolIndex++;
	}

	
	public void createNewQuoteEntry( Vector<QuoteData> quotes, QuoteData data )
	{
		if ( quotes.size() == 0 )
		{
			data.pos = 0;
			quotes.add( data );
		}
		else
		{
			data.pos = quotes.lastElement().pos + 1;
			quotes.add(data);
		}
		
	}
	

	
	private void updateLastQuoteEntry( Vector<QuoteData> quotes, QuoteData data )
	{
		if ( quotes.size() > 0 )
		{
			QuoteData lastElem = (QuoteData) quotes.lastElement();
			
			/*  this does not work
			if ((( data.close - data.low ) > 5 * pipSize) || (( data.high - data.close ) > 5 * pipSize) )
			{
				logger.info( "pipsize is " + pipSize );
				logger.info("invalid quote" + data.high + " " + data.low + " " + data.close);
				return;
			}*/

			
			if (data.high > lastElem.high)
				lastElem.high = data.high;
			if (data.low < lastElem.low )
				lastElem.low = data.low;
			lastElem.close = data.close;
			//lastElem.time = data.time; do not over ride the time
		}
	}
	
	
	public void createOrUpdateQuotes( HashMap<String, QuoteData> quotes, int CHART, final QuoteData data )
	{
		int minute = new Integer(data.time.substring(13,15));

		if ( CHART == Constants.CHART_3_MIN )
			minute = minute - minute%3;
		else if ( CHART == Constants.CHART_5_MIN )
			minute = minute - minute%5;
		else if ( CHART == Constants.CHART_10_MIN )
			minute = minute - minute%10;
		else if ( CHART == Constants.CHART_15_MIN )
			minute = minute - minute%15;
		else if ( CHART == Constants.CHART_30_MIN )
			minute = minute - minute%30;
		else if ( CHART == Constants.CHART_60_MIN )
			minute = 0;
		
		String minStr = new Integer(minute).toString();
		if ( minute < 10 )
			minStr = "0" + new Integer(minute).toString();
		else
			minStr = new Integer(minute).toString();
		
		String indexStr = data.time.substring(0,13) + minStr;
		
		QuoteData qdata = quotes.get(indexStr);
		if ( qdata == null )
		{
			qdata = new QuoteData();
			qdata.open = data.open;
			qdata.high = data.high;
			qdata.low = data.low;
			qdata.close = data.close;
			qdata.time = indexStr;
		}
		else
		{
			qdata.close = data.close;
				
			if (data.high > qdata.high)
				qdata.high = data.high;
			if (data.low < qdata.low )
				qdata.low = data.low;

		}
		
		quotes.put( indexStr, qdata);
	}

	
	public void replaceQuotes( Hashtable<String, QuoteData> quotes, int CHART, QuoteData data )
	{
		int minute = new Integer(data.time.substring(13,15));

		if ( CHART == Constants.CHART_3_MIN )
			minute = minute - minute%3;
		else if ( CHART == Constants.CHART_5_MIN )
			minute = minute - minute%5;
		else if ( CHART == Constants.CHART_10_MIN )
			minute = minute - minute%10;
		else if ( CHART == Constants.CHART_15_MIN )
			minute = minute - minute%15;
		else if ( CHART == Constants.CHART_30_MIN )
			minute = minute - minute%30;
		else if ( CHART == Constants.CHART_60_MIN )
			minute = 0;
		
		String minStr = new Integer(minute).toString();
		if ( minute < 10 )
			minStr = "0" + new Integer(minute).toString();
		else
			minStr = new Integer(minute).toString();
		
		String indexStr = data.time.substring(0,12) + minStr;
		
		quotes.put( indexStr, data);
	}

	
	
	
	private void initialize()
	{
		contracts = new Contract[TOTAL_SYMBOLS];
		quoteFirstTime = new boolean[TOTAL_SYMBOLS];
		tradeManager = new SF108TradeManager[TOTAL_SYMBOLS][TOTAL_TRADES];	
    	quotes_received = new boolean[TOTAL_SYMBOLS];
 		
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			contracts[i] = new Contract();
	}
	
	
	private void saveHistoryData()
	{
		try {
			FileWriter fw = new FileWriter(saveHistoryFile);
			
			Iterator<String> it = historyData.iterator();
			
			while ( it.hasNext())
			{
				String h = (String)it.next();
				fw.append(h + "\n");
			}
			
			fw.close();
		} 
		catch (IOException e){
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
	private void saveRealTimeData(String realTimeData )
	{
		try {
			FileWriter fw = new FileWriter(saveRealTimeFile, true );
			fw.append(realTimeData + "\n");
			fw.close();
		} 
		catch (IOException e){
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
	
	private boolean report()
	{
	    try
	    {
	    	FileWriter fstream = new FileWriter(reportFileName);
	    	BufferedWriter out = new BufferedWriter(fstream);

	    	out.write("Trade Report " + IBDataFormatter.format( new Date()) + "\n\n");
	    	double totalProfit = 0;
	    	int totalTrade = 0;
	    	for ( int i = 0; i < TOTAL_SYMBOLS; i++ )
	    	{
	    		for ( int j = 0; j < TOTAL_TRADES-1; j++)
	    		{
	    			if (( tradeManager[i][j].portofolioReport != null ) && (!tradeManager[i][j].portofolioReport.equals("")))
	    			{
	    				out.write(tradeManager[i][j].portofolioReport + "\n");
	    				totalProfit += tradeManager[i][j].totalProfit;
	    				totalTrade += tradeManager[i][j].totalTrade;
	    			}
	    		}
	    	}

			out.write("\n\nTOTAL PROFIT: " + totalProfit + " USD          TOTAL TRADES: " + totalTrade + "\n" );

	    	out.close();
	    	
	    	if ( totalProfit < -1000 )
	    		return true;
	   }
	   catch (Exception e)
	   {
		   e.printStackTrace();
   	   }

	   return false;
	}
	
	private void readHistoryData( String historyFileName )
	{
		String reqId;
		String date;
		String open;
		String high;
		String low;
		String close;
		String volume;
		String count;
		String WAP;
		String hasGaps;
		
		try
		{
			//csv file containing data
			String strFile = historyFileName;
			BufferedReader br = new BufferedReader( new FileReader(strFile));

			String strLine = "";
			StringTokenizer st = null;
			int lineNumber = 0;
		
			//read comma separated file line by line
			while( (strLine = br.readLine()) != null)
			{
				lineNumber++;
				//break comma separated line using ","
				st = new StringTokenizer(strLine, ",");
				
				//System.out.println(strLine);
				//while(st.hasMoreTokens()) 
				{
					reqId = st.nextToken();
					date = st.nextToken();
					if (date.indexOf("finished-") != -1)
						//date = date.substring(9);
						continue;
					open = st.nextToken();
					high = st.nextToken();
					low = st.nextToken();
					close = st.nextToken();
					//close = close.substring(0, close.length()-2);
					volume = st.nextToken();
					count = st.nextToken();
					WAP = st.nextToken();
					hasGaps = st.nextToken();
				}
				
			historicalData(new Integer(reqId), date, new Double(open), new Double(high), new Double(low), new Double(close),
						new Integer(volume), new Integer(count), new Double(WAP), new Boolean(hasGaps));

			}
			
			logger.warning(lineNumber + " history read");
		
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	
	private void readRealTimeData( String realTimeFileName )
	{
		String reqId, time, open, high, low, close,volume,wap, count;
		
		try
		{
			//csv file containing data
			String strFile = realTimeFileName;
			BufferedReader br = new BufferedReader( new FileReader(strFile));

			String strLine = "";
			StringTokenizer st = null;
			int lineNumber = 0;
		
			//read comma separated file line by line
			while( (strLine = br.readLine()) != null)
			{
				lineNumber++;
				//break comma separated line using ","
				st = new StringTokenizer(strLine, ",");
				//if ( strLine.length() < 20 )
				//	continue;
				//logger.warning("strLine:" + strLine);
				
				//while(st.hasMoreTokens()) 
				try
				{
					reqId = st.nextToken();
					time = st.nextToken();
					open = st.nextToken();
					high = st.nextToken();
					low = st.nextToken();
					close = st.nextToken();
					//volume = st.nextToken();
					//wap = st.nextToken();
					//count = st.nextToken();
				}
				catch( RuntimeException e )
				{
					continue;
				}
				
				//realtimeBar(new Integer(reqId), new Long(time), new Double(open), new Double(high), new Double(low), new Double(close),
				//		new Long(volume), new Double(wap), new Integer(count));
				if ( time.indexOf("finished-") == -1 )
					realtimeBar(new Integer(reqId), new Long(time), new Double(open), new Double(high), new Double(low), new Double(close),
						new Long(0), new Double(0), new Integer(0));

			}
			
			logger.warning(lineNumber + " real time data read");
		
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}


	@Override
	public void tickOptionComputation(int tickerId, int field,
			double impliedVol, double delta, double modelPrice,
			double pvDividend) {
		// TODO Auto-generated method stub
		
	}


	public void sleep ( int min )
	{
		try{
			Thread.sleep(min*60000);
		}
		catch ( Exception e)
		{
			e.printStackTrace();
		}
	}

	protected void finalize() throws Throwable
	{
	    m_client.eDisconnect();
	} 	


	
	private boolean skipSymbol ( int i )
	{
		if (( i == 2 ) || (i == 5 ) || ( i == 6 ) || (i==7) || (i==8) || (i==10) || ( i == 11))
			return true;
		else
			return false;

	}
	// TODO: move stop after it reached 14 pips profit perhaps
	
}


// TODO: Start to take profit when the move is getting stagging
// TODO: try to entry on one minute chart when there is sochastic diverage
// TODO: Move stop after entry?  this higher high/lower low alreay built in though
// TODO: stop and entry disconnect, perhaps to make stop 2 times larger and cancel later?
// TODO: when reversal, only looking for shallow ones
// TODO: the same bar triggered casued a "breaking stop", need the stop to wait for 1 more bar?  GBP.USD  nOV 18



/*
Trade Report 20101104  08:58:39

EUR.GBP 5MIN: 0.879825
RST 20101104  05:20:15 SELL 200000 0.879175
    20101104  05:23:10 BUY 200000 0.8797    Realized Profit: -105
CNT 20101104  05:20 BUY 200000 0.8797
    20101104  06:09:15 SELL 200000 0.879825    Realized Profit: 25
EUR.GBP Loss/Profit: -80 GBP   -124.34 USD

EUR.CHF 5MIN: 1.378375
RST 20101104  06:45:50 SELL 200000 1.378325
    20101104  06:51:25 BUY 200000 1.3796    Realized Profit: -255
CNT 20101104  06:50 BUY 200000 1.3796
Unrealized Profit: -245
EUR.CHF Loss/Profit: -500 CHF   -487.46 USD

EUR.NZD 5MIN: 1.80255
RST 20101104  04:51:05 SELL 200000 1.8026
    20101104  05:03:35 BUY 200000 1.80385    Realized Profit: -250
CNT 20101104  05:00 BUY 200000 1.80385
    20101104  05:06:25 SELL 200000 1.8024    Realized Profit: -290
EUR.NZD Loss/Profit: -540 NZD   -379.55 USD

EUR.AUD 5MIN: 1.40515
RST 20101104  02:05:15 BUY 200000 1.40545
    20101104  02:23:15 SELL 200000 1.405    Realized Profit: -90
CNT 20101104  02:20 SELL 200000 1.405
    20101104  02:52:15 BUY 200000 1.40515    Realized Profit: -30
EUR.AUD Loss/Profit: -120 AUD   -106.47 USD

GBP.AUD 5MIN: 1.600125
RST 20101104  05:21:05 BUY 200000 1.6035
    20101104  05:40:40 SELL 200000 1.60235    Realized Profit: -230
CNT 20101104  05:40 SELL 200000 1.60235
    20101104  06:38:10 BUY 200000 1.60025    Realized Profit: 420
GBP.AUD Loss/Profit: 190 AUD   168.57 USD

GBP.CAD 5MIN: 1.630025
RST 20101104  04:17:45 SELL 200000 1.625875
    20101104  04:40:35 BUY 200000 1.6271    Realized Profit: -245
CNT 20101104  04:40 BUY 200000 1.6271
    20101104  04:54:55 SELL 200000 1.6257    Realized Profit: -280
GBP.CAD Loss/Profit: -525 CAD   -496.21 USD

GBP.CHF 5MIN: 1.57275
RST 20101104  02:03:05 BUY 200000 1.5669
    20101104  03:04:35 SELL 200000 1.5666    Realized Profit: -60
CNT 20101104  03:00 SELL 200000 1.5666
    20101104  03:09:30 BUY 200000 1.56765    Realized Profit: -210
GBP.CHF Loss/Profit: -270 CHF   -263.23 USD

GBP.JPY 5MIN: 131.2
RST 20101104  05:35:00 SELL 200000 131.035
    20101104  05:43:35 BUY 200000 131.185    Realized Profit: -30000
CNT 20101104  05:40 BUY 200000 131.185
    20101104  05:53:10 SELL 200000 131.05    Realized Profit: -27000
GBP.JPY Loss/Profit: -57000 JPY   -673.78 USD

CHF.JPY 5MIN: 83.4225
RST 20101104  04:55:45 SELL 200000 83.7075
    20101104  04:57:55 BUY 200000 83.76    Realized Profit: -10500
CNT 20101104  04:55 BUY 200000 83.76
    20101104  05:55:10 SELL 200000 83.77    Realized Profit: 2000
CHF.JPY Loss/Profit: -8500 JPY   -100.48 USD



TOTAL PROFIT: -2462.9425979975713 USD
*/