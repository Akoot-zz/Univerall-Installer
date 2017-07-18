package com.Akoot.installer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.Akoot.util.data.Options;
import com.Akoot.util.data.StringUtil;
import com.Akoot.util.io.CthFile;
import com.Akoot.util.io.CthFileConfiguration;

public class Installer
{
	private static CthFile script = new CthFile("script");
	private static CthFileConfiguration config = new CthFileConfiguration("data");
	private static Map<String,Object> map = new HashMap<String,Object>();

	public static void main(String[] args)
	{
		if(script.exists()) install();
		else System.out.println("not found");
	}

	private static void install()
	{
		if(config.exists()) config.create();
		for(String command: script.read())
		{
			parse(command);
		}
		config.delete();
	}

	private static void print(String s)
	{
		if(s.contains("{"))
		{
			for(String key: config.listKeys())
			{
				s = s.replaceAll("\\{" + key + "\\}", config.getString(key));
			}
		}
		System.out.println(s);
	}

	private static void parse(String command)
	{
		String[] args = StringUtil.getArgs(command.split("\\s"));
		Options options = new Options(args);

		/* Copy/move */
		if(options.getCommand().equalsIgnoreCase("copy") || options.getCommand().equalsIgnoreCase("move"))
		{
			boolean copy = options.getCommand().equals("copy");
			File from = new File(options.getArgFor("from"));
			File to = new File(options.getArgFor("into"));
			File[] files;
			if(options.get(1).equalsIgnoreCase("everything"))
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
				System.out.println((copy ? "copy" : "mov") + "ing " + f.getName() + " from " + from + " into " + to);
			}
		}

		/* Ask and save */
		else if(options.getCommand().equalsIgnoreCase("ask"))
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

			print(question);
			String answer = System.console().readLine();

			if(yesNo)
			{
				config.set(key, answer.toLowerCase().startsWith("y"));
			}
			else
			{
				config.set(key, answer);
			}
		}

		/* Printing */
		else if(options.getCommand().equalsIgnoreCase("say"))
		{
			for(String s: options.getCommandArgs())
			{
				print(s);
			}
		}
		
		/* Logic */
		else if(options.getCommand().equalsIgnoreCase("if"))
		{
			String key = options.get(0);
			String check = options.getArgBefore("then");
			boolean isBoolean = check.equalsIgnoreCase("true") || check.equalsIgnoreCase("false");
			boolean not = options.getArgFor("is").equalsIgnoreCase("not");
			if((isBoolean && config.getBoolean(key) != not) || (config.getString(key).equals(check)))
			{
				parse(command.substring(command.indexOf("then")));
			}
		}
	}
}
