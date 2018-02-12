package tao.trading;

public class SessionData
{
	public String currentTime;
	public static String TASK_GET_POSITION_BEFORE_BUY = "GetPositionBeforeBuy";
	public static String TASK_BUY = "Buy";
	public static String TASK_GET_TIME = "GetTime";
	public static String TASK_CLOSE_EXISTING_SHORT_POSITION = "CloseExistingShortPosition";
	public static String TASK_CLOSE_EXISTING_LONG_POSITION = "CloseExistingLongPosition";
	public static String TASK_CLOSE_EXISTING_POSITION_BEFORE_BUY = "CloseExistingPostionBeforeBuy";
	
	public String task;
	
	//Buy properties
	public int buyPosition;
	public double buyStop;
	public double buyTarget;

	
	
	
	
	public String getTask()
	{
		return task;
	}
	public void setTask(String task)
	{
		this.task = task;
	}
	public static String getTASK_BUY()
	{
		return TASK_BUY;
	}
	public static void setTASK_BUY(String task_buy)
	{
		TASK_BUY = task_buy;
	}
	public int getBuyPosition()
	{
		return buyPosition;
	}
	public void setBuyPosition(int buyPosition)
	{
		this.buyPosition = buyPosition;
	}
	public double getBuyStop()
	{
		return buyStop;
	}
	public void setBuyStop(double buyStop)
	{
		this.buyStop = buyStop;
	}
	public double getBuyTarget()
	{
		return buyTarget;
	}
	public void setBuyTarget(double buyTarget)
	{
		this.buyTarget = buyTarget;
	}
	
}
