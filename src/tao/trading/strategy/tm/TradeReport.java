package tao.trading.strategy.tm;

public class TradeReport
{
	String tradeReport;
	double totalProfit;
	double totalUnrealizedProfit;
	int totalTrade;
	
	public double getTotalUnrealizedProfit()
	{
		return totalUnrealizedProfit;
	}
	public void setTotalUnrealizedProfit(double totalUnrealizedProfit)
	{
		this.totalUnrealizedProfit = totalUnrealizedProfit;
	}
	public String getTradeReport()
	{
		return tradeReport;
	}
	public void setTradeReport(String tradeReport)
	{
		this.tradeReport = tradeReport;
	}
	public double getTotalProfit()
	{
		return totalProfit;
	}
	public void setTotalProfit(double totalProfit)
	{
		this.totalProfit = totalProfit;
	}
	public int getTotalTrade()
	{
		return totalTrade;
	}
	public void setTotalTrade(int totalTrade)
	{
		this.totalTrade = totalTrade;
	}
	
	
}
