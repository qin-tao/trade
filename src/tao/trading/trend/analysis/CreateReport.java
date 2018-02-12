package tao.trading.trend.analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CreateReport {

	int TOTAL_FILE;
	int TOTAL_PARAM = 25;  // default
	int NUM_PARAM = 5;  // default
	int FILES_PER_PARM;
	double[] totalProfit;
	int[] totalTrade;
	
	public static void main(String[] args) {
		CreateReport cr = new CreateReport();
		cr.createReport(args[0], args[1], args[2]);
	}

	void createReport(String numOfParam, String symbol, String dir){
		
	    StringBuffer strBuf = new StringBuffer();
		Scanner scanner = null;
		List<String> files = new ArrayList<String>();
		listfile(dir, files);
		
		NUM_PARAM = Integer.parseInt(numOfParam);
		TOTAL_PARAM = NUM_PARAM * NUM_PARAM; 
		
		TOTAL_FILE = files.size();
		FILES_PER_PARM = TOTAL_FILE/TOTAL_PARAM; 
		totalProfit = new double[TOTAL_FILE];
		totalTrade = new int[TOTAL_FILE];
	    System.out.println("Total Parameters:" + TOTAL_PARAM + " Total Files:" + TOTAL_FILE + " Files per Parameter:" + FILES_PER_PARM);

		
		int count = 0;
		/*****************************************
		 *  Read all TOTAL Profit lines
		 ****************************************/
		for(String f : files) {
	        try {
	            scanner = new Scanner(new FileReader(f));
	        } catch (FileNotFoundException ex) {
	            System.out.println("FileNotFoundException: " + ex.getMessage());
	            System.exit(-1);
	        }
	        
	        String line;
	        int indexUSD = 0;
	        int indexTT = 0;
    		String totalProfitStr = null;
    		String totalTradeStr = null;
    		while(scanner.hasNextLine()) {
	        	line = scanner.nextLine();
	        	if ( line.indexOf("TOTAL PROFIT:") != -1 ){
	        		indexUSD = line.indexOf("USD");
	        		totalProfitStr = line.substring(13, indexUSD-1);
	        		indexTT = line.indexOf("TOTAL TRADES:");
	        		totalTradeStr = line.substring(indexTT + 13);
	    	        //System.out.println(line + "\t" + totalProfitStr + " " + totalTradeStr);
	    	        //System.out.println(Math.round(new Double (totalProfitStr)) + "\t\t" + totalTradeStr);
	    	        totalProfit[count] = new Double(totalProfitStr.trim());
	    	        totalTrade[count] = new Integer(totalTradeStr.trim());
	    	        count++;
	    	        break;
	        	}
	        }
	    }
	
    /*	System.out.println(" Total File:  " + totalFile);
	    for ( int i = 0; i < totalFile; i++ ){
	    	System.out.println(totalProfit[i] + "   " + totalTrade[i]);
	    }*/
	    
		System.out.println("total files read: " + count);
		
	    // calcuate past five years
	    /*System.out.println("All Performance:" + FILES_PER_PARM);
		double[] totalProfitPastAll = new double[TOTAL_PARAM];
		int[] totalTradePastAll = new int[TOTAL_PARAM];
		double[] avgProfitPastAll = new double[TOTAL_PARAM];
		calculatePerformance( FILES_PER_PARM, totalProfitPastAll,totalTradePastAll,avgProfitPastAll);
		*/
		
		// each year is 50 week as we remove last two weeks because of christmas and new year
		System.out.println("Five Year Performance:");
		double[] totalProfitPast5 = new double[TOTAL_PARAM];
		int[] totalTradePast5 = new int[TOTAL_PARAM];
		double[] avgProfitPast5 = new double[TOTAL_PARAM];
		if ( FILES_PER_PARM >= 260 )
			calculatePerformance( 260, totalProfitPast5,totalTradePast5,avgProfitPast5);	
		else 
			calculatePerformance( FILES_PER_PARM, totalProfitPast5,totalTradePast5,avgProfitPast5);	
	    
	    System.out.println("Two Year Performance:");
		double[] totalProfitPast2 = new double[TOTAL_PARAM];
		int[] totalTradePast2 = new int[TOTAL_PARAM];
		double[] avgProfitPast2 = new double[TOTAL_PARAM];
		calculatePerformance( 104, totalProfitPast2,totalTradePast2,avgProfitPast2);

	    System.out.println("One Year Performance:");
		double[] totalProfitPast1 = new double[TOTAL_PARAM];
		int[] totalTradePast1 = new int[TOTAL_PARAM];
		double[] avgProfitPast1 = new double[TOTAL_PARAM];
		calculatePerformance( 52, totalProfitPast1,totalTradePast1,avgProfitPast1);

	    System.out.println("Six month Performance:");
		double[] totalProfitPast6 = new double[TOTAL_PARAM];
		int[] totalTradePast6 = new int[TOTAL_PARAM];
		double[] avgProfitPast6 = new double[TOTAL_PARAM];
		calculatePerformance( 26, totalProfitPast6,totalTradePast6,avgProfitPast6);
		
	    System.out.println("Three month Performance:");
		double[] totalProfitPast3 = new double[TOTAL_PARAM];
		int[] totalTradePast3 = new int[TOTAL_PARAM];
		double[] avgProfitPast3 = new double[TOTAL_PARAM];
		calculatePerformance( 13, totalProfitPast3,totalTradePast3,avgProfitPast3);

	    System.out.println("One month Performance:");
		double[] totalProfitPast4 = new double[TOTAL_PARAM];
		int[] totalTradePast4 = new int[TOTAL_PARAM];
		double[] avgProfitPast4 = new double[TOTAL_PARAM];
		calculatePerformance( 4, totalProfitPast4,totalTradePast4,avgProfitPast4);

		/*
		strBuf.append("\n\n\n");
		strBuf.append("              \t" + FILES_PER_PARM + "\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\t\t" + FILES_PER_PARM + "\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\n");
	    for ( int index = 0; index < TOTAL_PARAM; index++){
    	    //strBuf.append( String.format("Parm %2d %2d  %4.2f  %4.2f  %4.2f  %4.2f  %4.2f  %4.2f %4.2f   %3d %3d %3d %3d %3d %3d %3d\n", i, j, avgProfitPastAll[index], avgProfitPast5[index], avgProfitPast2[index], avgProfitPast1[index], avgProfitPast6[index],avgProfitPast3[index],avgProfitPast4[index], totalTradePastAll[index], totalTradePast5[index], totalTradePast2[index], totalTradePast1[index], totalTradePast6[index], totalTradePast3[index], totalTradePast4[index]));
    	    strBuf.append( String.format("Parm %2d  \t%4.2f\t%4.2f\t%4.2f\t%4.2f\t%4.2f\t%4.2f\t%4.2f\t\t%3d\t%3d\t%3d\t%3d\t%3d\t%3d\t%3d\n", index, avgProfitPastAll[index], avgProfitPast5[index], avgProfitPast2[index], avgProfitPast1[index], avgProfitPast6[index],avgProfitPast3[index],avgProfitPast4[index], totalTradePastAll[index], totalTradePast5[index], totalTradePast2[index], totalTradePast1[index], totalTradePast6[index], totalTradePast3[index], totalTradePast4[index]));
     	}*/
	    
		String[] results = new String[TOTAL_PARAM];
		int HALF_PARM = NUM_PARAM/2;
		
		/*
	    if ( TOTAL_PARAM == 25 ){
		    strBuf.append("\n\n\n");
			strBuf.append("              \t\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\t\t\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\n");
		    int index0 = 0;
		    for ( int j = -2; j <= 2; j++){
		    	for ( int i = -2; i <= 2; i++){
		    		int index = (i+2)*5 + (j+2);
		    	    //strBuf.append( String.format("Parm %2d %2d  %4.2f  %4.2f  %4.2f  %4.2f  %4.2f  %4.2f %4.2f   %3d %3d %3d %3d %3d %3d %3d\n", i, j, avgProfitPastAll[index], avgProfitPast5[index], avgProfitPast2[index], avgProfitPast1[index], avgProfitPast6[index],avgProfitPast3[index],avgProfitPast4[index], totalTradePastAll[index], totalTradePast5[index], totalTradePast2[index], totalTradePast1[index], totalTradePast6[index], totalTradePast3[index], totalTradePast4[index]));
		    	    results[index0] = String.format("%2d   %2d    Parm %2d %2d  \t%4.2f\t%4.2f\t%4.2f\t%4.2f\t%4.2f\t%4.2f\t\t\t%3d\t%3d\t%3d\t%3d\t%3d\t%3d\n", index0, index, i, j, avgProfitPast5[index], avgProfitPast2[index], avgProfitPast1[index], avgProfitPast6[index],avgProfitPast3[index],avgProfitPast4[index], totalTradePast5[index], totalTradePast2[index], totalTradePast1[index], totalTradePast6[index], totalTradePast3[index], totalTradePast4[index]);
		    	    strBuf.append( results[index0]);
		    	    index0++;
		    	    if ( index0 % 5 == 0 )
		    	    	strBuf.append("-----------------------------------------------------------------------------------------------------------------------------------------------------\n");
		    	}
		    }
	    }*/

	    strBuf.append("\n\n\n");
		strBuf.append("              \t\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\t\t\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\n");
	    int index0 = 0;
	    for ( int j = -1*HALF_PARM; j <= HALF_PARM; j++){
	    	for ( int i = -1*HALF_PARM; i <= HALF_PARM; i++){
	    		int index = (i+HALF_PARM)*NUM_PARAM + (j+HALF_PARM);
	    	    //strBuf.append( String.format("Parm %2d %2d  %4.2f  %4.2f  %4.2f  %4.2f  %4.2f  %4.2f %4.2f   %3d %3d %3d %3d %3d %3d %3d\n", i, j, avgProfitPastAll[index], avgProfitPast5[index], avgProfitPast2[index], avgProfitPast1[index], avgProfitPast6[index],avgProfitPast3[index],avgProfitPast4[index], totalTradePastAll[index], totalTradePast5[index], totalTradePast2[index], totalTradePast1[index], totalTradePast6[index], totalTradePast3[index], totalTradePast4[index]));
	    	    results[index0] = String.format("%2d   %2d    Parm %2d %2d  \t%4.2f\t%4.2f\t%4.2f\t%4.2f\t%4.2f\t%4.2f\t\t\t%3d\t%3d\t%3d\t%3d\t%3d\t%3d\n", index0, index, i, j, avgProfitPast5[index], avgProfitPast2[index], avgProfitPast1[index], avgProfitPast6[index],avgProfitPast3[index],avgProfitPast4[index], totalTradePast5[index], totalTradePast2[index], totalTradePast1[index], totalTradePast6[index], totalTradePast3[index], totalTradePast4[index]);
	    	    strBuf.append( results[index0]);
	    	    index0++;
	    	    if ( index0 % NUM_PARAM == 0 )
	    	    	strBuf.append("-----------------------------------------------------------------------------------------------------------------------------------------------------\n");
	    	}
	    }

		System.out.println("\n\n");
	    //System.out.println(strBuf.toString());
	    
	    /*
		strBuf.append("\n\n\n");
		strBuf.append("              \t\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\t\t\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\n");
		for ( int j = 0;  j < 5; j++){
			for ( int i = 0;  i < 5; i++){
				strBuf.append( results[j+i*5]);
			}
	    	strBuf.append("-----------------------------------------------------------------------------------------------------------------------------------------------------\n");
		}*/
		strBuf.append("\n\n\n");
		strBuf.append("              \t\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\t\t\t5yr\t2yr\t1yr\t6mo\t3mo\t1mo\n");
		for ( int j = 0;  j < NUM_PARAM; j++){
			for ( int i = 0;  i < NUM_PARAM; i++){
				strBuf.append( results[j+i*NUM_PARAM]);
			}
	    	strBuf.append("-----------------------------------------------------------------------------------------------------------------------------------------------------\n");
		}
	    
		strBuf.append("\n\n\n");
	    
	    
	    // calculate every 6 month
	    int total6M = FILES_PER_PARM / 26 + 1;
	    StringBuffer[] strBuf26 = new StringBuffer[TOTAL_PARAM];
	    for ( int index = 0; index < TOTAL_PARAM; index++){
    		strBuf26[index] = new StringBuffer();
	    	strBuf26[index].append( String.format("%2d\t", index));
     	}
	    
	    for ( int i = 0; i < total6M; i++){
			double[] totalProfit26W = new double[TOTAL_PARAM];
			int[] totalTradePast26W = new int[TOTAL_PARAM];
			double[] avgProfitPast26W = new double[TOTAL_PARAM];
			int start = i*26;
			int end = (i+1)*26-1;
			if ( end > FILES_PER_PARM -1 )
				end =  FILES_PER_PARM -1;
			calculatePerformance1( start, end, totalProfit26W,totalTradePast26W,avgProfitPast26W);
			
		    for ( int index = 0; index < TOTAL_PARAM; index++){
		    	strBuf26[index].append( String.format("%4.2f\t%3d\t", avgProfitPast26W[index], totalTradePast26W[index]));
	     	}
	    }
	    
	    
	    strBuf.append("\n\nCalculate every 6 month:\n");
	    for ( int index = 0; index < TOTAL_PARAM; index++){
    	    strBuf.append( strBuf26[index] + "\n");
     	}

	    System.out.println(strBuf.toString());

        BufferedWriter output = null;
        try {
            File file = new File("performance_" + symbol+".txt");
            output = new BufferedWriter(new FileWriter(file));
            output.write(strBuf.toString());
        	output.close();
        } catch ( IOException e ) {
            e.printStackTrace();
        }
	}

	
	void calculatePerformance( int n, double[] totalProfitPast,int[] totalTradePast,double[] avgProfitPast){
		for ( int i = 0; i < TOTAL_PARAM; i++){
	    	for ( int j = FILES_PER_PARM *(i+1) -n; j <  FILES_PER_PARM *(i+1); j++ ){
	    		totalProfitPast[i] += totalProfit[j];
	    		totalTradePast[i] += totalTrade[j];
	    	}
	    	avgProfitPast[i] = totalProfitPast[i]/totalTradePast[i];
		    //System.out.println("Parm" + i + " " + totalProfitPast[i] + " " + totalTradePast[i] + " "+ avgProfitPast[i]);
	    }
	}

	void calculatePerformance1( int start, int end, double[] totalProfitPast,int[] totalTradePast,double[] avgProfitPast){
		for ( int i = 0; i < TOTAL_PARAM; i++){
	    	for ( int j = FILES_PER_PARM *i + start; j <=  FILES_PER_PARM * i + end; j++ ){
	    		totalProfitPast[i] += totalProfit[j];
	    		totalTradePast[i] += totalTrade[j];
	    	}
	    	avgProfitPast[i] = totalProfitPast[i]/totalTradePast[i];
		   // System.out.println("Parm" + i + " " + totalProfitPast[i] + " " + totalTradePast[i] + " "+ avgProfitPast[i]);
	    }
	}
	
	
	
/*	void createReport(String symbol, String dir){
		
	    StringBuffer strBuf = new StringBuffer();
		Scanner scanner = null;
		List<String> files = new ArrayList<String>();
		listfile(dir, files);
		
		int lineTotal = 0;
		double[] totalProfit = new double[TOTAL_LINE];
		int[] totalTrade = new int[TOTAL_LINE];
	    for(String f : files) {
	        try {
	            scanner = new Scanner(new FileReader(f));
	        } catch (FileNotFoundException ex) {
	            System.out.println("FileNotFoundException: " + ex.getMessage());
	            System.exit(-1);
	        }
	        
	        String line;
	        int indexUSD = 0;
	        int indexTT = 0;
    		String totalProfitStr = null;
    		String totalTradeStr = null;
	        while(scanner.hasNextLine()) {
	        	line = scanner.nextLine();
	        	if ( line.indexOf("TOTAL PROFIT:") != -1 ){
	        		indexUSD = line.indexOf("USD");
	        		totalProfitStr = line.substring(13, indexUSD-1);
	        		indexTT = line.indexOf("TOTAL TRADES:");
	        		totalTradeStr = line.substring(indexTT + 13);
	    	        //System.out.println(line + "\t" + totalProfitStr + " " + totalTradeStr);
	    	        totalProfit[lineTotal] = new Double(totalProfitStr.trim());
	    	        totalTrade[lineTotal] = new Integer(totalTradeStr.trim());
	    	        lineTotal++;
	        	}
	        }
	    }
	    
	    for ( int i = 0; i < lineTotal; i++ ){
	    	System.out.println(totalProfit[i] + "   " + totalTrade[i]);
	    }
	    if ( lineTotal != TOTAL_LINE ){
	    	strBuf.append( "Missign file, total line is " + lineTotal + "\n" );
	    	//System.exit(-1);
	    }
	    
	    // calcuate past five years
	    System.out.println("Five Year Performance:");
		double[] totalProfitPast5 = new double[TOTAL_PARAM];
		int[] totalTradePast5 = new int[TOTAL_PARAM];
		double[] avgProfitPast5 = new double[TOTAL_PARAM];
	    for ( int i = 0; i < TOTAL_PARAM; i++){
	    	
	    	for ( int j = TOTAL_PERIOD *i; j <  TOTAL_PERIOD *(i+1); j++ ){
	    		totalProfitPast5[i] += totalProfit[j];
	    		totalTradePast5[i] += totalTrade[j];
	    	}
	    	avgProfitPast5[i] = totalProfitPast5[i]/totalTradePast5[i];
		    System.out.println("Parm" + i + " " + totalProfitPast5[i] + " " + totalTradePast5[i] + " "+ avgProfitPast5[i]);
	    }
	    
	    System.out.println("Two Year Performance:");
		double[] totalProfitPast2 = new double[TOTAL_PARAM];
		int[] totalTradePast2 = new int[TOTAL_PARAM];
		double[] avgProfitPast2 = new double[TOTAL_PARAM];
	    for ( int i = 0; i < TOTAL_PARAM; i++){
	    	
	    	for ( int j = TOTAL_PERIOD *i + 156; j <  TOTAL_PERIOD *i + 260; j++ ){
	    		totalProfitPast2[i] += totalProfit[j];
	    		totalTradePast2[i] += totalTrade[j];
	    	}
	    	avgProfitPast2[i] = totalProfitPast2[i]/totalTradePast2[i];
		    System.out.println("Parm" + i + " " + totalProfitPast2[i] + " " + totalTradePast2[i] + " "+ avgProfitPast2[i]);
	    }

	    strBuf.append("Total Performance:\n");
	    //for ( int i = 0; i < TOTAL_PARAM; i++){
	    //	strBuf.append("Parm" + i + " " + avgProfitPast5[i] + " " + avgProfitPast2[i] + "\n");
	    //}
	    
	    int index = 0;
	    for ( int i = -2; i <= 2; i++){
	    	for ( int j = -2; j <= 2; j++){
	    	    strBuf.append( String.format("Parm %2d %2d  %4.2f  %4.2f   %3d %3d\n", i, j, avgProfitPast5[index], avgProfitPast2[index], totalTradePast5[index], totalTradePast2[index]));
	    	    index++;
	    	}
	    }
	    
	    System.out.println(strBuf.toString());

        BufferedWriter output = null;
        try {
            File file = new File("performance_" + symbol+".txt");
            output = new BufferedWriter(new FileWriter(file));
            output.write(strBuf.toString());
        	output.close();
        } catch ( IOException e ) {
            e.printStackTrace();
        }

	    
	}*/
	
	
	public void listfile(String directoryName, List<String> fileList) {

	    // .............list file
	    File directory = new File(directoryName);

	    // get all the files from a directory
	    File[] fList = directory.listFiles();

	    for (File file : fList) {
	        if (file.isFile()) {
	        	fileList.add(file.getAbsolutePath());
	        	//System.out.println(file.getAbsolutePath());
	        } else if (file.isDirectory()) {
	            listfile(file.getAbsolutePath(), fileList);
	        }
	    }
	}        

	
	public File[] listf(String directoryName) {

	    // .............list file
	    File directory = new File(directoryName);

	    // get all the files from a directory
	    File[] fList = directory.listFiles();

	    for (File file : fList) {
	        if (file.isFile()) {
	            System.out.println(file.getAbsolutePath());
	        } else if (file.isDirectory()) {
	            listf(file.getAbsolutePath());
	        }
	    }
	    System.out.println(fList);
	    return fList;
	}        
	
}
