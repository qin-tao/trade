package tao.trading;

import java.util.Vector;
import java.util.logging.Logger;

/*
 * 
 * In this example we shall calculate EMA for a the price of a stock. We want a 22 day EMA which is a common enough time frame for a long EMA.

The formula for calculating EMA is as follows:

EMA = Price(t) * k + EMA(y) * (1 - k)

t = today, y = yesterday, N = number of days in EMA, k = 2/(N+1)

Use the following steps to calculate a 22 day EMA:

1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869

2) Add the closing prices for the first 22 days together and divide them by 22.

3) You’re now ready to start getting the first EMA day by taking the following day’s (day 23) closing price multiplied by k, then multiply the previous day’s moving average by (1-k) and add the two.

4) Do step 3 over and over for each day that follows to get the full range of EMA.

This can of course be put into Excel or some other spreadsheet software to make the process of calculating EMA semi-automatic.

To give you an algorithmic view on how this can be accomplished, see below.

public float CalculateEMA(float todaysPrice, float numberOfDays, float EMAYesterday){
   float k = 2 / (numberOfDays + 1);
   return todaysPrice * k + EMAYesterday * (1 - k);
}

This method would typically be called from a loop through your data, looking something like this:

foreach (DailyRecord sdr in DataRecords){
   //call the EMA calculation
   ema = Formulas.EMA(sdr.Close, numberOfDays, yesterdayEMA);

   //put the calculated ema in an array
   m_emaSeries.Items.Add(sdr.TradingDate, ema);

  //make sure yesterdayEMA gets filled with the EMA we used this time around
   yesterdayEMA = ema;
}

 */
public class Indicator {

	Logger logger = Logger.getLogger("Indicator");

	public void calAll5EMA( Vector<QuoteData> quotes)
	{
		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		double k = 2.0 / 6.0;
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		double sum = 0;
		for ( int i = 0; i < 5; i++ )
		{
			sum += ((QuoteData)quotes.elementAt(i)).close;
		}
		double ema = sum/5;
		((QuoteData)quotes.elementAt(4)).ema5 = ema;
		
		int size = quotes.size();
		for( int i = 5; i < size; i++ )
		{
			((QuoteData)quotes.elementAt(i)).ema5 = ((QuoteData)quotes.elementAt(i)).close * k + ((QuoteData)quotes.elementAt(i-1)).ema5 * (1 - k);
			
		}
		
	}

	
	public void calAll20EMA( Vector<QuoteData> quotes)
	{
		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		double k = 2.0 / 21.0;
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		double sum = 0;
		for ( int i = 0; i < 20; i++ )
		{
			sum += ((QuoteData)quotes.elementAt(i)).close;
		}
		double ema = sum/20;
		((QuoteData)quotes.elementAt(19)).ema20 = ema;
		
		int size = quotes.size();
		for( int i = 20; i < size; i++ )
		{
			((QuoteData)quotes.elementAt(i)).ema20 = ((QuoteData)quotes.elementAt(i)).close * k + ((QuoteData)quotes.elementAt(i-1)).ema20 * (1 - k);
			
		}
		
	}
	
	
	
	public void calAll50EMA( Vector<QuoteData> quotes)
	{
		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		double k = 2.0 / 51.0;
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		double sum = 0;
		for ( int i = 0; i < 50; i++ )
		{
			sum += ((QuoteData)quotes.elementAt(i)).close;
		}
		double ema = sum/50;
		((QuoteData)quotes.elementAt(49)).ema50 = ema;
		
		int size = quotes.size();
		for( int i = 50; i < size; i++ )
		{
			((QuoteData)quotes.elementAt(i)).ema50 = ((QuoteData)quotes.elementAt(i)).close * k + ((QuoteData)quotes.elementAt(i-1)).ema50 * (1 - k);
			
		}
		
	}

	public void calAll200EMA( Vector<QuoteData> quotes)
	{
		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		double k = 2.0 / 201.0;
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		double sum = 0;
		for ( int i = 0; i < 200; i++ )
		{
			sum += ((QuoteData)quotes.elementAt(i)).close;
		}
		double ema = sum/200;
		((QuoteData)quotes.elementAt(199)).ema200 = ema;
		
		int size = quotes.size();
		for( int i = 200; i < size; i++ )
		{
			((QuoteData)quotes.elementAt(i)).ema200 = ((QuoteData)quotes.elementAt(i)).close * k + ((QuoteData)quotes.elementAt(i-1)).ema200 * (1 - k);
			
		}
		
	}

	
	

	static public double[] calculateEMA( double[] value, int days )
	{
		int size = value.length;

		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		double k = 2.0 / ((double)days + 1.0);
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		double sum = 0;
		for ( int i = 0; i < days; i++ )
		{
			sum += value[i];
		}
		double ema = sum/days;
		
		double[] emas = new double[size];
		emas[days-1] = ema;
		
		for( int i = days; i < size; i++ )
		{
			emas[i] = value[i] * k + emas[i-1]* (1-k);
		}
		
		return emas;
		
	}

	static public double[] calculateEMA( double[] value, int days, int startPos )
	{
		int size = value.length;

		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		double k = 2.0 / ((double)days + 1.0);
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		double sum = 0;
		for ( int i = startPos; i < days+ startPos; i++ )
		{
			sum += value[i];
		}
		double ema = sum/days;
		
		double[] emas = new double[size];
		emas[days + startPos -1] = ema;
		
		for( int i = days + startPos; i < size; i++ )
		{
			emas[i] = value[i] * k + emas[i-1]* (1-k);
		}
		
		return emas;
		
	}

	
	

	
	static public double[] calculateEMA( Vector<QuoteData> quotes, int days )
	{
		int size = quotes.size();

		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		double k = 2.0 / ((double)days + 1.0);
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		double sum = 0;
		for ( int i = 0; i < days; i++ )
		{
			sum += ((QuoteData)quotes.elementAt(i)).close;
		}
		double ema = sum/days;
		
		double[] emas = new double[size];
		emas[days-1] = ema;
		
		for( int i = days; i < size; i++ )
		{
			emas[i] = ((QuoteData)quotes.elementAt(i)).close * k + emas[i-1]* (1-k);
			//((QuoteData)quotes.elementAt(i)).ema50 = ((QuoteData)quotes.elementAt(i)).close * k + ((QuoteData)quotes.elementAt(i-1)).ema50 * (1 - k);
		}
		
		return emas;
		
	}
	

	static public double[] calculateEMA( Object[] quotes, int days )
	{
		int size = quotes.length;

		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		double k = 2.0 / ((double)days + 1.0);
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		double sum = 0;
		for ( int i = 0; i < days; i++ )
		{
			sum += ((QuoteData)quotes[i]).close;
		}
		double ema = sum/days;
		
		double[] emas = new double[size];
		emas[days-1] = ema;
		
		for( int i = days; i < size; i++ )
		{
			emas[i] = ((QuoteData)quotes[i]).close * k + emas[i-1]* (1-k);
			//((QuoteData)quotes.elementAt(i)).ema50 = ((QuoteData)quotes.elementAt(i)).close * k + ((QuoteData)quotes.elementAt(i-1)).ema50 * (1 - k);
		}
		
		return emas;
		
	}

	static public double[] calculateEMA_mid( QuoteData[] quotes, int days )
	{
		int size = quotes.length;

		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		double k = 2.0 / ((double)days + 1.0);
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		double sum = 0;
		for ( int i = 0; i < days; i++ )
		{
			//sum += ((QuoteData)quotes[i]).close;
			sum += (quotes[i].high + quotes[i].low)/2.0;
		}
		double ema = sum/days;
		
		double[] emas = new double[size];
		emas[days-1] = ema;
		
		for( int i = days; i < size; i++ )
		{
			emas[i] = (quotes[i].high + quotes[i].low)/2 * k + emas[i-1]* (1-k);
		}
		
		return emas;
		
	}
	
	
	
	static public double[] calculateSMA(  Object[] quotes, int days )
	{
		int size = quotes.length;

		double[] smas = new double[size];
		
		for( int i = days; i < size; i++ )
		{
			double sum = 0.0;
			for ( int j = i; j > i-days; j--)
			{
				sum += ((QuoteData)quotes[j]).close;
			}
			
			smas[i] = sum/(double)days;
		}
		
		return smas;
		
	}
	

	static public double[] calculateSMA(  double[] values, int days )
	{
		int size = values.length;

		double[] smas = new double[size];
		
		for( int i = days; i < size; i++ )
		{
			double sum = 0.0;
			for ( int j = i; j > i-days; j--)
			{
				sum += values[j];
			}
			
			smas[i] = sum/(double)days;
		}
		
		return smas;
		
	}

	
	
	/*
	public static double[] calculateStochastics( Vector<QuoteData> quotes, int period )
	{
		int size = quotes.size();
		double[] k = new double[size];
		
		//%K = (Today's closing price - lowest low during the last [period] days) ÷ 
		// (highest high during the last [period] days - lowest low during the last [period] days)
		
		for( int i = period; i < size; i++ )
		{
			double high = 0.0;
			double low = 999;
			for ( int j = i; j >= i-period; j--)
			{
				if (((QuoteData)quotes.elementAt(j)).high > high )
					high = ((QuoteData)quotes.elementAt(j)).high;
				if (((QuoteData)quotes.elementAt(j)).low < low )
					low = ((QuoteData)quotes.elementAt(j)).low;
			}
			k[i] = 100*(((QuoteData)quotes.elementAt(i)).close - low )/(high-low); 
		}
		
		return k;
		
	}*/
	
	
	public static double[] calculateStochastics( Vector<QuoteData> quotes, int period )
	{
		return calculateStochastics(quotes.toArray(), period);


	}

	public static double[] calculateStochastics( Object[] quotes, int period )
	{
		int size = quotes.length;
		double[] k = new double[size];
		
		//%K = (Today's closing price - lowest low during the last [period] days) ÷ 
		// (highest high during the last [period] days - lowest low during the last [period] days)
		
		for( int i = period; i < size; i++ )
		{
			double high = 0.0;
			double low = 999;
			for ( int j = i; j > i-period; j--)
			{
				if (((QuoteData)quotes[j]).high > high )
					high = ((QuoteData)quotes[j]).high;
				if (((QuoteData)quotes[j]).low < low )
					low = ((QuoteData)quotes[j]).low;
			}
			k[i] = 100*(((QuoteData)quotes[i]).close - low )/(high-low); 
		}

		return calculateEMA( k, 3, 0 );
		
	}
/*
	public static double[] calculateStochastics( Vector<QuoteData> quotes, int period )
	{
		int size = quotes.size();
		double[] k = new double[size];
		
		//%K = (Today's closing price - lowest low during the last [period] days) ÷ 
		// (highest high during the last [period] days - lowest low during the last [period] days)
		
		for( int i = period; i < size; i++ )
		{
			double high = 0.0;
			double low = 999;
			for ( int j = i; j >= i-period; j--)
			{
				if (((QuoteData)quotes.elementAt(j)).high > high )
					high = ((QuoteData)quotes.elementAt(j)).high;
				if (((QuoteData)quotes.elementAt(j)).low < low )
					low = ((QuoteData)quotes.elementAt(j)).low;
			}
			k[i] = 100*(((QuoteData)quotes.elementAt(i)).close - low )/(high-low); 
		}
		
		return k;
		
	}
	*/
	
	
	
	
	
	
	public float calculateEMA(float todaysPrice, int numberOfDays){
	{
		//1) Start by calculating k for the given timeframe. 2 / (22 + 1) = 0,0869
		float k = 2 / (numberOfDays + 1);
		
		//2) Add the closing prices for the first 22 days together and divide them by 22.
		
		for ( int i = 0; i < numberOfDays; i++ )
		{
			
		}
		
		//3) You’re now ready to start getting the first EMA day by taking the following day’s (day 23) closing price multiplied by k, then multiply the previous day’s moving average by (1-k) and add the two.

		//4) Do step 3 over and over for each day that follows to get the full range of EMA.

		return  k;
	}
			
	}
	public float calculateEMA(float todaysPrice, float numberOfDays, float EMAYesterday){
		   float k = 2 / (numberOfDays + 1);
		   return todaysPrice * k + EMAYesterday * (1 - k);
		}
	
	

	
	
	
	
	/*
	boolean[] calculateHighs(Vector<QuoteData> qts, double pullback)
	{
		int lastHigh=0;
		int thisLow = 0;
		int size = qts.size();  // we look past the last bar
		boolean[] highs = new boolean[size];
		Object[] quotes = qts.toArray();

		for ( int i = 1; i < size; i++ )
		{
			if (((QuoteData)quotes[i]).close > ((QuoteData)quotes[lastHigh]).close )
			{
				lastHigh = i;
				
				// found out how low this low goes
				for ( int j = i; j< size; j++)
				{
					if (((QuoteData)quotes[i]).close < ((QuoteData)quotes[thisLow]).close )
					{
						thisLow = j
					}
				}
			}
			else
			{
				thisLow = i;
			}
			
			if ((((QuoteData)quotes[lastHigh]).close - ((QuoteData)quotes[i]).close) > pullback  )
			{
				highs[lastHigh]= true;
				lastHigh = i+1;
				i++;	// skip i+1
			}
			
		}
		
		if ( low < currentPrice )
			return false;
		
		boolean pullbackreached = false;
		boolean ematouched = false;
		for ( int i = lowIndex; i < size; i ++)
		{
			if (( ((QuoteData)quotes[i]).close - low) > pullbackGap)
				pullbackreached = true;
			if ((((QuoteData)quotes[i]).close > ((QuoteData)quotes[i]).ema50 ) || (((QuoteData)quotes[i]).close > ((QuoteData)quotes[i]).ema200 ))
				ematouched = true;
		}
		
		if ( pullbackreached )//&& ematouched )
			return true;
		else
			return false;
		
	}
	
	int findHigh( )
*/
	
	static public ADX[] calculateADX( Vector<QuoteData> qts )
	{
	    int N = 14; 		// number of days (we use 14)
	    double TH;			// today’s high
	    double TL;			// today’s low
	    double TS;			// today’s settle
	    double YH;			// yesterday’s high
	    double YL;			// yesterday’s low
	    double YS;			// yesterday’s settle
	    double ADM_UP;		// average directional movement up
	    double ADM_DOWN;	// average directional movement down
	    double DI_UP;		// directional indicator up
	    double DI_DOWN; 	// directional indicator down
	    double DX;			// directional movement
	    double ADX; 		//  average directional movement 

		Object[] quotes = qts.toArray();
		int size = quotes.length;
		int lastbar = size -1;
		
		
	    //  1. Calculate DM+ and DM-:
	    //  a. If YH > = TH, and YL < = TL (i.e. if today’s trading is totally within yesterday’s range), then DM+ = 0; DM- = 0
	    //  b. If TH - YH = YL - TL (i.e. if the differences between highs and lows are the same), then DM+ = 0; DM- = 0
	    //  c. If TH - YH > YL - TL (i.e. if the differences between highs and lows are the same), then DM+ = TH - YH; DM- = 0
	    //  d. Otherwise (i.e. the difference between lows is more than the difference between highs), DM+ = 0; DM- = YL - TL 
		ADX[] adxs = new ADX[size];
		
		double[] DM_UP = new double[size];	// directional movement up
		double[] DM_DOWN = new double[size];	// directional movement down
		double[] TR = new double[size];		// true range
		
		//for ( int i = lastbar - N*2 + 1; i <= lastbar; i++ )
		for ( int i = 1; i < size; i++ )
		{
			YH = ((QuoteData)quotes[i-1]).high;
			YL = ((QuoteData)quotes[i-1]).low;
			YS = ((QuoteData)quotes[i-1]).close;
			
			TH = ((QuoteData)quotes[i]).high;
			TL = ((QuoteData)quotes[i]).low;
			TS = ((QuoteData)quotes[i]).close;

			//A = Today's High – Yesterday's High
			//B = Yesterday's Low – Today's Low

			//Depending upon the values of A and B, three possible scenarios are:
			//Values 	Scenarios
			//Both A and B < 0 	+DM=0, -DM=0
			//A > B 	+DM=A, -DM=0
			//A < B 	+DM=0, -DM=B */ 
			
			// +DM = Today's High - Yesterday's High (when price moves upward)
			// -DM = Yesterday's Low - Today's Low (when price moves downward)
			// You cannot have both +DM and -DM on the same day. If there is an outside day (where both calculations are positive) then the larger of the two results is taken. An inside day (where both calculations are negative) will always equal zero.
			
			double A = TH - YH;
			double B = YL - TL;
			
			if ((A <= 0 ) && ( B <= 0 ))
			{
				DM_UP[i] = 0;
				DM_DOWN[i] = 0;
			}
			else if ( A > 0 && B > 0 )
			{
				if ( A > B)
				{
					DM_UP[i] = A;
					DM_DOWN[i]= 0;
				}
				else
				{
					DM_UP[i] = 0;
					DM_DOWN[i]= B;
				}
				
			}
			else if ( A > B )
			{
				DM_UP[i] = A;
				DM_DOWN[i]= 0;
			}
			else 
			{
				DM_UP[i] = 0;
				DM_DOWN[i]= B;
			}
		
			//	Calculate the true range for the day. 
			//	True range is the largest of:
			//	Today's High - Today's Low,
			//	Today's High - Yesterday's Close, and
			//	Yesterday's Close - Today's Low			
			TR[i] = TH-TL;
			if ( TH - YS  > TR[i] )
				TR[i] = TH - YS;
			if ( YS - TL  > TR[i] )
				TR[i] = YS - TL;
			
		}
	    
		
  	   	//  2. Calculate the true range (TR), see ATR
	    //	Wilder suggested using a period of 14 for calculations:
	    //  * +DM14 = Wilder's exponential moving average* of +DM for 14 periods.
	    //  * -DM14 = Wilder's exponential moving average* of -DM for 14 periods.
	    //  * TR14 = Wilder's exponential moving average* of True Range for 14 periods.
		
		double[] ema_dm_up = calculateEMA( DM_UP, N, 1 );		// we calculate EMA from 1 as 0 has no value in it
		double[] ema_dm_down = calculateEMA( DM_DOWN, N, 1 );
		double[] ema_tr = calculateEMA( TR, N, 1 );
		
		
		/*Calculate the Directional Indicators
		Positive Directional Indicator (+DI14) = +DM14 divided by TR14
		Negative Directional Indicator (-DI14) = -DM14 divided by TR14*/
		for ( int i = 2; i < size ; i++ )						// calculate from 2 as values start from 1
		{
			adxs[i] = new ADX();
			adxs[i].DI_UP = ema_dm_up[i]/ema_tr[i];
			adxs[i].DI_DOWN = ema_dm_down[i]/ema_tr[i];
		
			//DI Difference = The absolute value of the difference between +DI14 and -DI14.
			//Calculate the Directional Index (DX):
	    	//DX = DI Difference divided by the sum of +DI14 and -DI14
			adxs[i].DX = Math.abs(adxs[i].DI_UP - adxs[i].DI_DOWN)/( adxs[i].DI_UP + adxs[i].DI_DOWN);
		}
	    
	    // ADX = the exponential moving average* of DX 
		// not yet calculated
		
		return adxs;
	}
	
	
	
	static public MACD[] calculateMACD( QuoteData[] quotes )
	{
		/*
		MACD: (12-day EMA - 26-day EMA) 
		Signal Line: 9-day EMA of MACD
		MACD Histogram: MACD - Signal Line
		*/
		
		int size = quotes.length;
		
		double[] close = new double[size];
		for ( int i = 0; i < size; i++ )
			close[i] = ((QuoteData)quotes[i]).close;
		
		double[] macd = new double[size];
		double[] ema_12 = calculateEMA( close, 12 );
		double[] ema_26 = calculateEMA( close, 26 );
		for ( int i = 0; i < size; i++ )
			macd[i] = ema_12[i] - ema_26[i];
		
		double[] signal_line = calculateEMA( macd, 9 );	
		
		MACD[] macd_ind = new MACD[size];
		for ( int i = 0; i < size; i++)
		{
			macd_ind[i] = new MACD();
			macd_ind[i].macd = macd[i];
			macd_ind[i].signalLine = signal_line[i];
			macd_ind[i].histogram = macd[i] - signal_line[i];
		}
		
		return macd_ind;
		
		

	}


	
	static public BallingerBand[] calculateBallingerBand( QuoteData[] quotes )
	{
		int TIME_PERIOD = 14;
		int STANDARD_DEVIATION = 2;
		
		int size = quotes.length;
		double[] close = new double[size];
		for ( int i = 0; i < size; i++ )
			close[i] = ((QuoteData)quotes[i]).close;

		double[] ma = calculateSMA( close, TIME_PERIOD );
		BallingerBand[] bb = new BallingerBand[size];
		
		for ( int i = TIME_PERIOD; i <= size-1; i++)
		{
			bb[i] = new BallingerBand();
			bb[i].ma = ma[i];
			
			double sum = 0;
			for ( int j = i-TIME_PERIOD; j <=i; j++ )
			{
				sum += (quotes[j].close - ma[j]) * (quotes[j].close - ma[j]);
			}
			double deviation=STANDARD_DEVIATION*Math.sqrt(sum/(TIME_PERIOD));;
			
			bb[i].upperBB = bb[i].ma + deviation;
			bb[i].lowerBB = bb[i].ma - deviation;
		}

		return bb;
		
	}

	
	
	static public BallingerBand[] calculateBallingerBand_org( QuoteData[] quotes )
	{
		int TIME_PERIOD = 12;
		int STANDARD_DEVIATION = 2;
		
		int size = quotes.length;
		double[] close = new double[size];
		for ( int i = 0; i < size; i++ )
			close[i] = ((QuoteData)quotes[i]).close;

		double[] ma = calculateSMA( close, TIME_PERIOD );
		BallingerBand[] bb = new BallingerBand[size];
		
		for ( int i = TIME_PERIOD; i <= size-1; i++)
		{
			bb[i] = new BallingerBand();
			bb[i].ma = ma[i];
			
			double sum = 0;
			for ( int j = TIME_PERIOD; j <=i; j++ )
			{
				sum += (quotes[j].close - ma[j]) * (quotes[j].close - ma[j]);
			}
			double deviation=STANDARD_DEVIATION*Math.sqrt(sum/(i-TIME_PERIOD));;
			
			bb[i].upperBB = bb[i].ma + deviation;
			bb[i].lowerBB = bb[i].ma - deviation;
		}

		return bb;
		
	}

	
	
}
