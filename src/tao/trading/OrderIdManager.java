package tao.trading;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class OrderIdManager
{
    private static OrderIdManager ref;

    private OrderIdManager()
    {
        // no code req'd
    }


    public static OrderIdManager getInstance()
    {
      if (ref == null)
          // it's ok, we can call this constructor
          ref = new OrderIdManager();
      return ref;
    }


	synchronized public int getOrderId()
	{
		int orderId = readOrderId();
		writeOrderId(++orderId);
		return orderId;
	}

	public void writeOrderId(int orderId)
	{
		try
		{
			// Create file
			FileWriter fstream = new FileWriter("c:/orderId.txt");
			BufferedWriter out = new BufferedWriter(fstream);
			String st = (new Integer(orderId)).toString();
			out.write(st);
			// Close the output stream
			out.close();
		} catch (Exception e)
		{// Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
	}

	public int readOrderId()
	{
		BufferedReader in;
		String read=null;
		try
		{

			in = new BufferedReader(new FileReader("c:/orderId.txt"));// open a
																	// bufferedReader
																	// to file
																	// hellowrold.txt
			read = in.readLine();// read a line from helloworld.txt and save
									// into a string
			in.close();// safley close the BufferedReader after use
		} 
		catch (IOException e)
		{
			System.out.println("There was a problem:" + e);

		}
		return new Integer(read);
	}

}
