package tao.trading;

public class Reversal123 {

	public int startpos;
	public int supportpos;
	public int breakpos;
	public double a;
	public QuoteData[] fractuals;
	public int fractual1, fractual2;
	public int type;
	
	public static int UP_BY_LOW = 1;
	public static int DOWN_BY_HIGH = 2;
	
	
	public Reversal123()
	{
	}
	
	public Reversal123(int startpos, int supportpos, int breakpos) {
		super();
		this.startpos = startpos;
		this.supportpos = supportpos;
		this.breakpos = breakpos;
	}
	
	public Reversal123(double a, QuoteData[] fractuals, int type )
	{
		this.a = a;
		this.fractuals = fractuals;
		this.type = type;
	}


	public Reversal123(double a, int fractual1, int fractual2 )
	{
		this.a = a;
		this.fractual1 = fractual1;
		this.fractual2 = fractual2;
	}

	
	public double calculateProjectedPrice ( int pos )
	{
		QuoteData last_fractual = fractuals[fractuals.length - 1];
		
		if ( type == UP_BY_LOW )
		   return last_fractual.low + (pos - last_fractual.pos)*a;

		return 0;
	}
	

	public double calculateProjectedPrice ( QuoteData[] quotes, int pos, int type )
	{
		QuoteData last_fractual = quotes[fractual2];
		
		if ( type == UP_BY_LOW )
		   return last_fractual.low + (pos - last_fractual.pos)*a;

		return 0;
	}

	
	
}
