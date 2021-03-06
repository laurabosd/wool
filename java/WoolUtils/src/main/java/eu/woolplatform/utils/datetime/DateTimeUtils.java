/*
 * Copyright 2019 Roessingh Research and Development.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package eu.woolplatform.utils.datetime;

import java.util.Calendar;
import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import eu.woolplatform.utils.exception.ParseException;

/**
 * This class contains various utility methods related to date and time.
 * 
 * @author Dennis Hofs (RRD)
 */
public class DateTimeUtils {
	
	/**
	 * Parses a date/time string and returns a date/time object of the
	 * specified class. We distinguish three types of date/time classes:
	 * UTC time, time with time zone, local date/time. The supported date/time
	 * classes are:
	 * 
	 * <p><ul>
	 * <li>long/Long (UNIX timestamp in milliseconds, UTC time)</li>
	 * <li>{@link Date Date} (UTC time)</li>
	 * <li>{@link Instant Instant} (UTC time)</li>
	 * <li>{@link Calendar Calendar} (with time zone)</li>
	 * <li>{@link DateTime DateTime} (with time zone)</li>
	 * <li>{@link LocalDate LocalDate}</li>
	 * <li>{@link LocalTime LocalTime}</li>
	 * <li>{@link LocalDateTime LocalDateTime}</li>
	 * </ul></p>
	 * 
	 * <p>It depends on the string format what class can be returned. This is
	 * detailed below. Supported string formats:</p>
	 *  
	 * <p><ul>
	 * <li>
	 * UNIX timestamp
	 * <p><ul>
	 * <li>long/Long, Date, Instant</li>
	 * <li>Calendar, DateTime: the timestamp is translated to the default time
	 * zone.</li>
	 * <li>LocalDate, LocalTime, LocalDateTime: the timestamp is translated to
	 * the default time zone before creating the local date/time.</li>
	 * </ul></p>
	 * </li>
	 * 
	 * <li>
	 * SQL date: yyyy-MM-dd
	 * <p><ul>
	 * <li>LocalDate</li>
	 * </ul></p>
	 * </li>
	 * 
	 * <li>
	 * SQL time: HH:mm:ss
	 * <p><ul>
	 * <li>LocalTime</li>
	 * </ul></p>
	 * </li>
	 * 
	 * <li>
	 * SQL datetime: yyyy-MM-dd HH:mm:ss
	 * <p><ul>
	 * <li>LocalDateTime</li>
	 * </ul></p>
	 * </li>
	 * 
	 * <li>
	 * any ISO date/time accepted by {@link ISODateTimeFormat#dateTimeParser()
	 * ISODateTimeFormat.dateTimeParser()}
	 * <p><ul>
	 * <li>long/Long, Date, Instant. If no time zone is given in the string, it
	 * interprets the date/time with the default time zone. If the date/time
	 * does not exist in the time zone (because of a DST change), this method
	 * throws an exception. These classes store UTC times, so any specified
	 * time zone is eventually lost. Note that a string with only a date is a
	 * valid ISO date, but this method considers it an SQL date so the result
	 * must be a LocalDate.</li>
	 * <li>Calendar, DateTime. The same as the UTC times except that any
	 * specified time zone is preserved in the result.</li>
	 * <li>LocalDate, LocalTime, LocalDateTime. Any specified time zone is
	 * ignored.</li>
	 * </ul></p>
	 * </li>
	 * </ul></p>
	 * 
	 * @param dateTimeString the date/time string
	 * @param clazz the result class
	 * @param <T> the type of date/time to return
	 * @return the date/time with the specified class
	 * @throws ParseException if the date/time string is invalid, or a
	 * date/time without a time zone is parsed in a time zone where that
	 * date/time does not exist
	 */
	public static <T> T parseDateTime(String dateTimeString,
			Class<T> clazz) throws ParseException {
		// try long
		try {
			long timestamp = Long.parseLong(dateTimeString);
			return dateTimeToType(new DateTime(timestamp), clazz);
		} catch (NumberFormatException ex) {}

		// try yyyy-MM-dd
		DateTimeFormatter parser = DateTimeFormat.forPattern(
				"yyyy-MM-dd");
		LocalDate localDate = null;
		try {
			localDate = parser.parseLocalDate(dateTimeString);
		} catch (IllegalArgumentException ex) {}
		try {
			if (localDate != null)
				return clazz.cast(localDate);
		} catch (ClassCastException ex) {
			throw new ParseException(
					"Pattern yyyy-MM-dd expects result class LocalDate, found: " +
					clazz.getName());
		}
		
		// try HH:mm:ss
		parser = DateTimeFormat.forPattern("HH:mm:ss");
		LocalTime localTime = null;
		try {
			localTime = parser.parseLocalTime(dateTimeString);
		} catch (IllegalArgumentException ex) {}
		try {
			if (localTime != null)
				return clazz.cast(localTime);
		} catch (ClassCastException ex) {
			throw new ParseException(
					"Pattern HH:mm:ss expects result class LocalTime, found: " +
					clazz.getName());
		}

		// try yyyy-MM-dd HH:mm:ss
		parser = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime localDateTime = null;
		try {
			localDateTime = parser.parseLocalDateTime(dateTimeString);
		} catch (IllegalArgumentException ex) {}
		try {
			if (localDateTime != null)
				return clazz.cast(localDateTime);
		} catch (ClassCastException ex) {
			throw new ParseException(
					"Pattern yyyy-MM-dd HH:mm:ss expects result class LocalDateTime, found: " +
					clazz.getName());
		}
		
		// try ISO time
		DateTime time;
		try {
			parser = ISODateTimeFormat.dateTimeParser().withOffsetParsed();
			time = parser.parseDateTime(dateTimeString);
		} catch (IllegalArgumentException ex) {
			throw new ParseException("Invalid date/time string: " +
					dateTimeString + ": " + ex.getMessage(), ex);
		}
		try {
			return dateTimeToType(time, clazz);
		} catch (IllegalArgumentException ex) {
			throw new ParseException("Invalid date/time target class: " +
					clazz.getName() + ": " + ex.getMessage(), ex);
		}
	}
	
	/**
	 * Converts a {@link DateTime DateTime} object to an object of the
	 * specified class. It supports the following classes.
	 * 
	 * <p><ul>
	 * <li>long/Long (UNIX timestamp in milliseconds): translated to UTC time,
	 * time zone is lost</li>
	 * <li>{@link Date Date}: translated to UTC time, time zone is lost</li>
	 * <li>{@link Instant Instant}: translated to UTC time, time zone is
	 * lost</li>
	 * <li>{@link Calendar Calendar}</li>
	 * <li>{@link DateTime DateTime}</li>
	 * <li>{@link LocalDate LocalDate}: time and time zone is ignored</li>
	 * <li>{@link LocalTime LocalTime}: date and time zone is ignored</li>
	 * <li>{@link LocalDateTime LocalDateTime}: time zone is ignored</li>
	 * </ul></p>
	 * 
	 * @param dateTime the date/time
	 * @param clazz the result class
	 * @param <T> the type of date/time to return
	 * @return the date/time with the specified class
	 * @throws IllegalArgumentException if the target class is not supported
	 */
	public static <T> T dateTimeToType(DateTime dateTime, Class<T> clazz)
			throws IllegalArgumentException {
		if (clazz == Long.TYPE || clazz == Long.class) {
			@SuppressWarnings("unchecked")
			T result = (T)Long.class.cast(dateTime.getMillis());
			return result;
		} else if (clazz == Date.class) {
			return clazz.cast(dateTime.toDate());
		} else if (clazz == Instant.class) {
			return clazz.cast(dateTime.toInstant());
		} else if (clazz == Calendar.class) {
			return clazz.cast(dateTime.toCalendar(null));
		} else if (clazz == DateTime.class) {
			return clazz.cast(dateTime);
		} else if (clazz == LocalDate.class) {
			return clazz.cast(dateTime.toLocalDate());
		} else if (clazz == LocalTime.class) {
			return clazz.cast(dateTime.toLocalTime());
		} else if (clazz == LocalDateTime.class) {
			return clazz.cast(dateTime.toLocalDateTime());
		} else {
			throw new IllegalArgumentException(
					"Unsupported date/time class: " + clazz.getName());
		}
	}

	/**
	 * Converts the specified local date/time to a date/time in the specified
	 * time zone. If the local time is in a DST gap, it will add one hour. It
	 * could therefore occur in the next day.
	 *
	 * @param localDateTime the local date/time
	 * @param tz the time zone
	 * @return the date/time
	 */
	public static DateTime localToUtcWithGapCorrection(
			LocalDateTime localDateTime, DateTimeZone tz) {
		if (!tz.isLocalDateTimeGap(localDateTime))
			return localDateTime.toDateTime(tz);
		else
			return localDateTime.plusHours(1).toDateTime(tz);
	}
}
