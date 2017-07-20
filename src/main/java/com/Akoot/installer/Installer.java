package com.Akoot.installer;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import com.Akoot.util.data.Options;
import com.Akoot.util.data.StringUtil;
import com.Akoot.util.io.Config;
import com.Akoot.util.io.CthFile;
import com.Akoot.util.io.IOUtils;
import com.Akoot.util.net.URLUtils;

public class Installer
{
	private static CthFile script;
	private static Map<String, Object> objects;

	public static void main(String[] args)
	{
		if(args.length == 0) script = new CthFile("script");
		else new CthFile(args[0]);

		if(script.exists()) install();
		else System.out.println("Install script not found!");
	}

	private static void install()
	{
		objects = new HashMap<String, Object>();

		for(String command: script.read())
		{
			parse(command);
		}
	}

	private static void parse(String command)
	{
		/* Parsing system variables */
		if(command.contains("%"))
		{
			Matcher m = Pattern.compile("%\\S+%").matcher(command);
			while(m.find())
			{
				String s = m.group();
				String clean = s.substring(1, s.length() - 1);
				String var = System.getenv(clean);
				if(var == null) var = System.getProperty(clean);

				if(s.equalsIgnoreCase("%time%")) var = System.currentTimeMillis() + "";

				if(var != null) command = command.replace(s, var);
			}
		}

		if(command.contains("{"))
		{
			for(String key: objects.keySet())
			{
				command = command.replaceAll("\\{" + key + "\\}", objects.get(key).toString());
			}
		}

		String[] args = StringUtil.getArgs(command.split("\\s"));
		Options options = new Options(args);
		String cmd = options.getCommand();

		/* Copy/move */
		if(cmd.equals("copy") || cmd.equals("move"))
		{
			boolean copy = cmd.equals("copy");
			boolean loud = options.has("loudly");
			String fromDir = options.getArgFor("from");
			File from = new File(fromDir.equalsIgnoreCase("here") ? "." : fromDir);
			File to = new File(options.getArgFor("into"));
			to.mkdirs();
			File[] files;
			if(options.get(0).equalsIgnoreCase("everything"))
			{
				files = from.listFiles();
			}
			else
			{
				String[] names = options.getArgsBetween("", "from");
				files = new File[names.length];
				for(int i = 0; i < names.length; i++)
				{
					files[i] = new File(names[i]);
				}
			}
			for(File f: files)
			{
				if(loud) System.out.println((copy ? "Copy: " : "Move: ") + f.getName() + " -> " + to);
				try
				{
					if(f.isDirectory())
					{
						if(copy) FileUtils.copyDirectoryToDirectory(f, to);
						else FileUtils.moveDirectoryToDirectory(f, to, true);
					}
					else
					{
						if(copy) FileUtils.copyFileToDirectory(f, to);
						else FileUtils.moveFileToDirectory(f, to, true);
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}

		/* Ask */
		else if(cmd.equals("ask"))
		{
			String question = "";
			boolean yesNo = false;
			String key = options.getArgFor("for");

			if(options.get(0).equalsIgnoreCase("y/n"))
			{
				question = options.get(1);
				yesNo = true;
			}
			else
			{
				question = options.get(0);
				yesNo = false;
			}

			System.out.print(question + " ");
			String answer = System.console().readLine();

			if(yesNo)
			{
				objects.put(key, answer.toLowerCase().startsWith("y"));
			}
			else
			{
				objects.put(key, answer);
			}
		}

		/* Printing */
		else if(cmd.equals("say"))
		{
			for(String s: options.getArgs())
			{
				System.out.println(s);
			}
		}

		/* Logic */
		else if(cmd.equals("if"))
		{
			String key = options.get(0);
			String check = options.getArgBefore("then");
			boolean isBoolean = check.equalsIgnoreCase("true") || check.equalsIgnoreCase("false");
			boolean isExists = check.equalsIgnoreCase("exists") || (check.equalsIgnoreCase("exist"));
			boolean notExists = options.getArgBefore("exist").toLowerCase().startsWith("doesn");
			boolean not = options.getArgFor("is").equalsIgnoreCase("not");
			boolean got = (objects.get(key) instanceof Boolean ? (Boolean) (objects.get(key)) : false);
			if((isBoolean && got != not) || (objects.get(key).toString().equals(check)))
			{
				parse(command.substring(command.indexOf("then")));
			}
			else if(isExists && new File(key).exists() && !notExists)
			{
				parse(command.substring(command.indexOf("then")));
			}
		}

		/* Download */
		else if(cmd.equals("download"))
		{
			String URL = options.get(0);
			String to = options.getArgFor("into");
			URLUtils.download(URL, new File(to));
			if(options.has("loudly"))
			{
				System.out.println("Download: " + URL + " -> " + to);
			}
		}

		/* Run */
		else if(cmd.equals("run"))
		{
			try
			{
				Runtime.getRuntime().exec(options.get(0));
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			return;
		}

		/* Open */
		else if(cmd.equals("open"))
		{
			String path = options.get(0);
			File file = new File(path);
			if(file.exists())
			{
				try
				{
					Desktop.getDesktop().open(file);
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				try
				{
					Desktop.getDesktop().browse(new URL(path).toURI());
				}
				catch (IOException | URISyntaxException e)
				{
					IOUtils.runProtocol(path);
				}
			}
		}

		/* Save */
		else if(cmd.equals("save"))
		{
			File to = new File(options.getArgFor("to"));
			new Config(to.getPath(), objects);
		}

		/* Wait */
		else if(cmd.equals("wait"))
		{
			if(options.size() == 2)
			{
				long ms = 0;

				if(options.get(1).toLowerCase().startsWith("millesecond"))
				{
					ms = Long.parseLong(options.get(0));
				}
				else if(options.get(1).toLowerCase().startsWith("second"))
				{
					ms = (long) (Double.parseDouble(options.get(0)) * 1000.0);
				}

				try
				{
					Thread.sleep(ms);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				System.out.print("Press enter to continue...");
				System.console().readLine();
			}
		}

		/* Remember */
		else if(cmd.equals("remember") || cmd.equals("remember,"))
		{
			objects.put(options.get(0), options.getArgFor("is"));
		}

		/* Delete */
		else if(cmd.equals("delete"))
		{
			boolean later = options.has("later");
			for(String s: options.getArgs())
			{
				File file = new File(s);
				if(!file.isDirectory())
				{
					if(later) file.deleteOnExit();
					else file.delete();
				}
				else
				{
					try
					{
						FileUtils.deleteDirectory(file);
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
				if(options.has("loudly"))
				{
					System.out.println("Delete: " + file.getAbsolutePath());
				}
			}
		}
	}
}
