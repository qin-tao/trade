package tao.trading.strategy;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import tao.trading.Constants;
import tao.trading.Indicator;
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

public class Calculate implements EWrapper
{
	private static Logger logger = Logger.getLogger("StretchElliotM");
	protected Indicator indicator = new Indicator();
	protected EClientSocket m_client = new EClientSocket(this);
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
	protected StretchElliotTradeManager2[] tradeManager;	
	Contract[] contracts, contracts_trade;
	int[] id_1m_history;
	int[] id_5m_history;
	int[] id_60m_history;
	int[] id_realtime;
	int[] index;
	Vector<QuoteData>[] quotes; 
	Vector<QuoteData>[] quotes5M; 
	Vector<QuoteData>[] quotes60M; 
	boolean[] quoteFirstTime;
	int totalSymbolIndex = 0;
	int connectionId = 100;
	DecimalFormat[] decimalFormat;
	int MAX_QUOTE_SIZE = 1200;
	int totalSymbol = 24;
	
	public static void main(String[] args)
	{
    	Calculate open = new Calculate();
		open.run();
		
		try{
			Thread.sleep(990000000);
		}
		catch( Exception e)
		{
		}
	}

	public void run() {
		
		// this will kick off the sequence
		m_client.reqCurrentTime();
		
	}

	public Calculate()
	{
		// create a logger
		try
		{
			// Create an appending file handler
			String logFile = "stretchElliotMinute2.log";
			boolean append = true;
			FileHandler handler = new FileHandler(logFile, append);
			//FileHandler handler = new FileHandler(logFile);
			handler.setFormatter(new SimpleFormatter());

			//ConsoleHandler handler = new ConsoleHandler();

			// Add to the desired logger
			logger = Logger.getLogger("StretchElliotM");
			logger.addHandler(handler);
		
		} 
		catch (IOException e)
		{
		}


		contracts = new Contract[totalSymbol];
		contracts_trade = new Contract[totalSymbol];
		quotes = new Vector[totalSymbol];
		quotes5M = new Vector[totalSymbol];
		quotes60M = new Vector[totalSymbol];
		index = new int[totalSymbol];
		quoteFirstTime = new boolean[totalSymbol];
		tradeManager = new StretchElliotTradeManager2[totalSymbol];	
    	decimalFormat = new DecimalFormat[totalSymbol];
		
		for ( int i = 0; i < totalSymbol; i++)
		{
			contracts[i] = new Contract();
			contracts_trade[i] = new Contract();
			quotes[i] = new Vector<QuoteData>(200);
			quotes5M[i] = new Vector<QuoteData>(200);
			quotes60M[i] = new Vector<QuoteData>(200);
		}
		
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
		id_5m_history = new int[]{100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123};
		id_60m_history = new int[]{150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173};
		id_realtime = new int[]{200,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223};

        m_client.eConnect("127.0.0.1" , 7496, 22);
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
			m_client.reqHistoricalData( id_60m_history[i], contracts[i],  
	    			"20090208 00:00:00",
	           		"10 D", "1 hour", "MIDPOINT", 1, 1);  
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
		
		System.out.println("id:" + reqId + " Date:" + date + " Open:" + open + " High" + high + " Low" + low + " Close:" + close + " Volumn:" + volume + " count:" + count + 
				" WAP:" + WAP + " hasGaps:" + hasGaps);
		
		if (date.indexOf("finished-") == -1)
		{
			for ( int i = 0; i < totalSymbol; i++)
			{
				if (reqId == id_60m_history[i])
				{
					QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
					quotes60M[i].add(data);
					break;
				}
			}
		}
		else
		{
			for ( int i = 0; i < totalSymbol; i++)
			{
				if (reqId == id_1m_history[i])
				{
					logger.info(contracts[i].m_symbol+"."+contracts[i].m_currency+ " " + quotes[i].size() + " bar received");
					
					m_client.reqRealTimeBars(id_realtime[i], contracts[i], 60, "MIDPOINT", true);
					System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " started");
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
		// no need to re-enter
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{
		String timeStr = IBDataFormatter.format(new Date(time*1000));
		String hr = timeStr.substring(9, 11);
		String min = timeStr.substring(12, 14);
		String sec = timeStr.substring(15,17);
		
		for ( int i = 0; i < totalSymbol; i++)
		{
			if (reqId == id_realtime[i])
			{
				Trade trade = tradeManager[i].getTrade();

				QuoteData data = new QuoteData(timeStr, open, high, low, close, 0, 0, wap, false);

				if ( quoteFirstTime[i]== false )
				{
					createNewQuoteEntry( quotes[i], data );
					createNewQuoteEntry( quotes5M[i], data );
					createNewQuoteEntry( quotes60M[i], data );
					quoteFirstTime[i]=true;
				}

				int second = new Integer(sec);
				int minute = new Integer(min);

				if ( second == 0)
				{
					createNewQuoteEntry( quotes[i], data );
					if (( trade != null ) && ( trade.status == Constants.STATUS_OPEN))
					{
						createNewQuoteEntry( trade.entryQuotes, data );
					}

					if ( minute % 3 == 0 )
					{
						createNewQuoteEntry( quotes5M[i], data );
					}
					
					if ( minute == 0 )
					{
						createNewQuoteEntry( quotes60M[i], data );
					}
				}

				// always need to update quotes
				updateLastQuoteEntry( quotes[i], data );
				updateLastQuoteEntry( quotes5M[i], data );
				updateLastQuoteEntry( quotes60M[i], data );

				double[] sma200 = indicator.calculateEMA(quotes[i], 200);
				double avgBarSize = tradeManager[i].getAverage(quotes[i]);
				
				double[] ema60_5 = indicator.calculateSMA(quotes60M[i], 5);
				double[] ema60_20 = indicator.calculateSMA(quotes60M[i], 20);

				// this is for better entry
				if ( trade == null )
				{
					if (( trade = tradeManager[i].calculateStretchHigh( quotes[i], sma200, ema60_5, ema60_20, avgBarSize)) != null )
					{
						trade.entryQuotes5M.add(quotes[i].lastElement());
						//tradeManager[i].trade = trade;
						logger.info(contracts[i].m_symbol+"."+contracts[i].m_currency+ " " + quotes[i].size() + " trade placed");
					}
					else if (( trade = tradeManager[i].calculateStretchLow( quotes[i], sma200, ema60_5, ema60_20, avgBarSize)) != null )
					{
						trade.entryQuotes5M.add(quotes[i].lastElement());
						//tradeManager[i].trade = trade;
						logger.info(contracts[i].m_symbol+"."+contracts[i].m_currency+ " " + quotes[i].size() + " trade placed");
					} 
					
				}
				else
				{
					updateLastQuoteEntry( trade.entryQuotes, data );
					
					// trade already exists
					if (trade.status.equals(Constants.STATUS_STOPPEDOUT) || trade.status.equals(Constants.STATUS_OPEN) )
					{
						//logger.info(contracts[i].m_symbol+"."+contracts[i].m_currency + "trackTradeEntry");
						tradeManager[i].trackTradeEntry(data);
					}
					else if (trade.status.equals(Constants.STATUS_PLACED))
					{
						// this is to check whether it needs any "stop and reversal"
						//logger.info(contracts[i].m_symbol+"."+contracts[i].m_currency + "trackTradeExit");
						tradeManager[i].trackTradeTarget(data, sma200, quotes5M[i]);
					}
					else if (trade.status.equals(Constants.STATUS_EXITING))
					{
						updateLastQuoteEntry( trade.exitQuotes, data );
						// this is to check whether it needs any "stop and reversal"
						//logger.info(contracts[i].m_symbol+"."+contracts[i].m_currency + "trackTradeExit");
						tradeManager[i].trackTradeExit(quotes[i], sma200, avgBarSize );
					}
				}
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


	private void createContract(String symbol, String type, String exchange, String currency, DecimalFormat decf)
	{
		Contract contract = contracts[totalSymbolIndex];
    	contract.m_conId = connectionId++;
    	contract.m_symbol = symbol;
        contract.m_secType = type;
        contract.m_exchange = exchange;
        contract.m_currency = currency;
       
        /*
		Contract contract2 = contracts_trade[totalSymbolIndex];
    	contract2.m_conId = connectionId++;
    	contract2.m_symbol = symbol;
        contract2.m_secType = type;
        contract2.m_exchange = "IDEAL";
        contract2.m_currency = currency;
         */
        tradeManager[totalSymbolIndex] = new StretchElliotTradeManager2(m_client,contract, totalSymbolIndex, logger );
        decimalFormat[totalSymbolIndex]= decf;
        // increase one for next
        totalSymbolIndex++;
	}
	

	
	public void createNewQuoteEntry( Vector<QuoteData> quotes, QuoteData data )
	{
		quotes.add( data );
		
		while( quotes.size() > MAX_QUOTE_SIZE )
			quotes.remove(0);
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

}
