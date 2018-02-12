package tao.trading.strategy;


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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import tao.trading.Command;
import tao.trading.CommandAction;
import tao.trading.Constants;
import tao.trading.EmailSender;
import tao.trading.QuoteData;
import tao.trading.Trade;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;
import com.thoughtworks.xstream.XStream;


// this is to add 1m 20 ma 2 bar entry
// prev version SF108


public class SF302 extends SF implements EWrapper
{
	String IB_ACCOUNT = "";//"DU31237";
	private static Logger logger;
	protected EClientSocket m_client = new EClientSocket(this);
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
	protected SimpleDateFormat DateFormatter = new SimpleDateFormat("yyyyMMdd");
	protected SimpleDateFormat InternalDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm");
	protected SF302TradeManager[] tradeManager;
	TradeManager2 closeAccountTradeManager;
	Contract[] contracts;
	int[] id_history;
	boolean init_history = false;
	boolean[] quotes_received; 
	int[] id_realtime;
	boolean[] id_realtime_started;
	long lastRealTimeRequest=0;
	boolean[] firstQuote;
	int totalSymbolIndex = 0;
	int connectionId = 100;
	String IB_ID;
	boolean timeout= false;
	int MODE = Constants.REAL_MODE;
	int TIME_PERIOD = TRADE_15M;
	double accOpenBalance = 0;
	Vector<String> historyData = new Vector<String>();
	HashMap<String, Double> exchangeRate = new HashMap<String, Double>();
	String saveRealTimeFile = "realtimeData.csv";
	String saveHistoryFile = "historyData.csv";
	String reportFileName = "report.txt";
	String blockSymbols = null;
	String noReverseSymbols = null;
	String outputDir;
	boolean dataBroken,linkLost;
	boolean historySaved;
	String updatePortfolioCmd;
	boolean init_quote_update = false;
	int last_hour = -1;
	int last_errorcode = 0;
	int not_received_realtime = 0;
	long systemStartTime, lastRealTimeUpdateTime, lastTickUpdateTime;
	XStream xstream;
	String market;

	QuoteData prevData[];

	
	boolean stop_loss = false;
	
	
	/*  News Hour
	US 			8:30 - 10:00
	Japan		18:50 - 23:30
	Canada		7:00 - 8:30
	UK			2:00 - 4:30
	Germany		2:00 - 6:00
	France		2:45 - 4:00
	Switzerland	1:45 - 5:30
	Australia	17:30 - 19:30
	*/
	
	
	public static void main(String[] args)
	{
		SF302 open;
		String account;
		int port;
		String conn_id;
		String market;
		float exp;
		String logdir = null;
		int mode = 0;

		//Test DU31237 7496 81 EUR 80000 100000 120000 C:/trade/data/101/historyData.csv C:/trade/data/101/realtimeData.csv report.txt
		if ( "Test".equalsIgnoreCase(args[0]))
    	{
    		account = args[1];
    		port = new Integer(args[2]);
    		conn_id = args[3];
    		market = args[4];
    		exp = new Float(args[5]);
    		String histFileName = args[6];
    		String realFileName = args[7];
    		String reportFileName = args[8];
    		mode = Constants.TEST_MODE;
    		
    		open = new SF302(account, port, conn_id, market, Constants.TEST_MODE, exp, logdir);
    		open.reportFileName = reportFileName;
    		open.readHistoryData( histFileName );
    		open.readRealTimeData( realFileName );
			open.report(true);
			open.listAllTradeEvent();
    		return;

    	}
		else	
    	{	
			if ( "Signal".equalsIgnoreCase(args[0]))
	    	{
				// run real quote, but does not place trade, give signals only
	    		//TestRun DU99006 7496 72 EUR 1 330 C:/trade/data/101/historyData.csv C:/trade/data/101/realtimeData.csv report.txt
	    		account = args[1];
	    		port = new Integer(args[2]);
	    		conn_id = args[3];
	    		market = args[4];
	    		exp = new Float(args[5]);
	    		logdir = args[6];
	    		mode = Constants.SIGNAL_MODE;
	    		
	    		open = new SF302(account, port, conn_id, market, Constants.SIGNAL_MODE, exp, logdir);
	    	}
			else
			{	
				// real mode 
	    		//DU99006 7496 72 EUR 80000 100000 120000 330 C:/trade/data/101/historyData.csv C:/trade/data/101/realtimeData.csv report.txt
	    		account = args[0];
	    		port = new Integer(args[1]);
	    		conn_id = args[2];
	    		market = args[3];
	    		exp = new Float(args[4]);
	    		logdir = args[5];
	    		mode = Constants.REAL_MODE;
	    		
	    		open = new SF302(account, port, conn_id, market, Constants.REAL_MODE, exp, logdir);
			}
			
    		open.systemStartTime = new Date().getTime();
    		open.run();
    		
    		boolean save = false;
    		while ( true  )
			{	
    			
    			/**************************************
    			 * Read External Commands
    			 *************************************/
    			for ( int j = 0; j < 60; j++)
    			{	
    				open.readExternalCommand();
    				open.sleep(0.1);
    			}
    			
    			System.out.println();
    			System.out.println(open.getTradeList());
    			
				
    			Date now = new Date();
				long nowl = now.getTime()/1000;

				// back up saving in case server crash, save every 10 minutes
				for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
				{
					open.tradeManager[i].saveTradeData(open.xstream);
				}
				open.saveTradeOpportunities();
				
				
				int notReceiveMin = (int)((nowl - open.lastRealTimeUpdateTime )/60);
				if ((notReceiveMin >= 20 ) && (( nowl - open.systemStartTime/1000) > 300))
				{
					logger.warning("Not detect real time data for " + notReceiveMin + " minutes");
					open.not_received_realtime = notReceiveMin;
					
					//if (  notReceiveMin == 30 )
					if ( ( notReceiveMin % 30 ) == 0 )
					{	
						EmailSender es = EmailSender.getInstance();
						es.sendYahooMail("302 not detected realtime data for " + notReceiveMin + " minutes", "sent from automated trading system");
						for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
						{
							logger.warning("request real time bar at " + notReceiveMin + " minutes");
							//open.id_realtime[i] += 50;//open.TOTAL_SYMBOLS;
							open.m_client.reqRealTimeBars(open.id_realtime[i], open.contracts[i], 60, "MIDPOINT", true);
						}
					}
				}

				/*
				notReceiveMin = (int)((nowl - open.lastTickUpdateTime/1000)/60);
				if ((notReceiveMin >= 20 ) && (( nowl - open.systemStartTime/1000) > 300))
				{
					logger.warning("Not detect tick data for " + notReceiveMin + " minutes");
					
					//if (  notReceiveMin == 30 )
					if ( ( notReceiveMin % 30 ) == 0 )
					{	
						EmailSender es = EmailSender.getInstance();
						es.sendYahooMail("not detected ticker data for " + notReceiveMin + " minutes", "sent from automated trading system");
						for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
						{
							logger.warning("request ticker bar at " + notReceiveMin + " minutes");
							open.tradeManager[i].tickId += 50;//open.TOTAL_SYMBOLS;
							open.m_client.reqMktData(open.tradeManager[i].tickId, open.contracts[i], null, false);  // 1 = bid, 2 = ask, 4 = last, 6 = high, 7 = low, 9 = close,
						}
					}
				}*/

				Calendar calendar = Calendar.getInstance();
				calendar.setTime( now );
				int weekday = calendar.get(Calendar.DAY_OF_WEEK);
				int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
				int minute=calendar.get(Calendar.MINUTE);
				
				if (( open.last_hour != -1 ) && ( hour_of_day != 0 ) && ( hour_of_day != open.last_hour ))   // 0 is 12am
				{
					// garbage collection every hour
					System.gc();
					
					
					String currentPositions = "";
					for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
						if ( open.tradeManager[i].trade != null )
							currentPositions += open.tradeManager[i].symbol +":"+ open.tradeManager[i].trade.status + " " + open.tradeManager[i].trade.action + " " + open.tradeManager[i].trade.POSITION_SIZE +" ";
		        	if ( currentPositions.length() > 5 )
		        		logger.warning(currentPositions);

					//System.out.println("QuoteUpdate:" + weekday + " " + hour_of_day);
					//open.checkDataBroken();  do not check data broken for this as it takes too many historical quotes
				}
				
				open.last_hour = hour_of_day;
					
				if (( mode == Constants.REAL_MODE ) || ( mode == Constants.SIGNAL_MODE))
				{
					if ( weekday == Calendar.FRIDAY ) 
					{
						if ( hour_of_day >= 16 )
							break;
					}
				}
				else
				{
					if (( hour_of_day == 17 ) && ( minute < 10 ))
						break;
				}
			}
			
			
			open.timeout = true;
			logger.warning(args[1] + " trading time is up");
		
			if (save == true )
			{
				// exit without close all positions
				logger.warning(args[1] + " save trading status....");
				for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
				{
					open.tradeManager[i].saveTradeData(open.xstream);
				}
			}

			
    		if ( mode == Constants.REAL_MODE)
			{
				logger.warning(args[1] + " close all positions....");
				if ( open.closeAllPositionsByLimit())
					open.sleep(5);  // wait 5 minutes for all positions to close 
			
				open.closeAllPositionsByMarket();
				
				// last, cancel any outstanding orders, this has to be absolute last one as it will cancel any (new) orders too
				open.cleanOpenOrder();

			}
			
			logger.warning("Trading system shut down");
			System.exit(0);
    	}
	}

	

	
	public void run() {
		
		// this requests account update so we can calculate the portfolio
		updatePortfolioCmd = Constants.CMD_UPDATE;
		m_client.reqAccountUpdates(true,Constants.ACCOUNT);	
		
		// this starts real time bar
		m_client.reqCurrentTime();
		
	}

	public void cleanOpenOrder()
	{
		updatePortfolioCmd = Constants.CMD_CLOSE_OUTSTANDING_ORDERS;
		m_client.reqOpenOrders();
		sleep(1);
		// in clean the orders in openOrder();
	}
	
	public boolean allPositionClosed()
	{
		boolean allpositionclosed = true;
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if ( tradeManager[i].trade != null )
			{
				logger.info("trade " + i + " " + " not closed");
				allpositionclosed = false;
			}
		}
		
		return allpositionclosed;
			
	}

	
	public boolean closeAllPositionsByLimit() {
		
		logger.info("Close all positions by limit orders");
		boolean limitOrderPlaced = false;
		// to set all target as current price
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if ( tradeManager[i].trade != null )
			{
				logger.warning(tradeManager[i].symbol + "." + tradeManager[i].CHART + " trade exists");
				if (( tradeManager[i].trade.status == Constants.STATUS_OPEN ) || ( tradeManager[i].trade.status == Constants.STATUS_STOPPEDOUT )) 
				{
					logger.warning(tradeManager[i].symbol + "." + tradeManager[i].CHART + " trade status open");
					tradeManager[i].cancelLimits();
					tradeManager[i].cancelStop();
					tradeManager[i].trade = null;
				}
				else if ( tradeManager[i].trade.status == Constants.STATUS_PLACED )
				{
					logger.warning(tradeManager[i].symbol + "." + tradeManager[i].CHART + " trade status placed");
					tradeManager[i].cancelTargets();
					tradeManager[i].cancelStop();
				
					//Object[] quotes = tradeManager[i].getQuoteData();
					//int lastbar = quotes.length -1;
					
					tradeManager[i].trade.status = Constants.STATUS_EXITING;
					
					String tradeAction = null;
					if (Constants.ACTION_BUY.equals(tradeManager[i].trade.action ))
					{
						if ( tradeManager[i].lastTick_ask != 0 )
						{	
							logger.warning(tradeManager[i].symbol + "." + tradeManager[i].CHART + " last tick ask is " + tradeManager[i].lastTick_ask);
							tradeManager[i].trade.targetPrice1 = tradeManager[i].adjustPrice(tradeManager[i].lastTick_ask, Constants.ADJUST_TYPE_UP);
						}
						else
						{
							QuoteData[] quotes = tradeManager[i].getQuoteData(Constants.CHART_15);
							int lastbar = quotes.length -1;
							logger.warning(tradeManager[i].symbol + "." + tradeManager[i].CHART + " last close is " + quotes[lastbar].close);
							tradeManager[i].trade.targetPrice1 = tradeManager[i].adjustPrice(tradeManager[i].lastTick_ask, Constants.ADJUST_TYPE_UP);
						}
						tradeAction = Constants.ACTION_SELL;
					}
					else if (Constants.ACTION_SELL.equals(tradeManager[i].trade.action ))
					{
						if ( tradeManager[i].lastTick_bid != 0 )
						{
							logger.warning(tradeManager[i].symbol + "." + tradeManager[i].CHART + " last tick bid is " + tradeManager[i].lastTick_bid);
							tradeManager[i].trade.targetPrice1 = tradeManager[i].adjustPrice(tradeManager[i].lastTick_bid, Constants.ADJUST_TYPE_DOWN);
						}
						else
						{
							QuoteData[] quotes = tradeManager[i].getQuoteData(Constants.CHART_15);
							int lastbar = quotes.length -1;
							logger.warning(tradeManager[i].symbol + "." + tradeManager[i].CHART + " last close is " + quotes[lastbar].close);
							tradeManager[i].trade.targetPrice1 = tradeManager[i].adjustPrice(tradeManager[i].lastTick_bid, Constants.ADJUST_TYPE_DOWN);
						}
						tradeAction = Constants.ACTION_BUY;
					}

					tradeManager[i].trade.targetPos1 = tradeManager[i].trade.remainingPositionSize;
					tradeManager[i].trade.targetId1 = tradeManager[i].placeLmtOrder(tradeAction, tradeManager[i].trade.targetPrice1, tradeManager[i].trade.targetPos1, null);//new Long(oca).toString());
					logger.warning(tradeManager[i].symbol + "." + tradeManager[i].CHART + " orderId:" + tradeManager[i].trade.targetId1 + " place limit target " + tradeAction + " order of " + tradeManager[i].trade.targetPos1 + " @ " + tradeManager[i].trade.targetPrice1);
					limitOrderPlaced = true;
				}
			}
		}
		
		return limitOrderPlaced;
	}


	public void closeAllPositionsByMarket() {
		
		logger.warning("Close all positions by market orders");

		updatePortfolioCmd = Constants.CMD_CLOSE;
    	requestAccountUpdate();

		sleep(1);  // sleep 1  minute
		logger.warning("All position closed");

	}
	
	
	public void requestAccountUpdate()
	{
		m_client.reqAccountUpdates(true,Constants.ACCOUNT);	
		m_client.reqAccountUpdates(false,Constants.ACCOUNT);	
	}

	
	public SF302( String account, int port, String ib_id, String trade_market, int mode, float exp, String outputdir)
	{
        xstream = new XStream();
        xstream.alias("trade", Trade.class);
        
        this.market = trade_market;
		
		String today = DateFormatter.format(new Date());
		String summaryLogFile = "sf302_summary_"+  today + ".log";
		String detailLogFile = "sf302_detail_"+ today + ".log";

		if ( outputdir != null )
		{
    		this.outputDir = outputdir;

    		summaryLogFile = outputDir + summaryLogFile;
			detailLogFile = outputDir + detailLogFile;
    		saveRealTimeFile = outputDir + saveRealTimeFile;
    		saveHistoryFile = outputDir + saveHistoryFile;
    		reportFileName = outputDir + reportFileName;
		}

		
		logger = Logger.getLogger("SF302_" + account);

		
		try
		{
			boolean append = true;
			logger = Logger.getLogger("SF302_" + account);

			FileHandler handler = new FileHandler(detailLogFile, append);
			handler.setLevel(Level.FINER);
			handler.setFormatter(new SimpleFormatter());
			logger.addHandler(handler);

			// this logger creates trade summary
			
			if (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.SIGNAL_MODE ))
			{
				FileHandler handler2 = new FileHandler(summaryLogFile, append);
				handler2.setLevel(Level.WARNING);
				handler2.setFormatter(new SimpleFormatter());
				logger.addHandler(handler2);
			}

			// Add to the desired logger
			
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
	   	exchangeRate.put("EUR",0.6940);
	   	exchangeRate.put("GBP",0.6150);
	   	exchangeRate.put("CHF",0.7954);
	   	exchangeRate.put("CAD",0.9891);
	   	exchangeRate.put("AUD",1.001532344487);
	   	exchangeRate.put("NZD",1.325334);
	   	exchangeRate.put("JPY",76.6525);
	   	exchangeRate.put("USD",1.0);
	   	
		id_history = new int[50];//{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
		id_realtime = new int[50];//]{500,501,502,503,504,505,506,507,508,509,510,511,512,513,514,515,516,517,518,519,520,521,522,523};

	/*	if ("EUR".equals(trade_market))
		{	
			TOTAL_SYMBOLS = 7;//7;//11;
			initialize();
			createContract(  0, 500, "EUR", "CASH", "IDEALPRO", "USD", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_4,outputdir);                    	        // 10
			createContract(  1, 501, "EUR", "CASH", "IDEALPRO", "JPY", true,  (int)(100000*exp),   0.01, 31, Constants.PRICE_TYPE_2,outputdir);								// 12.96
			//createContract(  2, 502, "EUR", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_3,"E");//								// 9.79
			createContract(  3, 503, "EUR", "CASH", "IDEALPRO", "GBP", true,  (int)(65000*exp), 0.0001,  30, Constants.PRICE_TYPE_4,outputdir);								// 15.5
	//		createContract(  4, 504, "EUR", "CASH", "IDEALPRO", "CHF", true,  (int)(92000*exp), 0.0001, 31, Constants.PRICE_TYPE_4,outputdir);								// 10.5
			//createContract(  5, 505, "EUR", "CASH", "IDEALPRO", "NZD", false, (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_3,"E");//								// 7.82
			//createContract(  6, 506, "EUR", "CASH", "IDEALPRO", "AUD", false, (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_4,"E");//								// 10.25
			//createContract(  7, 507, "GBP", "CASH", "IDEALPRO", "AUD", false, (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_4,"E");//								// 10.24
			//createContract(  8, 508, "GBP", "CASH", "IDEALPRO", "NZD", false, (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_3,"E");//								// 7.82
			//createContract(  9, 509, "GBP", "CASH", "IDEALPRO", "USD", true,  (int)(80000*exp), 0.0001, (int)(34+stop_exp), Constants.PRICE_TYPE_4,"EU");					// 10
			//createContract( 10, 510, "GBP", "CASH", "IDEALPRO", "CAD", true,  (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_3,"E");//								// 9.79
			//createContract( 11, 511, "GBP", "CASH", "IDEALPRO", "CHF", true,  (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_4,"E");//								// 10.46
			createContract( 12, 512, "GBP", "CASH", "IDEALPRO", "JPY", true,  (int)(68000*exp),   0.01,   34, Constants.PRICE_TYPE_2,outputdir);							// 12.96
			//createContract( 13, 513, "CHF", "CASH", "IDEALPRO", "JPY", false, (int)(120000*exp),   0.01, 36, Constants.PRICE_TYPE_2,"E");									// 10.46
			createContract( 14, 514, "USD", "CASH", "IDEALPRO", "JPY", false, (int)(86000*exp),   0.01, (27), Constants.PRICE_TYPE_2,outputdir);							// 12.99
			//createContract( 15, 515, "USD", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_3,"E");//								// 9.80
			//createContract( 16, 516, "USD", "CASH", "IDEALPRO", "CHF", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_4,"E");//								// 10.46
			createContract( 17, 517, "CAD", "CASH", "IDEALPRO", "JPY", true,  (int)(77160*exp),   0.01, 30, Constants.PRICE_TYPE_2,outputdir);//								    // 12.96
			createContract( 18, 518, "AUD", "CASH", "IDEALPRO", "USD", true,  (int)(94000*exp), 0.0001, 32, Constants.PRICE_TYPE_4,outputdir);//								    // 10
		}
		else*/
		{	
			TOTAL_SYMBOLS = 21;
			initialize();
			createContract(  0, 500, "EUR", "CASH", "IDEALPRO", "USD", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_4,outputdir);                    	        // 10
			createContract(  1, 501, "EUR", "CASH", "IDEALPRO", "JPY", true,  (int)(100000*exp),   0.01, 31, Constants.PRICE_TYPE_2,outputdir);								// 12.96
			createContract(  2, 502, "EUR", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_3,outputdir);//								// 9.79
			createContract(  3, 503, "EUR", "CASH", "IDEALPRO", "GBP", true,  (int)(65000*exp), 0.0001,  30, Constants.PRICE_TYPE_4,outputdir);								// 15.5
			createContract(  4, 504, "EUR", "CASH", "IDEALPRO", "CHF", true,  (int)(92000*exp), 0.0001, 31, Constants.PRICE_TYPE_4,outputdir);								// 10.5
			createContract(  5, 505, "EUR", "CASH", "IDEALPRO", "NZD", false, (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_3,outputdir);//								// 7.82
			createContract(  6, 506, "EUR", "CASH", "IDEALPRO", "AUD", false, (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_4,outputdir);//								// 10.25
			createContract(  7, 507, "GBP", "CASH", "IDEALPRO", "AUD", false, (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_4,outputdir);//								// 10.24
			createContract(  8, 508, "GBP", "CASH", "IDEALPRO", "NZD", false, (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_3,outputdir);//								// 7.82
			createContract(  9, 509, "GBP", "CASH", "IDEALPRO", "USD", true,  (int)(80000*exp), 0.0001, 34, Constants.PRICE_TYPE_4,outputdir);					// 10
			createContract( 10, 510, "GBP", "CASH", "IDEALPRO", "CAD", true,  (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_3,outputdir);//								// 9.79
			createContract( 11, 511, "GBP", "CASH", "IDEALPRO", "CHF", true,  (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_4,outputdir);//								// 10.46
			createContract( 12, 512, "GBP", "CASH", "IDEALPRO", "JPY", true,  (int)(68000*exp),   0.01,   34, Constants.PRICE_TYPE_2,outputdir);							// 12.96
			createContract( 13, 513, "CHF", "CASH", "IDEALPRO", "JPY", false, (int)(120000*exp),   0.01, 36, Constants.PRICE_TYPE_2,outputdir);									// 10.46
			createContract( 14, 514, "USD", "CASH", "IDEALPRO", "JPY", false, (int)(86000*exp),   0.01, (27), Constants.PRICE_TYPE_2,outputdir);							// 12.99
			createContract( 15, 515, "USD", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_3,outputdir);//								// 9.80
			createContract( 16, 516, "USD", "CASH", "IDEALPRO", "CHF", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_4,outputdir);//								// 10.46
			createContract( 17, 517, "CAD", "CASH", "IDEALPRO", "JPY", true,  (int)(77160*exp),   0.01, 30, Constants.PRICE_TYPE_2,outputdir);//								    // 12.96
			createContract( 18, 518, "AUD", "CASH", "IDEALPRO", "USD", true,  (int)(94000*exp), 0.0001, 32, Constants.PRICE_TYPE_4,outputdir);//								    // 10
			createContract( 19, 518, "AUD", "CASH", "IDEALPRO", "JPY", true,  (int)(94000*exp), 0.01, 32, Constants.PRICE_TYPE_2,outputdir);//								    // 10
			createContract( 20, 518, "AUD", "CASH", "IDEALPRO", "NZD", true,  (int)(94000*exp), 0.0001, 32, Constants.PRICE_TYPE_4,outputdir);//								    // 10
		}
		
		
		
/*		
		TOTAL_SYMBOLS = 19;//11;
		initialize();
		createContract(  0, 500, "EUR", "CASH", "IDEALPRO", "USD", true,  (int)(100000*exp), 0.0001, (int)(30), Constants.PRICE_TYPE_4,outputdir);             	//- 30   	6705 	67
		createContract(  1, 501, "EUR", "CASH", "IDEALPRO", "JPY", true,  (int)(100000*exp),   0.01, (int)(31), Constants.PRICE_TYPE_2,outputdir);				//- +1     30056    82
		createContract(  2, 502, "EUR", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, (int)(35), Constants.PRICE_TYPE_3,outputdir);//			//  +3    7333 95   +5 9334 101
		createContract(  3, 503, "EUR", "CASH", "IDEALPRO", "GBP", true,  (int)(100000*exp), 0.0001, (int)(30), Constants.PRICE_TYPE_4,outputdir);              //- 0   17835.36585	163
		createContract(  4, 504, "EUR", "CASH", "IDEALPRO", "CHF", true,  (int)(100000*exp), 0.0001, (int)(31), Constants.PRICE_TYPE_4,outputdir);              //  1   9147.913	70
		createContract(  5, 505, "EUR", "CASH", "IDEALPRO", "NZD", false, (int)(100000*exp), 0.0001, (int)(30+stop_exp), Constants.PRICE_TYPE_3,outputdir);//	//  0 3448.1874	33   ????
		createContract(  6, 506, "EUR", "CASH", "IDEALPRO", "AUD", false, (int)(100000*exp), 0.0001, (int)(30+stop_exp), Constants.PRICE_TYPE_4,outputdir);//   //  1 7483.53265	100	 2 8382.15565	100  ????
		createContract(  7, 507, "GBP", "CASH", "IDEALPRO", "AUD", false, (int)(80000*exp), 0.0001,  (int)(32+stop_exp), Constants.PRICE_TYPE_4,outputdir);//	//  -3 2190.64318	23  ????
		createContract(  8, 508, "GBP", "CASH", "IDEALPRO", "NZD", false, (int)(80000*exp), 0.0001,  (int)(32+stop_exp), Constants.PRICE_TYPE_3,outputdir);//	//  ????
		createContract(  9, 509, "GBP", "CASH", "IDEALPRO", "USD", true,  (int)(80000*exp), 0.0001,  (int)(34+stop_exp), Constants.PRICE_TYPE_4,outputdir);		//  ????
		createContract( 10, 510, "GBP", "CASH", "IDEALPRO", "CAD", true,  (int)(80000*exp), 0.0001,  (int)(32+stop_exp), Constants.PRICE_TYPE_3,outputdir);//   //  ????
		createContract( 11, 511, "GBP", "CASH", "IDEALPRO", "CHF", true,  (int)(80000*exp), 0.0001,  (int)(32+stop_exp), Constants.PRICE_TYPE_4,outputdir);//   //  0 4515.966809	40 
		createContract( 12, 512, "GBP", "CASH", "IDEALPRO", "JPY", true,  (int)(80000*exp),   0.01,  (int)(34), Constants.PRICE_TYPE_2,outputdir);              //- 0 19480.12133	62
		createContract( 13, 513, "CHF", "CASH", "IDEALPRO", "JPY", false, (int)(120000*exp),   0.01, (int)(36+stop_exp), Constants.PRICE_TYPE_2,outputdir);     //  ????
		createContract( 14, 514, "USD", "CASH", "IDEALPRO", "JPY", false, (int)(110000*exp),   0.01, (int)(27+stop_exp), Constants.PRICE_TYPE_2,outputdir);     //  0 7987.834709	98
		createContract( 15, 515, "USD", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, (int)(30+stop_exp), Constants.PRICE_TYPE_3,outputdir);//   //  -2  10653.62451	-1  132	11492.77121	133	 0  10527.24699	136
		createContract( 16, 516, "USD", "CASH", "IDEALPRO", "CHF", true,  (int)(100000*exp), 0.0001, (int)(28+stop_exp), Constants.PRICE_TYPE_4,outputdir);//   //  -2   9991.828011	136   ????
		createContract( 17, 517, "CAD", "CASH", "IDEALPRO", "JPY", true,  (int)(100000*exp),   0.01, (int)(30), Constants.PRICE_TYPE_2,outputdir);//			//- -1   13099.70321	114	 0   14639.1181	122 //
		createContract( 18, 518, "AUD", "CASH", "IDEALPRO", "USD", true,  (int)(100000*exp), 0.0001, (int)(32), Constants.PRICE_TYPE_4,outputdir);//			//- +2   16021.25	105
	*/
		
		
		prevData = new QuoteData[TOTAL_SYMBOLS];
        closeAccountTradeManager = new TradeManager2(account, m_client, null, 499, logger );

        if (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.SIGNAL_MODE )) 
        {
			m_client.eConnect("127.0.0.1" , port, new Integer(IB_ID));
			m_client.setServerLogLevel(5);
			logger.warning("SF202 started in " + MODE + " mode at port " + port);
			
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				tradeManager[i].loadTradeData(xstream);
				if ( tradeManager[i].trade != null )
					tradeManager[i].trade.positionUpdated = false;
			}
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
		
		last_errorcode = errorCode;

		switch (errorCode) {
		
		case 165:
		//case 1100:	
		//case 2105:
		//case 2106:
		//case 2107:	
					logger.info("E3: " + id + " Error Code:" + errorCode + " " + errorMsg);
					return;
		default:			
					logger.severe("E3: " + id + " Error Code:" + errorCode + " " + errorMsg);
					return;
	}

		//2105/2106
		//if ( errorCode == 2105 )
			//dataBroken = true; 
		/*
		if (( errorCode == 2106 ) && ( dataBroken == true))  // data farm disconnect, request history data
		{
			logger.warning("request history data for missing period");
			String currentTime = IBDataFormatter.format(new Date());
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;
				m_client.reqHistoricalData( id_history[i], contracts[i],  
		    			currentTime, "6000 S", "1 min", "MIDPOINT", 1, 1);	          
			}
			dataBroken = false;
		}

	
		if ( errorCode == 1100 )
			linkLost = true; 
		if (( errorCode == 1102 ) && ( linkLost == true))  // data farm disconnect, request history data
		{
			logger.warning("request history data for missing period");
			String currentTime = IBDataFormatter.format(new Date());
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;
				m_client.reqHistoricalData( id_history[i], contracts[i],  
		    			currentTime, "6000 S", "1 min", "MIDPOINT", 1, 1);	          
			}
			linkLost = false;
		}*/

	
	
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
	    			//currentTime, "6 D", "1 min", "MIDPOINT", 0, 1);	// 300 bars               
			    	currentTime, "10 D", "5 mins", "MIDPOINT", 0, 1);	// 300 bars               
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
		if (( MODE == Constants.REAL_MODE ) && ( historySaved == false ))
			historyData.add(reqId + "," + date + "," + open + "," + high + "," + low + "," + close + "," + 
					volume + "," + count + ","  + WAP + "," + hasGaps );

		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if (reqId == id_history[i])
			{
				//System.out.println("Received "+ contracts[i].m_symbol + "." + contracts[i].m_currency + " " + date + "," + open + "," + high + "," + low + "," + close + "," + 
				//		volume + "," + count + ","  + WAP + "," + hasGaps );

				if ( init_history == false )  // this is to allow history data to be pulled again later
				{	
					if (date.indexOf("finished-") == -1)
					{
						QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
					
						//createOrUpdateQuotes(tradeManager[i][TRADE_1M].qts, Constants.CHART_1_MIN, data );
						createOrUpdateQuotes(tradeManager[i].qts[Constants.CHART_5], Constants.CHART_5_MIN, data );
						createOrUpdateQuotes(tradeManager[i].qts[Constants.CHART_15], Constants.CHART_15_MIN, data );
						createOrUpdateQuotes(tradeManager[i].qts[Constants.CHART_60], Constants.CHART_60_MIN, data );
						createOrUpdateQuotes(tradeManager[i].qts[Constants.CHART_240], Constants.CHART_240_MIN, data );
					}
					else
					{
						if (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.SIGNAL_MODE )) 
						{
							quotes_received[i] = true;
							m_client.reqRealTimeBars(id_realtime[i], contracts[i], 60, "MIDPOINT", true);
							// m_client.reqMktData(199, m_contract, null, false);
							logger.warning(contracts[i].m_symbol + "." + contracts[i].m_currency + " started");
						
							init_history = true;
							for ( int j = 0; j < TOTAL_SYMBOLS; j++)
							{
								if ( quotes_received[j] == false )
									init_history = false;
							}
							
							if ( init_history == true )
							{
								System.out.println("Initial Historical Quotes:");
								for ( int k = 0; k < TOTAL_SYMBOLS; k++)
								{
									QuoteData[] qd = tradeManager[k].getQuoteData(Constants.CHART_5);
									for ( int j = 0; j < qd.length; j++)
										System.out.println(contracts[k].m_symbol + "." + contracts[k].m_currency + " 05IN " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
									qd = tradeManager[k].getQuoteData(Constants.CHART_15);
									for ( int j = 0; j < qd.length; j++)
										System.out.println(contracts[k].m_symbol + "." + contracts[k].m_currency + " 15IN " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
									qd = tradeManager[k].getQuoteData(Constants.CHART_60);
									for ( int j = 0; j < qd.length; j++)
										System.out.println(contracts[k].m_symbol + "." + contracts[k].m_currency + " 60IN " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
								}
							}
						}
					}
					break;
				}
				else
				{
					// this is the hourly HOUR chart update
					if (date.indexOf("finished-") == -1)
					{
						// this is special logic for hourly update
						String min = date.substring(13,15);
						String sec = date.substring(16,18);
						int min5 = new Integer(min);
						/*
						if (sec.equals("00") && min.equals("00"))
						{	
							System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " received: " + date + " Open:" + open + " High:" + high + " Low:" + low + " Close:" + close );
							QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
							
							ReplaceHourlyQuotes(tradeManager[i].qts[Constants.CHART_60], Constants.CHART_60_MIN, data, true );
						}*/
						
						if (sec.equals("00") && ( (min5 % 5) == 0 ))
						{	
							if ( firstQuote[i] == true )
							{
								firstQuote[i] = false;// this is to avoid the first quote as it can be a partial quote
								System.out.println("above first quote discarded");
							}
							else
							{	
								//System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " received: " + date + " Open:" + open + " High:" + high + " Low:" + low + " Close:" + close );
								QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
								replaceQuotes(tradeManager[i].qts[Constants.CHART_5], data );
							}
						}

					}
					else
					{	
						System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " hourly updated " + IBDataFormatter.format(new Date()));
						QuoteData[] baseQuote5 = tradeManager[i].getQuoteData(Constants.CHART_5);
						tradeManager[i].qts[Constants.CHART_15] = rebuildQuote( Constants.CHART_15_MIN, baseQuote5);
						tradeManager[i].qts[Constants.CHART_60] = rebuildQuote( Constants.CHART_60_MIN, baseQuote5);
						tradeManager[i].qts[Constants.CHART_240] = rebuildQuote( Constants.CHART_240_MIN, baseQuote5);

						QuoteData[] qd = tradeManager[i].getQuoteData(Constants.CHART_5);
						for ( int j = 0; j < qd.length; j++)
							System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " 0505 " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close + " " + qd[j].updated );
						qd = tradeManager[i].getQuoteData(Constants.CHART_15);;
						for ( int j = 0; j < qd.length; j++)
							System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " 1515 " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
						qd = tradeManager[i].getQuoteData(Constants.CHART_60);;
						for ( int j = 0; j < qd.length; j++)
							System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " 6060 " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
					}
				}
			}
		}
		
		// after all contract created, call update portfolio

		
		if (( historySaved == false) && (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.SIGNAL_MODE )))
		{
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
				if ( quotes_received[i] == false )
					return;
				
			// only save when everything is in place
			saveHistoryData();
			historySaved = true;
			historyData = null;  // free memory
			
			
			// end account update and get the result
			m_client.reqAccountUpdates(false,Constants.ACCOUNT);  		
			
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				if (( tradeManager[i].trade != null ) && ( tradeManager[i].trade.status.equals(Constants.STATUS_PLACED )) && (tradeManager[i].trade.positionUpdated == false))
				{
					logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position did not updated, trade removed");
					tradeManager[i].trade = null;
				}
			}
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
		// the only time to use this is to close all open orders
		// use any trade manager to cancel order
		if ( Constants.CMD_CLOSE_OUTSTANDING_ORDERS.equals(updatePortfolioCmd))
			closeAccountTradeManager.cancelOrder(orderId);
		
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
			logger.warning("Order filled- orderId:" + orderId + " status:" + status + " filled:" + filled + " remaining:" + remaining + " avgFillPrice:" + avgFillPrice);
			
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				if ( tradeManager[i].trade != null )
					tradeManager[i].checkOrderFilled(orderId, filled);
			}
		}
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{
		lastRealTimeUpdateTime = time;

		String timeStr = IBDataFormatter.format(new Date(time*1000));
		//String hr = timeStr.substring(9,12).trim();
		//String min = timeStr.substring(13,15);
		String sec = timeStr.substring(16,18);

		if (( MODE != Constants.TEST_MODE ) && ( init_history == false ))
			return;
			
		if ( timeout == false )
		{
			Calendar calendar = Calendar.getInstance();
			calendar.setTime( new Date(time*1000) );
			int weekday = calendar.get(Calendar.DAY_OF_WEEK);
			int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);

			if( ( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 12 ))
			{
				logger.warning("Friday 12AM, stop trading new positions");
				timeout = true;
			}
		}

		
		if (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.SIGNAL_MODE )) 
		{
			//saveRealTimeData(reqId + "," + timeStr + "," + open + "," + high + "," + low + "," + close + "," + volume + "," + wap + "," + count);
			//System.out.println(reqId + " Time:" + timeStr + " Open:" + open + " High" + high + " Low" + low + " Close:" + close);
			System.out.print("302.");
			
		}
		
		//int hour = new Integer(hr);
		int second = new Integer(sec);
		//int minute = new Integer(min);
		
		/* NOT SETTING UP STOP LOSS RIGH NOW
		// this will set the maximum loss as -1000
		if (stop_loss == true )
		{
			if (( timeout == false )&& ( second == 0 ) && ( report(false) == true ))
			{
				logger.warning("itoss triggered, halt trading new positions");
				timeout = true;
			}
		}*/

		
		if (( MODE == Constants.TEST_MODE ) && ( !"EUR".equals(market))) 
		{
			if (!( new Integer(reqId).toString().equals(market)))
			  return;
		}
		
		
		
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if (reqId == id_realtime[i])
			{
				if ( MODE == Constants.REAL_MODE ) 
					saveRealTimeData(tradeManager[i].symbol + "," + timeStr + "," + open + "," + high + "," + low + "," + close + "\t" + (Math.abs(open-close)/tradeManager[i].PIP_SIZE));

				
				//System.out.println(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " Time:" + timeStr + " Open:" + open + " High" + high + " Low" + low + " Close:" + close);
				if (( blockSymbols != null ) && ( blockSymbols.indexOf(tradeManager[i].symbol)!= -1))
				{
					System.out.println(tradeManager[i].symbol + " blocked");
					return;
				}

				

				tradeManager[i].currentTime = timeStr;
				
				QuoteData data = new QuoteData(timeStr, time*1000, open, high, low, close, 0, 0, wap, false);
				tradeManager[i].currQuoteData = data;
				
				if ( tradeManager[i].firstRealTime == 0 )
					tradeManager[i].firstRealTime = data.timeInMillSec;

				createOrUpdateQuotes(tradeManager[i].qts[Constants.CHART_5], Constants.CHART_5_MIN, data );
				createOrUpdateQuotes(tradeManager[i].qts[Constants.CHART_15], Constants.CHART_15_MIN, data );
				createOrUpdateQuotes(tradeManager[i].qts[Constants.CHART_60], Constants.CHART_60_MIN, data );
				createOrUpdateQuotes(tradeManager[i].qts[Constants.CHART_240], Constants.CHART_240_MIN, data );

				
				//if ( reqId != 507 )  //500
				  //return;
	
				
				
				//if ("20120423  14:21:00".equals(data.time))
				//{
				//	System.out.println("here");
				//}
				//System.out.println(data.low + " " + data.time);
				
				//logger.info(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " Time:" + timeStr + " Open:" + open + " High" + high + " Low" + low + " Close:" + close);

				// 3 States:  STATUS_DETECTED, STATUS_OPEN(limit order), STATUS_PLACED
				/*if (( MODE == Constants.REAL_MODE ) && ( tradeManager[i].tickStarted == false ))
				{
					m_client.reqMktData(tradeManager[i].tickId, tradeManager[i].m_contract, null, false);  // 1 = bid, 2 = ask, 4 = last, 6 = high, 7 = low, 9 = close,
					tradeManager[i].tickStarted = true;
				}*/

				if (!timeout)
				{
					//tradeManager[i].bigChartState = Constants.STATE_BREAKOUT_UP;
					if (!((tradeManager[i].trade != null) && tradeManager[i].trade.status.equals(Constants.STATUS_PLACED)))
						//tradeManager[i].checkNewHigherHighLowerLow(data,0);   // average 45 on all 
						
						//tradeManager[i].checkLargeTimeFramePullBack(data,0 );
						//tradeManager[i].checkLargeTimeFrameEvent3(data, 0);  // last successful need add
					//tradeManager[i].checkBreakOut4a(data, 0,0);
					//tradeManager[i].checkPullBack2050( data,0,0);// this one works
					 //tradeManager[i].checkPullBack20_org( data);   // default 110k
					//tradeManager[i].checkPullBack20_2( data);   
					//tradeManager[i].checkPullBack20_3( data);   
					
						
						tradeManager[i].checkPullBack20_signal( data);  // DEFAULT_SIGNAL   
					
					
					//tradeManager[i].checkPullBacktoBigMoves( data); /// big profit, but might have bugs
					
					//tradeManager[i].checkPullBack20_with_momentume(data);
				}

				
				if (tradeManager[i].trade != null )  
				{
					/*
					if (!timeout && (tradeManager[i].trade.status.equals(Constants.STATUS_DETECTED)))
						tradeManager[i].trackTradeEntry( data,0 );
					else 
						tradeManager[i].trackTradeTarget(data, 0,0 );
						*/
				}

				if ( MODE == Constants.TEST_MODE )
					tradeManager[i].report2(data.close);
				
				return;
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
		// check entry on tick price
		/*
		if ( field == 4 ) 
		{
			for (  int i = 0; i < TOTAL_SYMBOLS; i++ )
			{
				if (( tickerId == tradeManager[i].tickId ) && ( tradeManager[i].trade == null ))
				{
					System.out.print(".");
					tradeManager[i].trade = tradeManager[i].checkBreakOut( new Double(price) );
				}
			}
		}*/
		//System.out.println("tickerId:" + tickerId + " field:" + field + " price:" + price);
		
		// temporilary disable this
		
		if ((( field == 1 ) ||  (field == 2) || (field == 4)) && ( price > 0 )) // 1 bid, 2 ask  sometime I see price is -1.0
		{
			for (  int i = 0; i < TOTAL_SYMBOLS; i++ )
			{
				if ( tickerId == tradeManager[i].tickId ) 
				{
					if ( tradeManager[i].trade != null )
					{	
						if (!timeout && (tradeManager[i].trade.status.equals(Constants.STATUS_DETECTED)))
						{
							switch ( field )
							{
								case 1: tradeManager[i].lastTick_bid = price;
										tradeManager[i].lastTick = Constants.TICK_BID;
										if (Constants.ACTION_BUY.equals(tradeManager[i].trade.action))
										{
											//tradeManager[i].trackPullBackTradeBuy( null, price);
										}
										break;
								case 2: tradeManager[i].lastTick_ask = price;
										tradeManager[i].lastTick = Constants.TICK_ASK;
										if (Constants.ACTION_SELL.equals(tradeManager[i].trade.action))
										{
											//tradeManager[i].trackPullBackTradeSell( null, price);
										}
										break;
							}
						}
						// we do not use ticker data to trigger close
					}
				}
			}
		}
		lastTickUpdateTime = new Date().getTime();

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
		if ( updatePortfolioCmd == Constants.CMD_UPDATE )
		{
			logger.warning("Portfolio update:" + contract.m_conId + " " + contract.m_symbol + "." + contract.m_currency + " position size: " + position );
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
		    	//contract.m_conId = connectionId++;
		    	//contract.m_symbol = symbol;
		        //contract.m_secType = type;
		        //contract.m_exchange = exchange;
		        //contract.m_currency = currency;
				if ( contract.m_symbol.equals(tradeManager[i].m_contract.m_symbol) && contract.m_currency.equals(tradeManager[i].m_contract.m_currency)) 
				{
					if ( tradeManager[i].trade != null )
					{
						if (( position > 0 ) && tradeManager[i].trade.action.equals(Constants.ACTION_BUY))
						{
							tradeManager[i].trade.positionUpdated = true;
							if (tradeManager[i].trade.status.equals( Constants.STATUS_PLACED))
							{	
								if ( position == tradeManager[i].trade.remainingPositionSize )
								{
									logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position updated " + position );
								}
								else
								{
									logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position sized adjusted to " + Math.abs(position) );
									tradeManager[i].trade.remainingPositionSize = position;
								}
							}
							else if (tradeManager[i].trade.status.equals( Constants.STATUS_OPEN))
							{
								logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position filled " + Math.abs(position) );
								tradeManager[i].trade.remainingPositionSize = position;
								tradeManager[i].trade.status = Constants.STATUS_PLACED;
							}
						}
						else if (( position < 0 ) && tradeManager[i].trade.action.equals(Constants.ACTION_SELL))
						{
							tradeManager[i].trade.positionUpdated = true;
							if (tradeManager[i].trade.status.equals( Constants.STATUS_PLACED))
							{	
								if ( Math.abs(position) == tradeManager[i].trade.remainingPositionSize )
								{
									logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position updated " + Math.abs(position) );
								}
								else
								{
									logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position sized adjusted to " + Math.abs(position) );
									tradeManager[i].trade.remainingPositionSize = Math.abs(position);
								}
							}
							else if (tradeManager[i].trade.status.equals( Constants.STATUS_OPEN))
							{
								logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position filled " + Math.abs(position) );
								tradeManager[i].trade.remainingPositionSize = Math.abs(position);
								tradeManager[i].trade.status = Constants.STATUS_PLACED;
							}
						}
						else if ( position == 0 )
						{
							logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + " " + tradeManager[i].trade.action + " position no longer exist, remove trade");
							tradeManager[i].cancelAllOrders();
							tradeManager[i].trade = null;
						}
						else
						{
							logger.severe(tradeManager[i].symbol + " " + tradeManager[i].CHART + " position update does not match, remove trade"  );
							tradeManager[i].cancelAllOrders();
							tradeManager[i].trade = null;
						}
					}
					else
					{
						logger.warning(tradeManager[i].symbol + " " + tradeManager[i].CHART + "  trade DID NOT FOUND");
					}
				}

			}
		}
		else if ( updatePortfolioCmd == Constants.CMD_CLOSE )
		{
			// close each position
			if ( position != 0 ) 
			{
				logger.warning("To close this position: Contract:" + contract.m_symbol + "." + contract.m_currency + " position size: " + position );
	
				contract.m_exchange = "IDEALPRO";
				closeAccountTradeManager.closePosition( contract, position>0?Constants.ACTION_SELL:Constants.ACTION_BUY, Math.abs(position));
			}
		}
		
	}

	@Override
	public void connectionClosed()
	{
		// TODO Auto-generated method stub
		
	}


	private void createContract(int idHist, int idReal, String symbol, String type, String exchange, String currency, boolean minuteSupport, int tradeSize, double pipsize, int stopSize, int pricetype, 
			String outputDir )
	{
		//int conId =  connectionId++;
		Contract contract = contracts[totalSymbolIndex];
    	contract.m_conId = idHist;//conId;
    	contract.m_symbol = symbol;
        contract.m_secType = type;
        contract.m_exchange = exchange;
        contract.m_currency = currency;
       
        double exchangerate = exchangeRate.get(currency);
        
        id_history[totalSymbolIndex] = idHist;
        id_realtime[totalSymbolIndex] = idReal;
       	tradeManager[totalSymbolIndex] = new SF302TradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex].mainProgram = this;
       	tradeManager[totalSymbolIndex].MODE = this.MODE;
       	tradeManager[totalSymbolIndex].CHART = Constants.CHART_15_MIN;
       	tradeManager[totalSymbolIndex].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex].priceType = pricetype;
       	tradeManager[totalSymbolIndex].POSITION_SIZE = tradeSize;
       	tradeManager[totalSymbolIndex].FIXED_STOP = stopSize; //28;
//     	tradeManager[totalSymbolIndex].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex].currency = currency;
       	tradeManager[totalSymbolIndex].exchangeRate = exchangerate;
       	tradeManager[totalSymbolIndex].LIMIT_ORDER =  false;//( MODE == Constants.REAL_MODE )? true:false;
       	tradeManager[totalSymbolIndex].tickId = idReal + 100;
       	tradeManager[totalSymbolIndex].OUTPUT_DIR = outputDir;
       	
       	//for ( int i = 0; i < tradeManager[totalSymbolIndex].TOTAL_CHARTS; i++)
   		tradeManager[totalSymbolIndex].qts[Constants.CHART_5] = new HashMap<String, QuoteData>(1000);
   		tradeManager[totalSymbolIndex].qts[Constants.CHART_15] = new HashMap<String, QuoteData>(500);
   		tradeManager[totalSymbolIndex].qts[Constants.CHART_60] = new HashMap<String, QuoteData>(200);
   		tradeManager[totalSymbolIndex].qts[Constants.CHART_240] = new HashMap<String, QuoteData>(100);

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
		String time = data.time;
		int hr = new Integer(time.substring(10,12).trim());
		int minute = new Integer(time.substring(13,15));

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
		else if ( CHART == Constants.CHART_240_MIN )
		{
			hr = hr- hr%4;
			minute = 0;
			try
			{
				Date date = IBDataFormatter.parse(time);
				Calendar calendar = Calendar.getInstance();
				calendar.setTime( date );
				int weekday = calendar.get(Calendar.DAY_OF_WEEK);
				int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
				
				if (( weekday ==  Calendar.SUNDAY ) && ( hour_of_day < 20 ))
				{
					// this date will be conslidate to last friday's 16:00 bar
					long datel = date.getTime(); // milliseconds
					datel -= 48*60*60*1000;
					
					date.setTime(datel);
					time = InternalDataFormatter.format(date);
				}
			}
			catch ( Exception e)
			{
				logger.severe("can not parse date during 240 conversion " + data.time + " - " + e.getMessage());
				e.printStackTrace();
			}
		}
		
		String hrStr = new Integer(hr).toString();
		if ( hr < 10 )
			hrStr = "0" + new Integer(hr).toString();
		else
			hrStr = new Integer(hr).toString();

		String minStr = new Integer(minute).toString();
		if ( minute < 10 )
			minStr = "0" + new Integer(minute).toString();
		else
			minStr = new Integer(minute).toString();
		
		String indexStr = time.substring(0,10) + hrStr + ":" + minStr + ":00";
		
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


	public void ReplaceHourlyQuotes( HashMap<String, QuoteData> quotes, int CHART, final QuoteData data, boolean replaceOnly )
	{
		String indexStr = data.time.substring(0,13) + "00";
		
		QuoteData qdata = quotes.get(indexStr);
		
		if (( qdata == null ) || ( replaceOnly == true ))
		{
			qdata = new QuoteData();
			qdata.time = indexStr;
		}

		qdata.open = data.open;
		qdata.high = data.high;
		qdata.low = data.low;
		qdata.close = data.close;
		
		quotes.put( indexStr, qdata);
	}

	
	public void replaceQuotes( HashMap<String, QuoteData> quotes, QuoteData data )
	{
		String indexStr = data.time.substring(0,15);
		data.time = indexStr;
		data.updated = true;
		
		quotes.put( indexStr, data);
	}


	
	
	public HashMap<String, QuoteData> rebuildQuote( int CHART, QuoteData[] baseQuotes )
	{
		HashMap<String, QuoteData> quotes = new HashMap<String, QuoteData>(500);
		
		int len = baseQuotes.length;
		
		for ( int i = 0; i < len; i++)
		{
			String time = baseQuotes[i].time;
			int hr = new Integer(time.substring(10,12).trim());
			int minute = new Integer(time.substring(13,15));

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
			else if ( CHART == Constants.CHART_240_MIN )
			{
				hr = hr- hr%4;
				minute = 0;
				
				try
				{
					Date date = InternalDataFormatter.parse(time);
					Calendar calendar = Calendar.getInstance();
					calendar.setTime( date );
					int weekday = calendar.get(Calendar.DAY_OF_WEEK);
					int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
					
					if (( weekday ==  Calendar.SUNDAY ) && ( hour_of_day < 20 ))
					{
						// this date will be conslidate to last friday's 16:00 bar
						long datel = date.getTime(); // milliseconds
						datel -= 48*60*60*1000;
						
						date.setTime(datel);
						time = InternalDataFormatter.format(date);
					}
				}
				catch ( Exception e)
				{
					logger.severe("can not parse date during 240 conversion " + baseQuotes[i].time);
				}
			}
			
			String hrStr = new Integer(hr).toString();
			if ( hr < 10 )
				hrStr = "0" + new Integer(hr).toString();
			else
				hrStr = new Integer(hr).toString();

			String minStr = new Integer(minute).toString();
			if ( minute < 10 )
				minStr = "0" + new Integer(minute).toString();
			else
				minStr = new Integer(minute).toString();
			
			//String indexStr = baseQuotes[i].time.substring(0,13) + minStr;
			String indexStr = time.substring(0,10) + hrStr + ":" + minStr + ":00";
			
			QuoteData qdata = quotes.get(indexStr);
			if ( qdata == null )
			{
				qdata = new QuoteData();
				qdata.open = baseQuotes[i].open;
				qdata.high = baseQuotes[i].high;
				qdata.low = baseQuotes[i].low;
				qdata.close = baseQuotes[i].close;
				qdata.time = indexStr;
			}
			else
			{
				if (baseQuotes[i].high > qdata.high)
					qdata.high = baseQuotes[i].high;
				if (baseQuotes[i].low < qdata.low )
					qdata.low = baseQuotes[i].low;

				qdata.close = baseQuotes[i].close;

			}
			
			quotes.put( indexStr, qdata);
		}
		
		return quotes;
	}

	
	
	
	
	private void initialize()
	{
		contracts = new Contract[TOTAL_SYMBOLS];
		firstQuote = new boolean[TOTAL_SYMBOLS];
		tradeManager = new SF302TradeManager[TOTAL_SYMBOLS];	
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
	
	
	private boolean report( boolean writeToFile)
	{
    	double totalProfit = 0;
    	double totalUnrealizedProfit = 0;
    	int totalTrade = 0;

    	try
	    {
	    	if ( writeToFile == true )
	    	{	
		    	FileWriter fstream = new FileWriter(reportFileName);
		    	BufferedWriter out = new BufferedWriter(fstream);
	
		    	out.write("Trade Report " + IBDataFormatter.format( new Date()) + "\n\n");
		    	for ( int i = 0; i < TOTAL_SYMBOLS; i++ )
		    	{
	    			if (( tradeManager[i].portofolioReport != null ) && (!tradeManager[i].portofolioReport.equals("")))
	    			{
	    				out.write(tradeManager[i].portofolioReport + "\n");
	    				totalProfit += tradeManager[i].totalProfit;
	    				totalUnrealizedProfit += tradeManager[i].totalUnrealizedProfit;
	    				totalTrade += tradeManager[i].totalTrade;
	    			}
		    	}
	
				out.write("\n\nTOTAL PROFIT: " + totalProfit + " USD          TOTAL TRADES: " + totalTrade + "\n" );
				out.write("\nTOTAL UNREALIZED PROFIT: " + totalUnrealizedProfit + " USD\n" );
	
		    	out.close();
	    	}
	    	else
	    	{
		    	for ( int i = 0; i < TOTAL_SYMBOLS; i++ )
		    	{
	    			if (( tradeManager[i].portofolioReport != null ) && (!tradeManager[i].portofolioReport.equals("")))
	    			{
	    				totalProfit += tradeManager[i].totalProfit;
	    				totalTrade += tradeManager[i].totalTrade;
	    			}
		    	}
	    	}
	    	
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
		Vector<QuoteData> rtDatas = new Vector<QuoteData>();
		
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
				
				if ( time.indexOf("finished-") == -1 )
				{
					//realtimeBar(new Integer(reqId), new Long(time), new Double(open), new Double(high), new Double(low), new Double(close),
					//	new Long(0), new Double(0), new Integer(0));
					QuoteData data = new QuoteData(new Integer(reqId), new Long(time),new Double(open),new Double(high), new Double(low), new Double(close) );
					rtDatas.add( data);

				}
			}
			
			logger.warning(lineNumber + " real time data read");
			
			Object[] qts = rtDatas.toArray();
		    Arrays.sort(qts);
			QuoteData[] quotes = Arrays.copyOf(qts, qts.length, QuoteData[].class);
			
			int length = quotes.length - 1;
			for ( int i = 0; i <= length; i++)
			{
				realtimeBar(quotes[i].req_id, quotes[i].timeInMillSec, quotes[i].open, quotes[i].high, quotes[i].low, quotes[i].close, 
						new Long(0), new Double(0), new Integer(0));
				
			}

			logger.warning(lineNumber + " real time data processed");
		
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

	
	public void checkDataBroken()
	{
		String currentTime = IBDataFormatter.format(new Date());
		//logger.warning("request history data update - currentTime:" + currentTime);

		if ( init_quote_update == false )
		{
			/*
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;
				m_client.reqHistoricalData( id_history[i], contracts[i],  
						currentTime, "20 D", "1 hour", "MIDPOINT", 0, 1);
			}*/
			
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;
				QuoteData[] qts5 = tradeManager[i].getQuoteData(Constants.CHART_5);
				String earliestTime = qts5[0].time + ":00";
				m_client.reqHistoricalData( id_history[i], contracts[i],  
						earliestTime, "10 D", "5 mins", "MIDPOINT", 0, 1); // this gives 20 day history data
			}
			
			
			init_quote_update = true;
		}
		else
		{	
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;
	
				//System.out.println(tradeManager[i][TIME_PERIOD].symbol + " request history data update id:" + id_history[i] + " - currentTime:" + currentTime);
				
				m_client.reqHistoricalData( id_history[i], contracts[i],  
						//currentTime, "7200 S", "1 hour", "MIDPOINT", 0, 1);	// 2 hour
						currentTime, "10800 S", "5 mins", "MIDPOINT", 0, 1);	// 3 hour
						//currentTime, "20 D", "1 hour", "MIDPOINT", 0, 1);
						//currentTime, "10800 S", "1 hour", "MIDPOINT", 0, 1);	// 3 hour
				
				firstQuote[i] = true;   // to avoid first quote
			}
		}
	}

	
	public void sleep ( double min )
	{
		try
		{
			//Thread.sleep(min*60000);
			Thread.sleep((int)(min*6000));
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



	Command loadCommand(String account)
	{
	    String COMMAND_FILE = "./SF302.cmd";

	    try
	    {
	        File f = new File(COMMAND_FILE);
	        FileInputStream file = new FileInputStream(f);
    		ObjectInputStream input = new ObjectInputStream( file );
    		
        	Command cmd = ( Command ) input.readObject();

        	System.out.println("External Command Received:" + cmd.toString());
        	logger.warning("External Command Received:" + cmd.toString());
	    	
	        input.close();

	        // remove the command file;
	        f.delete();
	        
	        return cmd;

	    }
        catch ( FileNotFoundException ex )
	    {
        	//System.out.println("External Command file not found");
        	//e.printStackTrace();  ignore excetion as file does not exist is normal
	    } 
        catch ( Exception e )
        {
        	logger.warning("Load Command error:" + e.getMessage());
        	e.printStackTrace();
        }

	    return null;
	}


	public boolean  isNoReverse(String symbol )
	{
		if (( noReverseSymbols != null ) && (( noReverseSymbols.toLowerCase().indexOf(symbol.toLowerCase())!= -1) || noReverseSymbols.equalsIgnoreCase("all")))
			return true;
		
		return false;
	}

	
	public void saveTradeOpportunities()
	{
	    String TRADE_TRACE_FILE = "302TradeTrack.txt";

		

    	try // open file
	    {
	        Writer out = new OutputStreamWriter(new FileOutputStream(TRADE_TRACE_FILE));
	        out.write(getTradeList());
	        out.close();
	    } 
	    catch ( Exception e )
	    {
	    	logger.warning("Exception occured during saving trade tracks" + e.getMessage());
	    	e.printStackTrace();
	    } 

	}
	
	
	private String getTradeList()
	{
    	StringBuffer TradeOpp = new StringBuffer();
    	
    	// order by trade.detectTime;
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if ( tradeManager[i].trade != null )
				tradeManager[i].trade.sort = false;
		}

		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			Trade earlistTrade = null;
			for ( int j = 0; j < TOTAL_SYMBOLS; j++)
			{
				if (( tradeManager[j].trade != null ) && tradeManager[j].trade.status.equals(Constants.STATUS_DETECTED) && (tradeManager[j].trade.sort == false ))
				{
					if ( earlistTrade == null ) 
						earlistTrade = tradeManager[j].trade;
					else if (earlistTrade.detectTime.compareTo(tradeManager[j].trade.detectTime) > 0 )
						earlistTrade = tradeManager[j].trade;
				}
				
			}

			if ( earlistTrade != null )
			{
				TradeOpp.append(earlistTrade.symbol + " detected " + earlistTrade.detectTime + " " + earlistTrade.action + "\n" );
				//TradeOpp.append(getTradeEvents(earlistTrade));
				//TradeOpp.append("\n");
				earlistTrade.sort = true;
			}

		}
			
		TradeOpp.append("\n");
		/*	
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if (( tradeManager[i].trade != null ) && tradeManager[i].trade.status.equals(Constants.STATUS_PLACED))
			{
				//TradeOpp.append(tradeManager[i].symbol + " detected " + tradeManager[i].trade.detectTime + " " + tradeManager[i].trade.action + " placed " + tradeManager[i].trade.entryTime + "\n");
				TradeOpp.append(getTradeEvents(tradeManager[i].trade));
			}
		}*/
		
		return TradeOpp.toString();
	}
	
	private void readExternalCommand()
	{
		Command cmd = loadCommand(IB_ACCOUNT);
		
		if ( cmd != null )
		{	
			/*
			if ( cmd.getCmd() == CommandAction.save )
			{
				//save = true;
				break;
			}
			else if ( cmd.getCmd() == CommandAction.close )
			{
				break;
			}
			else*/
			if ( cmd.getCmd() == CommandAction.block )
			{
				blockSymbols = cmd.getSymbol();
			}
			else if ( cmd.getCmd() == CommandAction.no_reverse )
			{
				noReverseSymbols = cmd.getSymbol();
			}
			else if ( cmd.getCmd() == CommandAction.remove )
			{
				for ( int i = 0; i < TOTAL_SYMBOLS; i++)
				{
					if ((cmd.getSymbol().indexOf(tradeManager[i].symbol) != -1 ))
					{
						if ( tradeManager[i].trade != null)
						{
							tradeManager[i].cancelAllOrders();
							tradeManager[i].removeTrade();
							//tradeManager[i].trade = null;
						}
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.target )
			{
				for ( int i = 0; i < TOTAL_SYMBOLS; i++)
				{
					if (tradeManager[i].symbol.equalsIgnoreCase(cmd.getSymbol()))
					{
						tradeManager[i].createTradeTargetOrder(cmd.getQuantity(), cmd.getPrice() );
						break;
					}
				}
			}
			else if ( cmd.getCmd() == CommandAction.create )
			{
				for ( int i = 0; i < TOTAL_SYMBOLS; i++)
				{
					if (tradeManager[i].symbol.equalsIgnoreCase(cmd.getSymbol()))
					{
						tradeManager[i].createTrade(cmd.getAction(), cmd.getQuantity(), cmd.getPrice() );
						break;
						
					}
				}
			}
		}
		
		//open.blockSymbols = open.checkBlock();
		if (blockSymbols != null )
			System.out.println("block symbol: " + blockSymbols);

		if (noReverseSymbols != null )
			System.out.println("Do not reverse smybol: " + noReverseSymbols);

	}

	
	
}

/*
 
1.  pull back above previous low/high, would be a reversal
EUR.CHF 5MIN: 1.29695
RST 20110307  04:50 SELL 100000 1.2972
Unrealized Profit: 25
EUR.CHF Total Profit: 25 CHF   26.05 USD

2.  when pull back below the previous low on hourly chart ( 4 bar ), consider a reversal

todo list:
1.  1 time stop, breakeven, 2 time stop, move stop kick in, and 1.4 always on
2.  24 hours, setup algorith for next day

*/
