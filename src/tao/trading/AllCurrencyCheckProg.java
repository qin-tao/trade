package tao.trading;

import java.text.SimpleDateFormat;
import java.util.logging.Logger;

import tao.trading.strategy.QuoteTracker;

public class AllCurrencyCheckProg
{
	private static Logger logger = Logger.getLogger("MovingAverageCheckProg");
	protected static SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		QuoteTracker EUR_USD = new QuoteTracker(200,"EUR", "CASH", "IDEALPRO", "USD", 0.002, 300);
		QuoteTracker EUR_GBP = new QuoteTracker(201,"EUR", "CASH", "IDEALPRO", "GBP", 0.002, 300);
		QuoteTracker EUR_JPY = new QuoteTracker(202,"EUR", "CASH", "IDEALPRO", "JPY", 0.4, 300);
		QuoteTracker USD_JPY = new QuoteTracker(203,"USD", "CASH", "IDEALPRO", "JPY", 0.1, 300);
		QuoteTracker USD_CAD = new QuoteTracker(204,"USD", "CASH", "IDEALPRO", "CAD", 0.003, 300);
		QuoteTracker USD_CHF = new QuoteTracker(205,"USD", "CASH", "IDEALPRO", "CHF", 0.004, 300);
		QuoteTracker GBP_USD = new QuoteTracker(206,"GBP", "CASH", "IDEALPRO", "USD", 0.006, 300);

		logger.info("All Currency Check program started at " + IBDataFormatter.format(new java.util.Date()));
				USD_JPY.run();
				EUR_USD.run();
				EUR_GBP.run();
				EUR_JPY.run();
				USD_CAD.run();
				USD_CHF.run();
				GBP_USD.run();

			try{
				
				Thread.sleep(990000000);
			}
			catch( Exception e)
			{
			}
		}
	
}
