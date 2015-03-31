/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package com.thejoshwa.ultrasonic.androidapp.domain;

import java.util.regex.Pattern;

/**
 * Represents the version number of the Subsonic Android app.
 *
 * @author Sindre Mehus
 * @version $Revision: 1.3 $ $Date: 2006/01/20 21:25:16 $
 */
public class Version implements Comparable<Version>
{
	private static final Pattern COMPILE = Pattern.compile("\\.");
	private int major;
	private int minor;
	private int beta;
	private int bugFix;

	/**
	 * Creates a new version instance by parsing the given string.
	 *
	 * @param version A string of the format "1.27", "1.27.2" or "1.27.beta3".
	 */
	public Version(CharSequence version)
	{
		String[] s = COMPILE.split(version);
		major = Integer.valueOf(s[0]);
		minor = Integer.valueOf(s[1]);

		if (s.length > 2)
		{
			if (s[2].contains("beta"))
			{
				beta = Integer.valueOf(s[2].replace("beta", ""));
			}
			else
			{
				bugFix = Integer.valueOf(s[2]);
			}
		}
	}

	public int getMajor()
	{
		return major;
	}

	public int getMinor()
	{
		return minor;
	}

	/**
	 * Return whether this object is equal to another.
	 *
	 * @param o Object to compare to.
	 * @return Whether this object is equals to another.
	 */
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		final Version version = (Version) o;

		return beta == version.beta && bugFix == version.bugFix && major == version.major && minor == version.minor;
	}

	/**
	 * Returns a hash code for this object.
	 *
	 * @return A hash code for this object.
	 */
	public int hashCode()
	{
		int result;
		result = major;
		result = 29 * result + minor;
		result = 29 * result + beta;
		result = 29 * result + bugFix;
		return result;
	}

	/**
	 * Returns a string representation of the form "1.27", "1.27.2" or "1.27.beta3".
	 *
	 * @return A string representation of the form "1.27", "1.27.2" or "1.27.beta3".
	 */
	public String toString()
	{
		StringBuilder buf = new StringBuilder(3);
		buf.append(major).append('.').append(minor);
		if (beta != 0)
		{
			buf.append(".beta").append(beta);
		}
		else if (bugFix != 0)
		{
			buf.append('.').append(bugFix);
		}

		return buf.toString();
	}

	/**
	 * Compares this object with the specified object for order.
	 *
	 * @param version The object to compare to.
	 * @return A negative integer, zero, or a positive integer as this object is less than, equal to, or
	 * greater than the specified object.
	 */
	@Override
	public int compareTo(Version version)
	{
		if (major < version.major)
		{
			return -1;
		}

		if (major > version.major)
		{
			return 1;
		}

		if (minor < version.minor)
		{
			return -1;
		}

		if (minor > version.minor)
		{
			return 1;
		}

		if (bugFix < version.bugFix)
		{
			return -1;
		}

		if (bugFix > version.bugFix)
		{
			return 1;
		}

		int thisBeta = beta == 0 ? Integer.MAX_VALUE : beta;
		int otherBeta = version.beta == 0 ? Integer.MAX_VALUE : version.beta;

		if (thisBeta < otherBeta)
		{
			return -1;
		}

		if (thisBeta > otherBeta)
		{
			return 1;
		}

		return 0;
	}
}