package tao.trading.strategy;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.QuoteData;
import tao.trading.SlimJimOrderManager;
import tao.trading.StretchOrderManager3;
import tao.trading.Trade;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

public class SlimJim implements EWrapper
{
	private static Logger logger = Logger.getLogger("SlimJim");
	protected Indicator indicator = new Indicator();
	protected Contract contract;
	protected EClientSocket m_client = new EClientSocket(this);
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
	protected SlimJimBreakoutTradeManager[] slimJimBreakoutTradeManager;	
	protected SlimJimHangingTradeManager[] slimJimHangingTradeManager;	
	protected SlimJimReverseTradeManager[] slimJimReverseTradeManager;	
	String filename = null;
	Writer output = null;
	double pullback;
	int numOfBars;
	boolean[] history60m;
	boolean[] history1m;
	Contract[] contracts;
	//int[] id_1m_history;
	int[] id_60m_history;
	int[] id_1m_history;
	int[] id_realtime;
	int totalSymbol;
	int slimJimNumOfBars = 10;
	Vector<QuoteData>[] quotes1m; 
	Vector<QuoteData>[] quotes60m; 
	double[] high1min;
	double[] low1min;
	double[] open1min;
	double[] distance;
	boolean[] reqRealTimeBar;
	int totalSymbolIndex = 0;
	int connectionId = 100;
	
	public static void main(String[] args)
	{
		SlimJim slimJim = new SlimJim();
		slimJim.run();
		
		Date now = new Date();
		long min = ( now.getTime() /60000 ) % 60;
		
		toSleep((62-min)*60);  // this is to round up to 2 minute after the hour
		
		while ( true )
		{
			slimJim.run();
			toSleep(3600L);	// 1 hour
		}

		/*

			try{
				
				Thread.sleep(990000000);
			}
			catch( Exception e)
			{
			}*/
		}

	public void run() {
		
		// this will kick off the sequence
		m_client.reqCurrentTime();
		
	}

	public SlimJim()
	{
        totalSymbol = 24;
		
		contracts = new Contract[totalSymbol];
		distance = new double[totalSymbol];
		quotes1m = new Vector[totalSymbol];
		quotes60m = new Vector[totalSymbol];
		high1min = new double[totalSymbol];
		low1min = new double[totalSymbol];
		open1min = new double[totalSymbol];
		history60m = new boolean[totalSymbol];
		history1m = new boolean[totalSymbol];
		slimJimBreakoutTradeManager = new SlimJimBreakoutTradeManager[totalSymbol];	
		slimJimHangingTradeManager = new SlimJimHangingTradeManager[totalSymbol];	
		slimJimReverseTradeManager = new SlimJimReverseTradeManager[totalSymbol];	
		reqRealTimeBar = new boolean[totalSymbol];
		
		for ( int i = 0; i < totalSymbol; i++)
		{
			contracts[i] = new Contract();
			quotes1m[i] = new Vector<QuoteData>(200);
			quotes60m[i] = new Vector<QuoteData>(200);
			high1min[i] = 0;
			low1min[i] = 999;
			open1min[i] = -1;
			reqRealTimeBar[i] = false;
			history60m[i] = history1m[i] = false;
		}
		
		//createContract( 0, 100, "USD", "CASH", "IDEALPRO", "JPY", 0.15);//0.004

		/*
		createContract( 0, 100, "EUR", "CASH", "IDEALPRO", "USD", 0.0015);	
		createContract( 1, 101, "EUR", "CASH", "IDEALPRO", "GBP", 0.0015);
		createContract( 2, 102, "EUR", "CASH", "IDEALPRO", "CHF", 0.001);
		createContract( 3, 103, "EUR", "CASH", "IDEALPRO", "JPY", 0.4);
		createContract( 4, 104, "EUR", "CASH", "IDEALPRO", "CAD", 0.0025);
		createContract( 5, 105, "EUR", "CASH", "IDEALPRO", "AUD", 0.003);
		createContract( 6, 106, "USD", "CASH", "IDEALPRO", "JPY", 0.25);
		createContract( 7, 107, "USD", "CASH", "IDEALPRO", "CAD", 0.003);
		createContract( 8, 108, "GBP", "CASH", "IDEALPRO", "JPY", 0.6);
		createContract( 9, 109, "USD", "CASH", "IDEALPRO", "CHF", 0.0025);
		
		id_1m_history = new int[]{0,1,2,3,4,5,6,7,8,9};
		id_60m_history = new int[]{20,21,22,23,24,25,26,27,28,29};
		id_realtime = new int[]{100,101,102,103,104,105,106,107,108,109};
*/
		
		createContract( "EUR", "CASH", "IDEALPRO", "USD", new DecimalFormat("###.0000"));
		createContract( "EUR", "CASH", "IDEALPRO", "GBP", new DecimalFormat("###.00"));
		createContract( "EUR", "CASH", "IDEALPRO", "CHF", new DecimalFormat("###.00"));  //7
		createContract( "EUR", "CASH", "IDEALPRO", "JPY", new DecimalFormat("###.00"));
		createContract( "EUR", "CASH", "IDEALPRO", "CAD", new DecimalFormat("###.00"));
		createContract( "EUR", "CASH", "IDEALPRO", "AUD", new DecimalFormat("###.00"));
		createContract( "EUR", "CASH", "IDEALPRO", "NZD", new DecimalFormat("###.00"));
		createContract( "EUR", "CASH", "IDEALPRO", "NOK", new DecimalFormat("###.00"));
		createContract( "USD", "CASH", "IDEALPRO", "CHF", new DecimalFormat("###.0000"));
		createContract( "USD", "CASH", "IDEALPRO", "CAD", new DecimalFormat("###.0000"));
		createContract( "USD", "CASH", "IDEALPRO", "JPY", new DecimalFormat("###.00"));
		createContract( "GBP", "CASH", "IDEALPRO", "JPY", new DecimalFormat("###.00"));
		createContract( "GBP", "CASH", "IDEALPRO", "USD", new DecimalFormat("###.00"));
		createContract( "GBP", "CASH", "IDEALPRO", "CHF", new DecimalFormat("###.00"));
		createContract( "GBP", "CASH", "IDEALPRO", "CAD", new DecimalFormat("###.00"));
		createContract( "GBP", "CASH", "IDEALPRO", "AUD", new DecimalFormat("###.00"));
		createContract( "GBP", "CASH", "IDEALPRO", "NZD", new DecimalFormat("###.00"));
		createContract( "AUD", "CASH", "IDEALPRO", "USD", new DecimalFormat("###.00"));
		createContract( "AUD", "CASH", "IDEALPRO", "NZD", new DecimalFormat("###.00"));
		createContract( "CHF", "CASH", "IDEALPRO", "JPY", new DecimalFormat("###.00"));
		createContract( "CAD", "CASH", "IDEALPRO", "JPY", new DecimalFormat("###.00"));
		createContract( "NZD", "CASH", "IDEALPRO", "JPY", new DecimalFormat("###.00"));
		createContract( "NZD", "CASH", "IDEALPRO", "USD", new DecimalFormat("###.00"));
		createContract( "NZD", "CASH", "IDEALPRO", "CHF", new DecimalFormat("###.00"));

		
		id_1m_history = new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
		id_60m_history = new int[]{100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123};
		id_realtime = new int[]{200,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223};

        m_client.eConnect("127.0.0.1" , 7496, 2);
        m_client.setServerLogLevel(5);

	}
	
	@Override
	public void error(Exception e) {
		System.out.println("Error: " );
		e.printStackTrace();
		
	}

	@Override
	public void error(String str) {
		System.out.println("Error: " + str);
		
	}

	@Override
	public void error(int id, int errorCode, String errorMsg) {
		System.out.println("Error: " + id + " " + errorCode + " " + errorMsg);
		
		
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

		for ( int i = 0; i < totalSymbol; i++)
		{
			/*
			quotes60m[i].clear();
	    	m_client.reqHistoricalData( id_60m_history[i], contracts[i],  
	    			currentTime, //"20091015 16:26:44",
	                "36000 S", "1 min", "MIDPOINT", 1, 1);               
*/
			if ( reqRealTimeBar[i] == true )
				m_client.cancelRealTimeBars(id_realtime[i]);
	    	
			m_client.reqHistoricalData( id_60m_history[i], contracts[i],  
    			currentTime, //"20091015 16:26:44",
           		"10 D", "1 hour", "MIDPOINT", 1, 1);  
			
			
			//m_client.reqRealTimeBars(id_realtime[i], contracts[i], 60, "MIDPOINT", true);

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
		if (date.indexOf("finished-") == -1)
		{
			for ( int i = 0; i < totalSymbol; i++)
			{
				if (reqId == id_60m_history[i])
				{
					QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
					quotes60m[i].add(data);
					break;
				}
			}
		}
		else
		{
			for ( int i = 0; i < totalSymbol; i++)
			{
				if (reqId == id_60m_history[i])
				{
					// reduce size to 500
					while ( quotes60m[i].size() > 500 )
					{
						quotes60m[i].remove(0);
					}

					//logger.info( contracts[i].m_symbol + "." + contracts[i].m_currency + " started");

					//if ( reqRealTimeBar[i] == false )
					{
						m_client.reqRealTimeBars(id_realtime[i], contracts[i], 60, "MIDPOINT", true);
						reqRealTimeBar[i] = true;
						logger.info( contracts[i].m_symbol + "." + contracts[i].m_currency  + " real time bar requested");
					}
				}

				
/*				
				if (reqId == id_60m_history[i])
				{
					if ( reqRealTimeBar[i] == false )
					{
						m_client.reqRealTimeBars(id_realtime[i], contracts[i], 60, "MIDPOINT", true);
						reqRealTimeBar[i] = true;
					}
					//System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " started");

					/* this to move to real time bar
					double[] sma200_60m = indicator.calculateSMA(quotes60m[i], 200);
					double[] sma50_60m = indicator.calculateSMA(quotes60m[i], 50);
					double[] sma20_60m = indicator.calculateSMA(quotes60m[i], 20);

					if ( slimJimBreakoutTradeManager[i].getTrade() == null )
					{
						Trade trade = null;
						if (( trade = slimJimBreakoutTradeManager[i].calculateSlimJimBreakout(quotes60m[i], distance[i])) != null )
						{	
							System.out.println( "Breakout Triggered for " + contracts[i].m_symbol + "." + contracts[i].m_currency + " " + trade.toString());
							slimJimBreakoutTradeManager[i].placeTrade(trade);
						}
					}
					else
					{
						slimJimBreakoutTradeManager[i].trackTradeExit(quotes60m[i].lastElement());//,sma20_60m, sma50_60m, sma200_60m);
					}*/
					/*
					if ( slimJimHangingTradeManager[i].getTrade() == null )
					{
						Trade trade = null;
						if (( trade = slimJimHangingTradeManager[i].calculateSlimJimHanging(quotes60m[i], sma20_60m, distance[i])) != null )
						{	
							System.out.println( "Hanging Triggered for " + contracts[i].m_symbol + "." + contracts[i].m_currency + " " + trade.toString());
							slimJimHangingTradeManager[i].placeTrade(trade);
						}
					}
					
					if ( slimJimReverseTradeManager[i].getTrade() == null )
					{
						Trade trade = null;
						if (( trade = slimJimReverseTradeManager[i].calculateSlimJimReverse(quotes60m[i], sma200_60m, sma50_60m, distance[i])) != null )
						{	
							System.out.println( "Reversal Triggered for " + contracts[i].m_symbol + "." + contracts[i].m_currency + " " + trade.toString());
							slimJimReverseTradeManager[i].placeTrade(trade);
						}
					}
					*/
				
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
		//System.out.println("orderStatus: " + " orderId " + orderId + " status " + status + " filled " + filled 
		//		+ " remaining " + remaining + " avgFillPrice " + avgFillPrice + " permId " + permId + " parentId " + parentId
		//		+ " lastFillPrice " + lastFillPrice + " clientId " + clientId + " whyHeld " + whyHeld);
		
		//orderStatus:  orderId 113 status Filled filled 10 remaining 0 avgFillPrice 1.4709 permId 1101316837 parentId 0 lastFillPrice 1.4709 clientId 200 whyHeld null
		if ("Filled".equals(status)) //&& (orderId > Constants.ORDER_ID_STOP_OFFSET))
		{
			for ( int i = 0; i < totalSymbol; i++)
			{
				slimJimBreakoutTradeManager[i].checkStopTriggered(orderId);
			}
		}
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{

		String timeStr = IBDataFormatter.format(new Date(time*1000));
		String hr = timeStr.substring(9, 11);
		String min = timeStr.substring(12, 14);
		String sec = timeStr.substring(15,17);
		//logger.info(reqId + " " + " hr:" + hr + " min:"+min+ " sec:" + sec);
		//System.out.println("reqId:" + reqId + " time:" + time + " high:" + high + " low:" + low + " close:" + close);
		// the goal is once the current bar does not make new high

		if (sec.equals("00"))
			logger.info("reqId:" + reqId + " time:" + time + " high:" + high + " low:" + low + " close:" + close);
		
		QuoteData data = new QuoteData(timeStr, open, high, low, close, 0, 0, wap, false);

		
		for ( int i = 0; i < totalSymbol; i++)
		{
			if (reqId == id_realtime[i])
			{
				if ( slimJimBreakoutTradeManager[i].getTrade() == null )
				{
					Trade trade = null;
					if (( trade = slimJimBreakoutTradeManager[i].calculateSlimJimBreakout(data, quotes60m[i] )) != null )
					{	
						System.out.println( "Breakout Triggered for " + contracts[i].m_symbol + "." + contracts[i].m_currency + " " + trade.toString());
						slimJimBreakoutTradeManager[i].placeTrade(trade);
					}
				}
				else
				{
				//	slimJimBreakoutTradeManager[i].trackTradeExit(quotes60m[i].lastElement());//,sma20_60m, sma50_60m, sma200_60m);
				}
			}
		}
		 
		
		/*
		for ( int i = 0; i < totalSymbol; i++)
		{
			if (reqId == id_realtime[i])
			{
				if (high > high1min[i])
					high1min[i] = high; 
				if ( low < low1min[i] )
					low1min[i] = low;
				if ( open1min[i] == -1 )
					open1min[i] = open;

				
				if ( slimJimBreakoutTradeManager[i].getTrade() == null )
				{
					Trade trade = null;
					if (( trade = slimJimBreakoutTradeManager[i].calculateSlimJimBreakout(data, quotes60m[i])) != null )
					{	
						System.out.println( "Breakout Triggered for " + contracts[i].m_symbol + "." + contracts[i].m_currency + " " + trade.toString());
						slimJimBreakoutTradeManager[i].placeTrade(trade);
					}
				}
				
				if ( slimJimBreakoutTradeManager[i].getTrade() == null )
				{
					Trade trade = null;
					if (( trade = slimJimBreakoutTradeManager[i].calculateSlimJimBreakout(data, quotes60m[i])) != null )
					{	
						System.out.println( "Breakout Triggered for " + contracts[i].m_symbol + "." + contracts[i].m_currency + " " + trade.toString());
						slimJimBreakoutTradeManager[i].placeTrade(trade);
					}
				}

				if (sec.equals("00"))
				{
					QuoteData data1 = new QuoteData(timeStr, open1min[i], high1min[i], low1min[i], close, 0, 0, wap, false);
					quotes60m[i].add(data1);
					
					if ( quotes60m[i].size() > 1000)
						quotes60m[i].removeElementAt(0); // to prevent it running too long

					// reset counter
					high1min[i]=0;
					low1min[i]=999;
					open1min[i] = -1;
				}
				
			}
		}

*/
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
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
	public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta,
			double modelPrice, double pvDividend)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickPrice(int tickerId, int field, double price, int canAutoExecute)
	{
		// TODO Auto-generated method stub
		
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
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connectionClosed()
	{
		// TODO Auto-generated method stub
		
	}

	private void writeToFile( String str )
	{

		try {
		        BufferedWriter out = new BufferedWriter(new FileWriter("filename", true));
		        out.write(str );
		        out.close();
		    } catch (IOException e) {
		    }
	}
/*
	private void createContract( int i, int id, String symbol, String type, String exchange, String currency, double dist )
	{
		Contract contract = contracts[i];
    	contract.m_conId = id;
    	contract.m_symbol = symbol;
        contract.m_secType = type;
        contract.m_exchange = exchange;
        contract.m_currency = currency;
        
        distance[i] = dist;
        
        slimJimBreakoutTradeManager[i] = new SlimJimBreakoutTradeManager(m_client,contract, i, logger);
        slimJimHangingTradeManager[i] = new SlimJimHangingTradeManager(m_client,contract, i, logger );
        slimJimReverseTradeManager[i] = new SlimJimReverseTradeManager(m_client,contract, i, logger );
	}

	*/
	private void createContract(String symbol, String type, String exchange, String currency, DecimalFormat decf)
	{
		Contract contract = contracts[totalSymbolIndex];
    	contract.m_conId = connectionId++;
    	contract.m_symbol = symbol;
        contract.m_secType = type;
        contract.m_exchange = exchange;
        contract.m_currency = currency;
       
        slimJimBreakoutTradeManager[totalSymbolIndex] = new SlimJimBreakoutTradeManager(m_client,contract, totalSymbolIndex, logger);
        slimJimHangingTradeManager[totalSymbolIndex] = new SlimJimHangingTradeManager(m_client,contract, totalSymbolIndex, logger );
        slimJimReverseTradeManager[totalSymbolIndex] = new SlimJimReverseTradeManager(m_client,contract, totalSymbolIndex, logger );
        totalSymbolIndex++;
	}

	

	private int calculatePositionSize()
	{
		return 1;
	}
	
	static void toSleep( long sec )
	{
		try{
			Thread.sleep(sec * 1000);
			//Thread.sleep(120000);
		}catch( Exception e)
		{
		
		}
	}
	

	
	
}
