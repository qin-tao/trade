package tao.trading.strategy;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
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


// this is to add 1m 20 ma 2 bar entry
// prev version SF108


public class SF2USDJPYBO extends SF implements EWrapper
{
	String IB_ACCOUNT = "";//"DU31237";
	private static Logger logger;
	protected EClientSocket m_client = new EClientSocket(this);
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
	protected SimpleDateFormat DateFormatter = new SimpleDateFormat("yyyyMMdd");
	protected SF2USDJPYBOTradeManager[][] tradeManager;
	TradeManager2 closeAccountTradeManager;
	Contract[] contracts;
	int[] id_history;
	boolean init_history = false;
	boolean[] quotes_received; 
	int[] id_realtime;
	boolean[] id_realtime_started;
	boolean[] quoteFirstTime;
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
	String saveCmd = "save.cmd";
	String closeCmd = "close.cmd";
	String outputDir;
	boolean dataBroken,linkLost;
	boolean historySaved;
	String updatePortfolioCmd;
	int last_hour = -1;
	int last_errorcode = 0;
	long systemStartTime, lastRealTimeUpdateTime, lastTickUpdateTime;
	

	QuoteData prevData[];

	
	boolean stop_loss = false;
	
	public static void main(String[] args)
	{
		//Test DU31237 7496 81 EUR 80000 100000 120000 C:/trade/data/101/historyData.csv C:/trade/data/101/realtimeData.csv report.txt
		if ( "Test".equalsIgnoreCase(args[0]))
    	{
    		SF2USDJPYBO open;
    		String account = args[1];
    		int port = new Integer(args[2]);
    		String conn_id = args[3];
    		String market = args[4];
    		float exp = new Float(args[5]);
    		String histFileName = args[6];
    		String realFileName = args[7];
    		String reportFileName = args[8];

    		open = new SF2USDJPYBO(account, port, conn_id, market, Constants.TEST_MODE, exp, null);
    		open.reportFileName = reportFileName;
    		open.readHistoryData( histFileName );
    		open.readRealTimeData( realFileName );
			open.report(true);
    		return;

    	}
		else	// real mode 
    	{	
    		//DU99006 7496 72 EUR 80000 100000 120000 330 C:/trade/data/101/historyData.csv C:/trade/data/101/realtimeData.csv report.txt
    		SF2USDJPYBO open;
    		String account = args[0];
    		int port = new Integer(args[1]);
    		String conn_id = args[2];
    		String market = args[3];
    		float exp = new Float(args[4]);
    		String logdir = args[5];
    		open = new SF2USDJPYBO(account, port, conn_id, market, Constants.REAL_MODE, exp, logdir);
    		open.systemStartTime = new Date().getTime();
    		open.run();
    		
    		boolean save = false;
    		while ( true  )
			{	
				open.sleep(1);
				
				if ( open.checkSave() == true )
				{
					save = true;
					break;
				}
				
				if ( open.checkClose() == true )
					break;

				Date now = new Date();
				long nowl = now.getTime()/1000;
				
				int notReceiveMin = (int)((nowl - open.lastRealTimeUpdateTime )/60);
				if ((notReceiveMin >= 20 ) && (( nowl - open.systemStartTime/1000) > 300))
				{
					logger.warning("Not detect real time data for " + notReceiveMin + " minutes");
					
					//if ( ( notReceiveMin % 20 ) == 0 )
					if (  notReceiveMin == 30 )
					{	
						for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
						{
							logger.warning("request real time bar at 30");
							open.id_realtime[i] += 50;//open.TOTAL_SYMBOLS;
							open.m_client.reqRealTimeBars(open.id_realtime[i], open.contracts[i], 60, "MIDPOINT", true);
						}
					}
				}

				notReceiveMin = (int)((nowl - open.lastTickUpdateTime/1000)/60);
				if ((notReceiveMin >= 20 ) && (( nowl - open.systemStartTime/1000) > 300))
				{
					logger.warning("Not detect tick data for " + notReceiveMin + " minutes");
					
					//if ( ( notReceiveMin % 20 ) == 0 )
					if (  notReceiveMin == 30 )
					{	
						for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
						{
							logger.warning("request real time ticker bar at 30");
							open.tradeManager[i][open.TRADE_15M].tickId += 50;//open.TOTAL_SYMBOLS;
							open.m_client.reqMktData(open.tradeManager[i][open.TRADE_15M].tickId, open.contracts[i], null, false);  // 1 = bid, 2 = ask, 4 = last, 6 = high, 7 = low, 9 = close,
						}
					}
				}

				Calendar calendar = Calendar.getInstance();
				calendar.setTime( now );
				int weekday = calendar.get(Calendar.DAY_OF_WEEK);
				int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
				//int minute=calendar.get(Calendar.MINUTE);
				
				if (( open.last_hour != -1 ) && ( hour_of_day != 0 ) && ( hour_of_day != open.last_hour ))   // 0 is 12am
				{
					String currentPositions = "";
					for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
						if ( open.tradeManager[i][open.TRADE_15M].trade != null )
							currentPositions += open.tradeManager[i][open.TRADE_15M].symbol +":"+ open.tradeManager[i][open.TRADE_15M].trade.status + " " + open.tradeManager[i][open.TRADE_15M].trade.action + " " + open.tradeManager[i][open.TRADE_15M].trade.POSITION_SIZE +" ";
		        	if ( currentPositions.length() > 5 )
		        		logger.warning(currentPositions);

					if (open.last_errorcode != 0 ) 
					{
						open.checkDataBroken();
						open.last_errorcode = 0;
					}
				}
				open.last_hour = hour_of_day;
					
				if ( weekday == Calendar.FRIDAY ) 
				{
					/*
					if ( hour_of_day >= 10 )
					{
			        	logger.warning("Friday 10AM, stop trading new positions");
						open.timeout = true;
					}*/
					
					if ( hour_of_day >= 16 )
						break;
				}
			}
			
			
			open.timeout = true;
			logger.warning(args[1] + " trading time is up");
		
			if (save == true )
			{
				logger.warning(args[1] + " save trading status....");
				for ( int i = 0; i < open.TOTAL_SYMBOLS; i++)
				{
					open.tradeManager[i][open.TRADE_15M].saveTradeData();
				}
			}
			else
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

	
	public boolean closeAllPositionsByLimit() {
		
		logger.info("Close all positions by limit orders");
		boolean limitOrderPlaced = false;
		// to set all target as current price
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if ( tradeManager[i][TIME_PERIOD].trade != null )
			{
				logger.warning(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " trade exists");
				if (( tradeManager[i][TIME_PERIOD].trade.status == Constants.STATUS_OPEN ) || ( tradeManager[i][TIME_PERIOD].trade.status == Constants.STATUS_STOPPEDOUT )) 
				{
					logger.warning(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " trade status open");
					tradeManager[i][TIME_PERIOD].cancelLimits();
					tradeManager[i][TIME_PERIOD].cancelStop();
					tradeManager[i][TIME_PERIOD].trade = null;
				}
				else if ( tradeManager[i][TIME_PERIOD].trade.status == Constants.STATUS_PLACED )
				{
					logger.warning(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " trade status placed");
					tradeManager[i][TIME_PERIOD].cancelTargets();
					tradeManager[i][TIME_PERIOD].cancelStop();
				
					//Object[] quotes = tradeManager[i][TIME_PERIOD].getQuoteData();
					//int lastbar = quotes.length -1;
					
					tradeManager[i][TIME_PERIOD].trade.status = Constants.STATUS_EXITING;
					
					String tradeAction = null;
					if (Constants.ACTION_BUY.equals(tradeManager[i][TIME_PERIOD].trade.action ))
					{
						if ( tradeManager[i][TIME_PERIOD].lastTick_ask != 0 )
						{	
							logger.warning(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " last tick ask is " + tradeManager[i][TIME_PERIOD].lastTick_ask);
							tradeManager[i][TIME_PERIOD].trade.targetPrice1 = tradeManager[i][TIME_PERIOD].adjustPrice(tradeManager[i][TIME_PERIOD].lastTick_ask, Constants.ADJUST_TYPE_UP);
						}
						else
						{
							QuoteData[] quotes = tradeManager[i][TIME_PERIOD].getQuoteData();
							int lastbar = quotes.length -1;
							logger.warning(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " last close is " + quotes[lastbar].close);
							tradeManager[i][TIME_PERIOD].trade.targetPrice1 = tradeManager[i][TIME_PERIOD].adjustPrice(tradeManager[i][TIME_PERIOD].lastTick_ask, Constants.ADJUST_TYPE_UP);
						}
						tradeAction = Constants.ACTION_SELL;
					}
					else if (Constants.ACTION_SELL.equals(tradeManager[i][TIME_PERIOD].trade.action ))
					{
						if ( tradeManager[i][TIME_PERIOD].lastTick_bid != 0 )
						{
							logger.warning(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " last tick bid is " + tradeManager[i][TIME_PERIOD].lastTick_bid);
							tradeManager[i][TIME_PERIOD].trade.targetPrice1 = tradeManager[i][TIME_PERIOD].adjustPrice(tradeManager[i][TIME_PERIOD].lastTick_bid, Constants.ADJUST_TYPE_DOWN);
						}
						else
						{
							QuoteData[] quotes = tradeManager[i][TIME_PERIOD].getQuoteData();
							int lastbar = quotes.length -1;
							logger.warning(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " last close is " + quotes[lastbar].close);
							tradeManager[i][TIME_PERIOD].trade.targetPrice1 = tradeManager[i][TIME_PERIOD].adjustPrice(tradeManager[i][TIME_PERIOD].lastTick_bid, Constants.ADJUST_TYPE_DOWN);
						}
						tradeAction = Constants.ACTION_BUY;
					}

					tradeManager[i][TIME_PERIOD].trade.targetPos1 = tradeManager[i][TIME_PERIOD].trade.remainingPositionSize;
					tradeManager[i][TIME_PERIOD].trade.targetId1 = tradeManager[i][TIME_PERIOD].placeLmtOrder(tradeAction, tradeManager[i][TIME_PERIOD].trade.targetPrice1, tradeManager[i][TIME_PERIOD].trade.targetPos1, null);//new Long(oca).toString());
					logger.warning(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " orderId:" + tradeManager[i][TIME_PERIOD].trade.targetId1 + " place limit target " + tradeAction + " order of " + tradeManager[i][TIME_PERIOD].trade.targetPos1 + " @ " + tradeManager[i][TIME_PERIOD].trade.targetPrice1);
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

	
	public SF2USDJPYBO( String account, int port, String ib_id, String trade_market, int mode, float exp, String outputdir)
	{
		//double stop_exp = new Double(trade_market);
		double stop_exp = 1;
		
		String today = DateFormatter.format(new Date());

		String summaryLogFile = "sf202_summary_"+  today + ".log";
		String detailLogFile = "sf202_detail_"+ today + ".log";

		if ( outputdir != null )
		{
    		this.outputDir = outputdir;

    		summaryLogFile = outputDir + summaryLogFile;
			detailLogFile = outputDir + detailLogFile;
    		saveRealTimeFile = outputDir + saveRealTimeFile;
    		saveHistoryFile = outputDir + saveHistoryFile;
    		reportFileName = outputDir + reportFileName;
		}

		
		logger = Logger.getLogger("SF202_" + account);

		
		try
		{
			boolean append = true;
			logger = Logger.getLogger("SF202_" + account);

			FileHandler handler = new FileHandler(detailLogFile, append);
			handler.setLevel(Level.FINER);
			handler.setFormatter(new SimpleFormatter());
			logger.addHandler(handler);

			// this logger creates trade summary
			
			if ( MODE == Constants.REAL_MODE )
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
	   	
		id_history = new int[20];//{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
		id_realtime = new int[20];//]{500,501,502,503,504,505,506,507,508,509,510,511,512,513,514,515,516,517,518,519,520,521,522,523};

		
		TOTAL_SYMBOLS = 6;//11;
		initialize();
		createContract(  0, 500, "EUR", "CASH", "IDEALPRO", "USD", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_4,outputdir);
		createContract(  1, 501, "EUR", "CASH", "IDEALPRO", "JPY", true,  (int)(100000*exp),   0.01, (int)(30*stop_exp), Constants.PRICE_TYPE_2,outputdir);
		//createContract(  2, 502, "EUR", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_3,"E");//
		createContract(  3, 503, "EUR", "CASH", "IDEALPRO", "GBP", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_4,outputdir);
		createContract(  4, 504, "EUR", "CASH", "IDEALPRO", "CHF", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_4,outputdir);
		//createContract(  5, 505, "EUR", "CASH", "IDEALPRO", "NZD", false, (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_3,"E");//
		//createContract(  6, 506, "EUR", "CASH", "IDEALPRO", "AUD", false, (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_4,"E");//
		//createContract(  7, 507, "GBP", "CASH", "IDEALPRO", "AUD", false, (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_4,"E");//
		//createContract(  8, 508, "GBP", "CASH", "IDEALPRO", "NZD", false, (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_3,"E");//
		//createContract(  9, 509, "GBP", "CASH", "IDEALPRO", "USD", true,  (int)(80000*exp), 0.0001, (int)(34*stop_exp), Constants.PRICE_TYPE_4,"EU");
		//createContract( 10, 510, "GBP", "CASH", "IDEALPRO", "CAD", true,  (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_3,"E");//
		//createContract( 11, 511, "GBP", "CASH", "IDEALPRO", "CHF", true,  (int)(80000*exp), 0.0001, 32, Constants.PRICE_TYPE_4,"E");//
		createContract( 12, 512, "GBP", "CASH", "IDEALPRO", "JPY", true,  (int)(80000*exp),   0.01, (int)(34*stop_exp), Constants.PRICE_TYPE_2,outputdir);
		//createContract( 13, 513, "CHF", "CASH", "IDEALPRO", "JPY", false, (int)(120000*exp),   0.01, 36, Constants.PRICE_TYPE_2,"E");
		createContract( 14, 514, "USD", "CASH", "IDEALPRO", "JPY", false, (int)(110000*exp),   0.01, (int)(27*stop_exp), Constants.PRICE_TYPE_2,outputdir);
		//createContract( 15, 515, "USD", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_3,"E");//
		//createContract( 16, 516, "USD", "CASH", "IDEALPRO", "CHF", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_4,"E");//
		//createContract( 17, 517, "CAD", "CASH", "IDEALPRO", "JPY", true,  (int)(100000*exp),   0.01, 30, Constants.PRICE_TYPE_2,"E");//
		//createContract( 18, 518, "AUD", "CASH", "IDEALPRO", "USD", true,  (int)(100000*exp), 0.0001, 30, Constants.PRICE_TYPE_4,"E");//

		
/*		TOTAL_SYMBOLS = 19;//11;
		initialize();
		createContract(  0, 500, "EUR", "CASH", "IDEALPRO", "USD", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_4,outputdir);
		createContract(  1, 501, "EUR", "CASH", "IDEALPRO", "JPY", true,  (int)(100000*exp),   0.01, (int)(30*stop_exp), Constants.PRICE_TYPE_2,outputdir);
		createContract(  2, 502, "EUR", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_3,outputdir);//
		createContract(  3, 503, "EUR", "CASH", "IDEALPRO", "GBP", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_4,outputdir);
		createContract(  4, 504, "EUR", "CASH", "IDEALPRO", "CHF", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_4,outputdir);
		createContract(  5, 505, "EUR", "CASH", "IDEALPRO", "NZD", false, (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_3,outputdir);//
		createContract(  6, 506, "EUR", "CASH", "IDEALPRO", "AUD", false, (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_4,outputdir);//
		createContract(  7, 507, "GBP", "CASH", "IDEALPRO", "AUD", false, (int)(80000*exp), 0.0001,  (int)(32*stop_exp), Constants.PRICE_TYPE_4,outputdir);//
		createContract(  8, 508, "GBP", "CASH", "IDEALPRO", "NZD", false, (int)(80000*exp), 0.0001,  (int)(32*stop_exp), Constants.PRICE_TYPE_3,outputdir);//
		createContract(  9, 509, "GBP", "CASH", "IDEALPRO", "USD", true,  (int)(80000*exp), 0.0001,  (int)(34*stop_exp), Constants.PRICE_TYPE_4,outputdir);
		createContract( 10, 510, "GBP", "CASH", "IDEALPRO", "CAD", true,  (int)(80000*exp), 0.0001,  (int)(32*stop_exp), Constants.PRICE_TYPE_3,outputdir);//
		createContract( 11, 511, "GBP", "CASH", "IDEALPRO", "CHF", true,  (int)(80000*exp), 0.0001,  (int)(32*stop_exp), Constants.PRICE_TYPE_4,outputdir);//
		createContract( 12, 512, "GBP", "CASH", "IDEALPRO", "JPY", true,  (int)(80000*exp),   0.01,  (int)(34*stop_exp), Constants.PRICE_TYPE_2,outputdir);
		createContract( 13, 513, "CHF", "CASH", "IDEALPRO", "JPY", false, (int)(120000*exp),   0.01, (int)(36*stop_exp), Constants.PRICE_TYPE_2,outputdir);
		createContract( 14, 514, "USD", "CASH", "IDEALPRO", "JPY", false, (int)(110000*exp),   0.01, (int)(27*stop_exp), Constants.PRICE_TYPE_2,outputdir);
		createContract( 15, 515, "USD", "CASH", "IDEALPRO", "CAD", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_3,outputdir);//
		createContract( 16, 516, "USD", "CASH", "IDEALPRO", "CHF", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_4,outputdir);//
		createContract( 17, 517, "CAD", "CASH", "IDEALPRO", "JPY", true,  (int)(100000*exp),   0.01, (int)(30*stop_exp), Constants.PRICE_TYPE_2,outputdir);//
		createContract( 18, 518, "AUD", "CASH", "IDEALPRO", "USD", true,  (int)(100000*exp), 0.0001, (int)(30*stop_exp), Constants.PRICE_TYPE_4,outputdir);//
	*/	
		
		prevData = new QuoteData[TOTAL_SYMBOLS];
        closeAccountTradeManager = new TradeManager2(account, m_client, null, 499, logger );

        if (( MODE == Constants.REAL_MODE ) || ( MODE == Constants.RECORD_MODE )) 
        {
			m_client.eConnect("127.0.0.1" , port, new Integer(IB_ID));
			m_client.setServerLogLevel(5);
			logger.warning("SF202 started in " + MODE + " mode at port " + port);
			
			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				tradeManager[i][TRADE_15M].loadTradeData();
				if ( tradeManager[i][TRADE_15M].trade != null )
					tradeManager[i][TRADE_15M].trade.positionUpdated = false;
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
	    			currentTime, "6 D", "1 min", "MIDPOINT", 0, 1);	// 300 bars               
			    //	currentTime, "10 D", "5 mins", "MIDPOINT", 0, 1);	// 300 bars               
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
				if ( init_history == false )  // this is to allow history data to be pulled again later
				{	
					if (date.indexOf("finished-") == -1)
					{
						QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
					
						//createOrUpdateQuotes(tradeManager[i][TRADE_1M].qts, Constants.CHART_1_MIN, data );
						createOrUpdateQuotes(tradeManager[i][TRADE_5M].qts, Constants.CHART_5_MIN, data );
						createOrUpdateQuotes(tradeManager[i][TRADE_15M].qts, Constants.CHART_15_MIN, data );
						createOrUpdateQuotes(tradeManager[i][TRADE_60M].qts, Constants.CHART_60_MIN, data );
					}
					else
					{
						if ( MODE == Constants.REAL_MODE )   
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
								for ( int k = 0; k < TOTAL_SYMBOLS; k++)
								{
									QuoteData[] qd = tradeManager[k][TRADE_15M].smallTimeFrameTraderManager.getQuoteData();
									for ( int j = 0; j < qd.length; j++)
										System.out.println(contracts[k].m_symbol + "." + contracts[k].m_currency + " 05IN " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
									qd = tradeManager[k][TRADE_15M].getQuoteData();
									for ( int j = 0; j < qd.length; j++)
										System.out.println(contracts[k].m_symbol + "." + contracts[k].m_currency + " 15IN " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
									qd = tradeManager[k][TRADE_15M].largerTimeFrameTraderManager.getQuoteData();
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
						String sec = date.substring(16,18);
						if (sec.equals("00"))
						{	
							System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " received: " + date + " Open:" + open + " High:" + high + " Low:" + low + " Close:" + close );
							QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
							
							//ReplaceHourlyQuotes(tradeManager[i][TRADE_60M].qts, Constants.CHART_60_MIN, data, true );
							
							replaceQuotes( tradeManager[i][TRADE_5M].qts, Constants.CHART_5_MIN, data );
							createOrUpdateQuotes( tradeManager[i][TRADE_15M].qts, Constants.CHART_15_MIN, data );
							createOrUpdateQuotes( tradeManager[i][TRADE_60M].qts, Constants.CHART_60_MIN, data );
						}

					}
					else
					{	
						logger.info(contracts[i].m_symbol + "." + contracts[i].m_currency + " hourly updated " + IBDataFormatter.format(new Date()));
						QuoteData[] qd = tradeManager[i][TRADE_15M].smallTimeFrameTraderManager.getQuoteData();
						for ( int j = 0; j < qd.length; j++)
							System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " 5555 " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
						qd = tradeManager[i][TRADE_15M].getQuoteData();
						for ( int j = 0; j < qd.length; j++)
							System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " 1515 " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
						qd = tradeManager[i][TRADE_15M].largerTimeFrameTraderManager.getQuoteData();
						for ( int j = 0; j < qd.length; j++)
							System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " 6060 " + qd[j].time + " Open:" + qd[j].open + " High:" + qd[j].high + " Low:" + qd[j].low + " Close:" + qd[j].close );
					}
				}
			}
		}
		
		// after all contract created, call update portfolio

		
		if (( historySaved == false) && ( MODE == Constants.REAL_MODE ))
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
				if (( tradeManager[i][TRADE_15M].trade != null ) && (tradeManager[i][TRADE_15M].trade.positionUpdated == false))
				{
					logger.warning(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + " " + tradeManager[i][TRADE_15M].trade.action + " position did not updated, trade removed");
					tradeManager[i][TRADE_15M].trade = null;
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
				if ( tradeManager[i][TIME_PERIOD].trade != null )
					tradeManager[i][TIME_PERIOD].checkOrderFilled(orderId, filled);
			}
		}
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{
		lastRealTimeUpdateTime = time;

		String timeStr = IBDataFormatter.format(new Date(time*1000));
		String hr = timeStr.substring(9,12).trim();
		String min = timeStr.substring(13,15);
		String sec = timeStr.substring(16,18);

			
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

		
		if (( MODE == Constants.REAL_MODE ) && (!timeout))
		{
			saveRealTimeData(reqId + "," + time + "," + open + "," + high + "," + low + "," + close + "," + volume + "," + wap + "," + count);
			logger.info(reqId + " Time:" + timeStr + " Open:" + open + " High" + high + " Low" + low + " Close:" + close);
		}
		
		int hour = new Integer(hr);
		int second = new Integer(sec);
		int minute = new Integer(min);
		
		// this will set the maximum loss as -1000
		if (stop_loss == true )
		{
			if (( timeout == false )&& ( second == 0 ) && ( report(false) == true ))
			{
				logger.warning("itoss triggered, halt trading new positions");
				timeout = true;
			}
		}

		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if (reqId == id_realtime[i])
			{
				//System.out.println(tradeManager[i][TIME_PERIOD].symbol + "." + tradeManager[i][TIME_PERIOD].CHART + " Time:" + timeStr + " Open:" + open + " High" + high + " Low" + low + " Close:" + close);


				//tradeManager[i][TRADE_1M].currentTime = timeStr;
				//tradeManager[i][TRADE_3M].currentTime = timeStr;
				tradeManager[i][TRADE_5M].currentTime = timeStr;
				//tradeManager[i][TRADE_10M].currentTime = timeStr;
				tradeManager[i][TRADE_15M].currentTime = timeStr;
				//tradeManager[i][TRADE_30M].currentTime = timeStr;
				tradeManager[i][TRADE_60M].currentTime = timeStr;
				
				QuoteData data = new QuoteData(timeStr, time*1000, open, high, low, close, 0, 0, wap, false);
				tradeManager[i][TRADE_15M].currQuoteData = data;
				
				if ( tradeManager[i][TRADE_15M].firstRealTime == 0 )
					tradeManager[i][TRADE_15M].firstRealTime = data.timeInMillSec;

				//createOrUpdateQuotes(tradeManager[i][TRADE_1M].qts, Constants.CHART_1_MIN, data );
				//createOrUpdateQuotes(tradeManager[i][TRADE_3M].qts, Constants.CHART_3_MIN, data );
				createOrUpdateQuotes(tradeManager[i][TRADE_5M].qts, Constants.CHART_5_MIN, data );
				//createOrUpdateQuotes(tradeManager[i][TRADE_10M].qts, Constants.CHART_10_MIN, data );
				createOrUpdateQuotes(tradeManager[i][TRADE_15M].qts, Constants.CHART_15_MIN, data );
				//createOrUpdateQuotes(tradeManager[i][TRADE_30M].qts, Constants.CHART_30_MIN, data );
				createOrUpdateQuotes(tradeManager[i][TRADE_60M].qts, Constants.CHART_60_MIN, data );

				
				//if ( reqId != 512 )
				  //return;
	
/*				if (( MODE == Constants.REAL_MODE ) && ( tradeManager[i][TRADE_15M].tickStarted == false ))
				{
					m_client.reqMktData(tradeManager[i][TRADE_15M].tickId, tradeManager[i][TRADE_15M].m_contract, null, false);  // 1 = bid, 2 = ask, 4 = last, 6 = high, 7 = low, 9 = close,
					tradeManager[i][TRADE_15M].tickStarted = true;
				}

				if (!timeout)
				{
					if (!((tradeManager[i][TRADE_15M].trade != null) && tradeManager[i][TRADE_15M].trade.status.equals(Constants.STATUS_PLACED)))
						tradeManager[i][TRADE_15M].checkBreakOut4a(data, 0, 0 );
				}

				if (tradeManager[i][TRADE_15M].trade != null )  
				{
					if (!timeout && (tradeManager[i][TRADE_15M].trade.status.equals(Constants.STATUS_DETECTED)))
						tradeManager[i][TRADE_15M].trackTradeEntry( data,0 );
					else 
						tradeManager[i][TRADE_15M].trackTradeTarget(data, 0,0 );
				}

				if ( MODE != Constants.REAL_MODE )
					tradeManager[i][TRADE_15M].report2(data.close);
*/
				
				if ((tradeManager[i][TRADE_15M].trade == null) && (tradeManager[i][TRADE_15M].inTradingTime(hour, minute)))  
				{
					tradeManager[i][TRADE_15M].trade = tradeManager[i][TRADE_15M].checkUSDJPYBreakOut( data, 0, 0 );
				}

				if ( tradeManager[i][TRADE_15M].trade != null )  
				{
					if (tradeManager[i][TRADE_15M].inTradingTime(hour, minute) && (tradeManager[i][TRADE_15M].trade.status.equals(Constants.STATUS_OPEN)))
						tradeManager[i][TRADE_15M].trackTradeEntry( data,tradeManager[i][TRADE_1M].getQuoteData(), tradeManager[i][TRADE_30M].getQuoteData());
					else if (tradeManager[i][TRADE_15M].trade.status.equals(Constants.STATUS_PLACED))
						tradeManager[i][TRADE_15M].trackTradeTarget(data, tradeManager[i][TRADE_1M].getQuoteData(), tradeManager[i][TRADE_30M].getQuoteData());

					tradeManager[i][TRADE_15M].report2(data.close);

				}  

				
				
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
				if (( tickerId == tradeManager[i][TRADE_15M].tickId ) && ( tradeManager[i][TRADE_15M].trade == null ))
				{
					System.out.print(".");
					tradeManager[i][TRADE_15M].trade = tradeManager[i][TRADE_15M].checkBreakOut( new Double(price) );
				}
			}
		}*/
		
		
		// temporilary disable this
		
		if ((( field == 1 ) ||  (field == 2) || (field == 4)) && ( price > 0 )) // 1 bid, 2 ask  sometime I see price is -1.0
		{
			for (  int i = 0; i < TOTAL_SYMBOLS; i++ )
			{
				if ( tickerId == tradeManager[i][TRADE_15M].tickId ) 
				{
					if ( tradeManager[i][TRADE_15M].trade != null )
					{	
						if (!timeout && (tradeManager[i][TRADE_15M].trade.status.equals(Constants.STATUS_DETECTED)))
						{
							switch ( field )
							{
								case 1: tradeManager[i][TRADE_15M].lastTick_bid = price;
										tradeManager[i][TRADE_15M].lastTick = Constants.TICK_BID;
										//System.out.println(tradeManager[i][TRADE_15M].symbol + " bid " + price );
										//if ( tradeManager[i][TRADE_15M].trade == null )
										//	tradeManager[i][TRADE_15M].trade = tradeManager[i][TRADE_15M].checkBreakOut( null, price, 0 );
										if ((tradeManager[i][TRADE_15M].trade != null) && (tradeManager[i][TRADE_15M].trade.status.equals(Constants.STATUS_OPEN)))
											tradeManager[i][TRADE_15M].trackTradeEntry( null, price );
										break;
								case 2: tradeManager[i][TRADE_15M].lastTick_ask = price;
										tradeManager[i][TRADE_15M].lastTick = Constants.TICK_ASK;
										//System.out.println(tradeManager[i][TRADE_15M].symbol + " ask " + price );
										//if ( tradeManager[i][TRADE_15M].trade == null )
											//tradeManager[i][TRADE_15M].trade = tradeManager[i][TRADE_15M].checkBreakOut( null, 0, price );
										if ((tradeManager[i][TRADE_15M].trade != null) && (tradeManager[i][TRADE_15M].trade.status.equals(Constants.STATUS_OPEN)))
											tradeManager[i][TRADE_15M].trackTradeEntry( null, price );
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
				if ( contract.m_symbol.equals(tradeManager[i][TRADE_15M].m_contract.m_symbol) && contract.m_currency.equals(tradeManager[i][TRADE_15M].m_contract.m_currency)) 
				{
					if ( tradeManager[i][TRADE_15M].trade != null )
					{
						if (( position > 0 ) && tradeManager[i][TRADE_15M].trade.action.equals(Constants.ACTION_BUY))
						{
							tradeManager[i][TRADE_15M].trade.positionUpdated = true;
							if (tradeManager[i][TRADE_15M].trade.status.equals( Constants.STATUS_PLACED))
							{	
								if ( position == tradeManager[i][TRADE_15M].trade.remainingPositionSize )
								{
									logger.warning(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + " " + tradeManager[i][TRADE_15M].trade.action + " position updated " + position );
								}
								else
								{
									logger.warning(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + " " + tradeManager[i][TRADE_15M].trade.action + " position sized adjusted to " + Math.abs(position) );
									tradeManager[i][TRADE_15M].trade.remainingPositionSize = position;
								}
							}
							else if (tradeManager[i][TRADE_15M].trade.status.equals( Constants.STATUS_OPEN))
							{
								logger.warning(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + " " + tradeManager[i][TRADE_15M].trade.action + " position filled " + Math.abs(position) );
								tradeManager[i][TRADE_15M].trade.remainingPositionSize = position;
								tradeManager[i][TRADE_15M].trade.status = Constants.STATUS_PLACED;
							}
						}
						else if (( position < 0 ) && tradeManager[i][TRADE_15M].trade.action.equals(Constants.ACTION_SELL))
						{
							tradeManager[i][TRADE_15M].trade.positionUpdated = true;
							if (tradeManager[i][TRADE_15M].trade.status.equals( Constants.STATUS_PLACED))
							{	
								if ( position == tradeManager[i][TRADE_15M].trade.remainingPositionSize )
								{
									logger.warning(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + " " + tradeManager[i][TRADE_15M].trade.action + " position updated " + position );
								}
								else
								{
									logger.warning(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + " " + tradeManager[i][TRADE_15M].trade.action + " position sized adjusted to " + Math.abs(position) );
									tradeManager[i][TRADE_15M].trade.remainingPositionSize = position;
								}
							}
							else if (tradeManager[i][TRADE_15M].trade.status.equals( Constants.STATUS_OPEN))
							{
								logger.warning(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + " " + tradeManager[i][TRADE_15M].trade.action + " position filled " + Math.abs(position) );
								tradeManager[i][TRADE_15M].trade.remainingPositionSize = position;
								tradeManager[i][TRADE_15M].trade.status = Constants.STATUS_PLACED;
							}
						}
						else if ( position == 0 )
						{
							logger.warning(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + " " + tradeManager[i][TRADE_15M].trade.action + " position no longer exist, remove trade");
							tradeManager[i][TRADE_15M].cancelAllOrders();
							tradeManager[i][TRADE_15M].trade = null;
						}
						else
						{
							logger.severe(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + " position update does not match, remove trade"  );
							tradeManager[i][TRADE_15M].cancelAllOrders();
							tradeManager[i][TRADE_15M].trade = null;
						}
					}
					else
					{
						logger.warning(tradeManager[i][TRADE_15M].symbol + " " + tradeManager[i][TRADE_15M].CHART + "  trade DID NOT FOUND");
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
/*        
     	tradeManager[totalSymbolIndex][0] = new SF2USDJPYBOTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
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
                        	
       	tradeManager[totalSymbolIndex][1] = new SF2USDJPYBOTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
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
    */    	
       	tradeManager[totalSymbolIndex][2] = new SF2USDJPYBOTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][2].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][2].CHART = Constants.CHART_5_MIN;
       	tradeManager[totalSymbolIndex][2].TARGET_PROFIT_SIZE = 50;
       	tradeManager[totalSymbolIndex][2].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][2].priceType = pricetype;
       	tradeManager[totalSymbolIndex][2].POSITION_SIZE = tradeSize;//200000;//tradeSize;//50000;//200000;
       	tradeManager[totalSymbolIndex][2].FIXED_STOP = stopSize;
  //   	tradeManager[totalSymbolIndex][2].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][2].currency = currency;
       	tradeManager[totalSymbolIndex][2].exchangeRate = exchangerate;
 /*       	
       	tradeManager[totalSymbolIndex][3] = new SF2USDJPYBOTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
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
*/        
       	tradeManager[totalSymbolIndex][4] = new SF2USDJPYBOTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][4].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][4].CONN_ID = idHist;//conId;
       	tradeManager[totalSymbolIndex][4].CHART = Constants.CHART_15_MIN;
       	tradeManager[totalSymbolIndex][4].TARGET_PROFIT_SIZE = 40;
       	tradeManager[totalSymbolIndex][4].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][4].priceType = pricetype;
       	tradeManager[totalSymbolIndex][4].POSITION_SIZE = tradeSize;
       	tradeManager[totalSymbolIndex][4].FIXED_STOP = stopSize; //28;
//     	tradeManager[totalSymbolIndex][4].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][4].currency = currency;
       	tradeManager[totalSymbolIndex][4].exchangeRate = exchangerate;
       	tradeManager[totalSymbolIndex][4].LIMIT_ORDER =  false;//( MODE == Constants.REAL_MODE )? true:false;
       	tradeManager[totalSymbolIndex][4].tickId = idReal + 100;
       	tradeManager[totalSymbolIndex][4].OUTPUT_DIR = outputDir;
/*
       	tradeManager[totalSymbolIndex][5] = new SF2USDJPYBOTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][5].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][5].CHART = Constants.CHART_30_MIN;
       	tradeManager[totalSymbolIndex][5].TARGET_PROFIT_SIZE = 35;
       	tradeManager[totalSymbolIndex][5].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][5].priceType = pricetype;
       	tradeManager[totalSymbolIndex][5].POSITION_SIZE = 50000;
       	tradeManager[totalSymbolIndex][5].FIXED_STOP = 21;
       	tradeManager[totalSymbolIndex][5].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][5].currency = currency;
       	tradeManager[totalSymbolIndex][5].exchangeRate = exchangerate;
*/
       	tradeManager[totalSymbolIndex][6] = new SF2USDJPYBOTradeManager(IB_ACCOUNT, m_client,contract, totalSymbolIndex, logger );
       	tradeManager[totalSymbolIndex][6].MODE = this.MODE;
       	tradeManager[totalSymbolIndex][6].CHART = Constants.CHART_60_MIN;
       	tradeManager[totalSymbolIndex][6].TARGET_PROFIT_SIZE = 35;
       	tradeManager[totalSymbolIndex][6].PIP_SIZE = pipsize;
       	tradeManager[totalSymbolIndex][6].priceType = pricetype;
       	tradeManager[totalSymbolIndex][6].POSITION_SIZE = 50000;
       	tradeManager[totalSymbolIndex][6].FIXED_STOP = 21;
  //   	tradeManager[totalSymbolIndex][6].TIME_ZONE = timeZone;
       	tradeManager[totalSymbolIndex][6].currency = currency;
       	tradeManager[totalSymbolIndex][6].exchangeRate = exchangerate;
       
       //	tradeManager[totalSymbolIndex][2].largerTimeFrameTraderManager = tradeManager[totalSymbolIndex][6];
      	tradeManager[totalSymbolIndex][4].largerTimeFrameTraderManager = tradeManager[totalSymbolIndex][6];
      	tradeManager[totalSymbolIndex][4].smallTimeFrameTraderManager = tradeManager[totalSymbolIndex][2];

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

	
	
	
	public void replaceQuotes( HashMap<String, QuoteData> quotes, int CHART, QuoteData data )
	{
		/*
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
		
		String indexStr = data.time.substring(0,12) + minStr;*/
		String indexStr = data.time.substring(0,15);
		data.time = indexStr;
		
		quotes.put( indexStr, data);
	}

	
	
	
	private void initialize()
	{
		contracts = new Contract[TOTAL_SYMBOLS];
		quoteFirstTime = new boolean[TOTAL_SYMBOLS];
		tradeManager = new SF2USDJPYBOTradeManager[TOTAL_SYMBOLS][TOTAL_TRADES];	
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
	    			if (( tradeManager[i][TRADE_15M].portofolioReport != null ) && (!tradeManager[i][TRADE_15M].portofolioReport.equals("")))
	    			{
	    				out.write(tradeManager[i][TRADE_15M].portofolioReport + "\n");
	    				totalProfit += tradeManager[i][TRADE_15M].totalProfit;
	    				totalTrade += tradeManager[i][TRADE_15M].totalTrade;
	    			}
		    	}
	
				out.write("\n\nTOTAL PROFIT: " + totalProfit + " USD          TOTAL TRADES: " + totalTrade + "\n" );
	
		    	out.close();
	    	}
	    	else
	    	{
		    	for ( int i = 0; i < TOTAL_SYMBOLS; i++ )
		    	{
	    			if (( tradeManager[i][TRADE_15M].portofolioReport != null ) && (!tradeManager[i][TRADE_15M].portofolioReport.equals("")))
	    			{
	    				totalProfit += tradeManager[i][TRADE_15M].totalProfit;
	    				totalTrade += tradeManager[i][TRADE_15M].totalTrade;
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
		//if ( dataBroken == true ) 
		{
			String currentTime = IBDataFormatter.format(new Date());
			logger.warning("request history data update - currentTime:" + currentTime);

			for ( int i = 0; i < TOTAL_SYMBOLS; i++)
			{
				id_history[i] += TOTAL_SYMBOLS;

				//System.out.println(tradeManager[i][TIME_PERIOD].symbol + " request history data update id:" + id_history[i] + " - currentTime:" + currentTime);
				
				m_client.reqHistoricalData( id_history[i], contracts[i],  
    //					currentTime, "7200 S", "1 hour", "MIDPOINT", 0, 1);	// 2 hour
						currentTime, "10800 S", "5 mins", "MIDPOINT", 0, 1);	// 3 hour
				
			}
			//dataBroken = false;
		}
	}

	public void sleep ( int min )
	{
		try
		{
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



	
	private boolean checkSave()
	{
		try
		{
			BufferedReader br = new BufferedReader( new FileReader(outputDir +saveCmd));
			return true;
		}
		catch ( FileNotFoundException e)
		{
			return false;
		}
	}

	private boolean checkClose()
	{
		try
		{
			BufferedReader br = new BufferedReader( new FileReader(outputDir +closeCmd));
			return true;
		}
		catch ( FileNotFoundException e)
		{
			return false;
		}
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
