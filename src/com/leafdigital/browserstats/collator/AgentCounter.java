package com.leafdigital.browserstats.collator;

import java.io.*;
import java.util.*;

import com.leafdigital.browserstats.collator.Collator.TimePeriod;

/** 
 * Counts user agents in one or a number of date categories and outputs
 * the result to files. 
 */
public class AgentCounter
{
	private File folder;
	private String prefix;
	private TimePeriod period;
	private boolean unordered, overwrite;
	private Category[] categories;
	
	private HashMap<String, AgentCount> counts = new HashMap<String, AgentCount>();
	private HashSet<String> past = new HashSet<String>();

	/**
	 * @param folder Folder for output files
	 * @param prefix Prefix for output files
	 * @param period Time period to divide output files
	 * @param unordered True if input lines may be unordered
	 * @param overwrite True if it's OK to overwrite existing files
	 * @param categories List of categories
	 */
	public AgentCounter(File folder, String prefix, TimePeriod period,
		boolean unordered, boolean overwrite, Category[] categories)
	{
		this.folder = folder;
		this.prefix = prefix;
		this.period = period;
		this.unordered = unordered;
		this.overwrite = overwrite;
		this.categories = categories;
	}

	/**
	 * Processes a single log line.
	 * @param line Line
	 * @throws IOException If any I/O error occurs
	 */
	void process(LogLine line) throws IOException
	{
		AgentCount count = null;
		String currentPeriod = null;
		
		switch(period)
		{
		case ALL :
		{
			count = counts.get(null);
		}	break;
			
		case MONTHLY :
		{
			currentPeriod = line.getIsoDate().substring(0, 7);
			count = counts.get(currentPeriod);
			// If there is no data for this month...
			if(count==null)
			{
				// Give an error if that's because this month is in the past
				if(past.contains(currentPeriod))
				{
					throw new IOException("Line out of sequence (try -unordered):\n" 
						+ line);
				}
			}
		} break;
		
		case DAILY :
		{
			currentPeriod = line.getIsoDate();
			count = counts.get(currentPeriod);
			if(count==null)
			{
				if(past.contains(currentPeriod))
				{
					throw new IOException("Line out of sequence (try -unordered):\n" 
						+ line);
				}
			}
		} break;
			
		}

		// Create new data if required
		if(count==null)
		{
			System.err.print("\n" + 
				(currentPeriod == null ? "Output" : currentPeriod) + ":");
			count = new AgentCount();
			counts.put(currentPeriod, count);
		}
		
		// Flush out older data after 1am on the next day
		if(!unordered && counts.size() > 1 
			&& line.getIsoTime().compareTo("01:00:00") > 0)
		{
			for(Iterator<String> i=counts.keySet().iterator(); i.hasNext();)
			{
				String period = i.next();
				if(period.compareTo(currentPeriod) < 0)
				{
					// Flush out old period and free RAM
					flush(period);
					i.remove();
					System.gc(); // Just to make the stats (maybe) work
				}
			}
		}
		
		// Actually count data
		count.count(line.getUserAgent(), line.getIp(), line.getCategory());
	}
	
	/**
	 * Flushes a single disk file. Does not actually remove from list.
	 * @param timePeriod Time period
	 * @throws IOException If any I/O error occurs
	 */
	private void flush(String timePeriod) throws IOException
	{
		AgentCount count = counts.get(timePeriod);
		File target = new File(folder, prefix + 
			(timePeriod == null ? "" : "." + timePeriod) + ".useragents");
		if (target.exists() && !overwrite)
		{
			throw new IOException("Would overwrite " + target 
				+ ", aborting. (Use -overwrite to allow.)");
		}
		count.write(target, timePeriod, categories);
		past.add(timePeriod);
	}
	
	/**
	 * Flushes all data to disk. Used at end of process.
	 * @throws IOException If any I/O error occurs
	 */
  void flush() throws IOException  
  {
  	for(String period : counts.keySet())
  	{
  		flush(period);
  	}
  	System.err.println("\n");
  }

}