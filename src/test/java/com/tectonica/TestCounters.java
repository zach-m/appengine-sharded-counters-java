package com.tectonica;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class TestCounters
{
	private static final String INCREMENT_URL = "http://gae-sharded-counters.appspot.com/v2?name=%s&action=increment";
	private static final String GET_COUNT_URL = "http://gae-sharded-counters.appspot.com/v2?name=%s";

	private static final int THREAD_COUNT = 10;
	private static final int INC_PER_THREAD = 5;

	@Test
	public void test() throws Exception
	{
		final String counterName = Long.toHexString(System.currentTimeMillis());

		ExecutorService exec = Executors.newFixedThreadPool(THREAD_COUNT);
		for (int i = 0; i < THREAD_COUNT; i++)
		{
			final String threadName = "thread-" + i;
			System.err.println("Spawning " + threadName);
			exec.execute(new Runnable()
			{
				@Override
				public void run()
				{
					increment(counterName, INC_PER_THREAD, threadName);
				}
			});
		}
		exec.shutdown();
		exec.awaitTermination(1, TimeUnit.HOURS);

		int count = getCount(counterName);
		int expected = THREAD_COUNT * INC_PER_THREAD;
		System.out.println(String.format("Expected count = %d, Actual count = %d", expected, count));

		Assert.assertEquals(expected, count);
	}

	private void increment(final String counterName, final int times, final String threadName)
	{
		String incUrl = String.format(INCREMENT_URL, counterName);
		for (int i = 0; i < times; i++)
			System.out.println(threadName + " [" + i + "]: " + GET(incUrl));
	}

	private int getCount(final String counterName)
	{
		final String getUrl = String.format(GET_COUNT_URL, counterName);
		return Integer.parseInt(GET(getUrl));
	}

	private String GET(String url)
	{
		HttpURLConnection conn = null;
		try
		{
			conn = (HttpURLConnection) (new URL(url)).openConnection();
			conn.setRequestMethod("GET");
			conn.setUseCaches(false);
			conn.setDoInput(true);
			conn.setDoOutput(false);
			int statusCode = conn.getResponseCode();
			boolean ok = statusCode / 100 == 2;
			InputStream is = ok ? conn.getInputStream() : conn.getErrorStream();
			String content = (is == null) ? "" : streamToString(is);
			if (!ok)
				throw new RuntimeException(content);
			return content;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e.toString());
		}
		finally
		{
			if (conn != null)
				conn.disconnect();
		}
	}

	private String streamToString(InputStream is) throws IOException
	{
		StringBuffer sb = new StringBuffer();

		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line = in.readLine()) != null)
			sb.append(line).append("\n");
		in.close();

		return (sb.length() == 0) ? "" : sb.substring(0, sb.length() - "\n".length());
	}
}
