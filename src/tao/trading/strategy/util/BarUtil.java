package tao.trading.strategy.util;

import java.util.Arrays;

import tao.trading.QuoteData;

public class BarUtil
{
	static public double barSize(QuoteData qd)
	{
		return Math.abs(qd.open - qd.close);
	}
	
	static public boolean isUpBar( QuoteData qd)
	{
		if ( qd.close > qd.open )
			return true;
		else
			return false;
	}

	static public boolean isDownBar( QuoteData qd)
	{
		if ( qd.close < qd.open )
			return true;
		else
			return false;
	}

	static public double averageDownSizeBar( QuoteData[] quotes )
	{
		double downSize = 0;
		int downSizeNum = 0;
		
		for ( int i = 0; i < quotes.length; i++)
		{
			if ( quotes[i].close < quotes[i].open )
			{
				downSize += quotes[i].open - quotes[i].close;
				downSizeNum++;
			}
		}
		
		return downSize/downSizeNum;
		
	}

	static public double averageUpSizeBar( QuoteData[] quotes )
	{
		double upSize = 0;
		int upSizeNum = 0;
		
		for ( int i = 0; i < quotes.length; i++)
		{
			if ( quotes[i].close > quotes[i].open )
			{
				upSize +=  quotes[i].close - quotes[i].open;
				upSizeNum++;
			}
		}
		
		return upSize/upSizeNum;
		
	}


	static public double averageBarSizeOpenClose( QuoteData[] quotes)
	{
		return averageBarSizeOpenClose( quotes, 0, quotes.length-1);
	}
	
	static public double averageBarSizeOpenClose( QuoteData[] quotes, int begin, int end  ){
		double size = 0;
		int sizeNum = 0;
		for ( int i = begin; i <=end; i++){
			size +=  Math.abs(quotes[i].open - quotes[i].close);
			sizeNum++;
		}
		return size/sizeNum;
	}

	
	
	static public double averageBarSize( QuoteData[] quotes )
	{
		double size = 0;
		int sizeNum = 0;
		
		for ( int i = 0; i < quotes.length; i++){
			size +=  Math.abs(quotes[i].high - quotes[i].low);
			sizeNum++;
		}
		
		return size/sizeNum;
	}

	static public double averageBarSize( QuoteData[] quotes, int begin, int end )
	{
		double size = 0;
		int sizeNum = 0;
		
		for ( int i = begin; i <= end; i++){
			size +=  Math.abs(quotes[i].high - quotes[i].low);
			sizeNum++;
		}
		
		return size/sizeNum;
	}
	
	
	
	
	
	static public double averageBarSize2( QuoteData[] quotes )
	{
		int size = quotes.length-1;
		
		double[] barSize = new double[size];
		for ( int i = 0; i < size; i++)
		{
			barSize[i] = quotes[i].high - quotes[i].low;
		}

		Arrays.sort(barSize);
		
		double totalSize = 0;
		for ( int i= 3; i < size - 3; i++ )  // remove the highest 3 bar and lowest 3 bar for more accuracy;
			totalSize += barSize[i];
		
		return totalSize/(size - 6);
		
	}

	
	static public boolean isStrongPush ( double pushSize, int pushNum, double avgSize ){
		
		if ((pushNum == 1) && (pushSize > avgSize))
			return true;
		if (pushNum >1 )
			return true;
		
		/*
		if ( pushNum == 1 ){
			if ( pushSize > 2 * avgSize )
				return true;
		}
		else if ( pushNum == 2 ){
			if ( pushSize > 2 * 1 * avgSize )
				return true;
		}
		else if ( pushNum == 3 ){
			if ( pushSize > 3 * 0.8 * avgSize )
				return true;
		}
		else if ( pushNum == 4 ){
			if ( pushSize > 4 * 0.7 * avgSize )
				return true;
		}*/
		
		return false;
	}
	
}
