package tao.trading;

public class MATouch {

	public int touched;
	public int crossed;
	public double highest;
	public int highestPos;
	public double lowest;
	public int lowestPos;
	
	public int highBegin;
	public int highEnd;
	public int lowBegin;
	public int lowEnd;

	public int touchBegin;
	public int touchEnd;
	public QuoteData low;
	public QuoteData high;
	
	
	public static double DEFAULT_LOWEST = 9999;
	
	public MATouch() {
		super();
		
		lowest = DEFAULT_LOWEST;
	}
	
	
}
