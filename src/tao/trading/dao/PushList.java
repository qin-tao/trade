package tao.trading.dao;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MACD;
import tao.trading.QuoteData;

public class PushList
{
	public int direction;
	public PushHighLow[] phls;
	public int begin,end;
	double totalBreakOut;
	
	public PushList( int direction, PushHighLow[] phls, int begin, int end)
	{
		this.direction = direction;
		this.phls = phls;
		this.begin = begin;
		this.end = end;
	}

	public PushHighLow getPushHighLow( int index ){
		return phls[index];
	}
	
	public PushHighLow getLastPush(){
		return phls[phls.length-1];
	}

	public int getNumOfPushes()
	{
		if ( phls == null )
			return 0;
		
		return phls.length;
	}
	
	public int getTotalPushLength(){
		return end - begin;
	}
	
	
	
	public String toString( QuoteData[] quotes, double PIP_SIZE )
	{
		StringBuffer strBuf = new StringBuffer();
		
		if (Constants.DIRECTION_UP == direction){
			strBuf.append("PushBegin:" + quotes[begin].time + " " + quotes[begin].low + " PushEnd:" + quotes[end].time + " " + quotes[end].high + " Total Time:" + (end-begin+1) + " BreakOutSize:" + phls.length + "\n");
			totalBreakOut = (quotes[end].high - quotes[begin].low)/PIP_SIZE;
		}
		else if (Constants.DIRECTION_DOWN == direction){
			strBuf.append("PushBegin:" + quotes[begin].time + " " + quotes[begin].high + " PushEnd:" + quotes[end].time + " " + quotes[end].low + " Total Time:" + (end-begin+1) + " BreakOutSize:" + phls.length + "\n");
			totalBreakOut = (quotes[begin].high - quotes[end].low)/PIP_SIZE ;
		}
		strBuf.append("Total BreakOut:" + totalBreakOut + " Average BreakOut:" + totalBreakOut/(end-begin+1) + "\n");
		
		for ( int i = 0; i < phls.length; i++)
	 	{
			if ( direction == Constants.DIRECTION_UP )
			{
				strBuf.append("PushStart:" + quotes[phls[i].pushStart].time + " PreHigh:" + ((phls[i].prePos == -1)?"NULL     ":quotes[phls[i].prePos].time) +  " " + quotes[phls[i].prePos].high + " PullBack:" + ((phls[i].pullBack == null)?"NULL     ":phls[i].pullBack.time + " " + phls[i].pullBack.low) +
						" " + " CurHigh:" + ((phls[i].curPos == -1)?"NULL     ":quotes[phls[i].curPos].time) + " " + quotes[phls[i].curPos].high + " Gap:" + (phls[i].curPos - phls[i].prePos) + " "  /*+ " PushExt:" + quotes[phls[i].pushExt].time + " " + phls[i].pushLen + " " + phls[i].pullBackLen*/ + " " + phls[i].pullBackRatio );
			}
			else if ( direction == Constants.DIRECTION_DOWN )
			{
				strBuf.append("PushStart:" + quotes[phls[i].pushStart].time + " PreLow:" + ((phls[i].prePos == -1)?"NULL     ":quotes[phls[i].prePos].time) + " " + quotes[phls[i].prePos].low + " PullBack:" + ((phls[i].pullBack == null)?"NULL     ":phls[i].pullBack.time + " " + phls[i].pullBack.high) +
						" " + " CurLow:" + ((phls[i].curPos == -1)?"NULL     ":quotes[phls[i].curPos].time) + " " + quotes[phls[i].curPos].low + " Gap:" + (phls[i].curPos - phls[i].prePos) + " "  /*+ " PushExt:" + quotes[phls[i].pushExt].time + " " + phls[i].pushLen + " " + phls[i].pullBackLen*/ + " " + phls[i].pullBackRatio );

			}
			
			strBuf.append(" PushSize: " + (float)(Math.round(10*phls[i].pushSize/PIP_SIZE)/10.0f) + " ");
			strBuf.append("PullBackSize: " + (float)(Math.round(10*phls[i].pullBackSize/PIP_SIZE)/10.0f) + " ");
			if ( i < phls.length  )
			{
				strBuf.append("BreakOutSize: " + (float)(Math.round(10*phls[i].breakOutSize/PIP_SIZE)/10.0f));
			}
			
			
			strBuf.append("\n");
		}

		return strBuf.toString();
	}
	
	
	
	public void calculatePushTouchMA( QuoteData[] quotes, double[] ema)
	{
		int pushSize =  phls.length-1;
		if (Constants.DIRECTION_UP == direction)
		{
			for ( int i = 0; i < pushSize; i++ )
			{
				phls[i].pullBackTouch20MA = 0;
				for ( int j = phls[i].prePos; j <= phls[i].curPos; j++ )
				{
					if (quotes[j].low < ema[j])
						phls[i].pullBackTouch20MA++;
				}
			}
		}
		else if (Constants.DIRECTION_DOWN == direction)
		{
			for ( int i = 0; i < pushSize; i++ )
			{
				phls[i].pullBackTouch20MA = 0;
				for ( int j = phls[i].prePos; j <= phls[i].curPos; j++ )
				{
					if (quotes[j].high > ema[j])
						phls[i].pullBackTouch20MA++;
				}
			}
		}
	}
	
	
	public int getTouch20MANum()
	{
		int touch20MANum = 0;
		for ( int i = 0; i < phls.length-1; i++ )
		{
			touch20MANum += phls[i].pullBackTouch20MA;
		}

		return touch20MANum;
	}
	
	
	public void calculatePushMACD( QuoteData[] quotes)
	{
		MACD[] macd = Indicator.calculateMACD( quotes );
		
		int pushSize =  phls.length-1;
		if (Constants.DIRECTION_UP == direction)
		{
			for ( int i = 0; i < pushSize; i++ )
			{
				phls[i].pullBackMACDNegative = 0;
				for ( int j = phls[i].prePos; j <= phls[i].curPos; j++ )
				{
					if ( macd[j].histogram < 0 )
						phls[i].pullBackMACDNegative++;
				}
			}
		}
		else if (Constants.DIRECTION_DOWN == direction)
		{
			for ( int i = 0; i < pushSize; i++ )
			{
				phls[i].pullBackMACDPositive = 0;
				for ( int j = phls[i].prePos; j <= phls[i].curPos; j++ )
				{
					if ( macd[j].histogram > 0 )
						phls[i].pullBackMACDPositive++;
				}
			}
		}
	}
	

	
	
	
	
	//////////////////////////////////////////
	//
	//  Business Rules:
	//
	/////////////////////////////////////////
	public boolean rules_2_n_allSmallBreakOuts( double smallBreakOutSize)
	{
		for ( int i = 1; i < phls.length; i++)
		{
			if (phls[i].breakOutSize > smallBreakOutSize)
				return false;
		}
		
		return true;
	}

	
}
