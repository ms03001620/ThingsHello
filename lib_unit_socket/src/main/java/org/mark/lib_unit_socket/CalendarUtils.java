package org.mark.lib_unit_socket;

import java.util.Calendar;

/**
 * Created by mark on 2020/5/17
 */
public class CalendarUtils {

    public static String getTimeNowHHMMSS() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE) + ":" + cal.get(Calendar.SECOND);
    }
}
