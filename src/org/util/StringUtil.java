package org.util;

public class StringUtil {
	
	public static String formatWidths(String[] datas, int[] widths, String seperator)
	{
		String ret="";
		int space_num=0;
		for(int i=0;i<datas.length;i++)
		{
			ret+=datas[i];
			space_num=widths[i]-datas[i].length();
			for(int j=0;j<space_num;j++)
			{
				ret+=" ";
			}
			ret+=seperator;
		}
		return ret;
	}
}
